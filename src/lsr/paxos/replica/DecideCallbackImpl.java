package lsr.paxos.replica;

import static lsr.common.ProcessDescriptor.processDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.NavigableMap;
import java.util.TreeMap;

import lsr.common.ClientRequest;
import lsr.common.MovingAverage;
import lsr.common.SingleThreadDispatcher;
import lsr.paxos.UnBatcher;
import lsr.paxos.storage.ClientBatchStore;
import lsr.paxos.storage.ConsensusInstance;

import lsr.paxos.test.statistics.FlowPointData;
import lsr.paxos.test.statistics.ReplicaRequestTimelines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecideCallbackImpl implements DecideCallback {

    private final Replica replica;

    private final SingleThreadDispatcher replicaDispatcher;

    /**
     * Temporary storage for the instances that finished and are not yet
     * executed.
     * <p/>
     * Warning: multi-thread access
     */
    private final NavigableMap<Integer, ConsensusInstance> decidedWaitingExecution =
            new TreeMap<Integer, ConsensusInstance>();

    /**
     * Next instance that will be executed on the replica. Same as in replica
     */
    private int executeUB;

    /**
     * Used to predict how much time a single instance takes
     */
    private MovingAverage averageInstanceExecTime = new MovingAverage(0.4, 0);

    /**
     * If predicted time is larger than this threshold, batcher is given more
     * time to collect requests
     */
    private static final double OVERFLOW_THRESHOLD_MS = 250;

    public DecideCallbackImpl(Replica replica, int executeUB) {
        this.replica = replica;
        this.executeUB = executeUB;
        replicaDispatcher = replica.getReplicaDispatcher();
    }

    @Override
    public void onRequestOrdered(final int instance, final ConsensusInstance ci) {
        synchronized (decidedWaitingExecution) {
            decidedWaitingExecution.put(instance, ci);
        }

        if (processDescriptor.indirectConsensus) {
            Deque<ClientBatchID> clientBatchIds = ci.getClientBatchIds();
            for (ClientBatchID clientBatchId : clientBatchIds) {
                ClientRequest[] clientRequests = ClientBatchStore.instance.getBatch(clientBatchId);
                for (ClientRequest clientRequest : clientRequests) {
                    synchronized (ReplicaRequestTimelines.lock) {
                        ReplicaRequestTimelines.addFlowPoint(clientRequest.getRequestId(), new FlowPointData(FlowPointData.FlowPoint.DecidedCallbackImpl_onRequestOrdered, System.currentTimeMillis()));
                    }
                }
            }
        } else {
            ClientRequest[] clientRequests = UnBatcher.unpackCR(ci.getValue());
            for (ClientRequest clientRequest : clientRequests) {
                synchronized (ReplicaRequestTimelines.lock) {
                    ReplicaRequestTimelines.addFlowPoint(clientRequest.getRequestId(), new FlowPointData(FlowPointData.FlowPoint.DecidedCallbackImpl_onRequestOrdered, System.currentTimeMillis()));
                }
            }
        }

        replicaDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                executeRequests();
            }
        });
        Thread.yield();
    }

    /**
     * Returns how many instances is the service behind the Paxos protocol
     */
    public int decidedButNotExecutedCount() {
        synchronized (decidedWaitingExecution) {
            return decidedWaitingExecution.size();
        }
    }

    private void executeRequests() {
        replicaDispatcher.checkInDispatcher();

        if (decidedWaitingExecution.size() > 100) {
            // !!FIXME!! (JK) inform the proposer to inhibit proposing
        }

        logger.trace("Executing requests...");

        while (true) {
            ConsensusInstance ci;
            synchronized (decidedWaitingExecution) {
                ci = decidedWaitingExecution.get(executeUB);
            }
            if (ci == null) {
                logger.debug("Cannot continue execution. Next instance not decided: {}", executeUB);
                return;
            }

            ClientRequest[] requests;

            if (processDescriptor.indirectConsensus) {
                logger.info("Executing instance: {}", executeUB);
                Deque<ClientBatchID> batch = ci.getClientBatchIds();
                if (batch.size() == 1 && batch.getFirst().isNop()) {
                    replica.executeNopInstance(executeUB);
                    requests = new ClientRequest[0];
                } else {
                    long start = System.currentTimeMillis();
                    ArrayList<ClientRequest> requestsList = new ArrayList<ClientRequest>();
                    for (ClientBatchID bId : batch) {
                        assert !bId.isNop();
                        ClientRequest[] requestsFragment = ClientBatchStore.instance.getBatch(bId);
                        logger.debug("Executing batch: {}", bId);
                        replica.executeClientBatchAndWait(executeUB, requestsFragment);
                        requestsList.addAll(Arrays.asList(requestsFragment));
                        for (ClientRequest clientRequest : requestsList) {
                            synchronized (ReplicaRequestTimelines.lock) {
                                ReplicaRequestTimelines.addFlowPoint(clientRequest.getRequestId(), new FlowPointData(FlowPointData.FlowPoint.DecidedCallbackImpl_ExecuteRequests, System.currentTimeMillis()));
                            }
                        }
                    }
                    requests = (ClientRequest[]) requestsList.toArray();
                    averageInstanceExecTime.add(System.currentTimeMillis() - start);
                }
            } else {
                requests = UnBatcher.unpackCR(ci.getValue());
                for (ClientRequest request : requests) {
                    synchronized (ReplicaRequestTimelines.lock) {
                        ReplicaRequestTimelines.addFlowPoint(request.getRequestId(), new FlowPointData(FlowPointData.FlowPoint.DecidedCallbackImpl_ExecuteRequests, System.currentTimeMillis()));
                    }
                }
                if (logger.isDebugEnabled(processDescriptor.logMark_OldBenchmark)) {
                    logger.info(processDescriptor.logMark_OldBenchmark,
                            "Executing instance: {} {}",
                            executeUB, Arrays.toString(requests));
                } else {
                    logger.info("Executing instance: {}", executeUB);
                }
                long start = System.currentTimeMillis();
                replica.executeClientBatchAndWait(executeUB, requests);
                averageInstanceExecTime.add(System.currentTimeMillis() - start);
            }

            // Done with all the client batches in this instance
            replica.instanceExecuted(executeUB, requests);
            for (ClientRequest clientRequest : requests) {
                synchronized (ReplicaRequestTimelines.lock) {
                    ReplicaRequestTimelines.finishedRequestIds.add(clientRequest.getRequestId());
                }
            }
            synchronized (decidedWaitingExecution) {
                decidedWaitingExecution.remove(executeUB);
            }
            executeUB++;
        }

    }

    public void atRestoringStateFromSnapshot(final int nextInstanceId) {
        replicaDispatcher.checkInDispatcher();

        executeUB = nextInstanceId;
        replicaDispatcher.checkInDispatcher();
        synchronized (decidedWaitingExecution) {
            if (!decidedWaitingExecution.isEmpty()) {
                if (decidedWaitingExecution.lastKey() < nextInstanceId) {
                    decidedWaitingExecution.clear();
                } else {
                    while (decidedWaitingExecution.firstKey() < nextInstanceId) {
                        decidedWaitingExecution.pollFirstEntry();
                    }
                }
            }
        }
    }

    @Override
    public boolean hasDecidedNotExecutedOverflow() {
        double predictedTime = averageInstanceExecTime.get() * decidedWaitingExecution.size();
        return predictedTime >= OVERFLOW_THRESHOLD_MS;
    }

    static final Logger logger = LoggerFactory.getLogger(DecideCallbackImpl.class);
}
