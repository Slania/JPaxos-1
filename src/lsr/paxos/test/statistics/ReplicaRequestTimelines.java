package lsr.paxos.test.statistics;

import com.sun.org.apache.bcel.internal.generic.NEW;
import lsr.common.ProcessDescriptor;
import lsr.common.RequestId;
import lsr.paxos.replica.ClientBatchID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class ReplicaRequestTimelines implements Runnable{

    public static final Object lock = new Object();

    static final Logger logger = Logger.getLogger(ReplicaRequestTimelines.class.getCanonicalName());

    public static HashMap<ClientBatchID, List<FlowPointData>> batchFlowMap = new HashMap<ClientBatchID, List<FlowPointData>>();
    public static HashMap<RequestId, List<FlowPointData>> requestFlowMap = new HashMap<RequestId, List<FlowPointData>>();

    public static List<RequestId> finishedRequests = new ArrayList<RequestId>();

    public static final int processId = ProcessDescriptor.getInstance().localId;

    public static Long skew = (long) 0;

    public static void addFlowPoint(ClientBatchID clientBatchID, FlowPointData data){
        data.setTimestamp(data.getTimestamp() - skew);
        List<FlowPointData> fdp = batchFlowMap.get(clientBatchID);
        if (fdp == null) {
            fdp = new ArrayList<FlowPointData>();
        }
        fdp.add(data);
        batchFlowMap.put(clientBatchID, fdp);
    }

    public static void addFlowPoint(RequestId requestId, FlowPointData data){
        data.setTimestamp(data.getTimestamp() - skew);
        List<FlowPointData> fdp = requestFlowMap.get(requestId);
        if (fdp == null) {
            fdp = new ArrayList<FlowPointData>();
        }
        fdp.add(data);
        requestFlowMap.put(requestId, fdp);
    }

    public static void logFLowPoints(ClientBatchID clientBatchID){
        List<FlowPointData> flowPointData = batchFlowMap.get(clientBatchID);
        for (FlowPointData flowPoint : flowPointData) {
            logger.info("*******" + flowPoint.toString() + "*******");
        }
        batchFlowMap.remove(clientBatchID);
    }

    public static void logFLowPoints(RequestId requestId){
        List<FlowPointData> flowPointData = requestFlowMap.get(requestId);
        for (FlowPointData flowPoint : flowPointData) {
            logger.info("*******" + flowPoint.toString() + "*******");
        }
        requestFlowMap.remove(requestId);
    }

    @Override
    public void run() {
        for (RequestId finishedRequest : finishedRequests) {
            logFLowPoints(finishedRequest);
        }
    }
}
