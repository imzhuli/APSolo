package com.solo.ximple;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class ProtocolNewConnection {

    public static NewConnectionRequest parsePayload(byte[] payload) {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        bb.order(Protocol.Endian);
        try {
            long connectionId = bb.getLong();
            byte[] rawIp = new byte[4];
            bb.get(rawIp);
            int port = 0x0_FFFF & (int) bb.getShort();
            NewConnectionRequest ret = new NewConnectionRequest();
            ret.connectionId = connectionId;
            ret.address = new InetSocketAddress(InetAddress.getByAddress(rawIp), port);
            return ret;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static class NewConnectionRequest {
        long connectionId = -1;
        InetSocketAddress address = null;
    }

}