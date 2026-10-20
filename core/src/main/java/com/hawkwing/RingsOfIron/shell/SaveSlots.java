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
    /** Autosaves rotate through this many slots, the oldest overwritten. */
    public static final int AUTOSAVES = 3;
    public static final String AUTOSAVE = "autosave";

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

    /** Autosave slot {@code n}, 1 to {@link #AUTOSAVES}. */
    public FileHandle autosave(int n) {
        return dir.child(AUTOSAVE + "-" + n + ".json");
    }

    public static String autosaveName(int n) {
        return "Autosave " + n;
    }

    /** The slot the next autosave goes to: an empty one, else the oldest. */
    public int nextAutosave() {
        int oldest = 1;
        long oldestAt = Long.MAX_VALUE;
        for (int n = 1; n <= AUTOSAVES; n++) {
            FileHandle f = autosave(n);
            if (!f.exists()) return n;
            long at = SaveSummary.readFor(f, autosaveName(n)).savedAt;
            if (at < oldestAt) {
                oldestAt = at;
                oldest = n;
            }
        }
        return oldest;
    }

    /** The newest autosave, or null when there is none. */
    public SaveSummary latestAutosave() {
        SaveSummary best = null;
        for (int n = 1; n <= AUTOSAVES; n++) {
            FileHandle f = autosave(n);
            if (!f.exists()) continue;
            SaveSummary s = SaveSummary.readFor(f, autosaveName(n));
            if (best == null || s.savedAt > best.savedAt) best = s;
        }
        return best;
    }

    /** The file a save named {@code name} goes to: the name with anything but letters, digits, - and _ made _. */
    public FileHandle fileFor(String name) {
        String base = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_-]+", "_");
        if (base.isEmpty() || base.equals("_")) base = "save";
        if (base.equals(QUICK_SAVE) && !QUICK_SAVE_NAME.equals(name)) base = "save_" + base;
        if (base.startsWith(AUTOSAVE + "-")) base = "save_" + base;
        return dir.child(base + ".json");
    }

    /** Every save, newest first. */
    public Array<SaveSummary> list() {
        Array<SaveSummary> out = new Array<>();
        if (!dir.isDirectory()) return out;
        for (FileHandle f : dir.list(".json")) {
            String n = f.name();
            if (n.endsWith(SaveSummary.META_SUFFIX) || n.equals(GameSettings.FILE_NAME)) continue;
            String base = f.nameWithoutExtension();
            String fallback = base.equals(QUICK_SAVE) ? QUICK_SAVE_NAME
                    : base.startsWith(AUTOSAVE + "-") ? "Autosave " + base.substring(AUTOSAVE.length() + 1) : base;
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

    /**
     * The list of saves {@link #importFrom} copied in, kept beside them so a later copy knows which
     * are its own: one {@code <file> <crc32 in hex>} line each.
     */
    public static final String CAREER_LIST = "career-saves.txt";

    /** {@link #importFrom}'s result, each list of file names without {@code .json}. */
    public static final class Imported {
        /** Saves that weren't here before. */
        public final Array<String> copied = new Array<>();
        /** Saves an earlier copy put here, untouched since, now replaced by the new folder's. */
        public final Array<String> replaced = new Array<>();
        /** Saves already here that no copy made, or that the player has saved over since: left alone. */
        public final Array<String> kept = new Array<>();
    }

    /**
     * Copies the saves in {@code from} (the career test's {@code core/build/playtest-saves/seed-2026},
     * say) into this folder, each with its summary, so the load list shows them beside the player's own.
     * Each one copied is noted in {@link #CAREER_LIST} with its checksum. A later copy replaces those
     * files, and only those, so a new round's saves take the place of the last round's. A save no copy
     * made, or one the player has saved over since (its checksum moved), is left alone, its summary too,
     * and so is any settings file.
     */
    public Imported importFrom(FileHandle from) {
        Imported out = new Imported();
        if (!from.isDirectory()) return out;
        dir.mkdirs();
        java.util.Map<String, String> ours = readCareerList();
        FileHandle[] files = from.list(".json");
        java.util.Arrays.sort(files, (a, b) -> a.name().compareTo(b.name()));
        for (FileHandle f : files) {
            String n = f.name();
            if (n.endsWith(SaveSummary.META_SUFFIX) || n.equals(GameSettings.FILE_NAME)) continue;
            FileHandle to = dir.child(n);
            boolean replacing = false;
            if (to.exists()) {
                if (!checksum(to).equals(ours.get(n))) {
                    ours.remove(n);
                    out.kept.add(f.nameWithoutExtension());
                    continue;
                }
                replacing = true;
            }
            f.copyTo(to);
            FileHandle meta = SaveSummary.metaFor(f);
            if (meta.exists()) meta.copyTo(SaveSummary.metaFor(to));
            else SaveSummary.metaFor(to).delete();
            ours.put(n, checksum(to));
            (replacing ? out.replaced : out.copied).add(f.nameWithoutExtension());
        }
        writeCareerList(ours);
        return out;
    }

    private java.util.Map<String, String> readCareerList() {
        java.util.Map<String, String> ours = new java.util.TreeMap<>();
        FileHandle list = dir.child(CAREER_LIST);
        if (!list.exists()) return ours;
        for (String line : list.readString("UTF-8").split("\\R")) {
            int at = line.lastIndexOf(' ');
            if (at > 0) ours.put(line.substring(0, at), line.substring(at + 1).trim());
        }
        return ours;
    }

    private void writeCareerList(java.util.Map<String, String> ours) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : ours.entrySet()) {
            // Gone from this folder (the player deleted it): nothing left to replace
            if (dir.child(e.getKey()).exists()) sb.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        }
        dir.child(CAREER_LIST).writeString(sb.toString(), false, "UTF-8");
    }

    private static String checksum(FileHandle f) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(f.readBytes());
        return Long.toHexString(crc.getValue());
    }

    /** "Save N" for the first N with no such save yet. */
    public String nextName() {
        for (int n = 1; ; n++) {
            if (!fileFor("Save " + n).exists()) return "Save " + n;
        }
    }
}
