package cs451;

import java.net.DatagramSocket;
import java.util.List;

public class Receiver extends Thread {
    private DatagramSocket socket;
    private List<Host> hosts;
    private int myId;
    private String outputPath;
    private Logger logger;

    public Receiver(DatagramSocket socket, List<Host> hosts, int myId, Logger logger ) {
        
        this.logger = logger;
    }

    @Override
    public void run() {
        
        
    }
}
