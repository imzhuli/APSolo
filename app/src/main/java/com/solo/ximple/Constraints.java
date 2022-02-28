package com.solo.ximple;

public class Constraints {

    public static final int MaxCountryNameLength = 24;

    public static boolean IsEmptyString(String s) {
        return s == null || s.equals("");
    }

}
