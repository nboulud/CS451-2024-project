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
        byte[] buf = new byte[1024]; // Increase buffer size to handle larger packets

        while (!Thread.currentThread().isInterrupted()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try {
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength());

                // Parse the message, which may contain multiple messages separated by ';'
                String[] messageParts = received.split(";");
                if (messageParts.length == 0) {
                    // No valid messages received
                    continue;
                }

                Set<Integer> ackSeqNums = new HashSet<>(); // To collect sequence numbers to acknowledge

                for (String message : messageParts) {
                    // Each message is in the format "senderId:seqNum"
                    String[] parts = message.split(":");
                    if (parts.length != 2) {
                        // Invalid message format, skip this message
                        continue;
                    }

                    int senderId = Integer.parseInt(parts[0]);
                    int seqNum = Integer.parseInt(parts[1]);
                    String messageKey = senderId + ":" + seqNum;
                    //System.out.println("Message received from " + senderId + " containing " + seqNum);

                    // Check for duplicates
                    synchronized (deliveredMessages) {
                        if (!deliveredMessages.contains(messageKey)) {
                            // Deliver the message
                            deliveredMessages.add(messageKey);

                            // Log the delivery event
                            //logger.logDeliver(senderId, seqNum);

                            // Add seqNum to acknowledgments
                            ackSeqNums.add(seqNum);
                        }
                        // Else, duplicate message; ignore
                    }
                }

                // Send acknowledgments for the received messages
                if (!ackSeqNums.isEmpty()) {
                    StringBuilder ackMessageBuilder = new StringBuilder("ACK:");
                    for (Integer ackSeqNum : ackSeqNums) {
                        ackMessageBuilder.append(ackSeqNum).append(";");
                    }
                    String ackMessage = ackMessageBuilder.toString();
                    byte[] ackBuf = ackMessage.getBytes();

                    InetAddress senderAddress = packet.getAddress();
                    int senderPort = packet.getPort();

                    DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length, senderAddress, senderPort);
                    socket.send(ackPacket);
                    //System.out.println("Ack " + ackSeqNums + " sent to " + senderAddress);
                }

            } catch (SocketException e) {
                // Socket closed; exit the loop
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //System.out.println("All messages received. Receiver terminating.");
    }
}