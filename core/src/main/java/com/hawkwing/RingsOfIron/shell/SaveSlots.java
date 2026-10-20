package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

/**
 * Named saves in one folder ({@code saves/} beside the game's working directory): each is
 * {@code <file>.json}, core-engine's save, with its {@link SaveSummary} beside it. The F5 quick save
 * is the slot {@link #QUICK_SAVE} in the same folder, so it shows in the load list too.
 */
public class SaveSlots {
    public static final String QUICK_SAVE = "quicksave";
    public static final String QUICK_SAVE_NAME = "Quick save";

    private final FileHandle dir;

    public SaveSlots(FileHandle dir) {
        this.dir = dir;
    }

    public FileHandle dir() {
        return dir;
    }

    public FileHandle quickSave() {
        return dir.child(QUICK_SAVE + ".json");
    }

    /** The file a save named {@code name} goes to: the name with anything but letters, digits, - and _ made _. */
    public FileHandle fileFor(String name) {
        String base = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_-]+", "_");
        if (base.isEmpty() || base.equals("_")) base = "save";
        if (base.equals(QUICK_SAVE) && !QUICK_SAVE_NAME.equals(name)) base = "save_" + base;
        return dir.child(base + ".json");
    }

    /** Every save, newest first. */
    public Array<SaveSummary> list() {
        Array<SaveSummary> out = new Array<>();
        if (!dir.isDirectory()) return out;
        for (FileHandle f : dir.list(".json")) {
            String n = f.name();
            if (n.endsWith(SaveSummary.META_SUFFIX) || n.equals(GameSettings.FILE_NAME)) continue;
            String fallback = f.nameWithoutExtension().equals(QUICK_SAVE) ? QUICK_SAVE_NAME : f.nameWithoutExtension();
            out.add(SaveSummary.readFor(f, fallback));
        }
        out.sort((a, b) -> Long.compare(b.savedAt, a.savedAt));
        return out;
    }

    /** The newest save, for Continue, or null when there is none. */
    public SaveSummary latest() {
        Array<SaveSummary> all = list();
        return all.size == 0 ? null : all.first();
    }

    public void delete(SaveSummary save) {
        if (save.file == null) return;
        save.file.delete();
        SaveSummary.metaFor(save.file).delete();
    }

    /** "Save N" for the first N with no such save yet. */
    public String nextName() {
        for (int n = 1; ; n++) {
            if (!fileFor("Save " + n).exists()) return "Save " + n;
        }
    }
}
