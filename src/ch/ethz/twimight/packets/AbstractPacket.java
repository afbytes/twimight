package ch.ethz.twimight.packets;

import java.io.Serializable;

public abstract class AbstractPacket implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	//types of packets
	public static final int HELLO_FIRST_PACKET = 1 ;
	public static final int HELLO_SUCC_PACKET = 2 ;
	public static final int DATA_PACKET = 3 ;
	public static final int KEYS_PACKET = 4 ;

	public int type;	
	public boolean closeConnection;
	
}
