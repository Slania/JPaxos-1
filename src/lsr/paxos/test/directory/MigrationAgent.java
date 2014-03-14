package lsr.paxos.test.directory;

import lsr.paxos.client.Client;
import lsr.paxos.client.ReplicationException;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Created with IntelliJ IDEA.
 * User: Sripras
 * Date: 12/21/13
 * Time: 2:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class MigrationAgent {

    private ServerSocketChannel serverSocketChannel;

    private Client client;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(1024);

    private HashMap<String, String> objectReplicaSetMap = new HashMap<String, String>();

    public void start(int port, String hostAddress) throws IOException, ReplicationException {
        Selector selector = null;

        try {
            serverSocketChannel = ServerSocketChannel.open();
            InetSocketAddress address = new InetSocketAddress(hostAddress, port);
            serverSocketChannel.socket().bind(address);
            serverSocketChannel.configureBlocking(false);

            client = new Client(4);
            client.connect();

            DirectoryServiceCommand command = new DirectoryServiceCommand(hostAddress.getBytes(), port, DirectoryServiceCommand.DirectoryCommandType.REGISTER_MIGRATION_AGENT);
            byte[] response = client.execute(command.toByteArray());
            ByteBuffer buffer = ByteBuffer.wrap(response);
            String status = new String(buffer.array());
            logger.info("*********" + status + "*********");

            selector = Selector.open();

            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            outerLoop: while (true) {
                readBuffer.clear();
                selector.select();

                for (Iterator<SelectionKey> i = selector.selectedKeys().iterator(); i.hasNext();) {

                    SelectionKey key = i.next();
                    i.remove();

                    if (key.isConnectable()){
                        ((SocketChannel)key.channel()).finishConnect();
                    }

                    if (key.isAcceptable()) {
                        SocketChannel client = serverSocketChannel.accept();
                        logger.info("New connection from: " + client.socket());
                        client.configureBlocking(false);
                        client.socket().setTcpNoDelay(true);
                        client.register(selector, SelectionKey.OP_READ);
                    }

                    if (key.isReadable()) {
                        int readBytes = 0;
                        while (true) {
                            readBytes += ((SocketChannel)key.channel()).read(readBuffer);
                            logger.info("I read more: " + readBytes + " bytes.");
                            if (readBytes == -1) {
                                ((SocketChannel)key.channel()).close();
                                key.cancel();
                                continue outerLoop;
                            }
                            readBuffer.flip();
                            logger.info("Protocol says message has: " + readBuffer.getInt() + " bytes.");
                            readBuffer.rewind();
                            if (readBytes == readBuffer.getInt()) {
                                //not rewinding buffer because message size needs to be discarded anyway
                                break;
                            } else if (readBytes == 0) {
                                logger.info("Not yet received message fully");
                                continue outerLoop;
                            } else if (readBytes == -1) {
                                ((SocketChannel)key.channel()).close();
                                continue outerLoop;
                            } else {
                                readBuffer.flip();
                            }
                        }

                        if (readBytes == 0) {
                            continue outerLoop;
                        }

                        int objectIdLength = readBuffer.getInt();
                        int newReplicaSetLength = readBuffer.getInt();
                        int oldReplicaSetLength = readBuffer.getInt();

                        logger.info(String.valueOf(objectIdLength));
                        logger.info(String.valueOf(newReplicaSetLength));
                        logger.info(String.valueOf(oldReplicaSetLength));

                        byte[] objectId = new byte[objectIdLength];
                        byte[] newReplicaSet = new byte[newReplicaSetLength];
                        byte[] oldReplicaSet = new byte[oldReplicaSetLength];

                        readBuffer.get(objectId);
                        readBuffer.get(newReplicaSet);
                        readBuffer.get(oldReplicaSet);

                        logger.info(new String(objectId));
                        logger.info(new String(newReplicaSet));
                        logger.info(new String(oldReplicaSet));

                        if (objectReplicaSetMap.get(objectId) != null && objectReplicaSetMap.get(objectId).equals(new String(newReplicaSet) + "->" + new String(oldReplicaSet))) {
                            /* ack immediately, just a repeat message*/
                        } else {
                            objectReplicaSetMap.put(new String(objectId), new String(newReplicaSet) + "->" + new String(oldReplicaSet));

                            for (String object : objectReplicaSetMap.keySet()) {
                                System.out.println("Contents of map:");
                                System.out.println("Object: " + object + ", Replicas: " + objectReplicaSetMap.get(object));
                            }
                            System.out.println("********-------------------------------********");
                            /* wait random time to simulate migration of object */
                            Thread.sleep((long) 5000);
                        }
                        DirectoryServiceCommand ackCommand = new DirectoryServiceCommand(hostAddress.getBytes(), port, DirectoryServiceCommand.DirectoryCommandType.MIGRATION_AGENT_ACK, new String(objectId));
                        response = client.execute(ackCommand.toByteArray());
                        buffer = ByteBuffer.wrap(response);
                        status = new String(buffer.array());
                        logger.info("*********" + status + "*********");
                    }
                }
            }
        } catch (Exception e) {
            logger.info("Directory failure: " + e.getMessage());
        } finally {
            try {
                selector.close();
                serverSocketChannel.socket().close();
                serverSocketChannel.close();
            } catch (Exception e) {
                // do nothing - server failed
            }
        }
    }

    public static void main(String[] args) throws IOException, ReplicationException, InterruptedException {
        MigrationAgent directory = new MigrationAgent();
        if (args.length > 2) {
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface current = interfaces.nextElement();
            System.out.println(current);
            if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
            Enumeration<InetAddress> addresses = current.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress current_addr = addresses.nextElement();
                if (current_addr.isLoopbackAddress()) continue;
                if (current_addr instanceof Inet4Address) {
                    directory.start(port, current_addr.getHostAddress());
                }
            }
        }
    }

    private final static Logger logger = Logger.getLogger(MigrationAgent.class.getCanonicalName());

}

