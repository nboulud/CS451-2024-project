package cs451;

import java.io.IOException;
import java.net.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class Sender extends Thread {
    private final DatagramSocket socket;
    private final Host receiverHost;
    private final int numMessages;
    private final int myId;
    private final Logger logger;


    private static final int TIMEOUT = 100;
 
    private final ConcurrentHashMap<Integer, Long> unacknowledgedMessages;

    private final Set<Integer> loggedMessages = ConcurrentHashMap.newKeySet();


    public Sender(DatagramSocket socket, Host receiverHost, int numMessages, int myId, Logger logger) {
        this.socket = socket;
        this.receiverHost = receiverHost;
        this.numMessages = numMessages;
        this.myId = myId;
        this.logger = logger;
        this.unacknowledgedMessages = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {
        // Start a thread to listen for acknowledgments
        Thread ackListener = new Thread(this::listenForAcks);
        ackListener.start();

        // Send messages
        for (int seqNum = 1; seqNum <= numMessages; seqNum++) {
            sendMessage(seqNum);
            unacknowledgedMessages.put(seqNum, System.currentTimeMillis());
        }

        // Retransmission loop
        while (!unacknowledgedMessages.isEmpty() && !Thread.currentThread().isInterrupted()) {
            long currentTime = System.currentTimeMillis();

            for (Integer seqNum : unacknowledgedMessages.keySet()) {
                long lastSentTime = unacknowledgedMessages.get(seqNum);

                if (currentTime - lastSentTime >= TIMEOUT) {
                    sendMessage(seqNum);
                    unacknowledgedMessages.put(seqNum, currentTime);
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
            System.out.println("Message envoyé par " + myId + "contenant  " + seqNum);
            if(!loggedMessages.contains(seqNum)){
                logger.logSend(seqNum);
                loggedMessages.add(seqNum);
            }

        } catch (IOException e) {
            e.printStackTrace();
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
                    unacknowledgedMessages.remove(ackSeqNum);
                    System.out.println("Message reçu de receiver contenant ack " + ackSeqNum);
                }

            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
