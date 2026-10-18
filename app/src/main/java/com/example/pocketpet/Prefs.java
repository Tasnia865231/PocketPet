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

    public boolean isTilt() { return sp.getBoolean("tilt", true); }
    public void setTilt(boolean v) { sp.edit().putBoolean("tilt", v).apply(); }

    public boolean isShake() { return sp.getBoolean("shake", true); }
    public void setShake(boolean v) { sp.edit().putBoolean("shake", v).apply(); }

    public int getNotifHour() { return sp.getInt("notifHour", 18); }
    public void setNotifHour(int v) { sp.edit().putInt("notifHour", v).apply(); }

    public float getLat() { return sp.getFloat("lat", 22.8456f); }
    public void setLat(float v) { sp.edit().putFloat("lat", v).apply(); }

    public float getLon() { return sp.getFloat("lon", 89.5403f); }
    public void setLon(float v) { sp.edit().putFloat("lon", v).apply(); }

    public String getWeather() { return sp.getString("weather", "CLEAR_DAY"); }
    public void setWeather(String v) { sp.edit().putString("weather", v).apply(); }

    public long getWeatherTime() { return sp.getLong("weatherTime", 0L); }
    public void setWeatherTime(long v) { sp.edit().putLong("weatherTime", v).apply(); }
}
