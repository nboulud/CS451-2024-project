package cs451;

import java.util.Objects;

public class Message {

    public final int seqNum;
    public final int creatorId;
    public final int senderId;
    public final int destinationId;
    public final int nature;
    
    public Message(int seqNum, int creatorId, int senderId, int destinationId, int nature){
        this.seqNum = seqNum;
        this.creatorId = creatorId;
        this.senderId = senderId;
        this.destinationId =destinationId;
        this.nature = nature;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Message message = (Message) obj;
        return seqNum == message.seqNum &&
               creatorId == message.creatorId;
               
    }

    @Override
    public int hashCode() {
        return Objects.hash(seqNum, creatorId);
    }

    @Override
    public String toString() {
        return "Message{" +
               "seqNum=" + seqNum +
               ", creatorId=" + creatorId +
               '}';
    }
}
