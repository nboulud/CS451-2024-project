package cs451;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Logger {
    private BufferedWriter writer;

    public Logger(String outputPath) {
        try {
            Files.deleteIfExists(Paths.get(outputPath));
            Files.createFile(Paths.get(outputPath));
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

    public synchronized void logString (String string){
        try {
            writer.write(string + "\n");
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
