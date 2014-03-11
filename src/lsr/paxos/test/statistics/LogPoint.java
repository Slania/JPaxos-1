package lsr.paxos.test.statistics;

import lsr.common.RequestId;

public class LogPoint {

    private FlowPointData flowPointData;
    private RequestId requestId;

    public LogPoint(FlowPointData flowPointData, RequestId requestId) {
        this.flowPointData = flowPointData;
        this.requestId = requestId;
    }

    public FlowPointData getFlowPointData() {
        return flowPointData;
    }

    public RequestId getRequestId() {
        return requestId;
    }
}
