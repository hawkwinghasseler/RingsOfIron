package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ShortBuffer;
import java.nio.file.Files;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.EXTEfx.*;
import static org.lwjgl.openal.SOFTHRTF.*;

/**
 * Plays sound through OpenAL Soft, the open-source (LGPL) OpenAL that LWJGL ships and libGDX's
 * desktop backend already runs. It shares libGDX's OpenAL context rather than opening a device
 * of its own.
 *
 * Each playing instance gets an OpenAL source. An event plays a recorded clip when one exists
 * at {@code audio/events/<name>.wav} (for {@code event:/weapons/laser}, that is
 * {@code audio/events/weapons/laser.wav}); otherwise its {@link SynthVoice} placeholder is
 * rendered and streamed to the source, so every event is audible before any sound is recorded.
 *
 * The mix follows {@link AcousticModel}, as the synth backend's does: this class sets each
 * source's gain from the model and turns off OpenAL's own distance attenuation. OpenAL places
 * the source around the listener, which is where HRTF comes in: OpenAL Soft turns it on by
 * itself when it detects headphones, and the {@code audio.hrtf} system property ({@code on} or
 * {@code off}) forces it. Occlusion drives an EFX low-pass filter on each source; without the
 * EFX extension sounds play unmuffled.
 *
 * The world is 2D. Sources are placed relative to the listener, which faces world +y with +x
 * on its right; each sits its event's minimum distance above the plane, so a sound right on
 * top of the listener comes from the middle rather than hard to one side.
 *
 * Every OpenAL call is made from the game thread, in {@link #update} and {@link #dispose}.
 */
public class OpenAlAudioBackend extends SimulatedBackend {
    /** Where recorded clips are looked for: an internal (assets) folder. */
    public static final String DEFAULT_CLIP_DIR = "audio/events";
    /** Sample rate of the streamed synth placeholders. */
    static final int STREAM_RATE = SynthAudioBackend.SAMPLE_RATE;
    /** Buffers queued per streaming source, and frames per buffer: about 90 ms in flight. */
    static final int STREAM_BUFFERS = 4;
    static final int STREAM_FRAMES = 1024;
    /** Sources this backend may hold at once, leaving the rest of OpenAL Soft's 256 to libGDX. */
    static final int MAX_SOURCES = 128;

    private final String clipDir;
    private final boolean efx;
    private final Array<Voice> idle = new Array<>();
    private final ObjectMap<String, Clip> clips = new ObjectMap<>();
    private final float[] dry = new float[STREAM_FRAMES];
    private final ShortBuffer pcm = MemoryUtil.memAllocShort(STREAM_FRAMES);
    private int voicesCreated;
    private boolean outOfSources;
    private float masterVolume = 0.8f;
    private boolean disposed;

    /**
     * Plays through the current OpenAL context.
     *
     * @throws IllegalStateException if no OpenAL context is current
     */
    public OpenAlAudioBackend(AudioEventCatalog catalog, String clipDir) {
        super(catalog);
        long context = alcGetCurrentContext();
        if (context == MemoryUtil.NULL) throw new IllegalStateException("No current OpenAL context");
        ALCapabilities caps = AL.getCapabilities();
        this.clipDir = clipDir;
        this.efx = caps.ALC_EXT_EFX;
        if (!efx) log("OpenAL has no EFX extension: occluded sounds will not be muffled");
        applyHrtfSetting(alcGetContextsDevice(context), System.getProperty("audio.hrtf"));
    }

    /**
     * An OpenAL backend on libGDX's context with clips from {@link #DEFAULT_CLIP_DIR}, or null
     * when OpenAL is not running (another libGDX backend, or audio disabled). The reason is logged.
     */
    public static OpenAlAudioBackend tryCreate(AudioEventCatalog catalog) {
        try {
            OpenAlAudioBackend b = new OpenAlAudioBackend(catalog, DEFAULT_CLIP_DIR);
            log("Playing through " + alGetString(AL_RENDERER) + " " + alGetString(AL_VERSION)
                    + (b.efx ? " with EFX" : "") + ", HRTF " + b.hrtfStatus());
            return b;
        } catch (RuntimeException | LinkageError e) {
            log("OpenAL not available, using the built-in synth: " + e);
            return null;
        }
    }

    public void setMasterVolume(float volume) {
        masterVolume = volume;
    }

    /** Whether occlusion can muffle sounds (OpenAL's EFX extension is present). */
    public boolean hasEfx() {
        return efx;
    }

    /** OpenAL Soft's HRTF state, such as "enabled", "disabled" or "headphones detected". */
    public String hrtfStatus() {
        long device = alcGetContextsDevice(alcGetCurrentContext());
        if (!alcIsExtensionPresent(device, "ALC_SOFT_HRTF")) return "unsupported";
        switch (alcGetInteger(device, ALC_HRTF_STATUS_SOFT)) {
            case ALC_HRTF_DISABLED_SOFT: return "disabled";
            case ALC_HRTF_ENABLED_SOFT: return "enabled";
            case ALC_HRTF_DENIED_SOFT: return "denied by config";
            case ALC_HRTF_REQUIRED_SOFT: return "required by config";
            case ALC_HRTF_HEADPHONES_DETECTED_SOFT: return "headphones detected";
            case ALC_HRTF_UNSUPPORTED_FORMAT_SOFT: return "unsupported output format";
            default: return "unknown";
        }
    }

    /** Forces HRTF on or off for "on" or "off"; anything else leaves OpenAL Soft to decide. */
    private static void applyHrtfSetting(long device, String setting) {
        if (setting == null || !(setting.equals("on") || setting.equals("off"))) return;
        if (!alcIsExtensionPresent(device, "ALC_SOFT_HRTF")) {
            log("OpenAL has no HRTF support; ignoring audio.hrtf=" + setting);
            return;
        }
        int[] attributes = {ALC_HRTF_SOFT, setting.equals("on") ? ALC_TRUE : ALC_FALSE, 0};
        if (!alcResetDeviceSOFT(device, attributes)) log("OpenAL could not apply audio.hrtf=" + setting);
    }

    @Override
    protected SimulatedInstance createSimulated(SoundEventDef def) {
        Clip clip = clip(def.path);
        if (clip == null) return new Instance(def, null);
        // A recorded one-shot lasts as long as its recording
        SoundEventDef d = def.looping ? def
                : new SoundEventDef(def.path, false, clip.duration, def.minDistance, def.maxDistance, def.volume);
        return new Instance(d, clip);
    }

    /** The recorded clip for an event path, loaded on first use, or null if there is none. */
    private Clip clip(String eventPath) {
        if (clips.containsKey(eventPath)) return clips.get(eventPath);
        Clip clip = null;
        byte[] data = readClip(clipFile(eventPath));
        if (data != null) {
            try {
                WavClip wav = WavClip.parse(data);
                int buffer = alGenBuffers();
                alBufferData(buffer, AL_FORMAT_MONO16, wav.samples, wav.sampleRate);
                clip = new Clip(buffer, wav.duration());
            } catch (IllegalArgumentException e) {
                log("Could not load clip for " + eventPath + ": " + e.getMessage());
            }
        }
        clips.put(eventPath, clip);
        return clip;
    }

    /** The clip file for an event path: {@code event:/weapons/laser} becomes {@code weapons/laser.wav}. */
    static String clipFile(String eventPath) {
        String name = eventPath.startsWith("event:/") ? eventPath.substring("event:/".length()) : eventPath;
        return name + ".wav";
    }

    /** Reads a clip from the internal (assets) folder, or the file system when there is none. */
    private byte[] readClip(String name) {
        if (Gdx.files != null) {
            FileHandle f = Gdx.files.internal(clipDir).child(name);
            if (f.exists()) return f.readBytes();
        }
        File f = new File(clipDir, name);
        if (!f.isFile()) return null;
        try {
            return Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            log("Could not read " + f + ": " + e);
            return null;
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        for (SimulatedInstance i : instances) ((Instance) i).sync();
    }

    @Override
    protected void onRemoved(SimulatedInstance instance) {
        ((Instance) instance).silence();
    }

    /** Instances that currently hold an OpenAL source. */
    public int getVoicesInUse() {
        return voicesCreated - idle.size;
    }

    private Voice obtainVoice() {
        if (idle.size > 0) return idle.pop();
        if (voicesCreated >= MAX_SOURCES || outOfSources) return null;
        alGetError();
        int source = alGenSources();
        if (alGetError() != AL_NO_ERROR) {
            // OpenAL ran out of sources: play what fits and drop the rest silently
            log("OpenAL has no more sources after " + voicesCreated);
            outOfSources = true;
            return null;
        }
        alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
        alSourcef(source, AL_ROLLOFF_FACTOR, 0f); // AcousticModel sets the gain
        int filter = 0;
        if (efx) {
            filter = alGenFilters();
            alFilteri(filter, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
        }
        int[] buffers = new int[STREAM_BUFFERS];
        alGenBuffers(buffers);
        voicesCreated++;
        return new Voice(source, filter, buffers);
    }

    private void freeVoice(Voice v) {
        alSourceStop(v.source);
        alSourcei(v.source, AL_BUFFER, 0); // also unqueues streamed buffers
        alSourcei(v.source, AL_LOOPING, AL_FALSE);
        v.appliedGainHf = -1f;
        idle.add(v);
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (SimulatedInstance i : instances) ((Instance) i).silence();
        for (Voice v : idle) {
            alDeleteSources(v.source);
            if (v.filter != 0) alDeleteFilters(v.filter);
            alDeleteBuffers(v.buffers);
        }
        idle.clear();
        for (Clip c : clips.values()) if (c != null) alDeleteBuffers(c.buffer);
        clips.clear();
        MemoryUtil.memFree(pcm);
        super.dispose();
    }

    private static void log(String message) {
        if (Gdx.app != null) Gdx.app.log("audio", message);
        else System.err.println("[audio] " + message);
    }

    /** A loaded recording. */
    private static class Clip {
        final int buffer;
        final float duration;

        Clip(int buffer, float duration) {
            this.buffer = buffer;
            this.duration = duration;
        }
    }

    /** An OpenAL source with its low-pass filter and streaming buffers, reused between instances. */
    private static class Voice {
        final int source;
        final int filter;
        final int[] buffers;
        float appliedGainHf = -1f;

        Voice(int source, int filter, int[] buffers) {
            this.source = source;
            this.filter = filter;
            this.buffers = buffers;
        }
    }

    private class Instance extends SimulatedInstance {
        final Clip clip;
        /** The placeholder streamed when there is no clip. */
        final SynthVoice synth;
        Voice voice;
        private int starts;
        private int appliedStarts;

        Instance(SoundEventDef def, Clip clip) {
            super(def);
            this.clip = clip;
            synth = clip == null ? new SynthVoice(SynthVoice.patchFor(def.path), def.looping, def.duration) : null;
        }

        @Override
        public void start() {
            super.start();
            starts++;
        }

        /** Brings the OpenAL source in line with the instance after the mix has been worked out. */
        void sync() {
            if (!isPlaying()) {
                silence();
                return;
            }
            if (voice == null) {
                voice = obtainVoice();
                if (voice == null) return;
                appliedStarts = starts - 1; // begin playback below
            }
            int s = voice.source;
            alSourcef(s, AL_GAIN, mix.gain * masterVolume);
            alSource3f(s, AL_POSITION, x - listenerX, def.minDistance, -(y - listenerY));
            if (efx) {
                float gainHf = AcousticModel.lowPassGainHf(mix.cutoffHz);
                if (gainHf != voice.appliedGainHf) {
                    voice.appliedGainHf = gainHf;
                    alFilterf(voice.filter, AL_LOWPASS_GAINHF, gainHf);
                    alSourcei(s, AL_DIRECT_FILTER, voice.filter); // a source copies the filter when it is attached
                }
            }
            if (appliedStarts != starts) {
                appliedStarts = starts;
                begin(s);
            } else if (synth != null) {
                refill(s);
            }
        }

        /** Starts playback from the top. */
        private void begin(int s) {
            alSourceStop(s);
            alSourcei(s, AL_BUFFER, 0);
            if (clip != null) {
                alSourcei(s, AL_BUFFER, clip.buffer);
                alSourcei(s, AL_LOOPING, def.looping ? AL_TRUE : AL_FALSE);
            } else {
                alSourcei(s, AL_LOOPING, AL_FALSE);
                synth.restart();
                for (int b : voice.buffers) {
                    fill(b);
                    alSourceQueueBuffers(s, b);
                }
            }
            alSourcePlay(s);
        }

        /** Tops up a streaming source with freshly rendered placeholder audio. */
        private void refill(int s) {
            int processed = alGetSourcei(s, AL_BUFFERS_PROCESSED);
            for (int n = 0; n < processed; n++) {
                int b = alSourceUnqueueBuffers(s);
                fill(b);
                alSourceQueueBuffers(s, b);
            }
            // A long frame can drain the queue and stop the source; pick up where it left off
            if (alGetSourcei(s, AL_SOURCE_STATE) != AL_PLAYING) alSourcePlay(s);
        }

        private void fill(int buffer) {
            synth.renderDry(dry, STREAM_FRAMES, STREAM_RATE);
            pcm.clear();
            for (float v : dry) pcm.put((short) (Math.max(-1f, Math.min(1f, v)) * Short.MAX_VALUE));
            pcm.flip();
            alBufferData(buffer, AL_FORMAT_MONO16, pcm, STREAM_RATE);
        }

        /** Gives the source back. */
        void silence() {
            if (voice == null) return;
            freeVoice(voice);
            voice = null;
        }
    }
}
