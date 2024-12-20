
package cs451;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class Main {

    private static Logger logger;
    private static Broadcaster broadcaster;
    private static DatagramSocket socket;
    private static PerfectLink perfectLink;

    private static void handleSignal() {
        // Immediately stop network packet processing
        System.out.println("Immediately stopping network packet processing.");

        if (broadcaster != null) {
            broadcaster.interrupt();
        }

        // Close socket
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        // Write/flush output file if necessary
        System.out.println("Writing output.");
        if (logger != null) {
            logger.close();
        }
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::handleSignal));
    }

    public static void main(String[] args) throws InterruptedException {
        Parser parser = new Parser(args);
        parser.parse();

        initSignalHandlers();

        int numMessages = 0;

        // Parse the configuration file
        try (BufferedReader br = new BufferedReader(new FileReader(parser.config()))) {
            String line = br.readLine();
            numMessages = Integer.parseInt(line.trim());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


        String debugPath = parser.output() + ".debug";

        // Initialize logger
        logger = new Logger(parser.output(), debugPath);

        // Initialize socket
        try {
            Host myHost = parser.hosts().get(parser.myId() - 1);
            socket = new DatagramSocket(myHost.getPort());
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        // Initialize hosts map
        List<Host> hostsList = parser.hosts();
        Map<Integer, Host> hosts = new HashMap<>();
        for (Host host : hostsList) {
            hosts.put(host.getId(), host);
        }

        //broadcaster = new Broadcaster(null, hostsList, parser.myId(), logger, numMessages);

        // Initialize PerfectLink
        perfectLink = new PerfectLink(socket, parser.myId(), hosts, logger, broadcaster);

        int myId = parser.myId();               

        Host myHost = parser.hosts().get(myId - 1);
        perfectLink.start();

        Host receiverHost = parser.hosts().get(0);
        
        if (myHost.getId() != 1) {
            for (int i = 1; i <= numMessages; i++) {
                Message msg = new Message(i, myId, myId, receiverHost.getId(), 2);
                perfectLink.send(msg);
            }
        }

    }
}
