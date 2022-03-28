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

public class DnsClient implements DnsBase.Resolver{

    private static final long queryTimeout = 5_000;
    private long lastClearCacheTimestamp = new Date().getTime();
    private long lastClearQueryTimestamp = new Date().getTime();

    private final HashMap<String, InetAddress> dnsCache = new HashMap<>();
    private final HashMap<String, DnsBase.PendingQuery> dnsQuerySet = new HashMap<>();
    private InetAddress dnsServer = null;
    private DatagramChannel clientChannel = null;

    DatagramChannel getClientChannel() {
        return clientChannel;
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

    public void queryA(String hostname, DnsBase.ResultDelegate delegate) {
        if (DnsBase.IsIpString(hostname)) {
            try {
                InetAddress ipAddr = InetAddress.getByName(hostname);
                delegate.OnQueryResult(hostname, DnsBase.QueryResult.CACHED, ipAddr);
                return;
            } catch (UnknownHostException e) {}
        }

        InetAddress addr = dnsCache.get(hostname);
        if (addr != null) {
            delegate.OnQueryResult(hostname, DnsBase.QueryResult.CACHED, addr);
            return;
        }
        doQueryA(hostname, delegate);
    }

    public void doQueryA(String hostname, DnsBase.ResultDelegate delegate) {
        DnsBase.PendingQuery pendingQuery = dnsQuerySet.get(hostname);
        if (pendingQuery != null) {
            pendingQuery.delegateList.add(delegate);
            return;
        }
        pendingQuery = new DnsBase.PendingQuery();
        pendingQuery.delegateList.add(delegate);
        dnsQuerySet.put(hostname, pendingQuery);

        // do send request:
        DnsRequest request = new DnsRequest(hostname, QueryType.A);
        byte[] requestBytes = request.getRequest();
        try {
            clientChannel.send(ByteBuffer.wrap(request.getRequest()), new InetSocketAddress(dnsServer, 53));
        } catch (IOException e) {
            dispatchQueryResult(hostname, DnsBase.QueryResult.FAILED, null);
        }
    }

    public void checkResolve() {
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
                dispatchQueryResult(query, DnsBase.QueryResult.DONE, addr);
            } else {
                dispatchQueryResult(query, DnsBase.QueryResult.FAILED, addr);
            }
        }
    }

    public void removeTimeoutQueries(long now) {
        long timeout = queryTimeout;
        ArrayList<String> removeList = new ArrayList<>();
        for (Map.Entry<String, DnsBase.PendingQuery> entry : dnsQuerySet.entrySet()) {
            DnsBase.PendingQuery query = entry.getValue();
            if (query.startTimestamp + timeout < now) {
                removeList.add(entry.getKey());
            }
        }
        for (String key : removeList) {
            dispatchQueryResult(key, DnsBase.QueryResult.FAILED, null);
            dnsQuerySet.remove(key);
        }
    }

    public void poll()
    {
        doClearTimeout();
    }

    void doClearTimeout()
    {
        long now = new Date().getTime();
        if (now - lastClearCacheTimestamp >= 5 * 60_000) {
            dnsCache.clear();
            lastClearCacheTimestamp = now;
        }
        if (now - lastClearQueryTimestamp >= 1_000) {
            removeTimeoutQueries(now);
            lastClearQueryTimestamp = now;
        }
    }

    private void dispatchQueryResult(String hostname, DnsBase.QueryResult result, InetAddress address) {
        DnsBase.PendingQuery pendingQuery = dnsQuerySet.get(hostname);
        if (pendingQuery == null) {
            return;
        }
        for (DnsBase.ResultDelegate delegate : pendingQuery.delegateList) {
            delegate.OnQueryResult(hostname, result, address);
        }
        dnsQuerySet.remove(hostname);
    }

}
