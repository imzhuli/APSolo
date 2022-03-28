package com.solo.ximple;

import com.solo.ximple.dns.DnsBase;
import com.solo.ximple.dns.DnsClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

class Buffer {
    public final ByteBuffer bb = ByteBuffer.allocate(Protocol.MaxPacketSize);
    Buffer next = null;
}

class BufferChain {
    private static int tatalBufferCounter = 0;
    private int bufferCounter = 0;
    private Buffer firstBuffer = null;
    private Buffer lastBuffer = null;

    public static int getTotalBufferCounter() {
        return tatalBufferCounter;
    }

    public int getBufferCounter() {
        return bufferCounter;
    }

    public void push(Buffer buffer) {
        if (buffer == null || buffer.next != null) {
            return;
        }
        ++bufferCounter;
        ++tatalBufferCounter;
        if (firstBuffer == null) {
            firstBuffer = lastBuffer = buffer;
            return;
        }
        lastBuffer.next = buffer;
        lastBuffer = buffer;
    }

    public Buffer peek() {
        return firstBuffer;
    }

    public Buffer last() {
        return lastBuffer;
    }

    public Buffer pop() {
        if (firstBuffer == null) {
            return null;
        }
        Buffer target = firstBuffer;
        firstBuffer = target.next;
        target.next = null;
        if (firstBuffer == null) {
            lastBuffer = null;
        }
        --bufferCounter;
        --tatalBufferCounter;
        return target;
    }

    public void clear() {
        while (null != pop()) {
        }
    }
}

public class Connection implements DnsBase.ResultDelegate {

    private Selector selector = null;
    private long connectionId = -1;
    private SocketChannel socketChannel = null;
    private int port = 0; // used with hostname
    private final byte[] readBuffer = new byte[Protocol.MaxPacketSize];
    private int readBufferDataSize = 0;
    private Protocol.ProtocolHeader expectedRequestHeader = null;
    private final BufferChain writeBufferChain = new BufferChain();

    @Override
    public void OnQueryResult(String hostKey, DnsBase.QueryResult result, InetAddress address) {
        AppLog.D("QueryResult: " + hostKey + ", Result=" + result + ", Address=" + address);
        try {
            if (result != DnsBase.QueryResult.FAILED && address != null && socketChannel != null /* not destroyed */) {
                SocketAddress target = new InetSocketAddress(address, port);
                socketChannel.connect(target);
                socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ, this);
                return;
            }
        } catch (IOException e) {
        }
        PrxServiceThread.prxPushCloseConnection(connectionId);
        PrxServiceThread.destroyConnection(this, true);
    }

    public long getConnectionId() {
        return connectionId;
    }

    public SocketChannel getChannel() {
        return socketChannel;
    }

    public int getWriteBufferChainSize() {
        return writeBufferChain.getBufferCounter();
    }

    public int getTotalWriteBufferChainSize() {
        return BufferChain.getTotalBufferCounter();
    }

    boolean isUpstream() {
        return connectionId != -1;
    }

    boolean init(long connId, InetSocketAddress address, Selector selector) {
        this.selector = selector;
        connectionId = -1;
        boolean connectionEstablished = false;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            connectionEstablished = socketChannel.connect(address);
            socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ, this);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.valueOf(true));
                socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, Integer.valueOf(4096_000));
                socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, Integer.valueOf(4096_000));
            }
        } catch (IOException e) {
            AppLog.E("Failed to open channel");
            return false;
        }
        connectionId = connId;
        return true;
    }

    boolean init(long connId, String hostname, int port, DnsBase.Resolver dnsService, Selector selector) {
        this.selector = selector;
        connectionId = -1;
        boolean connectionEstablished = false;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, Integer.valueOf(4096_000));
                socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, Integer.valueOf(4096_000));
            }
            this.port = port;
            dnsService.queryA(hostname, this);
            AppLog.D("ConnectionWithHost: socket=" + socketChannel);
        } catch (IOException e) {
            AppLog.E("Failed to open channel");
            return false;
        }
        connectionId = connId;
        return true;
    }

    void clean() {
        AppLog.D("Cleaning: socket=" + socketChannel);
        writeBufferChain.clear();
        readBufferDataSize = 0;
        expectedRequestHeader = null;
        try {
            if (socketChannel != null) { // already destroyed
                socketChannel.close();
            }
        } catch (IOException e) {
            AppLog.E("Closing channel error");
        } finally {
            socketChannel = null;
            connectionId = -1;
            selector = null;
        }
    }

    public Request tryReadPacket() throws IOException {
        ByteBuffer rbb = ByteBuffer.wrap(readBuffer, readBufferDataSize, readBuffer.length - readBufferDataSize);
        int bytes = socketChannel.read(rbb);
        if (bytes < 0) {
            throw new IOException("prx server close");
        }
        if (bytes > 0) {
            readBufferDataSize += bytes;
        }
        if (readBufferDataSize < Protocol.HeaderSize) {
            return null;
        }
        if (expectedRequestHeader == null) {
            ByteBuffer hbb = ByteBuffer.wrap(readBuffer, 0, readBufferDataSize);
            hbb.order(Protocol.Endian);
            expectedRequestHeader = Protocol.readHeader(hbb);
            if (expectedRequestHeader == null) {
                throw new IOException("Invalid packet header");
            }
        }
        if (readBufferDataSize < expectedRequestHeader.PacketLength) {
            return null;
        }
        int payloadSize = expectedRequestHeader.getPayloadSize();
        byte[] payload = new byte[payloadSize];
        System.arraycopy(readBuffer, expectedRequestHeader.getPayloadOffset(), payload, 0, payloadSize);

        readBufferDataSize -= expectedRequestHeader.PacketLength;
        System.arraycopy(readBuffer, expectedRequestHeader.PacketLength, readBuffer, 0, readBufferDataSize);
        Request ret = new Request(expectedRequestHeader, payload);
        expectedRequestHeader = null;
        return ret;
    }

    public void postRawData(byte[] data) throws IOException {
        if (data.length > Protocol.MaxPacketSize) {
            throw new RuntimeException("Invalid packet size, find the bug!");
        }
        Buffer last = writeBufferChain.last();
        ByteBuffer src = ByteBuffer.wrap(data);
        if (getChannel().isConnected() && last == null) {
            socketChannel.write(src);
            if (src.remaining() > 0) {
                Buffer pending = new Buffer();
                pending.bb.put(src.array(), src.position(), src.remaining());
                pending.bb.flip();
                writeBufferChain.push(pending);
                socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
            }
        } else {
            Buffer pending = new Buffer();
            pending.bb.put(src.array(), src.position(), src.remaining());
            pending.bb.flip();
            writeBufferChain.push(pending);
        }
    }

    public void flush() throws IOException {
        while (true) {
            Buffer curr = writeBufferChain.peek();
            if (curr == null) {
                if (null == writeBufferChain.peek()) {
                    socketChannel.register(selector, SelectionKey.OP_READ, this);
                }
                return;
            }
            socketChannel.write(curr.bb);
            if (curr.bb.remaining() > 0) {
                socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
                return;
            }
            writeBufferChain.pop();
        }
    }

    public static class Request {
        public final Protocol.ProtocolHeader header;
        public final byte[] payload;

        public Request(Protocol.ProtocolHeader h, byte[] pl) {
            header = h;
            payload = pl;
        }
    }

}
