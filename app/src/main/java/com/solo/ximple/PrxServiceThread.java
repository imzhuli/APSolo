package com.solo.ximple;

import com.solo.ximple.dns.DnsClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrxServiceThread {

    public static class OutputDns implements DnsClient.Delegate {
        @Override
        public void OnQueryResult(String hostKey, DnsClient.QueryResult result, InetAddress address) {
            System.out.println("hostKey");
        }
    }

    private static final Map<Long, Connection> connectionMap = new HashMap<>();
    private static final AtomicBoolean KeepRunning = new AtomicBoolean();
    private static Selector selector = null;
    private static GeoInfo geoInfo = null;
    private static byte[] prxServerInfoKey = null;
    private static Connection prxServerConnection = null;
    private static boolean prxServerConnectionError = false;
    private static Exception threadErrorLog = null;
    private static long lastHeartBeat = 0;
    private static long lastServerCheck = 0;
    private static final DnsClient dnsClient = new DnsClient();
    private static long BanExpireTimeMS = 0;

    private static boolean init(InetSocketAddress address) {
        try {
            long Now = new Date().getTime();
            while(Now < BanExpireTimeMS) { try {
                    AppLog.D("Waiting for ban timeout");
                    Thread.sleep(60_000);
                    Now = new Date().getTime();
                } catch (InterruptedException e) {}
            }

            selector = Selector.open();
            geoInfo = null;
            prxServerInfoKey = null;
            prxServerConnection = new Connection();
            if (!prxServerConnection.init(-1, address, selector)) {
                prxServerConnection = null;
                selector.close();
                return false;
            }
            prxServerConnectionError = false;
            lastServerCheck = new Date().getTime();

            if (!dnsClient.init(null, AppConfig.appContextObject)) {
                return false;
            }
            if (!dnsClient.bindSelector(selector)) {
                dnsClient.clean();
            }
        } catch (IOException e) {
            AppLog.E("Failed to open selector");
            return false;
        }
        return true;
    }

    private static void clean() {
        for (Map.Entry<Long, Connection> entry : connectionMap.entrySet()) {
            entry.getValue().clean();
        }
        connectionMap.clear();
        /* prxServer */
        dnsClient.clean();
        lastServerCheck = 0;
        prxServerConnectionError = false;
        if (prxServerConnection != null) {
            prxServerConnection.clean();
            prxServerConnection = null;
        }
        prxServerInfoKey = null;
        geoInfo = null;
        try {
            selector.close();
        } catch (IOException e) {
            AppLog.E("Closing selector error");
        }
        selector = null;
    }

    private static boolean loopOnce() {
        long now = new Date().getTime();
        if (now - lastServerCheck >= 15 * 60_1000) { // server is not responsive
            AppLog.E("ServerCheck timeout");
            return false;
        }
        dnsClient.removeTimeoutQueries(now);
        try {
            if (0 == selector.select(10)) {
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        } catch (IOException e) {
            AppLog.E("Selector error " + e);
            return false;
        }
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> iter = selectedKeys.iterator();
        byte[] readBuffer = new byte[ProtocolConnectionPayload.MaxTransferDataSize];
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            if (key.attachment() instanceof DnsClient) {
                DnsClient client = (DnsClient) key.attachment();
                if (client != dnsClient) {
                    throw new RuntimeException("object mismatch");
                }
                dnsClient.checkResolv();
            } else {
                Connection connection = (Connection) key.attachment();
                SocketChannel channel = connection.getChannel();
                boolean needClose = false;
                try {
                    if (key.isConnectable()) {
                        AppLog.D("EventConnectable");
                        if (connection == prxServerConnection) {
                            AppLog.D("ConnectedToProxserver");
                            prxFinishConnection();
                            prxPushCheckKey();
                        } else {
                            try {
                                channel.finishConnect();
                                connection.flush();
                                prxPushConnectionEstablished(connection.getConnectionId());
                            } catch (IOException e) {
                                needClose = true;
                            } catch (NoConnectionPendingException e) {
                                needClose = true;
                            } catch (Exception e) {
                                needClose = true;
                            }
                        }
                    }
                    if (!needClose && key.isReadable() && (connection == prxServerConnection || (connection.getWriteBufferChainSize() <= 30 && connection.getTotalWriteBufferChainSize() <= 800))) {
                        if (connection == prxServerConnection) {
                            while (true) {
                                Connection.Request request = prxTryReadRequest();
                                if (request == null) {
                                    break;
                                }
                                prxProcessRequest(request);
                            }
                        } else {
                            // read as much as possible:
                            long connectionId = connection.getConnectionId();
                            ByteBuffer pbb = ByteBuffer.wrap(readBuffer);
                            int TotalBytes = 0;
                            int MaxReadOnce = 40_000;
                            while (true) {
                                try {
                                    int readBytes = connection.getChannel().read(pbb);
                                    if (pbb.position() > 0) {
                                        byte[] payload = new byte[pbb.position()];
                                        System.arraycopy(readBuffer, 0, payload, 0, pbb.position());
                                        prxPushUpstreamPayload(connectionId, payload);
                                        pbb.clear();

                                        TotalBytes += readBytes;
                                        if (TotalBytes >= MaxReadOnce) {
                                            break;
                                        }
                                    } else if (readBytes < 0) {
                                        AppLog.D("PeerClose:" + connection.getChannel());
                                        needClose = true;
                                    }

                                    break;
                                } catch (IOException e) {
                                    AppLog.E("IoException: ConnectionId=" + connectionId + " exception=" + e);
                                    needClose = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!needClose && key.isWritable()) {
                        if (connection == prxServerConnection) {
                            prxFlush();
                        } else {
                            try {
                                connection.flush();
                            } catch (IOException e) {
                                needClose = true;
                            }
                        }
                    }
                } catch (CancelledKeyException e) {
                    if (connection != prxServerConnection) {
                        needClose = true;
                    }
                }
                prxKeepalive(false);
                if (needClose) {
                    long connectionId = connection.getConnectionId();
                    destroyConnection(connection, true);
                    prxPushCloseConnection(connectionId);
                }
                if (prxServerConnectionError) {
                    return false;
                }
            }
        }
        selectedKeys.clear();
        return true;
    }

    public static void destroyConnection(Connection connection, boolean needRemovingFromMap) {
        if (needRemovingFromMap) {
            connectionMap.remove(connection.getConnectionId());
        }
        connection.clean();
    }

    // prx server communication:
    private static void prxFinishConnection() {
        if (prxServerConnectionError) {
            throw new RuntimeException("unexpected error before connection is establshed");
        }
        try {
            prxServerConnection.getChannel().finishConnect();
        } catch (IOException e) {
            AppLog.E("prxServer finish connection error: " + e);
            prxServerConnectionError = true;
        }
    }

    private static Connection.Request prxTryReadRequest() {
        if (prxServerConnectionError) {
            return null;
        }
        try {
            return prxServerConnection.tryReadPacket();
        } catch (IOException e) {
            prxServerConnectionError = true;
            return null;
        }
    }

    private static void prxProcessRequest(Connection.Request request) {
        lastServerCheck = new Date().getTime();
        if (prxServerConnectionError) {
            return;
        }
        switch (request.header.CommandId) {
            case CommandId.ServerCheck: {
                AppLog.D("ServerCheck");
                prxKeepalive(true);
                break;
            }
            case CommandId.NewConnection: {
                ProtocolNewConnection.NewConnectionRequest details = ProtocolNewConnection.parsePayload(request.payload);
                Connection newConnection = new Connection();
                if (!newConnection.init(details.connectionId, details.address, selector)) {
                    prxPushCloseConnection(details.connectionId);
                }
                Connection oldConnection = connectionMap.put(newConnection.getConnectionId(), newConnection);
                if (oldConnection != null) {
                    destroyConnection(oldConnection, false);
                }
                break;
            }
            case CommandId.NewHostConnection: {
                ProtocolNewHostConnection.NewConnectionRequest details = ProtocolNewHostConnection.parsePayload(request.payload);
                Connection newConnection = new Connection();
                if (!newConnection.init(details.connectionId, details.hostname, details.port, dnsClient, selector)) {
                    prxPushCloseConnection(details.connectionId);
                }
                Connection oldConnection = connectionMap.put(newConnection.getConnectionId(), newConnection);
                if (oldConnection != null) {
                    destroyConnection(oldConnection, false);
                }
                break;
            }
            case CommandId.ConnectionPayload: {
                ProtocolConnectionPayload.ConnectionPayloadRequest details = ProtocolConnectionPayload.parsePayload(request.payload);
                Connection upstream = connectionMap.get(details.connectionId);
                if (upstream == null) {
                    break;
                }
                try {
                    upstream.postRawData(details.payload);
                } catch (IOException e) {
                    AppLog.E("post payload to upstream error: " + e);
                    destroyConnection(upstream, true);
                }
                break;
            }
            case CommandId.CloseConnection: {
                ProtocolCloseConnection.CloseConnectionRequest details = ProtocolCloseConnection.parsePayload(request.payload);
                Connection upstream = connectionMap.get(details.connectionId);
                if (upstream == null) {
                    break;
                }
                destroyConnection(upstream, true);
                break;
            }
            case CommandId.BanVersion: {
                long duration = ProtocolBanVersion.parsePayload((request.payload));
                AppLog.D("Version banned for " + duration);
                BanExpireTimeMS = new Date().getTime() + duration;
                break;
            }
        }
    }

    private static void prxPushCheckKey() {
        if (prxServerConnectionError) {
            return;
        }
        try {
            byte[] raw = ProtocolCheckKey.buildRequest(prxServerInfoKey, geoInfo, ProxyMain.deviceId);
            prxServerConnection.postRawData(raw);
            lastHeartBeat = new Date().getTime();
        } catch (IOException e) {
            AppLog.E("Post chkkey to proxy error: " + e);
            prxServerConnectionError = true;
        }
    }

    private static void prxPushUpstreamPayload(long connectionId, byte[] payload) {
        if (prxServerConnectionError) {
            return;
        }
        byte[] raw = ProtocolConnectionPayload.buildRequest(connectionId, payload);
        try {
            prxServerConnection.postRawData(raw);
        } catch (IOException e) {
            AppLog.E("Post payload to proxy error: " + e);
            prxServerConnectionError = true;
        }
    }

    private static void prxFlush() {
        if (prxServerConnectionError) {
            return;
        }
        try {
            prxServerConnection.flush();
        } catch (IOException e) {
            AppLog.E("Flush proxy output error: " + e);
            prxServerConnectionError = true;
        }
    }

    private static void prxPushConnectionEstablished(long connectionId)
    {
        if (prxServerConnectionError) {
            return;
        }
        try {
            byte[] raw = ProtocolConnectionEstablished.buildRequest(connectionId);
            prxServerConnection.postRawData(raw);
        } catch (IOException e) {
            prxServerConnectionError = true;
        }
    }

    public static void prxPushCloseConnection(long connectionId) {
        if (prxServerConnectionError) {
            return;
        }
        try {
            byte[] notify = ProtocolCloseConnection.buildRequest(connectionId);
            prxServerConnection.postRawData(notify);
        } catch (IOException e) {
            prxServerConnectionError = true;
        }
    }

    private static void prxKeepalive(boolean force) {
        if (prxServerConnectionError) {
            return;
        }
        long now = new Date().getTime();
        if (!force && (now - lastHeartBeat < AppConfig.ProxyTimeout)) {
            return;
        }
        try {
            byte[] keepAlive = ProtocolKeepalive.buildRequest();
            prxServerConnection.postRawData(keepAlive);
        } catch (IOException e) {
            prxServerConnectionError = true;
        }
        lastHeartBeat = now;
    }

    public static void runPrxService(PrxServerInfo prxServerInfo, GeoInfo challangeGeoInfo) {
        synchronized (PrxServiceThread.class) {
            while (!KeepRunning.compareAndSet(false, true)) {
            }
            try {
                threadErrorLog = null;
                AppLog.D("PrxThreadStart");
                if (!init(new InetSocketAddress(prxServerInfo.address, prxServerInfo.port))) {
                    AppLog.E("Failed to init connection to prxserver");
                    return;
                }
                geoInfo = challangeGeoInfo;
                prxServerInfoKey = prxServerInfo.key;

                dnsClient.queryA("www.baidu.com", new OutputDns());
                while (KeepRunning.get() && loopOnce()) {
                }
                clean();
                KeepRunning.set(false);
                AppLog.D("PrxThreadEnd");
            } catch (Exception e) {
                clean();
                AppLog.E("RuntimeException: " + e);
                e.printStackTrace();
                threadErrorLog = e;
            }
        }
    }

    public static void stopPrxService() {
        KeepRunning.set(false);
    }

    public static Exception getLastError() {
        synchronized (PrxServiceThread.class) {
            return threadErrorLog;
        }
    }

}
