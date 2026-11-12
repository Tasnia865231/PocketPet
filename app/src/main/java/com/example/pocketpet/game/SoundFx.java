package com.example.pocketpet.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

import com.example.pocketpet.Bg;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * Sound effects and cozy background music for the mini-games.
 * Nothing is bundled: every sound is synthesised on first use, saved as a small WAV in the cache folder,
 * and played with SoundPool (effects) and MediaPlayer (looping music).
 */
public final class SoundFx {
    public static final int TAP = 0, PLACE_USER = 1, PLACE_CPU = 2, WIN = 3, LOSE = 4, DRAW = 5, REVEAL = 6,
            FLAG = 7, BOOM = 8, CATCH = 9, BAD = 10, BOUNCE = 11, CRUMBLE = 12, SWAP = 13, FOUND = 14, WRONG = 15;
    private static final int COUNT = 16;

    public static final int MUSIC_MENU = 0, MUSIC_TTT = 1, MUSIC_MINE = 2, MUSIC_JUMP = 3, MUSIC_HIDE = 4, MUSIC_CATCH = 5;

    private static final int RATE = 22050;
    private static final String VER = "v1";

    private final File cache;
    private final SharedPreferences prefs;
    private final SoundPool pool;
    private final int[] ids = new int[COUNT];
    private MediaPlayer music;
    private int musicWanted = -1;
    private int musicPlaying = -1;
    private boolean enabled;
    private boolean released;

    public SoundFx(Context ctx) {
        cache = ctx.getCacheDir();
        prefs = ctx.getSharedPreferences("pocketpet_games", Context.MODE_PRIVATE);
        enabled = prefs.getBoolean("sound", true);
        pool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        Bg.IO.execute(this::buildEffects);
    }

    // ------------------------------------------------------------------ public API

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean on) {
        enabled = on;
        prefs.edit().putBoolean("sound", on).apply();
        if (!on) stopPlayer();
        else if (musicWanted >= 0) startMusic(musicWanted);
    }

    public void play(int sfx) {
        if (!enabled || released || sfx < 0 || sfx >= COUNT) return;
        int id = ids[sfx];
        if (id != 0) pool.play(id, 0.9f, 0.9f, 1, 0, 1f);
    }

    /** Start a looping tune (does nothing if that tune is already playing). */
    public void startMusic(final int which) {
        musicWanted = which;
        if (!enabled || released) return;
        if (musicPlaying == which && music != null) { if (!music.isPlaying()) music.start(); return; }
        final File f = new File(cache, "music_" + VER + "_" + which + ".wav");
        Bg.IO.execute(() -> {
            if (!f.exists() || f.length() == 0) writeWav(f, makeMusic(which));
            Bg.UI.post(() -> { if (!released && enabled && musicWanted == which) playFile(f, which); });
        });
    }

    public void pauseMusic() {
        try { if (music != null && music.isPlaying()) music.pause(); } catch (Exception ignored) { }
    }

    public void resumeMusic() {
        if (!enabled || released) return;
        try {
            if (music != null) music.start();
            else if (musicWanted >= 0) startMusic(musicWanted);
        } catch (Exception ignored) { }
    }

    public void release() {
        released = true;
        stopPlayer();
        pool.release();
    }

    // ------------------------------------------------------------------ playback

    private void playFile(File f, int which) {
        stopPlayer();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(f.getAbsolutePath());
            mp.setLooping(true);
            mp.setVolume(0.33f, 0.33f);
            mp.prepare();
            mp.start();
            music = mp;
            musicPlaying = which;
        } catch (Exception e) {
            music = null;
            musicPlaying = -1;
        }
    }

    private void stopPlayer() {
        if (music != null) {
            try { music.stop(); } catch (Exception ignored) { }
            try { music.release(); } catch (Exception ignored) { }
            music = null;
        }
        musicPlaying = -1;
    }

    private void buildEffects() {
        for (int i = 0; i < COUNT; i++) {
            File f = new File(cache, "sfx_" + VER + "_" + i + ".wav");
            if (!f.exists() || f.length() == 0) writeWav(f, makeSfx(i));
            if (released) return;
            ids[i] = pool.load(f.getAbsolutePath(), 1);
        }
    }

    // ------------------------------------------------------------------ synthesis

    private static final int SINE = 0, TRI = 1, NOISE = 2;
    private static final Random NOISE_RND = new Random(99);

    /** One note: frequency glides f0 -> f1, quick attack, smooth decay. */
    private static short[] tone(float f0, float f1, float dur, float vol, int wave) {
        int n = (int) (RATE * dur);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * f / RATE;
            double s;
            if (wave == SINE) s = Math.sin(phase);
            else if (wave == TRI) s = 2 / Math.PI * Math.asin(Math.sin(phase));
            else s = NOISE_RND.nextDouble() * 2 - 1;
            double env = Math.min(1.0, i / (RATE * 0.006)) * Math.pow(1 - t, 1.7);
            out[i] = (short) (s * env * vol * 32767);
        }
        return out;
    }

    private static short[] silence(float dur) { return new short[(int) (RATE * dur)]; }

    private static short[] join(short[]... parts) {
        int n = 0;
        for (short[] p : parts) n += p.length;
        short[] out = new short[n];
        int k = 0;
        for (short[] p : parts) { System.arraycopy(p, 0, out, k, p.length); k += p.length; }
        return out;
    }

    /** Adds b into a starting at sample offset. */
    private static void mixInto(short[] a, short[] b, int offset) {
        for (int i = 0; i < b.length && offset + i < a.length; i++) {
            int v = a[offset + i] + b[i];
            a[offset + i] = (short) Math.max(-32768, Math.min(32767, v));
        }
    }

    private static float hz(float root, int semis) {
        return (float) (root * Math.pow(2.0, semis / 12.0));
    }

    private short[] makeSfx(int id) {
        switch (id) {
            case TAP:        return tone(520, 720, 0.07f, 0.45f, SINE);
            case PLACE_USER: return tone(660, 900, 0.11f, 0.5f, SINE);
            case PLACE_CPU:  return tone(470, 330, 0.13f, 0.5f, TRI);
            case WIN:        return join(tone(523, 523, 0.13f, 0.5f, TRI), tone(659, 659, 0.13f, 0.5f, TRI),
                                         tone(784, 784, 0.13f, 0.5f, TRI), tone(1047, 1047, 0.45f, 0.55f, TRI));
            case LOSE:       return join(tone(392, 392, 0.2f, 0.5f, SINE), tone(330, 330, 0.2f, 0.5f, SINE),
                                         tone(262, 240, 0.45f, 0.5f, SINE));
            case DRAW:       return join(tone(440, 440, 0.18f, 0.45f, SINE), tone(440, 440, 0.3f, 0.4f, SINE));
            case REVEAL:     return join(tone(880, 1040, 0.05f, 0.4f, SINE), tone(660, 780, 0.07f, 0.3f, SINE));
            case FLAG:       return tone(420, 560, 0.09f, 0.45f, TRI);
            case BOOM: {
                short[] noise = tone(0, 0, 0.55f, 0.35f, NOISE);
                mixInto(noise, tone(160, 50, 0.5f, 0.5f, SINE), 0);
                return noise;
            }
            case CATCH:      return join(tone(880, 1100, 0.06f, 0.45f, SINE), tone(1320, 1320, 0.1f, 0.4f, SINE));
            case BAD:        return tone(230, 90, 0.25f, 0.6f, TRI);
            case BOUNCE:     return tone(300, 700, 0.14f, 0.5f, SINE);
            case CRUMBLE:    return tone(0, 0, 0.22f, 0.35f, NOISE);
            case SWAP:       return tone(280, 520, 0.12f, 0.3f, SINE);
            case FOUND:      return join(tone(659, 659, 0.1f, 0.5f, TRI), tone(784, 784, 0.1f, 0.5f, TRI),
                                         tone(659, 659, 0.1f, 0.5f, TRI), tone(1047, 1047, 0.4f, 0.55f, TRI));
            default:         return join(tone(330, 250, 0.2f, 0.5f, TRI));
        }
    }

    /** A music-box style loop: pentatonic melody + soft bass, seamless at the loop point. */
    private short[] makeMusic(int which) {
        float[] bpm   = {74, 92, 84, 118, 80, 128};
        float[] root  = {261.63f, 293.66f, 220f, 392f, 329.63f, 349.23f};
        float[] dens  = {0.55f, 0.65f, 0.55f, 0.8f, 0.5f, 0.85f};
        int[] major = {0, 2, 4, 7, 9};
        int[] minor = {0, 3, 5, 7, 10};
        int[] scale = (which == MUSIC_MINE || which == MUSIC_HIDE) ? minor : major;

        float step = 60f / bpm[which] / 2f;            // eighth notes
        int steps = 32;
        int total = (int) (RATE * step * steps);
        float[] buf = new float[total + RATE * 2];
        Random rnd = new Random(which * 31L + 7);

        int[] melody = new int[16];
        for (int i = 0; i < 16; i++) {
            melody[i] = rnd.nextFloat() < dens[which] ? scale[rnd.nextInt(scale.length)] + (rnd.nextFloat() < 0.35f ? 12 : 0) : -1;
        }
        for (int s = 0; s < steps; s++) {
            int m = melody[s % 16];
            if (s >= 16 && rnd.nextFloat() < 0.15f) m = scale[rnd.nextInt(scale.length)];   // small variation
            if (m >= 0) bell(buf, (int) (s * step * RATE), hz(root[which], m), 1.0f, 0.22f);
            if (s % 8 == 0) bell(buf, (int) (s * step * RATE), hz(root[which] / 2f, scale[(s / 8) % scale.length]), 1.6f, 0.2f);
        }

        // fold the tail back onto the start so the loop has no click
        float[] mixed = new float[total];
        for (int i = 0; i < buf.length; i++) mixed[i % total] += buf[i];
        float peak = 0.001f;
        for (float v : mixed) peak = Math.max(peak, Math.abs(v));
        short[] out = new short[total];
        for (int i = 0; i < total; i++) out[i] = (short) (mixed[i] / peak * 0.55f * 32767);
        return out;
    }

    private static void bell(float[] buf, int start, float f, float dur, float vol) {
        int n = (int) (RATE * dur);
        for (int i = 0; i < n && start + i < buf.length; i++) {
            double t = i / (double) RATE;
            double env = Math.min(1.0, i / (RATE * 0.004)) * Math.exp(-t * (3.2 / dur));
            double s = Math.sin(2 * Math.PI * f * t) + 0.32 * Math.sin(4 * Math.PI * f * t) + 0.12 * Math.sin(6 * Math.PI * f * t);
            buf[start + i] += (float) (s * env * vol);
        }
    }

    private static void writeWav(File f, short[] pcm) {
        try (FileOutputStream os = new FileOutputStream(f)) {
            int dataLen = pcm.length * 2;
            ByteBuffer b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
            b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataLen).put(new byte[]{'W', 'A', 'V', 'E'});
            b.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1)
             .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
            b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataLen);
            for (short s : pcm) b.putShort(s);
            os.write(b.array());
        } catch (Exception ignored) {
            f.delete();
        }
    }
}
