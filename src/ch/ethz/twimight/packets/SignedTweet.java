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
		this.signature = sign;
		
	}
	
	/* Concatenates all fields over which we want to sign a tweet and returns the hash */
	public int hashCode() {
		// We sign the tweet, user name, user id, hash and timestamp
		String serializedInfo = status.concat(" " + user + userId + id + created);
		return serializedInfo.hashCode();
		
	}
	
	public boolean equals(SignedTweet tweet) {
		if ( this.status.equals(tweet.status) && this.user.equals(tweet.user) ) {
			if (this.created == tweet.created && this.id == tweet.id )
				return true;
		}
		return false;
	}

	
}
