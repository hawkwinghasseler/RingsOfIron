package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectFloatMap;
import org.lwjgl.PointerBuffer;
import org.lwjgl.fmod.FMOD;
import org.lwjgl.fmod.FMODStudio;
import org.lwjgl.fmod.FMOD_3D_ATTRIBUTES;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Locale;

import static org.lwjgl.fmod.FMOD.*;
import static org.lwjgl.fmod.FMODStudio.*;

/**
 * Plays sound through the FMOD Studio API, using LWJGL's FMOD bindings.
 *
 * FMOD's libraries are not shipped with LWJGL or this project: install the FMOD Engine
 * (2.03.x, to match the bindings) and copy {@code fmod} and {@code fmodstudio} for your platform
 * into a {@code fmod/} folder in the project root (see {@link #findLibraries}). Banks built by the
 * FMOD Studio project go in {@code assets/audio/banks/}.
 *
 * Events missing from the loaded banks play on the {@code fallback} backend instead, so the
 * synth placeholders keep working while banks are filled in one event at a time.
 *
 * The audio systems' parameters are applied here rather than left to the FMOD project:
 * {@link AudioParameters#OCCLUSION} sets a low-pass filter added to each event, and
 * {@link AudioParameters#TRANSMISSION} and {@link AudioParameters#PRESSURE} set the event's
 * volume, following {@link AcousticModel}. Every parameter is also passed to the event by
 * name, so a sound designer can add more on top (for example more reverb when muffled), but
 * should not automate volume or a low-pass on these three.
 *
 * The world is 2D; FMOD's is 3D. World (x, y) maps to FMOD (x, 0, y), with the listener facing
 * +y and +x on its right.
 */
public class FmodAudioBackend implements AudioBackend {
    /** Where banks are looked for: an internal (assets) folder. */
    public static final String DEFAULT_BANK_DIR = "audio/banks";
    /** Bank files loaded when the bank folder cannot be listed (inside a jar). */
    static final String[] DEFAULT_BANKS = {"Master.strings.bank", "Master.bank"};
    /** Voices FMOD may mix at once. */
    static final int MAX_CHANNELS = 512;

    private final long studio;
    private final long core;
    private final AudioBackend fallback;
    private final Array<FmodInstance> instances = new Array<>();
    private final ObjectFloatMap<String> globals = new ObjectFloatMap<>();
    private int banksLoaded;
    private boolean disposed;

    private FmodAudioBackend(long studio, long core, AudioBackend fallback) {
        this.studio = studio;
        this.core = core;
        this.fallback = fallback;
    }

    /**
     * Starts FMOD and loads every bank in {@link #DEFAULT_BANK_DIR}, or returns null when FMOD's
     * libraries are not installed or no banks are found. The reason is logged.
     *
     * @param fallback plays events the banks don't have; never null
     */
    public static FmodAudioBackend tryCreate(AudioBackend fallback) {
        FmodAudioBackend fmod;
        try {
            findLibraries();
            fmod = create(fallback);
        } catch (LinkageError | IllegalStateException e) {
            log("FMOD not available, using built-in audio: " + e);
            return null;
        }
        fmod.loadBankDirectory(DEFAULT_BANK_DIR);
        if (fmod.banksLoaded == 0) {
            log("No FMOD banks in " + DEFAULT_BANK_DIR + ", using built-in audio");
            fmod.release();
            return null;
        }
        return fmod;
    }

    /**
     * Starts FMOD Studio with no banks loaded.
     *
     * @throws LinkageError if FMOD's libraries cannot be loaded
     * @throws IllegalStateException if FMOD fails to start, such as on a version mismatch
     */
    public static FmodAudioBackend create(AudioBackend fallback) {
        // Load the core library first so fmodstudio finds it already in the process
        FMOD.getLibrary();
        FMODStudio.getLibrary();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            check(FMOD_Studio_System_Create(pp, FMOD_VERSION), "create the Studio system");
            long studio = pp.get(0);
            int r = FMOD_Studio_System_Initialize(studio, MAX_CHANNELS, FMOD_STUDIO_INIT_NORMAL, FMOD_INIT_NORMAL, MemoryUtil.NULL);
            if (r != FMOD_OK) {
                FMOD_Studio_System_Release(studio);
                check(r, "initialize");
            }
            check(FMOD_Studio_System_GetCoreSystem(studio, pp), "get the core system");
            return new FmodAudioBackend(studio, pp.get(0), fallback);
        }
    }

    /**
     * Points LWJGL at FMOD libraries in a {@code fmod/} folder (or the folder in the
     * {@code fmod.dir} system property), looked for in the working directory and its parent so
     * it is found when the game runs from {@code assets/}. Without one, LWJGL searches
     * {@code java.library.path} as usual.
     */
    public static void findLibraries() {
        String configured = System.getProperty("fmod.dir");
        File cwd = new File("").getAbsoluteFile();
        File[] candidates = configured != null ? new File[]{new File(configured)}
                : new File[]{new File(cwd, "fmod"), new File(cwd.getParentFile(), "fmod")};
        for (File dir : candidates) {
            File core = new File(dir, libraryFile("fmod"));
            File studio = new File(dir, libraryFile("fmodstudio"));
            if (core.isFile() && studio.isFile()) {
                Configuration.FMOD_LIBRARY_NAME.set(core.getAbsolutePath());
                Configuration.FMOD_STUDIO_LIBRARY_NAME.set(studio.getAbsolutePath());
                return;
            }
        }
    }

    /** The platform's file name for an FMOD library, e.g. {@code fmod.dll} or {@code libfmod.so}. */
    static String libraryFile(String name) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return name + ".dll";
        if (os.contains("mac")) return "lib" + name + ".dylib";
        return "lib" + name + ".so";
    }

    /** The backend that plays events missing from the banks. */
    public AudioBackend getFallback() { return fallback; }

    public int getBanksLoaded() { return banksLoaded; }

    /** Loads every {@code .bank} in an internal folder, or {@link #DEFAULT_BANKS} if it can't be listed. */
    public void loadBankDirectory(String dir) {
        if (Gdx.files == null) {
            File folder = new File(dir);
            File[] files = folder.listFiles((d, n) -> n.endsWith(".bank"));
            if (files != null) for (File f : files) loadBank(f.getPath());
            return;
        }
        FileHandle folder = Gdx.files.internal(dir);
        FileHandle[] files = folder.list(".bank");
        if (files.length > 0) {
            for (FileHandle f : files) loadBank(f);
        } else {
            for (String name : DEFAULT_BANKS) {
                FileHandle f = folder.child(name);
                if (f.exists()) loadBank(f);
            }
        }
    }

    /** Loads a bank from an internal (assets) path, or from the file system if no such asset exists. */
    @Override
    public void loadBank(String path) {
        if (Gdx.files != null && Gdx.files.internal(path).exists()) {
            loadBank(Gdx.files.internal(path));
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int r = FMOD_Studio_System_LoadBankFile(studio, path, FMOD_STUDIO_LOAD_BANK_NORMAL, stack.mallocPointer(1));
            onBankLoaded(path, r);
        }
    }

    /** Loads a bank from memory, so banks packed inside the game's jar work too. */
    public void loadBank(FileHandle file) {
        byte[] bytes = file.readBytes();
        ByteBuffer data = MemoryUtil.memAlignedAlloc(FMOD_STUDIO_LOAD_MEMORY_ALIGNMENT, bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(bytes).flip();
            // LOAD_MEMORY copies the data, so the buffer can be freed straight away
            int r = FMOD_Studio_System_LoadBankMemory(studio, data, FMOD_STUDIO_LOAD_MEMORY, FMOD_STUDIO_LOAD_BANK_NORMAL, stack.mallocPointer(1));
            onBankLoaded(file.path(), r);
        } finally {
            MemoryUtil.memAlignedFree(data);
        }
    }

    private void onBankLoaded(String path, int result) {
        if (result == FMOD_OK) banksLoaded++;
        else log("Could not load bank " + path + ": " + FMOD_ErrorString(result));
    }

    @Override
    public AudioEventInstance createInstance(String eventPath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            if (FMOD_Studio_System_GetEvent(studio, eventPath, pp) != FMOD_OK) {
                return fallback.createInstance(eventPath);
            }
            int r = FMOD_Studio_EventDescription_CreateInstance(pp.get(0), pp);
            if (r != FMOD_OK) {
                log("Could not create " + eventPath + ": " + FMOD_ErrorString(r));
                return fallback.createInstance(eventPath);
            }
            FmodInstance i = new FmodInstance(eventPath, pp.get(0));
            instances.add(i);
            return i;
        }
    }

    @Override
    public void setListener(float x, float y, float vx, float vy) {
        fallback.setListener(x, y, vx, vy);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FMOD_Studio_System_SetListenerAttributes(studio, 0, attributes(stack, x, y, vx, vy), null);
        }
    }

    @Override
    public void setGlobalParameter(String name, float value) {
        globals.put(name, value);
        fallback.setGlobalParameter(name, value);
        FMOD_Studio_System_SetParameterByName(studio, name, value, 0); // not every project has it
    }

    @Override
    public float getGlobalParameter(String name, float defaultValue) {
        return globals.get(name, defaultValue);
    }

    @Override
    public void update(float deltaTime) {
        for (int i = instances.size - 1; i >= 0; i--) {
            FmodInstance inst = instances.get(i);
            if (inst.update()) instances.removeIndex(i);
        }
        FMOD_Studio_System_Update(studio);
        fallback.update(deltaTime);
    }

    @Override
    public void dispose() {
        fallback.dispose();
        release();
    }

    private void release() {
        if (disposed) return;
        disposed = true;
        for (FmodInstance i : instances) i.freeFilter();
        instances.clear();
        FMOD_Studio_System_Release(studio); // frees every instance, bank and DSP
    }

    /** The FMOD equivalent of world (x, y) with velocity (vx, vy). */
    static FMOD_3D_ATTRIBUTES attributes(MemoryStack stack, float x, float y, float vx, float vy) {
        FMOD_3D_ATTRIBUTES a = FMOD_3D_ATTRIBUTES.calloc(stack);
        a.position$().set(x, 0f, y);
        a.velocity().set(vx, 0f, vy);
        a.forward().set(0f, 0f, 1f);
        a.up().set(0f, 1f, 0f);
        return a;
    }

    private static void check(int result, String what) {
        if (result != FMOD_OK) {
            String hint = result == FMOD_ERR_HEADER_MISMATCH ? " (install FMOD Engine 2.03.x to match LWJGL's bindings)" : "";
            throw new IllegalStateException("FMOD could not " + what + ": " + FMOD_ErrorString(result) + hint);
        }
    }

    private static void log(String message) {
        if (Gdx.app != null) Gdx.app.log("audio", message);
        else System.err.println("[audio] " + message);
    }

    /** One FMOD Studio {@code EventInstance}, with the low-pass the backend adds to it. */
    private class FmodInstance implements AudioEventInstance {
        private final String path;
        private final long handle;
        private final ObjectFloatMap<String> params = new ObjectFloatMap<>();
        private float x, y, vx, vy;
        private boolean started, released, stopped;
        /** The low-pass DSP, added once FMOD has created the event's channel group. */
        private long lowPass;
        private long channelGroup;
        private float appliedCutoff = -1f;

        FmodInstance(String path, long handle) {
            this.path = path;
            this.handle = handle;
        }

        @Override public String path() { return path; }

        @Override
        public void start() {
            started = true;
            stopped = false;
            FMOD_Studio_EventInstance_Start(handle);
        }

        @Override
        public void stop() {
            FMOD_Studio_EventInstance_Stop(handle, FMOD_STUDIO_STOP_ALLOWFADEOUT);
        }

        @Override
        public boolean isPlaying() {
            return started && !stopped && playbackState() != FMOD_STUDIO_PLAYBACK_STOPPED;
        }

        private int playbackState() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer state = stack.mallocInt(1);
                // A released instance's handle goes invalid once it has stopped
                if (FMOD_Studio_EventInstance_GetPlaybackState(handle, state) != FMOD_OK) return FMOD_STUDIO_PLAYBACK_STOPPED;
                return state.get(0);
            }
        }

        @Override
        public void release() {
            if (released) return;
            released = true;
            FMOD_Studio_EventInstance_Release(handle);
        }

        @Override
        public void setPosition(float x, float y) {
            this.x = x;
            this.y = y;
            apply3D();
        }

        @Override
        public void setVelocity(float vx, float vy) {
            this.vx = vx;
            this.vy = vy;
            apply3D();
        }

        private void apply3D() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FMOD_Studio_EventInstance_Set3DAttributes(handle, attributes(stack, x, y, vx, vy));
            }
        }

        @Override
        public void setParameter(String name, float value) {
            params.put(name, value);
            FMOD_Studio_EventInstance_SetParameterByName(handle, name, value, 0); // ignored if the event lacks it
            if (AudioParameters.TRANSMISSION.equals(name) || AudioParameters.PRESSURE.equals(name)) {
                float volume = Math.max(params.get(AudioParameters.TRANSMISSION, 1f), 0f)
                        * AcousticModel.pressureGain(params.get(AudioParameters.PRESSURE, 1f));
                FMOD_Studio_EventInstance_SetVolume(handle, volume);
            } else if (AudioParameters.OCCLUSION.equals(name)) {
                applyCutoff();
            }
        }

        @Override
        public float getParameter(String name, float defaultValue) {
            return params.get(name, defaultValue);
        }

        /** Per-frame upkeep. Returns true once the instance is finished with and can be dropped. */
        boolean update() {
            if (started && !stopped && playbackState() == FMOD_STUDIO_PLAYBACK_STOPPED) {
                stopped = true;
                freeFilter();
            }
            if (started && !stopped && lowPass == MemoryUtil.NULL) addFilter();
            return released && (stopped || !started);
        }

        /** Adds the low-pass once the event is playing; FMOD has no channel group for it before then. */
        private void addFilter() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer pp = stack.mallocPointer(1);
                if (FMOD_Studio_EventInstance_GetChannelGroup(handle, pp) != FMOD_OK) return;
                long group = pp.get(0);
                if (FMOD_System_CreateDSPByType(core, FMOD_DSP_TYPE_MULTIBAND_EQ, pp) != FMOD_OK) return;
                long dsp = pp.get(0);
                FMOD_DSP_SetParameterInt(dsp, FMOD_DSP_MULTIBAND_EQ_A_FILTER, FMOD_DSP_MULTIBAND_EQ_FILTER_LOWPASS_12DB);
                if (FMOD_ChannelGroup_AddDSP(group, FMOD_CHANNELCONTROL_DSP_TAIL, dsp) != FMOD_OK) {
                    FMOD_DSP_Release(dsp);
                    return;
                }
                channelGroup = group;
                lowPass = dsp;
                appliedCutoff = -1f;
                applyCutoff();
            }
        }

        private void applyCutoff() {
            if (lowPass == MemoryUtil.NULL) return;
            float cutoff = AcousticModel.cutoffHz(params.get(AudioParameters.OCCLUSION, 0f));
            if (cutoff == appliedCutoff) return;
            appliedCutoff = cutoff;
            FMOD_DSP_SetParameterFloat(lowPass, FMOD_DSP_MULTIBAND_EQ_A_FREQUENCY, cutoff);
        }

        void freeFilter() {
            if (lowPass == MemoryUtil.NULL) return;
            FMOD_ChannelGroup_RemoveDSP(channelGroup, lowPass); // fails harmlessly if FMOD already freed the group
            FMOD_DSP_Release(lowPass);
            lowPass = MemoryUtil.NULL;
            channelGroup = MemoryUtil.NULL;
        }
    }
}
