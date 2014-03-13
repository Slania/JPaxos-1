package lsr.paxos.test.statistics;

import lsr.common.ProcessDescriptor;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;

public class ResultsInterpreter {

    private Connection connection = null;
    private final Properties configuration = new Properties();

    String migrationAgentAckSql = "SELECT distinct request_id from instrumentation where request like 'Ack from migration agent: %'";
    String migrationInitiationSql = "SELECT distinct request_id from instrumentation where request like 'New Object %'";
    String migratedSql = "SELECT distinct request_id from instrumentation where request like 'Object %'";
    String updateMigrationTimestampSql = "SELECT distinct request_id from instrumentation where request like 'Updated migration timestamp %'";
    String directoryAcksSql = "SELECT distinct request_id from instrumentation where request like 'Directory acks for object %'";

    String logsSql = "SELECT replica_id, client_send_request, client_receive_reply, nioclientproxy_execute, clientbatchmanager_sendtoall, clientbatchmanager_batchsent, clientbatchmanager_onforwardclientbatch," +
            "paxos_enqueuerequest, proposerimpl_propose, learner_oncccept, acceptor_onpropose, paxos_decide, decidedcallbackimpl_onrequestordered, decidedcallbackimpl_executerequests, replica_executeclientrequest," +
            "service_execute_start, service_execute_finish, clientrequestmanager_onrequestexecuted, nioclientproxy_sent, clientrequestbatcher_sendbatch, request FROM instrumentation WHERE request_id = ?";


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

        if (connection == null) {
            try {
                connection = DriverManager.getConnection(url, user, password);
                connection.setAutoCommit(false);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        PreparedStatement preparedStatement = null;

        preparedStatement = connection.prepareStatement(migrationAgentAckSql);
        ResultSet requestIDs = preparedStatement.executeQuery();
        processRound("migration_agent_acks", requestIDs);

        preparedStatement = connection.prepareStatement(migrationInitiationSql);
        requestIDs = preparedStatement.executeQuery();
        processRound("migration_initiation", requestIDs);

        preparedStatement = connection.prepareStatement(migratedSql);
        requestIDs = preparedStatement.executeQuery();
        processRound("migrated", requestIDs);

        preparedStatement = connection.prepareStatement(updateMigrationTimestampSql);
        requestIDs = preparedStatement.executeQuery();
        processRound("updated_timestamp", requestIDs);

        preparedStatement = connection.prepareStatement(directoryAcksSql);
        requestIDs = preparedStatement.executeQuery();
        processRound("directory_acks", requestIDs);

    }

    private void processRound(String roundName, ResultSet requestIDs) throws SQLException {
        PreparedStatement preparedStatement;
        while (requestIDs.next()) {
            preparedStatement = connection.prepareStatement(logsSql);
            preparedStatement.setString(1, requestIDs.getString("request_id"));
            ResultSet logs = preparedStatement.executeQuery();
            String replicaId;
            Long clientSendRequest, clientReceiveReply, nioClientProxyExecute, paxosEnqueueRequest, paxosDecide, serviceExecuteStart, serviceExecuteFinish, nioClientProxySent;
            String clientSendRequest_s, clientReceiveReply_s, nioClientProxyExecute_s, paxosEnqueueRequest_s, paxosDecide_s, serviceExecuteStart_s, serviceExecuteFinish_s, nioClientProxySent_s, request;
            ArrayList<String> clientTimes = new ArrayList<String>();
            ArrayList<String> leaderTimes = new ArrayList<String>();

            while (logs.next()) {

                replicaId = logs.getString("replica_id");
                if (logs.wasNull()) {
                    replicaId = "unknown replica";
                }
                request = logs.getString("request");
                if (logs.wasNull()) {
                    request = "unknown request";
                }

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

                if (clientSendRequest != -1 && clientReceiveReply != -1) {
                    clientTimes.add(String.valueOf(clientReceiveReply - clientSendRequest));
                }
                if (paxosEnqueueRequest != -1 && paxosDecide != -1) {
                    leaderTimes.add(String.valueOf(paxosDecide - paxosEnqueueRequest));
                }

            }

            writeTimesToFile(roundName, "client", clientTimes);
            writeTimesToFile(roundName, "leader", leaderTimes);
        }
    }

    private void writeTimesToFile(String roundName, String reportingReplica, ArrayList<String> times) {
        File file = new File(roundName + reportingReplica + ".txt");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
            BufferedWriter bw = new BufferedWriter(fw);
            for (String time : times) {
                bw.write(time);
                bw.newLine();
            }
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws SQLException {
        ResultsInterpreter resultsInterpreter = new ResultsInterpreter();
        resultsInterpreter.start();
    }
}
