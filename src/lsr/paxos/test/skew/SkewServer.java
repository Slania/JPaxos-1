package lsr.paxos.test.skew;

import lsr.paxos.test.statistics.SkewTimelines;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SkewServer {

    private ServerSocket skewServer;
    private Socket skewClient;
    private DataOutputStream skewClientOutputStream;
    private DataInputStream skewClientInputStream;

    public void start(String skewServerPort) throws IOException {
        Thread skewTimeline = new Thread(new SkewTimelines());
        skewTimeline.start();
        int syn = 1;
        int transactionNumber = 1;
        skewServer = new ServerSocket(Integer.valueOf(skewServerPort));
        skewClient = skewServer.accept();
        skewClientOutputStream = new DataOutputStream(skewClient.getOutputStream());
        skewClientInputStream = new DataInputStream(skewClient.getInputStream());
        while (syn == 1) {
            syn = skewClientInputStream.read();
            SkewTimelines.addFlowPoint(transactionNumber + "," + System.currentTimeMillis());
            if (syn == 1) {
                skewClientOutputStream.write(1);
                skewClientOutputStream.flush();
            }
            transactionNumber++;
        }
    }

    public static void main(String args[]) throws IOException {
        SkewServer skewClient = new SkewServer();
        skewClient.start(args[0]);
    }


}
