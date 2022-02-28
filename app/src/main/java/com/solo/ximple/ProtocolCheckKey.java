package com.solo.ximple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ProtocolCheckKey {

    public static byte[] buildRequest(byte[] key, GeoInfo geoInfo, String deviceId) {
        int payloadSize = 16 + 4 + 4 + 32 + 4 + 256;
        byte[] ret = new byte[Protocol.HeaderSize + payloadSize];
        Protocol.ProtocolHeader header = new Protocol.ProtocolHeader();
        header.CommandId = CommandId.CheckKey;
        header.RequestId = AppConfig.Version;
        byte[] payload = new byte[payloadSize];
        ByteBuffer pbb = ByteBuffer.wrap(payload);

        int cityCode = Integer.parseInt(geoInfo.data.adcode);
//        String cityName = geoInfo.data.city;
//        // version before 2_2_000000
//        byte[] cityNameBytes = null;
//        while (true) {
//            byte[] cb = cityName.getBytes(StandardCharsets.UTF_8);
//            if (cb.length >= 32) {
//                cityName = cityName.substring(cityName.length() - 1);
//            } else {
//                cityNameBytes = cb;
//                break;
//            }
//        }
        pbb.order(Protocol.Endian);
        pbb.put(key);
        pbb.putInt(156); // china
        pbb.putInt(cityCode);
        // version before 2_2_000000
        // pbb.put(cityNameBytes);
        pbb.put(new byte[32]);
        if (deviceId != null) {
            byte[] deviceIdBytes = null;
            while (true) {
                byte[] cb = deviceId.getBytes(StandardCharsets.UTF_8);
                if (cb.length > 256) {
                    deviceId = deviceId.substring(deviceId.length() - 1);
                } else {
                    deviceIdBytes = cb;
                    break;
                }
            }
            pbb.putInt(deviceIdBytes.length);
            pbb.put(deviceIdBytes);
        }

        ByteBuffer bb = ByteBuffer.wrap(ret);
        bb.order(Protocol.Endian);
        Protocol.writePacket(bb, header, payload);
        return ret;
    }

}