package com.ringsofiron.uihud.variants.nasapunk.screens;

/** One cell of a page's table: its words, a unit set dim beside them, and how they are set. */
public final class Cell {
    /**
     * How a cell is set: a row's name, a value, body text, a dim label, or a value in the colour of
     * what it says (cyan in hand or on order, green working, amber short or late, red lost, magenta
     * the navigation target), or a key printed on its cap.
     */
    public enum Tone { NAME, VALUE, BODY, LABEL, CYAN, GREEN, AMBER, RED, MAGENTA, KEY }

    public String text = "", unit = "";
    public Tone tone = Tone.VALUE;

    public Cell set(String text, String unit, Tone tone) {
        this.text = text == null ? "" : text;
        this.unit = unit == null ? "" : unit;
        this.tone = tone == null ? Tone.VALUE : tone;
        return this;
    }

    @Override
    public String toString() {
        return unit.isEmpty() ? text : text + " " + unit;
    }
}
