package lsr.paxos.test.directory;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class ResultsInterpreter {

    private Connection connection = null;
    private final Properties configuration = new Properties();

    public void start() throws SQLException {

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

        String requestIDSql = "SELECT DISTINCT request_id from instrumentation";
        String logsSql = "SELECT replica_id, client_send_request, client_receive_reply, nioclientproxy_execute, clientbatchmanager_sendtoall, clientbatchmanager_batchsent, clientbatchmanager_onforwardclientbatch," +
                "paxos_enqueuerequest, proposerimpl_propose, learner_oncccept, acceptor_onpropose, paxos_decide, decidedcallbackimpl_onrequestordered, decidedcallbackimpl_executerequests, replica_executeclientrequest," +
                "service_execute_start, service_execute_finish, clientrequestmanager_onrequestexecuted, nioclientproxy_sent, clientrequestbatcher_sendbatch, request FROM instrumentation WHERE request_id = ?";

        if (connection == null) {
            try {
                connection = DriverManager.getConnection(url, user, password);
                connection.setAutoCommit(false);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        PreparedStatement preparedStatement = null;
        preparedStatement = connection.prepareStatement(requestIDSql);
        ResultSet requestIDs = preparedStatement.executeQuery();

        while (requestIDs.next()) {
            preparedStatement = connection.prepareStatement(logsSql);
            preparedStatement.setString(1, requestIDs.getString("request_id"));
            ResultSet logs = preparedStatement.executeQuery();
            String replicaId;
            Long clientSendRequest, clientReceiveReply, nioClientProxyExecute, paxosEnqueueRequest, paxosDecide, serviceExecuteStart, serviceExecuteFinish, nioClientProxySent;
            String clientSendRequest_s, clientReceiveReply_s, nioClientProxyExecute_s, paxosEnqueueRequest_s, paxosDecide_s, serviceExecuteStart_s, serviceExecuteFinish_s, nioClientProxySent_s, request;
            while (logs.next()) {

                replicaId = logs.getString("replica_id");
                if (logs.wasNull()) {
                    replicaId = "unknown replica";
                }
                request = logs.getString("request");
                if (logs.wasNull()) {
                    request = "unknown request";
                }

                System.out.println("REQUEST: " + request);

                clientSendRequest_s = logs.getString("client_send_request");
                if (!logs.wasNull()) {
                    clientSendRequest = Long.valueOf(clientSendRequest_s);
                } else {
                    clientSendRequest = (long) -1;
                }
                clientReceiveReply_s = logs.getString("client_receive_reply");
                if (!logs.wasNull()) {
                    clientReceiveReply = Long.valueOf(clientReceiveReply_s);
                } else {
                    clientReceiveReply = (long) -1;
                }
                nioClientProxyExecute_s = logs.getString("nioclientproxy_execute");
                if (!logs.wasNull()) {
                    nioClientProxyExecute = Long.valueOf(nioClientProxyExecute_s);
                } else {
                    nioClientProxyExecute = (long) -1;
                }
                paxosEnqueueRequest_s = logs.getString("paxos_enqueuerequest");
                if (!logs.wasNull()) {
                    paxosEnqueueRequest = Long.valueOf(paxosEnqueueRequest_s);
                } else {
                    paxosEnqueueRequest = (long) -1;
                }
                paxosDecide_s = logs.getString("paxos_decide");
                if (!logs.wasNull()) {
                    paxosDecide = Long.valueOf(paxosDecide_s);
                } else {
                    paxosDecide = (long) -1;
                }
                serviceExecuteStart_s = logs.getString("service_execute_start");
                if (!logs.wasNull()) {
                    serviceExecuteStart = Long.valueOf(serviceExecuteStart_s);
                } else {
                    serviceExecuteStart = (long) -1;
                }
                serviceExecuteFinish_s = logs.getString("service_execute_finish");
                if (!logs.wasNull()) {
                    serviceExecuteFinish = Long.valueOf(serviceExecuteFinish_s);
                } else {
                    serviceExecuteFinish = (long) -1;
                }
                nioClientProxySent_s = logs.getString("nioclientproxy_sent");
                if (!logs.wasNull()) {
                    nioClientProxySent = Long.valueOf(nioClientProxySent_s);
                } else {
                    nioClientProxySent = (long) -1;
                }

                System.out.println("Machine: " + replicaId);
                if (clientSendRequest != -1 && clientReceiveReply != -1) {
                    System.out.println("Full client side round trip: " + (clientReceiveReply - clientSendRequest));
                }
                if (nioClientProxyExecute != -1 && nioClientProxySent != -1) {
                    System.out.println("Full server side round trip: " + (clientReceiveReply - clientSendRequest));
                }
                if (paxosEnqueueRequest != -1 && paxosDecide != -1) {
                    System.out.println("Paxos end to end: " + (paxosDecide - paxosEnqueueRequest));
                }
                if (serviceExecuteStart != -1 && serviceExecuteFinish != -1) {
                    System.out.println("Service time: " + (serviceExecuteFinish - serviceExecuteStart));
                }

            }

            System.out.println("-----------------------------------------------------------------------");
        }

    }

    public static void main(String[] args) throws SQLException {
        ResultsInterpreter resultsInterpreter = new ResultsInterpreter();
        resultsInterpreter.start();
    }
}
