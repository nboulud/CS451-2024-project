package cs451;

import java.net.DatagramSocket;
import java.util.List;

public class Sender extends Thread {
    private DatagramSocket socket;
    private List<Host> hosts;
    private int receiverId;
    private int numMessages;
    private int myId;
    private String outputPath;
    private Logger logger;


    public Sender(DatagramSocket socket, List<Host> hosts, int receiverId, int numMessages, int myId, Logger logger) {
        this.logger = logger;
    }

    @Override
    public void run() {
        logger.logSend(1);
    }
}
