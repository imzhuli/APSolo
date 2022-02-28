package com.solo.ximple;

import java.io.PrintWriter;
import java.io.StringWriter;

public class XimpleUtilities {

    public static String getErrorDetail(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return e.getMessage() + "\n" + e.getCause() + "\n" + sw.toString();
    }

}
