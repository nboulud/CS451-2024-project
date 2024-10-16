package cs451;

import java.io.IOException;
import java.net.*;
import java.util.HashSet;
import java.util.Set;

public class Receiver extends Thread {
    private final DatagramSocket socket;
    private final int myId;
    private final Logger logger;
    


    // To track delivered messages (senderId:sequenceNumber)
    private final Set<String> deliveredMessages;
 

    public Receiver(DatagramSocket socket, int myId, Logger logger) {
        this.socket = socket;
        this.myId = myId;
        this.logger = logger;
        this.deliveredMessages = new HashSet<>();
        
    }

    @Override
    public void run() {
        byte[] buf = new byte[256];

        while (!Thread.currentThread().isInterrupted() ) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try {
                socket.receive(packet);
                
                String received = new String(packet.getData(), 0, packet.getLength());

                // Parse the message
                String[] parts = received.split(":");
                if (parts.length != 2) {
                    // Invalid message format
                    continue;
                }

                int senderId = Integer.parseInt(parts[0]);
                int seqNum = Integer.parseInt(parts[1]);
                String messageKey = senderId + ":" + seqNum;
                System.out.println("Message reçu de " + senderId + " contenant  " + seqNum);

                // Check for duplicates
                synchronized (deliveredMessages) {
                    if (!deliveredMessages.contains(messageKey)) {
                        // Deliver the message
                        deliveredMessages.add(messageKey);

                        // Log the delivery event
                        logger.logDeliver(senderId, seqNum);

                        String ackMessage = "ACK:" + seqNum;
                        byte[] ackBuf = ackMessage.getBytes();

                        InetAddress senderAddress = packet.getAddress();
                        int senderPort = packet.getPort();

                        DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length, senderAddress, senderPort);
                        socket.send(ackPacket);
                        System.out.println("Ack " + seqNum + " envoyé à " + senderId);

                    }
                    // Else, duplicate message; ignore
                }

                
            } catch (SocketException e) {
                // Socket closed; exit the loop
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("All messages received. Receiver terminating.");
    }
}
