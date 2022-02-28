package com.solo.ximple;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class ProtocolNewHostConnection {

    public static class NewConnectionRequest {
        long connectionId = -1;
        String hostname;
        int port;
    }

    public static NewConnectionRequest parsePayload(byte[] payload) {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        bb.order(Protocol.Endian);
        try {
            long connectionId = bb.getLong();
            int hostnameLength = 0x00FF & (int)bb.get();
            byte[] stringBytes = new byte[hostnameLength];
            bb.get(stringBytes);
            String hostname = new String(stringBytes);
            int port = 0x0_FFFF & (int) bb.getShort();

            NewConnectionRequest ret = new NewConnectionRequest();
            ret.connectionId = connectionId;
            ret.hostname = hostname;
            ret.port = port;
            return ret;
        } catch (Exception e) {
            return null;
        }
    }

}
