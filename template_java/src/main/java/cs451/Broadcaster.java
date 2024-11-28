package cs451;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Broadcaster extends Thread {
    private final PerfectLink perfectLink;
    private final List<Host> hosts;
    private final Map<Integer, Host> hostMap;
    private final int myId;
    private final int numMessages;
    private final Logger logger;

    // Messages to broadcast
    private int nextSeqNum = 1; // Next sequence number to send

    // URB data structures
    private final Set<String> receivedMessages = ConcurrentHashMap.newKeySet(); // messageId
    private final Set<String> deliveredMessages = ConcurrentHashMap.newKeySet(); // messageId
    private final ConcurrentHashMap<String, Set<Integer>> ackCounts = new ConcurrentHashMap<>(); // messageId -> set of processes that acknowledged
    private final int N; // Total number of processes

    // FIFO data structures
    private final Map<Integer, Integer> nextExpectedSeq = new ConcurrentHashMap<>(); // senderId -> next expected seqNum
    private final Map<Integer, Map<Integer, String>> pendingMessages = new ConcurrentHashMap<>(); // senderId -> (seqNum -> message)

    public Broadcaster(PerfectLink perfectLink, List<Host> hosts, int myId, Logger logger, int numMessages) {
        this.perfectLink = perfectLink;
        this.hosts = hosts;
        this.myId = myId;
        this.logger = logger;
        this.numMessages = numMessages;
        this.N = hosts.size();

        // Create a map from host ID to Host object for easy access
        hostMap = new HashMap<>();
        for (Host host : hosts) {
            hostMap.put(host.getId(), host);
        }

        // Initialize next expected sequence numbers
        for (Host host : hosts) {
            nextExpectedSeq.put(host.getId(), 1);
            pendingMessages.put(host.getId(), new ConcurrentHashMap<>());
        }
    }

    @Override
    public void run() {
        // Start PerfectLink listener
        perfectLink.start();

        // Broadcast messages
        while (nextSeqNum <= numMessages) {
            String message = Integer.toString(nextSeqNum);;
            urbBroadcast(message);
            nextSeqNum++;

            // Log the broadcast
            logger.logSend(nextSeqNum - 1);
        }
    }

    private void urbBroadcast(String message) {
        // Send the message to all hosts using Perfect Links
        for (Host host : hosts) {
            if (host.getId() == myId){
                urbDeliver(message);
            }else{
                Message msg = new Message(nextSeqNum, MAX_PRIORITY, N, N, isInterrupted(), isDaemon(), isAlive());
                perfectLink.send(msg);
            }
            
        }
    }



    public void urbDeliver(String message) {
        // Message format: senderId:seqNum
        String[] parts = message.split(":");
        if (parts.length != 2) {
            return; // Invalid message format
        }
        int senderId = Integer.parseInt(parts[0]);
        int seqNum = Integer.parseInt(parts[1]);

        String messageId = senderId + ":" + seqNum;

        // If first time seeing the message
        if (!receivedMessages.contains(messageId)) {
            receivedMessages.add(messageId);

            // Initialize acknowledgment set
            ackCounts.put(messageId, ConcurrentHashMap.newKeySet());

            // Add self to acknowledgment set
            ackCounts.get(messageId).add(myId);

            // Send acknowledgment to everybody
            String ackMessageId = senderId + ":" + seqNum;
            Message msg = new Message(seqNum, seqNum, senderId, seqNum, isInterrupted(), isDaemon(), isAlive());
            for (Host host : hosts) {
                perfectLink.sendAckURB(msg);
            }
            
            // Rebroadcast the message
            urbBroadcast(message);
        }

        // Attempt to deliver the message
        checkAndDeliver(messageId, senderId, seqNum);
        //System.out.println("Process " + myId + " urbDeliver message from " + senderId + ": " + message);
    }

    public void handleAck(String ackMessage) {
        // ACK message format: "ACK:ackSenderId:originalSenderId:seqNum"
        String[] parts = ackMessage.split(":");
        if (parts.length != 4) {
            return; // Invalid ACK message format
        }
        int ackSenderId = Integer.parseInt(parts[1]);
        int originalSenderId = Integer.parseInt(parts[2]);
        int seqNum = Integer.parseInt(parts[3]);
        String messageId = originalSenderId + ":" + seqNum;

        //System.out.println(messageId + " avant de mettre a jour : " + ackCounts.get(messageId).size());

        // Add the acknowledger (destId) to the acknowledgment set
        ackCounts.computeIfAbsent(messageId, k -> ConcurrentHashMap.newKeySet()).add(ackSenderId);
        
        // Attempt to deliver the message
        checkAndDeliver(messageId, originalSenderId, seqNum);
        //System.out.println("Process " + myId + " received ACK from " + ackSenderId + " for message " + messageId);

    }
    

    private void checkAndDeliver(String messageId, int senderId, int seqNum) {
        Set<Integer> acks = ackCounts.get(messageId);
        if(senderId != myId){
            System.out.println("le nombre de ack : " + ackCounts.get(messageId).size());
        }
        //System.out.println("Check and deliver " + messageId + " alors " + acks.size());
        if (acks != null && acks.size() > N / 2 || senderId==myId ) {
            // Deliver the message if not already delivered
            if (!deliveredMessages.contains(messageId)) {
                deliveredMessages.add(messageId);

                // FIFO delivery check
                System.out.println("delivered message : " + messageId + " by " + myId);
                logger.logDeliver(senderId, seqNum);
            }
        }
    }

   /*  private void fifoDeliver(int senderId, int seqNum) {
        // Add message to pending buffer
        pendingMessages.get(senderId).put(seqNum, senderId + ":" + seqNum);

        // Try to deliver messages in order
        int nextSeq = nextExpectedSeq.get(senderId);

        while (pendingMessages.get(senderId).containsKey(nextSeq)) {
            String messageKey = senderId + ":" + nextSeq;
            pendingMessages.get(senderId).remove(nextSeq);

            // Deliver the message
            logger.logDeliver(senderId, nextSeq);

            // Increment expected sequence number
            nextSeq++;
            nextExpectedSeq.put(senderId, nextSeq);
        }
    }*/
}
