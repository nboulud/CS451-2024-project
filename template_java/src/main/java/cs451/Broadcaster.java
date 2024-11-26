package cs451;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Broadcaster extends Thread {
    private final PerfectLink perfectLink;
    private final List<Host> hosts;
    private final int myId;
    private final int numMessages;
    private final Logger logger;

    // Messages to broadcast
    private int nextSeqNum = 1; // Next sequence number to send

    // URB data structures
    private final Set<String> receivedMessages = ConcurrentHashMap.newKeySet(); // messageId
    private final Set<String> deliveredMessages = ConcurrentHashMap.newKeySet(); // messageId

    // FIFO data structures
    private final Map<Integer, Integer> nextExpectedSeq = new ConcurrentHashMap<>(); // senderId -> next expected seqNum
    private final Map<Integer, Map<Integer, String>> pendingMessages = new ConcurrentHashMap<>(); // senderId -> (seqNum -> message)

    public Broadcaster(PerfectLink perfectLink, List<Host> hosts, int myId, Logger logger, int numMessages) {
        this.perfectLink = perfectLink;
        this.hosts = hosts;
        this.myId = myId;
        this.logger = logger;
        this.numMessages = numMessages;

        // Initialize next expected sequence numbers
        for (Host host : hosts) {
            nextExpectedSeq.put(host.getId(), 1);
            pendingMessages.put(host.getId(), new HashMap<>());
        }
    }

    @Override
    public void run() {
        // Start PerfectLink listener
        perfectLink.start();

        // Start listener thread
        Thread listenerThread = new Thread(this::listenForMessages);
        listenerThread.start();

        // Broadcast messages
        while (nextSeqNum <= numMessages) {
            String message = myId + ":" + nextSeqNum;
            urbBroadcast(message);
            nextSeqNum++;

            // Log the broadcast
            logger.logSend(nextSeqNum - 1);
        }

        // Keep the thread alive to continue receiving messages
        try {
            listenerThread.join();
        } catch (InterruptedException e) {
            // Thread interrupted
        }
    }

    private void urbBroadcast(String message) {
        // Send the message to all hosts using Perfect Links
        for (Host host : hosts) {
            if (host.getId() != myId) {
                perfectLink.send(host, message);
            } else {
                // Deliver to self immediately
                urbDeliver(message);
            }
        }
    }

    private void listenForMessages() {
        while (!Thread.currentThread().isInterrupted()) {
            String received = perfectLink.deliver();
            if (received != null) {
                urbDeliver(received);
            }
        }
    }

    private void urbDeliver(String message) {
        // Message format: senderId:seqNum
        String[] parts = message.split(":");
        int senderId = Integer.parseInt(parts[0]);
        int seqNum = Integer.parseInt(parts[1]);

        String messageId = senderId + ":" + seqNum; 
        // Check if message has been received before
        if (!receivedMessages.contains(messageId)) {
            receivedMessages.add(messageId);

            // Rebroadcast the message
            if (senderId != myId) {
                urbBroadcast(message);
            }

            // Attempt to deliver the message
            fifoDeliver(message);
        }
    }

    private void fifoDeliver(String message) {
        // Message format: senderId:seqNum
        String[] parts = message.split(":");
        if (parts.length != 2) {
            // Invalid message format
            return;
        }

        int senderId = Integer.parseInt(parts[0]);
        int seqNum = Integer.parseInt(parts[1]);

        // Add message to pending buffer
        pendingMessages.get(senderId).put(seqNum, message);

        // Try to deliver messages in order
        int nextSeq = nextExpectedSeq.get(senderId);

        while (pendingMessages.get(senderId).containsKey(nextSeq)) {
            String msgToDeliver = pendingMessages.get(senderId).remove(nextSeq);
            String messageKey = senderId + ":" + nextSeq;

            if (!deliveredMessages.contains(messageKey)) {
                // Deliver the message
                deliveredMessages.add(messageKey);

                // Log the delivery
                System.out.println("logged deliver par "+ myId + " du message " + message);
                logger.logDeliver(senderId, nextSeq);
            }

            // Increment expected sequence number
            nextSeq++;
            nextExpectedSeq.put(senderId, nextSeq);
        }
    }
}
