package com.hawkwing.RingsOfIron.art;

import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The collision shape drawn in an exterior hull's SVG: its {@code <g id="collision">} layer (an
 * Inkscape layer labelled {@code collision} works too), whose {@code <polygon>}, {@code <rect>} and
 * straight-line {@code <path>} shapes say what the craft collides on (see documentation/art/Design.md).
 * The game rasterizes nothing of it.
 *
 * Exterior art is in the body frame, 64 units a meter with y flipped, so each shape comes out in
 * body-local meters. Shapes may overlap. Each becomes one or more convex polygons of 3 to 8
 * vertices, counter-clockwise, as {x0, y0, x1, y1, ...}: a convex shape is kept whole (split in a fan
 * past 8 vertices), a concave one is cut into triangles.
 */
public final class CollisionLayer {
    /** The layer's id (or Inkscape label). */
    public static final String LAYER = "collision";
    /** Most vertices Box2D takes in one polygon. */
    public static final int MAX_VERTICES = 8;

    private static final String INKSCAPE = "http://www.inkscape.org/namespaces/inkscape";
    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");
    private static final Pattern TRANSFORM = Pattern.compile("(matrix|translate|scale|rotate)\\s*\\(([^)]*)\\)");
    private static final Pattern PATH_TOKEN = Pattern.compile("[A-Za-z]|" + NUMBER.pattern());

    private CollisionLayer() {
    }

    /**
     * The convex pieces of {@code svg}'s collision layer, or null if the file does not exist or has
     * no such layer.
     *
     * @throws IllegalArgumentException if the file cannot be read, or the layer holds a shape that
     *                                  cannot be a collision polygon (a curve, too few points).
     */
    public static List<float[]> read(Path svg) {
        if (!Files.isRegularFile(svg)) return null;
        Element layer;
        try {
            layer = layer(parse(svg).getDocumentElement());
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read " + svg + ": " + e.getMessage(), e);
        }
        if (layer == null) return null;
        List<float[]> out = new ArrayList<>();
        try {
            shapes(layer, new Affine2(), out);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(svg + ": " + e.getMessage(), e);
        }
        return out;
    }

    private static Document parse(Path svg) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return f.newDocumentBuilder().parse(svg.toFile());
    }

    /** The top-level {@code <g>} with the layer's id or Inkscape label, or null. */
    private static Element layer(Element root) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element) || !"g".equals(n.getLocalName())) continue;
            Element g = (Element) n;
            if (LAYER.equals(g.getAttribute("id")) || LAYER.equals(g.getAttributeNS(INKSCAPE, "label"))) return g;
        }
        return null;
    }

    /** Adds the pieces of every shape in {@code e} (itself included), under the transform so far. */
    private static void shapes(Element e, Affine2 parent, List<float[]> out) {
        Affine2 m = new Affine2(parent).mul(transform(e.getAttribute("transform")));
        switch (e.getLocalName()) {
            case "polygon":
                addPolygon(points(e.getAttribute("points")), m, out);
                return;
            case "rect": {
                float x = number(e, "x"), y = number(e, "y"), w = number(e, "width"), h = number(e, "height");
                addPolygon(new float[]{x, y, x + w, y, x + w, y + h, x, y + h}, m, out);
                return;
            }
            case "path":
                for (float[] p : path(e.getAttribute("d"))) addPolygon(p, m, out);
                return;
            default:
                NodeList children = e.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element) shapes((Element) children.item(i), m, out);
                }
        }
    }

    /** Takes a shape in SVG units to body meters, and cuts it into convex pieces. */
    private static void addPolygon(float[] svg, Affine2 m, List<float[]> out) {
        float[] v = dropClosingPoint(svg);
        if (v.length < 6) throw new IllegalArgumentException("a collision shape needs at least 3 points");
        Vector2 p = new Vector2();
        for (int i = 0; i < v.length; i += 2) {
            m.applyTo(p.set(v[i], v[i + 1]));
            v[i] = p.x / ArtLibrary.UNITS_PER_TILE;
            v[i + 1] = -p.y / ArtLibrary.UNITS_PER_TILE;
        }
        float area = signedArea(v);
        if (Math.abs(area) < 1e-4f) return; // nothing to collide on
        if (area < 0f) reverse(v);
        if (isConvex(v)) {
            fan(v, out);
            return;
        }
        ShortArray tris = new EarClippingTriangulator().computeTriangles(v);
        for (int i = 0; i < tris.size; i += 3) {
            float[] t = {v[tris.get(i) * 2], v[tris.get(i) * 2 + 1], v[tris.get(i + 1) * 2], v[tris.get(i + 1) * 2 + 1],
                    v[tris.get(i + 2) * 2], v[tris.get(i + 2) * 2 + 1]};
            float a = signedArea(t);
            if (Math.abs(a) < 1e-4f) continue;
            if (a < 0f) reverse(t);
            out.add(t);
        }
    }

    /** A convex polygon as is, or as a fan of pieces of at most {@link #MAX_VERTICES} sharing its first vertex. */
    private static void fan(float[] v, List<float[]> out) {
        int n = v.length / 2;
        if (n <= MAX_VERTICES) {
            out.add(v);
            return;
        }
        for (int start = 1; start < n - 1; start += MAX_VERTICES - 2) {
            int end = Math.min(n - 1, start + MAX_VERTICES - 2);
            float[] piece = new float[(end - start + 2) * 2];
            piece[0] = v[0];
            piece[1] = v[1];
            for (int k = start; k <= end; k++) {
                piece[(k - start + 1) * 2] = v[k * 2];
                piece[(k - start + 1) * 2 + 1] = v[k * 2 + 1];
            }
            out.add(piece);
        }
    }

    /** True if every corner of counter-clockwise {@code v} turns left (or runs straight). */
    static boolean isConvex(float[] v) {
        int n = v.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n, k = (i + 2) % n;
            float cross = (v[j * 2] - v[i * 2]) * (v[k * 2 + 1] - v[j * 2 + 1])
                    - (v[j * 2 + 1] - v[i * 2 + 1]) * (v[k * 2] - v[j * 2]);
            if (cross < -1e-6f) return false;
        }
        return true;
    }

    /** Area of polygon {@code v}: positive when it runs counter-clockwise. */
    static float signedArea(float[] v) {
        float a = 0f;
        int n = v.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            a += v[i * 2] * v[j * 2 + 1] - v[j * 2] * v[i * 2 + 1];
        }
        return a * 0.5f;
    }

    private static void reverse(float[] v) {
        for (int i = 0, j = v.length - 2; i < j; i += 2, j -= 2) {
            float x = v[i], y = v[i + 1];
            v[i] = v[j];
            v[i + 1] = v[j + 1];
            v[j] = x;
            v[j + 1] = y;
        }
    }

    private static float[] dropClosingPoint(float[] v) {
        int n = v.length;
        if (n >= 4 && v[0] == v[n - 2] && v[1] == v[n - 1]) {
            float[] out = new float[n - 2];
            System.arraycopy(v, 0, out, 0, n - 2);
            return out;
        }
        return v.clone();
    }

    private static float[] points(String s) {
        float[] out = numbers(s);
        if (out.length % 2 != 0) throw new IllegalArgumentException("odd number of coordinates in points=\"" + s + "\"");
        return out;
    }

    private static float[] numbers(String s) {
        FloatArray out = new FloatArray();
        Matcher m = NUMBER.matcher(s);
        while (m.find()) out.add(Float.parseFloat(m.group()));
        return out.toArray();
    }

    private static float number(Element e, String name) {
        String v = e.getAttribute(name).trim();
        return v.isEmpty() ? 0f : Float.parseFloat(v.replaceAll("[a-z%]+$", ""));
    }

    /**
     * The polygons of a path made only of straight lines (M, L, H, V and Z, absolute or relative):
     * each subpath is one polygon.
     */
    private static List<float[]> path(String d) {
        List<float[]> out = new ArrayList<>();
        FloatArray cur = new FloatArray();
        Matcher m = PATH_TOKEN.matcher(d);
        List<String> tokens = new ArrayList<>();
        while (m.find()) tokens.add(m.group());
        char cmd = 0;
        float x = 0f, y = 0f, startX = 0f, startY = 0f;
        int i = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (Character.isLetter(t.charAt(0))) {
                cmd = t.charAt(0);
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    if (cur.size > 0) out.add(cur.toArray());
                    cur.clear();
                    x = startX;
                    y = startY;
                    continue;
                }
                if ("MmLlHhVv".indexOf(cmd) < 0) {
                    throw new IllegalArgumentException("collision paths take straight lines only, not '" + cmd + "'");
                }
                continue;
            }
            boolean rel = Character.isLowerCase(cmd);
            switch (Character.toUpperCase(cmd)) {
                case 'M':
                case 'L':
                    float px = Float.parseFloat(tokens.get(i)), py = Float.parseFloat(tokens.get(i + 1));
                    i += 2;
                    x = rel ? x + px : px;
                    y = rel ? y + py : py;
                    if (Character.toUpperCase(cmd) == 'M') {
                        if (cur.size > 0) out.add(cur.toArray());
                        cur.clear();
                        startX = x;
                        startY = y;
                        // Further pairs after a moveto are linetos
                        cmd = rel ? 'l' : 'L';
                    }
                    break;
                case 'H':
                    float hx = Float.parseFloat(tokens.get(i++));
                    x = rel ? x + hx : hx;
                    break;
                case 'V':
                    float vy = Float.parseFloat(tokens.get(i++));
                    y = rel ? y + vy : vy;
                    break;
                default:
                    throw new IllegalArgumentException("a collision path must start with a moveto");
            }
            cur.add(x, y);
        }
        if (cur.size > 0) out.add(cur.toArray());
        return out;
    }

    /** An SVG {@code transform} attribute as an affine transform. */
    static Affine2 transform(String s) {
        Affine2 out = new Affine2();
        if (s == null || s.isEmpty()) return out;
        Matcher m = TRANSFORM.matcher(s);
        while (m.find()) {
            float[] a = numbers(m.group(2));
            if (a.length == 0) continue;
            Affine2 t = new Affine2();
            switch (m.group(1).toLowerCase(Locale.ROOT)) {
                case "matrix":
                    // x' = a x + c y + e, y' = b x + d y + f
                    if (a.length < 6) throw new IllegalArgumentException("matrix() takes 6 numbers");
                    t.m00 = a[0];
                    t.m10 = a[1];
                    t.m01 = a[2];
                    t.m11 = a[3];
                    t.m02 = a[4];
                    t.m12 = a[5];
                    break;
                case "translate":
                    t.setToTranslation(a[0], a.length > 1 ? a[1] : 0f);
                    break;
                case "scale":
                    t.setToScaling(a[0], a.length > 1 ? a[1] : a[0]);
                    break;
                case "rotate":
                    // rotate(angle [cx cy]), degrees: clockwise on screen, since SVG y points down
                    float cx = a.length > 2 ? a[1] : 0f, cy = a.length > 2 ? a[2] : 0f;
                    t.setToTranslation(cx, cy).rotate(a[0]).translate(-cx, -cy);
                    break;
                default:
                    break;
            }
            out.mul(t);
        }
        return out;
    }
}
