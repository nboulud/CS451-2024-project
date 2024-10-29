package cs451;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Sender extends Thread {
    private final DatagramSocket socket;
    private final Host receiverHost;
    private final int numMessages;
    private final int myId;
    private final Logger logger;

    private static final int TIMEOUT = 100;
    private final List<Integer> window = Collections.synchronizedList(new LinkedList<>());
    private final ConcurrentHashMap<Integer, Long> sentMessages = new ConcurrentHashMap<>();
    private int nextSeqNum = 1; // Next sequence number to send
    private final Set<Integer> loggedMessages = ConcurrentHashMap.newKeySet();

    // Make windowSize variable
    private int windowSize;
    private int maxWindowSize = 50000;
    private double cwnd;
    private int numberOfAcksForIncreasing = 0;
    private int numberOfAcksForDecreasing = 0;

    // Maximum number of messages per packet
    private static final int MAX_MESSAGES_PER_PACKET = 8;

    public Sender(DatagramSocket socket, Host receiverHost, int numMessages, int myId, Logger logger, int initialWindowSize) {
        this.socket = socket;
        this.receiverHost = receiverHost;
        this.numMessages = numMessages;
        this.myId = myId;
        this.logger = logger;
        this.cwnd = initialWindowSize;
        this.windowSize = (int) Math.floor(cwnd);
    }

    @Override
    public void run() {
        // Start a thread to listen for acknowledgments
        Thread ackListener = new Thread(this::listenForAcks);
        ackListener.start();

        // Initialize the window
        synchronized (window) {
            while (window.size() < windowSize && nextSeqNum <= numMessages) {
                window.add(nextSeqNum);
                sentMessages.put(nextSeqNum, System.currentTimeMillis());
                nextSeqNum++;
            }
            // Send initial batch
            sendMessages(new ArrayList<>(window));
        }

        while (!Thread.currentThread().isInterrupted() && (window.size() > 0 || nextSeqNum <= numMessages)) {
            // Retransmit timed-out messages
            retransmitUnacknowledgedMessages();

            // Fill the window if possible
            synchronized (window) {
                while (window.size() < windowSize && nextSeqNum <= numMessages) {
                    window.add(nextSeqNum);
                    sentMessages.put(nextSeqNum, System.currentTimeMillis());
                    nextSeqNum++;
                }
                // Send batch of new messages
                sendMessages(new ArrayList<>(window));
            }

            try {
                Thread.sleep(TIMEOUT / 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        //System.out.println("All messages acknowledged. Sender terminating.");

        // Clean up
        ackListener.interrupt();
        socket.close();
    }

    private void sendMessages(List<Integer> seqNums) {
        // Split the list into batches of MAX_MESSAGES_PER_PACKET
        for (int i = 0; i < seqNums.size(); i += MAX_MESSAGES_PER_PACKET) {
            int end = Math.min(i + MAX_MESSAGES_PER_PACKET, seqNums.size());
            List<Integer> batch = seqNums.subList(i, end);
            sendMessageBatch(batch);
        }
    }

    private void sendMessageBatch(List<Integer> seqNums) {
        // Create message data
        StringBuilder messageDataBuilder = new StringBuilder();
        for (Integer seqNum : seqNums) {
            messageDataBuilder.append(myId).append(":").append(seqNum).append(";");
            // Log the message if not already logged
            if (!loggedMessages.contains(seqNum)) {
                logger.logSend(seqNum);
                loggedMessages.add(seqNum);
            }
        }
        String messageData = messageDataBuilder.toString();
        byte[] buf = messageData.getBytes();

        try {
            InetAddress receiverAddress = InetAddress.getByName(receiverHost.getIp());
            DatagramPacket packet = new DatagramPacket(buf, buf.length, receiverAddress, receiverHost.getPort());

            socket.send(packet);
            //System.out.println("Batch sent by " + myId + " containing " + seqNums);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void retransmitUnacknowledgedMessages() {
        long currentTime = System.currentTimeMillis();
        synchronized (window) {
            List<Integer> timedOutSeqNums = new ArrayList<>();
            boolean congestionDetected = false;

            for (Integer seqNum : window) {
                Long lastSentTime = sentMessages.get(seqNum);
                if (lastSentTime != null && currentTime - lastSentTime >= TIMEOUT) {
                    timedOutSeqNums.add(seqNum);
                    congestionDetected = true;
                }
            }

            if (congestionDetected) {
                numberOfAcksForDecreasing++;
                if (numberOfAcksForDecreasing > 200) {
                    decreaseWindow();
                    numberOfAcksForDecreasing = 0;
                }
            }

            // Retransmit timed-out messages in batches
            if (!timedOutSeqNums.isEmpty()) {
                sendMessages(timedOutSeqNums);
                long currentTimestamp = System.currentTimeMillis();
                for (Integer seqNum : timedOutSeqNums) {
                    sentMessages.put(seqNum, currentTimestamp);
                }
            }
        }
    }

    private void increaseWindow() {
        synchronized (window) {
            cwnd = Math.min(cwnd * 2, numMessages);
            if (cwnd >= maxWindowSize) {
                windowSize = maxWindowSize;
            } else {
                windowSize = (int) Math.floor(cwnd);
            }

            // No need to adjust window here; it will be adjusted in the main loop
        }
    }

    private void decreaseWindow() {
        synchronized (window) {
            cwnd = Math.max(cwnd / 2, 1.0);
            windowSize = (int) Math.floor(cwnd);

            // Remove extra messages from the window if window size decreased
            while (window.size() > windowSize) {
                int removedSeqNum = window.remove(window.size() - 1);
                sentMessages.remove(removedSeqNum);
            }
        }
    }

    private void listenForAcks() {
        byte[] buf = new byte[1024];

        while (!Thread.currentThread().isInterrupted()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try {
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());

                if (received.startsWith("ACK:")) {
                    String ackData = received.substring(4);
                    String[] ackSeqNums = ackData.split(";");
                    for (String ackSeqNumStr : ackSeqNums) {
                        int ackSeqNum = Integer.parseInt(ackSeqNumStr);
                        //System.out.println("Received ACK for " + ackSeqNum);

                        synchronized (window) {
                            if (window.contains(ackSeqNum)) {
                                window.remove((Integer) ackSeqNum);
                                sentMessages.remove(ackSeqNum);
                                numberOfAcksForIncreasing++;

                                if (numberOfAcksForIncreasing > 200) {
                                    increaseWindow();
                                    numberOfAcksForIncreasing = 0;
                                }
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