package com.solo.ximple;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ProxyMain {

    public static String deviceId = null;
    public static AtomicBoolean ChallengeThreadRunning = new AtomicBoolean(false);

    public static Runnable proxyThread = new Runnable() {

        private final byte[] RecvBuffer = new byte[Protocol.MaxPacketSize];
        private InetSocketAddress ccAddress = null;
        private DatagramSocket localUdpSocket = null;
        private InetAddress outAddress = null;
        // these fields are for further comparing and should not be reset
        private boolean preservedAddress = false;
        private long initTimeout = ProxyStatus.initIdleTimeout;
        private InetAddress lastAddress = null;
        private PrxServerInfo prxServerInfo = null;
        private GeoInfo myGeoInfo = null;

        private void reset() {
            if (localUdpSocket != null) {
                localUdpSocket.close();
                localUdpSocket = null;
            }
            ccAddress = null;
            outAddress = null;
            ProxyStatus.currentPhase = ProxyStatus.Phase.Init;

            // add some timeout
            if (initTimeout < ProxyStatus.initIdleTimeout) {
                initTimeout = ProxyStatus.initIdleTimeout;
            } else {
                initTimeout = initTimeout + (long) (initTimeout * 0.1) + (long) (Math.random() * 5);
                if (initTimeout > ProxyStatus.maxInitIdleTimeout) {
                    initTimeout = ProxyStatus.maxInitIdleTimeout;
                }
            }
        }

        private boolean doInit() {
            try {
                AppLog.D("InitTimeout: " + initTimeout);
                Thread.sleep(initTimeout);
                if (preservedAddress) { // double sleep time
                    Thread.sleep(initTimeout);
                }
                // random cc:
                String HostAddr = AppConfig.CC_HostAddrList[(int) (Math.random() * AppConfig.CC_HostAddrList.length)];
                String[] Host_NamePort = HostAddr.split(":");
                String CC_Host = Host_NamePort[0];
                int CC_Port = Integer.parseInt(Host_NamePort[1]);
                AppLog.D("Trying hostname:" + CC_Host + ":" + CC_Port);

                InetAddress[] ccAddresses = InetAddress.getAllByName(CC_Host);
                if (ccAddresses.length == 0) {
                    AppLog.D("No address found!");
                    return false;
                }
                for (InetAddress addr : ccAddresses) {
                    AppLog.D("Addr: " + addr.getHostAddress());
                }
                ccAddress = new InetSocketAddress(ccAddresses[(int) (Math.random() * ccAddresses.length)], CC_Port);
            } catch (Exception e) {
                AppLog.E("Init Phase Error: " + e.getMessage());
                return false;
            }
            ProxyStatus.currentPhase = ProxyStatus.Phase.IpCheck;
            return true;
        }

        private boolean doCheckIp() {
            if (localUdpSocket == null) {
                try {
                    localUdpSocket = new DatagramSocket();
                    localUdpSocket.setSoTimeout(AppConfig.CC_ChallangeSocketTimeout);
                } catch (SocketException e) {
                    AppLog.E(e.getMessage());
                }
            }
            try {
                byte[] request = ProtocolChallenge.buildRequest();
                DatagramPacket packet = new DatagramPacket(request, request.length, ccAddress);
                localUdpSocket.send(packet);
            } catch (IOException e) {
                AppLog.E(e.getMessage());
                return false;
            }
            while (true) {
                try {
                    DatagramPacket response = new DatagramPacket(RecvBuffer, RecvBuffer.length);
                    localUdpSocket.receive(response);
                    InetAddress source = response.getAddress();
                    if (!source.equals(ccAddress.getAddress())) {
                        continue;
                    }
                    RegionAddress regionAddress = ProtocolChallenge.parseResponse(response);
                    outAddress = regionAddress.address;
                    if (regionAddress.countryId != 0 && regionAddress.cityId != 0) {
                        myGeoInfo = new GeoInfo();
                        myGeoInfo.data.adcode = "" + regionAddress.cityId;
                        lastAddress = outAddress;
                        break;
                    }
                    break;
                } catch (IOException e) {
                    AppLog.E(e.getMessage());
                    return false;
                }
            }
            ProxyStatus.currentPhase = ProxyStatus.Phase.UpdatingGeoInfo;
            return true;
        }

        private boolean doInitWithGeoInfo() {

            if (lastAddress != null && lastAddress.equals(outAddress) && myGeoInfo != null ) {
                AppLog.D("IpRemainUnchanged, skip geoinfo, my address is: " + outAddress);
            } else {
                return false;
            }

            try {
                byte[] request = ProtocolChallengeGeo.buildRequest(outAddress, myGeoInfo.data.city);
                DatagramPacket packet = new DatagramPacket(request, request.length, ccAddress);
                localUdpSocket.send(packet);
            } catch (IOException e) {
                AppLog.E(e.getMessage());
                return false;
            }
            while (true) {
                try {
                    DatagramPacket response = new DatagramPacket(RecvBuffer, RecvBuffer.length);
                    localUdpSocket.receive(response);
                    InetAddress source = response.getAddress();
                    if (!source.equals(ccAddress.getAddress())) {
                        continue;
                    }
                    prxServerInfo = ProtocolChallengeGeo.parseResponse(response);
                    AppLog.D("ServerInfo:Address=" + prxServerInfo.address);
                    break;
                } catch (IOException e) {
                    AppLog.E(e.getMessage());
                    return false;
                }
            }

            ProxyStatus.currentPhase = ProxyStatus.Phase.ConfigReady;
            return true;
        }

        private void doPostException(Exception error) {
            if (error == null) {
                return;
            }
            String myId = "";
            if (outAddress != null) {
                myId = myId + outAddress;
            }
            if (prxServerInfo != null) {
                myId = myId + ":" + prxServerInfo.address;
            }
            byte[] errorMessageNotify = ProtocolErrorReport.buildRequest(myId + " " + XimpleUtilities.getErrorDetail(error));
            if (errorMessageNotify == null) {
                AppLog.E("Failed to build exception report request");
                return;
            }
            try {
                DatagramPacket packet = new DatagramPacket(errorMessageNotify, errorMessageNotify.length, ccAddress);
                localUdpSocket.send(packet);
            } catch (Exception e) {
                AppLog.E("Post exception error: " + e.getMessage());
            }
        }

        public void run() {
            while(!ChallengeThreadRunning.compareAndSet(false, true))
            {}

            while (ChallengeThreadRunning.get()) {

                if (ProxyStatus.currentPhase == ProxyStatus.Phase.Init) {
                    if (!doInit()) {
                        continue;
                    }
                }
                if (ProxyStatus.currentPhase == ProxyStatus.Phase.IpCheck) {
                    if (!doCheckIp()) {
                        reset();
                        continue;
                    }
                }
                if (ProxyStatus.currentPhase == ProxyStatus.Phase.UpdatingGeoInfo) {
                    if (!doInitWithGeoInfo()) {
                        reset();
                        continue;
                    }
                }

                if (ProxyStatus.currentPhase == ProxyStatus.Phase.ConfigReady) {
                    Thread prxServiceThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            PrxServiceThread.runPrxService(prxServerInfo, myGeoInfo);
                        }
                    });
                    prxServiceThread.start();
                    long StartTime = new Date().getTime();
                    while (ChallengeThreadRunning.get()) {
                        try {
                            prxServiceThread.join();
                            break;
                        }
                        catch (InterruptedException e) {}
                    }
                    PrxServiceThread.stopPrxService();
                    while (prxServiceThread.isAlive()) {
                        try {
                            prxServiceThread.join();
                        }
                        catch (InterruptedException e) {}
                    }

                    long EndTime = new Date().getTime();
                    AppLog.D("main thread lifetime " + (EndTime - StartTime));
                    if (EndTime - StartTime >= 5 * 60 * 1000) {
                        initTimeout = 0;
                    }
                    reset();
                }
            }
        }
    };

    private static void testPostException(Exception error) {
        if (error == null) {
            return;
        }
        byte[] errorMessageNotify = ProtocolErrorReport.buildRequest(XimpleUtilities.getErrorDetail(error));
        if (errorMessageNotify == null) {
            AppLog.E("Failed to build exception report request");
            return;
        }
    }

    public static void main(String... args) {
        App();
    }

    private static AtomicReference<Thread> AppThread = new AtomicReference();

    public static void AppWithDeviceId(String did, Object appContextObject) {
        AppConfig.appContextObject = appContextObject;
        deviceId = did;
        App();
    }

    private static void App() {
        Thread AppThreadInstance = new Thread(proxyThread);
        if (!AppThread.compareAndSet(null, AppThreadInstance)) {
            throw new RuntimeException("multiple instances");
        }
        AppLog.D("AppStarted");
        AppThreadInstance.start();
        while (true) {
            try {
                AppThreadInstance.join();
                break;
            } catch (InterruptedException e) {
                AppLog.E("AppWait");
            }
        }
        AppLog.D("AppFinished");
    }

    public static void AppStop() {
        Thread AppThreadInstance = AppThread.get();
        if (AppThreadInstance == null) {
            return;
        }
        while(ChallengeThreadRunning.compareAndSet(true, false)) {}
        AppThreadInstance.interrupt();
        while(AppThreadInstance.isAlive()) {
            try {
                AppThreadInstance.join();
            }
            catch (InterruptedException e) {}
        }
        AppThread.set(null);
    }

}
