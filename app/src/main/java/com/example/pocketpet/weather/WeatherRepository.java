package com.example.pocketpet.weather;

import android.content.Context;
import com.example.pocketpet.Prefs;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** Open-Meteo: free, no API key. Blocking: call from Bg.IO. */
public class WeatherRepository {
    private final Prefs prefs;

    public WeatherRepository(Context ctx) {
        prefs = new Prefs(ctx);
    }

    public WeatherType cached() {
        try {
            return WeatherType.valueOf(prefs.getWeather());
        } catch (Exception e) {
            return WeatherType.CLEAR_DAY;
        }
    }

    /** Returns the current weather, or null if the request failed (keep showing the cached scene). */
    public WeatherType fetch() {
        if (System.currentTimeMillis() - prefs.getWeatherTime() < 30 * 60 * 1000L) return cached();
        try {
            URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + prefs.getLat()
                    + "&longitude=" + prefs.getLon() + "&current=weather_code,is_day");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
            WeatherType t = map(cur.getInt("weather_code"), cur.getInt("is_day") == 1);
            prefs.setWeather(t.name());
            prefs.setWeatherTime(System.currentTimeMillis());
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    // WMO weather interpretation codes
    public WeatherType map(int code, boolean day) {
        if (code == 0 || code == 1) return day ? WeatherType.CLEAR_DAY : WeatherType.CLEAR_NIGHT;
        if (code == 2 || code == 3) return WeatherType.CLOUDY;
        if (code == 45 || code == 48) return WeatherType.FOG;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return WeatherType.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return WeatherType.SNOW;
        if (code >= 95 && code <= 99) return WeatherType.STORM;
        return WeatherType.CLOUDY;
    }
}
