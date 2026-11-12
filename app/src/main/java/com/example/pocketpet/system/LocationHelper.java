package com.example.pocketpet.system;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.example.pocketpet.Prefs;

/**
 * Uses the coarse-location permission (asked at runtime, with an explanation) so the weather outside Mochi's window is
 * the weather where YOU are. Without the permission the saved coordinates from Settings are used.
 *
 * refresh()      = quick: uses the phone's last known position (may find nothing if no app asked for location lately).
 * refreshAsync() = refresh(), and if that finds nothing, asks the phone for ONE fresh position (up to 15 seconds).
 */
public final class LocationHelper {
    private LocationHelper() {}

    /** Answer of refreshAsync(). Always called on the main thread. */
    public interface Callback { void onResult(boolean found); }

    public static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static void save(Context c, Location l) {
        Prefs prefs = new Prefs(c);
        prefs.setLat((float) l.getLatitude());
        prefs.setLon((float) l.getLongitude());
        prefs.setLocTime(System.currentTimeMillis());
        prefs.setWeatherTime(0L);                    // force a fresh weather fetch for the new place
    }

    /** Saves the last known coarse position and forces a fresh weather fetch. Returns true if a position was found. */
    @SuppressWarnings("MissingPermission")
    public static boolean refresh(Context c) {
        if (!hasPermission(c)) return false;
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(provider);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Exception ignored) { }
            }
            if (best == null) return false;
            save(c, best);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Call from the main thread. Tries the last known position first, then one fresh position from the network. */
    @SuppressWarnings({"MissingPermission", "deprecation"})
    public static void refreshAsync(Context c, final Callback cb) {
        final Context app = c.getApplicationContext();
        if (!hasPermission(app)) { cb.onResult(false); return; }
        if (refresh(app)) { cb.onResult(true); return; }
        try {
            final LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
            if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { cb.onResult(false); return; }   // Location switched off in phone settings
            final Handler h = new Handler(Looper.getMainLooper());
            final boolean[] done = {false};
            final LocationListener l = new LocationListener() {
                @Override public void onLocationChanged(Location loc) {
                    if (done[0]) return;
                    done[0] = true;
                    h.removeCallbacksAndMessages(null);
                    save(app, loc);
                    cb.onResult(true);
                }
                @Override public void onProviderEnabled(String p) { }
                @Override public void onProviderDisabled(String p) { }
                @Override public void onStatusChanged(String p, int s, Bundle e) { }
            };
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, l, Looper.getMainLooper());
            h.postDelayed(() -> {
                if (done[0]) return;
                done[0] = true;
                try { lm.removeUpdates(l); } catch (Exception ignored) { }
                cb.onResult(false);
            }, 15_000);
        } catch (Exception e) {
            cb.onResult(false);
        }
    }
}
