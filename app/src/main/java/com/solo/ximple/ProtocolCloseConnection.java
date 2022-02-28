package com.solo.ximple;

import java.nio.ByteBuffer;

public class ProtocolCloseConnection {

    public static CloseConnectionRequest parsePayload(byte[] payload) {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        bb.order(Protocol.Endian);
        CloseConnectionRequest ret = new CloseConnectionRequest();
        ret.connectionId = bb.getLong();
        return ret;
    }

    public static byte[] buildRequest(long connectionId) {
        byte[] payload = new byte[9];
        ByteBuffer pbb = ByteBuffer.wrap(payload);
        pbb.order(Protocol.Endian);
        pbb.putLong(connectionId);

        byte[] packet = new byte[Protocol.HeaderSize + payload.length];
        ByteBuffer bb = ByteBuffer.wrap(packet);
        bb.order(Protocol.Endian);

        Protocol.ProtocolHeader header = new Protocol.ProtocolHeader();
        header.CommandId = CommandId.CloseConnection;
        Protocol.writePacket(bb, header, payload);
        return packet;
    }

    public static class CloseConnectionRequest {
        long connectionId = -1;
    }

}