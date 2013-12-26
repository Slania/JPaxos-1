package lsr.paxos.test.directory;

import lsr.common.ProcessDescriptor;
import lsr.service.SimplifiedService;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DirectoryService extends SimplifiedService {

    private Socket socket;

    private final Properties configuration = new Properties();

    private HashMap<DirectoryServiceCommand, Boolean> map = new HashMap<DirectoryServiceCommand, Boolean>();
    private static final int BATCH_EXECUTE_SIZE = 100;

    protected byte[] execute(byte[] value) {
        logger.info("***** opening properties file ****");
        FileInputStream fis = null;
        try {
            fis = new FileInputStream("paxos.properties");
            configuration.load(fis);
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.info("******** in execute method of DirectoryService at time: " + System.currentTimeMillis() + " ********");
        DirectoryServiceCommand command;
        try {
            command = new DirectoryServiceCommand(value);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Incorrect request", e);
            return null;
        }

        switch (command.getDirectoryCommandType()) {
            case INSERT: {
                Boolean migrationStatus = command.isMigrationComplete();
                map.put(command, migrationStatus);

                ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
                DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);

                logger.info(command.toString());

                Connection connection = null;
                PreparedStatement preparedStatement = null;

                String url = "jdbc:postgresql://" + configuration.getProperty("db." + ProcessDescriptor.getInstance().localId);
                String user = "postgres";
                String password = "password";

                try {
                    connection = DriverManager.getConnection(url, user, password);
                    String sql = "INSERT INTO migrations(object_id, old_replica_set, new_replica_set, migration_complete, creation_time) VALUES(?, ?, ?, ?, ?)";
                    preparedStatement = connection.prepareStatement(sql);
                    preparedStatement.setString(1, new String(command.getObjectId()));
                    preparedStatement.setString(2, command.getOldReplicaSetAsCsv());
                    preparedStatement.setString(3, command.getNewReplicaSetAsCsv());
                    preparedStatement.setBoolean(4, command.isMigrationComplete());
                    preparedStatement.setTimestamp(5, Timestamp.valueOf(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd kk:mm:ss"))));
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }

                try {
                    dataOutput.write(command.toString().getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                return byteArrayOutput.toByteArray();
            }

            case UPDATE_MIGRATION_COMPLETE: {
                ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
                DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);

                Connection connection = null;
                PreparedStatement preparedStatement = null;

                String url = "jdbc:postgresql://" + configuration.getProperty("db." + ProcessDescriptor.getInstance().localId);
                String user = "postgres";
                String password = "password";

                try {
                    connection = DriverManager.getConnection(url, user, password);
                    String sql = "UPDATE migrations SET migration_acks = ?, migration_complete = ? where object_id = ?";
                    preparedStatement = connection.prepareStatement(sql);
                    preparedStatement.setString(1, new String(command.getMigrationAcks()));
                    preparedStatement.setBoolean(2, command.isMigrationComplete());
                    preparedStatement.setString(3, new String(command.getObjectId()));
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }

                try {
                    dataOutput.writeInt(1);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                return byteArrayOutput.toByteArray();
            }

            case UPDATE_MIGRATED: {
                ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
                DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);

                Connection connection = null;
                PreparedStatement preparedStatement = null;

                String url = "jdbc:postgresql://" + configuration.getProperty("db." + ProcessDescriptor.getInstance().localId);
                String user = "postgres";
                String password = "password";

                try {
                    connection = DriverManager.getConnection(url, user, password);
                    String sql = "UPDATE migrations SET migrated = ? where object_id = ?";
                    preparedStatement = connection.prepareStatement(sql);
                    preparedStatement.setBoolean(1, command.isMigrated());
                    preparedStatement.setString(2, new String(command.getObjectId()));
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }

                try {
                    dataOutput.writeInt(1);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                return byteArrayOutput.toByteArray();
            }

            case UPDATE_MIGRATION_TIMESTAMP: {
                ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
                DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);

                Connection connection = null;
                PreparedStatement preparedStatement = null;

                String url = "jdbc:postgresql://" + configuration.getProperty("db." + ProcessDescriptor.getInstance().localId);
                String user = "postgres";
                String password = "password";

                try {
                    connection = DriverManager.getConnection(url, user, password);
                    String sql = "UPDATE migrations SET migration_started_timestamp = ? where object_id = ?";
                    preparedStatement = connection.prepareStatement(sql);
                    preparedStatement.setTimestamp(1, Timestamp.valueOf(new String(command.getMigrationTimestamp())));
                    preparedStatement.setString(2, new String(command.getObjectId()));
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }

                try {
                    dataOutput.writeInt(1);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                return byteArrayOutput.toByteArray();
            }

            case READ: {
                ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
                DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);

                Connection connection = null;
                PreparedStatement preparedStatement = null;

                String url = "jdbc:postgresql://" + configuration.getProperty("db." + ProcessDescriptor.getInstance().localId);
                String user = "postgres";
                String password = "password";

                try {
                    connection = DriverManager.getConnection(url, user, password);
                    String sql = "SELECT object_id, old_replica_set, new_replica_set, migration_acks, migration_complete from migrations where object_id = ?";
                    preparedStatement = connection.prepareStatement(sql);
                    preparedStatement.setString(1, new String(command.getObjectId()));
                    ResultSet rs = preparedStatement.executeQuery();

                    while (rs.next()) {
                        String response = "";
                        response += "Object " + rs.getString(1) + " migrating from " + rs.getString(2) + " to " + rs.getString(3) + ". Acks received from directories: " + rs.getString(4)+ ". Migration status: " + rs.getBoolean(5);
                        try {
                            dataOutput.write(response.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }

                        return byteArrayOutput.toByteArray();
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            case REGISTER_DIRECTORY: {
                ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
                DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);

                Connection connection = null;
                PreparedStatement preparedStatement = null;

                String url = "jdbc:postgresql://" + configuration.getProperty("db." + ProcessDescriptor.getInstance().localId);
                String user = "postgres";
                String password = "password";

                try {
                    connection = DriverManager.getConnection(url, user, password);
                    String update = "UPDATE directories SET time_stamp = ? WHERE ip = ? AND port = ?";
                    String insert = "INSERT INTO directories(ip, port, time_stamp) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM directories WHERE ip = ? AND port = ?)";
                    preparedStatement = connection.prepareStatement(update);
                    preparedStatement.setTimestamp(1, Timestamp.valueOf(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd kk:mm:ss"))));
                    preparedStatement.setString(2, new String(command.getDirectoryNodeIP()));
                    preparedStatement.setInt(3, command.getDirectoryNodePort());
                    preparedStatement.executeUpdate();
                    preparedStatement = connection.prepareStatement(insert);
                    preparedStatement.setString(1, new String(command.getDirectoryNodeIP()));
                    preparedStatement.setInt(2, command.getDirectoryNodePort());
                    preparedStatement.setTimestamp(3, Timestamp.valueOf(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd kk:mm:ss"))));
                    preparedStatement.setString(4, new String(command.getDirectoryNodeIP()));
                    preparedStatement.setInt(5, command.getDirectoryNodePort());
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }

                try {
                    dataOutput.write("received directory command".getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                return byteArrayOutput.toByteArray();
            }

            case REGISTER_MIGRATION_AGENT: {
                ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
                DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);

                Connection connection = null;
                PreparedStatement preparedStatement = null;

                String url = "jdbc:postgresql://" + configuration.getProperty("db." + ProcessDescriptor.getInstance().localId);
                String user = "postgres";
                String password = "password";

                try {
                    connection = DriverManager.getConnection(url, user, password);
                    String update = "UPDATE migration_agents SET time_stamp = ? WHERE ip = ? AND port = ?";
                    String insert = "INSERT INTO migration_agents(ip, port, time_stamp) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM migration_agents WHERE ip = ? AND port = ?)";
                    preparedStatement = connection.prepareStatement(update);
                    preparedStatement.setTimestamp(1, Timestamp.valueOf(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd kk:mm:ss"))));
                    preparedStatement.setString(2, new String(command.getDirectoryNodeIP()));
                    preparedStatement.setInt(3, command.getDirectoryNodePort());
                    preparedStatement.executeUpdate();
                    preparedStatement = connection.prepareStatement(insert);
                    preparedStatement.setString(1, new String(command.getDirectoryNodeIP()));
                    preparedStatement.setInt(2, command.getDirectoryNodePort());
                    preparedStatement.setTimestamp(3, Timestamp.valueOf(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd kk:mm:ss"))));
                    preparedStatement.setString(4, new String(command.getDirectoryNodeIP()));
                    preparedStatement.setInt(5, command.getDirectoryNodePort());
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }

                try {
                    dataOutput.write("received migration agent command".getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                return byteArrayOutput.toByteArray();
            }

            case MIGRATION_AGENT_ACK:
                ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
                DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);

                Connection connection = null;
                PreparedStatement preparedStatement = null;

                String url = "jdbc:postgresql://" + configuration.getProperty("db." + ProcessDescriptor.getInstance().localId);
                String user = "postgres";
                String password = "password";

                try {
                    connection = DriverManager.getConnection(url, user, password);
                    String fetchId = "SELECT id FROM migration_agents WHERE ip = ? AND port = ?";
                    String fetchMigration = "SELECT migration_progress_acks, migrated FROM migrations WHERE object_id = ? and migration_complete = false";
                    preparedStatement = connection.prepareStatement(fetchId);
                    preparedStatement.setString(1, new String(command.getDirectoryNodeIP()));
                    preparedStatement.setInt(2, command.getDirectoryNodePort());
                    ResultSet rs = preparedStatement.executeQuery();
                    int migrationAgentId = -1;
                    boolean migrated = false;
                    String migrationProgress = null;
                    while (rs.next()) {
                        migrationAgentId = rs.getInt(1);
                    }
                    preparedStatement = connection.prepareStatement(fetchMigration);
                    preparedStatement.setString(1, new String(command.getObjectId()));
                    rs = preparedStatement.executeQuery();
                    while (rs.next()) {
                        migrationProgress = rs.getString(1);
                        if (rs.wasNull()) {
                            migrationProgress = null;
                        }
                        migrated = rs.getBoolean(2);
                    }
                    System.out.println("Object Id: " + command.getObjectId());
                    System.out.println("Migration Agent IP: " + command.getDirectoryNodeIP());
                    System.out.println("Migration Agent Port: " + command.getDirectoryNodePort());
                    System.out.println("Migration Agent ID: " + migrationAgentId);
                    System.out.println("Migrated: " + migrated);
                    System.out.println("Migration progress: " + migrationProgress);
                    if (!migrated) {
                        if ((migrationProgress != null &&
                            (
                                !migrationProgress.contains("," + migrationAgentId + ",") ||
                                !migrationProgress.contains("," + migrationAgentId) ||
                                !migrationProgress.contains(migrationAgentId + ",") ||
                                (!migrationProgress.contains(",") && !migrationProgress.contains(String.valueOf(migrationAgentId)))
                            ))
                            ||
                            migrationProgress == null
                        ) {
                            if (migrationProgress != null && migrationProgress.contains(",")) {
                                migrationProgress += "," + migrationAgentId;
                            } else {
                                migrationProgress = String.valueOf(migrationAgentId);
                            }
                            logger.info("Migration Acks after update: " + migrationProgress);
                            String sql = "UPDATE migrations SET migration_progress_acks = ? WHERE object_id = ?";
                            preparedStatement = connection.prepareStatement(sql);
                            preparedStatement.setString(1, migrationProgress);
                            preparedStatement.setString(2, new String(command.getObjectId()));
                            preparedStatement.executeUpdate();
                        }
                    }

                    try {
                        dataOutput.write("received migration agent update".getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }

                    return byteArrayOutput.toByteArray();

                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }

        }
        return null;
    }

    protected byte[] makeSnapshot() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            DataOutputStream dataOutputStream = new DataOutputStream(stream);
            dataOutputStream.write(ProcessDescriptor.getInstance().localId);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return stream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    protected void updateToSnapshot(byte[] snapshot) {
        ByteArrayInputStream stream = new ByteArrayInputStream(snapshot);
        DataInputStream dataInputStream;
        try {
            dataInputStream = new DataInputStream(stream);
            int masterDB = dataInputStream.readInt();
            restoreFromMaster(masterDB);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void restoreFromMaster(int masterDB) {
        Connection sourceConnection = null;
        Connection destinationConnection = null;

        PreparedStatement selectStatement = null;
        PreparedStatement insertStatement = null;
        PreparedStatement deleteStatement = null;

        ResultSet resultSet = null;

        try
        {
            String destUrl = "jdbc:postgresql://" + configuration.getProperty("db." + ProcessDescriptor.getInstance().localId);
            String sourceUrl = "jdbc:postgresql://" + configuration.getProperty("db." + masterDB);
            String user = "postgres";
            String password = "password";

            sourceConnection = DriverManager.getConnection(sourceUrl, user, password);
            destinationConnection = DriverManager.getConnection(destUrl, user, password);

            String tableName = "migrations";

            selectStatement = sourceConnection.prepareStatement("SELECT * FROM " + tableName);
            deleteStatement = sourceConnection.prepareStatement("DELETE FROM" + tableName);
            resultSet = selectStatement.executeQuery();

            insertStatement = destinationConnection.prepareStatement(createInsertSql(resultSet.getMetaData()));

            deleteStatement.execute();

            int batchSize = 0;
            while (resultSet.next())
            {
                setParameters(insertStatement, resultSet);
                insertStatement.addBatch();
                batchSize++;

                if (batchSize >= BATCH_EXECUTE_SIZE)
                {
                    insertStatement.executeBatch();
                    batchSize = 0;
                }
            }

            insertStatement.executeBatch();

            tableName = "directories";

            selectStatement = sourceConnection.prepareStatement("SELECT * FROM " + tableName);
            deleteStatement = sourceConnection.prepareStatement("DELETE FROM" + tableName);
            resultSet = selectStatement.executeQuery();

            insertStatement = destinationConnection.prepareStatement(createInsertSql(resultSet.getMetaData()));

            deleteStatement.execute();

            batchSize = 0;
            while (resultSet.next())
            {
                setParameters(insertStatement, resultSet);
                insertStatement.addBatch();
                batchSize++;

                if (batchSize >= BATCH_EXECUTE_SIZE)
                {
                    insertStatement.executeBatch();
                    batchSize = 0;
                }
            }

            insertStatement.executeBatch();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally
        {
            try {
                resultSet.close();
                insertStatement.close();
                selectStatement.close();
                sourceConnection.close();
                destinationConnection.close();
            } catch (SQLException e) {

            }
        }
    }

    private String createInsertSql(ResultSetMetaData resultSetMetaData) throws SQLException
    {
        StringBuffer insertSql = new StringBuffer("INSERT INTO ");
        StringBuffer values = new StringBuffer(" VALUES (");

        insertSql.append(resultSetMetaData.getTableName(1));

        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++)
        {
            insertSql.append(resultSetMetaData.getColumnName(i));
            values.append("?");

            if (i <= resultSetMetaData.getColumnCount())
            {
                insertSql.append(", ");
                values.append(", ");
            }
            else
            {
                insertSql.append(")");
                values.append(")");
            }
        }

        return insertSql.toString() + values.toString();
    }

    private void setParameters(PreparedStatement preparedStatement, ResultSet resultSet) throws SQLException
    {
        for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++)
        {
            preparedStatement.setObject(i, resultSet.getObject(i));
        }
    }

    private void connectTo() throws IOException {
        // close previous connection if any
        cleanClose();

//        String host = "localhost";
        String host = "localhost";
        int port = 1111;
        logger.info("Connecting to " + host + ":" + port);
        socket = new Socket(host, port);

//        socket.setSoTimeout(Math.min(timeout, MAX_TIMEOUT));
        socket.setSoTimeout(3000);
        socket.setReuseAddress(true);
        socket.setTcpNoDelay(true);
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        DataInputStream input = new DataInputStream(socket.getInputStream());

        logger.info("*****" + "Connected to localhost directory service" + "*****");
    }

    private void cleanClose() {
        try {
            if (socket != null) {
                socket.shutdownOutput();
                socket.close();
                socket = null;
                logger.info("Closing socket");
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.log(Level.WARNING, "Not clean socket closing.");
        }
    }

    private static final Logger logger = Logger.getLogger(DirectoryService.class.getCanonicalName());
}
