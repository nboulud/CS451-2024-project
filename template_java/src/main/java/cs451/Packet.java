package cs451;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Packet {
    List<Map.Entry<Integer, Integer>> messagesPairs;
    int destinationId;
    int nature = 2; //2 means data et 0 means ack

    Packet(List<Map.Entry<Integer, Integer>> messagesPairs, int destinationId) {
        this.messagesPairs = messagesPairs;
        this.destinationId = destinationId;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // Check for reference equality
        if (o == null || getClass() != o.getClass()) return false; // Check for type equality
        Packet packet = (Packet) o;
        return Objects.equals(messagesPairs, packet.messagesPairs) && destinationId==packet.destinationId; // Compare messagesPairs for equality
    }

    @Override
    public int hashCode() {
        return Objects.hash(messagesPairs, destinationId); // Generate hash code based on messagesPairs
    }

    @Override
    public String toString() {
        return "Packet{" +
            "messagesPairs=" + messagesPairs +
            ", destinationId=" + destinationId +
            '}';
    }
}
