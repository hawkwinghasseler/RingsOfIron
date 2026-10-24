package com.hawkwing.RingsOfIron.game;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a playtest benchmark measured against its balance targets. Each {@link #range}, {@link #atMost},
 * {@link #atLeast} or {@link #expect} records one target with what the game gave, on or off target,
 * without stopping the run, so one benchmark reports every number it reaches. {@link #finish} prints
 * the table, writes it to {@code build/playtest/<name>.md} and fails the benchmark if anything was off
 * target, so the test report shows which.
 *
 * <p>Off target is not a bug: the targets were tuned on the scripted career bot, and a human tester
 * weighs them against how the same moment played by hand ({@code documentation/playtest/}).
 */
final class BalanceReport {
    private final String name;
    private final List<String> rows = new ArrayList<>();
    private final List<String> off = new ArrayList<>();

    BalanceReport(String name) {
        this.name = name;
    }

    /** {@code measured} from {@code lo} to {@code hi}, inclusive. */
    BalanceReport range(String target, double measured, double lo, double hi) {
        return row(target, fmt(measured), fmt(lo) + " to " + fmt(hi), measured >= lo && measured <= hi);
    }

    BalanceReport atMost(String target, double measured, double most) {
        return row(target, fmt(measured), "at most " + fmt(most), measured <= most);
    }

    BalanceReport atLeast(String target, double measured, double least) {
        return row(target, fmt(measured), "at least " + fmt(least), measured >= least);
    }

    /** A target that is a yes or no, with what was seen. */
    BalanceReport expect(String target, boolean met, String seen) {
        return row(target, seen, "yes", met);
    }

    /** A number worth seeing beside the targets, with no target of its own. */
    BalanceReport note(String what, String seen) {
        rows.add(String.format(Locale.ROOT, "| %s | %s | (no target) | - |", what, seen));
        return this;
    }

    private BalanceReport row(String target, String measured, String wanted, boolean on) {
        rows.add(String.format(Locale.ROOT, "| %s | %s | %s | %s |", target, measured, wanted, on ? "on target" : "OFF TARGET"));
        if (!on) off.add(target + ": " + measured + ", wanted " + wanted);
        return this;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e12 ? String.format(Locale.ROOT, "%,.0f", v) : String.format(Locale.ROOT, "%,.2f", v);
    }

    /** Prints and writes the table; fails, listing them, if any target was missed. */
    void finish() {
        List<String> out = new ArrayList<>();
        out.add("## " + name);
        out.add("");
        out.add("| Target | Measured | Wanted | |");
        out.add("|---|---|---|---|");
        out.addAll(rows);
        System.out.println(String.join("\n", out));
        try {
            Path file = Paths.get(System.getProperty("user.dir")).resolve("build").resolve("playtest")
                    .resolve(name.replaceAll("[^A-Za-z0-9]+", "-").toLowerCase(Locale.ROOT) + ".md");
            Files.createDirectories(file.getParent());
            Files.write(file, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!off.isEmpty()) throw new AssertionError(name + ": off target, for a tester to weigh:\n  " + String.join("\n  ", off));
    }
}
