package lsr.paxos.test.statistics;

public class FlowPointData {

    //points where we want to color/track the request flow
    public enum FlowPoint {
        Client_Send_Request, Client_Receive_Reply, NioClientProxy_Execute, ClientBatchManager_SendToAll, ClientBatchManager_BatchSent,
        ClientBatchManager_OnForwardClientBatch, Paxos_EnqueueRequest,
        ProposerImpl_Propose, Learner_OnAccept, Acceptor_OnPropose, Paxos_Decide,
        DecidedCallbackImpl_onRequestOrdered, DecidedCallbackImpl_ExecuteRequests, Replica_ExecuteClientRequest, Service_Execute_Start,
        Service_Execute_Finish, ClientRequestManager_OnRequestExecuted, NioClientProxy_Sent, ClientRequestBatcher_SendBatch
    }

    private FlowPoint flowPoint;
    private Long timestamp;
    private int replicaId = -1;

    public FlowPointData(FlowPoint flowPoint, Long timestamp) {
        this.flowPoint = flowPoint;
        this.timestamp = timestamp;
    }

    public FlowPointData(FlowPoint flowPoint, Long timestamp, int replicaId) {
        this.flowPoint = flowPoint;
        this.timestamp = timestamp;
        this.replicaId = replicaId;
    }

    public FlowPoint getFlowPoint() {
        return flowPoint;
    }

    public void setFlowPoint(FlowPoint flowPoint) {
        this.flowPoint = flowPoint;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public int getReplicaId() {
        return replicaId;
    }

    public void setReplicaId(int replicaId) {
        this.replicaId = replicaId;
    }

    @Override
    public String toString() {
        return flowPoint.toString() + ": " + timestamp.toString() + "\n";
    }
}
