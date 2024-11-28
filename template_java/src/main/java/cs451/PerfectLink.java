package cs451;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import cs451.Broadcaster;

public class PerfectLink {
    private final DatagramSocket socket;
    private final int myId;
    private final Logger logger;
    private final int QueueSizeMax = 10000;
    private double windowSize = 3000;
    private int MIN_WINDOW_SIZE = 3000;
    private int MAX_WINDOW_SIZE = 30000;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    private LinkedBlockingQueue<Message> SendQueue = new LinkedBlockingQueue<>(QueueSizeMax); 

    private final Map<Integer, Host> hosts;

    // Sender-side data structures
    private final Set<Message> MapMessageWithoutAck = ConcurrentHashMap.newKeySet();
    ;
    private final int RETRANSMIT_TIMEOUT = 1000;
    private final int WINDOW_TIMEOUT = 10; 
    

    // Receiver-side data structures
    private final Set<Message> deliveredMessages = ConcurrentHashMap.newKeySet(); // messageId

    // Listener threads
    private Thread listenerThread;
    private Thread queueThread;
    private Thread windowThread;

    private AtomicInteger NumberOfAck = new AtomicInteger(0);
    private AtomicInteger NumberOfTimeOut = new AtomicInteger(0);

    private final Broadcaster broadcaster;


    public PerfectLink(DatagramSocket socket, int myId, Map<Integer, Host> hosts, Logger logger, Broadcaster broadcaster) {
        this.socket = socket;
        this.myId = myId;
        this.hosts = hosts;
        this.logger = logger;
        this.broadcaster = broadcaster;
    }

    public void start() {
        // Start the listener thread
        listenerThread = new Thread(() -> listen());
        listenerThread.start();

        // Start the retransmission thread
        windowThread = new Thread(() -> windowUpdate());
        windowThread.start();

        queueThread = new Thread(() -> queueUpdate());
        queueThread.start();
    }

    public void stop() {
        // Interrupt the listener thread
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        if (queueThread != null) {
            queueThread.interrupt();
        }
        if (windowThread != null) {
            windowThread.interrupt();
        }

        // Close the socket
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }


    private void listen() {
        byte[] buf = new byte[65535]; // Adjust buffer size if needed

        while (!Thread.currentThread().isInterrupted()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try {
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());

                // Handle the received message
                handleReceivedMessage(received, packet.getAddress(), packet.getPort());
            } catch (SocketException e) {
                // Socket closed; exit loop
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleReceivedMessage(String received, InetAddress senderAddress, int senderPort) {
        //System.out.println("Process " + myId + " received message: " + received);
        if (received.startsWith("ACK:")) {
            String[] parts = received.split(":");
            if (parts.length != 4) {
                // Invalid ACK message format
                return;
            }
            int ackSenderId = Integer.parseInt(parts[1]);
            int CreatorId = Integer.parseInt(parts[2]);
            int seqNum = Integer.parseInt(parts[3]);

            System.err.println("Received ACK for the message " + CreatorId + " : "+ seqNum);

            // Construct messageId as stored in sendQueue
            Message msg = new Message(seqNum, CreatorId, myId, ackSenderId, false, false, true);

            NumberOfAck.incrementAndGet();

           // System.out.println("Reçu le ack du message : " +seqNum +":" +CreatorId + " envoyé par " + myId + " pour "+ ackSenderId);

            // Remove the acknowledged message from sendQueue
            MapMessageWithoutAck.remove(msg);

        } else if(received.startsWith("ACK_URB:")) {

            broadcaster.handleAck(received);

        } else {

            // Extract senderId from message
            String[] parts = received.split(":");
            if (parts.length < 2) {
                // Invalid message format
                return;
            }
      
            int NumberOfMessage = Integer.parseInt(parts[0]);
            int senderId = Integer.parseInt(parts[1]);
            //System.out.println(received);
            for(int i =2; i<NumberOfMessage*2+2; i+=2){
                int creator = Integer.parseInt(parts[i]);
                int seqNum = Integer.parseInt(parts[i+1]);
                Message msg = new Message(seqNum, creator, senderId, myId, false, false, true);
                Message msg_ack = new Message(seqNum, creator, myId, senderId, true, false, false);
                sendAck(msg_ack);
                if (!deliveredMessages.contains(msg)) {
                    deliveredMessages.add(msg);
                    System.out.println("delivers " + creator + ":"+seqNum);
    
                    // Enqueue the message (excluding senderId) for delivery
                    logger.logDeliver(senderId, seqNum);
                    if(broadcaster != null){
                        //broadcaster.urbDeliver(message);
                    }
                }
            }

        }
    }


    private void queueUpdate(){
        while (true){
            try{

                /*synchronized (MapMessageWithoutAck) {
                    while (MapMessageWithoutAck.size() >= windowSize) {
                        System.err.println("bloqué ici ");
                        MapMessageWithoutAck.wait();
                    }
                } */
                Message message = SendQueue.take();
                System.out.println("message : " + message.creatorId +":"+ message.seqNum);
            
                if (message != null) {
                    // Extract the message and receiver

                    int destinationId = message.destinationId;
                    int seqNum = message.seqNum;
                    
                    // Try to get more messages for the same receiver
                    List<Message> messagesList = new ArrayList<>();
                    messagesList.add(message);
                    for(Message msg : SendQueue){
                        if (msg.destinationId == destinationId){
                            messagesList.add(msg);
                            SendQueue.remove(msg);
                        }
                        if (messagesList.size() == 8) {
                            break;
                        }
                        if(messagesList.size() > 8){
                            System.out.println("Grosse tricherie");
                        }
                    }  
                    
                    StringBuilder messageDataBuilder = new StringBuilder();
                    
                    messageDataBuilder.append(messagesList.size()).append(":").append(message.senderId);
                    for (Message msg : messagesList){
                        messageDataBuilder.append(":").append(msg.creatorId).append(":").append(msg.seqNum);
                    }
                    String messageData = messageDataBuilder.toString();
                    System.out.println(messageData);
                    
                    // Send the packet
                    try {
                        sendMessage(message, messageData);
                        for(Message msg : messagesList){
                            MapMessageWithoutAck.add(msg);
                            logger.logSend(msg.seqNum);
                        }
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void windowUpdate(){
        try {
            while (true) {
                // Sleep for the timeout duration
                Thread.sleep(500);

                System.out.println("\nACK count: " + NumberOfAck.get());
                System.out.println("Timeout count: " + NumberOfTimeOut.get());
                System.out.println("Window size: " + windowSize);
                System.out.println("Timeout: " + WINDOW_TIMEOUT);
                System.out.println("Delivered messages: " + deliveredMessages.size());
                System.out.println("MessageWithAck: " + MapMessageWithoutAck.size());
                System.out.println("Queue: " + SendQueue.size());

                double Rate = (double) NumberOfAck.get() / (NumberOfAck.get() + NumberOfTimeOut.get());
                if (Rate < 0.3) {
                    windowSize = Math.max(windowSize / 2, MIN_WINDOW_SIZE);
                    System.out.println("Decreasing window size to " + windowSize);
                } else if (Rate > 0.7) {
                    windowSize = Math.min(windowSize * 2, MAX_WINDOW_SIZE);
                    System.out.println("Increasing window size to " + windowSize);
                } else {
                    System.out.println("Keeping window size at " + windowSize);
                }

                // Reset counters for the next interval
                NumberOfAck.set(0);
                NumberOfTimeOut.set(0);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    public void send(Message message) {
        // Include senderId in the message
        //System.out.println("message : " +message.seqNum +":" +message.creatorId + " envoyé par " + myId + " pour "+ message.destinationId);


        while (SendQueue.size() + 100 > QueueSizeMax) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        SendQueue.add(message);
    }


    

    private void sendMessage(Message msg, String string) {
        try{
            Host destination = hosts.get(msg.destinationId);
            InetAddress address = InetAddress.getByName(destination.getIp());
            byte[] buf = string.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, destination.getPort());
            socket.send(packet);

            if(msg.is_data){
                scheduler.schedule(() -> {
                    if (MapMessageWithoutAck.contains(msg)) {
                        try {
                            //System.out.println("resend messages");
                            sendMessage(msg, string);
                            NumberOfTimeOut.incrementAndGet();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, RETRANSMIT_TIMEOUT, TimeUnit.MILLISECONDS);
            }    
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void sendAck(Message message) {
        // ACK message format: "ACK:ackSenderId:originalSenderId:seqNum"
        String ackMessage = "ACK:" + myId + ":" + message.creatorId + ":" + message.seqNum;
        // Send the ACK directly without adding to sendQueue
        sendMessage(message, ackMessage);
    }


    public void sendAckURB(Message message) {
        // ACK message format: "ACK:ackSenderId:originalSenderId:seqNum"
        String ackMessageURB = "ACK_URB:" + myId + ":" + message.creatorId + ":" + message.seqNum;
        // Send the ACK directly without adding to sendQueue
        sendMessage(message, ackMessageURB);
    }
    
}
