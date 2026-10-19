package com.ringsofiron.benchmarks;

import com.ringsofiron.coreengine.TickProfiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collects what the {@link TickProfiler} hooks report, frame by frame: each section's time in a
 * frame is the sum of every time it ran in that frame. {@link #table} prints mean, percentiles and
 * worst frame per section, slowest first.
 */
public final class TickStats implements TickProfiler {
    /** Section for the whole frame, timed around {@code GameWorld.update}. */
    public static final String FRAME = "frame total";

    private final Map<String, long[]> current = new LinkedHashMap<>();
    private final Map<String, List<Long>> frames = new LinkedHashMap<>();
    private int frameCount;

    @Override
    public void record(String section, long nanos) {
        current.computeIfAbsent(section, k -> new long[1])[0] += nanos;
    }

    /** Closes the frame: every section seen so far gets this frame's total (zero if it did not run). */
    public void endFrame() {
        for (String s : current.keySet()) {
            frames.computeIfAbsent(s, k -> {
                List<Long> l = new ArrayList<>();
                for (int i = 0; i < frameCount; i++) l.add(0L);
                return l;
            });
        }
        for (Map.Entry<String, List<Long>> e : frames.entrySet()) {
            long[] t = current.get(e.getKey());
            e.getValue().add(t == null ? 0L : t[0]);
        }
        current.clear();
        frameCount++;
    }

    public void reset() {
        current.clear();
        frames.clear();
        frameCount = 0;
    }

    public int frames() {
        return frameCount;
    }

    /** Mean time of {@code section} per frame, milliseconds; 0 if it never ran. */
    public double meanMs(String section) {
        List<Long> l = frames.get(section);
        if (l == null || l.isEmpty()) return 0;
        long sum = 0;
        for (long v : l) sum += v;
        return sum / 1e6 / l.size();
    }

    /** The {@code p}-th percentile (0 to 100) of {@code section}'s per-frame time, milliseconds. */
    public double percentileMs(String section, double p) {
        List<Long> l = frames.get(section);
        if (l == null || l.isEmpty()) return 0;
        long[] sorted = l.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        int i = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, i))] / 1e6;
    }

    /**
     * A Markdown table of every section whose mean is at least {@code minMeanMs} or whose worst frame
     * is at least ten times that, slowest mean first.
     */
    public String table(double minMeanMs) {
        List<String> sections = new ArrayList<>(frames.keySet());
        sections.sort((a, b) -> Double.compare(meanMs(b), meanMs(a)));
        StringBuilder out = new StringBuilder();
        out.append("| Section | Mean ms | p50 | p99 | Max |\n|---|---:|---:|---:|---:|\n");
        for (String s : sections) {
            if (meanMs(s) < minMeanMs && percentileMs(s, 100) < minMeanMs * 10) continue;
            out.append(String.format(Locale.ROOT, "| %s | %.3f | %.3f | %.3f | %.3f |%n", s, meanMs(s),
                    percentileMs(s, 50), percentileMs(s, 99), percentileMs(s, 100)));
        }
        return out.toString();
    }
}
