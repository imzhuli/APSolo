package com.solo.ximple;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

public class ProtocolBanVersion {

    static long /* DurationMS */ parsePayload(byte[] payload) {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        bb.order(Protocol.Endian);
        ProtocolCloseConnection.CloseConnectionRequest ret = new ProtocolCloseConnection.CloseConnectionRequest();
        long Duration = bb.getLong();
        return Duration;
    }

}
