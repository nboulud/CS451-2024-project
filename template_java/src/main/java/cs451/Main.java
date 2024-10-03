package cs451;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import Sende

public class Main {

    private static Logger logger;

    private static void handleSignal() {
        //immediately stop network packet processing
        System.out.println("Immediately stopping network packet processing.");

        if (sender != null) {
            sender.interrupt();
        }
        if (receiver != null) {
            receiver.interrupt();
        }
        if (logger != null) {
            logger.close();
        }

        // Close sockets
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
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                handleSignal();
            }
        });
    }
    


    public static void main(String[] args) throws InterruptedException {
        Parser parser = new Parser(args);
        parser.parse();

        logger = new Logger(parser.output());

        initSignalHandlers();

        int m = 0;
        int receiverId = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(parser.config()))) {
            String line = br.readLine();
            String[] parts = line.trim().split("\\s+");
            m = Integer.parseInt(parts[0]);
            receiverId = Integer.parseInt(parts[1]);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        boolean isSender = (parser.myId() != receiverId);

        DatagramSocket socket = null;
        try {
            Host myHost = parser.hosts().get(parser.myId() - 1);
            socket = new DatagramSocket(myHost.getPort());
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        if (isSender) {
            Sender sender = new Sender(socket, parser.hosts(), receiverId, m, parser.myId(), parser.output()), logger;
            sender.start();
        }

        if (!isSender) {
            Receiver receiver = new Receiver(socket, parser.hosts(), parser.myId(), parser.output());
            receiver.start();
        }

        if (isSender) {
            sender.start();
        } else {
            receiver.start();
        }

        try {
            if (isSender) {
                sender.join();
            } else {
                receiver.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // example
        long pid = ProcessHandle.current().pid();
        System.out.println("My PID: " + pid + "\n");
        System.out.println("From a new terminal type `kill -SIGINT " + pid + "` or `kill -SIGTERM " + pid + "` to stop processing packets\n");

        System.out.println("My ID: " + parser.myId() + "\n");
        System.out.println("List of resolved hosts is:");
        System.out.println("==========================");
        for (Host host: parser.hosts()) {
            System.out.println(host.getId());
            System.out.println("Human-readable IP: " + host.getIp());
            System.out.println("Human-readable Port: " + host.getPort());
            System.out.println();
        }
        System.out.println();

        System.out.println("Path to output:");
        System.out.println("===============");
        System.out.println(parser.output() + "\n");

        System.out.println("Path to config:");
        System.out.println("===============");
        System.out.println(parser.config() + "\n");

        System.out.println("Doing some initialization\n");

        System.out.println("Broadcasting and delivering messages...\n");

        // After a process finishes broadcasting,
        // it waits forever for the delivery of messages.
        while (true) {
            // Sleep for 1 hour
            Thread.sleep(60 * 60 * 1000);
        }
    }
}
