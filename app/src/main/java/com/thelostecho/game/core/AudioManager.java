package com.thelostecho.game.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * Audio subsystem. All SFX are synthesized once at startup and loaded into a
 * SoundPool (no binary asset files required). Music is generated as WAV files,
 * cached to disk, and played through two MediaPlayers so area changes can
 * crossfade between tracks. Settings (sfx/music volume, mute) persist.
 */
public final class AudioManager {

    public static final int SFX_SONAR = 1;
    public static final int SFX_FOOT_CONCRETE = 2;
    public static final int SFX_FOOT_METAL = 3;
    public static final int SFX_FOOT_GRASS = 4;
    public static final int SFX_DRONE_ALERT = 5;
    public static final int SFX_TURRET_FIRE = 6;
    public static final int SFX_DOOR_OPEN = 7;
    public static final int SFX_DOOR_LOCKED = 8;
    public static final int SFX_COLLECT = 9;
    public static final int SFX_MENU_CLICK = 10;
    public static final int SFX_STONE = 11;
    public static final int SFX_STUN = 12;
    public static final int SFX_ALARM = 13;
    public static final int SFX_HUM = 14;
    public static final int SFX_INTERACT = 15;

    public static final int SURFACE_CONCRETE = 0;
    public static final int SURFACE_METAL = 1;
    public static final int SURFACE_GRASS = 2;

    public static final int MUSIC_SLUM = 0;
    public static final int MUSIC_LAB = 1;
    public static final int MUSIC_BASEMENT = 2;
    public static final int MUSIC_DATA = 3;

    private static final int SAMPLE_RATE = 22050;
    private static final String PREFS = "lostecho_audio";
    private static final String KEY_SFX = "sfx_volume";
    private static final String KEY_MUSIC = "music_volume";
    private static final String KEY_MUTED = "muted";

    private static AudioManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Random rng = new Random();
    private final SoundPool soundPool;

    private MediaPlayer current;
    private MediaPlayer pending;
    private int currentTrackId = -1;
    private int pendingTrackId = -1;
    private boolean crossfading;
    private boolean fadingIn;
    private float fadeTimer;
    private final float fadeDuration = 1.2f;

    private float sfxVolume = 0.9f;
    private float musicVolume = 0.7f;
    private boolean muted;

    private int humStreamId = 0;

    private AudioManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sfxVolume = prefs.getFloat(KEY_SFX, 0.9f);
        musicVolume = prefs.getFloat(KEY_MUSIC, 0.7f);
        muted = prefs.getBoolean(KEY_MUTED, false);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(12)
                .setAudioAttributes(attrs)
                .build();

        loadSfx(SFX_SONAR, synthSweep(380f, 980f, 0.5f, Wave.SINE, 0.8f));
        loadSfx(SFX_FOOT_CONCRETE, synthNoiseBurst(0.09f, 0.5f, 900f));
        loadSfx(SFX_FOOT_METAL, synthNoiseBurst(0.07f, 0.55f, 2600f));
        loadSfx(SFX_FOOT_GRASS, synthNoiseBurst(0.14f, 0.35f, 500f));
        loadSfx(SFX_DRONE_ALERT, synthSweep(700f, 900f, 0.35f, Wave.SAWTOOTH, 0.5f));
        loadSfx(SFX_TURRET_FIRE, synthSweep(240f, 90f, 0.16f, Wave.SQUARE, 0.6f));
        loadSfx(SFX_DOOR_OPEN, synthSweep(320f, 110f, 0.7f, Wave.TRIANGLE, 0.6f));
        loadSfx(SFX_DOOR_LOCKED, synthSweep(120f, 70f, 0.22f, Wave.SQUARE, 0.7f));
        loadSfx(SFX_COLLECT, synthTone(880f, 0.16f, Wave.SINE, 0.7f));
        loadSfx(SFX_MENU_CLICK, synthTone(1000f, 0.06f, Wave.SQUARE, 0.4f));
        loadSfx(SFX_STONE, synthNoiseBurst(0.28f, 0.3f, 1400f));
        loadSfx(SFX_STUN, synthSweep(1000f, 2100f, 0.3f, Wave.SINE, 0.8f));
        loadSfx(SFX_ALARM, synthTone(620f, 0.22f, Wave.SQUARE, 0.5f));
        loadSfx(SFX_HUM, synthNoiseBurst(1.0f, 0.18f, 300f));
        loadSfx(SFX_INTERACT, synthTone(640f, 0.09f, Wave.TRIANGLE, 0.5f));
    }

    public static synchronized AudioManager getInstance(Context context) {
        if (instance == null) {
            instance = new AudioManager(context);
        }
        return instance;
    }

    private void loadSfx(int id, byte[] pcm) {
        if (pcm != null) {
            soundPool.load(pcm, 0, pcm.length, 1);
        }
    }

    /** Called every frame so crossfades animate. */
    public void update(float delta) {
        if (crossfading && current != null && pending != null) {
            fadeTimer += delta;
            float k = fadeTimer / fadeDuration;
            if (k >= 1f) {
                k = 1f;
                current.stop();
                current.release();
                current = pending;
                currentTrackId = pendingTrackId;
                pending = null;
                pendingTrackId = -1;
                crossfading = false;
                current.setVolume(musicVolume, musicVolume);
            } else {
                current.setVolume(musicVolume * (1f - k), musicVolume * (1f - k));
                pending.setVolume(musicVolume * k, musicVolume * k);
            }
        } else if (fadingIn && current != null) {
            fadeTimer += delta;
            float k = fadeTimer / fadeDuration;
            if (k >= 1f) {
                k = 1f;
                fadingIn = false;
            }
            current.setVolume(musicVolume * k, musicVolume * k);
        }
    }

    public void playMusic(int trackId) {
        if (trackId == currentTrackId && !crossfading) {
            return;
        }
        if (trackId == pendingTrackId) {
            return;
        }
        try {
            MediaPlayer next = new MediaPlayer();
            next.setDataSource(prepareTrackFile(trackId));
            next.setLooping(true);
            next.prepare();
            next.setVolume(0f, 0f);
            next.start();

            if (current != null) {
                pending = next;
                pendingTrackId = trackId;
                crossfading = true;
                fadeTimer = 0f;
            } else {
                current = next;
                currentTrackId = trackId;
                fadingIn = true;
                fadeTimer = 0f;
                current.setVolume(0f, 0f);
            }
        } catch (Exception ignored) {
            // Audio is optional; never crash the game over music.
        }
    }

    private String prepareTrackFile(int trackId) {
        String name;
        if (trackId == MUSIC_SLUM) {
            name = "music_slum.wav";
        } else if (trackId == MUSIC_LAB) {
            name = "music_lab.wav";
        } else if (trackId == MUSIC_BASEMENT) {
            name = "music_basement.wav";
        } else {
            name = "music_data.wav";
        }
        File file = new File(appContext.getCacheDir(), name);
        if (!file.exists()) {
            short[] samples;
            if (trackId == MUSIC_SLUM) {
                samples = synthPad(new float[]{110f, 220f, 261.63f, 329.63f}, 8f, 0.10f);
            } else if (trackId == MUSIC_LAB) {
                samples = synthPad(new float[]{73.42f, 146.83f, 174.61f, 220f}, 8f, 0.09f);
            } else if (trackId == MUSIC_BASEMENT) {
                samples = synthPad(new float[]{55f, 110f, 130.81f}, 8f, 0.12f);
            } else {
                samples = synthPad(new float[]{87.31f, 174.61f, 207.65f}, 8f, 0.07f);
            }
            writeWav(file, samples);
        }
        return file.getAbsolutePath();
    }

    /** Plays a fire-and-forget SFX with volume and optional pitch shift. */
    public void playSfx(int sfxId, float volume, float pitch) {
        if (muted) {
            return;
        }
        float v = Math.max(0f, Math.min(1f, volume * sfxVolume));
        float p = Math.max(0.5f, Math.min(2f, pitch));
        soundPool.play(sfxId, v, v, 1, 0, p);
    }

    public void playSfx(int sfxId) {
        playSfx(sfxId, 1f, 1f);
    }

    /** Footstep with surface variation; volume drops while sneaking. */
    public void playFootstep(int surface, boolean sneaking) {
        int sfx;
        if (surface == SURFACE_METAL) {
            sfx = SFX_FOOT_METAL;
        } else if (surface == SURFACE_GRASS) {
            sfx = SFX_FOOT_GRASS;
        } else {
            sfx = SFX_FOOT_CONCRETE;
        }
        float vol = sneaking ? 0.22f : 0.55f;
        float pitch = 0.92f + rng.nextFloat() * 0.16f;
        playSfx(sfx, vol, pitch);
    }

    /** Starts the looping drone hum used for proximity-based audio. */
    public int startDroneHum() {
        if (humStreamId != 0) {
            return humStreamId;
        }
        float v = muted ? 0f : 0.1f * sfxVolume;
        humStreamId = soundPool.play(SFX_HUM, v, v, 1, -1, 1f);
        return humStreamId;
    }

    /** Adjusts drone hum volume by 1/distance factor. */
    public void setDroneHumVolume(float factor) {
        if (humStreamId == 0) {
            return;
        }
        float v = muted ? 0f : Math.max(0f, Math.min(1f, factor * 0.35f * sfxVolume));
        soundPool.setVolume(humStreamId, v, v);
    }

    public void stopDroneHum() {
        if (humStreamId != 0) {
            soundPool.stop(humStreamId);
            humStreamId = 0;
        }
    }

    public void setSfxVolume(float v) {
        sfxVolume = Math.max(0f, Math.min(1f, v));
        prefs.edit().putFloat(KEY_SFX, sfxVolume).apply();
    }

    public void setMusicVolume(float v) {
        musicVolume = Math.max(0f, Math.min(1f, v));
        if (current != null) {
            current.setVolume(musicVolume, musicVolume);
        }
        if (pending != null) {
            pending.setVolume(musicVolume, musicVolume);
        }
        prefs.edit().putFloat(KEY_MUSIC, musicVolume).apply();
    }

    public void setMuted(boolean m) {
        muted = m;
        prefs.edit().putBoolean(KEY_MUTED, m).apply();
        if (current != null) {
            current.setVolume(m ? 0f : musicVolume, m ? 0f : musicVolume);
        }
        if (pending != null) {
            pending.setVolume(m ? 0f : musicVolume, m ? 0f : musicVolume);
        }
        if (humStreamId != 0) {
            setDroneHumVolume(1f);
        }
    }

    public float getSfxVolume() {
        return sfxVolume;
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public boolean isMuted() {
        return muted;
    }

    /** Releases every audio resource. Call once from onDestroy. */
    public void release() {
        stopDroneHum();
        soundPool.release();
        if (current != null) {
            try {
                current.stop();
            } catch (Exception ignored) {
            }
            current.release();
            current = null;
        }
        if (pending != null) {
            try {
                pending.stop();
            } catch (Exception ignored) {
            }
            pending.release();
            pending = null;
        }
        instance = null;
    }

    // ------------------------------------------------------------------
    // Synthesis helpers
    // ------------------------------------------------------------------

    private enum Wave { SINE, SQUARE, SAWTOOTH, TRIANGLE }

    private float waveSample(Wave w, float phase) {
        switch (w) {
            case SINE:
                return (float) Math.sin(phase);
            case SQUARE:
                return Math.sin(phase) >= 0f ? 1f : -1f;
            case SAWTOOTH:
                return (float) (2.0 * ((phase / (2 * Math.PI)) - Math.floor(0.5 + phase / (2 * Math.PI))));
            case TRIANGLE:
                return (float) (2.0 / Math.PI) * (float) Math.asin(Math.sin(phase));
            default:
                return 0f;
        }
    }

    private byte[] synthTone(float freq, float dur, Wave w, float vol) {
        return synthSweep(freq, freq, dur, w, vol);
    }

    private byte[] synthSweep(float f0, float f1, float dur, Wave w, float vol) {
        int n = (int) (SAMPLE_RATE * dur);
        short[] out = new short[n];
        float phase = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) SAMPLE_RATE;
            float k = i / (float) n;
            float freq = f0 + (f1 - f0) * k;
            phase += (float) (2 * Math.PI * freq / SAMPLE_RATE);
            float env = 0.5f + 0.5f * (float) Math.sin(Math.PI * k);
            out[i] = (short) (waveSample(w, phase) * env * vol * 32767f);
        }
        return toPcmBytes(out);
    }

    private byte[] synthNoiseBurst(float dur, float vol, float cutoff) {
        int n = (int) (SAMPLE_RATE * dur);
        short[] out = new short[n];
        float filter = 0f;
        for (int i = 0; i < n; i++) {
            float k = i / (float) n;
            float white = rng.nextFloat() * 2f - 1f;
            filter += (white - filter) * Math.min(1f, cutoff / SAMPLE_RATE);
            float env = 0.5f + 0.5f * (float) Math.sin(Math.PI * k);
            out[i] = (short) (filter * env * vol * 32767f);
        }
        return toPcmBytes(out);
    }

    private short[] synthPad(float[] freqs, float dur, float vol) {
        int n = (int) (SAMPLE_RATE * dur);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) SAMPLE_RATE;
            float slow = (float) Math.sin(2f * Math.PI * 0.25f * t) * 0.5f + 0.5f;
            float chord = 0f;
            for (int f = 0; f < freqs.length; f++) {
                float vibrato = 1f + 0.002f * (float) Math.sin(2f * Math.PI * 0.6f * t + f);
                chord += (float) Math.sin(2f * Math.PI * freqs[f] * vibrato * t);
                chord += 0.4f * (float) Math.sin(2f * Math.PI * freqs[f] * 2f * t);
            }
            chord /= freqs.length;
            float loop = (float) Math.sin(2f * Math.PI * t / dur);
            float env = 0.8f + 0.2f * loop;
            out[i] = (short) (chord * env * vol * slow * 0.6f * 32767f);
        }
        return out;
    }

    private byte[] toPcmBytes(short[] samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int v = samples[i];
            bytes[i * 2] = (byte) (v & 0xFF);
            bytes[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return bytes;
    }

    private void writeWav(File file, short[] samples) {
        try {
            int dataLen = samples.length * 2;
            byte[] header = new byte[44];
            int sampleRate = SAMPLE_RATE;
            int channels = 1;
            int bitsPerSample = 16;
            int byteRate = sampleRate * channels * bitsPerSample / 8;
            int blockAlign = channels * bitsPerSample / 8;
            header[0] = 'R';
            header[1] = 'I';
            header[2] = 'F';
            header[3] = 'F';
            writeInt(header, 4, 36 + dataLen);
            header[8] = 'W';
            header[9] = 'A';
            header[10] = 'V';
            header[11] = 'E';
            header[12] = 'f';
            header[13] = 'm';
            header[14] = 't';
            header[15] = ' ';
            writeInt(header, 16, 16);
            header[20] = 1;
            header[21] = 0;
            header[22] = (byte) channels;
            header[23] = 0;
            writeInt(header, 24, sampleRate);
            writeInt(header, 28, byteRate);
            header[32] = (byte) blockAlign;
            header[33] = 0;
            header[34] = (byte) bitsPerSample;
            header[35] = 0;
            header[36] = 'd';
            header[37] = 'a';
            header[38] = 't';
            header[39] = 'a';
            writeInt(header, 40, dataLen);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(header);
            byte[] pcm = toPcmBytes(samples);
            fos.write(pcm);
            fos.flush();
            fos.close();
        } catch (Exception ignored) {
            // Music is best-effort; never crash over file IO.
        }
    }

    private void writeInt(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }
}
