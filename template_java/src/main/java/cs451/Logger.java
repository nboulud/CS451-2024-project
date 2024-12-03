package cs451;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class Logger {
    private BufferedWriter writer;
    private final Object lock = new Object();
    private final String debugPath;

    public Logger(String outputPath, String debugPath) {
        try {
            Files.deleteIfExists(Paths.get(outputPath));
            Files.createFile(Paths.get(outputPath));
            writer = new BufferedWriter(new FileWriter(outputPath, true));
            Files.deleteIfExists(Paths.get(debugPath));
            Files.createFile(Paths.get(debugPath));
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.debugPath = debugPath;
    }

    public synchronized void logSend(int sequenceNumber) {
        synchronized(lock){
            try {
                writer.write("b " + sequenceNumber + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void logDeliver(Map.Entry<Integer, Integer> pair) {
        synchronized(lock){
            try {
                writer.write("d " + pair.getKey() + " " + pair.getValue() + "\n");
                
            } catch (IOException e) {
                e.printStackTrace();
            }
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

    public synchronized void logDebug(String string) {
        synchronized(lock){
            try {
                BufferedWriter writer_debug = new BufferedWriter(new FileWriter(debugPath, true));
                writer_debug.write(string + "\n" + "\n");
                writer_debug.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
