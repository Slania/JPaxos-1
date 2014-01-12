package lsr.paxos.test.statistics;

import lsr.common.ProcessDescriptor;
import lsr.common.RequestId;
import lsr.paxos.replica.ClientBatchID;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public class ReplicaRequestTimelines implements Runnable{

    String initSql = "insert into instrumentation(request_id, replica_id) values(?,?)";
    String client_send_request_sql = "update instrumentation set client_send_request = ? where request_id = ? and replica_id = ?";
    String client_receive_reply_sql = "update instrumentation set client_receive_reply = ? where request_id = ? and replica_id = ?";
    String nioclientproxy_execute_sql = "update instrumentation set nioclientproxy_execute = ? where request_id = ? and replica_id = ?";
    String clientbatchmanager_sendtoall_sql = "update instrumentation set clientbatchmanager_sendtoall = ? where request_id = ? and replica_id = ?";
    String clientbatchmanager_batchsent_sql = "update instrumentation set clientbatchmanager_batchsent = ? where request_id = ? and replica_id = ?";
    String clientbatchmanager_onforwardclientbatch_sql = "update instrumentation set clientbatchmanager_onforwardclientbatch = ? where request_id = ? and replica_id = ?";
    String paxos_enqueuerequest_sql = "update instrumentation set paxos_enqueuerequest = ? where request_id = ? and replica_id = ?";
    String proposerimpl_propose_sql = "update instrumentation set proposerimpl_propose = ? where request_id = ? and replica_id = ?";
    String learner_oncccept_sql = "update instrumentation set learner_oncccept = ? where request_id = ? and replica_id = ?";
    String acceptor_onpropose_sql = "update instrumentation set acceptor_onpropose = ? where request_id = ? and replica_id = ?";
    String paxos_decide_sql = "update instrumentation set paxos_decide = ? where request_id = ? and replica_id = ?";
    String decidedcallbackimpl_onrequestordered_sql = "update instrumentation set decidedcallbackimpl_onrequestordered = ? where request_id = ? and replica_id = ?";
    String decidedcallbackimpl_executerequests_sql = "update instrumentation set decidedcallbackimpl_executerequests = ? where request_id = ? and replica_id = ?";
    String replica_executeclientrequest_sql = "update instrumentation set replica_executeclientrequest = ? where request_id = ? and replica_id = ?";
    String service_execute_start_sql = "update instrumentation set service_execute_start = ? where request_id = ? and replica_id = ?";
    String service_execute_finish_sql = "update instrumentation set service_execute_finish = ? where request_id = ? and replica_id = ?";
    String clientrequestmanager_onrequestexecuted_sql = "update instrumentation set clientrequestmanager_onrequestexecuted = ? where request_id = ? and replica_id = ?";
    String nioclientproxy_sent_sql = "update instrumentation set nioclientproxy_sent = ? where request_id = ? and replica_id = ?";
    String clientrequestbatcher_sendbatch_sql = "update instrumentation set clientrequestbatcher_sendbatch = ? where request_id = ? and replica_id = ?";

    public static final Object lock = new Object();

    static final Logger logger = Logger.getLogger(ReplicaRequestTimelines.class.getCanonicalName());

    public static HashMap<ClientBatchID, List<FlowPointData>> batchFlowMap = new HashMap<ClientBatchID, List<FlowPointData>>();
    public static HashMap<RequestId, List<FlowPointData>> requestFlowMap = new HashMap<RequestId, List<FlowPointData>>();

    public static List<RequestId> finishedRequestIds = new ArrayList<RequestId>();
    public static HashMap<RequestId, String> requestIdNameMap = new HashMap<RequestId, String>();

    public static Long skew = (long) 0;
    private final Properties configuration = new Properties();

    public static void addFlowPoint(ClientBatchID clientBatchID, FlowPointData data){
        data.setTimestamp(data.getTimestamp() - skew);
        List<FlowPointData> fdp = batchFlowMap.get(clientBatchID);
        if (fdp == null) {
            fdp = new ArrayList<FlowPointData>();
        }
        fdp.add(data);
        batchFlowMap.put(clientBatchID, fdp);
    }

    public static void addRequest(RequestId requestId, String request) {
        requestIdNameMap.put(requestId, request);
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

    public void logFLowPoints(RequestId requestId){
        Connection connection = null;
        PreparedStatement preparedStatement = null;


        String url = "jdbc:postgresql://" + configuration.getProperty("db.instrumentation");
        String user = "postgres";
        String password = "password";
        String replicaId = "";
        if (ProcessDescriptor.getInstance() != null) {
            replicaId = String.valueOf(ProcessDescriptor.getInstance().localId);
        } else {
            replicaId = "client";
        }

        try {
            connection = DriverManager.getConnection(url, user, password);
            List<FlowPointData> flowPointData = requestFlowMap.get(requestId);
            for (FlowPointData flowPoint : flowPointData) {
                if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.Client_Send_Request))
                    preparedStatement = connection.prepareStatement(client_send_request_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.Client_Receive_Reply))
                    preparedStatement = connection.prepareStatement(client_receive_reply_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.NioClientProxy_Execute))
                    preparedStatement = connection.prepareStatement(nioclientproxy_execute_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.ClientBatchManager_SendToAll))
                    preparedStatement = connection.prepareStatement(clientbatchmanager_sendtoall_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.ClientBatchManager_BatchSent))
                    preparedStatement = connection.prepareStatement(clientbatchmanager_batchsent_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.ClientBatchManager_OnForwardClientBatch))
                    preparedStatement = connection.prepareStatement(clientbatchmanager_onforwardclientbatch_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.Paxos_EnqueueRequest))
                    preparedStatement = connection.prepareStatement(paxos_enqueuerequest_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.ProposerImpl_Propose))
                    preparedStatement = connection.prepareStatement(proposerimpl_propose_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.Learner_OnAccept))
                    preparedStatement = connection.prepareStatement(learner_oncccept_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.Acceptor_OnPropose))
                    preparedStatement = connection.prepareStatement(acceptor_onpropose_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.Paxos_Decide))
                    preparedStatement = connection.prepareStatement(paxos_decide_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.DecidedCallbackImpl_onRequestOrdered))
                    preparedStatement = connection.prepareStatement(decidedcallbackimpl_onrequestordered_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.DecidedCallbackImpl_ExecuteRequests))
                    preparedStatement = connection.prepareStatement(decidedcallbackimpl_executerequests_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.Replica_ExecuteClientRequest))
                    preparedStatement = connection.prepareStatement(replica_executeclientrequest_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.Service_Execute_Start))
                    preparedStatement = connection.prepareStatement(service_execute_start_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.Service_Execute_Finish))
                    preparedStatement = connection.prepareStatement(service_execute_finish_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.ClientRequestManager_OnRequestExecuted))
                    preparedStatement = connection.prepareStatement(clientrequestmanager_onrequestexecuted_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.NioClientProxy_Sent))
                    preparedStatement = connection.prepareStatement(nioclientproxy_sent_sql);
                else if (flowPoint.getFlowPoint().equals(FlowPointData.FlowPoint.ClientRequestBatcher_SendBatch))
                    preparedStatement = connection.prepareStatement(clientrequestbatcher_sendbatch_sql);
                else {
                    System.out.println("Instrumentation logging error: No matching flowpoint found");
                    return;
                }
                preparedStatement.setString(1, String.valueOf(flowPoint.getTimestamp()));
                preparedStatement.setString(2, requestId.toString());
                preparedStatement.setString(3, replicaId);

                preparedStatement.addBatch();
                logger.info("*******" + flowPoint.toString() + "*******");
            }
            preparedStatement.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        requestFlowMap.remove(requestId);
    }

    @Override
    public void run() {
        logger.info("******* Replica Request Timelines started *******");
        Connection connection = null;
        PreparedStatement preparedStatement = null;

        logger.info("***** opening properties file ****");
        FileInputStream fis = null;
        try {
            fis = new FileInputStream("paxos.properties");
            configuration.load(fis);
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String url = "jdbc:postgresql://" + configuration.getProperty("db.instrumentation");
        String user = "postgres";
        String password = "password";
        String replicaId = "";
        if (ProcessDescriptor.getInstance() != null) {
            replicaId = String.valueOf(ProcessDescriptor.getInstance().localId);
        } else {
            replicaId = "client";
        }

        try {
            connection = DriverManager.getConnection(url, user, password);
            while (true) {
                synchronized (ReplicaRequestTimelines.lock) {
                    for (RequestId finishedRequestId : finishedRequestIds) {
                        preparedStatement = connection.prepareStatement(initSql);
                        preparedStatement.setString(1, finishedRequestId.toString());
                        preparedStatement.setString(2, replicaId);
                        logger.info("********************************************");
                        logger.info("****** Replica Request Id: " + finishedRequestId.toString() + " ******");
                        logFLowPoints(finishedRequestId);
                        logger.info("********************************************");
                    }
                    /* All logged, clear*/
                }
                synchronized (ReplicaRequestTimelines.lock) {
                    finishedRequestIds = new ArrayList<RequestId>();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
