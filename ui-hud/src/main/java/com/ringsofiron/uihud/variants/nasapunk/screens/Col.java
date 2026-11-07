package com.ringsofiron.uihud.variants.nasapunk.screens;

/** A column of a page's table: its head, its width in UI units (0 takes what is left), and its side. */
public final class Col {
    public final String head;
    public final float width;
    /** Set to the column's right edge, as figures are; else from its left. */
    public final boolean end;

    public Col(String head, float width, boolean end) {
        this.head = head;
        this.width = width;
        this.end = end;
    }

    public static Col start(String head, float width) {
        return new Col(head, width, false);
    }

    public static Col end(String head, float width) {
        return new Col(head, width, true);
    }
}
