package cs451;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Broadcaster extends Thread {
    private PerfectLink perfectLink;
    private final List<Host> hosts;
    private final Map<Integer, Host> hostMap;
    private final int myId;
    private final int numMessages;
    private final Logger logger;

    // Messages to broadcast
    private int nextSeqNum = 1; // Next sequence number to send

    // URB data structures
    private final Set<Message> receivedMessages = ConcurrentHashMap.newKeySet(); // messageId
    private final Set<Message> deliveredMessages = ConcurrentHashMap.newKeySet(); // messageId
    private final ConcurrentHashMap<Message, Set<Integer>> ackCounts = new ConcurrentHashMap<>(); // messageId -> set of processes that acknowledged
    private final int N; // Total number of processes

    // FIFO data structures
    private final Map<Integer, Integer> nextExpectedSeq = new ConcurrentHashMap<>(); // senderId -> next expected seqNum
    private final Map<Integer, Map<Integer, Message>> pendingMessages = new ConcurrentHashMap<>(); // senderId -> (seqNum -> message)

    public Broadcaster(PerfectLink perfectLink, List<Host> hosts, int myId, Logger logger, int numMessages) {
        this.hosts = hosts;
        this.myId = myId;
        this.logger = logger;
        this.numMessages = numMessages;
        this.N = hosts.size();
        this.perfectLink = perfectLink;
        

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

    public void  SetPerfectLink(PerfectLink perfectLink){
        this.perfectLink =perfectLink;
    }

    @Override
    public void run() {
        // Start PerfectLink listener
        perfectLink.start();

        
        // Broadcast messages
        while (nextSeqNum <= numMessages) {
            for (Host host : hosts) {
                Message msg = new Message(nextSeqNum, myId, myId, host.getId(), 2);
                logger.logDebug("message broadcast 1ere fois " + msg.creatorId + ":" + msg.seqNum + " a " + msg.destinationId);
                urbBroadcast(msg);
            }
            nextSeqNum++;

            // Log the broadcast
            logger.logSend(nextSeqNum - 1);
        }
    }

    private void urbBroadcast(Message message) {
        // Send the message to all hosts using Perfect Links
        if (message.destinationId == myId){
            urbDeliver(message);
        }else{
            perfectLink.send(message);
        }
    }



    public void urbDeliver(Message message) {

        // If first time seeing the message

        logger.logDebug("message reçus " + message.creatorId + ":" + message.seqNum + " de la part de " + message.senderId);

        if (!receivedMessages.contains(message)) {
            receivedMessages.add(message);

            // Initialize acknowledgment set
            ackCounts.put(message, ConcurrentHashMap.newKeySet());

            // Add self to acknowledgment set
            ackCounts.get(message).add(myId);
            ackCounts.get(message).add(message.senderId);

            // Send acknowledgment to everybody
            for (Host host : hosts) {
                //System.out.println("hots.getid " + host.getId());
                //System.out.println("myId : " + myId + " senderId : " + message.senderId + " creatorId : "+ message.creatorId );
                if (host.getId() != myId && host.getId() != message.senderId && host.getId() != message.creatorId 
                    && message.creatorId != myId && !deliveredMessages.contains(message)){
                    Message NewMessage = new Message(message.seqNum, message.creatorId, myId, host.getId(), 2);
                    logger.logDebug("message broadcast une fois reçu " + NewMessage.creatorId + ":" + NewMessage.seqNum + " a " + NewMessage.destinationId);
                    urbBroadcast(NewMessage);
                }
                if (host.getId() != myId) {
                    Message msg_ack = new Message(message.seqNum, message.creatorId, myId, host.getId(), 1);
                    perfectLink.sendAckURB(msg_ack);
                }
            }
            
        }

        // Attempt to deliver the message
        checkAndDeliver(message);
    }

    public void handleAck(Message message_ack) {
        
        // Add the acknowledger (destId) to the acknowledgment set
        Message message_data = new Message(message_ack.seqNum, message_ack.creatorId, myId, message_ack.senderId, 2);

        logger.logDebug("Reçois ACK_URB message " + message_data.creatorId + ":" + message_data.seqNum + " de la part de "+ message_ack.senderId);
        ackCounts.computeIfAbsent(message_data, k -> ConcurrentHashMap.newKeySet()).add(message_data.destinationId);
        
        // Attempt to deliver the message
        checkAndDeliver(message_data);
    }
    

    private void checkAndDeliver(Message message) {
        Set<Integer> acks = ackCounts.get(message);
        logger.logDebug("Check and deliver " + message.creatorId + ":"+ message.seqNum + " de la part de " + message.senderId +" alors " + acks.size());
        if (acks != null && acks.size() >= Math.ceil(((double)N) / 2)) {
            // Deliver the message if not already delivered
            if (!deliveredMessages.contains(message)) {
                deliveredMessages.add(message);

                logger.logDebug("Message deliver (pas fifo) : " + message.creatorId + ":" + message.seqNum);

                // FIFO delivery check
                fifoDeliver(message);
            }
        }
    }

    private void fifoDeliver(Message message) {
        // Add message to pending buffer
        pendingMessages.get(message.creatorId).put(message.seqNum, message);

        // Try to deliver messages in order
        int nextSeq = nextExpectedSeq.get(message.creatorId);

        while (pendingMessages.get(message.creatorId).containsKey(nextSeq)) {
            pendingMessages.get(message.creatorId).remove(nextSeq);

            // Deliver the message
            //logger.logDeliver(message.creatorId, nextSeq);
            logger.logDebug("Message deliver (FIFO) : " + message.creatorId + ":" + message.seqNum);

            // Increment expected sequence number
            nextSeq++;
            nextExpectedSeq.put(message.creatorId, nextSeq);
        }
    }
}
