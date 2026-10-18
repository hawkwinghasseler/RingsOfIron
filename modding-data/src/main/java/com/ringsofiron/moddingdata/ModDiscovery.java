package com.ringsofiron.moddingdata;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds mods in the mods folder and puts them in load order: every mod after the mods it
 * {@code depends} on or names in {@code load_after}, ties broken by id so the order is
 * stable. Mods with a missing dependency or in a dependency cycle are left out and
 * reported.
 */
public class ModDiscovery {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final LoadReport report;

    public ModDiscovery(LoadReport report) {
        this.report = report;
    }

    public List<ModManifest> discover(Path modsDir) {
        if (!Files.isDirectory(modsDir)) return new ArrayList<>();
        List<Path> folders;
        try (Stream<Path> list = Files.list(modsDir)) {
            folders = list.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            report.error("mods", "Cannot list " + modsDir + ": " + e.getMessage());
            return new ArrayList<>();
        }

        Map<String, ModManifest> byId = new TreeMap<>();
        for (Path folder : folders) {
            ModManifest manifest = readManifest(folder);
            if (manifest == null) continue;
            if (!manifest.enabled) {
                report.info(manifest.id, "Disabled in mod.toml; skipped");
                continue;
            }
            if (byId.containsKey(manifest.id)) {
                report.error(manifest.id, "Two mods use this id (" + byId.get(manifest.id).directory.getFileName()
                        + " and " + folder.getFileName() + "); keeping the first");
                continue;
            }
            byId.put(manifest.id, manifest);
        }
        return order(byId);
    }

    ModManifest readManifest(Path folder) {
        Path file = folder.resolve("mod.toml");
        ModManifest manifest;
        if (Files.isRegularFile(file)) {
            try {
                Map<String, Object> tree = DataFileParser.parse(file);
                manifest = GSON.fromJson(GSON.toJsonTree(tree), ModManifest.class);
            } catch (IOException | RuntimeException e) {
                report.error(folder.getFileName().toString(), "Bad mod.toml: " + e.getMessage());
                return null;
            }
        } else {
            manifest = new ModManifest();
        }
        if (manifest.id == null || manifest.id.isEmpty()) manifest.id = folder.getFileName().toString();
        if (manifest.name == null) manifest.name = manifest.id;
        if (manifest.depends == null) manifest.depends = new ArrayList<>();
        if (manifest.loadAfter == null) manifest.loadAfter = new ArrayList<>();
        manifest.directory = folder;
        return manifest;
    }

    private List<ModManifest> order(Map<String, ModManifest> byId) {
        // Drop mods whose hard dependencies are missing, repeatedly, since dropping one can orphan others.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ModManifest m : new ArrayList<>(byId.values())) {
                for (String dep : m.depends) {
                    if (!byId.containsKey(dep)) {
                        report.error(m.id, "Needs mod '" + dep + "', which is missing or failed; skipped");
                        byId.remove(m.id);
                        changed = true;
                        break;
                    }
                }
            }
        }

        Map<String, Set<String>> before = new HashMap<>();
        for (ModManifest m : byId.values()) {
            Set<String> preds = new HashSet<>(m.depends);
            for (String soft : m.loadAfter) if (byId.containsKey(soft)) preds.add(soft);
            before.put(m.id, preds);
        }

        List<ModManifest> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        while (placed.size() < byId.size()) {
            ModManifest next = null;
            for (ModManifest m : byId.values()) { // TreeMap: alphabetical
                if (!placed.contains(m.id) && placed.containsAll(before.get(m.id))) {
                    next = m;
                    break;
                }
            }
            if (next == null) {
                List<String> stuck = new ArrayList<>();
                for (String id : byId.keySet()) if (!placed.contains(id)) stuck.add(id);
                report.error("mods", "Dependency cycle between " + stuck + "; these mods were skipped");
                break;
            }
            ordered.add(next);
            placed.add(next.id);
        }
        for (ModManifest m : ordered) report.info(m.id, "Found mod " + m.name + " " + m.version);
        return ordered;
    }
}
