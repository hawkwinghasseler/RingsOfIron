// FILAMENT widget kit: composite widgets built from the parts in vfd.js, shared by every mockup.
const SCREENS = {};
const KIT = (() => {
  'use strict';
  const V = VFD;

  function scene(canvas, W = 1280, H = 800) { return new V.Scene(canvas, W, H, 2); }
  function loadImage(src) {
    return new Promise((ok, fail) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => fail(new Error('no image ' + src)); i.src = src; });
  }
  async function world(S, src, o = {}) {
    const img = await loadImage(src);
    S.image(img, o.x ?? 0, o.y ?? 0, o.w ?? S.W, o.h ?? S.H, { filter: o.filter });
  }

  // ------------------------------------------------------------ small parts
  // A key cap: a little rubber key with its legend. Returns its width.
  function keycap(S, x, y, label, o = {}) {
    const h = o.h ?? 18, size = o.size ?? 12;
    const w = Math.max(h, S.legendWidth(label, { size, weight: 600, condense: 0.86, track: 0.1 }) + 7);
    const b = S.base;
    b.save();
    b.fillStyle = '#020203'; b.beginPath(); b.roundRect(x - 0.8, y - 0.6, w + 1.6, h + 2.2, 2.6); b.fill();
    const g = b.createLinearGradient(0, y, 0, y + h);
    g.addColorStop(0, o.lit ? '#4a4d52' : '#3d4045'); g.addColorStop(1, '#222427');
    b.fillStyle = g; b.beginPath(); b.roundRect(x, y, w, h, 2.2); b.fill();
    b.restore();
    S.silk(x + w / 2, y + h / 2 + size * 0.37, label, { size, align: 'center', color: '#e4e8ea', condense: 0.86 });
    return w;
  }
  // A row of verbs, each a key cap and a word in dot matrix: [['E', 'USE'], ['V', 'SIT']].
  function verbs(S, x, y, items, o = {}) {
    let cx = x;
    const p = o.pitch ?? 1.5;
    for (const [k, word] of items) {
      cx += keycap(S, cx, y, k, { h: 7 * p + 2 }) + 4;
      cx += S.dm(cx, y + 1, word, { pitch: p, level: o.level ?? 'norm', color: o.color, layer: o.layer, ghost: false }) + 10;
    }
    return cx - x;
  }

  // A module with its silk title printed on the bezel above it.
  function titled(S, x, y, w, h, title, o = {}) {
    if (title) S.silk(x + 1, y - 4.5, title, { size: o.titleSize ?? 12, color: o.titleColor });
    S.module(x, y, w, h, o);
  }

  // A slab: a free-standing module with a thin bezel of its own.
  function slab(S, x, y, w, h, o = {}) {
    const b = S.base, r = 5, pad = o.pad ?? 5;
    b.save();
    b.shadowColor = 'rgba(0,0,0,0.75)'; b.shadowBlur = 12; b.shadowOffsetY = 3;
    const g = b.createLinearGradient(0, y - pad, 0, y + h + pad);
    g.addColorStop(0, '#1c1d20'); g.addColorStop(1, '#0e0f10');
    b.fillStyle = g; b.beginPath(); b.roundRect(x - pad, y - pad, w + 2 * pad, h + 2 * pad, r); b.fill();
    b.shadowColor = 'transparent';
    b.strokeStyle = '#2a2c30'; b.lineWidth = 0.8; b.beginPath(); b.roundRect(x - pad + 0.4, y - pad + 0.4, w + 2 * pad - 0.8, h + 2 * pad - 0.8, r); b.stroke();
    b.restore();
    if (o.title) S.silk(x + 1, y - 1.2, o.title, { size: 6, color: '#6d7379' });
    S.module(x, y + (o.title ? 2 : 0), w, h - (o.title ? 2 : 0), { radius: 2.5, filaments: o.filaments, glare: o.glare });
  }

  // A bare bezel plate, for a group of windows and keys that stands free of the racks.
  function plate(S, x, y, w, h, o = {}) {
    const b = S.base, r = o.radius ?? 5, pad = o.pad ?? 5;
    b.save();
    b.shadowColor = 'rgba(0,0,0,0.75)'; b.shadowBlur = 12; b.shadowOffsetY = 3;
    const g = b.createLinearGradient(0, y - pad, 0, y + h + pad);
    g.addColorStop(0, '#1c1d20'); g.addColorStop(1, '#0e0f10');
    b.fillStyle = g; b.beginPath(); b.roundRect(x - pad, y - pad, w + 2 * pad, h + 2 * pad, r); b.fill();
    b.shadowColor = 'transparent';
    b.strokeStyle = '#2a2c30'; b.lineWidth = 0.8; b.beginPath(); b.roundRect(x - pad + 0.4, y - pad + 0.4, w + 2 * pad - 0.8, h + 2 * pad - 0.8, r); b.stroke();
    b.restore();
  }
  // Dark letters cut out of whatever is lit on the glass (an inverse block's text).
  function knock(S, x, y, str, p = 1.5) {
    S.cut(ctx => knockDots(ctx, x, y, str, p));
  }
  // A pip: a small fixed filtered lamp (a severity column beside a list row).
  function pip(S, x, y, color, on, o = {}) {
    const r = o.r ?? 1.7;
    if (on) S.dot(x, y, { size: r * 2, color, level: o.level ?? 'full' });
    else S.dot(x, y, { size: r * 2, color, ghostOnly: true, k: 2.2 });
  }

  // ------------------------------------------------------------ annunciator
  // A tile: caution is an amber outlined cell, warning a red inverse block; off is all ghost.
  function tile(S, x, y, w, h, tag, value, sev, o = {}) {
    const color = sev === 'warning' ? 'red' : sev === 'caution' ? 'amber' : (o.color ?? 'zinc');
    const tagSize = o.tagSize ?? 9.5, p = o.pitch ?? 1.5;
    const ty = y + h / 2 + tagSize * 0.36;
    const valueCells = o.valueCells ?? Math.max(0, Math.floor((w - 10 - S.legendWidth(tag, { size: tagSize }) - 6) / (6 * p)));
    if (!sev || sev === 'off') {
      S.frameLit(x, y, w, h, 1.1, { ghostOnly: true, color });
      S.legend(x + 5, ty, tag, { size: tagSize, color, lit: false });
      return;
    }
    if (sev === 'warning') {
      S.rectLit(x, y, w, h, { color, level: o.level ?? 'norm', r: 1.2 });
      const tw = S.knockout(x + 5, ty, tag, { size: tagSize });
      if (value) {
        // dark dots in the lit block: the value's glyph dots are knocked out
        const vx = x + 5 + tw + 6, vy = y + (h - 7 * p) / 2;
        S.cut(ctx => knockDots(ctx, vx, vy, value, p), o);
      }
    } else {
      S.frameLit(x, y, w, h, 1.1, { color, level: o.level ?? 'norm' });
      const tw = S.legend(x + 5, ty, tag, { size: tagSize, color, level: 'full', ghost: false });
      if (value) S.dm(x + 5 + tw + 6, y + (h - 7 * p) / 2, value, { pitch: p, color, level: 'norm', ghost: false });
    }
  }
  function knockDots(ctx, x, y, str, p) {
    const d = p * 0.86;
    [...String(str).toUpperCase()].forEach((ch, i) => {
      const g = V.ROM[ch] || V.ROM['?'];
      for (let r = 0; r < 7; r++) for (let c = 0; c < 5; c++) if (g[r][c] === '#') {
        ctx.fillStyle = '#000'; ctx.fillRect(x + i * 6 * p + c * p + (p - d) / 2, y + r * p + (p - d) / 2, d, d);
      }
    });
  }

  // ------------------------------------------------------------ soft keys
  // Labels drawn in the module's bottom row, keys on the bezel under the module.
  // items: [{t: 'NAV', k: '1', active, led, ledOn, dim}]
  function softKeys(S, mx, my, mw, mh, items, o = {}) {
    const n = o.slots ?? items.length, slotW = mw / n, p = o.pitch ?? 1.5;
    const ly = my + mh - 7 * p - (o.inset ?? 4);
    const ky = my + mh + (o.keyGap ?? 5);
    items.forEach((it, i) => {
      if (!it) return;
      const cx = mx + slotW * (i + 0.5);
      const cells = it.t.length, lw = cells * 6 * p - p;
      S.dm(cx - lw / 2, ly, it.t, { pitch: p, level: it.active ? 'full' : it.dim ? 'dim' : 'norm', inverse: it.active ? [0, cells] : null, color: it.color, ghost: false });
      if (o.keys !== false) {
        const kw = Math.min(slotW - 6, o.keyW ?? 40);
        S.key(cx - kw / 2, ky, kw, o.keyH ?? 24, it.k ?? '', { size: 12, led: it.led, ledOn: it.ledOn });
      }
    });
    // tick marks between label slots, printed on the glass edge
    for (let i = 1; i < n; i++) S.rectLit(mx + slotW * i - 0.4, ly - 1, 0.8, 7 * p + 2, { ghostOnly: true, k: 1.5 });
  }

  // ------------------------------------------------------------ text helpers
  // Label legend (dim) + dot-matrix value. Returns the end x.
  function field(S, x, y, label, value, o = {}) {
    const p = o.pitch ?? 1.5;
    let cx = x;
    if (label) cx += S.legend(x, y + 7 * p - 0.2, label, { size: o.labelSize ?? 8.5, level: o.labelLevel ?? 'dim', color: o.labelColor ?? o.color }) + 5;
    if (o.labelW) cx = x + o.labelW;
    return cx + S.dm(cx, y, value, { pitch: p, level: o.level ?? 'norm', color: o.color, cells: o.cells, align: o.align, ghost: o.ghost, inverse: o.inverse });
  }

  // A dot-matrix table. cols: [{h, cells, align}]; rows: [[...]]; o.focus: row index.
  function table(S, x, y, cols, rows, o = {}) {
    const p = o.pitch ?? 1.5, rowH = o.rowH ?? 7 * p + 5, cellW = 6 * p;
    let cx = x;
    const xs = cols.map(c => { const at = cx; cx += (c.cells + (c.gap ?? 1)) * cellW; return at; });
    if (o.header !== false) {
      cols.forEach((c, i) => {
        const w = c.cells * cellW - p;
        const hx = c.align === 'right' ? xs[i] + w : xs[i];
        S.legend(hx, y + 7, c.h, { size: 7.5, level: 'dim', align: c.align === 'right' ? 'right' : 'left', color: o.color });
      });
      y += 13;
    }
    rows.forEach((r, ri) => {
      const ry = y + ri * rowH;
      const focus = ri === o.focus;
      if (focus) S.dm(x - cellW * 1.5, ry, '▶', { pitch: p, level: 'full', color: o.color, ghost: false });
      cols.forEach((c, ci) => {
        const val = String(r[ci] ?? '');
        const lv = (o.levels && o.levels[ri] && o.levels[ri][ci]) || (focus ? 'full' : (o.rowLevel ? o.rowLevel(ri) : 'norm'));
        S.dm(xs[ci], ry, val, { pitch: p, cells: c.cells, align: c.align === 'right' ? 'right' : 'left', level: lv,
          inverse: focus && o.inverseFocus !== false ? [0, c.cells] : null, color: (o.colors && o.colors[ri] && o.colors[ri][ci]) || o.color, ghost: o.ghost ?? false });
      });
    });
    return y + rows.length * rowH;
  }

  // ------------------------------------------------------------ bars
  const DEPLETE = (i, n) => (i < n * 0.2 ? 'red' : i < n * 0.5 ? 'amber' : 'zinc');
  const RISING = (i, n) => (i >= n * 0.8 ? 'red' : i >= n * 0.6 ? 'amber' : 'zinc');
  // A depleting resource (propellant, hull, air, shield mass). A full tank is not news, so the fill is plain
  // zinc; the thresholds are filtered notches printed under the bar, lit while the value is past them. The
  // alarm itself is the row's pips (amber, red), which lead the row in fixed columns as they do in lists.
  // Returns the severity, so the caller can light its pips.
  const NOTCHES = [{ at: 0.5, color: 'amber' }, { at: 0.2, color: 'red' }];
  function dsev(frac, notches = NOTCHES) {
    let sev = null;
    for (const nt of notches) if (frac <= nt.at && sev !== 'red') sev = nt.color;
    return sev;
  }
  function dbar(S, x, y, w, h, n, frac, o = {}) {
    const notches = o.notches ?? NOTCHES;
    S.bar(x, y, w, h, n, frac, { level: o.level ?? 'norm', gap: o.gap });
    for (const nt of notches) {
      const past = frac <= nt.at, nx = x + w * nt.at;
      S.rectLit(nx - 0.6, y + h + 1.4, 1.2, 3.4, past ? { color: nt.color, level: 'norm' } : { ghostOnly: true, color: nt.color, k: 3 });
    }
    return dsev(frac, notches);
  }
  function barRow(S, x, y, label, frac, o = {}) {
    const lw = o.labelW ?? (o.zone ? 34 : 46), bw = o.w ?? 120, bh = o.h ?? 6, n = o.n ?? 20;
    S.legend(x, y + bh, label, { size: o.labelSize ?? 8, level: o.labelLevel ?? 'dim' });
    if (o.zone) S.bar(x + lw, y, bw, bh, n, frac, { zone: o.zone, level: o.level ?? 'norm', peak: o.peak });
    else {
      const sev = dbar(S, x + lw, y, bw, bh, n, frac, { level: o.level, notches: o.notches });
      pip(S, x + lw - 11, y + bh / 2, 'amber', sev === 'amber', { r: 1.5 }); pip(S, x + lw - 5, y + bh / 2, 'red', sev === 'red', { r: 1.5 });
    }
    if (o.value != null) S.dm(x + lw + bw + 6, y + bh / 2 - 5.25, o.value, { pitch: 1.5, cells: o.valueCells ?? 4, align: 'right', level: o.valueLevel ?? 'norm', ghost: false });
  }

  // ------------------------------------------------------------ scopes
  // Sector scope: 16 bearings x 4 range rings, red threat rim. contacts: [{brg (deg), ring 0..3, hostile, lock, missile}]
  function sectorScope(S, cx, cy, r, o = {}) {
    const rings = o.rings ?? 4, n = o.sectors ?? 16, inner = r * 0.16, rimW = r * 0.08, rimGap = r * 0.04;
    const cellOuter = r - rimW - rimGap * 2 - r * 0.05;
    const ringW = (cellOuter - inner) / rings;
    const lit = {};
    for (const c of o.contacts || []) {
      const s = Math.floor(((c.brg % 360) + 360) % 360 / (360 / n));
      lit[c.ring + ':' + s] = c;
    }
    for (let k = 0; k < rings; k++) {
      const r0 = inner + k * ringW + 0.8, r1 = inner + (k + 1) * ringW - 0.8;
      S.ring(cx, cy, r0, r1, n, (i) => {
        const c = lit[k + ':' + i];
        if (!c) return null;
        return { color: 'zinc', level: c.lock ? 'full' : c.hostile || c.missile ? 'norm' : 'dim' };
      }, { start: -360 / n / 2, gapDeg: 2.4 });
    }
    // lock bearing ring (zinc, thin) and the red threat rim
    const lockBrg = (o.contacts || []).filter(c => c.lock).map(c => Math.floor(((c.brg % 360) + 360) % 360 / (360 / n)));
    S.ring(cx, cy, cellOuter + rimGap, cellOuter + rimGap + r * 0.035, n, i => lockBrg.includes(i) ? { color: 'zinc', level: 'full' } : null, { start: -360 / n / 2, gapDeg: 3 });
    const threat = new Set((o.contacts || []).filter(c => c.hostile || c.missile).map(c => Math.floor(((c.brg % 360) + 360) % 360 / (360 / n))));
    S.ring(cx, cy, r - rimW, r, n, i => threat.has(i) ? { color: 'red', level: 'full' } : null, { start: -360 / n / 2, gapDeg: 3, zone: () => 'red' });
    // own ship in the middle, pointing along its heading
    S.icon('ship', cx - inner * 0.75, cy - inner * 0.75, inner * 1.5, { level: 'norm' });
  }

  // ------------------------------------------------------------ ship plan
  // The starter corvette as the game builds it (chassis.toml): a hull of 16 x 6 tiles, a wall at x 8 with
  // a door at y 3, so two rooms: the HOLD aft (tanks, cargo bays) and the BRIDGE forward (helm, weapons
  // console, life support, reactor), open to the reactor. The engines sit outside the stern; the hull
  // art adds the bow past the layout. Tile coordinates as the deck view shows them: bow to the right,
  // port up (chassis row y is drawn at 5 - y).
  const CORVETTE = {
    w: 21, h: 6,
    hull: [[2, 0], [18, 0], [20.6, 3], [18, 6], [2, 6]],
    rooms: [{ id: 'HOLD', x: 3, y: 1, w: 5, h: 4 }, { id: 'BRIDGE', x: 8, y: 1, w: 9, h: 4 }],
    walls: [{ x: 8, y0: 1, y1: 5, door: [2, 3] }],
    engines: [[0, 1], [0, 4]],
  };
  // The gunship (chassis.gunship): a hull of 20 x 8 tiles with two walls, x 10 and x 17, each with a door
  // at y 2; ENGINES aft (life support, tanks), REACTOR amidships (reactor, shield emitter, medbay), BRIDGE
  // forward (helm, weapons console). Two Medium drives outside the stern. Port up, as on deck.
  const GUNSHIP = {
    w: 23.5, h: 8,
    hull: [[3, 0], [22, 0], [23, 1], [23, 7], [22, 8], [3, 8]],
    rooms: [{ id: 'ENGINES', x: 4, y: 1, w: 6, h: 6 }, { id: 'REACTOR', x: 10, y: 1, w: 7, h: 6 }, { id: 'BRIDGE', x: 17, y: 1, w: 5, h: 6 }],
    walls: [{ x: 10, y0: 1, y1: 7, door: [5, 6] }, { x: 17, y0: 1, y1: 7, door: [5, 6] }],
    engines: [[0, 1, 3, 2], [0, 5, 3, 2]],
  };
  // Draws the plan on a graphic dot panel. states: {ROOM: 'fire'|'breach'|'vacuum'|'smoke'|'short'};
  // crew: [[tx, ty, working, you]] in tile coordinates; marks: [[tx, ty, kind]] for a hazard in one spot.
  function shipPlan(S, x, y, w, h, o = {}) {
    const plan = o.plan ?? CORVETTE, p = o.pitch ?? 2;
    const t = Math.floor(Math.min(w / plan.w, h / plan.h) / p) * p; // a tile in units, whole dots
    const ox = x + Math.floor((w - plan.w * t) / 2 / p) * p, oy = y + Math.floor((h - plan.h * t) / 2 / p) * p;
    const X = tx => ox + tx * t, Y = ty => oy + ty * t;
    const dots = (x0, y0, x1, y1, lv, skip) => { // a line of dots, one every pitch
      const n = Math.max(1, Math.round(Math.hypot(x1 - x0, y1 - y0) / p));
      for (let i = 0; i <= n; i++) {
        const px = x0 + (x1 - x0) * i / n, py = y0 + (y1 - y0) * i / n;
        if (skip && skip(px, py)) continue;
        S.dot(px, py, { size: p * 0.78, level: lv });
      }
    };
    const lv = o.wallLevel ?? 'dim';
    // a room's pattern stops short of the crew in it, so a dot standing in a fire still reads
    const crewAt = (o.crew || []).map(c => [X(c[0] + 0.5), Y(c[1] + 0.5)]);
    const clear = (px, py) => crewAt.some(([cx, cy]) => Math.abs(px - cx) < p * 3 && Math.abs(py - cy) < p * 3);
    for (let i = 0; i < plan.hull.length; i++) {
      const a = plan.hull[i], b = plan.hull[(i + 1) % plan.hull.length];
      dots(X(a[0]), Y(a[1]), X(b[0]), Y(b[1]), lv);
    }
    for (const e of plan.engines || []) { // [x, y, w, h]: an engine bell's footprint, open toward the hull
      const ew = e[2] ?? 2, eh = e[3] ?? 1;
      dots(X(e[0]), Y(e[1]), X(e[0] + ew), Y(e[1]), lv); dots(X(e[0]), Y(e[1] + eh), X(e[0] + ew), Y(e[1] + eh), lv); dots(X(e[0]), Y(e[1]), X(e[0]), Y(e[1] + eh), lv);
    }
    for (const wl of plan.walls || []) dots(X(wl.x), Y(wl.y0), X(wl.x), Y(wl.y1), lv, (px, py) => wl.door && py > Y(wl.door[0]) + p * 0.5 && py < Y(wl.door[1]) - p * 0.5);
    for (const rm of plan.rooms) {
      const rx = X(rm.x), ry = Y(rm.y), rw = rm.w * t, rh = rm.h * t;
      const st = (o.states || {})[rm.id];
      // a legend label sits at the top of the room, and the room's pattern fills what is below it
      const named = o.labels && (st || o.labels === 'all'), top = named && o.legendLabels ? 18 : 0, fy = ry + top;
      // fills by pattern: fire a dense checker, smoke a sparse dither, breach an X, vacuum left empty
      if (st === 'fire') for (let yy = fy + p * 2; yy < ry + rh - p; yy += p) for (let xx = rx + p * 2; xx < rx + rw - p; xx += p) {
        if (Math.round((xx - rx) / p + (yy - ry) / p) % 2 === 0 && !clear(xx, yy)) S.dot(xx, yy, { size: p * 0.78, level: 'full' });
      }
      if (st === 'smoke') for (let yy = fy + p * 2; yy < ry + rh - p; yy += p * 2) for (let xx = rx + p * 2 + (Math.round((yy - ry) / p) % 4 ? p : 0); xx < rx + rw - p; xx += p * 2) if (!clear(xx, yy)) S.dot(xx, yy, { size: p * 0.7, level: 'dim' });
      if (st === 'breach') {
        const bw = Math.min(rw - p * 4, rh - top - p * 4), bx = rx + (rw - bw) / 2, by = fy + p * 2;
        dots(bx, by, bx + bw, by + bw, 'full'); dots(bx + bw, by, bx, by + bw, 'full');
      }
      if (st && st !== 'vacuum') { // a room with something wrong in it has its walls lit
        dots(rx, ry, rx + rw, ry, 'norm'); dots(rx, ry + rh, rx + rw, ry + rh, 'norm');
        dots(rx, ry, rx, ry + rh, 'norm', (px, py) => (plan.walls || []).some(wl => wl.door && Math.abs(px - X(wl.x)) < 0.1 && py > Y(wl.door[0]) + p * 0.5 && py < Y(wl.door[1]) - p * 0.5));
        dots(rx + rw, ry, rx + rw, ry + rh, 'norm', (px, py) => (plan.walls || []).some(wl => wl.door && Math.abs(px - X(wl.x)) < 0.1 && py > Y(wl.door[0]) + p * 0.5 && py < Y(wl.door[1]) - p * 0.5));
      }
      if (named && o.legendLabels) S.legend(rx + rw / 2, ry + 15, rm.id, { align: 'center', level: st ? 'norm' : 'dim', ghost: false });
      else if (named) S.dm(rx + p * 3, ry + p * 2.5, rm.id, { pitch: 1.5, level: st ? 'norm' : 'dim', ghost: false });
    }
    for (const m of o.marks || []) {
      const mx = X(m[0] + 0.5), my = Y(m[1] + 0.5);
      if (m[2] === 'short') for (const [dx, dy] of [[-1, -2], [0, -1], [-1, 0], [0, 1], [1, 0], [0, 2]]) S.dot(mx + dx * p, my + dy * p, { size: p * 0.78, level: 'full' });
      else for (let i = -2; i <= 2; i++) { S.dot(mx + i * p, my + i * p, { size: p * 0.78, level: 'full' }); if (i) S.dot(mx + i * p, my - i * p, { size: p * 0.78, level: 'full' }); }
    }
    for (const c of o.crew || []) {
      const cx = X(c[0] + 0.5), cy = Y(c[1] + 0.5);
      if (c[3]) { // the captain: a hollow square of dots
        for (const [dx, dy] of [[-1, -1], [0, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [0, 1], [1, 1]]) S.dot(cx + dx * p, cy + dy * p, { size: p * 0.8, level: 'full' });
      } else S.dot(cx, cy, { size: p * 1.3, level: 'full' });
      if (c[2]) { S.dot(cx - p * 2, cy, { size: p * 0.6, level: 'norm' }); S.dot(cx + p * 2, cy, { size: p * 0.6, level: 'norm' }); }
    }
    return { ox, oy, t, X, Y };
  }

  // ------------------------------------------------------------ projected HUD sprites
  const SPR = {
    prograde: ['..###..', '.#...#.', '#.....#', '#..#..#', '#.....#', '.#...#.', '..###..'],
    retrograde: ['..###..', '.#...#.', '#.#.#.#', '#..#..#', '#.#.#.#', '.#...#.', '..###..'],
    lead: ['...#...', '.......', '.......', '#..#..#', '.......', '.......', '...#...'],
    impact: ['#...#', '.#.#.', '..#..', '.#.#.', '#...#'],
    missile: ['.#.', '###', '.#.'],
    neutral: ['..#..', '.#.#.', '#...#', '.#.#.', '..#..'],
    station: ['#####', '#...#', '#.#.#', '#...#', '#####'],
    chevronR: ['#...', '.#..', '..#.', '...#', '..#.', '.#..', '#...'],
    chevronL: ['...#', '..#.', '.#..', '#...', '.#..', '..#.', '...#'],
    chevronU: ['...#...', '..#.#..', '.#...#.', '#.....#'],
    chevronD: ['#.....#', '.#...#.', '..#.#..', '...#...'],
    arrowR: ['..#..', '...#.', '#####', '...#.', '..#..'],
  };
  function sprite(S, name, x, y, o = {}) { S.sprite(x, y, SPR[name], { layer: 'proj', center: true, pitch: o.pitch ?? 2, color: o.color, level: o.level ?? 'norm' }); }
  // Corner brackets around a box. kind: 'square' (friendly / lock), 'tri' (hostile), 'diamond' (nav)
  function brackets(S, cx, cy, half, kind, o = {}) {
    const p = o.pitch ?? 2, L = o.len ?? 4, opt = { layer: 'proj', color: o.color, level: o.level ?? 'norm', size: p * 0.8 };
    const d = (x, y) => S.dot(x, y, opt);
    if (kind === 'diamond') {
      for (const [sx, sy] of [[0, -1], [1, 0], [0, 1], [-1, 0]]) {
        const ax = cx + sx * half * 1.25, ay = cy + sy * half * 1.25;
        for (let i = 0; i < L; i++) {
          // two short diagonal arms from each point of the diamond
          const k = i * p * 0.9;
          if (sx) { d(ax - sx * k, ay - k); d(ax - sx * k, ay + k); } else { d(ax - k, ay - sy * k); d(ax + k, ay - sy * k); }
        }
      }
      return;
    }
    for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
      const ax = cx + sx * half, ay = cy + sy * half;
      if (kind === 'tri') {
        for (let i = 0; i < L; i++) { d(ax - sx * i * p, ay); d(ax, ay - sy * i * p); }
        for (let i = 1; i < L - 1; i++) d(ax - sx * i * p, ay - sy * (L - 1 - i) * p);
      } else {
        for (let i = 0; i < L; i++) { d(ax - sx * i * p, ay); d(ax, ay - sy * i * p); }
      }
    }
    if (o.ticks) for (const [sx, sy] of [[0, -1], [1, 0], [0, 1], [-1, 0]]) {
      for (let i = 0; i < 3; i++) d(cx + sx * (half + 3 * p + i * p), cy + sy * (half + 3 * p + i * p));
    }
  }
  function label(S, x, y, lines, o = {}) {
    const p = o.pitch ?? 2;
    lines.forEach((ln, i) => {
      if (!ln) return;
      S.dm(x, y + i * 7 * p * 1.35, ln, { pitch: p, layer: 'proj', color: o.color, level: i === 0 ? (o.level ?? 'norm') : 'dim', align: o.align, ghost: false });
    });
  }

  // ------------------------------------------------------------ world (mockup backgrounds)
  // Hull art from the game: origin at the SVG origin, bow toward +x; viewBoxes from assets/art/hulls.
  const HULLS = {
    corvette: [-896, -326.4, 1420.8, 652.8], raider: [-672, -339.2, 1401.6, 678.4], freighter: [-896, -480, 1600, 960],
    station_hub: [-1408, -1152, 2816, 2304], station_outpost: [-1152, -896, 1984, 1792], picket: [-672, -307.2, 1382.4, 614.4],
    gunship: [-896, -364.8, 1804.8, 729.6], corsair: [-768, -345.6, 1587.2, 716.8], interceptor: [-672, -288, 1216, 576],
  };
  const ART = '../../../../assets/art/hulls/';
  function rng(seed) { let s = (seed >>> 0) || 1; return () => (s = (s * 1664525 + 1013904223) >>> 0) / 4294967296; }
  // Black space and a fixed starfield.
  function space(S, o = {}) {
    const b = S.base, r = rng(o.seed ?? 7);
    b.fillStyle = '#030407'; b.fillRect(0, 0, S.W, S.H);
    for (let i = 0; i < (o.stars ?? 560); i++) {
      const x = r() * S.W, y = r() * S.H, m = r(), t = r();
      const a = 0.1 + m * m * m * 0.75, s = 0.45 + m * m * 0.9;
      b.fillStyle = t < 0.12 ? `rgba(180,198,255,${a})` : t > 0.92 ? `rgba(255,228,196,${a})` : `rgba(222,228,238,${a})`;
      b.fillRect(x, y, s, s);
    }
  }
  // A hull at (x, y): k UI units per game unit (the corvette is 1,421 game units long), deg clockwise from +x.
  async function hull(S, name, x, y, k, deg = 0, o = {}) {
    const img = await loadImage(ART + name + '.svg');
    const [vx, vy, vw, vh] = HULLS[name];
    const b = S.base; b.save(); b.translate(x, y); b.rotate(deg * Math.PI / 180);
    if (o.filter) b.filter = o.filter;
    b.globalAlpha = o.alpha ?? 1;
    b.drawImage(img, vx * k, vy * k, vw * k, vh * k);
    b.restore();
  }
  // A main-engine plume behind a hull (thrusting).
  function plume(S, x, y, len, deg, o = {}) {
    const b = S.base; b.save(); b.translate(x, y); b.rotate(deg * Math.PI / 180); b.globalCompositeOperation = 'lighter';
    const g = b.createLinearGradient(0, 0, -len, 0);
    g.addColorStop(0, 'rgba(255,214,150,0.85)'); g.addColorStop(0.25, 'rgba(255,140,60,0.45)'); g.addColorStop(1, 'rgba(255,80,20,0)');
    b.fillStyle = g; b.beginPath(); b.moveTo(0, -(o.w ?? 3)); b.lineTo(-len, 0); b.lineTo(0, o.w ?? 3); b.closePath(); b.fill();
    b.restore();
  }

  // A rack of any outline: a polygon of [x, y] corners, rounded by o.radius.
  function rackShape(S, pts, o = {}) {
    const b = S.base, r = o.radius ?? 6, n = pts.length;
    const path = (inset) => {
      b.beginPath();
      const mid = (a, c) => [(a[0] + c[0]) / 2, (a[1] + c[1]) / 2];
      const m0 = mid(pts[n - 1], pts[0]); b.moveTo(m0[0], m0[1]);
      for (let i = 0; i < n; i++) { const p1 = pts[i], p2 = pts[(i + 1) % n]; b.arcTo(p1[0], p1[1], p2[0], p2[1], r - inset); }
      b.closePath();
    };
    const ys = pts.map(p => p[1]), y0 = Math.min(...ys), y1 = Math.max(...ys);
    b.save();
    b.shadowColor = 'rgba(0,0,0,0.7)'; b.shadowBlur = 16; b.shadowOffsetY = o.shadowY ?? -3;
    const g = b.createLinearGradient(0, y0, 0, y1);
    g.addColorStop(0, '#1e1f22'); g.addColorStop(0.1, '#17181a'); g.addColorStop(1, '#0d0e0f');
    b.fillStyle = g; path(0); b.fill();
    b.shadowColor = 'transparent';
    b.strokeStyle = '#2c2e32'; b.lineWidth = 1; path(0.5); b.stroke();
    b.save(); path(0); b.clip(); b.globalAlpha = 0.035; b.fillStyle = '#ffffff';
    const xs = pts.map(p => p[0]), x0 = Math.min(...xs), x1 = Math.max(...xs);
    for (let yy = y0 + 3; yy < y1 - 2; yy += 2) b.fillRect(x0 + 3, yy, x1 - x0 - 6, 0.5);
    b.restore();
    b.restore();
  }

  // ------------------------------------------------------------ projected symbology
  // Dots on a circle, every `step` units; o.skip(angle) leaves gaps.
  function ringDots(S, cx, cy, r, o = {}) {
    const n = o.n ?? Math.max(8, Math.round(2 * Math.PI * r / (o.step ?? 3.2)));
    for (let i = 0; i < n; i++) {
      const a = (i / n) * Math.PI * 2 + (o.phase ?? 0);
      if (o.skip && o.skip(a)) continue;
      S.dot(cx + Math.cos(a) * r, cy + Math.sin(a) * r, { layer: 'proj', size: o.size ?? 1.7, color: o.color, level: o.level ?? 'norm' });
    }
  }
  // Velocity markers: prograde a ring, centre dot and three wings; retrograde a ring with an X and the same wings.
  function velMark(S, kind, x, y, o = {}) {
    const r = o.r ?? 7.5, c = { layer: 'proj', color: o.color, level: o.level ?? 'norm' };
    if (o.square) { // a station or target frame: the ring becomes a square, so the frame reads by shape
      const n = Math.round(2 * r / 2.9);
      for (let i = 0; i < n; i++) {
        const t = -r + (2 * r * i) / n;
        for (const [px, py] of [[t, -r], [r, t], [-t, r], [-r, -t]]) S.dot(x + px, y + py, { ...c, size: 1.7 });
      }
    } else ringDots(S, x, y, r, { ...c, step: 2.9 });
    const d = (px, py, s) => S.dot(px, py, { ...c, size: s ?? 1.7 });
    if (kind === 'pro') d(x, y, 2.6);
    else for (const s of [-1, 1]) for (let i = -1; i <= 1; i++) if (i) { d(x + i * 2.6, y + s * i * 2.6); d(x + i * 4.4, y + s * i * 4.4, 1.4); }
    for (const [dx, dy] of [[-1, 0], [1, 0], [0, -1]]) for (let i = 1; i <= 2; i++) d(x + dx * (r + i * 2.9), y + dy * (r + i * 2.9));
    // the frame's tag reads on the side away from your own ship (o.tagLeft), never across the hull
    if (o.tag) S.dm(o.tagLeft ? x - r - 9 - S.dmWidth(o.tag.length, { pitch: 1.5 }) : x + r + 9, y - 5, o.tag, { pitch: 1.5, layer: 'proj', level: 'dim', ghost: false, color: o.color });
  }
  // Lead reticle: a ring broken on the diagonals, with a pip. At fight ranges the lead is tens of metres, so the
  // reticle sits on the target's hull (o.onTarget): the world view dims a soft disc under it, as the deck view
  // does under a tag, so its dots don't read as the hull's own lights.
  function reticle(S, x, y, o = {}) {
    const r = o.r ?? 8, c = { layer: 'proj', color: o.color, level: o.level ?? 'norm' };
    if (o.onTarget) scrim(S, x - r - 5, y - r - 5, 2 * r + 10, 2 * r + 10, r + 5);
    ringDots(S, x, y, r, { ...c, step: 2.6, skip: a => Math.abs(Math.sin(2 * a)) > 0.72 });
    S.dot(x, y, { ...c, size: 2.2 });
  }
  // Impact cross (red), a missile glyph and its predicted track.
  function cross(S, x, y, o = {}) {
    const c = { layer: 'proj', color: o.color ?? 'red', level: o.level ?? 'full', size: 1.8 };
    for (let i = -3; i <= 3; i++) { S.dot(x + i * 2.4, y + i * 2.4, c); if (i) S.dot(x + i * 2.4, y - i * 2.4, c); }
  }
  function track(S, pts, o = {}) {
    for (let i = 0; i < pts.length - 1; i++) S.dotLine(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], { layer: 'proj', step: o.step ?? 6, size: o.size ?? 1.5, color: o.color ?? 'red', level: o.level ?? 'norm', skipFirst: i > 0 });
  }
  function missile(S, x, y, o = {}) {
    S.sprite(x, y, ['..#..', '.###.', '##.##', '.###.', '..#..'], { layer: 'proj', center: true, pitch: o.pitch ?? 1.6, color: o.color ?? 'red', level: o.level ?? 'full' });
  }
  // A fire arc: two radial dot lines and the arc between them (deg clockwise from +x).
  function arc(S, cx, cy, r0, r1, a0, a1, o = {}) {
    const c = { layer: 'proj', color: o.color, level: o.level ?? 'dim', step: o.step ?? 5, size: o.size ?? 1.3 };
    const R = Math.PI / 180;
    for (const a of [a0, a1]) S.dotLine(cx + Math.cos(a * R) * r0, cy + Math.sin(a * R) * r0, cx + Math.cos(a * R) * r1, cy + Math.sin(a * R) * r1, c);
    const n = Math.max(2, Math.round((a1 - a0) * R * r1 / c.step));
    for (let i = 0; i <= n; i++) { const a = (a0 + (a1 - a0) * i / n) * R; S.dot(cx + Math.cos(a) * r1, cy + Math.sin(a) * r1, c); }
  }
  // An edge marker: a chevron at the screen edge pointing out, and its label inside.
  function edge(S, x, y, dir, lines, o = {}) {
    const spr = { r: 'chevronR', l: 'chevronL', u: 'chevronU', d: 'chevronD' }[dir];
    S.sprite(x, y, SPR[spr], { layer: 'proj', center: true, pitch: 2, color: o.color, level: o.level ?? 'norm' });
    const p = o.pitch ?? 2, w = Math.max(...lines.map(l => l.length)) * 6 * p;
    const lx = dir === 'r' ? x - 10 - w : dir === 'l' ? x + 10 : x - w / 2;
    const ly = dir === 'd' ? y - 14 - lines.length * 7 * p * 1.35 : dir === 'u' ? y + 12 : y - 5;
    label(S, lx, ly, lines, { pitch: p, color: o.color, level: o.level });
  }
  // The deck view darkens a soft pill under each tag (the world is dimmed; the projector can't draw black).
  function scrim(S, x, y, w, h, radius = 4) {
    const b = S.base; b.save(); b.filter = 'blur(4px)'; b.fillStyle = 'rgba(0,0,0,0.62)';
    b.beginPath(); b.roundRect(x, y, w, h, radius); b.fill(); b.restore();
  }
  // A hazard tag on the deck: the severity sprite and the short code (or a name), 5x7 at pitch 2 over a scrim.
  // Warnings are red with a diamond, cautions amber with a triangle: the shape is the colour-free channel.
  function deckTag(S, x, y, text, sev, o = {}) {
    const p = 2, color = sev === 'warning' ? 'red' : sev === 'caution' ? 'amber' : o.color;
    const glyph = sev === 'warning' ? '♦' : sev === 'caution' ? '▲' : '';
    const str = (glyph ? glyph + ' ' : '') + text, w = str.length * 6 * p - p;
    const lx = x - w / 2, ly = y - 3.5 * p;
    scrim(S, lx - 6, ly - 5, w + 12, 7 * p + 10);
    S.dm(lx, ly, str, { pitch: p, layer: 'proj', color, level: o.level ?? 'norm', ghost: false });
    return w + 12;
  }
  // A row of state legends, lit or ghost: [['ASSIST', true], ['HOLD', false]]. Returns the end x.
  function states(S, x, y, items, o = {}) {
    let cx = x;
    for (const it of items) {
      const [txt, on, color] = it;
      const w = S.legendWidth(txt, { size: o.size ?? 13 });
      S.legend(cx, y, txt, { size: o.size ?? 13, lit: !!on, level: on === 'dim' ? 'dim' : (o.level ?? 'norm'), color: color ?? o.color, box: o.box });
      cx += w + (o.gap ?? 9);
    }
    return cx;
  }

  // Reports a row that ran past its limit (layout is by hand; this catches what the eye might miss).
  function fits(x, limit, what) { if (x > limit + 0.5) console.warn(`overflow: ${what} ends at ${x.toFixed(1)}, limit ${limit}`); return x; }

  return { scrim, deckTag, fits, plate, knock, pip, rackShape, space, hull, plume, ringDots, velMark, reticle, cross, track, missile, arc, edge, states, rng, scene, loadImage, world, keycap, verbs, titled, slab, tile, softKeys, field, table, barRow, dbar, dsev, DEPLETE, RISING, sectorScope, shipPlan, CORVETTE, GUNSHIP, sprite, SPR, brackets, label };
})();
