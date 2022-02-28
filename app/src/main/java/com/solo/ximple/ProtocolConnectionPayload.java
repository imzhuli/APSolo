package com.solo.ximple;

import java.nio.ByteBuffer;

public class ProtocolConnectionPayload {

    public static final int MaxTransferDataSize = Protocol.MaxPayloadSize - 256;

    public static ConnectionPayloadRequest parsePayload(byte[] payload) {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        bb.order(Protocol.Endian);
        ConnectionPayloadRequest ret = new ConnectionPayloadRequest();
        ret.connectionId = bb.getLong();
        ret.payload = new byte[bb.remaining()];
        bb.get(ret.payload);
        return ret;
    }

    public static byte[] buildRequest(long connectionId, byte[] payload) {
        int completePayloadSize = payload.length + 8; // connctionId;
        if (completePayloadSize > Protocol.MaxPayloadSize) {
            return null;
        }

        byte[] completePayload = new byte[completePayloadSize];
        ByteBuffer pbb = ByteBuffer.wrap(completePayload);
        pbb.order(Protocol.Endian);
        pbb.putLong(connectionId);
        pbb.put(payload);

        byte[] packet = new byte[Protocol.HeaderSize + completePayload.length];
        ByteBuffer bb = ByteBuffer.wrap(packet);
        bb.order(Protocol.Endian);

        Protocol.ProtocolHeader header = new Protocol.ProtocolHeader();
        header.CommandId = CommandId.ConnectionPayload;
        Protocol.writePacket(bb, header, completePayload);
        return packet;
    }

    public static class ConnectionPayloadRequest {
        long connectionId = -1;
        byte[] payload = null;
    }

}