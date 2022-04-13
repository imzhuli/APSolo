package com.solo.ximple;

import java.util.Date;

public class AppLog {

    private static String TAG = "solo_ProxyLogger";

    public static void D(String s) {
         System.out.println("D: " + new Date() + ": " + TAG + ":" + s);
    }

    public static void E(String s) {
        System.out.println("E: " + new Date() + ": " + TAG + ":" + s);
    }

}
