package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

/**
 * The GL state a render pass sets, in one place: blending, and the stencil a pass marks a shape
 * into and then keeps its drawing to. Each pass that starts one ends it ({@link #endBlend},
 * {@link #endStencil}), leaving the state as the next pass expects to find it.
 *
 * A stencil pass runs: {@link #markStencil} (or {@link #clearStencil} then {@link #stencilWrite}),
 * draw the shape, {@link #keepWhere}, draw what it clips, {@link #endStencil}.
 */
public final class GlPass {

    private GlPass() {}

    /** Turns blending on with the usual alpha blend: source by its alpha over what is there. */
    public static void alphaBlend() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Turns blending off at the end of a pass that turned it on. */
    public static void endBlend() {
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Clears the stencil to 0 and turns the stencil test on. */
    public static void clearStencil() {
        GL20 gl = Gdx.gl;
        gl.glClearStencil(0);
        gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
        gl.glEnable(GL20.GL_STENCIL_TEST);
    }

    /**
     * What is drawn next goes into the stencil and not the color: where it passes {@code func}
     * against {@code ref}, the stencil takes {@code op} ({@code GL_REPLACE} writes {@code ref}).
     */
    public static void stencilWrite(int func, int ref, int op) {
        GL20 gl = Gdx.gl;
        gl.glColorMask(false, false, false, false);
        gl.glStencilFunc(func, ref, 0xff);
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, op);
    }

    /** Clears the stencil and starts marking: what is drawn next writes {@code value} into it. */
    public static void markStencil(int value) {
        clearStencil();
        stencilWrite(GL20.GL_ALWAYS, value, GL20.GL_REPLACE);
    }

    /** Stops marking: what is drawn next shows only where the stencil passes {@code func} against {@code ref}. */
    public static void keepWhere(int func, int ref) {
        GL20 gl = Gdx.gl;
        gl.glColorMask(true, true, true, true);
        gl.glStencilFunc(func, ref, 0xff);
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
    }

    /** Turns the stencil test off at the end of a stencil pass. */
    public static void endStencil() {
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
    }
}
