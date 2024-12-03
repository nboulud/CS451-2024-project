package cs451;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DeliveredMessage {
    private final int senderId;
    private final int packetHash;
    List<Map.Entry<Integer, Integer>> messagesPairs;

    public DeliveredMessage(List<Map.Entry<Integer, Integer>> messagesPairs, int senderId) {
        this.senderId = senderId;
        this.messagesPairs= messagesPairs;
        this.packetHash = computePacketHash(messagesPairs);
    }

    private int computePacketHash(List<Map.Entry<Integer, Integer>> messagesPairs) {
        int hash = 1;
        for (Map.Entry<Integer, Integer> entry : messagesPairs) {
            hash = 31 * hash + entry.getKey();
            hash = 31 * hash + entry.getValue();
        }
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeliveredMessage that = (DeliveredMessage) o;
        return senderId == that.senderId && packetHash == that.packetHash;
    }

    @Override
    public int hashCode() {
        int result = senderId;
        result = 31 * result + packetHash;
        return result;
    }
}
