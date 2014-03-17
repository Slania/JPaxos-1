package lsr.paxos.test.statistics;

import java.io.*;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

public class SkewTimelines implements Runnable {

    static final Logger logger = Logger.getLogger(SkewTimelines.class.getCanonicalName());

    public static ArrayBlockingQueue<String> skewPointData = new ArrayBlockingQueue<String>(2056);

    public static void addFlowPoint(String data) {
        try {
            skewPointData.put(data);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
    try {
        logger.info("******* Skew Timelines started *******");
            while (true) {
                String nextPoint = skewPointData.poll();
                if (nextPoint != null) {
                    log(nextPoint);
                }
            }
        } catch (InterruptedException e) {
        e.printStackTrace();
    } catch (IOException e) {
        e.printStackTrace();
    }
    }

    private void log(String data) throws IOException, InterruptedException {
        String localHostname = getLocalHostname();
        StringTokenizer hostNameTokenizer = new StringTokenizer(localHostname, ".");
        String me = "";
        while (hostNameTokenizer.hasMoreElements()) {
            me = (String) hostNameTokenizer.nextElement();
            break;
        }
        File file = new File(me + "_skew.txt");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(data);
        bw.close();
    }

    private String getLocalHostname() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("hostname");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        process.waitFor();
        InputStream inputStream = process.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.readLine();
    }

}
