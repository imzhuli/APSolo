package com.solo.ximple.dns;

import com.solo.ximple.SyncedQueue;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DnsClientBySystem implements DnsBase.Resolver {

    private static class SystemQueryResult
    {
        String              hostname;
        DnsBase.QueryResult result;
        InetAddress         address = null;
    }

    private static final long queryTimeout = 3_000;
    private long lastClearCacheTimestamp = new Date().getTime();
    private long lastClearQueryTimestamp = new Date().getTime();

    private final HashMap<String, InetAddress> dnsCache = new HashMap<>();
    private final HashMap<String, DnsBase.PendingQuery> dnsQuerySet = new HashMap<>();
    private final SyncedQueue<SystemQueryResult> resultQueue = new SyncedQueue<>();
    private final List<DnsBase.PendingQuery> queryQueue = new LinkedList<>();
    private Thread queryThread;
    private AtomicBoolean killQueryThread = new AtomicBoolean();

    public boolean init(String [] servers, Object appContextObject) {
        return createQueryThread();
    }

    public boolean bindSelector(Selector selector) {
        return true;
    }

    public void checkResolve()
    {
    }

    public void clean() {
        stopQueryThread();
    }

    public void queryA(String hostname, DnsBase.ResultDelegate delegate) {
        if (DnsBase.IsIpString(hostname)) {
            try {
                InetAddress ipAddr = InetAddress.getByName(hostname);
                delegate.OnQueryResult(hostname, DnsBase.QueryResult.CACHED, ipAddr);
                return;
            }
            catch (UnknownHostException e) {}
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
        pendingQuery.name = hostname;
        pendingQuery.delegateList.add(delegate);
        dnsQuerySet.put(hostname, pendingQuery);

        synchronized (queryQueue) {
            queryQueue.add(pendingQuery);
            queryQueue.notify();
        }
    }

    private void queryThreadFunction() {
        while(true) {
            if (killQueryThread.get()) {
                break;
            }
            List<DnsBase.PendingQuery> queue = new ArrayList<>();
            synchronized (queryQueue) {
                if (queryQueue.isEmpty()) {
                    try {
                        queryQueue.wait();
                    } catch (InterruptedException e) {
                      continue;
                    }
                }
                queue.addAll(queryQueue);
                queryQueue.clear();
            }

            for (DnsBase.PendingQuery query : queue) {
                InetAddress address = null;
                SystemQueryResult result = new SystemQueryResult();
                result.hostname = query.name;
                try {
                    address = InetAddress.getByName(query.name);
                    result.result = DnsBase.QueryResult.DONE;
                    result.address = address;
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    result.result = DnsBase.QueryResult.FAILED;
                }
                resultQueue.put(result);
            }
        }
    }

    private boolean createQueryThread() {
        killQueryThread.set(false);
        queryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                queryThreadFunction();
            }
        });
        queryThread.start();
        return true;
    }

    private void stopQueryThread() {
        if (null == queryThread){
            return;
        }
        killQueryThread.set(true);
        queryThread.interrupt();
        while(true) {
            try {
                queryThread.join();
                break;
            } catch (InterruptedException e) {}
        }
        queryQueue.clear();
        queryThread = null;
        resultQueue.clear();
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

    public void poll()
    {
        doClearTimeout();
        LinkedList<SystemQueryResult> list = resultQueue.grabQueue();
        for (SystemQueryResult result : list) {
            dispatchQueryResult(result.hostname, result.result, result.address);
        }
    }

    private void dispatchQueryResult(String hostname, DnsBase.QueryResult result, InetAddress address) {
        DnsBase.PendingQuery pendingQuery = dnsQuerySet.get(hostname);
        if (pendingQuery == null) {
            return;
        }
        if (result == DnsBase.QueryResult.DONE && null != address) {
            dnsCache.put(hostname, address);
        }
        for (DnsBase.ResultDelegate delegate : pendingQuery.delegateList) {
            delegate.OnQueryResult(hostname, result, address);
        }
        dnsQuerySet.remove(hostname);
    }


}
