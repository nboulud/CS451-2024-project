package cs451;

import java.util.Objects;

public class Message {

    public final int seqNum;
    public final int creatorId;
    public final int senderId;
    public final int destinationId;
    public final boolean is_ack;
    public final boolean is_ack_urb;
    public final boolean is_data;
    
    public Message(int seqNum, int creatorId, int senderId, int destinationId, boolean is_ack, boolean is_ack_urb, boolean is_data){
        this.seqNum = seqNum;
        this.creatorId = creatorId;
        this.senderId = senderId;
        this.destinationId =destinationId;
        this.is_ack = is_ack;
        this.is_ack_urb = is_ack_urb;
        this.is_data = is_data;
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
               creatorId == message.creatorId &&
               senderId == message.senderId &&
               destinationId == message.destinationId &&
               is_ack == message.is_ack &&
               is_ack_urb == message.is_ack_urb &&
               is_data == message.is_data;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seqNum, creatorId, senderId, destinationId, is_ack, is_ack_urb, is_data);
    }

    @Override
    public String toString() {
        return "Message{" +
               "seqNum=" + seqNum +
               ", creatorId=" + creatorId +
               ", senderId=" + senderId +
               ", destinationId=" + destinationId +
               ", is_ack=" + is_ack +
               ", is_ack_urb=" + is_ack_urb +
               ", is_data=" + is_data +
               '}';
    }
}
