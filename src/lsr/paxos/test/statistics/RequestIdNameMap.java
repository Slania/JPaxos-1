package lsr.paxos.test.statistics;

import lsr.common.RequestId;

public class RequestIdNameMap {

    private RequestId requestId;
    private String request;

    public RequestIdNameMap(RequestId requestId, String request) {
        this.requestId = requestId;
        this.request = request;
    }

    public RequestId getRequestId() {
        return requestId;
    }

    public String getRequest() {
        return request;
    }
}
