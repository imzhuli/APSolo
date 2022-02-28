package com.solo.ximple.dns;

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

public class DnsClient {

    private static final long queryTimeout = 3_000;
    private final HashMap<String, InetAddress> dnsCache = new HashMap<>();
    private final HashMap<String, PendingQuery> dnsQuerySet = new HashMap<>();
    private InetAddress dnsServer = null;
    private DatagramChannel clientChannel = null;

    DatagramChannel getClientChannel() {
        return clientChannel;
    }

    public boolean init(String[] servers) {
        if (servers == null) {
            servers = new DnsServersDetector(null).getServers();
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

    public void checkResolv()
    {
        byte[] recvBuf = new byte[4096];
        String query = "";
        while(true) {
            ByteBuffer bb = ByteBuffer.wrap(recvBuf);
            InetAddress addr = null;
            try {
                clientChannel.receive(bb);
                if (bb.position() == 0) {
                    break;
                }
                DnsResponse response = new DnsResponse(recvBuf);
                ArrayList<byte[]> subs = response.getQuery();
                for (int i = 0 ; i < subs.size(); ++i) {
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

    public void removeTimeoutQueries(long now)
    {
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
