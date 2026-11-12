package com.example.pocketpet.voice;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.PlaybackParams;
import android.util.Log;

import com.example.pocketpet.Bg;

/**
 * MOCHI VOICE: listens to whatever you say (any language, babble, laughing, animal sounds...) and plays it back
 * in a cute, higher-pitched voice. It does NOT understand words, so it works for every language and sound.
 *
 * How it works (all on the phone, nothing is sent anywhere or saved):
 *   1. Record from the microphone. It starts "for real" when it hears sound and stops by itself after a short silence.
 *   2. Play the same recording back with a higher pitch (PlaybackParams), so it sounds like a little cartoon creature.
 *   3. The recording only exists in memory while this runs, then it is thrown away.
 *
 * The caller must already have the RECORD_AUDIO permission. All Listener methods run on the main thread.
 */
public final class VoiceEcho {

    public interface Listener {
        void onListening();       // microphone is open: Mochi says "I'm listening!"
        void onSpeaking();        // recording finished: Mochi is repeating it
        void onNothingHeard();    // nothing but silence
        void onError();           // microphone not available
    }

    // ---- tune the cute voice here ----
    private static final float VOICE_PITCH = 1.7f;    // higher = squeakier (1.0 = your own voice)
    private static final float VOICE_SPEED = 1.05f;   // 1.0 = same speed as you spoke

    // ---- listening settings ----
    private static final int RATE = 16_000;                 // samples per second
    private static final int CHUNK = RATE / 20;             // 50 ms pieces
    private static final int MAX_CHUNKS = 200;              // never record more than 10 seconds
    private static final int WAIT_CHUNKS = 120;             // give up if nobody speaks within 6 seconds
    private static final int SILENCE_CHUNKS = 16;           // 0.8 s of quiet after speech = finished
    private static final int CALIBRATE_CHUNKS = 4;          // first 0.2 s measures the room noise
    private static final int PRE_ROLL_CHUNKS = 5;           // keep 0.25 s before the first sound so no word start is cut

    private static final String TAG = "VoiceEcho";

    private volatile boolean busy = false;
    private volatile boolean cancelled = false;

    public boolean isBusy() { return busy; }

    /** Stops listening / playing as soon as possible (e.g. when the screen closes). */
    public void cancel() { cancelled = true; }

    public synchronized void start(final Listener l) {
        if (busy) return;
        busy = true;
        cancelled = false;
        // Own thread: recording can take up to 10 s and must not block the app's shared background workers.
        new Thread(() -> run(l), "MochiVoice").start();
    }

    private void run(Listener l) {
        try {
            Bg.UI.post(l::onListening);
            short[] voice = record();
            if (cancelled) return;
            if (voice == null) { Bg.UI.post(l::onNothingHeard); return; }
            Bg.UI.post(l::onSpeaking);
            play(voice);
        } catch (Exception e) {
            Log.w(TAG, "Voice echo failed", e);
            if (!cancelled) Bg.UI.post(l::onError);
        } finally {
            busy = false;
        }
    }

    // ---------------------------------------------------------------- listen

    /** Returns the trimmed, loudness-adjusted recording, or null when nobody spoke. */
    private short[] record() {
        int minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) throw new IllegalStateException("No microphone buffer");
        AudioRecord rec = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, CHUNK * 8));
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) { rec.release(); throw new IllegalStateException("Microphone not ready"); }

        short[] all = new short[MAX_CHUNKS * CHUNK];
        short[] buf = new short[CHUNK];
        int n = 0, chunks = 0, silent = 0, speechStart = -1;
        double noise = 0, threshold = 900;
        boolean speaking = false;
        try {
            rec.startRecording();
            while (!cancelled && chunks < MAX_CHUNKS) {
                int r = rec.read(buf, 0, CHUNK);
                if (r <= 0) break;
                double sum = 0;
                for (int i = 0; i < r; i++) sum += (double) buf[i] * buf[i];
                double rms = Math.sqrt(sum / r);
                int before = n;
                System.arraycopy(buf, 0, all, n, r);
                n += r;
                chunks++;

                if (chunks <= CALIBRATE_CHUNKS) {                       // learn how noisy the room is
                    noise += rms;
                    if (chunks == CALIBRATE_CHUNKS) threshold = Math.max(700, Math.min(2500, noise / CALIBRATE_CHUNKS * 3.0));
                    continue;
                }
                if (rms > threshold) {                                  // someone is making a sound
                    if (!speaking) speechStart = Math.max(0, before - PRE_ROLL_CHUNKS * CHUNK);
                    speaking = true;
                    silent = 0;
                } else if (speaking) {
                    if (++silent >= SILENCE_CHUNKS) break;              // finished
                } else if (chunks >= WAIT_CHUNKS) {
                    break;                                              // nobody spoke
                }
            }
        } finally {
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
        }

        if (cancelled || !speaking) return null;
        int end = n - Math.max(0, silent - 4) * CHUNK;                  // cut the long quiet tail, keep 0.2 s
        end = Math.max(speechStart + 1, Math.min(n, end));
        int len = end - speechStart;
        if (len < RATE / 4) return null;                                // shorter than a quarter second: just a click

        short[] out = new short[len];
        System.arraycopy(all, speechStart, out, 0, len);
        normalize(out);
        return out;
    }

    /** Makes quiet voices louder (but never clips). */
    private static void normalize(short[] s) {
        int peak = 1;
        for (short v : s) peak = Math.max(peak, Math.abs((int) v));
        float gain = Math.min(6f, 24000f / peak);
        if (gain < 1.2f) return;
        for (int i = 0; i < s.length; i++) s[i] = (short) Math.max(-32768, Math.min(32767, Math.round(s[i] * gain)));
    }

    // ---------------------------------------------------------------- repeat in the cute voice

    private void play(short[] data) throws InterruptedException {
        AudioTrack track = new AudioTrack(
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                data.length * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
        try {
            track.write(data, 0, data.length);
            float speed = VOICE_SPEED;
            try {
                track.setPlaybackParams(new PlaybackParams().setPitch(VOICE_PITCH).setSpeed(VOICE_SPEED));
            } catch (Exception e) {
                // Very old/odd audio drivers: fall back to the simple "faster = higher" trick
                speed = 1.5f;
                track.setPlaybackRate((int) (RATE * speed));
            }
            track.play();
            long expectedMs = (long) (data.length * 1000L / (RATE * (double) speed));
            long until = System.currentTimeMillis() + expectedMs + 1200;
            while (!cancelled && System.currentTimeMillis() < until) {
                if (track.getPlaybackHeadPosition() >= data.length) break;
                Thread.sleep(40);
            }
            Thread.sleep(150);                                           // let the last sound finish
        } finally {
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
        }
    }
}
