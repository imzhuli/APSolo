package com.solo.ximple;

public class ProxyStatus {

    public static final long initIdleTimeout = 3000;

	public static final long maxInitIdleTimeout = 60 * 60_000;
    public static long lastCheckTime = 0;
    public static String oldIp = "";
    public static String outIp = "";
    public static GeoInfo geoInfo = null;
    public static Phase currentPhase = Phase.Init;

    public enum Phase {
        Init, IpCheck, UpdatingGeoInfo, ConfigReady,
    }

}
