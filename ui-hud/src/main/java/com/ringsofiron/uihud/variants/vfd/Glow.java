package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;

import java.nio.IntBuffer;

/**
 * The glow of lit phosphor (style-vfd.md, section 6): a lit layer is drawn into a buffer of its own,
 * blurred at half and quarter size, and added to the screen with the sharp layer on top, as the
 * mockups' renderer adds its three blurs. The control grid, a fine mesh in front of the phosphor seen
 * only where it glows, is laid over the sharp layer as it is added.
 *
 * <p>Where the shaders do not compile, {@link #available} is false and the lit layers are drawn
 * straight onto the screen, sharp and without glow.
 */
final class Glow implements Disposable {
    private static final String VERTEX = "attribute vec4 a_position;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "varying vec2 v_uv;\n"
            + "void main() {\n"
            + "  v_uv = a_texCoord0;\n"
            + "  gl_Position = a_position;\n"
            + "}\n";
    private static final String HEADER = "#ifdef GL_ES\nprecision mediump float;\n#endif\n";
    private static final String COPY = HEADER
            + "uniform sampler2D u_tex;\n"
            + "varying vec2 v_uv;\n"
            + "void main() { gl_FragColor = texture2D(u_tex, v_uv); }\n";
    /** Thirteen taps along u_step, Gaussian with u_sigma in taps; the weights are normalised as they go. */
    private static final String BLUR = HEADER
            + "uniform sampler2D u_tex;\n"
            + "uniform vec2 u_step;\n"
            + "uniform float u_sigma;\n"
            + "varying vec2 v_uv;\n"
            + "void main() {\n"
            + "  vec4 sum = texture2D(u_tex, v_uv);\n"
            + "  float total = 1.0;\n"
            + "  for (int i = 1; i <= 6; i++) {\n"
            + "    float x = float(i);\n"
            + "    float w = exp(-x * x / (2.0 * u_sigma * u_sigma));\n"
            + "    sum += (texture2D(u_tex, v_uv + u_step * x) + texture2D(u_tex, v_uv - u_step * x)) * w;\n"
            + "    total += 2.0 * w;\n"
            + "  }\n"
            + "  gl_FragColor = sum / total;\n"
            + "}\n";
    private static final String COMPOSITE = HEADER
            + "uniform sampler2D u_sharp;\n"
            + "uniform sampler2D u_soft;\n"
            + "uniform sampler2D u_mid;\n"
            + "uniform sampler2D u_wide;\n"
            + "uniform vec3 u_k;\n"
            + "uniform float u_cell;\n"
            + "uniform float u_mesh;\n"
            + "varying vec2 v_uv;\n"
            + "void main() {\n"
            + "  vec2 at = mod(gl_FragCoord.xy, u_cell);\n"
            + "  float grid = (at.x < 1.0 || at.y < 1.0) ? 1.0 - u_mesh : 1.0;\n"
            + "  vec3 c = texture2D(u_sharp, v_uv).rgb * grid\n"
            + "      + texture2D(u_soft, v_uv).rgb * u_k.x\n"
            + "      + texture2D(u_mid, v_uv).rgb * u_k.y\n"
            + "      + texture2D(u_wide, v_uv).rgb * u_k.z;\n"
            + "  gl_FragColor = vec4(c, 0.0);\n"
            + "}\n";

    private ShaderProgram copy, blur, composite;
    private Mesh quad;
    private final boolean available;
    private FrameBuffer sharp, soft, midA, midB, wideA, wideB;
    private int width, height;
    private final IntBuffer viewport = BufferUtils.newIntBuffer(16);
    private final IntBuffer binding = BufferUtils.newIntBuffer(16);
    /** The viewport and framebuffer drawn to before the capture, put back after it: the screen, or a buffer the HUD is drawn into. */
    private int vx, vy, vw, vh, previous;
    private boolean capturing;

    Glow() {
        boolean ok = Gdx.gl20 != null;
        if (ok) {
            copy = new ShaderProgram(VERTEX, COPY);
            blur = new ShaderProgram(VERTEX, BLUR);
            composite = new ShaderProgram(VERTEX, COMPOSITE);
            for (ShaderProgram p : new ShaderProgram[] {copy, blur, composite}) {
                if (!p.isCompiled()) {
                    ok = false;
                    if (Gdx.app != null) Gdx.app.error("Filament", "Glow shader did not compile; lit layers draw without glow:\n" + p.getLog());
                }
            }
            if (ok) {
                quad = new Mesh(true, 4, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
                        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));
                quad.setVertices(new float[] {-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f});
            }
        }
        available = ok;
    }

    boolean available() {
        return available;
    }

    /** Whether a lit layer is being captured, so cuts erase the buffer instead of painting dark. */
    boolean capturing() {
        return capturing;
    }

    /**
     * Starts capturing: from here until {@link #end} everything drawn goes into the glow buffer. The
     * batch must not be drawing; the caller begins it again after.
     */
    void begin() {
        viewport.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
        vx = viewport.get(0);
        vy = viewport.get(1);
        vw = viewport.get(2);
        vh = viewport.get(3);
        binding.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, binding);
        previous = binding.get(0);
        ensureSize(Math.max(vw, 1), Math.max(vh, 1));
        target(sharp);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        capturing = true;
    }

    /**
     * Ends the capture and adds the layer to the screen: the sharp layer through the mesh, then its
     * blurs at {@code soft}, {@code mid} and {@code wide} strength. {@code unitsPx} is screen pixels per
     * UI unit, which sets the blur radii and the mesh's cell. The batch must not be drawing.
     */
    void end(float unitsPx, float soft, float mid, float wide, float mesh) {
        capturing = false;
        Gdx.gl.glDisable(GL20.GL_BLEND);
        // Half size, unblurred: the soft halo next to the glyph (the mockups' 0.7 unit blur)
        pass(copy, sharp, this.soft, 0f, 0f, 0f);
        // Half size, 2.6 units
        float midSigma = 2.6f * unitsPx / 2f;
        blurPair(this.soft, midA, midB, midSigma);
        // Quarter size, 9 units
        pass(copy, this.soft, wideA, 0f, 0f, 0f);
        float wideSigma = 9f * unitsPx / 4f;
        blurPair(wideA, wideB, wideA, wideSigma);
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous);
        Gdx.gl.glViewport(vx, vy, vw, vh);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_ONE, GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_ONE);
        composite.bind();
        bindTexture(3, wideA.getColorBufferTexture());
        bindTexture(2, midB.getColorBufferTexture());
        bindTexture(1, this.soft.getColorBufferTexture());
        bindTexture(0, sharp.getColorBufferTexture());
        composite.setUniformi(composite.fetchUniformLocation("u_sharp", false), 0);
        composite.setUniformi(composite.fetchUniformLocation("u_soft", false), 1);
        composite.setUniformi(composite.fetchUniformLocation("u_mid", false), 2);
        composite.setUniformi(composite.fetchUniformLocation("u_wide", false), 3);
        composite.setUniformf(composite.fetchUniformLocation("u_k", false), soft, mid, wide);
        composite.setUniformf(composite.fetchUniformLocation("u_cell", false), Math.max(3f, Math.round(1.6f * unitsPx)));
        composite.setUniformf(composite.fetchUniformLocation("u_mesh", false), mesh);
        quad.render(composite, GL20.GL_TRIANGLE_FAN);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** A Gaussian blur of {@code sigma} texels: across from {@code src} into {@code tmp}, then down into {@code dst}. */
    private void blurPair(FrameBuffer src, FrameBuffer tmp, FrameBuffer dst, float sigma) {
        // Thirteen taps reach two and a half sigmas: spread them when the blur is wider than that
        float spread = Math.max(1f, sigma * 2.5f / 6f);
        float tapSigma = Math.max(sigma / spread, 0.5f);
        pass(blur, src, tmp, spread / src.getWidth(), 0f, tapSigma);
        pass(blur, tmp, dst, 0f, spread / tmp.getHeight(), tapSigma);
    }

    private void pass(ShaderProgram program, FrameBuffer src, FrameBuffer dst, float stepU, float stepV, float sigma) {
        target(dst);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        program.bind();
        bindTexture(0, src.getColorBufferTexture());
        program.setUniformi(program.fetchUniformLocation("u_tex", false), 0);
        if (program == blur) {
            program.setUniformf(program.fetchUniformLocation("u_step", false), stepU, stepV);
            program.setUniformf(program.fetchUniformLocation("u_sigma", false), sigma);
        }
        quad.render(program, GL20.GL_TRIANGLE_FAN);
    }

    /** Draws into {@code b} from here on, over all of it. */
    private static void target(FrameBuffer b) {
        b.bind();
        Gdx.gl.glViewport(0, 0, b.getWidth(), b.getHeight());
    }

    private static void bindTexture(int unit, Texture t) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
        t.bind();
    }

    private void ensureSize(int w, int h) {
        if (sharp != null && w == width && h == height) return;
        disposeBuffers();
        width = w;
        height = h;
        sharp = buffer(w, h);
        int hw = Math.max(1, (w + 1) / 2), hh = Math.max(1, (h + 1) / 2);
        soft = buffer(hw, hh);
        midA = buffer(hw, hh);
        midB = buffer(hw, hh);
        int qw = Math.max(1, (w + 3) / 4), qh = Math.max(1, (h + 3) / 4);
        wideA = buffer(qw, qh);
        wideB = buffer(qw, qh);
    }

    private static FrameBuffer buffer(int w, int h) {
        FrameBuffer b = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        b.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        b.getColorBufferTexture().setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        return b;
    }

    private void disposeBuffers() {
        for (FrameBuffer b : new FrameBuffer[] {sharp, soft, midA, midB, wideA, wideB}) if (b != null) b.dispose();
        sharp = soft = midA = midB = wideA = wideB = null;
    }

    @Override
    public void dispose() {
        disposeBuffers();
        if (copy != null) copy.dispose();
        if (blur != null) blur.dispose();
        if (composite != null) composite.dispose();
        if (quad != null) quad.dispose();
    }
}
