package com.solo.ximple.dns;

import java.net.InetAddress;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class DnsBase {
    private static final String ipv4Pattern = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";
    private static final String ipv6Pattern1 = "([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}";
    private static final String ipv6Pattern2 = "((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)";
    /***
     * Check Ip format
     */
    private static Pattern VALID_IPV4_PATTERN = null;
    private static Pattern VALID_IPV6_PATTERN1 = null;
    private static Pattern VALID_IPV6_PATTERN2 = null;

    static {
        try {
            VALID_IPV4_PATTERN = Pattern.compile(ipv4Pattern, Pattern.CASE_INSENSITIVE);
            VALID_IPV6_PATTERN1 = Pattern.compile(ipv6Pattern1, Pattern.CASE_INSENSITIVE);
            VALID_IPV6_PATTERN2 = Pattern.compile(ipv6Pattern2, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            System.out.println("Neither");
        }
    }

    public static boolean IsIpString(String ipStr) {
        if (VALID_IPV4_PATTERN.matcher(ipStr).matches()) {
            return true;
        }
        if (VALID_IPV6_PATTERN1.matcher(ipStr).matches()) {
            return true;
        }
        return VALID_IPV6_PATTERN2.matcher(ipStr).matches();
    }

    public static class PendingQuery {
        String name = null;
        long startTimestamp = new Date().getTime();
        ArrayList<ResultDelegate> delegateList = new ArrayList<>();
    }

    public enum QueryResult {
        CACHED, DONE, FAILED
    }

    public interface ResultDelegate {
        void OnQueryResult(String hostKey, QueryResult result, InetAddress address);
    }

    public interface Resolver {
        boolean init(String [] servers, Object appContextObject);
        boolean bindSelector(Selector selector); // if resolver requires a resolver
        void poll();        // called every loop
        void checkResolve(); // on selector event (dns udp event)
        void clean();
        void queryA(String hostname, ResultDelegate delegate);
    }

}
