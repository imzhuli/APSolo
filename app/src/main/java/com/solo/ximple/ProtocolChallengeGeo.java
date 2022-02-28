package com.solo.ximple;

import com.solo.ximple.Protocol.ProtocolHeader;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ProtocolChallengeGeo {

    public static byte[] buildRequest(InetAddress outAddress, String city) {
        byte[] payload = new byte[4 /* out addr */ + 3 * 32 /* addresses */];
        byte[] ret = new byte[Protocol.HeaderSize + payload.length];
        ProtocolHeader header = new ProtocolHeader();
        header.CommandId = CommandId.ChallengeGeo;

        ByteBuffer pbb = ByteBuffer.wrap(payload);
        pbb.order(Protocol.Endian);
        pbb.put(outAddress.getAddress());
        pbb.put(city.getBytes(StandardCharsets.UTF_8));

        ByteBuffer bb = ByteBuffer.wrap(ret);
        bb.order(Protocol.Endian);
        Protocol.writePacket(bb, header, payload);

        return ret;
    }

    public static PrxServerInfo parseResponse(DatagramPacket response) {
        final int RequiredPacketSize = Protocol.HeaderSize + 4 /* ip */ + 2 /* port */ + 16 /* key */;
        if (response.getLength() != RequiredPacketSize /* ipv4 */) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(response.getData());
        bb.order(Protocol.Endian);
        ProtocolHeader header = Protocol.readHeader(bb);
        if (header == null || header.PacketLength != RequiredPacketSize || header.CommandId != CommandId.ChallengeGeoResp) {
            return null;
        }
        try {
            byte[] rawIp = new byte[4];
            bb.get(rawIp);
            PrxServerInfo ret = new PrxServerInfo();
            ret.address = InetAddress.getByAddress(rawIp);
            ret.port = 0x0_FFFF & (int) bb.getShort();
            ret.key = new byte[16];
            bb.get(ret.key);
            return ret;
        } catch (UnknownHostException e) {
            return null;
        }

    }

}
