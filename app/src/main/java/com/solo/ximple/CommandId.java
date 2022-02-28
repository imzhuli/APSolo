package com.solo.ximple;


public class CommandId {

	public static final int KeepAlive     = 0x00; // no response needed
	
	public static final int Challenge = 0x01; // to config center
	public static final int ChallengeResp = 0x02;

	public static final int ChallengeGeo     = 0x03; // to config center
	public static final int ChallengeGeoResp = 0x04;

	public static final int NewConnection = 0x05;
	public static final int CloseConnection = 0x06;
	public static final int ConnectionPayload = 0x07;
	public static final int CheckKey = 0x08;
	public static final int NewHostConnection = 0x09;

	public static final int ServerCheck =0x00FF;
	public static final int ErrorLogReport = 0xFFFF;

}
