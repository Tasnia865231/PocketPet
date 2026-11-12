package com.example.pocketpet;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("pocketpet", Context.MODE_PRIVATE);
    }

    public String getUser() { return sp.getString("user", ""); }
    public void setUser(String v) { sp.edit().putString("user", v).apply(); }

    /** UID of the last player who used this phone (so a different player starts with a fresh local game). */
    public String getLastUid() { return sp.getString("lastUid", ""); }
    public void setLastUid(String v) { sp.edit().putString("lastUid", v).apply(); }

    public boolean isTilt() { return sp.getBoolean("tilt", true); }
    public void setTilt(boolean v) { sp.edit().putBoolean("tilt", v).apply(); }

    public boolean isShake() { return sp.getBoolean("shake", true); }
    public void setShake(boolean v) { sp.edit().putBoolean("shake", v).apply(); }

    /** Mochi voice (tap Mochi, he repeats what you say in a cute voice). On by default; switch it off in Settings. */
    public boolean isVoiceEnabled() { return sp.getBoolean("voice", true); }
    public void setVoiceEnabled(boolean v) { sp.edit().putBoolean("voice", v).apply(); }

    public int getNotifHour() { return sp.getInt("notifHour", 18); }
    public void setNotifHour(int v) { sp.edit().putInt("notifHour", v).apply(); }

    public float getLat() { return sp.getFloat("lat", 22.8456f); }
    public void setLat(float v) { sp.edit().putFloat("lat", v).apply(); }

    public float getLon() { return sp.getFloat("lon", 89.5403f); }
    public void setLon(float v) { sp.edit().putFloat("lon", v).apply(); }

    /** When the coordinates were last filled in from the phone's location (so the app does not ask too often). */
    public long getLocTime() { return sp.getLong("locTime", 0L); }
    public void setLocTime(long v) { sp.edit().putLong("locTime", v).apply(); }

    public String getWeather() { return sp.getString("weather", "CLEAR_DAY"); }
    public void setWeather(String v) { sp.edit().putString("weather", v).apply(); }

    public long getWeatherTime() { return sp.getLong("weatherTime", 0L); }
    public void setWeatherTime(long v) { sp.edit().putLong("weatherTime", v).apply(); }

    /** Small yes/no flags (for example "already asked for this permission"). */
    public boolean getFlag(String key) { return sp.getBoolean("flag_" + key, false); }
    public void setFlag(String key, boolean v) { sp.edit().putBoolean("flag_" + key, v).apply(); }
}
