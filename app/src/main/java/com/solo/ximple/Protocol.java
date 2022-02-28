package com.solo.ximple;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Protocol {

    // for server side optimization, using little endian.
    public static final ByteOrder Endian = ByteOrder.LITTLE_ENDIAN;

    public static final int MagicMask = 0xFF_000000;
    public static final int LengthMask = 0x00_FFFFFF;
    public static final int HeaderSize = 32;
    public static final int MaxPacketSize = 4096 & LengthMask;
    public static final int MaxPayloadSize = MaxPacketSize - HeaderSize;

    public static final int MagicValue = 0xCD_000000;

    public static boolean CheckPackageLength(int length) {
        return ((length & MagicMask) == MagicValue) && ((length & LengthMask) <= MaxPacketSize);
    }

	public static ProtocolHeader readHeader(ByteBuffer bb) {
        if (bb.order() != Endian || bb.remaining() < HeaderSize) {
            return null;
        }
        ProtocolHeader ret = new ProtocolHeader();
        ret.PacketLength = bb.getInt();
        ret.PackageSequenceId = 0x0_FF & (int) bb.get();
        ret.PackageSequenceMax = 0x0_FF & (int) bb.get();
        ret.CommandId = 0x0_FFFF & (int) bb.getShort();
        ret.RequestId = bb.getLong();
        ret.TraceId = new byte[16];
        bb.get(ret.TraceId);
        if (!CheckPackageLength(ret.PacketLength)) {
            return null;
        }
        ret.PacketLength &= LengthMask;
        return ret;
    }

    public static boolean writeHeader(ByteBuffer bb, ProtocolHeader header) {
        if (bb.order() != Endian || bb.remaining() < HeaderSize) {
            return false;
        }
        bb.putInt(header.PacketLength | MagicValue);
        bb.put((byte) header.PackageSequenceId);
        bb.put((byte) header.PackageSequenceMax);
        bb.putShort((short) header.CommandId);
        bb.putLong(header.RequestId);
        if (header.TraceId != null && header.TraceId.length == 16) {
            bb.put(header.TraceId);
        } else {
            bb.putLong(0);
            bb.putLong(0);
        }
        return true;
    }

    public static boolean writePacket(ByteBuffer bb, ProtocolHeader header, byte[] payload) {
        if (bb.order() != Endian || bb.remaining() < HeaderSize) {
            return false;
        }
        if (header.PacketLength == 0) {
            if (payload == null) {
                header.PacketLength = HeaderSize;
            } else {
                header.PacketLength = HeaderSize + payload.length;
            }
        } else {
            if (payload != null && header.PacketLength != HeaderSize + payload.length) {
                return false;
            }
            if (header.PacketLength != HeaderSize) {
                return false;
            }
        }
        bb.putInt(header.PacketLength | MagicValue);
        bb.put((byte) header.PackageSequenceId);
        bb.put((byte) header.PackageSequenceMax);
        bb.putShort((short) header.CommandId);
        bb.putLong(header.RequestId);
        if (header.TraceId != null && header.TraceId.length == 16) {
            bb.put(header.TraceId);
        } else {
            bb.putLong(0);
            bb.putLong(0);
        }
        if (payload != null) {
            bb.put(payload);
        }
        return true;
    }

    public static class ProtocolHeader {
        int PacketLength = 0; // header size included, lower 24 bits as length, higher 8 bits as a magic check
        int PackageSequenceId = 0;
        int PackageSequenceMax = 0;
        int CommandId = 0;
        long RequestId = 0;
        byte[] TraceId;

        /****
         * uint32_t PacketLength; // header size included, lower 24 bits as length,
         * higher 8 bits as a magic check uint8_t PackageSequenceId; uint8_t
         * PackageSequenceTotal; uint16_t CommandId; uint64_t RequestId; ubyte
         * TraceId[16]; // allow uuid
         ****/
        int getPayloadOffset() {
            return HeaderSize;
        }

        int getPayloadSize() {
            if (PacketLength >= HeaderSize) {
                return PacketLength - HeaderSize;
            }
            return -1;
        }
    }

}
