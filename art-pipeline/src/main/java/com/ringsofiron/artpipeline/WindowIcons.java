package com.ringsofiron.artpipeline;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The game window's icon: the corvette's hull from the art pack, bow up and to the right, on a
 * dark disc with a thin orbit ring, at 128, 64, 32 and 16 px. Run with
 * {@code gradlew :art-pipeline:windowIcons}; the PNGs go in the lwjgl3 module's resources.
 * Arguments: the hull SVG, then the output folder.
 */
public final class WindowIcons {
    public static final int[] SIZES = {128, 64, 32, 16};

    public static void main(String[] args) throws IOException {
        Path svgFile = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        // The collision outline is an unfilled shape that would draw black over the hull
        String text = new String(Files.readAllBytes(svgFile), StandardCharsets.UTF_8)
                .replaceAll("(?s)<g id=\"collision\">.*?</g>", "");
        Path hullOnly = Files.createTempFile("hull", ".svg");
        Files.write(hullOnly, text.getBytes(StandardCharsets.UTF_8));
        SVGDocument svg = new SVGLoader().load(hullOnly.toUri().toURL(), LoaderContext.builder().build());
        Files.delete(hullOnly);
        if (svg == null) throw new IOException("Could not read " + svgFile);
        Files.createDirectories(out);
        for (int size : SIZES) {
            ImageIO.write(icon(svg, size), "png", out.resolve("icon" + size + ".png").toFile());
        }
    }

    static BufferedImage icon(SVGDocument svg, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(new Color(0x0b, 0x12, 0x20));
        g.fill(new Ellipse2D.Float(0.5f, 0.5f, size - 1f, size - 1f));
        if (size >= 32) {
            g.setColor(new Color(0x3f, 0x80, 0xd0, 160));
            g.setStroke(new java.awt.BasicStroke(Math.max(1f, size / 48f)));
            float r = size * 0.43f;
            g.draw(new Ellipse2D.Float(size / 2f - r, size / 2f - r * 0.55f, 2 * r, 2 * r * 0.55f));
        }
        // The hull, bow toward the upper right, filling most of the disc
        float w = (float) svg.size().getWidth(), h = (float) svg.size().getHeight();
        float scale = size * 0.92f / w;
        g.translate(size / 2.0, size / 2.0);
        g.rotate(-Math.PI / 4);
        g.translate(-w * scale / 2.0, -h * scale / 2.0);
        svg.render(null, g, new ViewBox(0f, 0f, w * scale, h * scale));
        g.dispose();
        return image;
    }
}
