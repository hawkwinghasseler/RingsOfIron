package com.ringsofiron.uihud.variants.nasapunk.screens;

/**
 * A line of a page outside its tables: a label and its value (CREDITS 3 900 CR), a part's head
 * between rules (ROUTE), or a line of text with a dim note at its end.
 */
public final class Line {
    public enum Kind { PAIR, HEAD, TEXT }

    public Kind kind = Kind.PAIR;
    public String label = "", value = "", unit = "", note = "";
    public Cell.Tone tone = Cell.Tone.VALUE;
    /** A share from 0 to 1 drawn as a bar under the value, or NaN for none. */
    public float bar = Float.NaN;

    public static Line pair(String label, String value, String unit, Cell.Tone tone) {
        Line l = new Line();
        l.kind = Kind.PAIR;
        l.label = label == null ? "" : label;
        l.value = value == null ? "" : value;
        l.unit = unit == null ? "" : unit;
        l.tone = tone == null ? Cell.Tone.VALUE : tone;
        return l;
    }

    public static Line head(String title) {
        Line l = new Line();
        l.kind = Kind.HEAD;
        l.label = title == null ? "" : title;
        return l;
    }

    public static Line text(String text, Cell.Tone tone) {
        Line l = new Line();
        l.kind = Kind.TEXT;
        l.value = text == null ? "" : text;
        l.tone = tone == null ? Cell.Tone.BODY : tone;
        return l;
    }

    public Line note(String note) {
        this.note = note == null ? "" : note;
        return this;
    }

    public Line bar(float share) {
        this.bar = share;
        return this;
    }
}
