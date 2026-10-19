package com.ringsofiron.audiospatial.backend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A recorded sound read from a PCM {@code .wav} file, mixed down to mono 16-bit so OpenAL can
 * place it in space (OpenAL only spatializes mono sources).
 */
public class WavClip {
    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_EXTENSIBLE = 0xFFFE;

    public final short[] samples;
    public final int sampleRate;

    public WavClip(short[] samples, int sampleRate) {
        this.samples = samples;
        this.sampleRate = sampleRate;
    }

    /** Length in seconds. */
    public float duration() {
        return samples.length / (float) sampleRate;
    }

    /**
     * Reads an 8- or 16-bit PCM WAV file with any number of channels.
     *
     * @throws IllegalArgumentException if the data is not a WAV file in a supported format
     */
    public static WavClip parse(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) { // "RIFF", "WAVE"
            throw new IllegalArgumentException("Not a WAV file");
        }
        int format = -1, channels = 0, rate = 0, bits = 0;
        int pos = 12;
        while (pos + 8 <= data.length) {
            int id = b.getInt(pos);
            int size = b.getInt(pos + 4);
            int body = pos + 8;
            if (size < 0 || body + size > data.length) size = data.length - body; // tolerate a truncated last chunk
            if (id == 0x20746d66) { // "fmt "
                format = b.getShort(body) & 0xFFFF;
                channels = b.getShort(body + 2) & 0xFFFF;
                rate = b.getInt(body + 4);
                bits = b.getShort(body + 14) & 0xFFFF;
                if (format == FORMAT_EXTENSIBLE && size >= 26) format = b.getShort(body + 24) & 0xFFFF;
            } else if (id == 0x61746164) { // "data"
                if (format != FORMAT_PCM || (bits != 8 && bits != 16) || channels < 1 || rate <= 0) {
                    throw new IllegalArgumentException("Unsupported WAV format " + format + ", " + bits + "-bit, "
                            + channels + " channels; use 8- or 16-bit PCM");
                }
                return new WavClip(mixDown(b, body, size, channels, bits), rate);
            }
            pos = body + size + (size & 1); // chunks are padded to even sizes
        }
        throw new IllegalArgumentException("WAV file has no data");
    }

    private static short[] mixDown(ByteBuffer b, int start, int size, int channels, int bits) {
        int bytes = bits / 8;
        int frames = size / (bytes * channels);
        short[] out = new short[frames];
        int p = start;
        for (int f = 0; f < frames; f++) {
            int sum = 0;
            for (int c = 0; c < channels; c++, p += bytes) {
                sum += bits == 16 ? b.getShort(p) : ((b.get(p) & 0xFF) - 128) << 8;
            }
            out[f] = (short) (sum / channels);
        }
        return out;
    }
}
