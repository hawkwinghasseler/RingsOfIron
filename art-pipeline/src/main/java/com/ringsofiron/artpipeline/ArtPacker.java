package com.ringsofiron.artpipeline;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns the SVG art in {@code assets/art} into what the game loads: one texture atlas with a
 * region per file, named by its folder and file name ({@code tiles/floor}, {@code walls/hull_heavy_nes}),
 * the emissive layer of each file that has one as its own region under {@code glow/}
 * ({@code glow/components/reactor_xl}), and {@code art.json}, which gives each region's SVG viewBox
 * (so exterior art keeps its body origin and turrets their pivot) and the collider and
 * interaction point from its {@code meta} layer.
 *
 * <pre>java ArtPacker &lt;svg folder&gt; &lt;output folder&gt; [pixels per tile]</pre>
 *
 * Run through Gradle as {@code gradlew :art-pipeline:packArt}; the game's run tasks do it for
 * you, and only when an SVG changed.
 */
public final class ArtPacker {
    /** SVG units in one tile (see documentation/art/Design.md). */
    public static final float UNITS_PER_TILE = 64f;
    public static final String ATLAS_NAME = "art";
    private static final String INKSCAPE = "http://www.inkscape.org/namespaces/inkscape";

    private ArtPacker() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ArtPacker <svg folder> <output folder> [pixels per tile]");
            System.exit(2);
        }
        Path source = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        float pixelsPerTile = args.length > 2 ? Float.parseFloat(args[2]) : UNITS_PER_TILE;
        int count = pack(source, out, pixelsPerTile);
        System.out.println("Packed " + count + " SVGs from " + source + " into " + out.resolve(ATLAS_NAME + ".atlas"));
    }

    /** Rasterizes and packs every SVG under {@code source}; returns how many there were. */
    public static int pack(Path source, Path out, float pixelsPerTile) throws Exception {
        float scale = pixelsPerTile / UNITS_PER_TILE;
        Path staging = out.resolveSibling(out.getFileName() + "-png");
        deleteTree(staging);
        deleteTree(out);
        Files.createDirectories(staging);
        Files.createDirectories(out);

        List<Path> svgs;
        try (Stream<Path> files = Files.walk(source)) {
            svgs = files.filter(p -> p.toString().endsWith(".svg")).sorted().collect(Collectors.toList());
        }
        StringBuilder index = new StringBuilder();
        index.append("{\n  \"pixelsPerTile\": ").append(num(pixelsPerTile)).append(",\n  \"unitsPerTile\": ")
                .append(num(UNITS_PER_TILE)).append(",\n  \"regions\": {");
        boolean first = true;
        for (Path svg : svgs) {
            String name = source.relativize(svg).toString().replace(File.separatorChar, '/');
            name = name.substring(0, name.length() - ".svg".length());
            Document dom = parse(svg);
            Element root = dom.getDocumentElement();
            float[] box = viewBox(root);

            Element meta = group(root, "meta");
            Element emissive = group(root, "emissive");
            String collider = meta == null ? null : collider(meta);
            String interact = meta == null ? null : interact(meta);

            // The art as the scene sees it: everything but the invisible meta and collision shapes
            if (meta != null) meta.getParentNode().removeChild(meta);
            Element collision = group(root, "collision");
            if (collision != null) collision.getParentNode().removeChild(collision);
            write(render(dom, box, scale), staging.resolve(name + ".png"));
            boolean glow = emissive != null && hasShapes(emissive);
            if (glow) {
                // The emissive layer alone, for a glow pass
                Element base = group(root, "base");
                if (base != null) base.getParentNode().removeChild(base);
                write(render(dom, box, scale), staging.resolve("glow/" + name + ".png"));
            }

            index.append(first ? "\n" : ",\n").append("    \"").append(name).append("\": {")
                    .append("\"x\": ").append(num(box[0])).append(", \"y\": ").append(num(box[1]))
                    .append(", \"w\": ").append(num(box[2])).append(", \"h\": ").append(num(box[3]));
            if (glow) index.append(", \"glow\": true");
            if (collider != null) index.append(", \"collider\": ").append(collider);
            if (interact != null) index.append(", \"interact\": ").append(interact);
            index.append("}");
            first = false;
        }
        index.append("\n  }\n}\n");
        try (Writer w = Files.newBufferedWriter(out.resolve(ATLAS_NAME + ".json"), StandardCharsets.UTF_8)) {
            w.write(index.toString());
        }

        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.maxWidth = 4096;
        settings.maxHeight = 4096;
        settings.paddingX = 2;
        settings.paddingY = 2;
        settings.edgePadding = true;
        settings.duplicatePadding = true;
        settings.bleed = true;
        // Region names keep their trailing numbers (cargo_stack_2 is a name, not frame 2)
        settings.useIndexes = false;
        settings.stripWhitespaceX = false;
        settings.stripWhitespaceY = false;
        settings.combineSubdirectories = true;
        settings.flattenPaths = false;
        settings.filterMin = Texture.TextureFilter.MipMapLinearLinear;
        settings.filterMag = Texture.TextureFilter.Linear;
        settings.silent = true;
        TexturePacker.process(settings, staging.toString(), out.toString(), ATLAS_NAME);
        deleteTree(staging);
        return svgs.size();
    }

    private static Document parse(Path svg) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return f.newDocumentBuilder().parse(svg.toFile());
    }

    /** minX, minY, width, height of the root's viewBox (or its width and height). */
    private static float[] viewBox(Element root) {
        String vb = root.getAttribute("viewBox").trim();
        if (!vb.isEmpty()) {
            String[] p = vb.split("[\\s,]+");
            return new float[]{Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])};
        }
        return new float[]{0f, 0f, length(root.getAttribute("width")), length(root.getAttribute("height"))};
    }

    private static float length(String s) {
        return Float.parseFloat(s.replaceAll("[a-z%]+$", ""));
    }

    /** The top-level {@code <g id="...">} (or Inkscape layer with that label), or null. */
    private static Element group(Element root, String id) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element) || !"g".equals(n.getLocalName())) continue;
            Element g = (Element) n;
            if (id.equals(g.getAttribute("id")) || id.equals(g.getAttributeNS(INKSCAPE, "label"))) return g;
        }
        return null;
    }

    private static boolean hasShapes(Element g) {
        NodeList children = g.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) return true;
        }
        return false;
    }

    private static Element byId(Element scope, String id) {
        NodeList all = scope.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (id.equals(e.getAttribute("id"))) return e;
        }
        return null;
    }

    /** {"rect": [x, y, w, h]} or {"circle": [cx, cy, r]} in SVG units, or null. */
    private static String collider(Element meta) {
        Element c = byId(meta, "collider");
        if (c == null) return null;
        if ("circle".equals(c.getLocalName())) {
            return "{\"circle\": [" + attr(c, "cx") + ", " + attr(c, "cy") + ", " + attr(c, "r") + "]}";
        }
        return "{\"rect\": [" + attr(c, "x") + ", " + attr(c, "y") + ", " + attr(c, "width") + ", " + attr(c, "height") + "]}";
    }

    /** [cx, cy] in SVG units, or null. */
    private static String interact(Element meta) {
        Element c = byId(meta, "interact");
        if (c == null) return null;
        return "[" + attr(c, "cx") + ", " + attr(c, "cy") + "]";
    }

    private static String attr(Element e, String name) {
        String v = e.getAttribute(name);
        return v.isEmpty() ? "0" : num(length(v));
    }

    private static BufferedImage render(Document dom, float[] box, float scale) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        javax.xml.transform.Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        t.transform(new DOMSource(dom), new StreamResult(bytes));
        SVGDocument svg = new SVGLoader().load(new ByteArrayInputStream(bytes.toByteArray()), null,
                LoaderContext.builder().build());
        if (svg == null) throw new IOException("Could not read SVG");
        int w = Math.max(1, Math.round(box[2] * scale)), h = Math.max(1, Math.round(box[3] * scale));
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        svg.render(null, g, new ViewBox(0f, 0f, w, h));
        g.dispose();
        return image;
    }

    private static void write(BufferedImage image, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private static String num(float v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).collect(Collectors.toList())) {
                Files.delete(p);
            }
        }
    }
}
