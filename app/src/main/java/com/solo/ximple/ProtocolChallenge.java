package com.solo.ximple;

import com.solo.ximple.Protocol.ProtocolHeader;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

class RegionAddress
{
    int countryId;
    int cityId;
    InetAddress address;
}

public class ProtocolChallenge {

    public static byte[] buildRequest() {
        byte[] ret = new byte[Protocol.HeaderSize];
        ProtocolHeader header = new ProtocolHeader();
        header.CommandId = CommandId.Challenge;

        ByteBuffer bb = ByteBuffer.wrap(ret);
        bb.order(Protocol.Endian);
        Protocol.writePacket(bb, header, null);
        return ret;
    }

    public static RegionAddress parseResponse(DatagramPacket response) {
        final int RequiredPacketSize = Protocol.HeaderSize + 4;
        if (response.getLength() != RequiredPacketSize /* ipv4 */) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(response.getData());
        bb.order(Protocol.Endian);
        ProtocolHeader header = Protocol.readHeader(bb);
        if (header == null || header.PacketLength != RequiredPacketSize || header.CommandId != CommandId.ChallengeResp) {
            return null;
        }
        byte[] rawIp = new byte[4];
        bb.get(rawIp);
        try {
            RegionAddress ret = new RegionAddress();
            long regionKey = header.RequestId;
            ret.countryId = (int)(regionKey >> 32);
            ret.cityId = (int)(regionKey & 0x0_FFFF_FFFF);
            ret.address = InetAddress.getByAddress(rawIp);
            return ret;
        } catch (UnknownHostException e) {
            return null;
        }
    }

}
