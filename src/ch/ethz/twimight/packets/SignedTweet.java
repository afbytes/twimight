package ch.ethz.twimight.packets;

import java.io.Serializable;
import java.security.interfaces.RSAPublicKey;



public class SignedTweet implements Serializable  {
	public RSAPublicKey publicKey;
	public byte[] signature;
	public long id, created, userId;		
	public String status,user;		
	public int hopCount,isFromServer, hasBeenSent;			
	
	
	public SignedTweet(long id, long created, String status, String user,long userId, int isFromServer, int hasBeenSent,
			int hopCount,RSAPublicKey publicKey, byte[] sign) {
		
		this.created = created;
		this.id = id;
		this.userId = userId;
		this.status = status;
		this.user = user;
		this.isFromServer = isFromServer;
		this.hasBeenSent = hasBeenSent;
		this.hopCount = hopCount;
		this.publicKey = publicKey;		
		signature = sign;
		
	}
	
	public int hashCode() {
		String string = status.concat(" " + user + userId + id + created);
		return string.hashCode();
		
	}
	
	public boolean equals(SignedTweet tweet) {
		if ( this.status.equals(tweet.status) && this.user.equals(tweet.user) ) {
			if (this.created == tweet.created && this.id == tweet.id )
				return true;
		}
		return false;
	}

	
}
