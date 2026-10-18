package com.example.pocketpet.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import java.util.ArrayDeque;

/**
 * Tilt: low-pass filter on the accelerometer isolates gravity.
 * Shake: linear acceleration (raw - gravity) above a threshold, 4+ times within 1 second.
 * Register in onResume, unregister in onPause.
 */
public class SensorHelper implements SensorEventListener {
    public interface Listener {
        void onTilt(float x, float y, float z);
        void onShake();
    }

    private final SensorManager sm;
    private final Sensor acc;
    private final Listener listener;
    private final float[] g = new float[3];
    private final ArrayDeque<Long> hits = new ArrayDeque<>();

    public boolean tiltOn = true;
    public boolean shakeOn = true;

    public SensorHelper(Context ctx, Listener listener) {
        this.listener = listener;
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void start() {
        if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float a = 0.8f;
        for (int i = 0; i < 3; i++) g[i] = a * g[i] + (1 - a) * e.values[i];
        if (tiltOn) listener.onTilt(g[0], g[1], g[2]);
        else listener.onTilt(0f, 9.8f, 0f);

        float lx = e.values[0] - g[0];
        float ly = e.values[1] - g[1];
        float lz = e.values[2] - g[2];
        double mag = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (shakeOn && mag > 9.0) {
            long now = SystemClock.elapsedRealtime();
            hits.addLast(now);
            while (!hits.isEmpty() && now - hits.peekFirst() > 1000) hits.removeFirst();
            if (hits.size() >= 4) {
                hits.clear();
                listener.onShake();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {}
}
