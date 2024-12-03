package cs451;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.AbstractMap.SimpleEntry;


import cs451.Broadcaster;

public class PerfectLink {
    private final DatagramSocket socket;
    private final int myId;
    private final Logger logger;
    private final int QueueSizeMax = 750;
    private double windowSize = 3000;
    private int MIN_WINDOW_SIZE = 3000;
    private int MAX_WINDOW_SIZE = 10000;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    private LinkedBlockingQueue<Message> SendQueue = new LinkedBlockingQueue<>(QueueSizeMax); 

    private final Map<Integer, Host> hosts;

    // Sender-side data structures
    private final Set<Packet> MapMessageWithoutAck = ConcurrentHashMap.newKeySet();
    ;
    private final int RETRANSMIT_TIMEOUT = 3000;
    private final int WINDOW_TIMEOUT = 1000; 
    

    // Receiver-side data structures
    private final Set<Map.Entry<Integer, Integer>> deliveredMessages = ConcurrentHashMap.newKeySet(); // messageId

    // Listener threads
    private Thread listenerThread;
    private Thread queueThread;
    private Thread windowThread;

    private AtomicInteger NumberOfAck = new AtomicInteger(0);
    private AtomicInteger NumberOfTimeOut = new AtomicInteger(0);

    private final Broadcaster broadcaster;

    private boolean alreadyResend;

    


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
                logger.logDebug("PerfectLink listen() : recoit paquet port : " + packet.getPort() + " contenu : " + received);
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
        System.out.println("Recois");
        if (received.startsWith("ACK:")) {
            String[] parts = received.split(":");
            if (parts.length < 4) {
                // Invalid ACK message format
                return;
            }
            
            int NumberOfMessage = Integer.parseInt(parts[2]);
            int ackSenderId = Integer.parseInt(parts[1]);
            Packet packet = new Packet(new ArrayList<>(), ackSenderId);
            int max = NumberOfMessage*2+4;
            for(int i = 4; i<max; i+=2){
                int creator = Integer.parseInt(parts[i]);
                int seqNum = Integer.parseInt(parts[i+1]);
                NumberOfAck.incrementAndGet();
                packet.messagesPairs.add(new AbstractMap.SimpleEntry<>(creator, seqNum));
            }
            MapMessageWithoutAck.remove(packet);


        } else if(received.startsWith("ACK_URB:")) {

            String[] parts = received.split(":");
            if (parts.length != 4) {
                // Invalid ACK message format
                return;
            }
            int ackSenderId = Integer.parseInt(parts[1]);
            int CreatorId = Integer.parseInt(parts[2]);
            int seqNum = Integer.parseInt(parts[3]);

            Message message_ackurb = new Message(seqNum, CreatorId, ackSenderId, myId, 1);

            //broadcaster.handleAck(message_ackurb);

        } else {

            // Extract senderId from message
            String[] parts = received.split(":");
            if (parts.length < 2) {
                // Invalid message format
                return;
            }

      
            int NumberOfMessage = Integer.parseInt(parts[0]);
            int senderId = Integer.parseInt(parts[1]);
            int max = NumberOfMessage*2+2;

            sendAck(received, senderId);


            for(int i =2; i<max; i+=2){
                int creator = Integer.parseInt(parts[i]);
                int seqNum = Integer.parseInt(parts[i+1]);
                Map.Entry<Integer,Integer> pair = new AbstractMap.SimpleEntry<>(creator, seqNum);
                if (!deliveredMessages.contains(pair)) {
                    deliveredMessages.add(pair);
        
                    logger.logDeliver(pair); 
                }
            }
            

        }
    }


    private void queueUpdate(){
        while (true){
            try{

                logger.logDebug("MapMessageWithoutAck: "+ MapMessageWithoutAck.size());

                 synchronized (MapMessageWithoutAck) {
                    while (MapMessageWithoutAck.size() >= windowSize) {
                        logger.logDebug("PerfectLink queueUpdate() : je suis bloqué car la map : " + MapMessageWithoutAck.size() + " est plus grand que la window : " + windowSize);
                       try {
                            Thread.sleep(1000);
                       } catch (Exception e) {
                        // TODO: handle exception
                       }
                    }
                }

                
                Message message = SendQueue.take();
                //System.out.println("message : " + message.creatorId +":"+ message.seqNum);
            
                if (message != null) {
                    // Extract the message and receiver

                    Packet packet = new Packet(new ArrayList<>(), message.destinationId);
                    packet.messagesPairs.add(new AbstractMap.SimpleEntry<>(message.creatorId, message.seqNum));

                    int destinationId = message.destinationId;
                    int seqNum = message.seqNum;
                    
                    // Try to get more messages for the same receiver
                    List<Message> messagesList = new ArrayList<>();
                    messagesList.add(message);
                    for(Message msg : SendQueue){
                        if (msg.destinationId == destinationId){
                            messagesList.add(msg);
                            SendQueue.remove(msg);
                            packet.messagesPairs.add(new AbstractMap.SimpleEntry<>(msg.creatorId, msg.seqNum));

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

                    MapMessageWithoutAck.add(packet);
                    
                    // Send the packet
                    try {
                        for(Message msg : messagesList){
                            
                            logger.logSend(msg.seqNum);
                        }
                        sendMessage(packet, messageData);
                        
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
                Thread.sleep(WINDOW_TIMEOUT);

                //System.out.println("\nACK count: " + NumberOfAck.get());
                //System.out.println("Timeout count: " + NumberOfTimeOut.get());
                //System.out.println("Window size: " + windowSize);
                //System.out.println("Timeout: " + WINDOW_TIMEOUT);
                //System.out.println("Delivered messages: " + deliveredMessages.size());
                //System.out.println("MessageWithAck: " + MapMessageWithoutAck.size());
                //System.out.println("Queue: " + SendQueue.size());

                double Rate = (double) NumberOfAck.get() / (NumberOfAck.get() + NumberOfTimeOut.get());
                if (Rate < 0.3) {
                    windowSize = Math.max(windowSize / 2, MIN_WINDOW_SIZE);
                    logger.logDebug("PerfectLink window Update () : Baisse taille de window : " + windowSize);
                } else if (Rate > 0.7) {
                    windowSize = Math.min(windowSize * 2, MAX_WINDOW_SIZE);
                    logger.logDebug("PerfectLink window Update () : Augmente taille de window : " + windowSize);
                } else {
                    logger.logDebug("PerfectLink window Update () : Garde la taille de window " + windowSize);
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


    

    private void sendMessage(Packet packet, String string) {
        System.out.println("Envoie");
        try{
            Host destination = hosts.get(packet.destinationId);
            InetAddress address = InetAddress.getByName(destination.getIp());
            byte[] buf = string.getBytes();
            DatagramPacket packet_send = new DatagramPacket(buf, buf.length, address, destination.getPort());
            logger.logDebug("PerfectLink sendMessage() : envoie : "+ string + " au port : "+ packet_send.getPort());
            socket.send(packet_send);

            if(packet.nature==2){
                scheduler.schedule(() -> {
                    if (MapMessageWithoutAck.contains(packet)) {
                        try {
                            logger.logDebug("PerfectLink sendMessage() : doit renvoyer le packet car pas de ack reçu " + string);
                            sendMessage(packet, string);
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


    public void sendAck(String string, int destinationId) {
        System.out.println("Envoie ack");
        try {
            StringBuilder messageAckBuilder = new StringBuilder();
            messageAckBuilder.append("ACK:").append(myId).append(string);
            String messageAck = messageAckBuilder.toString();
            Host destination = hosts.get(destinationId);
            InetAddress address = InetAddress.getByName(destination.getIp());
            byte[] buf = messageAck.getBytes();
            DatagramPacket packet_send = new DatagramPacket(buf, buf.length, address, destination.getPort());
            logger.logDebug("PerfectLink sendAck() : envoie : "+ messageAck + " au port : "+ packet_send.getPort());
            socket.send(packet_send);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }


    public void sendAckURB(Message message) {
        // ACK message format: "ACK:ackSenderId:originalSenderId:seqNum"
        String ackMessage = "ACK_URB:" + myId + ":" + message.creatorId + ":" + message.seqNum;
        // Send the ACK directly without adding to sendQueue
      
        Packet packet = new Packet(new ArrayList<>(), message.destinationId);
        packet.messagesPairs.add(new AbstractMap.SimpleEntry<>(message.creatorId, message.seqNum));

        sendMessage(packet, ackMessage);
    }
    
}
