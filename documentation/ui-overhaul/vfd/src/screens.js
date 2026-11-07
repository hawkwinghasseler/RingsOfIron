// FILAMENT: the parts of the screens beyond the flight HUD (style-vfd.md section 18), shared by every
// spec: the map's plot, the terminal, the slate, setup mode (the menus) and the printed cards. The
// shell's screens and the cards are the same under all three specs, so they are drawn here; each spec's
// own screens are composed in its file (stations.js, context.js, task.js).
const SCR = (() => {
  'use strict';
  const K = KIT, V = VFD;
  const P = 1.5, CW = 9, RH = 13.5; // a character cell at pitch 1.5: 9 wide, 13.5 from row to row (6 x 9 dots)
  const width = (n, p = P) => n * 6 * p - p;

  // ------------------------------------------------------------ the character grid
  // A dot-matrix display is a grid of 5x7 cells, and every cell's unlit dots show faintly through the
  // glass, so the grid is drawn once, as a pattern, and all its text sits on it.
  function cellTile(p, dpr, k = 1) {
    const t = document.createElement('canvas');
    t.width = Math.round(6 * p * dpr); t.height = Math.round(9 * p * dpr);
    const c = t.getContext('2d'), d = p * 0.8 * dpr;
    c.fillStyle = `rgba(${[70, 246, 204].map((v, i) => v + ([150, 160, 155][i] - v) * 0.35).join(',')},${0.075 * 0.8 * k})`;
    for (let r = 0; r < 7; r++) for (let q = 0; q < 5; q++) c.fillRect((q * p + (p - p * 0.8) / 2) * dpr, (r * p + (p - p * 0.8) / 2) * dpr, d, d);
    return t;
  }
  // grid(S, x, y, cols, rows): the cells' ghosts; returns the grid, which places text by cell.
  function grid(S, x, y, cols, rows, o = {}) {
    const p = o.pitch ?? P;
    if (o.ghost !== false && !o.lcd) {
      const b = S.base; b.save();
      b.translate(x, y); b.scale(1 / S.dpr, 1 / S.dpr);
      b.fillStyle = b.createPattern(cellTile(p, S.dpr, o.k), 'repeat');
      b.fillRect(0, 0, cols * 6 * p * S.dpr - p * S.dpr, rows * 9 * p * S.dpr - 2 * p * S.dpr);
      b.restore();
    }
    const g = {
      x, y, cols, rows, p, cw: 6 * p, rh: 9 * p, lcd: o.lcd,
      cx: c => x + c * 6 * p, ry: r => y + r * 9 * p,
      // text at a cell; o.cells pads it to a field, o.align right-aligns it in the field
      text(c, r, str, t = {}) {
        const f = g.lcd ? lcdDm : (S2, xx, yy, s, oo) => S2.dm(xx, yy, s, oo);
        return f(S, g.cx(c), g.ry(r), str, { pitch: p, level: t.level ?? 'norm', ghost: false, cells: t.cells, align: t.align, inverse: t.inverse, color: t.color });
      },
      // a whole row, padded to the grid's width; focus inverts it and puts the cursor in column 0
      row(r, str, t = {}) {
        const s = (t.cursor ? '▶' : ' ') + str;
        g.text(t.c ?? 0, r, s.padEnd(t.cells ?? cols - (t.c ?? 0)), { ...t, cells: t.cells ?? cols - (t.c ?? 0), inverse: t.focus ? [1, (t.cells ?? cols - (t.c ?? 0))] : null, level: t.focus ? 'norm' : t.level });
      },
      // severity pips in two cells: amber, then red
      pips(c, r, sev) {
        const yy = g.ry(r) + 3 * p;
        if (g.lcd) { lcdPip(S, g.cx(c) + 2 * p, yy, sev === 'amber', false); lcdPip(S, g.cx(c + 1) + 2 * p, yy, sev === 'red', true); return; }
        K.pip(S, g.cx(c) + 2 * p, yy, 'amber', sev === 'amber'); K.pip(S, g.cx(c + 1) + 2 * p, yy, 'red', sev === 'red');
      },
    };
    return g;
  }
  // Text built from columns: [[text, cells, 'r'?], ...] -> one padded string.
  function cols(spec) {
    return spec.map(([t, n, a]) => { t = String(t ?? ''); if (t.length > n) t = t.slice(0, n - 1) + '…'; return a === 'r' ? t.padStart(n) : t.padEnd(n); }).join('');
  }

  // ------------------------------------------------------------ the terminal
  // A slab of smoked glass in its own bezel, with page keys along its top edge and action keys along its
  // bottom edge; each key's label sits in the glass beside it, and its cap prints the keyboard key.
  // pages: [{t: 'MARKET', k: 'T', active}, null (a page this place doesn't offer: blank glass), ...]
  // keys:  [{t: 'BUY', k: 'ENTER', active, led, ledOn}, ...]; o.right: keys packed at the right end
  // o.side: the page keys run down the left edge instead, each label in the glass's first column beside
  // its key, so a 480 panel still takes six pages with their full names (INFIRMARY is nine cells); the
  // first key sits o.sideTop below the glass's top, under the title bar, and the page's content starts
  // right of the labels (the returned left).
  const KEY_H = 24, KEY_GAP = 5, SIDE_W = 44;
  function terminal(S, x, y, w, h, o = {}) {
    const side = !!(o.pages && o.side);
    const top = o.pages && !side ? KEY_H + KEY_GAP + 4 : 0, bot = o.keys ? KEY_H + KEY_GAP + 4 : 0, left = side ? SIDE_W + KEY_GAP + 4 : 0;
    K.plate(S, x, y, w, h, { pad: 7 });
    const gx = x + left, gy = y + top, gw = w - left, gh = h - top - bot;
    S.module(gx, gy, gw, gh, { filaments: o.filaments ?? Math.max(2, Math.round(gh / 120)) });
    if (o.pages && !side) keyStrip(S, gx + 8, y, gw - 16, o.pages, { slots: o.pageSlots, labelY: gy + 6, above: true });
    let lx = gx + 8;
    if (side) lx = sideKeys(S, x, gx + 8, gy + (o.sideTop ?? 8), o.pages, o.sidePitch ?? 40);
    if (o.keys) keyStrip(S, gx + 8, gy + gh + KEY_GAP + 4, gw - 16, o.keys, { slots: o.keySlots, labelY: gy + gh - 7 * P - 6, right: o.right });
    return { x: gx, y: gy, w: gw, h: gh, top: gy + (o.pages && !side ? 7 * P + 14 : 8), bottom: gy + gh - (o.keys ? 7 * P + 12 : 8), left: lx };
  }
  // Page keys down an edge: the key in the bezel, its label lit beside it in the glass (inverse while its
  // page is up), a ghost tick between labels. Returns where the content right of the labels starts.
  function sideKeys(S, kx, lx, y, items, pitch) {
    const cells = Math.max(...items.map(it => (it && it.t ? it.t.length : 0)));
    items.forEach((it, i) => {
      const ky = y + i * pitch, ty = ky + (KEY_H - 7 * P) / 2;
      S.key(kx, ky, SIDE_W, KEY_H, it ? it.k ?? '' : '', { size: 12 });
      if (it && it.t) S.dm(lx, ty, it.t, { pitch: P, level: it.active ? 'full' : 'norm', inverse: it.active ? [0, it.t.length] : null, ghost: false });
      if (i) S.rectLit(lx, ky - (pitch - KEY_H) / 2, width(cells), 0.8, { ghostOnly: true, k: 1.5 });
    });
    const cx = lx + width(cells) + 10;
    S.rectLit(cx, y - 4, 0.8, items.length * pitch, { ghostOnly: true, k: 2.2 });
    return cx + 12;
  }
  // A row of rubber keys with their labels in the glass row next to them.
  function keyStrip(S, x, y, w, items, o = {}) {
    const n = o.slots ?? items.length, slot = w / n;
    items.forEach((it, i) => {
      const sx = o.right ? x + w - (items.length - i) * slot : x + i * slot;
      const kw = Math.min(slot - 8, it && it.wide ? slot - 8 : 64), kx = sx + (slot - kw) / 2;
      S.key(kx, y, kw, KEY_H, it ? it.k ?? '' : '', { size: 12, led: it && it.led, ledOn: it && it.ledOn });
      if (!it || !it.t) return; // blank glass: a key whose page this place doesn't offer
      const cells = it.t.length, lw = width(cells), lx = sx + (slot - lw) / 2;
      S.dm(lx, o.labelY, it.t, { pitch: P, level: it.active ? 'full' : it.dim ? 'dim' : 'norm', inverse: it.active ? [0, cells] : null, ghost: false });
    });
    for (let i = 1; i < n; i++) {
      const tx = o.right ? x + w - (n - i) * slot : x + i * slot;
      S.rectLit(tx - 0.4, o.labelY - 1, 0.8, 7 * P + 2, { ghostOnly: true, k: 1.5 });
    }
  }
  // A key cap and its word, for a footer drawn in a row (as the menus' legend of keys).
  function capWord(S, x, y, cap, word, o = {}) {
    let cx = x;
    for (const c of [].concat(cap)) cx += K.keycap(S, cx, y, c, { h: 18 }) + 3;
    cx += 3;
    return cx + S.dm(cx, y + 4, word, { pitch: P, level: o.level ?? 'norm', ghost: false }) + 14;
  }
  // PG 1/3 with its arrows lit where there is more.
  function pager(S, x, y, page, of) {
    let cx = x + S.legend(x, y + 10, 'PG', { size: 13, level: 'dim' }) + 6;
    cx += S.dm(cx, y, `${page}/${of}`, { pitch: P, level: 'norm', ghost: false }) + 8;
    cx += S.dm(cx, y, '▲', { pitch: P, level: page > 1 ? 'norm' : 'ghost', ghost: true }) + 5;
    S.dm(cx, y, '▼', { pitch: P, level: page < of ? 'norm' : 'ghost', ghost: true });
  }
  // A seven-segment figure with its unit legend; returns the end x.
  function figure(S, x, y, h, value, unit, o = {}) {
    let cx = x;
    if (o.label) cx += S.legend(cx, y + h, o.label, { size: 13, level: 'dim' }) + 7;
    cx += S.seg7(cx, y, h, value, { cells: o.cells, level: o.level ?? 'norm', color: o.color }) + 5;
    if (unit) cx += S.legend(cx, y + h, unit, { size: 13, level: 'dim', color: o.color });
    return cx;
  }
  // A choice: the value between ◀ and ▶, stepped with Left and Right.
  function choice(S, x, y, value, cells, o = {}) {
    S.dm(x, y, '◀', { pitch: P, level: 'dim', ghost: false });
    S.dm(x + CW * 2, y, value, { pitch: P, cells, level: o.level ?? 'norm', ghost: o.ghost ?? true, inverse: o.focus ? [0, cells] : null });
    S.dm(x + CW * (cells + 3), y, '▶', { pitch: P, level: 'dim', ghost: false });
    return x + CW * (cells + 4);
  }
  // A text field: fixed cells, its ghosts showing, and a block cursor (lit: the blink's on phase).
  function entry(S, x, y, text, cells, o = {}) {
    S.dm(x, y, text, { pitch: P, cells, level: 'norm', ghost: true });
    if (o.cursor !== false) S.rectLit(x + text.length * CW - 0.4, y - 1, CW - 1.2, 9 * P - 2.5, { level: 'full' });
    S.rectLit(x - 3, y + 7 * P + 2.5, cells * CW + 4, 0.8, { ghostOnly: true, k: 3 });
  }

  // ------------------------------------------------------------ the plot
  // A graphic dot panel at pitch 3: every dot of it ghosts through the glass, and everything on it snaps
  // to its dots. km(x, y) maps the map's metres to the panel; the drawing calls light dots.
  function plot(S, x, y, w, h, view, o = {}) {
    const p = o.pitch ?? 3, dpr = S.dpr;
    S.module(x, y, w, h, { filaments: Math.round(h / 110), radius: 3 });
    const ox = x + ((w % p) / 2) + p / 2, oy = y + ((h % p) / 2) + p / 2, nx = Math.floor(w / p), ny = Math.floor(h / p);
    // the ghost dots, as a pattern
    const t = document.createElement('canvas'); t.width = Math.round(p * dpr); t.height = Math.round(p * dpr);
    const tc = t.getContext('2d'); tc.fillStyle = 'rgba(115,215,190,0.085)';
    const d = p * 0.62 * dpr; tc.fillRect((p * dpr - d) / 2, (p * dpr - d) / 2, d, d);
    const b = S.base; b.save(); b.beginPath(); b.rect(x + 1, y + 1, w - 2, h - 2); b.clip();
    b.translate(ox - p / 2, oy - p / 2); b.scale(1 / dpr, 1 / dpr); b.fillStyle = b.createPattern(t, 'repeat');
    b.fillRect(0, 0, nx * p * dpr, ny * p * dpr); b.restore();
    const lit = new Map();
    const P2 = {
      p, x, y, w, h,
      // grid index of a point, and the point of an index
      gi: (px, py) => [Math.round((px - ox) / p), Math.round((py - oy) / p)],
      at: (i, j) => [ox + i * p, oy + j * p],
      inside: (i, j) => i >= 0 && j >= 0 && i < nx && j < ny,
      km: (ex, ey) => [view.cx + ex / view.kmPerUnit, view.cy - ey / view.kmPerUnit],
      // light one panel dot (the brightest call wins)
      on(i, j, level = 'norm') {
        if (!P2.inside(i, j)) return;
        const k = i + ',' + j, rank = { dim: 1, norm: 2, full: 3 }[level];
        if ((lit.get(k) || 0) < rank) lit.set(k, rank);
      },
      // a dot chain through screen points: every `every`th dot of the path lit
      chain(pts, level = 'norm', every = 1) {
        const seen = []; let last = null;
        for (let s = 0; s < pts.length - 1; s++) {
          const [x0, y0] = pts[s], [x1, y1] = pts[s + 1], L = Math.hypot(x1 - x0, y1 - y0), n = Math.max(1, Math.ceil(L / (p * 0.5)));
          for (let q = 0; q <= n; q++) {
            const g = P2.gi(x0 + (x1 - x0) * q / n, y0 + (y1 - y0) * q / n);
            if (last && g[0] === last[0] && g[1] === last[1]) continue;
            seen.push(g); last = g;
          }
        }
        seen.forEach((g, k) => { if (k % every === 0) P2.on(g[0], g[1], level); });
      },
      // a circle or ellipse arc in panel units (centre, radii, rotation, from/to in radians)
      ellipse(cx, cy, rx, ry, rot, a0, a1, level, every) {
        const pts = [], n = Math.max(24, Math.ceil(Math.max(rx, ry) * Math.abs(a1 - a0) / p));
        for (let q = 0; q <= n; q++) {
          const a = a0 + (a1 - a0) * q / n, ex = Math.cos(a) * rx, ey = Math.sin(a) * ry;
          pts.push([cx + ex * Math.cos(rot) - ey * Math.sin(rot), cy + ex * Math.sin(rot) + ey * Math.cos(rot)]);
        }
        P2.chain(pts, level, every);
      },
      // a body: an ordered-dither disc with a solid limb. A body drawn large (past 60 units) is the ground
      // under the orbits, not a mark: a 25% dither and its limb at L1, so the orbits stand out over it.
      disc(cx, cy, r) {
        const [ci, cj] = P2.gi(cx, cy), R = Math.ceil(r / p) + 1, big = r > 60;
        for (let i = ci - R; i <= ci + R; i++) for (let j = cj - R; j <= cj + R; j++) {
          const [px, py] = P2.at(i, j), dd = Math.hypot(px - cx, py - cy);
          if (dd <= r && dd > r - p * 1.05) P2.on(i, j, big ? 'dim' : 'norm');
          else if (dd <= r - p * 1.05 && (big ? i % 2 === 0 && j % 2 === 0 : (i + j) % 2 === 0)) P2.on(i, j, 'dim');
        }
      },
      // keep a rectangle clear (a label's ground): no dot lit in it, its ghosts still show
      clear(cx0, cy0, cw, ch) {
        for (const k of [...lit.keys()]) {
          const [i, j] = k.split(',').map(Number), [px, py] = P2.at(i, j);
          if (px > cx0 && px < cx0 + cw && py > cy0 && py < cy0 + ch) lit.delete(k);
        }
      },
      // a bitmap sprite centred on a point, snapped to the panel's dots
      sprite(rows, px, py, level = 'norm') {
        const [ci, cj] = P2.gi(px, py), hw = Math.floor(rows[0].length / 2), hh = Math.floor(rows.length / 2);
        rows.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === '#') P2.on(ci - hw + c, cj - hh + r, level); }));
      },
      // flush the lit dots onto the glass (one bloom with everything else)
      done() {
        const g = S.glass, dd = p * 0.8;
        for (const [k, rank] of lit) {
          const [i, j] = k.split(',').map(Number), [px, py] = P2.at(i, j);
          S.dot(px, py, { size: dd, level: ['', 'dim', 'norm', 'full'][rank] });
        }
        void g;
      },
    };
    return P2;
  }
  // Plot sprites (on the panel's own dots, pitch 3)
  const PS = {
    ship: ['.###.', '#...#', '#...#', '#...#', '.###.'],
    station: ['#####', '#...#', '#.#.#', '#...#', '#####'],
    friendly: ['##.##', '#...#', '.....', '#...#', '##.##'],
    neutral: ['..#..', '.#.#.', '#...#', '.#.#.', '..#..'],
    hostile: ['#...#', '##.##', '.....', '##.##', '#...#'],
    node: ['.###.', '#...#', '#...#', '#...#', '.###.'],
    nodeSel: ['.###.', '#####', '#####', '#####', '.###.'],
    ap: ['..#..', '.###.', '#####'],
    pe: ['#####', '.###.', '..#..'],
    impact: ['#...#', '.#.#.', '..#..', '.#.#.', '#...#'],
    far: ['##', '##'],
    waypoint: ['..#..', '.#.#.', '#...#', '.#.#.', '..#..'],
    arrowU: ['..#..', '.###.', '#.#.#', '..#..', '..#..'],
    arrowD: ['..#..', '..#..', '#.#.#', '.###.', '..#..'],
    arrowL: ['..#..', '.#...', '#####', '.#...', '..#..'],
    arrowR: ['..#..', '...#.', '#####', '...#.', '..#..'],
  };

  // ------------------------------------------------------------ the slate (the captain's LCD)
  // A graphic LCD in a dark rubber housing: dark dots on grey-green with a faint shadow on the reflector;
  // the backlight (dim teal) is on for 3 s after a key press.
  function slate(S, x, y, w, h, o = {}) {
    const b = S.base, r = 9;
    b.save();
    b.shadowColor = 'rgba(0,0,0,0.8)'; b.shadowBlur = 16; b.shadowOffsetY = 4;
    const g = b.createLinearGradient(0, y, 0, y + h);
    g.addColorStop(0, '#2a2b2d'); g.addColorStop(1, '#161718');
    b.fillStyle = g; b.beginPath(); b.roundRect(x, y, w, h, r); b.fill();
    b.shadowColor = 'transparent';
    b.strokeStyle = '#3a3c3f'; b.lineWidth = 0.8; b.beginPath(); b.roundRect(x + 0.4, y + 0.4, w - 0.8, h - 0.8, r); b.stroke();
    b.restore();
    const top = o.top ?? 0; // room over the glass for keys along the housing's top edge
    const lx = x + (o.keysLeft ? 78 : 14), ly = y + 26 + top, lw = w - (o.keysLeft ? 78 : 14) - 14, lh = h - 26 - top - (o.keysBottom ? 52 : 16);
    S.silk(x + 14, y + 17, o.brand ?? 'SLATE', { size: 12, color: '#7b8186' });
    if (o.owner) S.silk(x + w - 14, y + 17, o.owner, { size: 12, color: '#7b8186', align: 'right' });
    S.lcdPanel(lx, ly, lw, lh, { backlight: o.backlight });
    // side keys: rubber keys down the left edge, their labels in the LCD's first column
    const keys = o.keysLeft || [];
    keys.forEach((it, i) => {
      const ky = ly + (o.keyTop ?? 8) + i * (o.keyPitch ?? 36);
      S.key(x + 14, ky, 52, KEY_H, it.k ?? '', { size: 12 });
      if (it.t) lcdDm(S, lx + 6, ky + 6, it.t, { inverse: it.active ? [0, it.t.length] : null, level: it.active ? 'full' : 'norm', cells: it.t.length, backlight: o.backlight });
    });
    return { x: lx, y: ly, w: lw, h: lh };
  }
  // LCD dot matrix: dark dots and their shadow; inverse is a dark block with the dots left clear.
  function lcdDm(S, x, y, str, o = {}) {
    const p = o.pitch ?? P, d = p * 0.86, b = S.base;
    const chars = [...String(str).toUpperCase()];
    const n = o.cells ?? chars.length;
    while (chars.length < n) (o.align === 'right' ? chars.unshift(' ') : chars.push(' '));
    if (chars.length > n) chars.length = n;
    const ink = o.level === 'dim' ? 'rgba(26,32,22,0.5)' : '#1a2016';
    const inv = o.inverse;
    if (inv && inv[1] > inv[0]) {
      b.fillStyle = 'rgba(0,0,0,0.16)'; b.fillRect(x + inv[0] * 6 * p - p * 0.5 + 0.7, y - p + 0.8, (inv[1] - inv[0]) * 6 * p, 9 * p);
      b.fillStyle = '#1a2016'; b.fillRect(x + inv[0] * 6 * p - p * 0.5, y - p, (inv[1] - inv[0]) * 6 * p, 9 * p);
    }
    for (const pass of [0, 1]) chars.forEach((ch, i) => {
      const g = V.ROM[ch] ?? V.ROM['?'], flip = inv && i >= inv[0] && i < inv[1];
      for (let r = 0; r < 7; r++) for (let c = 0; c < 5; c++) {
        if (g[r][c] !== '#') continue;
        const dx = x + i * 6 * p + c * p + (p - d) / 2, dy = y + r * p + (p - d) / 2;
        if (flip) { if (pass) { b.fillStyle = o.backlight ? '#5aa596' : '#8e9a7e'; b.fillRect(dx, dy, d, d); } continue; }
        if (pass === 0) { b.fillStyle = 'rgba(0,0,0,0.13)'; b.fillRect(dx + 0.6, dy + 0.7, d, d); }
        else { b.fillStyle = ink; b.fillRect(dx, dy, d, d); }
      }
    });
    return n * 6 * p - p;
  }
  // On the LCD a pip can't be amber or red: a caution is an outlined square, a warning a solid one.
  function lcdPip(S, x, y, on, warning) {
    const b = S.base, r = 2;
    b.save();
    if (!on) { b.strokeStyle = 'rgba(26,32,22,0.12)'; b.lineWidth = 0.6; b.strokeRect(x - r, y - r, r * 2, r * 2); b.restore(); return; }
    b.fillStyle = 'rgba(0,0,0,0.14)'; b.fillRect(x - r + 0.6, y - r + 0.7, r * 2, r * 2);
    if (warning) { b.fillStyle = '#1a2016'; b.fillRect(x - r, y - r, r * 2, r * 2); }
    else { b.strokeStyle = '#1a2016'; b.lineWidth = 1; b.strokeRect(x - r + 0.5, y - r + 0.5, r * 2 - 1, r * 2 - 1); }
    b.restore();
  }

  // ------------------------------------------------------------ printed cards (out of fiction)
  const CARD = '#e9e6dc', INKC = '#1c1c1c';
  function card(S, x, y, w, h) {
    const b = S.base; b.save();
    b.shadowColor = 'rgba(0,0,0,0.7)'; b.shadowBlur = 22; b.shadowOffsetY = 6;
    b.fillStyle = CARD; b.beginPath(); b.roundRect(x, y, w, h, 7); b.fill();
    b.shadowColor = 'transparent';
    // the laminate: a faint sheen across the top
    const g = b.createLinearGradient(x, y, x + w * 0.4, y + h);
    g.addColorStop(0, 'rgba(255,255,255,0.35)'); g.addColorStop(0.3, 'rgba(255,255,255,0.04)'); g.addColorStop(1, 'rgba(0,0,0,0.03)');
    b.fillStyle = g; b.beginPath(); b.roundRect(x, y, w, h, 7); b.fill();
    b.restore();
  }
  function print(S, x, y, str, o = {}) {
    const b = S.base, size = o.size ?? 13; b.save();
    b.font = `${o.weight ?? 600} ${size}px Inter`; b.fillStyle = o.color ?? INKC; b.letterSpacing = `${(o.track ?? 0.02) * size}px`;
    const w = b.measureText(str).width;
    b.fillText(str, o.align === 'right' ? x - w : o.align === 'center' ? x - w / 2 : x, y); b.restore();
    return w;
  }
  // A printed key cap: an outlined rounded key with its legend. Returns its width.
  function printedKey(S, x, y, label, o = {}) {
    const b = S.base, h = o.h ?? 17, size = o.size ?? 11;
    b.save(); b.font = `700 ${size}px Inter`;
    const w = Math.max(h, b.measureText(label).width + 9);
    b.fillStyle = '#d8d4c8'; b.beginPath(); b.roundRect(x, y, w, h, 3); b.fill();
    b.strokeStyle = INKC; b.lineWidth = 1; b.beginPath(); b.roundRect(x + 0.5, y + 0.5, w - 1, h - 1, 3); b.stroke();
    b.fillStyle = INKC; b.fillText(label, x + (w - b.measureText(label).width) / 2, y + h / 2 + size * 0.36);
    b.restore();
    return w;
  }

  // ------------------------------------------------------------ setup mode: the dark cockpit
  // The outside view through the window between the top and bottom racks: the stars and the lit limb of
  // the planet below, as the game's menu draws them. The racks are dark: nothing aboard is live yet.
  function cockpit(S, o = {}) {
    K.space(S, { seed: o.seed ?? 5, stars: 620 });
    const b = S.base, W = S.W, H = S.H, r = W * 0.9, cx = W / 2, cy = H + r * 0.82;
    b.save();
    for (let k = 0; k < 6; k++) {
      b.strokeStyle = `rgba(64,128,230,${0.10 - k * 0.015})`; b.lineWidth = 3;
      b.beginPath(); b.arc(cx, cy, r + k * 3, Math.PI, Math.PI * 2); b.stroke();
    }
    const g = b.createRadialGradient(cx, cy, r * 0.9, cx, cy, r);
    g.addColorStop(0, 'rgba(6,10,20,0)'); g.addColorStop(1, 'rgba(30,60,120,0.10)');
    b.fillStyle = g; b.beginPath(); b.arc(cx, cy, r, 0, Math.PI * 2); b.fill();
    b.restore();
    S.rack(-8, -8, W + 16, 40, { radius: 6, shadowY: 2 });
    S.rack(-8, H - 46, W + 16, 54, { radius: 6 });
  }
  // A FILAMENT screen drawn under the pause: the game as it stood, every tube and the world on the dimmed bus.
  // o.dark draws it unpowered: every tube off, every ghost showing (a ship lost).
  async function under(S, id, k = 0.45, o = {}) {
    const c = document.createElement('canvas'), make = K.scene;
    if (o.dark) K.scene = (cv, W, H) => { const S2 = make(cv, W, H); S2.dark = true; return S2; };
    try { await SCREENS[id](c); } finally { K.scene = make; }
    S.image(c, 0, 0, S.W, S.H, { filter: `brightness(${k})` });
  }

  return { P, CW, RH, width, grid, cols, terminal, keyStrip, sideKeys, capWord, pager, figure, choice, entry, plot, PS, slate, lcdDm, lcdPip, card, print, printedKey, cockpit, under, KEY_H };
})();

// ====================================================================== setup mode: the shell's menus
// What the menu look can build (Variants.md, "The menus"): the shell keeps each menu's layout, buttons
// and keys; the look gives the skin's styles, the backdrop and the pause backdrop, and dresses each menu
// once it is built. So every menu here keeps the classic one's rows and order, in FILAMENT's parts.
(() => {
  'use strict';
  const K = KIT, s = SCR;
  // the skin's styles
  const st = {
    display: (S, cx, y, str) => S.seg14(cx - S.seg14Width(22, [...str].length) / 2, y, 22, str, { level: 'full' }),
    heading: (S, cx, y, str) => S.seg14(cx - S.seg14Width(16, [...str].length) / 2, y, 16, str, { level: 'norm' }),
    title: (S, x, y, str) => S.legend(x, y + 11, str, { size: 13, level: 'full', ghost: false }),
    text: (S, x, y, str, o = {}) => S.dm(x, y, str, { pitch: 1.5, level: o.level ?? 'norm', color: o.color, ghost: true, cells: o.cells, align: o.align }),
  };
  const centre = (S, cx, y, str, o = {}) => st.text(S, cx - s.width([...str].length) / 2, y, str, o);
  // the panel drawable: smoked glass in a bezel of its own
  function panel(S, x, y, w, h) {
    K.plate(S, x, y, w, h, { pad: 7 });
    S.module(x, y, w, h, { filaments: Math.max(2, Math.round(h / 120)) });
  }
  // the menu button: a field of cells at pitch 2 with its word after the cursor's column. Focus inverts
  // the field and lights the cursor; a disabled one is dim, and the focus steps over it.
  const BP = 2, BC = 12;
  function button(S, x, y, cells, str, state) {
    S.dm(x, y, ' '.repeat(cells), { pitch: BP, ghost: true });
    if (state === 'focus') {
      S.dm(x, y, '▶', { pitch: BP, level: 'full', ghost: false });
      S.dm(x, y, (' ' + str).padEnd(cells), { pitch: BP, level: 'norm', ghost: false, inverse: [1, cells] });
    } else {
      S.dm(x + BC, y, str, { pitch: BP, level: state === 'disabled' ? 'dim' : 'norm', ghost: false });
    }
    if (state === 'checked') S.frameLit(x + BC - 4, y - 5, (cells - 1) * BC + 4, 9 * BP + 6, 1, { level: 'norm' });
    return x + cells * BC - BP;
  }
  const bw = cells => cells * BC - BP;
  // a check box: a lit block when on, a ghost frame when off, then its words
  function check(S, x, y, on, str) {
    if (on) S.rectLit(x, y + 1, 9, 9, { level: 'norm', r: 0.8 });
    else S.frameLit(x, y + 1, 9, 9, 1, { ghostOnly: true, k: 3 });
    return st.text(S, x + 18, y, str);
  }
  // the legend of keys in the bottom rack's window (the look draws it in its backdrop, for the menu it dressed last)
  function legendRack(S, items, o = {}) {
    S.module(16, S.H - 39, S.W - 32, 26, { filaments: 1 });
    let x = 28;
    for (const [caps, word] of items) x = s.capWord(S, x, S.H - 35, caps, word, { level: 'norm' });
    if (o.right) st.text(S, S.W - 28 - s.width(o.right.length), S.H - 30.5, o.right, { level: 'dim' });
  }
  // over the paused game the legend is a slab of its own under the menu
  function legendSlab(S, cy, items) {
    let w = 0;
    for (const [caps, word] of items) w += caps.reduce((a, c) => a + Math.max(18, S.legendWidth(c, { size: 12, weight: 600, condense: 0.86, track: 0.1 }) + 7) + 3, 0) + 3 + s.width(word.length) + 14;
    w -= 14;
    const x = 640 - w / 2;
    K.slab(S, x - 10, cy, w + 20, 26, { pad: 3, filaments: 1, glare: false });
    let cx = x;
    for (const [caps, word] of items) cx = s.capWord(S, cx, cy + 4, caps, word);
  }
  // a scroll pane's bar: a column of segments, the lit run the part of the list in view
  function scrollBar(S, x, y, h, n, from, to) {
    const gap = 2, sh = (h - gap * (n - 1)) / n;
    for (let i = 0; i < n; i++) {
      const on = i >= Math.round(from * n) && i < Math.round(to * n);
      S.rectLit(x, y + i * (sh + gap), 5, sh, on ? { level: 'dim' } : { ghostOnly: true, k: 1.6 });
    }
  }
  // the backdrop behind the main menu: the cockpit with nothing aboard live but the computer
  function backdrop(S) {
    s.cockpit(S);
    // the top rack's windows, unpowered: every ghost shows what each would hold
    S.dark = true;
    S.module(16, 6, 300, 20, { filaments: 1 });
    for (let i = 0; i < 6; i++) S.legend(26 + i * 48, 21, ['WARN', 'CAUT', 'FIRE', 'HULL', 'AIR', 'POWER'][i], { size: 12 });
    S.module(964, 6, 300, 20, { filaments: 1 });
    S.seg7(978, 9, 14, '88:88:88', { cells: 6 });
    S.seg7(1120, 9, 14, '888.8', { cells: 4 });
    S.dark = false;
    // the computer's own window, the one live tube: SETUP
    S.module(560, 6, 160, 20, { filaments: 1 });
    S.seg14(640 - S.seg14Width(12, 5) / 2, 10, 12, 'SETUP', { level: 'norm' });
  }

  // ------------------------------------------------------------------ the main menu
  SCREENS['sh-main'] = async (canvas) => {
    const S = K.scene(canvas);
    backdrop(S);
    const w = 452, h = 334, x = 640 - w / 2, y = 206, cells = 21, fx = 640 - bw(cells) / 2;
    panel(S, x, y, w, h);
    st.display(S, 640, y + 24, 'RINGS OF IRON');
    centre(S, 640, y + 56, 'A NEWTONIAN SHIP SIM', { level: 'dim' });
    let ry = y + 92;
    button(S, fx, ry, cells, 'CONTINUE', 'focus'); ry += 26;
    centre(S, 640, ry, 'QUICK SAVE   DAY 3 14:22   CORVETTE   18,450 CR', { level: 'dim' }); ry += 26;
    for (const t of ['NEW GAME', 'FIRST PLAY (GUIDED)', 'LOAD', 'SETTINGS', 'QUIT']) { button(S, fx, ry, cells, t); ry += 30; }
    legendRack(S, [[['↑', '↓'], 'CHOOSE'], [['ENTER'], 'SELECT']], { right: '0.47.0 · 8ECF3E01' });
    S.finish();
  };

  // ------------------------------------------------------------------ load, over the paused game
  // The pause backdrop puts every tube on the dimmer bus and the world under a dark scrim; the menu is
  // the only thing at full drive.
  SCREENS['sh-load'] = async (canvas) => {
    const S = K.scene(canvas);
    await s.under(S, 'st-helm', 0.42);
    const w = 608, h = 560, x = 640 - w / 2, y = 96;
    panel(S, x, y, w, h);
    st.heading(S, 640, y + 24, 'LOAD GAME');
    // the save list: a scroll pane 560 wide; its bar is a column of segments, the lit run the part in view
    const lx = x + 24, ly = y + 70, lw = 560, lh = 420;
    const saves = [
      ['QUICK SAVE', 'DAY 3 14:22   CORVETTE   18,450 CR', '2026-11-05 21:14'],
      ['AUTOSAVE', 'DAY 3 09:05   CORVETTE   18,450 CR', '2026-11-05 20:51'],
      ['BEFORE SHACKLETON', 'DAY 2 22:40   CORVETTE   12,200 CR', '2026-11-04 23:02'],
      ['THE KESTREL ESCORT', 'DAY 2 04:16   CORVETTE   9,860 CR', '2026-11-04 21:40'],
      ['PATROL START', 'DAY 0 06:00   CORVETTE   5,000 CR', '2026-11-03 19:30'],
      ['COMBINE RUN', 'DAY 11 03:12   HAULER   41,980 CR', '2026-10-29 22:17'],
      ['FIRST PLAY', 'DAY 0 00:41   CORVETTE   5,000 CR', '2026-10-28 18:05'],
      ['ICE HAULING', 'DAY 6 18:02   HAULER   22,300 CR', '2026-10-27 20:44'],
    ];
    saves.forEach((sv, i) => {
      const ry = ly + 6 + i * 51;
      st.text(S, lx + 4, ry, sv[0], { level: i === 0 ? 'full' : 'norm' });
      st.text(S, lx + 4, ry + 15, sv[1], { level: 'dim' });
      st.text(S, lx + 4, ry + 30, sv[2], { level: 'dim' });
      const bx = lx + lw - 24 - bw(6) - 6 - bw(8);
      button(S, bx, ry + 12, 6, 'LOAD', i === 0 ? 'focus' : 'norm');
      button(S, bx + bw(6) + 6, ry + 12, 8, 'DELETE');
      if (i < saves.length - 1) S.rectLit(lx + 4, ry + 45, lw - 36, 0.7, { ghostOnly: true, k: 2 });
    });
    scrollBar(S, lx + lw - 8, ly, lh - 8, 14, 0, 0.82);
    button(S, 640 - bw(6) / 2, ly + lh + 12, 6, 'BACK');
    legendSlab(S, y + h + 16, [[['↑', '↓'], 'CHOOSE'], [['ENTER'], 'SELECT'], [['ESC'], 'BACK']]);
    S.finish();
  };

  // ------------------------------------------------------------------ settings
  SCREENS['sh-settings'] = async (canvas) => {
    const S = K.scene(canvas);
    backdrop(S);
    const w = 592, h = 728, x = 640 - w / 2, y = 40, x0 = x + 24, c2 = x0 + 280, c3 = c2 + 214;
    panel(S, x, y, w, h);
    st.heading(S, 640, y + 20, 'SETTINGS');
    let ry = y + 56;
    const sect = (t, top) => { if (top) ry += 10; st.title(S, x0, ry, t); ry += 22; };
    const vol = (t, f) => {
      st.text(S, x0, ry, t, { level: 'dim' });
      S.bar(c2, ry + 1.5, 200, 8, 20, f, { level: 'norm', gap: 1.6 });
      st.text(S, c3, ry, Math.round(f * 100) + '%', { cells: 4, align: 'right' });
      ry += 22;
    };
    const pick = (t, v, cells, o = {}) => {
      st.text(S, x0, ry, t, { level: 'dim' });
      const e = s.choice(S, c2, ry, v, cells, o);
      if (o.note) st.text(S, e + 10, ry, o.note, { level: 'dim' });
      ry += 23;
    };
    sect('SOUND');
    vol('MASTER VOLUME', 0.8); vol('EFFECTS', 1.0); vol('AMBIENCE', 0.6);
    pick('HRTF (3D SOUND IN HEADPHONES)', 'AUTO', 9);
    sect('DISPLAY', true);
    check(S, x0, ry, true, 'FULLSCREEN'); ry += 22;
    pick('WINDOW SIZE', '1920 X 1200', 12);
    pick('UI SCALE', 'AUTO', 12, { note: 'NOW 150%' });
    sect('INTERFACE', true);
    pick('HUD', '90S VFD: TASK', 20, { focus: true });
    sect('CONTROLS', true);
    check(S, x0, ry, false, 'GAMEPAD'); ry += 22;
    check(S, x0, ry, true, 'WALKTHROUGH AT THE START OF A NEW GAME'); ry += 26;
    // the key list: a scroll pane 520 wide, 240 tall
    const kl = [['MAIN ENGINE FORWARD / WALK UP', 'W'], ['MAIN ENGINE BACK / WALK DOWN', 'S'], ['TURN LEFT / WALK LEFT', 'A'],
      ['TURN RIGHT / WALK RIGHT', 'D'], ['STRAFE LEFT', 'Z'], ['STRAFE RIGHT', 'C'], ['ROTATION ASSIST', 'X'], ['FIRE GUNS', 'SPACE'],
      ['LAUNCH MISSILE', 'R'], ['DECOY / FORCE DOOR', 'F'], ['RAISE / LOWER SHIELDS', '9']];
    kl.forEach(([t, k], i) => {
      const yy = ry + i * 21.5;
      st.text(S, x0, yy + 2, t, { level: 'dim' });
      button(S, x0 + 520 - 18 - bw(16), yy, 16, k);
    });
    scrollBar(S, x0 + 520 - 7, ry, 236, 14, 0, 0.24);
    ry += 240 + 12;
    let bx = 640 - (bw(11) + bw(6) + bw(5) + 20) / 2;
    bx = button(S, bx, ry, 11, 'RESET KEYS') + 10;
    bx = button(S, bx, ry, 6, 'APPLY') + 10;
    button(S, bx, ry, 5, 'BACK');
    // the legend, in the rack's window either side of the panel
    S.module(16, S.H - 39, x - 30, 26, { filaments: 1 });
    S.module(x + w + 14, S.H - 39, S.W - 16 - (x + w + 14), 26, { filaments: 1 });
    let lx = 28;
    lx = s.capWord(S, lx, S.H - 35, ['↑', '↓'], 'CHOOSE');
    s.capWord(S, lx, S.H - 35, ['◀', '▶'], 'CHANGE');
    lx = x + w + 26;
    lx = s.capWord(S, lx, S.H - 35, ['ENTER'], 'SELECT');
    s.capWord(S, lx, S.H - 35, ['ESC'], 'BACK');
    S.finish();
  };

  // ------------------------------------------------------------------ new game
  SCREENS['sh-new'] = async (canvas) => {
    const S = K.scene(canvas);
    backdrop(S);
    const w = 488, h = 306, x = 640 - w / 2, y = 226;
    panel(S, x, y, w, h);
    st.heading(S, 640, y + 24, 'NEW GAME');
    let ry = y + 70;
    // the seed: typed into a field of cells, the cursor a lit block; "random" shows while it is empty
    const sx = 640 - (s.width(4) + 12 + 300) / 2;
    st.text(S, sx, ry, 'SEED', { level: 'dim' });
    s.entry(S, sx + s.width(4) + 12, ry, '48213', 33);
    ry += 26;
    centre(S, 640, ry, 'LEAVE IT EMPTY FOR A NEW WORLD; THE SAME', { level: 'dim' }); ry += 15;
    centre(S, 640, ry, 'SEED GIVES THE SAME START', { level: 'dim' }); ry += 30;
    // who to serve: two toggles, one always picked (a lit frame); the focus is inverse
    const tx = 640 - (s.width(5) + 12 + bw(15) + 8 + bw(16)) / 2;
    st.text(S, tx, ry + 2, 'SERVE', { level: 'dim' });
    let bx = tx + s.width(5) + 12;
    bx = button(S, bx, ry, 15, 'TERRAN PATROL', 'checked') + 8;
    button(S, bx, ry, 16, 'SELENE COMBINE');
    ry += 32;
    centre(S, 640, ry, 'THE PATROL STARTS YOU AT HIGHPORT IN EARTH', { level: 'dim' }); ry += 15;
    centre(S, 640, ry, 'ORBIT; THE COMBINE AT SHACKLETON ICE ABOVE', { level: 'dim' }); ry += 15;
    centre(S, 640, ry, 'THE MOON', { level: 'dim' }); ry += 30;
    bx = 640 - (bw(6) + 10 + bw(5)) / 2;
    bx = button(S, bx, ry, 6, 'START') + 10;
    button(S, bx, ry, 5, 'BACK');
    legendRack(S, [[['↑', '↓'], 'CHOOSE'], [['ENTER'], 'SELECT'], [['ESC'], 'BACK']]);
    S.finish();
  };

  // ------------------------------------------------------------------ the mod scripts dialog
  // It floats over the game as it starts. The window's title sits in a red-filtered strip: scripts run
  // with the player's full access, the one warning the shell gives.
  SCREENS['sh-mods'] = async (canvas) => {
    const S = K.scene(canvas);
    await s.under(S, 'tk-helm-quiet', 0.42);
    const w = 700, h = 230, x = 640 - w / 2, y = 280;
    panel(S, x, y, w, h);
    // the title strip: the window's name on a red tile, the strip's red frame ghosting beyond it
    const tw = S.legendWidth('MOD SCRIPTS', { size: 13 }) + 20;
    S.frameLit(x + 8, y + 8, w - 16, 22, 1.1, { ghostOnly: true, color: 'red', k: 2.5 });
    S.rectLit(x + 8, y + 8, tw, 22, { color: 'red', level: 'norm', r: 1.2 });
    S.knockout(x + 18, y + 24, 'MOD SCRIPTS', { size: 13 });
    let ry = y + 46;
    st.text(S, x + 16, ry, 'THESE MODS INCLUDE SCRIPTS. SCRIPTS RUN WITH FULL ACCESS TO YOUR'); ry += 15;
    st.text(S, x + 16, ry, 'COMPUTER, LIKE ANY PROGRAM YOU INSTALL. ONLY RUN MODS FROM PEOPLE'); ry += 15;
    st.text(S, x + 16, ry, 'YOU TRUST.'); ry += 26;
    st.text(S, x + 16, ry, '- DEEP SPACE MARKETS 1.4  BY OSTRANDER', { color: 'amber' }); ry += 15;
    st.text(S, x + 16, ry, '- PIRATE CONTRACTS 0.9.2  BY KEL', { color: 'amber' }); ry += 26;
    st.text(S, x + 16, ry, "WITHOUT SCRIPTS, THESE MODS' DATA AND ASSETS STILL LOAD.", { level: 'dim' }); ry += 22;
    check(S, x + 16, ry, false, 'REMEMBER FOR THESE MODS'); ry += 28;
    let bx = 640 - (bw(13) + 12 + bw(11)) / 2;
    bx = button(S, bx, ry, 13, 'RUN SCRIPTS') + 12;
    button(S, bx, ry, 11, 'DATA ONLY', 'focus');
    S.finish();
  };
})();

// ====================================================================== printed cards (out of fiction)
// The controls (F1) and the end of a career are printed quick-reference cards laid over the game: the
// only light surface in the interface, plainly not part of the ship (7.12).
(() => {
  'use strict';
  const K = KIT, s = SCR;
  const INK = '#1c1c1c', SOFT = '#5d5b55';
  // one hint: its key caps, then what they do
  function hint(S, x, y, keys, words) {
    let cx = x;
    for (const k of keys) cx += s.printedKey(S, cx, y - 13, k, { size: 12, h: 18 }) + 3;
    s.print(S, Math.max(cx + 6, x + 96), y, words, { size: 13 });
  }
  function section(S, x, y, title, rows, o = {}) {
    if (o.here) {
      const b = S.base, tw = s.print(S, -999, -999, title, { size: 13, weight: 700, track: 0.06 });
      b.fillStyle = INK; b.beginPath(); b.roundRect(x - 6, y - 15, tw + 12 + 58, 21, 3); b.fill();
      s.print(S, x, y, title, { size: 13, weight: 700, color: '#e9e6dc', track: 0.06 });
      s.print(S, x + tw + 10, y, 'HERE', { size: 12, weight: 700, color: '#bdb8aa', track: 0.12 });
    } else s.print(S, x, y, title, { size: 13, weight: 700, track: 0.06 });
    S.base.fillStyle = 'rgba(28,28,28,0.35)'; S.base.fillRect(x, y + 8, 280, 0.8);
    rows.forEach(([keys, words], i) => hint(S, x, y + 32 + i * 21, keys, words));
    return y + 32 + rows.length * 21 + 18;
  }

  SCREENS['card-controls'] = async (canvas) => {
    const S = K.scene(canvas);
    await s.under(S, 'cx-helm-cruise', 0.8);
    const x = 140, y = 70, w = 1000, h = 660;
    s.card(S, x, y, w, h);
    s.print(S, x + 30, y + 44, 'Controls', { size: 24, weight: 700, track: 0 });
    s.print(S, x + 30, y + 66, 'Your keys, by where they work. The section you are in comes first.', { size: 13, color: SOFT });
    let kx = x + w - 30 - s.print(S, -999, -999, 'closes this card', { size: 13 });
    s.print(S, kx, y + 44, 'closes this card', { size: 13 });
    s.printedKey(S, kx - 34, y + 30, 'F1', { size: 12, h: 18 });
    S.base.fillStyle = INK; S.base.fillRect(x + 30, y + 84, w - 60, 1.4);
    const c1 = x + 30, c2 = x + 30 + 320, c3 = x + 30 + 640, top = y + 120;
    section(S, c1, top, 'AT THE HELM', [
      [['W', 'S'], 'thrust'], [['A', 'D'], 'turn'], [['SPACE'], 'guns'], [['M'], 'map'], [['V'], 'stand up'],
      [['Z', 'C'], 'strafe'], [['X'], 'rotation assist'], [['R'], 'missile'], [['F'], 'decoy'], [['TAB'], 'next target'],
      [['G'], 'arm port, ask clearance'], [['H'], 'next port'], [[',', '.'], 'time warp down / up'],
      [['9'], 'raise / lower shields'], [['8'], 'shield trim'], [['WHEEL'], 'zoom'],
    ], { here: true });
    let y2 = section(S, c2, top, 'ORBIT MAP AND BURN PLANNER', [
      [['N'], 'plan a burn'], [['B'], 'next burn'], [['↑', '↓'], 'prograde / retrograde'], [['→', '←'], 'radial out / in'],
      [[']', '['], 'burn later / earlier (Shift x10)'], [['DEL'], 'delete burn'],
    ]);
    y2 = section(S, c2, y2, 'DOCKED', [[['J'], 'mission board'], [['U'], 'undock'], [['T'], 'market'], [['Y'], 'yard'], [['I'], 'crew, hire']]);
    section(S, c2, y2, 'ANYWHERE', [[['I'], 'crew'], [['J'], 'missions held'], [['L'], 'notice log'], [['P'], 'twice: abandon ship'],
      [['F5'], 'quick save'], [['F9'], 'quick load'], [['ESC'], 'menu']]);
    let y3 = section(S, c3, top, 'ON DECK', [[['W', 'A', 'S', 'D'], 'walk'], [['E'], 'use console'], [['V'], 'sit at the console'],
      [['F'], 'force a shut door'], [['B'], 'cut a shut door'], [['O'], 'deck lights']]);
    section(S, c3, y3, 'AT THE WEAPONS CONSOLE', [[['MOUSE'], 'aim'], [['SPACE'], 'guns'], [['R'], 'missile'], [['TAB'], 'next target'],
      [['F'], 'decoy'], [['9'], 'raise / lower shields'], [['8'], 'shield trim'], [['V'], 'stand up'], [['WHEEL'], 'zoom']]);
    S.finish({ mesh: true });
  };

  // Ship lost or career over: the tubes go dark, and the card says what happened and the ways on. The
  // game keeps the keys (its quick load, Enter, Esc); the card only names them.
  SCREENS['card-lost'] = async (canvas) => {
    const S = K.scene(canvas);
    await s.under(S, 'st-helm', 0.7, { dark: true });
    const w = 520, h = 236, x = 640 - w / 2, y = 272;
    s.card(S, x, y, w, h);
    s.print(S, x + 30, y + 52, 'SHIP LOST', { size: 30, weight: 700, track: 0.04 });
    s.print(S, x + 30, y + 80, 'You crashed into Earth on day 3.', { size: 15 });
    S.base.fillStyle = INK; S.base.fillRect(x + 30, y + 98, w - 60, 1.4);
    [[['F9'], 'load the quick save'], [['ENTER'], 'load the latest autosave'], [['ESC'], 'menu']]
      .forEach(([k, t], i) => hint(S, x + 30, y + 134 + i * 28, k, t));
    S.finish();
  };
})();

// ====================================================================== the map's plot, as each spec shows it
// The orbit canvas stays the game's (MapPainter draws each piece where the map puts it); these are the
// two views the mockups use: the ship's orbit close to the planet, and the whole Earth-Moon transfer.
(() => {
  'use strict';
  const s = SCR, D = Math.PI / 180;
  // a conic through its focus: periapsis distance rp, eccentricity e, periapsis at angle w (clockwise on
  // screen), from true anomaly n0 to n1 (degrees)
  function conic(P2, fx, fy, rp, e, w, n0, n1, level, every) {
    const pts = [], steps = Math.max(40, Math.abs(n1 - n0) * 3);
    for (let q = 0; q <= steps; q++) {
      const n = (n0 + (n1 - n0) * q / steps) * D, r = rp * (1 + e) / (1 + e * Math.cos(n));
      if (r < 0 || r > 4000) continue;
      pts.push([fx + r * Math.cos(w * D + n), fy + r * Math.sin(w * D + n)]);
    }
    P2.chain(pts, level, every);
    return pts;
  }
  const at = (cx, cy, r, deg) => [cx + r * Math.cos(deg * D), cy + r * Math.sin(deg * D)];
  // a burn node; the selected one is inverse (its blink's lit phase) with its four handles around it.
  // The handles point along the orbit and out from the body: dir is the orbit's direction there.
  function node(P2, x, y, sel, handles) {
    P2.sprite(sel ? s.PS.nodeSel : s.PS.node, x, y, sel ? 'full' : 'norm');
    if (!handles) return;
    // [sprite, dx, dy]: prograde first (L2), then retrograde, radial out and in (L1)
    handles.forEach(([spr, dx, dy], i) => P2.sprite(s.PS[spr], x + dx, y + dy, i ? 'dim' : 'norm'));
  }
  // a label on the panel: 5x7 text at pitch 1.5, kept clear of its mark by a leader of dots (to the
  // label's left end, or its right end with o.right when the label sits left of its mark). The leader
  // leaves at the first line, or under the last with o.foot (a mark below the label), so it never
  // crosses the label's own text. o.align 'right' sets every line to the right edge: a label left of its
  // mark then ends at its leader.
  function tag(S, P2, x, y, lines, o = {}) {
    const ws = lines.map(t => s.width([...t].length)), lw = Math.max(...ws), n = lines.length;
    if (o.ground) P2.clear(x - 4, y - 4, lw + 8, n * 13.5 + 4);       // the label's ground
    lines.forEach((t, i) => S.dm(x + (o.align === 'right' ? lw - ws[i] : 0), y + i * 13.5, t, { pitch: 1.5, level: i ? 'dim' : (o.level ?? 'norm'), ghost: false }));
    const ex = o.right ? x + lw + 4 : x - 3, ey = o.foot ? y + (n - 1) * 13.5 + 13 : y + 5;
    if (o.from) P2.chain([o.from, [ex, ey]], 'dim', 2);
  }
  // a ruler: ten dots lit for the scale's length, its figure after it
  function scaleBar(S, P2, x, y, len, text) {
    P2.chain([[x, y], [x + len, y]], 'dim', 1);
    P2.chain([[x, y - 4], [x, y + 3]], 'dim', 1); P2.chain([[x + len, y - 4], [x + len, y + 3]], 'dim', 1);
    S.dm(x + len + 8, y - 5, text, { pitch: 1.5, level: 'dim', ghost: false });
  }

  // The ship's orbit over the planet: burn 1 at the orbit's left (180), the plan climbing over the top and out
  // to the right; the ship 12 minutes before the burn, Highport, a freighter and the raider in company.
  function orbitLeo(S, x, y, w, h, o = {}) {
    const P2 = s.plot(S, x, y, w, h, {});
    const cx = x + w * (o.ex ?? 0.42), cy = y + h * (o.ey ?? 0.6), R = o.R ?? Math.min(h * 0.27, w * 0.2), r = R + 24;
    P2.disc(cx, cy, R);
    P2.ellipse(cx, cy, r, r, 0, 0, Math.PI * 2, 'norm', 2);          // the orbit now
    const plan = conic(P2, cx, cy, r, 0.78, 180, 0, 158, 'full', 1);  // the plan, solid
    // the apsides of the orbit now
    const [apx, apy] = at(cx, cy, r, 300), [pex, pey] = at(cx, cy, r, 120);
    P2.sprite(s.PS.ap, apx, apy - 7, 'norm'); P2.sprite(s.PS.pe, pex, pey + 7, 'norm');
    // burn 1 on the orbit (selected), burn 2 on the climb
    const [b1x, b1y] = at(cx, cy, r, 180);
    node(P2, b1x, b1y, o.sel !== 2, o.sel !== 2 && [['arrowU', 0, -16], ['arrowD', 0, 16], ['arrowL', -16, 0], ['arrowR', 16, 0]]);
    const b2 = plan[Math.round(plan.length * 0.36)];
    node(P2, b2[0], b2[1], o.sel === 2);
    // the ship and its company
    const [sx, sy] = at(cx, cy, r, 134), near = o.near ?? [['HIGHPORT', '3.0 KM'], ['FREIGHTER', '3.1 KM'], ['RAIDER', '10.8 KM']];
    P2.sprite(s.PS.ship, sx, sy, 'full');
    P2.sprite(s.PS.station, sx - 9, sy + 9, 'norm');
    P2.sprite(s.PS.neutral, sx + 10, sy + 14, 'dim');
    if (near[2]) P2.sprite(s.PS.hostile, sx - 20, sy + 26, 'norm');
    // labels, kept apart, each on a leader
    const lx = x + 18;
    tag(S, P2, lx, sy + 44, near[0], { from: [sx - 12, sy + 10] });
    tag(S, P2, lx + 120, sy + 78, near[1], { from: [sx + 10, sy + 18], level: 'dim' });
    if (near[2]) tag(S, P2, lx, sy + 86, near[2], { from: [sx - 22, sy + 30] });
    tag(S, P2, b1x - 74, b1y - 34, o.b1 ?? ['BURN 1', 'T-12:05'], { level: 'full' });
    tag(S, P2, b2[0] + 12, b2[1] - 26, o.b2 ?? ['BURN 2', 'T-2:05:00']);
    S.dm(apx + 8, apy - 12, 'AP 403', { pitch: 1.5, level: 'dim', ghost: false });
    S.dm(pex - 62, pey + 10, 'PE 397', { pitch: 1.5, level: 'dim', ghost: false });
    S.dm(cx - 22, cy + 8, 'EARTH', { pitch: 1.5, level: 'dim', ghost: false });
    // where the plan leaves the canvas, the Nav target is pulled to the edge
    const last = plan.filter(([px, py]) => px < x + w - 4 && py < y + h - 4 && px > x && py > y).pop();
    const edge = o.edge ?? ['SHACKLETON ICE', '1,240 KM  ▶'];
    if (last && edge) {
      S.dm(x + w - 12 - s.width(edge[0].length), last[1] - 34, edge[0], { pitch: 1.5, level: 'norm', ghost: false });
      S.dm(x + w - 12 - s.width(edge[1].length), last[1] - 20, edge[1], { pitch: 1.5, level: 'dim', ghost: false });
    }
    // Gateway, past the canvas: a mark on the edge with its distance
    if (o.gateway !== false) {
      S.dm(x + w - 12 - s.width(17), y + (o.gatewayY ?? 14), 'GATEWAY 62,000 KM', { pitch: 1.5, level: 'dim', ghost: false });
      S.dm(x + w - 12 - s.width(1), y + (o.gatewayY ?? 14) + 14, '▲', { pitch: 1.5, level: 'dim', ghost: false });
    }
    scaleBar(S, P2, x + 18, y + h - 18, 60, '3,000 KM');
    P2.done(); // last: the leaders and the ruler light the panel's dots too
    return { P2, cx, cy, R, r };
  }

  // The whole transfer: Earth small, the Moon's orbit, the transfer climbing to the Moon's sphere of
  // influence, the leg around the Moon and the capture burn; the traffic on the route.
  function orbitTransfer(S, x, y, w, h, o = {}) {
    const P2 = s.plot(S, x, y, w, h, {});
    const ex = x + w * (o.ex ?? 0.34), ey = y + h * (o.ey ?? 0.56), RM = Math.min(h * 0.44, w * 0.4);
    P2.disc(ex, ey, 11);
    P2.ellipse(ex, ey, 17, 17, 0, 0, Math.PI * 2, 'norm', 2);                    // the orbit now, around Earth
    P2.ellipse(ex, ey, RM, RM, 0, 0, Math.PI * 2, 'dim', 3);                     // the Moon's orbit
    const ma = o.moonAt ?? -28, [mx, my] = at(ex, ey, RM, ma), soi = RM * 0.17;
    P2.ellipse(mx, my, soi, soi, 0, 0, Math.PI * 2, 'dim', 2);                   // its sphere of influence
    P2.disc(mx, my, 5);
    // the transfer: periapsis opposite the Moon, apoapsis at the Moon's orbit, flown to the sphere's edge
    const rp = 17, ra = RM, e = (ra - rp) / (ra + rp), wp = ma + 180;
    const legE = [];
    { const steps = 240; for (let q = 0; q <= steps; q++) { const n = 180 * q / steps * D, r = rp * (1 + e) / (1 + e * Math.cos(n)), px = ex + r * Math.cos(wp * D + n), py = ey + r * Math.sin(wp * D + n); if (Math.hypot(px - mx, py - my) < soi) break; legE.push([px, py]); } }
    P2.chain(legE, 'full', o.proposal ? 3 : 1);
    // the leg inside the sphere: round the Moon's near side to the capture burn below it, then the low orbit
    const rl = 18, entry = legE[legE.length - 1], a0 = Math.atan2(entry[1] - my, entry[0] - mx), a1 = 90 * D;
    const legM = [];
    for (let q = 0; q <= 40; q++) { const t = q / 40, a = a0 + (a1 - a0 - 2 * Math.PI * (a1 < a0 ? 0 : 1)) * t, rr = soi + (rl - soi) * t; legM.push([mx + rr * Math.cos(a), my + rr * Math.sin(a)]); }
    P2.chain(legM, 'full', o.proposal ? 3 : 1);
    P2.ellipse(mx, my, rl, rl, 0, 0, Math.PI * 2, 'dim', 1);                      // the orbit the plan ends in
    const b2 = [mx, my + rl];
    const [b1x, b1y] = at(ex, ey, rp, wp);
    node(P2, b1x, b1y, o.sel === 1);
    node(P2, b2[0], b2[1], o.sel === 2, o.sel === 2 && [['arrowL', -20, 0], ['arrowR', 20, 0], ['arrowD', 0, 17], ['arrowU', 0, -12]]);
    // the traffic: Highport in its orbit, freighters on the route, a pirate sighting
    P2.sprite(s.PS.station, ex - 24, ey + 12, 'norm');
    P2.sprite(s.PS.neutral, ex + RM * 0.42, ey - RM * 0.3, 'dim');
    P2.sprite(s.PS.neutral, ex + RM * 0.7, ey - RM * 0.05, 'dim');
    P2.sprite(s.PS.hostile, ex + RM * 0.18, ey + RM * 0.5, 'norm');
    // the Nav target's brackets (Shackleton Ice, on the Moon): four corners of dots, lit full
    for (const [dx, dy] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
      const bx = mx + dx * 30, by = my + dy * 30;
      S.dotLine(bx, by, bx - dx * 6, by, { size: 1.6, step: 2.4, level: 'full' });
      S.dotLine(bx, by, bx, by - dy * 6, { size: 1.6, step: 2.4, level: 'full' });
    }
    const L = (tx, ty, lines, lv) => lines.forEach((t, i) => S.dm(tx, ty + i * 13.5, t, { pitch: 1.5, level: i ? 'dim' : lv ?? 'norm', ghost: false }));
    // the Moon's labels outside its sphere, the sphere's own name on a ground clear of the Moon's orbit
    const lx = mx + Math.max(38, soi + 8);
    L(lx, my - 30, ['SHACKLETON ICE', 'SELENE COMBINE'], 'full');
    L(Math.max(lx, mx + 48), my + 6, ['MOON']);
    L(ex - 22, ey + 26, ['EARTH', 'HIGHPORT']);
    L(b1x - 96, b1y - 34, ['BURN 1', 'T-1:29:51']);
    L(mx - 118, my + 30, ['BURN 2', 'T-3D 17H'], 'full');
    L(ex + RM * 0.18 + 12, ey + RM * 0.5 - 4, ['PIRATES SEEN', 'DAY 2']);
    P2.clear(mx - soi - 10, my - soi - 16, s.width(3) + 8, 18);
    S.dm(mx - soi - 6, my - soi - 12, 'SOI', { pitch: 1.5, level: 'dim', ghost: false });
    scaleBar(S, P2, x + 18, y + h - 18, 73, '100,000 KM');
    P2.done();
    return { P2, ex, ey, mx, my, RM };
  }

  // A climb to a higher circular orbit, to scale: burn 1 on the orbit now, the transfer over the top to
  // burn 2 half an orbit later, and the circle the plan ends in. o.kpu: km per unit; o.alt0, o.alt1: the
  // two orbits' altitudes in km; o.shipDeg: where the ship is (the orbit runs clockwise on screen). Marks
  // closer than a sprite apart are spread along the orbit to stay legible, the ship first, then the nav
  // target (o.navAhead degrees on). A near-circular orbit gets no apsis marks; its tag carries Ap and Pe.
  function orbitClimb(S, x, y, w, h, o) {
    const P2 = s.plot(S, x, y, w, h, {});
    const RE = 6371, R = RE / o.kpu, r0 = (RE + o.alt0) / o.kpu, r1 = (RE + o.alt1) / o.kpu;
    const cx = x + w * (o.ex ?? 0.5), cy = y + h * (o.ey ?? 0.5);
    P2.disc(cx, cy, R);
    P2.ellipse(cx, cy, r0, r0, 0, 0, Math.PI * 2, 'norm', 2);                     // the orbit now
    P2.ellipse(cx, cy, r1, r1, 0, 0, Math.PI * 2, 'dim', 1);                      // the orbit the plan ends in
    conic(P2, cx, cy, r0, (r1 - r0) / (r1 + r0), 180, 0, 180, 'full', 1);         // the transfer, solid
    const [b1x, b1y] = at(cx, cy, r0, 180), [b2x, b2y] = at(cx, cy, r1, 0);
    // burn 1 selected: prograde is up at the orbit's left, radial out is left
    node(P2, b1x, b1y, true, [['arrowU', 0, -16], ['arrowD', 0, 16], ['arrowL', -16, 0], ['arrowR', 16, 0]]);
    node(P2, b2x, b2y, false);
    const [sx, sy] = at(cx, cy, r0, o.shipDeg), [nx, ny] = at(cx, cy, r0, o.shipDeg + o.navAhead);
    P2.sprite(s.PS.ship, sx, sy, 'full');
    P2.sprite(s.PS.station, nx, ny, 'norm');
    // the nav target's brackets, as on the transfer view
    for (const [dx, dy] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
      const bx = nx + dx * 12, by = ny + dy * 12;
      S.dotLine(bx, by, bx - dx * 5, by, { size: 1.6, step: 2.4, level: 'full' });
      S.dotLine(bx, by, bx, by - dy * 5, { size: 1.6, step: 2.4, level: 'full' });
    }
    // the labels, in the corners outside the rings, each on a leader
    tag(S, P2, x + 14, b1y - 58, o.b1, { level: 'full', from: [b1x - 9, b1y - 9], right: true });
    tag(S, P2, b2x + 14, b2y - 58, o.b2, { from: [b2x + 6, b2y - 8] });
    tag(S, P2, x + 84, ny + 86, o.nav, { from: [nx - 10, ny + 10], right: true });
    const [o0x, o0y] = at(cx, cy, r0, 62), [o1x, o1y] = at(cx, cy, r1, 35);
    tag(S, P2, x + w - 212, y + h - 84, o.now, { from: [o0x + 3, o0y + 3] });
    tag(S, P2, x + w - 172, y + h - 144, o.after, { from: [o1x + 3, o1y + 3] });
    P2.clear(cx - s.width(5) / 2 - 5, cy - 3, s.width(5) + 10, 17);              // the ground for EARTH
    S.dm(cx - s.width(5) / 2, cy + 1, 'EARTH', { pitch: 1.5, level: 'dim', ghost: false });
    scaleBar(S, P2, x + 18, y + h - 18, 1000 / o.kpu, '1,000 KM');
    P2.done();
    return { P2, cx, cy, R, r0, r1 };
  }

  // The first leg of a trip to another body, as the game plans it (Context version 14): burn 1 in the orbit
  // now starts the transfer, drawn solid to the Moon's sphere of influence and no further, since the
  // capture and the approach are planned at the crossing. The Moon is drawn twice, as the game draws a body
  // the plan meets (PlayerOrbitPath): where it is now, with the Nav's brackets on Shackleton Ice, and as the
  // ghost the leg runs into, its outline and sphere where it will be at the closest approach (ghostTime:
  // the leg's periapsis, T-4.1 d, 0.15 rad past the leg's apoapsis). Orbits run anticlockwise and the Moon
  // moves 13.2 degrees a day, so the Moon now is 54 degrees back along its orbit. The Moon's orbit and
  // sphere are to scale (o.RM units for 384,400 km); Earth and the orbit now are drawn larger than it,
  // since at this scale the orbit lies inside the disc. The ship is spread back along its orbit from burn
  // 1's handles, and the near traffic, too close to the ship to draw apart here, is one cluster label
  // (W1's rule).
  function orbitLeg(S, x, y, w, h, o = {}) {
    const P2 = s.plot(S, x, y, w, h, {});
    const RM = o.RM ?? 440, kpu = 384400 / RM, ex = x + (o.ex ?? 86), ey = y + h * (o.ey ?? 0.3), r0 = 34;
    P2.disc(ex, ey, 10);
    P2.ellipse(ex, ey, r0, r0, 0, 0, Math.PI * 2, 'norm', 2);                     // the orbit now
    P2.ellipse(ex, ey, RM, RM, 0, 0, Math.PI * 2, 'dim', 3);                      // the Moon's orbit
    const gDeg = o.ghostAt ?? -8.6, [gx, gy] = at(ex, ey, RM, gDeg), soi = 66100 / kpu;
    P2.ellipse(gx, gy, soi, soi, 0, 0, Math.PI * 2, 'dim', 3);                    // the ghost: its sphere
    P2.ellipse(gx, gy, 5, 5, 0, 0, Math.PI * 2, 'dim', 1);                        // and its outline
    const [mx, my] = at(ex, ey, RM, gDeg + 54);                                   // the Moon now
    P2.disc(mx, my, 5);
    // the leg: periapsis at burn 1, left of Earth; under Earth to the apoapsis 378,000 km out to the right,
    // flown until it meets the ghost's sphere
    const ra = 378000 / kpu, e = (ra - r0) / (ra + r0), leg = [];
    for (let q = 0; q <= 720; q++) {
      const n = Math.PI * q / 720, r = r0 * (1 + e) / (1 + e * Math.cos(n));
      const px = ex + r * Math.cos(Math.PI - n), py = ey + r * Math.sin(Math.PI - n);
      if (Math.hypot(px - gx, py - gy) < soi) break;
      leg.push([px, py]);
    }
    P2.chain(leg, 'full', 1);
    const [qx, qy] = leg[leg.length - 1];                                         // the crossing
    P2.sprite(s.PS.waypoint, qx, qy, 'norm');
    // burn 1, selected: prograde is down at the orbit's left, radial out is left
    const b1x = ex - r0, b1y = ey;
    node(P2, b1x, b1y, true, [['arrowD', 0, 16], ['arrowU', 0, -16], ['arrowL', -16, 0], ['arrowR', 16, 0]]);
    const [sx, sy] = at(ex, ey, r0, o.shipDeg ?? 227);
    P2.sprite(s.PS.ship, sx, sy, 'full');
    // the Nav target's brackets (Shackleton Ice, in a low orbit of the Moon now)
    for (const [dx, dy] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
      const bx = mx + dx * 16, by = my + dy * 16;
      S.dotLine(bx, by, bx - dx * 6, by, { size: 1.6, step: 2.4, level: 'full' });
      S.dotLine(bx, by, bx, by - dy * 6, { size: 1.6, step: 2.4, level: 'full' });
    }
    // the labels, each on its ground, clear of the leg: burn 1's above it to the left, the cluster's right
    // of the ship, the crossing's under the leg's end inside the ellipse (its leader leaving the crossing to
    // the left), the ghost's left of its sphere, the Nav's left of the Moon now
    const g = { ground: true }, wd = (lines) => Math.max(...lines.map(t => s.width([...t].length)));
    tag(S, P2, x + 8, b1y - 112, o.b1 ?? ['BURN 1', 'T-12:05'], { ...g, level: 'full', from: [b1x - 11, b1y - 13], right: true, foot: true });
    tag(S, P2, sx + 40, sy - 32, o.near ?? ['4 CONTACTS'], { ...g, from: [sx + 8, sy - 2] });
    const cross = o.cross ?? ['MOON SOI', 'T-3.3 D', 'CAPTURE PLANNED HERE'];
    tag(S, P2, qx - 40 - wd(cross), qy + 7, cross, { ...g, from: [qx - 9, qy + 3], right: true, align: 'right' });
    const ghost = o.ghost ?? ['MOON AT CLOSEST APPROACH', 'T-4.1 D'];
    tag(S, P2, gx - soi - 16 - wd(ghost), gy - 38, ghost, { ...g, from: [gx - 8, gy - 4], right: true, align: 'right' });
    const nav = o.nav ?? ['SHACKLETON ICE'];
    tag(S, P2, mx - 40 - wd(nav), my - 22, nav, { ...g, level: 'full', from: [mx - 18, my - 14], right: true, align: 'right' });
    P2.clear(mx - s.width(4) / 2 - 4, my + 22, s.width(4) + 8, 16);
    S.dm(mx - s.width(4) / 2, my + 26, 'MOON', { pitch: 1.5, level: 'dim', ghost: false });
    P2.clear(ex + r0 + 4, ey - 9, s.width(5) + 8, 18);
    S.dm(ex + r0 + 8, ey - 5, 'EARTH', { pitch: 1.5, level: 'dim', ghost: false });
    scaleBar(S, P2, x + 18, y + h - 18, 100000 / kpu, '100,000 KM');
    if (o.clearTop) P2.clear(x, y, w, o.clearTop);                               // the view row's labels
    P2.done();
    return { P2, ex, ey, mx, my, gx, gy, RM, qx, qy };
  }

  // The plan's timeline: a strip of 64 cells from now to the last event on a log scale, every event lit in
  // its cell; a deadline lights the amber rail under the strip. events: [{t (h), mark, label, deadline}]
  // An estimate (o.estimate: the capture before the crossing plans it) is a hollow cell, its label at L1.
  function timeline(S, x, y, w, events, o = {}) {
    const n = 64, T = Math.max(...events.map(e => e.t)), t0 = o.t0 ?? 1;
    const cell = t => Math.min(n - 1, Math.round((n - 1) * Math.log(1 + t / t0) / Math.log(1 + T / t0)));
    const cw = w / n;
    for (let i = 0; i < n; i++) {
      S.rectLit(x + i * cw + 0.6, y, cw - 1.2, 9, { ghostOnly: true, k: 1.6 });
      S.rectLit(x + i * cw + 0.6, y + 12, cw - 1.2, 2.4, { ghostOnly: true, color: 'amber', k: 2.4 });
    }
    S.rectLit(x, y, cw - 1.2, 9, { level: 'norm' });                // now
    // the labels: each right-aligned to its cell, pushed left to clear the next one; a label that would
    // run into the one before it drops to the second row
    const placed = [];
    let nextLeft = x + w + 10;
    [...events].sort((a, b) => b.t - a.t).forEach(ev => {
      const c = cell(ev.t), cx = x + c * cw, lw = Math.max(...[].concat(ev.label).map(t => s.width([...t].length)));
      if (ev.deadline) S.rectLit(x, y + 12, cx - x + cw - 1.2, 2.4, { color: 'amber', level: 'norm' });
      else if (ev.estimate) S.frameLit(cx + 0.6, y, cw - 1.2, 9, 1, { level: 'norm' });
      else S.rectLit(cx + 0.6, y, cw - 1.2, 9, { level: 'full' });
      const right = Math.min(cx + cw, nextLeft - 20);
      placed.push({ ev, lx: Math.max(x + 40, right - lw), lw, cx });
      nextLeft = right - lw;
    });
    placed.reverse().forEach((p, i) => {
      const prev = placed[i - 1], row = prev && prev.lx + prev.lw + 10 > p.lx ? 1 : 0;
      [].concat(p.ev.label).forEach((t, k) => S.dm(p.lx, y + 22 + row * 14 + k * 13.5, t, { pitch: 1.5, level: k || p.ev.estimate ? 'dim' : 'norm', color: p.ev.deadline ? 'amber' : undefined, ghost: false }));
      if (p.lx + p.lw < p.cx - 4) S.dotLine(p.lx + p.lw + 4, y + 26, p.cx + cw / 2, y + 17, { size: 1, step: 3, level: 'dim' });
    });
    S.dm(x, y + 22, 'NOW', { pitch: 1.5, level: 'dim', ghost: false });
    return cell;
  }

  Object.assign(SCR, { conic, orbitLeo, orbitTransfer, orbitClimb, orbitLeg, timeline, node, tag, scaleBar });
})();

// ====================================================================== the slate's LCD parts
(() => {
  'use strict';
  // An LCD bar: dark segments with their shadow on the reflector; the unlit ones a faint print.
  function lcdBar(S, x, y, w, h, n, frac, o = {}) {
    const b = S.base, gap = 1.2, sw = (w - gap * (n - 1)) / n, lit = Math.round(frac * n);
    for (let i = 0; i < n; i++) {
      const sx = x + i * (sw + gap);
      // inverse (in a focused row's dark block): the lit segments left clear
      if (o.inverse) { b.fillStyle = i < lit ? '#8e9a7e' : 'rgba(142,154,126,0.18)'; b.fillRect(sx, y, sw, h); continue; }
      if (i < lit) { b.fillStyle = 'rgba(0,0,0,0.14)'; b.fillRect(sx + 0.6, y + 0.7, sw, h); b.fillStyle = '#1a2016'; b.fillRect(sx, y, sw, h); }
      else { b.fillStyle = 'rgba(26,32,22,0.08)'; b.fillRect(sx, y, sw, h); }
    }
  }
  // A rule printed on the LCD's glass (a segment of its own, always dark).
  function lcdRule(S, x, y, w, vertical) {
    const b = S.base; b.fillStyle = 'rgba(26,32,22,0.35)';
    if (vertical) b.fillRect(x, y, 0.8, w); else b.fillRect(x, y, w, 0.8);
  }
  // The slate's keys under its glass: a rubber key, its action printed on the housing over it.
  function slateKeys(S, x, y, items) {
    let kx = x;
    for (const it of items) {
      const kw = Math.max(52, S.legendWidth(it.k, { size: 12, weight: 600, condense: 0.86, track: 0.1 }) + 20);
      const tw = S.legendWidth(it.t, { size: 12, weight: 600, condense: 0.86, track: 0.12 }) * 1.08;
      const slot = Math.max(kw, tw) + 18;
      S.silk(kx + slot / 2, y, it.t, { size: 12, color: it.dim ? '#4f5458' : '#8d949a', align: 'center' });
      S.key(kx + (slot - kw) / 2, y + 6, kw, 24, it.k, { size: 12, labelColor: it.dim ? '#6a7075' : undefined });
      kx += slot + 6;
    }
    return kx;
  }
  Object.assign(SCR, { lcdBar, lcdRule, slateKeys });
})();
