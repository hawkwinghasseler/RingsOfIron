package com.ringsofiron.audiospatial.backend;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class WavClipTest {

    /** A 16-bit PCM WAV file holding interleaved {@code samples}. */
    static byte[] wav16(short[] samples, int channels, int rate) {
        return wav(1, 16, channels, rate, samples.length * 2, b -> { for (short s : samples) b.putShort(s); });
    }

    private static byte[] wav(int format, int bits, int channels, int rate, int dataBytes, java.util.function.Consumer<ByteBuffer> data) {
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(36 + dataBytes).putInt(0x45564157);
        b.putInt(0x20746d66).putInt(16).putShort((short) format).putShort((short) channels).putInt(rate)
                .putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8)).putShort((short) bits);
        b.putInt(0x61746164).putInt(dataBytes);
        data.accept(b);
        return b.array();
    }

    @Test
    void readsMono16() {
        WavClip c = WavClip.parse(wav16(new short[]{0, 1000, -1000, 32767}, 1, 22050));
        assertArrayEquals(new short[]{0, 1000, -1000, 32767}, c.samples);
        assertEquals(22050, c.sampleRate);
        assertEquals(4f / 22050, c.duration(), 1e-7f);
    }

    @Test
    void mixesStereoDownToMono() {
        WavClip c = WavClip.parse(wav16(new short[]{1000, 3000, -2000, 0}, 2, 44100));
        assertArrayEquals(new short[]{2000, -1000}, c.samples);
    }

    @Test
    void reads8Bit() {
        byte[] f = wav(1, 8, 1, 8000, 3, b -> b.put((byte) 128).put((byte) 255).put((byte) 0));
        assertArrayEquals(new short[]{0, 127 << 8, -128 << 8}, WavClip.parse(f).samples);
    }

    @Test
    void skipsUnknownChunks() {
        byte[] plain = wav16(new short[]{5, 6}, 1, 44100);
        // Insert an odd-sized "LIST" chunk (padded to even) between "fmt " and "data"
        ByteBuffer b = ByteBuffer.allocate(plain.length + 12).order(ByteOrder.LITTLE_ENDIAN);
        b.put(plain, 0, 36).putInt(0x5453494c).putInt(3).put(new byte[4]).put(plain, 36, plain.length - 36);
        assertArrayEquals(new short[]{5, 6}, WavClip.parse(b.array()).samples);
    }

    @Test
    void rejectsWhatItCannotPlay() {
        assertThrows(IllegalArgumentException.class, () -> WavClip.parse(new byte[]{1, 2, 3}));
        byte[] floats = wav(3, 32, 1, 44100, 4, b -> b.putFloat(0.5f));
        assertThrows(IllegalArgumentException.class, () -> WavClip.parse(floats));
    }
}
