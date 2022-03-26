package com.solo.ximple.dns;

import android.content.Context;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class DnsClient {

    private static final long queryTimeout = 5_000;
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

    private final HashMap<String, InetAddress> dnsCache = new HashMap<>();
    private final HashMap<String, PendingQuery> dnsQuerySet = new HashMap<>();
    private InetAddress dnsServer = null;
    private DatagramChannel clientChannel = null;

    private static boolean IsIpString(String ipStr) {
        if (VALID_IPV4_PATTERN.matcher(ipStr).matches()) {
            return true;
        }
        if (VALID_IPV6_PATTERN1.matcher(ipStr).matches()) {
            return true;
        }
        return VALID_IPV6_PATTERN2.matcher(ipStr).matches();
    }

    DatagramChannel getClientChannel() {
        return clientChannel;
    }

    public boolean init(String[] servers) {
        return init(servers, null);
    }

    public boolean init(String[] servers, Object contextObject) {
        Context context = null;
        if (contextObject instanceof Context) {
            context = (Context) contextObject;
        }
        if (servers == null) {
            servers = new DnsServersDetector(context).getServers();
        }
        if (servers.length == 0) {
            return false;
        }
        try {
            dnsServer = InetAddress.getByName(servers[0]);
        } catch (UnknownHostException e) {
            return false;
        }
        if (clientChannel != null) {
            throw new RuntimeException("channel is already opened");
        }
        try {
            clientChannel = DatagramChannel.open();
            clientChannel.configureBlocking(false);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                clientChannel.bind(null);
            }
        } catch (IOException e) {
            clientChannel = null;
            dnsServer = null;
            return false;
        }
        return true;
    }

    public void clean() {
        if (clientChannel == null) {
            return;
        }
        try {
            clientChannel.close();
        } catch (IOException e) {
        }
        clientChannel = null;
    }

    public boolean bindSelector(Selector selector) {
        try {
            clientChannel.register(selector, SelectionKey.OP_READ, this);
        } catch (ClosedChannelException e) {
            return false;
        }
        return true;
    }

    public void clearCache() {
        dnsCache.clear();
    }

    public void queryA(String hostname, Delegate delegate) {
        if (IsIpString(hostname)) {
            try {
                InetAddress ipAddr = InetAddress.getByName(hostname);
                delegate.OnQueryResult(hostname, QueryResult.CACHED, ipAddr);
            }
            catch (UnknownHostException e)
            {}
        }

        InetAddress addr = dnsCache.get(hostname);
        if (addr != null) {
            delegate.OnQueryResult(hostname, QueryResult.CACHED, addr);
            return;
        }
        doQueryA(hostname, delegate);
    }

    public void doQueryA(String hostname, Delegate delegate) {
        PendingQuery pendingQuery = dnsQuerySet.get(hostname);
        if (pendingQuery != null) {
            pendingQuery.delegateList.add(delegate);
            return;
        }
        pendingQuery = new PendingQuery();
        pendingQuery.delegateList.add(delegate);
        dnsQuerySet.put(hostname, pendingQuery);
        // do send request:
        ++pendingQuery.retryTimes;

        DnsRequest request = new DnsRequest(hostname, QueryType.A);
        byte[] requestBytes = request.getRequest();
        try {
            clientChannel.send(ByteBuffer.wrap(request.getRequest()), new InetSocketAddress(dnsServer, 53));
        } catch (IOException e) {
            dispatchQueryResult(hostname, QueryResult.FAILED, null);
        }
    }

    public void checkResolv() {
        byte[] recvBuf = new byte[4096];
        String query = "";
        while (true) {
            ByteBuffer bb = ByteBuffer.wrap(recvBuf);
            InetAddress addr = null;
            try {
                clientChannel.receive(bb);
                if (bb.position() == 0) {
                    break;
                }
                DnsResponse response = new DnsResponse(recvBuf);
                ArrayList<byte[]> subs = response.getQuery();
                for (int i = 0; i < subs.size(); ++i) {
                    if (i == 0) {
                        query = new String(subs.get(i));
                    } else {
                        query = query + "." + new String(subs.get(i));
                    }
                }

                for (DnsRecord r : response.getAnswerRecords()) {
                    if (r.getQueryType() == QueryType.A) {
                        addr = InetAddress.getByName(r.getDomain());
                        break;
                    }
                }
            } catch (Exception e) {
                addr = null;
            }
            if (addr != null) {
                dispatchQueryResult(query, QueryResult.DONE, addr);
            } else {
                dispatchQueryResult(query, QueryResult.FAILED, addr);
            }
        }
    }

    public void removeTimeoutQueries(long now) {
        long timeout = queryTimeout;
        ArrayList<String> removeList = new ArrayList<>();
        for (Map.Entry<String, PendingQuery> entry : dnsQuerySet.entrySet()) {
            PendingQuery query = entry.getValue();
            if (query.startTimestamp + timeout < now) {
                removeList.add(entry.getKey());
            }
        }
        for (String key : removeList) {
            dispatchQueryResult(key, QueryResult.FAILED, null);
            dnsQuerySet.remove(key);
        }
    }

    private void dispatchQueryResult(String hostname, QueryResult result, InetAddress address) {
        PendingQuery pendingQuery = dnsQuerySet.get(hostname);
        if (pendingQuery == null) {
            return;
        }
        for (Delegate delegate : pendingQuery.delegateList) {
            delegate.OnQueryResult(hostname, result, address);
        }
        dnsQuerySet.remove(hostname);
    }
    public enum QueryResult {
        CACHED, DONE, FAILED
    }

    public interface Delegate {
        void OnQueryResult(String hostKey, QueryResult result, InetAddress address);
    }

    private static class PendingQuery {
        int retryTimes = 0;
        long startTimestamp = new Date().getTime();
        ArrayList<Delegate> delegateList = new ArrayList<>();
    }

}
