package lsr.paxos.test.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public class MessageTimelines implements Runnable {

    public static final Object lock = new Object();

    static final Logger logger = Logger.getLogger(MessageTimelines.class.getCanonicalName());

    public static HashMap<String, List<MessageData>> allMessageData = new HashMap<String, List<MessageData>>();
    public static List<String> sentMessages = new ArrayList<String>();

    public static void addMessagePoint(MessageData data) {
        List<MessageData> messageData = allMessageData.get(data.getMessage().toString());
        if (messageData == null) {
            messageData = new ArrayList<MessageData>();
        }
        messageData.add(data);
        allMessageData.put(messageData.toString(), messageData);
    }

    public void logMessagePoints() {
        Long queuedTime = (long) 0;
        Long sentTime = (long) 0;
        for (String sentMessage : sentMessages) {
            List<MessageData> messageData = allMessageData.get(sentMessage);
            for (MessageData data : messageData) {
                logger.info(data.getMessage() + " - " + data.getQueuePoint().toString() + " at: " + data.getTimestamp());
                if (data.getQueuePoint().equals(MessageData.QueuePoint.Queued)) {
                    queuedTime = data.getTimestamp();
                }
                if (data.getQueuePoint().equals(MessageData.QueuePoint.Sent)) {
                    sentTime = data.getTimestamp();
                }
            }
            logger.info(messageData.get(0).getMessage() + " - total time: " + (sentTime - queuedTime));
        }
    }

    @Override
    public void run() {
        while (true) {
            if ((System.currentTimeMillis() % 1000) == 0) {
                logMessagePoints();
            }
        }
    }
}
