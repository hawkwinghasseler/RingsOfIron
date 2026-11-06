// FILAMENT: a mockup renderer for the 90s VFD design language (documentation/ui-overhaul/style-vfd.md).
//
// Everything is drawn from geometry, the way the game should draw it: seven- and fourteen-segment
// glyphs from segment masks, 5x7 dot-matrix text from the character ROM below, bars and rings from
// segment counts. Nothing emissive comes from a font, so every segment can be ghosted, dimmed,
// blinked, burned out or left glowing on its own.
//
// Layout is in UI units (the game's 1280x800 layout); the canvas is drawn at `dpr` pixels per unit.
// Lit parts go to an emissive layer that is bloomed onto the base at the end (finish()); ghosts,
// glass, bezels and printed legends go straight to the base.
(function (global) {
  'use strict';

  // ---------------------------------------------------------------- palette
  // Three phosphor colours only. Zinc is the bare phosphor; amber and red are filters printed on the
  // glass over fixed regions, so a region's colour never changes, only whether it is lit.
  const PHOSPHOR = {
    zinc: { rgb: [70, 246, 204], hot: [222, 255, 246] },
    amber: { rgb: [255, 172, 52], hot: [255, 238, 204] },
    red: { rgb: [255, 64, 46], hot: [255, 216, 204] },
  };
  // Drive levels: the grid's duty cycle. ghost is an unlit element seen through the smoked glass.
  const LEVEL = { ghost: 0.075, dim: 0.40, norm: 0.80, full: 1.0 };
  const INK = {
    glass: '#040605', glassTop: '#08100e', bezel: '#141517', bezelHi: '#26282b', bezelLo: '#08090a',
    silk: '#8d949a', silkDim: '#5b6167', key: '#26282c', keyHi: '#3a3d42', keyLo: '#111214',
    lcd: '#9aa58a', lcdInk: '#1b2117', cut: '#0a0605',
  };

  // ---------------------------------------------------------------- 5x7 character ROM
  // Upper case only, by design: lower case is folded up. 7 rows of 5, '#' lit.
  const ROM = {};
  function def(ch, rows) { ROM[ch] = rows.split(' '); }
  def(' ', '..... ..... ..... ..... ..... ..... .....');
  def('!', '..#.. ..#.. ..#.. ..#.. ..#.. ..... ..#..');
  def('"', '.#.#. .#.#. .#.#. ..... ..... ..... .....');
  def('#', '.#.#. .#.#. ##### .#.#. ##### .#.#. .#.#.');
  def('%', '##... ##..# ...#. ..#.. .#... #..## ...##');
  def('&', '.##.. #..#. #.#.. .#... #.#.# #..#. .##.#');
  def("'", '.##.. ..#.. .#... ..... ..... ..... .....');
  def('(', '...#. ..#.. .#... .#... .#... ..#.. ...#.');
  def(')', '.#... ..#.. ...#. ...#. ...#. ..#.. .#...');
  def('*', '..... ..#.. #.#.# .###. #.#.# ..#.. .....');
  def('+', '..... ..#.. ..#.. ##### ..#.. ..#.. .....');
  def(',', '..... ..... ..... ..... .##.. ..#.. .#...');
  def('-', '..... ..... ..... ##### ..... ..... .....');
  def('.', '..... ..... ..... ..... ..... .##.. .##..');
  def('/', '..... ....# ...#. ..#.. .#... #.... .....');
  def('0', '.###. #...# #..## #.#.# ##..# #...# .###.');
  def('1', '..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.');
  def('2', '.###. #...# ....# ...#. ..#.. .#... #####');
  def('3', '##### ...#. ..#.. ...#. ....# #...# .###.');
  def('4', '...#. ..##. .#.#. #..#. ##### ...#. ...#.');
  def('5', '##### #.... ####. ....# ....# #...# .###.');
  def('6', '..##. .#... #.... ####. #...# #...# .###.');
  def('7', '##### ....# ...#. ..#.. .#... .#... .#...');
  def('8', '.###. #...# #...# .###. #...# #...# .###.');
  def('9', '.###. #...# #...# .#### ....# ...#. .##..');
  def(':', '..... .##.. .##.. ..... .##.. .##.. .....');
  def(';', '..... .##.. .##.. ..... .##.. ..#.. .#...');
  def('<', '...#. ..#.. .#... #.... .#... ..#.. ...#.');
  def('=', '..... ..... ##### ..... ##### ..... .....');
  def('>', '.#... ..#.. ...#. ....# ...#. ..#.. .#...');
  def('?', '.###. #...# ....# ...#. ..#.. ..... ..#..');
  def('@', '.###. #...# ....# .##.# #.#.# #.#.# .###.');
  def('A', '.###. #...# #...# #...# ##### #...# #...#');
  def('B', '####. #...# #...# ####. #...# #...# ####.');
  def('C', '.###. #...# #.... #.... #.... #...# .###.');
  def('D', '###.. #..#. #...# #...# #...# #..#. ###..');
  def('E', '##### #.... #.... ####. #.... #.... #####');
  def('F', '##### #.... #.... ####. #.... #.... #....');
  def('G', '.###. #...# #.... #.### #...# #...# .####');
  def('H', '#...# #...# #...# ##### #...# #...# #...#');
  def('I', '.###. ..#.. ..#.. ..#.. ..#.. ..#.. .###.');
  def('J', '..### ...#. ...#. ...#. ...#. #..#. .##..');
  def('K', '#...# #..#. #.#.. ##... #.#.. #..#. #...#');
  def('L', '#.... #.... #.... #.... #.... #.... #####');
  def('M', '#...# ##.## #.#.# #.#.# #...# #...# #...#');
  def('N', '#...# #...# ##..# #.#.# #..## #...# #...#');
  def('O', '.###. #...# #...# #...# #...# #...# .###.');
  def('P', '####. #...# #...# ####. #.... #.... #....');
  def('Q', '.###. #...# #...# #...# #.#.# #..#. .##.#');
  def('R', '####. #...# #...# ####. #.#.. #..#. #...#');
  def('S', '.#### #.... #.... .###. ....# ....# ####.');
  def('T', '##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..');
  def('U', '#...# #...# #...# #...# #...# #...# .###.');
  def('V', '#...# #...# #...# #...# #...# .#.#. ..#..');
  def('W', '#...# #...# #...# #.#.# #.#.# #.#.# .#.#.');
  def('X', '#...# #...# .#.#. ..#.. .#.#. #...# #...#');
  def('Y', '#...# #...# #...# .#.#. ..#.. ..#.. ..#..');
  def('Z', '##### ....# ...#. ..#.. .#... #.... #####');
  def('[', '.###. .#... .#... .#... .#... .#... .###.');
  def(']', '.###. ...#. ...#. ...#. ...#. ...#. .###.');
  def('_', '..... ..... ..... ..... ..... ..... #####');
  def('|', '..#.. ..#.. ..#.. ..#.. ..#.. ..#.. ..#..');
  // The custom glyphs every module carries (the "CG-ROM" page)
  def('↑', '..#.. .###. #.#.# ..#.. ..#.. ..#.. ..#..');
  def('↓', '..#.. ..#.. ..#.. ..#.. #.#.# .###. ..#..');
  def('→', '..... ..#.. ...#. ##### ...#. ..#.. .....');
  def('←', '..... ..#.. .#... ##### .#... ..#.. .....');
  def('°', '.##.. #..#. #..#. .##.. ..... ..... .....');
  def('Δ', '..... ..#.. ..#.. .#.#. .#.#. #...# #####');
  def('▲', '..... ..#.. ..#.. .###. .###. ##### .....');
  def('▼', '..... ##### .###. .###. ..#.. ..#.. .....');
  def('▶', '#.... ##... ###.. ####. ###.. ##... #....');
  def('◀', '....# ...## ..### .#### ..### ...## ....#');
  def('█', '##### ##### ##### ##### ##### ##### #####');
  def('·', '..... ..... ..... ..#.. ..... ..... .....');
  def('×', '..... #...# .#.#. ..#.. .#.#. #...# .....');
  def('±', '..#.. ..#.. ##### ..#.. ..#.. ..... #####');
  def('³', '.##.. ...#. ..#.. ...#. .##.. ..... .....');
  def('…', '..... ..... ..... ..... ..... #.#.# .....');
  def('¢', '..#.. .###. #.#.. #.#.. #.#.. .###. ..#..');
  def('♦', '..... ..#.. .###. ##### .###. ..#.. .....');
  def('●', '..... .###. ##### ##### ##### .###. .....');
  def('‖', '.#.#. .#.#. .#.#. .#.#. .#.#. .#.#. .#.#.');
  def('»', '..... #.#.. .#.#. ..#.# .#.#. #.#.. .....');

  // ---------------------------------------------------------------- segment masks
  const SEG7 = {
    '0': 'abcdef', '1': 'bc', '2': 'abged', '3': 'abgcd', '4': 'fgbc', '5': 'afgcd', '6': 'afgedc',
    '7': 'abc', '8': 'abcdefg', '9': 'abcdfg', '-': 'g', '_': 'd', ' ': '', 'A': 'abcefg', 'b': 'cdefg',
    'C': 'adef', 'c': 'deg', 'd': 'bcdeg', 'E': 'adefg', 'F': 'aefg', 'H': 'bcefg', 'h': 'cefg', 'L': 'def',
    'o': 'cdeg', 'O': 'abcdef', 'P': 'abefg', 'r': 'eg', 't': 'defg', 'U': 'bcdef', 'u': 'cde', 'n': 'ceg',
    'S': 'afgcd', 'y': 'bcdfg', 'J': 'bcde', '°': 'abfg',
  };
  // 14-segment: a top, b/c right, d bottom, e/f left, g1/g2 middle halves, h i j upper diagonal,
  // centre and diagonal, k l m the lower ones.
  const SEG14 = {
    ' ': [], '0': 'a b c d e f j k', '1': 'b c j', '2': 'a b g1 g2 e d', '3': 'a b c d g2', '4': 'f g1 g2 b c',
    '5': 'a f g1 g2 c d', '6': 'a f e d c g1 g2', '7': 'a b c', '8': 'a b c d e f g1 g2', '9': 'a b c d f g1 g2',
    'A': 'a b c e f g1 g2', 'B': 'a b c d i l g2', 'C': 'a d e f', 'D': 'a b c d i l', 'E': 'a d e f g1 g2',
    'F': 'a e f g1', 'G': 'a c d e f g2', 'H': 'b c e f g1 g2', 'I': 'a d i l', 'J': 'b c d e',
    'K': 'e f g1 j m', 'L': 'd e f', 'M': 'b c e f h j', 'N': 'b c e f h m', 'O': 'a b c d e f',
    'P': 'a b e f g1 g2', 'Q': 'a b c d e f m', 'R': 'a b e f g1 g2 m', 'S': 'a f g1 g2 c d', 'T': 'a i l',
    'U': 'b c d e f', 'V': 'e f k j', 'W': 'b c e f k m', 'X': 'h j k m', 'Y': 'h j l', 'Z': 'a d j k',
    '-': 'g1 g2', '+': 'g1 g2 i l', '/': 'j k', '*': 'g1 g2 h i j k l m', '=': 'g1 g2 d', '_': 'd',
    '<': 'j m', '>': 'h k', "'": 'i', '°': 'a b g2 i', '.': [], ':': 'i l',
  };
  for (const k in SEG14) if (typeof SEG14[k] === 'string') SEG14[k] = SEG14[k].split(' ');

  // ---------------------------------------------------------------- helpers
  const rgba = (rgb, a) => `rgba(${rgb[0] | 0},${rgb[1] | 0},${rgb[2] | 0},${a})`;
  const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
  function litStyle(color, level) {
    const p = PHOSPHOR[color] || PHOSPHOR.zinc;
    const lv = typeof level === 'number' ? level : LEVEL[level || 'norm'];
    if (lv >= 0.99) return rgba(mix(p.rgb, p.hot, 0.42), 1);
    if (lv >= 0.75) return rgba(mix(p.rgb, p.hot, 0.12), lv + 0.1);
    return rgba(p.rgb, lv);
  }
  function ghostStyle(color, k) {
    const p = PHOSPHOR[color] || PHOSPHOR.zinc;
    return rgba(mix(p.rgb, [150, 160, 155], 0.35), LEVEL.ghost * (k || 1));
  }
  function poly(ctx, pts) {
    ctx.beginPath();
    ctx.moveTo(pts[0][0], pts[0][1]);
    for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
    ctx.closePath();
    ctx.fill();
  }
  function rrect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }
  // A segment between two points: a stretched hexagon with pointed ends, shortened by gap at each end.
  function segPoly(x0, y0, x1, y1, t, gap) {
    const dx = x1 - x0, dy = y1 - y0, L = Math.hypot(dx, dy);
    const ux = dx / L, uy = dy / L, nx = -uy, ny = ux;
    const a = gap, b = L - gap, hw = t / 2, tip = Math.min(hw, (b - a) / 2);
    const P = (s, o) => [x0 + ux * s + nx * o, y0 + uy * s + ny * o];
    return [P(a, 0), P(a + tip, hw), P(b - tip, hw), P(b, 0), P(b - tip, -hw), P(a + tip, -hw)];
  }

  // ---------------------------------------------------------------- icons (16x16 box)
  // Each icon is a list of separate parts, as on the glass: [kind, ...]. 'l' polyline (stroked),
  // 'p' polygon (filled), 'c' circle arc [cx, cy, r, a0, a1] (stroked), 'd' dot [cx, cy, r].
  const D = Math.PI / 180;
  const ICONS = {
    missile: [['p', [[3, 7], [11, 7], [14, 8], [11, 9], [3, 9]]], ['p', [[1, 3.5], [3.2, 3.5], [5.5, 6.2], [2.5, 6.2]]],
      ['p', [[1, 12.5], [3.2, 12.5], [5.5, 9.8], [2.5, 9.8]]], ['l', [[0.5, 8], [1.8, 8]]]],
    lock: [['c', 8, 8, 5.5, 20, 70], ['c', 8, 8, 5.5, 110, 160], ['c', 8, 8, 5.5, 200, 250], ['c', 8, 8, 5.5, 290, 340],
      ['l', [[8, 0.5], [8, 4]]], ['l', [[8, 12], [8, 15.5]]], ['l', [[0.5, 8], [4, 8]]], ['l', [[12, 8], [15.5, 8]]], ['d', 8, 8, 1.1]],
    dock: [['l', [[6.5, 2.5], [2.5, 2.5], [2.5, 13.5], [6.5, 13.5]]], ['l', [[9.5, 2.5], [13.5, 2.5], [13.5, 13.5], [9.5, 13.5]]],
      ['p', [[5, 6.2], [7.2, 8], [5, 9.8]]], ['p', [[11, 6.2], [8.8, 8], [11, 9.8]]]],
    prop: [['l', [[5, 2], [11, 2]]], ['l', [[4, 4], [12, 4], [12, 12], [4, 12], [4, 4]]], ['p', [[5.5, 9], [10.5, 9], [10.5, 10.6], [5.5, 10.6]]],
      ['p', [[5.5, 6.4], [10.5, 6.4], [10.5, 8], [5.5, 8]]], ['p', [[6.5, 13.5], [9.5, 13.5], [10.5, 15.5], [5.5, 15.5]]]],
    power: [['p', [[9.5, 0.8], [3.5, 9], [7.4, 9], [6, 15.2], [12.5, 6.6], [8.6, 6.6]]]],
    o2: [['c', 5.2, 8, 3.6, 0, 360], ['c', 10.8, 8, 3.6, 200, 520]],
    fire: [['p', [[8, 0.8], [12.4, 7], [12.6, 11], [10.4, 14.6], [8, 15.4], [5.4, 14.6], [3.4, 11], [4, 7.6], [6, 9.6], [6.4, 5]]]],
    breach: [['l', [[1, 3], [6.5, 3]]], ['l', [[9.5, 3], [15, 3]]], ['l', [[1, 13], [6, 13]]], ['l', [[10, 13], [15, 13]]],
      ['l', [[6.5, 3], [9, 6.5], [7, 9.5], [10, 13]]], ['l', [[1, 3], [1, 13]]], ['l', [[15, 3], [15, 13]]]],
    crew: [['c', 8, 4.2, 2.8, 0, 360], ['p', [[3, 15.5], [4, 10], [6, 8.6], [10, 8.6], [12, 10], [13, 15.5]]]],
    cargo: [['l', [[2, 3], [14, 3], [14, 14], [2, 14], [2, 3]]], ['l', [[2, 7], [14, 7]]], ['p', [[6.5, 3], [9.5, 3], [9.5, 6], [6.5, 6]]]],
    warp: [['p', [[1.5, 3], [4.5, 3], [9.5, 8], [4.5, 13], [1.5, 13], [6.5, 8]]], ['p', [[7.5, 3], [10.5, 3], [15.5, 8], [10.5, 13], [7.5, 13], [12.5, 8]]]],
    auto: [['c', 8, 8, 6.5, 200, 340], ['c', 8, 8, 6.5, 20, 160], ['l', [[2, 8], [6, 8]]], ['l', [[10, 8], [14, 8]]], ['d', 8, 8, 1.6], ['l', [[8, 10], [8, 14]]]],
    save: [['l', [[2, 2], [12, 2], [14, 4], [14, 14], [2, 14], [2, 2]]], ['p', [[5, 2.8], [10.5, 2.8], [10.5, 6.5], [5, 6.5]]],
      ['p', [[4.5, 9], [11.5, 9], [11.5, 13.2], [4.5, 13.2]]]],
    comms: [['l', [[8, 7], [8, 15.5]]], ['d', 8, 6, 1.4], ['c', 8, 6, 3.6, 300, 420], ['c', 8, 6, 3.6, 120, 240], ['c', 8, 6, 6.6, 305, 415], ['c', 8, 6, 6.6, 125, 235]],
    caution: [['l', [[8, 1.5], [15, 14.5], [1, 14.5], [8, 1.5]]], ['p', [[7.2, 5.5], [8.8, 5.5], [8.4, 10.5], [7.6, 10.5]]], ['d', 8, 12.4, 0.9]],
    shield: [['l', [[8, 1], [14, 3.2], [13.4, 9], [8, 15], [2.6, 9], [2, 3.2], [8, 1]]], ['p', [[8, 4], [11.5, 5.2], [11.1, 8.6], [8, 12]]]],
    heat: [['l', [[6.5, 10], [6.5, 2.5], [9.5, 2.5], [9.5, 10]]], ['c', 8, 12, 3.2, -60, 240], ['d', 8, 12, 1.7], ['l', [[11, 4.5], [13, 4.5]]], ['l', [[11, 7], [13, 7]]]],
    ship: [['p', [[1, 6], [4, 4.5], [11, 4.5], [15, 8], [11, 11.5], [4, 11.5], [1, 10]]]],
    station: [['c', 8, 8, 6.2, 0, 360], ['p', [[6.6, 6.6], [9.4, 6.6], [9.4, 9.4], [6.6, 9.4]]], ['l', [[8, 1.8], [8, 5]]], ['l', [[8, 11], [8, 14.2]]], ['l', [[1.8, 8], [5, 8]]], ['l', [[11, 8], [14.2, 8]]]],
    orbit: [['c', 8, 8, 6.8, 0, 360], ['d', 8, 8, 2.4], ['p', [[13, 2.5], [15.5, 2.5], [15.5, 5], [13, 5]]]],
    clock: [['c', 8, 8, 6.5, 0, 360], ['l', [[8, 3.5], [8, 8], [11, 10]]]],
    tow: [['l', [[8, 1], [8, 8]]], ['c', 8, 11, 3, -10, 200], ['l', [[5, 4], [11, 4]]]],
    msg: [['l', [[1.5, 3.5], [14.5, 3.5], [14.5, 12.5], [1.5, 12.5], [1.5, 3.5]]], ['l', [[1.5, 3.5], [8, 8.5], [14.5, 3.5]]]],
    pd: [['c', 8, 8, 6.5, 0, 360], ['l', [[8, 4], [8, 12]]], ['l', [[4, 8], [12, 8]]], ['d', 8, 8, 1.6]],
    target: [['p', [[8, 1], [15, 8], [8, 15], [1, 8]]]],
    drive: [['p', [[0.8, 5.4], [3.6, 5.4], [3.6, 10.6], [0.8, 10.6]]], ['p', [[4.6, 6.8], [6.4, 6.8], [10.2, 2.8], [10.2, 13.2], [6.4, 9.2], [4.6, 9.2]]],
      ['l', [[11.6, 5.2], [15.2, 4]]], ['l', [[11.6, 8], [15.6, 8]]], ['l', [[11.6, 10.8], [15.2, 12]]]],
    eva: [['c', 5.4, 3.8, 2.4, 0, 360], ['p', [[1.2, 14.6], [2, 9.8], [3.6, 8.4], [7.2, 8.4], [8.8, 9.8], [9.6, 14.6]]], ['l', [[9.6, 11.6], [11.4, 9.4], [13, 11.8], [15, 8.6]]]],
    cassette: [['l', [[1, 3], [15, 3], [15, 13], [1, 13], [1, 3]]], ['c', 5.2, 7.5, 1.8, 0, 360], ['c', 10.8, 7.5, 1.8, 0, 360], ['l', [[4, 13], [5, 10.5], [11, 10.5], [12, 13]]]],
  };

  // ---------------------------------------------------------------- the scene
  class Scene {
    constructor(canvas, W, H, dpr) {
      this.W = W; this.H = H; this.dpr = dpr;
      canvas.width = Math.round(W * dpr); canvas.height = Math.round(H * dpr);
      canvas.style.width = W + 'px'; canvas.style.height = H + 'px';
      this.canvas = canvas;
      this.base = canvas.getContext('2d');
      const mk = () => { const c = document.createElement('canvas'); c.width = canvas.width; c.height = canvas.height; return c; };
      this.glassC = mk(); this.projC = mk(); this.cutC = mk();
      this.glass = this.glassC.getContext('2d'); this.proj = this.projC.getContext('2d'); this.cutX = this.cutC.getContext('2d');
      for (const c of [this.base, this.glass, this.proj, this.cutX]) c.setTransform(dpr, 0, 0, dpr, 0, 0);
      this.cuts = 0;
      this.modules = [];
      this.font = 'Inter';
      // While dark is set, everything drawn is unpowered (9.5): no tube lights, every ghost shows, so a dead
      // module still shows what it would hold.
      this.dark = false;
    }
    ctxFor(o) { return o && o.layer === 'proj' ? this.proj : this.glass; }
    // The guide's minimum sizes (section 15). A call below them is reported once, unless it says it is
    // a specimen (o.small), so a mockup can't quietly drop under the minimums.
    tooSmall(kind, v, min, str, o) {
      if (v >= min - 1e-6 || (o && o.small)) return;
      const key = kind + ':' + v + ':' + str;
      if ((this._warned ||= new Set()).has(key)) return;
      this._warned.add(key); console.warn(`under minimum: ${kind} ${v} < ${min} "${String(str).slice(0, 24)}"`);
    }

    // -------- backgrounds
    image(img, x, y, w, h, o) {
      const b = this.base; b.save();
      if (o && o.filter) b.filter = o.filter;
      b.drawImage(img, x, y, w, h); b.restore();
    }
    fill(x, y, w, h, style) { this.base.fillStyle = style; this.base.fillRect(x, y, w, h); }

    // A bezel plate: satin black moulded plastic.
    rack(x, y, w, h, o = {}) {
      const b = this.base, r = o.radius ?? 6;
      b.save();
      b.shadowColor = 'rgba(0,0,0,0.6)'; b.shadowBlur = 14; b.shadowOffsetY = o.shadowY ?? -2;
      const g = b.createLinearGradient(0, y, 0, y + h);
      g.addColorStop(0, '#1d1e21'); g.addColorStop(0.12, '#17181a'); g.addColorStop(1, '#0f1011');
      b.fillStyle = g; rrect(b, x, y, w, h, r); b.fill();
      b.shadowColor = 'transparent';
      b.strokeStyle = INK.bezelHi; b.lineWidth = 1; rrect(b, x + 0.5, y + 0.5, w - 1, h - 1, r); b.stroke();
      // a fine moulded texture: horizontal brushing
      b.globalAlpha = 0.035; b.fillStyle = '#ffffff';
      for (let yy = y + 3; yy < y + h - 2; yy += 2) b.fillRect(x + 4, yy, w - 8, 0.5);
      b.restore();
    }
    // A round housing, for scopes that stand out of a rack.
    pod(cx, cy, r) {
      const b = this.base; b.save();
      b.shadowColor = 'rgba(0,0,0,0.65)'; b.shadowBlur = 16;
      const g = b.createRadialGradient(cx, cy - r * 0.4, r * 0.2, cx, cy, r);
      g.addColorStop(0, '#202124'); g.addColorStop(1, '#101113');
      b.fillStyle = g; b.beginPath(); b.arc(cx, cy, r, 0, Math.PI * 2); b.fill();
      b.shadowColor = 'transparent'; b.strokeStyle = INK.bezelHi; b.lineWidth = 1;
      b.beginPath(); b.arc(cx, cy, r - 0.5, Math.PI * 1.05, Math.PI * 1.95); b.stroke();
      b.restore();
    }

    // A display window: smoked glass set into the bezel. Filaments run across it.
    module(x, y, w, h, o = {}) {
      const b = this.base, r = o.radius ?? 3;
      b.save();
      b.fillStyle = '#000'; rrect(b, x - 1.5, y - 1.5, w + 3, h + 3, r + 1.5); b.fill();
      const g = b.createLinearGradient(0, y, 0, y + h);
      g.addColorStop(0, INK.glassTop); g.addColorStop(1, INK.glass);
      b.fillStyle = g; rrect(b, x, y, w, h, r); b.fill();
      b.strokeStyle = 'rgba(255,255,255,0.06)'; b.lineWidth = 0.6;
      b.beginPath(); b.moveTo(x + r, y + 0.6); b.lineTo(x + w - r, y + 0.6); b.stroke();
      b.restore();
      this.modules.push({ x, y, w, h, r, filaments: o.filaments ?? Math.max(1, Math.round(h / 34)), circle: false, glare: o.glare ?? true });
    }
    roundModule(cx, cy, rad, o = {}) {
      const b = this.base; b.save();
      b.fillStyle = '#000'; b.beginPath(); b.arc(cx, cy, rad + 1.5, 0, Math.PI * 2); b.fill();
      const g = b.createRadialGradient(cx, cy - rad * 0.5, 0, cx, cy, rad);
      g.addColorStop(0, INK.glassTop); g.addColorStop(1, INK.glass);
      b.fillStyle = g; b.beginPath(); b.arc(cx, cy, rad, 0, Math.PI * 2); b.fill();
      b.restore();
      this.modules.push({ x: cx - rad, y: cy - rad, w: rad * 2, h: rad * 2, cx, cy, rad, circle: true, filaments: o.filaments ?? 4, glare: true });
    }

    // -------- printed (non-emissive) legends on the bezel
    silk(x, y, str, o = {}) {
      const b = this.base, size = o.size ?? 12;
      this.tooSmall('silk', size, 12, str, o);
      b.save();
      b.font = `${o.weight ?? 600} ${size}px ${this.font}`;
      b.fillStyle = o.color ?? INK.silk;
      b.textBaseline = 'alphabetic';
      const sx = o.condense ?? 0.86;
      const text = (o.upper === false ? str : str.toUpperCase());
      b.letterSpacing = `${(o.track ?? 0.12) * size}px`;
      const w = b.measureText(text).width * sx;
      const ax = o.align === 'right' ? x - w : o.align === 'center' ? x - w / 2 : x;
      b.translate(ax, y); b.scale(sx, 1); b.fillText(text, 0, 0);
      b.restore();
      return w;
    }
    silkRule(x0, y, x1, o = {}) {
      const b = this.base; b.save(); b.strokeStyle = o.color ?? INK.silkDim; b.lineWidth = o.width ?? 0.6;
      b.beginPath(); b.moveTo(x0, y); b.lineTo(x1, y); b.stroke(); b.restore();
    }

    // -------- annunciator legends: phosphor laid down in a fixed shape
    legend(x, y, str, o = {}) {
      const size = o.size ?? 13, sx = o.condense ?? 0.8;
      this.tooSmall('legend', size, 12, str, o);
      const lit = o.lit !== false && !this.dark;
      const draw = (ctx, style) => {
        ctx.save();
        ctx.font = `${o.weight ?? 700} ${size}px ${this.font}`;
        ctx.letterSpacing = `${(o.track ?? 0.06) * size}px`;
        const w = ctx.measureText(str).width * sx;
        const ax = o.align === 'right' ? x - w : o.align === 'center' ? x - w / 2 : x;
        ctx.translate(ax, y); ctx.scale(sx, 1);
        ctx.fillStyle = style; ctx.fillText(str, 0, 0);
        if (o.box) { ctx.strokeStyle = style; ctx.lineWidth = 0.9 / sx * 0.9; ctx.strokeRect(-2.5 / sx, -size * 0.86, (w + 5) / sx, size * 1.14); }
        ctx.restore();
        return w;
      };
      const gw = o.ghost !== false ? draw(this.base, ghostStyle(o.color, 1.3)) : 0;
      return lit ? draw(this.ctxFor(o), litStyle(o.color, o.level)) : (gw || this.legendWidth(str, o));
    }

    // -------- seven segments
    seg7(x, y, h, str, o = {}) {
      this.tooSmall('seg7', h, 12, str, o);
      const w = h * (o.aspect ?? 0.54), t = h * (o.thick ?? 0.13), gap = h * 0.022, s = Math.tan((o.slant ?? 8) * D);
      const adv = w + h * (o.spacing ?? 0.26);
      // parse into cells: char + decimal point / colon flags
      const cells = [];
      for (const ch of String(str)) {
        if (ch === '.' && cells.length) { cells[cells.length - 1].dp = true; continue; }
        if (ch === ':' && cells.length) { cells[cells.length - 1].colon = true; continue; }
        if (ch === ',' && cells.length) { cells[cells.length - 1].comma = true; continue; }
        cells.push({ ch });
      }
      const n = o.cells ?? cells.length;
      while (cells.length < n) (o.align === 'left' ? cells.push({ ch: ' ' }) : cells.unshift({ ch: ' ' }));
      const shear = (px, py, ox) => [ox + px + (h - py) * s, y + py];
      const segs = {
        a: [t / 2, t / 2, w - t / 2, t / 2], g: [t / 2, h / 2, w - t / 2, h / 2], d: [t / 2, h - t / 2, w - t / 2, h - t / 2],
        f: [t / 2, t / 2, t / 2, h / 2], b: [w - t / 2, t / 2, w - t / 2, h / 2],
        e: [t / 2, h / 2, t / 2, h - t / 2], c: [w - t / 2, h / 2, w - t / 2, h - t / 2],
      };
      const litCtx = this.ctxFor(o), lit = litStyle(o.color, o.level), ghost = o.ghost !== false;
      // a colon takes a narrow cell of its own between two digits
      const colonW = h * 0.2;
      let cx0 = x;
      const at = cells.map(c => { const v = cx0; cx0 += adv + (c.colon ? colonW : 0); return v; });
      cells.forEach((cell, i) => {
        const ox = at[i];
        const on = this.dark ? '' : SEG7[cell.ch] ?? '';
        for (const k of 'abcdefg') {
          const sg = segs[k];
          const pts = segPoly(sg[0], sg[1], sg[2], sg[3], t, gap).map(p => shear(p[0], p[1], ox));
          if (on.includes(k) && !(o.dead && o.dead(i, k))) { litCtx.fillStyle = lit; poly(litCtx, pts); }
          else if (ghost) { this.base.fillStyle = ghostStyle(o.color); poly(this.base, pts); }
        }
        if (o.dp !== false) {
          const dp = [[w + h * 0.06, h - t], [w + h * 0.06 + t, h - t], [w + h * 0.06 + t, h], [w + h * 0.06, h]].map(p => shear(p[0], p[1], ox));
          if (cell.dp && !this.dark) { litCtx.fillStyle = lit; poly(litCtx, dp); } else if (ghost && o.dpGhost) { this.base.fillStyle = ghostStyle(o.color); poly(this.base, dp); }
        }
        // the thousands mark, as a calculator's tube has it: the point with a tail below the baseline
        if (cell.comma && !this.dark) {
          const px = w + h * 0.06;
          litCtx.fillStyle = lit;
          poly(litCtx, [[px, h - t], [px + t, h - t], [px + t, h], [px, h]].map(p => shear(p[0], p[1], ox)));
          poly(litCtx, [[px + t * 0.35, h], [px + t, h], [px + t * 0.35, h + t * 1.5], [px - t * 0.3, h + t * 1.5]].map(p => shear(p[0], p[1], ox)));
        }
        if (cell.colon) {
          for (const cy of [h * 0.3, h * 0.7]) {
            const cx = w + (adv - w + colonW) / 2 - t / 2;
            const pts = [[cx, cy - t / 2], [cx + t, cy - t / 2], [cx + t, cy + t / 2], [cx, cy + t / 2]].map(p => shear(p[0], p[1], ox));
            if (this.dark) { this.base.fillStyle = ghostStyle(o.color); poly(this.base, pts); } else { litCtx.fillStyle = lit; poly(litCtx, pts); }
          }
        }
      });
      return cx0 - x - h * (o.spacing ?? 0.26);
    }
    seg7Width(h, n, o = {}) { return n * h * ((o.aspect ?? 0.54) + (o.spacing ?? 0.26)) - h * (o.spacing ?? 0.26) + (o.colons ?? 0) * h * 0.2; }

    // -------- fourteen segments
    seg14(x, y, h, str, o = {}) {
      this.tooSmall('seg14', h, 12, str, o);
      const w = h * (o.aspect ?? 0.6), t = h * (o.thick ?? 0.1), gap = h * 0.025, s = Math.tan((o.slant ?? 8) * D);
      const adv = w + h * (o.spacing ?? 0.24);
      const chars = [...String(str).toUpperCase()];
      const n = o.cells ?? chars.length;
      while (chars.length < n) (o.align === 'right' ? chars.unshift(' ') : chars.push(' '));
      const shear = (px, py, ox) => [ox + px + (h - py) * s, y + py];
      const hw = t / 2, cx = w / 2, cy = h / 2, dt = t * 0.95;
      const S = {
        a: [hw, hw, w - hw, hw, t], d: [hw, h - hw, w - hw, h - hw, t],
        g1: [hw, cy, cx, cy, t], g2: [cx, cy, w - hw, cy, t],
        f: [hw, hw, hw, cy, t], b: [w - hw, hw, w - hw, cy, t], e: [hw, cy, hw, h - hw, t], c: [w - hw, cy, w - hw, h - hw, t],
        i: [cx, hw, cx, cy, t], l: [cx, cy, cx, h - hw, t],
        h: [t * 1.25, t * 1.25, cx - t * 0.55, cy - t * 0.55, dt], j: [w - t * 1.25, t * 1.25, cx + t * 0.55, cy - t * 0.55, dt],
        k: [t * 1.25, h - t * 1.25, cx - t * 0.55, cy + t * 0.55, dt], m: [w - t * 1.25, h - t * 1.25, cx + t * 0.55, cy + t * 0.55, dt],
      };
      const litCtx = this.ctxFor(o), lit = litStyle(o.color, o.level), ghost = o.ghost !== false;
      chars.forEach((ch, i) => {
        const ox = x + i * adv, on = this.dark ? [] : SEG14[ch] ?? [];
        for (const k in S) {
          const sg = S[k];
          const diag = 'hjkm'.includes(k);
          const pts = segPoly(sg[0], sg[1], sg[2], sg[3], sg[4], diag ? gap * 0.6 : gap).map(p => shear(p[0], p[1], ox));
          if (on.includes(k)) { litCtx.fillStyle = lit; poly(litCtx, pts); }
          else if (ghost) { this.base.fillStyle = ghostStyle(o.color); poly(this.base, pts); }
        }
      });
      return n * adv - h * (o.spacing ?? 0.24);
    }
    seg14Width(h, n, o = {}) { return n * h * ((o.aspect ?? 0.6) + (o.spacing ?? 0.24)) - h * (o.spacing ?? 0.24); }

    // -------- dot matrix
    // Text on 5x7 cells, 6 dots per cell across. o.cells pads to a fixed field; o.inverse = [from, to)
    // lights the cells' unlit dots instead; o.ghost draws the unlit dots of every cell.
    dm(x, y, str, o = {}) {
      const p = o.pitch ?? 2, d = p * (o.dot ?? 0.8), rr = d * 0.28;
      if (String(str).trim()) this.tooSmall('dot pitch', p, 1.5, str, o);
      const chars = [...String(str).toUpperCase()];
      const n = o.cells ?? chars.length;
      while (chars.length < n) (o.align === 'right' ? chars.unshift(' ') : chars.push(' '));
      if (chars.length > n) chars.length = n;
      const litCtx = this.ctxFor(o), lit = litStyle(o.color, o.level);
      const ghost = o.ghost !== false && o.layer !== 'proj';
      const gst = ghostStyle(o.color, 0.8);
      const adv = p * (o.cellW ?? 6);
      const inv = this.dark ? null : o.inverse;
      // inverse cells: one lit block over the run (gaps and leading included), the glyphs' dots cut out of it
      if (inv && inv[1] > inv[0]) {
        const bx = x + inv[0] * adv - p * 0.5, bw = (inv[1] - inv[0]) * adv - p * 0.5 + p * 0.5;
        litCtx.fillStyle = lit; rrect(litCtx, bx, y - p, bw, 9 * p, p * 0.4); litCtx.fill();
      }
      const holes = [];
      chars.forEach((ch, i) => {
        const g = ROM[ch] ?? ROM['?'];
        const flip = inv && i >= inv[0] && i < inv[1];
        for (let r = 0; r < 7; r++) for (let c = 0; c < 5; c++) {
          const on = !this.dark && g[r][c] === '#';
          const dx = x + i * adv + c * p + (p - d) / 2, dy = y + r * p + (p - d) / 2;
          if (flip) { if (on) holes.push([x + i * adv + c * p + p * 0.04, y + r * p + p * 0.04, p * 0.92, p * 0.92]); }
          else if (on) { litCtx.fillStyle = lit; rrect(litCtx, dx, dy, d, d, rr); litCtx.fill(); }
          else if (ghost) { this.base.fillStyle = gst; rrect(this.base, dx, dy, d, d, rr); this.base.fill(); }
        }
      });
      if (holes.length) this.cut(c => { c.fillStyle = '#000'; for (const h of holes) c.fillRect(h[0], h[1], h[2], h[3]); }, o);
      return n * adv - p;
    }
    dmWidth(n, o = {}) { const p = o.pitch ?? 2; return n * p * (o.cellW ?? 6) - p; }

    // A free bitmap on the dot grid: rows of '#'/'.' strings. For the projected HUD's sprites.
    sprite(x, y, rows, o = {}) {
      if (this.dark) return;
      const p = o.pitch ?? 2, d = p * (o.dot ?? 0.8), rr = d * 0.3;
      const ctx = this.ctxFor(o); ctx.fillStyle = litStyle(o.color, o.level);
      const cx = o.center ? x - (rows[0].length * p) / 2 : x, cy = o.center ? y - (rows.length * p) / 2 : y;
      rows.forEach((row, r) => [...row].forEach((ch, c) => {
        if (ch === '#') { rrect(ctx, cx + c * p + (p - d) / 2, cy + r * p + (p - d) / 2, d, d, rr); ctx.fill(); }
      }));
    }
    // A chain of dots from one point to another, every `step` units.
    dotLine(x0, y0, x1, y1, o = {}) {
      if (this.dark) return;
      const step = o.step ?? 4, d = o.size ?? 1.6, L = Math.hypot(x1 - x0, y1 - y0), n = Math.max(1, Math.floor(L / step));
      const ctx = this.ctxFor(o); ctx.fillStyle = litStyle(o.color, o.level);
      for (let i = o.skipFirst ? 1 : 0; i <= n; i++) {
        const t = i / n, x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t;
        rrect(ctx, x - d / 2, y - d / 2, d, d, d * 0.3); ctx.fill();
      }
    }
    // A plain dot (for panel plots and markers).
    dot(x, y, o = {}) {
      if (this.dark && !o.ghostOnly) return;
      const d = o.size ?? 1.6, ctx = o.ghostOnly ? this.base : this.ctxFor(o);
      ctx.fillStyle = o.ghostOnly ? ghostStyle(o.color, o.k ?? 1) : litStyle(o.color, o.level);
      rrect(ctx, x - d / 2, y - d / 2, d, d, d * 0.3); ctx.fill();
    }

    // -------- bars: n segments; frac lit from the start. zone(i, n) -> colour (fixed filters).
    bar(x, y, w, h, n, frac, o = {}) {
      const vertical = o.vertical, gap = o.gap ?? 1.2;
      const len = vertical ? h : w, segL = (len - gap * (n - 1)) / n;
      const litN = this.dark ? 0 : Math.round(frac * n);
      const peak = o.peak != null && !this.dark ? Math.min(n - 1, Math.round(o.peak * n) - 1) : -1;
      const litCtx = this.ctxFor(o);
      for (let i = 0; i < n; i++) {
        const color = o.zone ? o.zone(i, n) : (o.color ?? 'zinc');
        let sx, sy, sw, sh;
        if (vertical) { sx = x; sw = w; sh = segL; sy = y + h - (i + 1) * segL - i * gap; }
        else { sy = y; sh = h; sw = segL; sx = x + i * (segL + gap); }
        const tick = o.taper ? (0.55 + 0.45 * (i / (n - 1))) : 1;
        if (!vertical && o.taper) { sy = y + h * (1 - tick); sh = h * tick; }
        if (i < litN || i === peak) {
          litCtx.fillStyle = litStyle(color, i === peak && i >= litN ? 'norm' : o.level);
          litCtx.fillRect(sx, sy, sw, sh);
        } else if (o.ghost !== false) {
          this.base.fillStyle = ghostStyle(color, 1.1); this.base.fillRect(sx, sy, sw, sh);
        }
      }
    }

    // -------- rings: n annular sectors between r0 and r1, starting at a0 (deg, 0 = up, clockwise).
    ring(cx, cy, r0, r1, n, litFn, o = {}) {
      const a0 = (o.start ?? 0), span = o.span ?? 360, gapDeg = o.gapDeg ?? 1.8;
      const step = span / n;
      for (let i = 0; i < n; i++) {
        const st = this.dark ? null : litFn(i, n) || null; // null, or {color, level}
        const color = (st && st.color) || (o.zone ? o.zone(i, n) : o.color) || 'zinc';
        const s0 = (a0 + i * step + gapDeg / 2 - 90) * D, s1 = (a0 + (i + 1) * step - gapDeg / 2 - 90) * D;
        const ctx = st ? this.ctxFor(o) : this.base;
        ctx.fillStyle = st ? litStyle(color, st.level) : ghostStyle(color, 1.1);
        if (!st && o.ghost === false) continue;
        ctx.beginPath(); ctx.arc(cx, cy, r1, s0, s1); ctx.arc(cx, cy, r0, s1, s0, true); ctx.closePath(); ctx.fill();
      }
    }

    // -------- plain lit shapes (tile frames, inverse blocks)
    rectLit(x, y, w, h, o = {}) {
      const gh = o.ghostOnly || this.dark, ctx = gh ? this.base : this.ctxFor(o);
      ctx.fillStyle = gh ? ghostStyle(o.color, o.k ?? 1.2) : litStyle(o.color, o.level);
      if (o.r) { rrect(ctx, x, y, w, h, o.r); ctx.fill(); } else ctx.fillRect(x, y, w, h);
    }
    frameLit(x, y, w, h, t, o = {}) {
      // an outlined cell made of four separate bars with gaps at the corners, as segments would be
      const g = o.gap ?? 1.2;
      this.rectLit(x + g, y, w - 2 * g, t, o); this.rectLit(x + g, y + h - t, w - 2 * g, t, o);
      this.rectLit(x, y + g, t, h - 2 * g, o); this.rectLit(x + w - t, y + g, t, h - 2 * g, o);
    }
    // Cuts a shape out of the lit glass: fn draws it in black. The shape is also kept on the cut mask, so
    // finish() can darken it again after the bloom; otherwise the glow around it fills the letters in.
    cut(fn, o = {}) {
      if (this.dark) return;
      const g = this.ctxFor(o); g.save(); g.globalCompositeOperation = 'destination-out'; fn(g); g.restore();
      if (o.layer === 'proj') return;
      const c = this.cutX; c.save(); fn(c); c.restore(); this.cuts++;
    }
    // Cuts text out of the emissive layer: dark letters in a lit block.
    knockout(x, y, str, o = {}) {
      const size = o.size ?? 13, sx = o.condense ?? 0.8;
      this.tooSmall('inverse legend', size, 12, str, o);
      let w = 0;
      this.cut(ctx => {
        ctx.font = `${o.weight ?? 700} ${size}px ${this.font}`; ctx.letterSpacing = `${(o.track ?? 0.06) * size}px`;
        w = ctx.measureText(str).width * sx;
        const ax = o.align === 'right' ? x - w : o.align === 'center' ? x - w / 2 : x;
        ctx.translate(ax, y); ctx.scale(sx, 1); ctx.fillStyle = '#000'; ctx.fillText(str, 0, 0);
      }, o);
      return w;
    }
    legendWidth(str, o = {}) {
      const b = this.base, size = o.size ?? 13; b.save();
      b.font = `${o.weight ?? 700} ${size}px ${this.font}`; b.letterSpacing = `${(o.track ?? 0.06) * size}px`;
      const w = b.measureText(str).width * (o.condense ?? 0.8); b.restore(); return w;
    }

    // -------- icons
    icon(name, x, y, size, o = {}) {
      const parts = ICONS[name]; if (!parts) return;
      const k = size / 16, lw = (o.stroke ?? 1.5);
      const draw = (ctx, style) => {
        ctx.save(); ctx.translate(x, y); ctx.scale(k, k);
        ctx.fillStyle = style; ctx.strokeStyle = style; ctx.lineWidth = lw; ctx.lineCap = 'butt'; ctx.lineJoin = 'miter';
        for (const part of parts) {
          if (part[0] === 'p') poly(ctx, part[1]);
          else if (part[0] === 'l') { ctx.beginPath(); part[1].forEach((pt, i) => i ? ctx.lineTo(pt[0], pt[1]) : ctx.moveTo(pt[0], pt[1])); ctx.stroke(); }
          else if (part[0] === 'c') { ctx.beginPath(); ctx.arc(part[1], part[2], part[3], (part[4] - 90) * D, (part[5] - 90) * D); ctx.stroke(); }
          else if (part[0] === 'd') { ctx.beginPath(); ctx.arc(part[1], part[2], part[3], 0, Math.PI * 2); ctx.fill(); }
        }
        ctx.restore();
      };
      if (o.ghost !== false && o.layer !== 'proj') draw(this.base, ghostStyle(o.color, 1.3));
      if (o.lit !== false && !this.dark) draw(this.ctxFor(o), litStyle(o.color, o.level));
    }

    // -------- keys
    // A rubber key with a printed legend, and an LED above it when o.led is given.
    key(x, y, w, h, label, o = {}) {
      const b = this.base; b.save();
      b.fillStyle = '#050506'; rrect(b, x - 1, y - 1, w + 2, h + 2.5, 3.2); b.fill();
      const g = b.createLinearGradient(0, y, 0, y + h);
      g.addColorStop(0, o.pressed ? '#1d1f22' : INK.keyHi); g.addColorStop(0.5, INK.key); g.addColorStop(1, '#1b1c1f');
      b.fillStyle = g; rrect(b, x, y, w, h, 2.6); b.fill();
      b.strokeStyle = 'rgba(255,255,255,0.08)'; b.lineWidth = 0.6;
      b.beginPath(); b.moveTo(x + 2.5, y + 0.6); b.lineTo(x + w - 2.5, y + 0.6); b.stroke();
      b.restore();
      if (label) this.silk(x + w / 2, y + h / 2 + (o.size ?? 12) * 0.36, label, { size: o.size ?? 12, align: 'center', color: o.labelColor ?? '#c9ced2' });
      if (o.led) this.led(x + w / 2, y - 4.5, o.led, o.ledOn);
    }
    led(x, y, color, on, r = 1.5) {
      on = on && !this.dark;
      const b = this.base;
      b.save(); b.fillStyle = '#050505'; b.beginPath(); b.arc(x, y, r + 0.7, 0, Math.PI * 2); b.fill();
      const rgb = color === 'green' ? [90, 255, 120] : color === 'red' ? [255, 60, 50] : [255, 170, 40];
      b.fillStyle = rgba(rgb, on ? 1 : 0.13); b.beginPath(); b.arc(x, y, r, 0, Math.PI * 2); b.fill();
      b.restore();
      if (on) { const c = this.glass; c.fillStyle = rgba(rgb, 0.9); c.beginPath(); c.arc(x, y, r, 0, Math.PI * 2); c.fill(); }
    }
    // A lamp key: a translucent cap lit from behind (master WARN / CAUT).
    lampKey(x, y, w, h, l1, l2, color, on) {
      on = on && !this.dark;
      const b = this.base;
      b.save(); b.fillStyle = '#050506'; rrect(b, x - 1.2, y - 1.2, w + 2.4, h + 3, 3); b.fill();
      const rgb = color === 'red' ? [255, 52, 36] : color === 'zinc' ? [70, 246, 204] : [255, 138, 18];
      b.fillStyle = on ? rgba(mix(rgb, [0, 0, 0], 0.22), 1) : rgba(mix(rgb, [20, 20, 20], 0.86), 1);
      rrect(b, x, y, w, h, 2.5); b.fill();
      b.restore();
      if (on) { const c = this.glass; c.fillStyle = rgba(rgb, 0.42); rrect(c, x, y, w, h, 2.5); c.fill(); }
      const ink = on ? (color === 'zinc' ? 'rgba(0,30,22,0.92)' : 'rgba(40,6,0,0.92)') : 'rgba(150,156,160,0.55)';
      if (l2) { this.silkOver(x + w / 2, y + h / 2 - 1.5, l1, ink, 12); this.silkOver(x + w / 2, y + h / 2 + 11, l2, ink, 12); }
      else this.silkOver(x + w / 2, y + h / 2 + 4.3, l1, ink, 12);
    }
    // Ink printed on top of everything (a lamp cap's legend, which must sit over its glow).
    silkOver(x, y, str, color, size) { (this.late ||= []).push({ x, y, str, color, size }); }

    // -------- LCD (the reflective backup strip, and personal devices)
    lcdPanel(x, y, w, h, o = {}) {
      const b = this.base; b.save();
      b.fillStyle = '#000'; rrect(b, x - 1.5, y - 1.5, w + 3, h + 3, 3); b.fill();
      const g = b.createLinearGradient(0, y, 0, y + h);
      g.addColorStop(0, o.backlight ? '#68b3a4' : '#97a386'); g.addColorStop(1, o.backlight ? '#4f978a' : '#7f8a70');
      b.fillStyle = g; rrect(b, x, y, w, h, 2); b.fill(); b.restore();
      if (o.backlight) { const c = this.glass; c.fillStyle = 'rgba(60,220,200,0.045)'; rrect(c, x, y, w, h, 2); c.fill(); }
    }
    // LCD segments are dark, cast a faint shadow on the reflector, and ghost very slightly.
    lcdSeg7(x, y, h, str, o = {}) {
      this.tooSmall('lcd seg7', h, 12, str, o);
      this._drawSegs(x + 0.7, y + 0.8, h, str, 'rgba(0,0,0,0.16)', o);
      this._drawSegs(x, y, h, str, o.ink ?? '#1a2016', o, 'rgba(0,0,0,0.045)');
    }
    _drawSegs(x, y, h, str, style, o, ghost) {
      const w = h * 0.54, t = h * 0.13, gap = h * 0.022, s = Math.tan((o.slant ?? 6) * D), adv = w + h * 0.26;
      const segs = { a: [t / 2, t / 2, w - t / 2, t / 2], g: [t / 2, h / 2, w - t / 2, h / 2], d: [t / 2, h - t / 2, w - t / 2, h - t / 2],
        f: [t / 2, t / 2, t / 2, h / 2], b: [w - t / 2, t / 2, w - t / 2, h / 2], e: [t / 2, h / 2, t / 2, h - t / 2], c: [w - t / 2, h / 2, w - t / 2, h - t / 2] };
      const cells = []; for (const ch of String(str)) { if (ch === ':' && cells.length) { cells[cells.length - 1].colon = true; continue; } if (ch === '.' && cells.length) { cells[cells.length - 1].dp = true; continue; } cells.push({ ch }); }
      const b = this.base;
      cells.forEach((cell, i) => {
        const ox = x + i * adv, on = SEG7[cell.ch] ?? '';
        for (const k of 'abcdefg') {
          const sg = segs[k];
          const pts = segPoly(sg[0], sg[1], sg[2], sg[3], t, gap).map(p => [ox + p[0] + (h - p[1]) * s, y + p[1]]);
          if (on.includes(k)) { b.fillStyle = style; poly(b, pts); } else if (ghost) { b.fillStyle = ghost; poly(b, pts); }
        }
        if (cell.colon) for (const cy of [h * 0.3, h * 0.7]) { b.fillStyle = style; const cx = ox + w + h * 0.1 + (h - cy) * s; b.fillRect(cx, y + cy - t / 2, t, t); }
        if (cell.dp) { b.fillStyle = style; b.fillRect(ox + w + h * 0.06, y + h - t, t, t); }
      });
    }
    lcdText(x, y, str, o = {}) {
      const b = this.base, size = o.size ?? 13;
      this.tooSmall('lcd text', size, 12, str, o);
      for (const [dx, dy, col] of [[0.6, 0.7, 'rgba(0,0,0,0.16)'], [0, 0, o.ink ?? '#1a2016']]) {
        b.save(); b.font = `700 ${size}px ${this.font}`; b.letterSpacing = `${0.08 * size}px`;
        const sx = 0.82, w = b.measureText(str).width * sx;
        const ax = o.align === 'right' ? x - w : o.align === 'center' ? x - w / 2 : x;
        b.translate(ax + dx, y + dy); b.scale(sx, 1); b.fillStyle = col; b.fillText(str, 0, 0); b.restore();
      }
    }

    // -------- the projected layer goes onto the world before the hardware is drawn over it
    _bloom(layer, k) {
      const b = this.base, dpr = this.dpr;
      b.save(); b.setTransform(1, 0, 0, 1, 0, 0); b.globalCompositeOperation = 'lighter';
      b.filter = `blur(${9 * dpr}px)`; b.globalAlpha = 0.42 * k; b.drawImage(layer, 0, 0);
      b.filter = `blur(${2.6 * dpr}px)`; b.globalAlpha = 0.85 * k; b.drawImage(layer, 0, 0);
      b.filter = `blur(${0.7 * dpr}px)`; b.globalAlpha = 0.6; b.drawImage(layer, 0, 0);
      b.filter = 'none'; b.globalAlpha = 1; b.drawImage(layer, 0, 0);
      b.restore();
    }
    projectNow(k = 0.8) {
      this._bloom(this.projC, k);
      const p = this.proj; p.save(); p.setTransform(1, 0, 0, 1, 0, 0); p.clearRect(0, 0, this.projC.width, this.projC.height); p.restore();
    }
    // Brownout: everything lit inside the region keeps only k of its drive (the dimmer bus, not a fade).
    dimRegion(x, y, w, h, k) {
      const g = this.glass; g.save(); g.globalCompositeOperation = 'destination-out';
      g.fillStyle = `rgba(0,0,0,${1 - k})`; g.fillRect(x, y, w, h); g.restore();
    }

    // -------- finishing: mesh, bloom, filaments, glare
    finish(o = {}) {
      const b = this.base, dpr = this.dpr, W = this.canvas.width, H = this.canvas.height;
      // the control grid: a fine mesh in front of the phosphor, seen only where it glows
      if (o.mesh !== false) {
        const tile = document.createElement('canvas'), m = Math.max(3, Math.round(1.6 * dpr));
        tile.width = m; tile.height = m;
        const t = tile.getContext('2d'); t.fillStyle = 'rgba(0,0,0,0.30)'; t.fillRect(0, 0, m, 1); t.fillRect(0, 0, 1, m);
        const g = this.glass; g.save(); g.setTransform(1, 0, 0, 1, 0, 0);
        g.globalCompositeOperation = 'destination-out'; g.fillStyle = g.createPattern(tile, 'repeat'); g.fillRect(0, 0, W, H); g.restore();
      }
      const bloom = (layer, k) => {
        b.save(); b.setTransform(1, 0, 0, 1, 0, 0); b.globalCompositeOperation = 'lighter';
        b.filter = `blur(${9 * dpr}px)`; b.globalAlpha = 0.42 * k; b.drawImage(layer, 0, 0);
        b.filter = `blur(${2.6 * dpr}px)`; b.globalAlpha = 0.85 * k; b.drawImage(layer, 0, 0);
        b.filter = `blur(${0.7 * dpr}px)`; b.globalAlpha = 0.6; b.drawImage(layer, 0, 0);
        b.filter = 'none'; b.globalAlpha = 1; b.drawImage(layer, 0, 0);
        b.restore();
      };
      bloom(this.glassC, o.bloom ?? 1);
      bloom(this.projC, o.projBloom ?? 0.8);
      // cut letters: the bloom has filled them with glow, but the eye sees a dark letter in a lit block,
      // because the glow is in the glass in front of the letter, not in the letter. Darken them again.
      if (this.cuts) {
        const t = document.createElement('canvas'); t.width = W; t.height = H;
        const tc = t.getContext('2d'); tc.drawImage(this.cutC, 0, 0);
        tc.globalCompositeOperation = 'source-in'; tc.fillStyle = INK.cut; tc.fillRect(0, 0, W, H);
        b.save(); b.setTransform(1, 0, 0, 1, 0, 0); b.globalAlpha = o.cutDepth ?? 0.8; b.drawImage(t, 0, 0); b.restore();
      }
      // filaments and glare, clipped to each window
      for (const mo of this.modules) {
        b.save(); b.beginPath();
        if (mo.circle) b.arc(mo.cx, mo.cy, mo.rad, 0, Math.PI * 2); else rrect(b, mo.x, mo.y, mo.w, mo.h, mo.r);
        b.clip();
        for (let i = 1; i <= mo.filaments; i++) {
          const fy = mo.y + (mo.h * i) / (mo.filaments + 1);
          b.strokeStyle = 'rgba(0,0,0,0.30)'; b.lineWidth = 0.45; b.beginPath(); b.moveTo(mo.x, fy); b.lineTo(mo.x + mo.w, fy); b.stroke();
          b.strokeStyle = 'rgba(255,120,60,0.035)'; b.lineWidth = 0.3; b.beginPath(); b.moveTo(mo.x, fy + 0.4); b.lineTo(mo.x + mo.w, fy + 0.4); b.stroke();
        }
        if (mo.glare) {
          const g = b.createLinearGradient(mo.x, mo.y, mo.x + mo.w * 0.6, mo.y + mo.h * 1.2);
          g.addColorStop(0, 'rgba(255,255,255,0.055)'); g.addColorStop(0.35, 'rgba(255,255,255,0.012)'); g.addColorStop(0.36, 'rgba(255,255,255,0)');
          b.fillStyle = g; b.fillRect(mo.x, mo.y, mo.w, mo.h);
        }
        b.restore();
      }
      for (const s of this.late || []) {
        b.save(); b.font = `700 ${s.size}px ${this.font}`; b.letterSpacing = `${0.1 * s.size}px`;
        const sx = 0.82, w = b.measureText(s.str).width * sx;
        b.translate(s.x - w / 2, s.y); b.scale(sx, 1); b.fillStyle = s.color; b.fillText(s.str, 0, 0); b.restore();
      }
    }
  }

  global.VFD = { Scene, PHOSPHOR, LEVEL, INK, ROM, SEG7, SEG14, ICONS };
})(window);
