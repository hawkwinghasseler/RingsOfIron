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
 * <p>Art with a {@code height} layer (the exterior hulls) is lit by the game rather than by its
 * paint, and gets three more regions: {@code flat/<name>}, the art with the light painted on it
 * (every element marked {@code data-baked}) left out; {@code raised/<name>}, only what stands up
 * off the roof (marked {@code data-raised}), drawn again over the shadows the roof takes; and
 * {@code normal/<name>}, a normal map worked out from the height layer. Its {@code relief}
 * layer, the outline and height of each raised part, goes into {@code art.json} as what casts
 * shadows.
 *
 * <pre>java ArtPacker &lt;svg folder&gt; &lt;output folder&gt; [pixels per tile]</pre>
 *
 * Run through Gradle as {@code gradlew :art-pipeline:packArt}; the game's run tasks do it for
 * you, and only when an SVG changed.
 */
public final class ArtPacker {
    /** SVG units in one tile (see documentation/art/Design.md). */
    public static final float UNITS_PER_TILE = 64f;
    /**
     * Pixels between regions in the atlas. The atlas is mipmapped, and a mip level 2^n smaller
     * averages 2^n pixels into one, so a gap of 2 let art seen a few times smaller than it was
     * rasterized (a hull at the starting zoom) pick up faint lines from its neighbor's edge.
     */
    static final int PADDING = 16;
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
            Element reliefLayer = group(root, "relief");
            String relief = reliefLayer == null ? null : relief(reliefLayer);
            if (reliefLayer != null) root.removeChild(reliefLayer);
            Element height = group(root, "height");
            if (height != null) root.removeChild(height);
            write(render(dom, box, scale), staging.resolve(name + ".png"));
            if (height != null) litVariants(dom, height, box, scale, staging, name);
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
            if (relief != null) index.append(", \"relief\": ").append(relief);
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
        // Wide enough that the mipmaps of art drawn zoomed out don't take in their neighbors' edges
        settings.paddingX = PADDING;
        settings.paddingY = PADDING;
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

    /** Shapes that draw something, as opposed to groups and definitions. */
    private static final java.util.Set<String> SHAPES = java.util.Set.of(
            "rect", "circle", "ellipse", "path", "polygon", "polyline", "line", "use", "image", "text");
    /** Blur of the height map before its slopes are taken, in pixels at 64 a tile: rounds each edge into a bevel. */
    static final float HEIGHT_BLUR = 1.6f;
    /** Steepest slope (rise over run) the normal map keeps; steeper is eased toward it, so a wall reads as a bevel. */
    static final float MAX_SLOPE = 1.4f;

    /**
     * The regions of art the game lights itself (see the class comment): {@code flat/}, {@code raised/}
     * (with the emissive layer, which no shadow darkens) and {@code normal/}. Leaves {@code dom}
     * without its emissive layer and baked light.
     */
    private static void litVariants(Document dom, Element height, float[] box, float scale, Path staging, String name)
            throws Exception {
        Element root = dom.getDocumentElement();
        for (Element e : marked(root, "data-baked")) e.getParentNode().removeChild(e);
        write(render(dom, box, scale), staging.resolve("flat/" + name + ".png"));
        // The raised parts keep the emissive layer over them: a light is not darkened by a shadow
        Document raised = (Document) dom.cloneNode(true);
        keepRaised(group(raised.getDocumentElement(), "base"));
        write(render(raised, box, scale), staging.resolve("raised/" + name + ".png"));
        Element emissive = group(root, "emissive");
        if (emissive != null) root.removeChild(emissive);
        Element base = group(root, "base");
        if (base != null) root.removeChild(base);
        // Hidden in the file, so a browser shows the art and not the height map over it
        height.removeAttribute("display");
        root.appendChild(height);
        BufferedImage h = render(dom, box, scale);
        write(normals(h, Float.parseFloat(height.getAttribute("data-base")),
                Float.parseFloat(height.getAttribute("data-scale")), scale * UNITS_PER_TILE), staging.resolve("normal/" + name + ".png"));
    }

    private static List<Element> marked(Element scope, String attribute) {
        List<Element> out = new ArrayList<>();
        NodeList all = scope.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (e.hasAttribute(attribute)) out.add(e);
        }
        return out;
    }

    /** Removes every shape under {@code g} that is not marked raised or inside something that is. */
    private static void keepRaised(Element g) {
        if (g == null) return;
        List<Element> drop = new ArrayList<>();
        NodeList all = g.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (!SHAPES.contains(e.getLocalName())) continue;
            boolean keep = false;
            for (Node n = e; n != g && n instanceof Element; n = n.getParentNode()) {
                if (((Element) n).hasAttribute("data-raised")) {
                    keep = true;
                    break;
                }
            }
            if (!keep) drop.add(e);
        }
        for (Element e : drop) e.getParentNode().removeChild(e);
    }

    /**
     * A normal map from a height map: gray {@code base} is the bare roof and each step of 1 above
     * it {@code metersPerStep} higher, with {@code pixelsPerMeter}. Each normal is in the art's own
     * frame (x along the image, y up it, z out of the page), stored as RGB = normal / 2 + 0.5.
     */
    static BufferedImage normals(BufferedImage height, float base, float metersPerStep, float pixelsPerMeter) {
        int w = height.getWidth(), h = height.getHeight();
        float[] m = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = height.getRGB(x, y);
                float a = (argb >>> 24) / 255f, gray = (argb & 0xff) / 255f;
                // Unpremultiplied: what is not drawn (space) is gray 0
                m[y * w + x] = (gray * a - base) * metersPerStep;
            }
        }
        m = blur(m, w, h, HEIGHT_BLUR * pixelsPerMeter / UNITS_PER_TILE);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float gx = (m[y * w + Math.min(w - 1, x + 1)] - m[y * w + Math.max(0, x - 1)]) * 0.5f * pixelsPerMeter;
                // image y runs down, the normal's y up
                float gy = (m[Math.max(0, y - 1) * w + x] - m[Math.min(h - 1, y + 1) * w + x]) * 0.5f * pixelsPerMeter;
                float g = (float) Math.hypot(gx, gy);
                if (g > 1e-6f) {
                    float eased = (float) (MAX_SLOPE * Math.tanh(g / MAX_SLOPE)) / g;
                    gx *= eased;
                    gy *= eased;
                }
                float nx = -gx, ny = -gy, nz = 1f, len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                int r = Math.round((nx / len * 0.5f + 0.5f) * 255f);
                int gg = Math.round((ny / len * 0.5f + 0.5f) * 255f);
                int b = Math.round((nz / len * 0.5f + 0.5f) * 255f);
                out.setRGB(x, y, 0xff000000 | r << 16 | gg << 8 | b);
            }
        }
        return out;
    }

    /** A Gaussian blur of {@code sigma} pixels, edges clamped. */
    private static float[] blur(float[] in, int w, int h, float sigma) {
        int r = Math.max(1, (int) Math.ceil(sigma * 2.5f));
        float[] k = new float[2 * r + 1];
        float sum = 0f;
        for (int i = -r; i <= r; i++) sum += k[i + r] = (float) Math.exp(-i * i / (2f * sigma * sigma));
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        float[] tmp = new float[w * h], out = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float v = 0f;
                for (int i = -r; i <= r; i++) v += k[i + r] * in[y * w + Math.min(w - 1, Math.max(0, x + i))];
                tmp[y * w + x] = v;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float v = 0f;
                for (int i = -r; i <= r; i++) v += k[i + r] * tmp[Math.min(h - 1, Math.max(0, y + i)) * w + x];
                out[y * w + x] = v;
            }
        }
        return out;
    }

    /**
     * The relief layer as JSON: an array with one entry per raised part, its height in meters then
     * its outline's corners in SVG units, {@code [h, x0, y0, x1, y1, ...]}. A circle is a 16-gon.
     */
    static String relief(Element layer) {
        StringBuilder out = new StringBuilder("[");
        NodeList all = layer.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (!e.hasAttribute("data-h")) continue;
            List<float[]> pts = new ArrayList<>();
            switch (e.getLocalName()) {
                case "circle": {
                    float cx = length(e.getAttribute("cx")), cy = length(e.getAttribute("cy")), r = length(e.getAttribute("r"));
                    for (int k = 0; k < 16; k++) {
                        double a = k * Math.PI / 8;
                        pts.add(new float[]{cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r});
                    }
                    break;
                }
                case "rect": {
                    float x = length(e.getAttribute("x")), y = length(e.getAttribute("y"));
                    float w = length(e.getAttribute("width")), h = length(e.getAttribute("height"));
                    pts.add(new float[]{x, y});
                    pts.add(new float[]{x + w, y});
                    pts.add(new float[]{x + w, y + h});
                    pts.add(new float[]{x, y + h});
                    break;
                }
                case "polygon": {
                    String[] p = e.getAttribute("points").trim().split("[\\s,]+");
                    for (int k = 0; k + 1 < p.length; k += 2) pts.add(new float[]{Float.parseFloat(p[k]), Float.parseFloat(p[k + 1])});
                    break;
                }
                default:
                    continue;
            }
            if (pts.size() < 3) continue;
            if (out.length() > 1) out.append(", ");
            out.append("[").append(num(length(e.getAttribute("data-h"))));
            for (float[] p : pts) out.append(", ").append(num(p[0])).append(", ").append(num(p[1]));
            out.append("]");
        }
        return out.append("]").toString();
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
