package cs451;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class Sender extends Thread {
    private final DatagramSocket socket;
    private final Host receiverHost;
    private final int numMessages;
    private final int myId;
    private final Logger logger;
    private final int windowSize;


    private static final int TIMEOUT = 100;
    private final List<Integer> window = Collections.synchronizedList(new LinkedList<>());
    private final ConcurrentHashMap<Integer, Long> sentMessages = new ConcurrentHashMap<>();
    private int nextSeqNum = 1; // Next sequence number to send
    private final Set<Integer> loggedMessages = ConcurrentHashMap.newKeySet();


    public Sender(DatagramSocket socket, Host receiverHost, int numMessages, int myId, Logger logger, int windowSize) {
        this.socket = socket;
        this.receiverHost = receiverHost;
        this.numMessages = numMessages;
        this.myId = myId;
        this.logger = logger;
        this.windowSize = windowSize;
    }

    @Override
    public void run() {
        // Start a thread to listen for acknowledgments
        Thread ackListener = new Thread(this::listenForAcks);
        ackListener.start();

        synchronized (window) {
            while (window.size() < windowSize && nextSeqNum <= numMessages) {
                window.add(nextSeqNum);
                sendMessage(nextSeqNum);
                sentMessages.put(nextSeqNum, System.currentTimeMillis());
                nextSeqNum++;
            }
        }

        while (!Thread.currentThread().isInterrupted() && (window.size() > 0 || nextSeqNum <= numMessages)) {
            // Retransmit timed-out messages
            retransmitUnacknowledgedMessages();

            // Fill the window if possible
            synchronized (window) {
                while (window.size() < windowSize && nextSeqNum <= numMessages) {
                    window.add(nextSeqNum);
                    sendMessage(nextSeqNum);
                    sentMessages.put(nextSeqNum, System.currentTimeMillis());
                    nextSeqNum++;
                }
            }

            try {
                Thread.sleep(TIMEOUT / 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("All messages acknowledged. Sender terminating.");

        // Clean up
        ackListener.interrupt();
        socket.close();
    }



    private void sendMessage(int seqNum) {
        // Create message data
        String messageData = myId + ":" + seqNum;
        byte[] buf = messageData.getBytes();

        try {
            InetAddress receiverAddress = InetAddress.getByName(receiverHost.getIp());
            DatagramPacket packet = new DatagramPacket(buf, buf.length, receiverAddress, receiverHost.getPort());

            socket.send(packet);
            System.out.println("Message envoyé par " + myId + " contenant  " + seqNum);
            if(!loggedMessages.contains(seqNum)){
                logger.logSend(seqNum);
                loggedMessages.add(seqNum);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void retransmitUnacknowledgedMessages() {
        long currentTime = System.currentTimeMillis();
        synchronized (window) {
            for (Integer seqNum : window) {
                Long lastSentTime = sentMessages.get(seqNum);
                if (lastSentTime != null && currentTime - lastSentTime >= TIMEOUT) {
                    sendMessage(seqNum);
                    sentMessages.put(seqNum, currentTime);
                }
            }
        }
    }


    private void listenForAcks() {
        byte[] buf = new byte[256];

        while (!Thread.currentThread().isInterrupted()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try {
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());

                if (received.startsWith("ACK:")) {
                    int ackSeqNum = Integer.parseInt(received.substring(4));
                    System.out.println("Message reçu de receiver contenant ack " + ackSeqNum);
                    
                    synchronized (window) {
                        if (window.contains(ackSeqNum)) {
                            window.remove((Integer) ackSeqNum); // Remove the sequence number from the window
                            sentMessages.remove(ackSeqNum);

                            // Add the next sequence number to the window if there are more messages
                            if (nextSeqNum <= numMessages) {
                                window.add(nextSeqNum);
                                sendMessage(nextSeqNum);
                                sentMessages.put(nextSeqNum, System.currentTimeMillis());
                                nextSeqNum++;
                            }
                        }
                    }
                }

            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
