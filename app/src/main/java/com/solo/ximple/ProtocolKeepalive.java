package com.solo.ximple;

import java.nio.ByteBuffer;

public class ProtocolKeepalive {

    public static byte[] buildRequest() {
        byte[] ret = new byte[Protocol.HeaderSize];
        Protocol.ProtocolHeader header = new Protocol.ProtocolHeader();
        header.CommandId = CommandId.KeepAlive;

        ByteBuffer bb = ByteBuffer.wrap(ret);
        bb.order(Protocol.Endian);
        Protocol.writePacket(bb, header, null);
        return ret;
    }


}
