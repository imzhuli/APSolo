package com.solo.ximple;

import java.nio.ByteBuffer;

public class ProtocolConnectionEstablished {

    public static byte[] buildRequest(long connectionId) {
        byte[] payload = new byte[9];
        ByteBuffer pbb = ByteBuffer.wrap(payload);
        pbb.order(Protocol.Endian);
        pbb.putLong(connectionId);

        byte[] packet = new byte[Protocol.HeaderSize + payload.length];
        ByteBuffer bb = ByteBuffer.wrap(packet);
        bb.order(Protocol.Endian);

        Protocol.ProtocolHeader header = new Protocol.ProtocolHeader();
        header.CommandId = CommandId.ConnectionEstablished;
        Protocol.writePacket(bb, header, payload);

        return packet;
    }

}
