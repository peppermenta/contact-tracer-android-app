package com.example.contacttracing;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Handler;

/**
 * Wrapper Class to Handle Storage of Values in Shared Preferences
 */
public class PreferenceData {
    private static final String PREF_USER_LOGGEDIN_STATUS = "logged_in_status";
    private static final String PREF_USER_JWT_TOKEN = "user_jwt_token";

    public static SharedPreferences getSharedPreferences(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    public static void setUserLoggedInStatus(Context ctx, boolean status) {
        Editor editor = getSharedPreferences(ctx).edit();
        editor.putBoolean(PREF_USER_LOGGEDIN_STATUS, status);
        editor.commit();
    }

    public static boolean getUserLoggedInStatus(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_USER_LOGGEDIN_STATUS, false);
    }

    public static void setUserJwt(Context ctx, String token) {
        Editor editor = getSharedPreferences(ctx).edit();
        editor.putString(PREF_USER_JWT_TOKEN, token);
        editor.commit();
    }

    public static String getUserJwt(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_USER_JWT_TOKEN, "");
    }
}
