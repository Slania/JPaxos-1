package lsr.paxos.test.statistics;

import lsr.paxos.messages.Message;
import lsr.paxos.messages.MessageFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MessageData {

    public enum QueuePoint {
        Sent, Queued
    }

    private Message message;
    private Long timestamp;
    private QueuePoint queuePoint;

    public MessageData(byte[] message, Long timestamp, QueuePoint queuePoint) {
        this.timestamp = timestamp;
        this.queuePoint = queuePoint;
        InputStream is = new ByteArrayInputStream(message);
        try {
            this.message = MessageFactory.create(new DataInputStream(is));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public QueuePoint getQueuePoint() {
        return queuePoint;
    }

    public void setQueuePoint(QueuePoint queuePoint) {
        this.queuePoint = queuePoint;
    }
}