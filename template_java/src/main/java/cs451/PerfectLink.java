package cs451;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class PerfectLink {
    private final DatagramSocket socket;
    private final int myId;
    private final Logger logger;

    private final Map<Integer, Host> hosts;

    // Sender-side data structures
    private final ConcurrentHashMap<String, PendingMessage> sendQueue = new ConcurrentHashMap<>();
    private final int RETRANSMIT_TIMEOUT = 100; // in milliseconds

    // Receiver-side data structures
    private final Set<String> deliveredMessages = ConcurrentHashMap.newKeySet(); // messageId
    private final BlockingQueue<String> deliverQueue = new LinkedBlockingQueue<>();

    // Listener thread
    private Thread listenerThread;
    private Thread retransmitThread;

    private static class PendingMessage {
        byte[] buf;
        Host destination;
        long lastSentTimestamp;

        PendingMessage(byte[] buf, Host destination, long lastSentTimestamp) {
            this.buf = buf;
            this.destination = destination;
            this.lastSentTimestamp = lastSentTimestamp;
        }
    }

    public PerfectLink(DatagramSocket socket, int myId, Map<Integer, Host> hosts, Logger logger) {
        this.socket = socket;
        this.myId = myId;
        this.hosts = hosts;
        this.logger = logger;
    }

    public void start() {
        // Start the listener thread
        listenerThread = new Thread(this::listen);
        listenerThread.start();

        // Start the retransmission thread
        retransmitThread = new Thread(this::retransmitMessages);
        retransmitThread.start();
    }

    public void stop() {
        // Interrupt the listener thread
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        if (retransmitThread != null) {
            retransmitThread.interrupt();
        }

        // Close the socket
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public void send(Host destination, String message) {
        String messageId = generateMessageId(myId, destination.getId(), message);

        // Prepare the message bytes
        byte[] buf = ("DATA:" + message).getBytes();

        // Create a PendingMessage
        PendingMessage pendingMessage = new PendingMessage(buf, destination, System.currentTimeMillis());

        // Add to sendQueue
        sendQueue.put(messageId, pendingMessage);

        // Send the message
        sendMessage(destination, buf);
    }

    public String deliver() {
        try {
            return deliverQueue.take(); // Blocks if the queue is empty
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void listen() {
        byte[] buf = new byte[4096]; // Adjust buffer size if needed

        while (!Thread.currentThread().isInterrupted()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try {
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());

                InetAddress senderAddress = packet.getAddress();
                int senderPort = packet.getPort();

                // Handle the received message
                handleReceivedMessage(received, senderAddress, senderPort);
            } catch (SocketException e) {
                // Socket closed; exit loop
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleReceivedMessage(String received, InetAddress senderAddress, int senderPort) {
        if (received.startsWith("DATA:")) {
            String message = received.substring(5); // Remove "DATA:" prefix

            // Extract messageId
            String[] parts = message.split(":");
            int senderId = Integer.parseInt(parts[0]);
            int seqNum = Integer.parseInt(parts[1]);
            int destId = myId; // We are the destination

            String AckMessageId = senderId + ":" + destId + ":" + seqNum;

            // Send acknowledgment
            sendAck(senderAddress, senderPort, AckMessageId);

            String deliveryMessageId = senderId + ":" + seqNum;

            // Check if message has been delivered before
            if (!deliveredMessages.contains(deliveryMessageId)) {
                deliveredMessages.add(deliveryMessageId);

                // Enqueue the message  delivery
                deliverQueue.add(message);
            }
        } else if (received.startsWith("ACK:")) {
            String messageId = received.substring(4); // Remove "ACK:" prefix

            // Remove from send queue
            sendQueue.remove(messageId);
        }
    }

    private void sendAck(InetAddress address, int port, String messageId) {
        String ackMessage = "ACK:" + messageId;
        byte[] ackBuf = ackMessage.getBytes();

        DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length, address, port);

        try {
            socket.send(ackPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void retransmitMessages() {
        while (!Thread.currentThread().isInterrupted()) {
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<String, PendingMessage> entry : sendQueue.entrySet()) {
                String messageId = entry.getKey();
                PendingMessage pendingMessage = entry.getValue();

                if (currentTime - pendingMessage.lastSentTimestamp >= RETRANSMIT_TIMEOUT) {
                    // Retransmit the message
                    sendMessage(pendingMessage.destination, pendingMessage.buf);

                    // Update the timestamp
                    pendingMessage.lastSentTimestamp = currentTime;
                }
            }

            try {
                Thread.sleep(RETRANSMIT_TIMEOUT / 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void sendMessage(Host destination, byte[] buf) {
        try {
            InetAddress address = InetAddress.getByName(destination.getIp());
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, destination.getPort());
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String generateMessageId(int senderId, int destId, String message) {
        // Assuming message includes sequence number
        String[] parts = message.split(":");
        String seqNum = parts[1];

        return senderId + ":" + destId + ":" + seqNum;
    }
}
