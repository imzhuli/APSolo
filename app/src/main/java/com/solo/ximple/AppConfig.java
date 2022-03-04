package com.solo.ximple;

public class AppConfig {

    public static final String Ipplus360CoordSystem = "WGS84";
    // public static final String[] CC_HostAddrList = {"120.77.66.5:16000"}; // bj-test
    public static final String[] CC_HostAddrList = {"113.105.101.59:16660"}; // of-test
    // public static final String[] CC_HostAddrList = {"bmw.bestipip.com:16000"}; // of-test
    // public static final String[] CC_HostAddrList = {"39.108.119.236:16660"}; // lx
    public static final int CC_ChallangeSocketTimeout = 5_000;
    // updated by requests:
    public static final int ProxyTimeout = 1 * 60_000;
    public static long Version=2_3_0304_100_01L;
    public static boolean LogEnable = true;
}
