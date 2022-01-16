package com.example.monitor.repositories.parseutils;

import com.google.gson.Gson;

public class JSONUtils {
    private static final Gson gson = new Gson();
//    private JSONUtils(){}

    public static boolean isJSONValid(String jsonInString) {
        try {
            gson.fromJson(jsonInString, Object.class);
            return true;
        } catch(com.google.gson.JsonSyntaxException ex) {
            return false;
        }
    }
}
