package com.hawkwing.RingsOfIron;

import java.io.InputStream;
import java.util.Properties;

/**
 * The build's version, read from the version.properties core's Gradle build writes: the project
 * version and the short git commit, when the build ran in a checkout. Shown on the main menu.
 */
public final class BuildInfo {
    private static final String LABEL = load("version.properties");

    private BuildInfo() {
    }

    /** "v1.0.0 (a1b2c3d)", "v1.0.0" without a commit, or "dev" when the file is missing. */
    public static String label() {
        return LABEL;
    }

    static String load(String resource) {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream(resource)) {
            if (in == null) return "dev";
            p.load(in);
        } catch (Exception e) {
            return "dev";
        }
        return format(p.getProperty("version", ""), p.getProperty("commit", ""));
    }

    static String format(String version, String commit) {
        version = version == null ? "" : version.trim();
        commit = commit == null ? "" : commit.trim();
        if (version.isEmpty()) return commit.isEmpty() ? "dev" : "dev (" + commit + ")";
        return commit.isEmpty() ? "v" + version : "v" + version + " (" + commit + ")";
    }
}
