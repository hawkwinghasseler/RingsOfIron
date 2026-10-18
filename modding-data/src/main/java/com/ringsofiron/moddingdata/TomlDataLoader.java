package com.ringsofiron.moddingdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads every {@code .toml} and {@code .json} file under a data directory into a
 * {@link DefRegistry}. Files are read in path order so results never depend on the
 * file system's listing order.
 */
public class TomlDataLoader {

    private final DefRegistry registry;
    private final LoadReport report;

    public TomlDataLoader(DefRegistry registry, LoadReport report) {
        this.registry = registry;
        this.report = report;
    }

    /**
     * @param dataDir    directory to scan recursively; a missing directory loads nothing
     * @param sourceName credited on every definition, e.g. "base" or the mod id
     * @return number of files that loaded
     * @throws IllegalStateException if {@code failFast} and any file is invalid
     */
    public int loadDirectory(Path dataDir, String sourceName, boolean failFast) {
        if (!Files.isDirectory(dataDir)) return 0;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dataDir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(DataFileParser::isDataFile)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            fail(sourceName, "Cannot list " + dataDir + ": " + e.getMessage(), failFast);
            return 0;
        }
        int loaded = 0;
        for (Path file : files) {
            if (loadFile(file, sourceName + ":" + dataDir.relativize(file).toString().replace('\\', '/'), failFast)) {
                loaded++;
            }
        }
        report.info(sourceName, "Loaded " + loaded + " data file(s) from " + dataDir);
        return loaded;
    }

    public boolean loadFile(Path file, String sourceLabel, boolean failFast) {
        try {
            Map<String, Object> tree = DataFileParser.parse(file);
            registry.mergeFile(tree, sourceLabel, report);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            fail(sourceLabel, e.getMessage(), failFast);
            return false;
        }
    }

    private void fail(String source, String message, boolean failFast) {
        report.error(source, message);
        if (failFast) throw new IllegalStateException(source + ": " + message);
    }
}
