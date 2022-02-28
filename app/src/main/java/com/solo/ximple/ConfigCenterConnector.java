package com.solo.ximple;


import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;

public class ConfigCenterConnector {

    private final ArrayList<SocketAddress> configCenterAddressList = new ArrayList<>();
    private DatagramChannel challangeChannel = null;

    public boolean Init() {
        if (configCenterAddressList == null || configCenterAddressList.isEmpty()) {
            return false;
        }
        try {
            challangeChannel = DatagramChannel.open();
        } catch (IOException e) {
            if (challangeChannel != null) {
                try {
                    challangeChannel.close();
                } catch (IOException e1) {
                }
                challangeChannel = null;
            }
            return false;
        }
        return true;
    }

    public void Clean() {
        if (challangeChannel != null) {
            try {
                challangeChannel.close();
            } catch (IOException e1) {
            }
            challangeChannel = null;
        }
    }

    public boolean register() {
        return false;
    }

}
