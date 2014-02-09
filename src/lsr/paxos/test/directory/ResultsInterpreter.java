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
            Integer clientSendRequest, clientReceiveReply, nioClientProxyExecute, paxosEnqueueRequest, paxosDecide, serviceExecuteStart, serviceExecuteFinish, nioClientProxySent;
            String clientSendRequest_s, clientReceiveReply_s, nioClientProxyExecute_s, paxosEnqueueRequest_s, paxosDecide_s, serviceExecuteStart_s, serviceExecuteFinish_s, nioClientProxySent_s;
            while (logs.next()) {
                replicaId = logs.getString("replica_id");
                if (logs.wasNull()) {
                    replicaId = "unknown replica";
                }
                clientSendRequest_s = logs.getString("client_send_request");
                if (!logs.wasNull()) {
                    clientSendRequest = Integer.valueOf(clientSendRequest_s);
                } else {
                    clientSendRequest = -1;
                }
                clientReceiveReply_s = logs.getString("client_receive_reply");
                if (!logs.wasNull()) {
                    clientReceiveReply = Integer.valueOf(clientReceiveReply_s);
                } else {
                    clientReceiveReply = -1;
                }
                nioClientProxyExecute_s = logs.getString("nioclientproxy_execute");
                if (!logs.wasNull()) {
                    nioClientProxyExecute = Integer.valueOf(nioClientProxyExecute_s);
                } else {
                    nioClientProxyExecute = -1;
                }
                paxosEnqueueRequest_s = logs.getString("paxos_enqueuerequest");
                if (!logs.wasNull()) {
                    paxosEnqueueRequest = Integer.valueOf(paxosEnqueueRequest_s);
                } else {
                    paxosEnqueueRequest = -1;
                }
                paxosDecide = Integer.valueOf(logs.getString("paxos_decide"));
                if (!logs.wasNull()) {
                    paxosDecide = -1;
                } else {
                    paxosDecide = -1;
                }
                serviceExecuteStart_s = logs.getString("service_execute_start");
                if (!logs.wasNull()) {
                    serviceExecuteStart = Integer.valueOf(serviceExecuteStart_s);
                } else {
                    serviceExecuteStart = -1;
                }
                serviceExecuteFinish_s = logs.getString("service_execute_finish");
                if (logs.wasNull()) {
                    serviceExecuteFinish = Integer.valueOf(serviceExecuteFinish_s);
                } else {
                    serviceExecuteFinish = -1;
                }
                nioClientProxySent_s = logs.getString("nioclientproxy_sent");
                if (!logs.wasNull()) {
                    nioClientProxySent = Integer.valueOf(nioClientProxySent_s);
                } else {
                    nioClientProxySent = -1;
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
        }

    }

    public static void main(String[] args) throws SQLException {
        ResultsInterpreter resultsInterpreter = new ResultsInterpreter();
        resultsInterpreter.start();
    }
}
