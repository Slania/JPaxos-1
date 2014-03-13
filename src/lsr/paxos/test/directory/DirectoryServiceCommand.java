package lsr.paxos.test.directory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DirectoryServiceCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<Integer> oldReplicaSet;
    private List<Integer> newReplicaSet;
    private DirectoryCommandType directoryCommandType;
    private byte[] objectId;
    private byte[] directoryNodeIP;
    private int directoryNodePort;
    private boolean migrationComplete;
    private boolean migrated;
    private byte[] migrationAcks;
    private byte[] migrationTimestamp;

    public enum DirectoryCommandType {
        INSERT, DELETE, READ, UPDATE_MIGRATION_COMPLETE, UPDATE_MIGRATION_TIMESTAMP, REGISTER_DIRECTORY, REGISTER_MIGRATION_AGENT, MIGRATION_AGENT_ACK, UPDATE_MIGRATED
    }

    public DirectoryServiceCommand(List<Integer> oldReplicaSet, List<Integer> newReplicaSet, DirectoryCommandType directoryCommandType, String objectId, boolean migrationComplete) {
        this.oldReplicaSet = oldReplicaSet;
        this.newReplicaSet = newReplicaSet;
        this.directoryCommandType = directoryCommandType;
        this.migrationComplete = migrationComplete;
        this.objectId = objectId.getBytes();
    }

    //UPDATE_MIGRATION_COMPLETE
    public DirectoryServiceCommand(String objectId, boolean migrationComplete, String migrationAcks, DirectoryCommandType directoryCommandType) {
        this.objectId = objectId.getBytes();
        this.migrationComplete = migrationComplete;
        this.migrationAcks = migrationAcks.getBytes();
        this.directoryCommandType = directoryCommandType;
    }

    //UPDATE_MIGRATED
    public DirectoryServiceCommand(String objectId, boolean migrated, DirectoryCommandType directoryCommandType) {
        this.objectId = objectId.getBytes();
        this.migrated = migrated;
        this.directoryCommandType = directoryCommandType;
    }

    //UPDATE_MIGRATION_TIMESTAMP
    public DirectoryServiceCommand(String objectId, String migrationTimestamp, DirectoryCommandType directoryCommandType) {
        this.objectId = objectId.getBytes();
        this.migrationTimestamp = migrationTimestamp.getBytes();
        this.directoryCommandType = directoryCommandType;
    }

    //READ/DELETE
    public DirectoryServiceCommand(String objectId, DirectoryCommandType directoryCommandType) {
        this.objectId = objectId.getBytes();
        this.directoryCommandType = directoryCommandType;
    }

    //REGISTER
    public DirectoryServiceCommand(byte[] directoryNodeIP, int directoryNodePort, DirectoryCommandType directoryCommandType) {
        this.directoryNodeIP = directoryNodeIP;
        this.directoryNodePort = directoryNodePort;
        this.directoryCommandType = directoryCommandType;
    }

    //MIGRATION_AGENT_ACK
    public DirectoryServiceCommand(byte[] directoryNodeIP, int directoryNodePort, DirectoryCommandType directoryCommandType, String objectId) {
        this.directoryNodeIP = directoryNodeIP;
        this.directoryNodePort = directoryNodePort;
        this.objectId = objectId.getBytes();
        this.directoryCommandType = directoryCommandType;
    }

    //INSERT
    public DirectoryServiceCommand(List<Integer> oldReplicaSet, List<Integer> newReplicaSet, DirectoryCommandType directoryCommandType, String objectId) {
        this.oldReplicaSet = oldReplicaSet;
        this.newReplicaSet = newReplicaSet;
        this.directoryCommandType = directoryCommandType;
        this.objectId = objectId.getBytes();
        //will not be read/used
        migrationComplete = false;
    }

    public DirectoryServiceCommand(byte[] bytes) throws IOException {
        DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(bytes));
        this.directoryCommandType = DirectoryCommandType.values()[dataInput.readInt()];
        System.out.println("Directory Command Type: " + directoryCommandType.toString());

        switch (directoryCommandType) {
            case REGISTER_DIRECTORY: {
                int directoryIpLength = dataInput.readInt();
                directoryNodeIP = new byte[directoryIpLength];
                dataInput.readFully(directoryNodeIP, 0, directoryIpLength);
                directoryNodePort = dataInput.readInt();
                break;
            }
            case REGISTER_MIGRATION_AGENT: {
                int directoryIpLength = dataInput.readInt();
                directoryNodeIP = new byte[directoryIpLength];
                dataInput.readFully(directoryNodeIP, 0, directoryIpLength);
                directoryNodePort = dataInput.readInt();
                break;
            }
            case MIGRATION_AGENT_ACK: {
                int directoryIpLength = dataInput.readInt();
                directoryNodeIP = new byte[directoryIpLength];
                dataInput.readFully(directoryNodeIP, 0, directoryIpLength);
                directoryNodePort = dataInput.readInt();
                int objectIdLength = dataInput.readInt();
                objectId = new byte[objectIdLength];
                dataInput.readFully(objectId, 0, objectIdLength);
                break;
            }
            case INSERT: {
                int objectIdLength = dataInput.readInt();
                System.out.println("ObjectId Length: " + objectIdLength);
                int oldReplicaSetSize = dataInput.readInt();
                System.out.println("Old Replica Set Size: " + oldReplicaSetSize);
                int newReplicaSetSize = dataInput.readInt();
                System.out.println("New Replica Set Size: " + newReplicaSetSize);
                objectId = new byte[objectIdLength];
                dataInput.readFully(objectId, 0, objectIdLength);
                System.out.println("Object Id: " + new String(objectId));
                oldReplicaSet = new ArrayList<Integer>();
                newReplicaSet = new ArrayList<Integer>();
                if (oldReplicaSetSize > 0) {
                    for (int i = 1; i <= oldReplicaSetSize; i++) {
                        oldReplicaSet.add(dataInput.readInt());
                    }
                }
                System.out.println("Size of old replica list: " + oldReplicaSet.size());
                if (newReplicaSetSize > 0) {
                    for (int i = 1; i <= newReplicaSetSize; i++) {
                        newReplicaSet.add(dataInput.readInt());
                    }
                }
                System.out.println("Size of new replica list: " + newReplicaSet.size());
                migrationComplete = dataInput.readByte() == 1;
                System.out.println("Migration complete: " + migrationComplete);
                break;
            }
            case UPDATE_MIGRATION_COMPLETE: {
                int objectIdLength = dataInput.readInt();
                System.out.println("ObjectId Length: " + objectIdLength);
                int migrationAcksLength = dataInput.readInt();
                System.out.println("MigrationAcks Length: " + migrationAcksLength);
                objectId = new byte[objectIdLength];
                dataInput.readFully(objectId, 0, objectIdLength);
                System.out.println("Object Id: " + new String(objectId));
                migrationAcks = new byte[migrationAcksLength];
                dataInput.readFully(migrationAcks, 0, migrationAcksLength);
                System.out.println("Migration Acks: " + new String(migrationAcks));
                migrationComplete = dataInput.readByte() == 1;
                System.out.println("Migration complete: " + migrationComplete);
                break;
            }
            case UPDATE_MIGRATED: {
                int objectIdLength = dataInput.readInt();
                System.out.println("ObjectId Length: " + objectIdLength);
                objectId = new byte[objectIdLength];
                dataInput.readFully(objectId, 0, objectIdLength);
                migrated = dataInput.readByte() == 1;
                System.out.println("Migrated: " + migrated);
                break;
            }
            case UPDATE_MIGRATION_TIMESTAMP: {
                int objectIdLength = dataInput.readInt();
                System.out.println("ObjectId Length: " + objectIdLength);
                int migrationTimestampLength = dataInput.readInt();
                System.out.println("MigrationTimestamp Length: " + migrationTimestampLength);
                objectId = new byte[objectIdLength];
                dataInput.readFully(objectId, 0, objectIdLength);
                System.out.println("Object Id: " + new String(objectId));
                migrationTimestamp = new byte[migrationTimestampLength];
                dataInput.readFully(migrationTimestamp, 0, migrationTimestampLength);
                System.out.println("Migration Timestamp: " + new String(migrationTimestamp));
                break;
            }
            case DELETE:
            case READ: {
                int objectIdLength = dataInput.readInt();
                System.out.println("ObjectId Length: " + objectIdLength);
                objectId = new byte[objectIdLength];
                dataInput.readFully(objectId, 0, objectIdLength);
                System.out.println("Object Id: " + new String(objectId));
                break;
            }
            default: {
                System.out.println("Unknown directory command type");
                throw new ClassCastException();
            }

        }
    }

    public List<Integer> getOldReplicaSet() {
        return oldReplicaSet;
    }

    public List<Integer> getNewReplicaSet() {
        return newReplicaSet;
    }

    public byte[] getDirectoryNodeIP() {
        return directoryNodeIP;
    }

    public int getDirectoryNodePort() {
        return directoryNodePort;
    }

    public byte[] getMigrationAcks() {
        return migrationAcks;
    }

    public byte[] getMigrationTimestamp() {
        return migrationTimestamp;
    }

    public DirectoryCommandType getDirectoryCommandType() {
        return directoryCommandType;
    }

    public byte[] getObjectId() {
        return objectId;
    }

    public boolean isMigrationComplete() {
        return migrationComplete;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public String getOldReplicaSetAsCsv() {
        if (oldReplicaSet.isEmpty()) {
            return "<empty/not-specified>";
        }
        StringBuilder builder = new StringBuilder();

        for (Integer integer : oldReplicaSet) {
            builder.append(integer.toString());
            builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    public String getNewReplicaSetAsCsv() {
        if (newReplicaSet.isEmpty()) {
            return "<empty/not-specified>";
        }
        StringBuilder builder = new StringBuilder();

        for (Integer integer : newReplicaSet) {
            builder.append(integer.toString());
            builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    public byte[] toByteArray() {
        switch (directoryCommandType) {
            case REGISTER_DIRECTORY: {
                //4 for the ordinal of the CommandType
                int numOfBytes = 4 + 4 + directoryNodeIP.length + 4;
                ByteBuffer buffer = ByteBuffer.allocate(numOfBytes);
                buffer.putInt(directoryCommandType.ordinal());
                buffer.putInt(directoryNodeIP.length);
                buffer.put(directoryNodeIP);
                buffer.putInt(directoryNodePort);
                return buffer.array();
            }
            case REGISTER_MIGRATION_AGENT: {
                //4 for the ordinal of the CommandType
                int numOfBytes = 4 + 4 + directoryNodeIP.length + 4;
                ByteBuffer buffer = ByteBuffer.allocate(numOfBytes);
                buffer.putInt(directoryCommandType.ordinal());
                buffer.putInt(directoryNodeIP.length);
                buffer.put(directoryNodeIP);
                buffer.putInt(directoryNodePort);
                return buffer.array();
            }
            case MIGRATION_AGENT_ACK: {
                int numOfBytes = 4 + 4 + directoryNodeIP.length + 4 + 4 + objectId.length;
                ByteBuffer buffer = ByteBuffer.allocate(numOfBytes);
                buffer.putInt(directoryCommandType.ordinal());
                buffer.putInt(directoryNodeIP.length);
                buffer.put(directoryNodeIP);
                buffer.putInt(directoryNodePort);
                buffer.putInt(objectId.length);
                buffer.put(objectId);
                return buffer.array();
            }
            case INSERT: {
                //4 for the ordinal of the CommandType
                int numOfBytes = 4 + (oldReplicaSet.size() * (Integer.SIZE / Byte.SIZE)) + (newReplicaSet.size() * (Integer.SIZE / Byte.SIZE)) + 1 + objectId.length;
                //4 + 4 for the integer sizes of the 2 lists and the integer length of the byte array
                numOfBytes += 4 + 4 + 4;
                ByteBuffer buffer = ByteBuffer.allocate(numOfBytes);
                buffer.putInt(directoryCommandType.ordinal());
                buffer.putInt(objectId.length);
                buffer.putInt(oldReplicaSet.size());
                buffer.putInt(newReplicaSet.size());
                buffer.put(objectId);
                for (Integer integer : oldReplicaSet) {
                    buffer.putInt(integer);
                }
                for (Integer integer : newReplicaSet) {
                    buffer.putInt(integer);
                }
                buffer.put((byte) (migrationComplete ? 1 : 0));
                return buffer.array();
            }
            case UPDATE_MIGRATION_COMPLETE: {
                //4 for the ordinal of the CommandType
                int numOfBytes = 4 + 4 + 4 + objectId.length + migrationAcks.length + 1;
                ByteBuffer buffer = ByteBuffer.allocate(numOfBytes);
                buffer.putInt(directoryCommandType.ordinal());
                buffer.putInt(objectId.length);
                buffer.putInt(migrationAcks.length);
                buffer.put(objectId);
                buffer.put(migrationAcks);
                buffer.put((byte) (migrationComplete ? 1 : 0));
                return buffer.array();
            }
            case UPDATE_MIGRATED: {
                //4 for the ordinal of the CommandType
                int numOfBytes = 4 + 4 + objectId.length + 1;
                ByteBuffer buffer = ByteBuffer.allocate(numOfBytes);
                buffer.putInt(directoryCommandType.ordinal());
                buffer.putInt(objectId.length);
                buffer.put(objectId);
                buffer.put((byte) (migrated ? 1 : 0));
                return buffer.array();
            }
            case UPDATE_MIGRATION_TIMESTAMP: {
                //4 for the ordinal of the CommandType
                int numOfBytes = 4 + 4 + 4 + objectId.length + migrationTimestamp.length;
                ByteBuffer buffer = ByteBuffer.allocate(numOfBytes);
                buffer.putInt(directoryCommandType.ordinal());
                buffer.putInt(objectId.length);
                buffer.putInt(migrationTimestamp.length);
                buffer.put(objectId);
                buffer.put(migrationTimestamp);
                return buffer.array();
            }
            case READ: {
                //4 for the ordinal of the CommandType
                int numOfBytes = 4 + 4 + objectId.length;
                ByteBuffer buffer = ByteBuffer.allocate(numOfBytes);
                buffer.putInt(directoryCommandType.ordinal());
                buffer.putInt(objectId.length);
                buffer.put(objectId);
                return buffer.array();
            }

        }
        return null;
    }

    public String toString() {
        switch (directoryCommandType) {
            case REGISTER_DIRECTORY: {
                return "Register directory: " + new String(directoryNodeIP) + ":" + directoryNodePort;
            }
            case REGISTER_MIGRATION_AGENT: {
                return "Register migration agent: " + new String(directoryNodeIP) + ":" + directoryNodePort;
            }
            case MIGRATION_AGENT_ACK: {
                return "Ack from migration agent: " + new String(directoryNodeIP) + ":" + directoryNodePort + "for object " + new String(objectId);
            }
            case DELETE: {
                return "Delete object" + new String(objectId);
            }
            case READ: {
                return "Read status for object " + new String(objectId);
            }
            case INSERT: {
                return "New Object " + new String(objectId) + " migrating from " + getOldReplicaSetAsCsv() + " to " + getNewReplicaSetAsCsv() + ". Migration status: " + migrationComplete;
            }
            case UPDATE_MIGRATION_COMPLETE: {
                return "Directory acks for object " + new String(objectId) + ":" + new String(migrationAcks) + ", migration complete: " + migrationComplete;
            }
            case UPDATE_MIGRATED: {
                return "Object " + new String(objectId) + " migrated: " + migrated;
            }
            case UPDATE_MIGRATION_TIMESTAMP: {
                return "Updated migration timestamp " +  new String(migrationTimestamp) + " for object " + new String(objectId);
            }
            default:
                return "Unknown directory command type";
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean equals(Object obj) {
        if (this.getClass() != obj.getClass())
            return false;
        if (this == obj)
            return true;
        DirectoryServiceCommand that = (DirectoryServiceCommand) obj;
        return new String(objectId).equals(new String(that.getObjectId()));
    }
}
