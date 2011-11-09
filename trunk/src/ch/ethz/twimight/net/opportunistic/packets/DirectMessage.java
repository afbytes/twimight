package ch.ethz.twimight.net.opportunistic.packets;

import java.io.Serializable;

public class DirectMessage implements Serializable {
	
	public long id;
	public long created_at;
	public byte[] message,sender;
	public String recipientUser;
	public int hasBeenSent;
	
	public DirectMessage(long id, long created_at, byte[] message, String recipientUser, 
			byte[] sender, int hasBeenSent) {
		this.created_at = created_at;
		this.id = id;
		this.message = message;
		this.recipientUser = recipientUser;
		this.sender = sender;
		this.hasBeenSent = hasBeenSent;
	}
	
	
}
