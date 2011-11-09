package ch.ethz.twimight.net.opportunistic.packets;

import java.util.ArrayList;


public class TweetPacket extends AbstractPacket {
	
	public long timestamp;
	public ArrayList<SignedTweet> tweets ;
	public ArrayList<DirectMessage> directmessages;
	public long actualTweetNumber;
	
	
	public TweetPacket(long timestamp, ArrayList<SignedTweet> tweets,ArrayList<DirectMessage> direct, long number) {
		this.timestamp = timestamp;
		this.tweets = tweets;
		this.directmessages = direct;
		this.actualTweetNumber = number;
		closeConnection = true;
		
		type = DATA_PACKET;
	}
	
	
	
	
	

}
