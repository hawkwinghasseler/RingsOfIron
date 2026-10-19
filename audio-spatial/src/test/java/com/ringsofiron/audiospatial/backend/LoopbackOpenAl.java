package com.ringsofiron.audiospatial.backend;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryUtil;
import org.opentest4j.TestAbortedException;

import java.nio.ByteBuffer;

import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.SOFTHRTF.ALC_HRTF_SOFT;
import static org.lwjgl.openal.SOFTLoopback.*;

/**
 * An OpenAL Soft context on a loopback device: no sound card, and the mixed output is read back
 * with {@link #render}. Made current the way libGDX's desktop backend makes its own context
 * current, so the backend under test finds it the same way. Skips the test where OpenAL Soft
 * cannot load.
 */
final class LoopbackOpenAl implements AutoCloseable {
    static final int RATE = 44100;
    private final long device;
    private final long context;

    private LoopbackOpenAl(long device, long context) {
        this.device = device;
        this.context = context;
    }

    static LoopbackOpenAl open() {
        try {
            try {
                ALC.create();
            } catch (IllegalStateException alreadyCreated) {
                // an earlier test loaded it
            }
            ALCCapabilities base = ALC.createCapabilities(MemoryUtil.NULL);
            if (!base.ALC_SOFT_loopback) throw new TestAbortedException("OpenAL has no loopback device");
            long device = alcLoopbackOpenDeviceSOFT((ByteBuffer) null);
            int[] attributes = {
                    ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT,
                    ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT,
                    ALC_FREQUENCY, RATE,
                    ALC_HRTF_SOFT, ALC_FALSE, // plain stereo panning, so left/right levels are predictable
                    0};
            long context = alcCreateContext(device, attributes);
            if (context == MemoryUtil.NULL) {
                alcCloseDevice(device);
                throw new TestAbortedException("OpenAL could not create a loopback context");
            }
            alcMakeContextCurrent(context);
            AL.createCapabilities(ALC.createCapabilities(device));
            return new LoopbackOpenAl(device, context);
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
            throw new TestAbortedException("OpenAL Soft is not available here: " + e);
        }
    }

    /** Mixes the next {@code frames} stereo frames, interleaved left, right. */
    float[] render(int frames) {
        float[] out = new float[frames * 2];
        alcRenderSamplesSOFT(device, out, frames);
        return out;
    }

    @Override
    public void close() {
        alcMakeContextCurrent(MemoryUtil.NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }
}
