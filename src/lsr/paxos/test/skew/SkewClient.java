package lsr.paxos.test.skew;

import lsr.paxos.test.statistics.ReplicaRequestTimelines;
import lsr.paxos.test.statistics.SkewTimelines;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class SkewClient {

    private Socket skewServer;
    private DataOutputStream skewServerOutputStream;
    private DataInputStream skewServerInputStream;

    public void start(String skewServerName, String skewServerPort) throws IOException {
        Thread skewTimeline = new Thread(new SkewTimelines());
        skewTimeline.start();
        int ack = 1;
        int transactionNumber = 1;
        skewServer = new Socket(skewServerName, Integer.valueOf(skewServerPort));
        skewServerOutputStream = new DataOutputStream(skewServer.getOutputStream());
        skewServerInputStream = new DataInputStream(skewServer.getInputStream());
        while (ack == 1) {
            skewServerOutputStream.write(1);
            skewServerOutputStream.flush();
            SkewTimelines.addFlowPoint(transactionNumber + "," + System.currentTimeMillis());
            ack = skewServerInputStream.readInt();
            transactionNumber++;
        }
    }

    public static void main(String args[]) throws IOException {
        SkewClient skewClient = new SkewClient();
        skewClient.start(args[0], args[1]);
    }


}
