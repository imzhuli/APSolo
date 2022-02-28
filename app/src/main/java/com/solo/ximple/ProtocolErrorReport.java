package com.solo.ximple;

import java.nio.ByteBuffer;

public class ProtocolErrorReport {

    public static final int MaxErrorByteLength = Protocol.MaxPayloadSize - 256;

    public static byte[] buildRequest(String errorLog) {
        byte[] ret = new byte[Protocol.MaxPacketSize];

        if (errorLog.length() > MaxErrorByteLength) {
            errorLog = errorLog.substring(0, MaxErrorByteLength);
        }
        byte[] payload;
        try {
            while (true) {
                payload = errorLog.getBytes();
                if (payload.length > MaxErrorByteLength) {
                    errorLog = errorLog.substring(0, errorLog.length() - 128);
                    continue;
                }
                break;
            }
        } catch (Exception e) {
            return null;
        }

        Protocol.ProtocolHeader header = new Protocol.ProtocolHeader();
        header.CommandId = CommandId.ErrorLogReport;
        ByteBuffer bb = ByteBuffer.wrap(ret);
        bb.order(Protocol.Endian);
        Protocol.writePacket(bb, header, payload);
        return ret;
    }

}
