package cs451;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Logger {
    private BufferedWriter writer;

    public Logger(String outputPath) {
        try {
            writer = new BufferedWriter(new FileWriter(outputPath, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void logSend(int sequenceNumber) {
        try {
            writer.write("b " + sequenceNumber + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void logDeliver(int senderId, int sequenceNumber) {
        try {
            writer.write("d " + senderId + " " + sequenceNumber + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
