// FILAMENT x Task-driven design (functional-task.md, round 9): the HUD as a late-90s digital dash. No racks
// span the view; each corner carries its own moulded pod, like a car's instrument cluster, trip computer and
// message centre. The annunciator is a cluster of tell-tales; the alert band is a message centre, a red
// window for the one warning with an amber row hung under it for the cautions; the hint line is a strip of
// key caps. Laid at the minimum sizes (guide section 15): column text is 5x7 at pitch 1.5, 9 units a cell.
(() => {
  const K = KIT, V = VFD;
  const R = Math.PI / 180;
  const P = 1.5, CELL = 9;
  const LX = 16, RX = 964, CW = 300; // the left and right columns
  const BX = 340, BW = 600; // the message centre (Z2)
  const fit = (x0, x1, p = P) => Math.floor((x1 - x0 + p) / (6 * p)); // dot-matrix cells between two x

  // ------------------------------------------------------------------ small parts
  // An icon cut out of whatever is lit (a tell-tale's icon in a red block).
  function knockIcon(S, name, x, y, size) {
    const parts = V.ICONS[name]; if (!parts) return;
    const k = size / 16;
    S.cut(ctx => {
      ctx.translate(x, y); ctx.scale(k, k);
      ctx.fillStyle = '#000'; ctx.strokeStyle = '#000'; ctx.lineWidth = 1.7;
      for (const pt of parts) {
        if (pt[0] === 'p') { ctx.beginPath(); pt[1].forEach((q, i) => (i ? ctx.lineTo(q[0], q[1]) : ctx.moveTo(q[0], q[1]))); ctx.closePath(); ctx.fill(); }
        else if (pt[0] === 'l') { ctx.beginPath(); pt[1].forEach((q, i) => (i ? ctx.lineTo(q[0], q[1]) : ctx.moveTo(q[0], q[1]))); ctx.stroke(); }
        else if (pt[0] === 'c') { ctx.beginPath(); ctx.arc(pt[1], pt[2], pt[3], (pt[4] - 90) * R, (pt[5] - 90) * R); ctx.stroke(); }
        else if (pt[0] === 'd') { ctx.beginPath(); ctx.arc(pt[1], pt[2], pt[3], 0, Math.PI * 2); ctx.fill(); }
      }
    });
  }
  // A graphic dot panel's grid, seen unlit through the glass.
  function grid(S, x, y, w, h, p = 4) {
    for (let yy = y + p / 2; yy < y + h; yy += p) for (let xx = x + p / 2; xx < x + w; xx += p) S.dot(xx, yy, { size: 1.05, ghostOnly: true, k: 0.9 });
  }
  function ring(S, cx, cy, r, o = {}) {
    const n = Math.max(12, Math.round(2 * Math.PI * r / (o.step ?? 4)));
    for (let i = 0; i < n; i++) { const a = i / n * Math.PI * 2; S.dot(cx + Math.cos(a) * r, cy + Math.sin(a) * r, { size: o.size ?? 1.2, level: o.level ?? 'dim', color: o.color }); }
  }
  // Key caps for a key string: 'W A S D' is four caps. Returns the width.
  function caps(S, x, y, keys, h = 18) {
    let cx = x;
    for (const k of keys.split(' ')) cx += K.keycap(S, cx, y, k, { h, size: 12 }) + 3;
    return cx - 3 - x;
  }
  function capsWidth(S, keys, h = 18) {
    return keys.split(' ').reduce((a, k) => a + Math.max(h, S.legendWidth(k, { size: 12, weight: 600, condense: 0.86, track: 0.1 }) + 7) + 3, 0) - 3;
  }

  // ------------------------------------------------------------------ Z1 + Z4: the cluster (CHR over ANN)
  function cluster(S, d) {
    K.plate(S, LX, 14, CW, 140, { pad: 6, radius: 8 });
    chr(S, d.chr);
    ann(S, d.ann || {});
  }
  // CHR: day and time, the warp rate, and on the second line the next stop or why warp is held.
  function chr(S, d) {
    const x = LX, y = 14, w = CW, h = 52, b1 = y + 22, b2 = y + 44;
    S.module(x, y, w, h, { filaments: 2 });
    let cx = x + 8;
    cx += S.legend(cx, b1, 'DAY', { level: 'dim' }) + 5;
    cx += S.seg7(cx, b1 - 15, 15, d.day, { cells: 3, level: 'norm' }) + 10;
    cx += S.seg7(cx, b1 - 15, 15, d.time, { cells: 6, level: 'norm' });
    // warp: dim at 1x, the loudest thing on the module while warping
    const one = d.warp === '1';
    let rx = x + w - 8 - S.legendWidth('RAILS');
    S.legend(rx, b1, 'RAILS', { lit: !!d.rails, level: 'full' });
    rx -= 6 + S.legendWidth('X'); S.legend(rx, b1, 'X', { level: one ? 'dim' : 'full' });
    rx -= 3 + S.seg7Width(12, 5); S.seg7(rx, b1 - 12, 12, d.warp, { cells: 5, level: one ? 'dim' : 'full' });
    K.fits(cx + 8, rx, 'CHR first line');
    // two fixed legends say what the words after them are: the next stop, or why warp is held
    let nx = x + 8;
    nx += S.legend(nx, b2, 'NEXT', { lit: !!d.next, level: 'dim' }) + 6;
    nx += S.legend(nx, b2, 'HELD', { lit: !!d.held, level: 'full' }) + 8;
    S.dm(nx, b2 - 10.5, d.held || d.next || '', { pitch: P, cells: fit(nx, x + w - 8), level: d.held ? 'full' : 'norm' });
  }
  // ANN: ten tell-tales in two rows. Each cell is printed in register: its frame behind amber, its block behind
  // red, its icon and word clear. Advisory lights the icon and word softly; a caution adds the amber frame; a
  // warning lights the red block with the icon and word cut out of it. No SHIELD on a ship without an emitter:
  // that place is blank glass.
  const LAMPS = [['HULL', 'breach'], ['FIRE', 'fire'], ['AIR', 'o2'], ['POWER', 'power'], ['PROP', 'prop'],
    ['DRIVE', 'drive'], ['ARMS', 'missile'], ['CREW', 'crew'], ['EVA', 'eva'], ['SHIELD', 'shield']];
  function ann(S, st, o = {}) {
    const x = LX, y = 74, w = CW, h = 80, gap = 4;
    S.module(x, y, w, h, { filaments: 0 });
    const cw = (w - 8 - gap * 4) / 5, ch = (h - 8 - gap) / 2;
    LAMPS.forEach(([tag, icon], i) => {
      if (tag === 'SHIELD' && !o.shields) return;
      lamp(S, x + 4 + (i % 5) * (cw + gap), y + 4 + Math.floor(i / 5) * (ch + gap), cw, ch, tag, icon, st[tag]);
    });
  }
  function lamp(S, x, y, w, h, tag, icon, sev) {
    const is = 14, ix = x + w / 2 - is / 2, iy = y + 4, tx = x + w / 2, ty = y + h - 5;
    if (sev === 'warning') {
      S.rectLit(x + 1, y + 1, w - 2, h - 2, { color: 'red', level: 'norm', r: 1.2 });
      knockIcon(S, icon, ix, iy, is);
      S.knockout(tx, ty, tag, { size: 12, align: 'center' });
      return;
    }
    S.frameLit(x, y, w, h, 1.4, sev === 'caution' ? { color: 'amber', level: 'full' } : { ghostOnly: true, color: 'amber', k: 1.2 });
    const lv = sev === 'caution' ? 'norm' : 'dim', lit = sev === 'caution' || sev === 'advisory';
    S.icon(icon, ix, iy, is, { lit, level: lv, stroke: 1.6 });
    S.legend(tx, ty, tag, { size: 12, lit, level: lv, align: 'center' });
  }

  // ------------------------------------------------------------------ Z4: PER, the captain's own kit (LCD)
  function per(S, d) {
    const x = LX, y = 168, w = CW, h = 64;
    K.plate(S, x, y, w, h, { pad: 6, radius: 8 });
    S.lcdPanel(x, y, w, h, { backlight: d.alert });
    S.lcdText(x + 10, y + 19, d.health);
    S.lcdText(x + 10, y + 38, d.suit);
    S.lcdText(x + 10, y + 57, d.here);
    S.lcdText(x + 216, y + 19, 'AIR');
    S.lcdSeg7(x + 216, y + 26, 24, d.air, { slant: 6 });
  }

  // ------------------------------------------------------------------ Z2: the message centre (ALR)
  // A red window for the one standing warning, and an amber row hung under it for three cautions and a count
  // of the rest. Each row keeps its place: with no warning the red window stays, unlit.
  function band(S, d = {}) {
    K.plate(S, BX, 12, BW, 96, { pad: 6, radius: 8 });
    warnWindow(S, d.warning || {});
    cautionRow(S, d.cautions || [], d.more || 0);
  }
  function warnWindow(S, d) {
    const x = BX, y = 12, w = BW, h = 56, base = y + 32;
    S.module(x, y, w, h, { filaments: 2 });
    // the first line stands on one baseline: the title, what it is, then the one number, biggest, at the end
    S.seg14(x + 8, base - 16, 16, d.title || '', { cells: 10, color: 'red', level: 'full' });
    const ux = x + w - 8 - S.dmWidth(1, { pitch: 2 });
    S.dm(ux, base - 14, d.unit || '', { pitch: 2, cells: 1, color: 'red', level: 'norm' });
    const nx = ux - 6 - S.seg7Width(28, 3, { colons: 1 });
    S.seg7(nx, base - 28, 28, d.number || '', { cells: 3, color: 'red', level: 'full' });
    const lx = nx - 8 - S.dmWidth(6, { pitch: P });
    S.dm(lx, base - 10.5, d.label || '', { pitch: P, cells: 6, color: 'red', level: 'norm', align: 'right' });
    const dx = x + 8 + S.seg14Width(16, 10) + 12;
    S.dm(dx, base - 14, d.detail || '', { pitch: 2, cells: fit(dx, lx - 8, 2), color: 'red', level: 'norm' });
    // the second line: the action that fits the post, with its key, then what the ship is doing about it
    let ax = x + 8;
    if (d.key) ax += K.keycap(S, ax, y + 37, d.key, { h: 16, size: 12 }) + 6;
    const ac = fit(ax, x + 200);
    S.dm(ax, y + 40, d.action || '', { pitch: P, cells: ac, color: 'red', level: 'full' });
    const nx2 = ax + ac * CELL + 8;
    S.dm(nx2, y + 40, d.note || '', { pitch: P, cells: fit(nx2, x + w - 8), color: 'red', level: 'dim' });
  }
  // A caution cell is two lines of 19: what and where, then who is on it or the key. Each line takes its part
  // of the alert's full text when that fits, and the same part of the short form when it doesn't.
  function cautionRow(S, list, more) {
    const x = BX, y = 74, w = BW, h = 34, cw = 177, gap = 4;
    S.module(x, y, w, h, { filaments: 1 });
    const am = { pitch: P, color: 'amber' };
    for (let i = 0; i < 3; i++) {
      const c = list[i], cx = x + 4 + i * (cw + gap);
      if (i) S.rectLit(cx - gap / 2 - 0.4, y + 5, 0.8, h - 10, { ghostOnly: true, color: 'amber', k: 2 });
      const lines = c ? [0, 1].map(j => (c.full[j].length <= 19 ? c.full[j] : c.short[j])) : ['', ''];
      S.dm(cx + 4, y + 5, lines[0], { ...am, cells: 19, level: 'full' });
      S.dm(cx + 4, y + 19, lines[1], { ...am, cells: 19, level: 'norm' });
    }
    const mx = x + 4 + 3 * (cw + gap);
    S.rectLit(mx - gap / 2 - 0.4, y + 5, 0.8, h - 10, { ghostOnly: true, color: 'amber', k: 2 });
    S.dm(mx + 4, y + 5, more ? '+' + more : '', { ...am, cells: 2, level: 'full' });
    S.dm(mx + 4, y + 19, more ? 'MORE' : '', { ...am, cells: 4, level: 'norm' });
    K.fits(mx + 4 + S.dmWidth(4, { pitch: P }), x + w - 4, 'caution row');
  }

  // ------------------------------------------------------------------ Z3: OBJ and NTF (the trip computer)
  // A row is two lines: the contract and its time left, then its next step, with LATE in its amber place.
  function obj(S, rows, y = 14) {
    const x = RX, w = CW, rh = 32, h = 4 + rows.length * rh;
    K.slab(S, x, y, w, h, { pad: 5 });
    const tw = S.seg7Width(13, 5), tx = x + w - 6 - tw, lw = S.legendWidth('LATE');
    let ry = y + 4;
    for (const r of rows) {
      S.seg7(tx, ry + 1, 13, r.left, { cells: 5, level: 'norm' });
      S.dm(x + 6, ry + 3, r.title, { pitch: P, cells: fit(x + 6, tx - 8), level: 'norm' });
      S.dm(x + 15, ry + 17.5, r.next || '', { pitch: P, cells: fit(x + 15, x + w - 6 - lw - 8), level: 'dim' });
      S.legend(x + w - 6 - lw, ry + 28, 'LATE', { color: 'amber', lit: !!r.late, level: 'full' });
      ry += rh;
    }
    return y + h;
  }
  function ntf(S, y, lines) {
    const x = RX, w = CW, h = 4 + lines.length * 15;
    K.slab(S, x, y, w, h, { pad: 5 });
    lines.forEach((l, i) => S.dm(x + 6, y + 4 + i * 15, l, { pitch: P, cells: fit(x + 6, x + w - 6), level: 'norm' }));
  }

  // ------------------------------------------------------------------ Z6: TGT
  function tgt(S, d) {
    const x = RX, y = 380, w = CW, h = 166, L = x + 6, V0 = x + 56;
    const row = i => y + 18 + i * 20;
    K.slab(S, x, y, w, h, { pad: 5 });
    // relation: five fixed words, only the true one lit, HOSTILE behind red glass
    const rel = n => d.rel === n && 'norm';
    K.fits(K.states(S, L, row(0), [['OWN', rel('OWN')], ['FRIEND', rel('FRIEND')], ['NEUTRAL', rel('NEUTRAL')], ['HOSTILE', rel('HOSTILE'), 'red'], ['INERT', rel('INERT')]], { gap: 9 }) - 9, x + w - 6, 'TGT relations');
    // the name, big, and the contract it belongs to at the end of the line
    S.dm(L, row(1) - 14, d.name, { pitch: 2, cells: 14, level: 'full' });
    const cw = S.dmWidth(8, { pitch: P });
    S.icon('target', x + w - 6 - cw - 18, row(1) - 12, 13, { lit: !!d.contract, level: 'norm' });
    S.dm(x + w - 6 - cw, row(1) - 10.5, d.contract || '', { pitch: P, cells: 8, level: 'norm' });
    S.dm(L, row(2) - 10.5, d.cls, { pitch: P, cells: fit(L, x + w - 6), level: 'dim' });
    K.states(S, L, row(3), [['FIRING ON YOU', d.firing && 'full', 'red'], ['HAS YOU LOCKED', d.locked && 'norm', 'red']], { gap: 12 });
    const lab = (t, i) => S.legend(L, row(i), t, { level: 'dim' });
    lab('STATE', 4); S.dm(V0, row(4) - 10.5, d.state, { pitch: P, cells: fit(V0, x + w - 6), level: 'norm' });
    // hull as the sensors guess it: four steps, never a percentage the sensors can't know
    lab('HULL', 5);
    S.bar(V0, row(5) - 9, 52, 9, 4, d.hull / 4, { level: 'norm', gap: 2 });
    const hw = V0 + 60;
    S.dm(hw, row(5) - 10.5, d.hullWord, { pitch: P, cells: 15, level: 'norm' });
    S.legend(x + w - 6, row(5), 'EST', { level: 'dim', align: 'right' });
    // range and how fast it changes; then the closest approach
    lab('RANGE', 6);
    let cx = V0 + S.seg7(V0, row(6) - 15, 15, d.range, { cells: 3, level: 'norm' }) + 5;
    cx += S.legend(cx, row(6), 'KM', { level: 'dim' }) + 14;
    cx += S.dm(cx, row(6) - 10.5, d.closing, { pitch: P, cells: 7, level: 'norm' }) + 8;
    cx += S.seg7(cx, row(6) - 13, 13, d.rate, { cells: 3, level: 'norm' }) + 5;
    K.fits(cx + S.legend(cx, row(6), 'M/S', { level: 'dim' }), x + w - 6, 'TGT range row');
    lab('CPA', 7);
    cx = V0 + S.seg7(V0, row(7) - 13, 13, d.cpa, { cells: 3, level: 'norm' }) + 5;
    cx += S.legend(cx, row(7), 'KM IN', { level: 'dim' }) + 6;
    K.fits(cx + S.seg7(cx, row(7) - 13, 13, d.cpaT, { cells: 4, level: 'norm' }), x + w - 6, 'TGT CPA row');
  }

  // ------------------------------------------------------------------ Z7: the weapons pod (WPN + SNS)
  function weaponsPod(S) {
    K.rackShape(S, [[692, 612], [1080, 612], [1080, 570], [1272, 570], [1272, 764], [692, 764]], { radius: 8, shadowY: 3 });
  }
  function scopePod(S) {
    K.rackShape(S, [[1080, 570], [1272, 570], [1272, 764], [1080, 764]], { radius: 8, shadowY: 3 });
  }
  // WPN expanded: what is in hand, then a row per mount, then the decoy. Fixed columns, so the eye runs down
  // STATE to see what will fire.
  const COL = { pos: 15, gun: 91, who: 158, st: 225, tof: 337 };
  function wpn(S, d) {
    const x = 700, y = 620, w = 380, h = 38 + d.mounts.length * 14 + 28;
    S.module(x, y, w, h, { filaments: 4 });
    S.dm(x + 6, y + 6, d.head, { pitch: P, cells: fit(x + 6, x + w - 6), level: 'norm' });
    const hd = (t, c, n) => S.dm(x + COL[c], y + 22, t, { pitch: P, cells: n, level: 'dim', ghost: false });
    hd('MOUNT', 'pos', 8); hd('GUN', 'gun', 7); hd('WHO', 'who', 7); hd('STATE', 'st', 12); hd('TOF', 'tof', 4);
    d.mounts.forEach((m, i) => {
      const ry = y + 38 + i * 14;
      // pips first: amber for a gun that can't fire for now, red for one that is broken
      K.pip(S, x + 5, ry + 5.25, 'amber', m.sev === 'caution', { r: 1.5 }); K.pip(S, x + 10, ry + 5.25, 'red', m.sev === 'warning', { r: 1.5 });
      S.dm(x + COL.pos, ry, m.pos, { pitch: P, cells: 8, level: 'norm' });
      S.dm(x + COL.gun, ry, m.gun, { pitch: P, cells: 7, level: 'dim' });
      S.dm(x + COL.who, ry, m.who, { pitch: P, cells: 7, level: m.who === 'HAND' ? 'full' : 'norm' });
      S.dm(x + COL.st, ry, m.state, { pitch: P, cells: 12, level: 'norm' });
      S.dm(x + COL.tof, ry, m.tof || '', { pitch: P, cells: 4, level: 'dim' });
    });
    K.fits(x + COL.tof + S.dmWidth(4, { pitch: P }), x + w - 6, 'WPN columns');
    const by = y + 38 + d.mounts.length * 14 + 2;
    S.rectLit(x + 6, by, w - 12, 0.7, { ghostOnly: true, k: 2 });
    // the decoy: unlimited, so READY or the seconds until it is
    const b = by + 19;
    let cx = x + 15 + S.legend(x + 15, b, 'DECOY', { level: 'dim' }) + 10;
    cx += S.legend(cx, b, 'READY', { lit: d.decoy === 'READY', level: 'norm' }) + 10;
    cx += S.seg7(cx, b - 12, 12, d.decoy === 'READY' ? '' : d.decoy, { cells: 1, level: 'norm' }) + 4;
    S.legend(cx, b, 'S', { level: 'dim', lit: d.decoy !== 'READY' });
  }
  // SNS: a square graphic dot panel, north up, ranges as dotted rings; the range keys on the pod below it.
  function sns(S, d) {
    const x = 1088, y = 578, w = 176, h = 152;
    S.module(x, y, w, h, { filaments: 3 });
    grid(S, x + 2, y + 2, w - 4, h - 4, 4);
    const cx = x + w / 2, cy = y + h / 2, r = 60;
    ring(S, cx, cy, r * 0.5); ring(S, cx, cy, r);
    S.dotLine(cx, cy, cx + Math.cos(d.heading * R) * (r - 6), cy + Math.sin(d.heading * R) * (r - 6), { step: 3, size: 1.2, level: 'dim' });
    S.sprite(cx, cy, ['.#.', '###', '.#.'], { center: true, pitch: 1.6, level: 'norm' });
    for (const c of d.contacts) {
      const px = cx + c.x * r, py = cy + c.y * r;
      if (c.kind === 'hostile') S.sprite(px, py, ['..#..', '.#.#.', '#####'], { center: true, pitch: 1.6, color: 'red', level: 'norm' });
      if (c.kind === 'missile') S.sprite(px, py, ['.#.', '###', '.#.'], { center: true, pitch: 1.3, color: 'red', level: 'full' });
      if (c.kind === 'neutral') S.sprite(px, py, ['..#..', '.#.#.', '#...#', '.#.#.', '..#..'], { center: true, pitch: 1.4, level: 'dim' });
      if (c.kind === 'station') S.sprite(px, py, ['#####', '#...#', '#.#.#', '#...#', '#####'], { center: true, pitch: 1.4, level: 'norm' });
      if (c.lock) for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) for (const [ax, ay] of [[0, 0], [-sx, 0], [0, -sy]]) S.dot(px + sx * 7 + ax * 2, py + sy * 7 + ay * 2, { size: 1.3, level: 'full' });
    }
    // the corners hold the words: hostiles top left, the range bottom left, AUTO bottom right
    const hc = d.hostiles || 0;
    // two cells, right aligned: a lone slanted 1 reads as a slash without the ghost 8 beside it
    const hx = x + 6 + S.seg7(x + 6, y + 5, 12, hc ? String(hc) : '', { cells: 2, color: 'red', level: 'norm' }) + 7;
    S.legend(hx, y + 17, 'HOSTILE', { color: 'red', lit: hc > 0, level: 'norm' });
    S.legend(x + 6, y + h - 6, d.range, { level: 'norm' });
    S.legend(x + w - 8, y + h - 6, 'AUTO', { lit: !!d.auto, level: 'dim', align: 'right', box: true });
    S.key(x + w - 54, y + h + 6, 24, 24, '−', { size: 14 }); S.key(x + w - 26, y + h + 6, 24, 24, '+', { size: 14 });
  }

  // ------------------------------------------------------------------ Z5: SCH, the ship schematic
  // No text: rooms are named on hover. What is wrong is drawn in its room; crew are dots, the captain a ring.
  function sch(S, d) {
    const x = LX, y = 646, w = CW, h = 112;
    K.plate(S, x, y, w, h, { pad: 6, radius: 8 });
    S.module(x, y, w, h, { filaments: 3 });
    grid(S, x + 2, y + 2, w - 4, h - 4, 4);
    K.shipPlan(S, x + 10, y + 10, w - 20, h - 20, { states: d.states, crew: d.crew, marks: d.marks });
  }

  // ------------------------------------------------------------------ Z8: FLT and HLM
  const FL = { x: 420, y: 662, w: 440, h: 96 };
  function flt(S, d) {
    const { x, y, w, h } = FL;
    K.plate(S, x, y, w, h, { pad: 6, radius: 9 });
    S.module(x, y, w, h, { filaments: 4 });
    // the four modes as fixed legends, the one in use inverse, like a deck's PLAY / REC / FF row
    let mx = x + 10;
    for (const m of ['DEPARTURE', 'DOCK', 'BURN', 'CRUISE']) {
      const mw = S.legendWidth(m);
      if (m === d.mode) { S.rectLit(mx - 4, y + 5, mw + 8, 17, { level: 'norm', r: 1 }); S.knockout(mx, y + 18, m); }
      else S.legend(mx, y + 18, m, { lit: false });
      mx += mw + 18;
    }
    // CRUISE: the frame, its distance and the speed in it
    let fx = x + 10 + S.legend(x + 10, y + 40, 'FRAME', { level: 'dim' }) + 6;
    fx += S.seg7(fx, y + 28, 12, d.frameKm || '', { cells: 3, level: 'norm' }) + 5;
    S.legend(fx, y + 40, 'KM', { level: 'dim', lit: !!d.frameKm });
    S.dm(x + 10, y + 46, d.frame, { pitch: P, cells: 16, level: 'norm' });
    const sx = x + 176, sw = S.seg7(sx, y + 27, 30, d.speed, { cells: 5, level: 'norm' });
    S.legend(sx + sw + 6, y + 57, 'M/S', { level: 'dim' });
    // indicators, dark at rest: assist only when it is off, the drives while they fire
    const ix = x + 336;
    S.legend(ix, y + 40, 'ASSIST OFF', { lit: !!d.assistOff, level: 'full', box: true });
    K.states(S, ix, y + 60, [['MAIN', d.main && 'norm'], ['RCS', d.rcs && 'norm']], { gap: 10 });
    // delta-v left, with what home takes as a notch and what the plan needs as a bracket
    const b = y + 86;
    let vx = x + 10 + S.legend(x + 10, b, 'ΔV', { level: 'dim' }) + 6;
    vx += S.seg7(vx, b - 15, 15, d.dv.toLocaleString('en-US'), { cells: 5, level: 'norm' }) + 8;
    vx += S.legend(vx, b, 'M/S', { level: 'dim' }) + 10;
    const hx0 = x + w - 10 - S.seg7Width(12, 4), hl = S.legendWidth('HOME');
    const bx = vx, bw = hx0 - 6 - hl - 12 - bx, by = b - 9;
    S.bar(bx, by, bw, 8, 40, d.dv / d.dvFull, { level: 'dim', gap: 1.1 });
    const hx = bx + bw * d.home / d.dvFull;
    S.rectLit(hx - 0.7, by + 10, 1.4, 5, { level: 'norm' });
    S.dot(hx - 2.2, by + 12, { size: 1.1, level: 'norm' }); S.dot(hx + 2.2, by + 12, { size: 1.1, level: 'norm' });
    S.legend(hx0 - 6 - hl, b, 'HOME', { level: 'dim' });
    S.seg7(hx0, b - 12, 12, d.home.toLocaleString('en-US'), { cells: 4, level: 'dim' });
  }
  // HLM: who flies and the standing order, one line; never a countdown.
  // Two rows: who has the helm, then the standing order in a window as wide as the slab, so the spec's orders
  // fit whole ("approaching Raider to 2,000 m" is 29 cells); one longer than its window marquees, as the ticker does.
  function hlm(S, x, y, w, d) {
    K.slab(S, x, y, w, 40, { pad: 4 });
    const cx = x + 6 + S.legend(x + 6, y + 17, 'HELM', { level: 'dim' }) + 8;
    S.dm(cx, y + 6.75, d.who, { pitch: P, cells: 5, level: 'norm' });
    S.dm(x + 6, y + 22.75, d.order, { pitch: P, cells: fit(x + 6, x + w - 6), level: 'norm' });
  }

  // ------------------------------------------------------------------ Z9: HNT, the key strip
  // Key caps on a strip along the bottom edge, each with its word in a small window; a key already shown by a
  // mode, an alert or the prompt never repeats here.
  function hnt(S, items) {
    const x = LX, y = 768, h = 24;
    const parts = items.map(([keys, word]) => ({ keys, word, kw: capsWidth(S, keys), ww: S.dmWidth(word.length, { pitch: P }) + 12 }));
    const w = 6 + parts.reduce((a, p) => a + p.kw + 4 + p.ww + 12, 0) - 12 + 6;
    K.plate(S, x, y, w, h, { pad: 3, radius: 6 });
    let cx = x + 6;
    for (const p of parts) {
      cx += caps(S, cx, y + 3, p.keys) + 4;
      S.module(cx, y + 3, p.ww, 18, { filaments: 0, glare: false, radius: 2 });
      S.dm(cx + 6, y + 6.75, p.word, { pitch: P, level: 'dim' });
      cx += p.ww + 12;
    }
    K.fits(x + w, 1264, 'key strip');
  }

  // ------------------------------------------------------------------ world helpers
  // A deck hazard's tag: its name, then who is working it as a bar of projected dots, or NOBODY.
  function hazard(S, x, y, text, sev, work) {
    K.deckTag(S, x, y, text, sev);
    const ly = y + 16;
    if (work == null) {
      const w = S.dmWidth(6, { pitch: 2 });
      K.scrim(S, x - w / 2 - 6, ly - 5, w + 12, 24);
      S.dm(x - w / 2, ly, 'NOBODY', { pitch: 2, layer: 'proj', color: 'amber', level: 'dim', ghost: false });
      return;
    }
    const n = 14, step = 5, w = (n - 1) * step, done = Math.round(work * n);
    K.scrim(S, x - w / 2 - 6, ly - 4, w + 12, 14);
    for (let i = 0; i < n; i++) S.dot(x - w / 2 + i * step, ly + 3, { layer: 'proj', size: 2.4, level: i < done ? 'norm' : 'dim' });
  }
  // A console's prompt (guide 7.10): a small slab, the console's name in legend type, then each verb behind its key cap.
  function prompt(S, x, y, name, verbs) {
    const vw = verbs.reduce((a, [k, v]) => a + capsWidth(S, k, 16) + 4 + S.dmWidth(v.length, { pitch: P }) + 10, 0) - 10;
    const w = 8 + S.legendWidth(name) + 12 + vw + 8;
    K.slab(S, x, y, w, 24, { pad: 4 });
    let cx = x + 8 + S.legend(x + 8, y + 17, name, { level: 'dim' }) + 12;
    for (const [k, v] of verbs) { cx += caps(S, cx, y + 4, k, 16) + 4; cx += S.dm(cx, y + 6.75, v, { pitch: P, level: 'full' }) + 10; }
    return w;
  }
  function glow(S, x, y, r, c) {
    const b = S.base; b.save(); b.globalCompositeOperation = 'lighter';
    const rg = b.createRadialGradient(x, y, 0, x, y, r); rg.addColorStop(0, c); rg.addColorStop(1, 'rgba(0,0,0,0)');
    b.fillStyle = rg; b.fillRect(x - r, y - r, r * 2, r * 2); b.restore();
  }

  // ================================================================== Helm at rest
  SCREENS['tk-helm-quiet'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 12 });
    const k = 0.085;
    await K.hull(S, 'station_hub', 452, 286, k, 0);
    await K.hull(S, 'freighter', 812, 498, k, 150);
    await K.hull(S, 'corvette', 640, 400, k, -4);
    // stations always carry their name; the passing freighter is a glyph
    K.sprite(S, 'station', 452, 286, { pitch: 2, level: 'dim' });
    K.label(S, 582, 279, ['HIGHPORT'], { pitch: 2, level: 'norm' });
    K.sprite(S, 'neutral', 812, 498, { pitch: 2, level: 'dim' });
    // prograde in the frame FLT names: a square, because the frame is a station, not the orbit
    const a = -4 * R;
    K.velMark(S, 'pro', 640 + Math.cos(a) * 96, 400 + Math.sin(a) * 96, { level: 'norm', square: true });
    S.projectNow();

    cluster(S, { chr: { day: '3', time: '14:22:05', warp: '1' }, ann: {} });
    scopePod(S);
    // the 5 km step holds Highport, the nearest contact, 3.0 km off; a freighter 4.1 km out, another past the rim
    sns(S, { heading: -4, range: '5 KM', auto: true, contacts: [{ x: -0.508, y: -0.321, kind: 'station' }, { x: 0.72, y: 0.4, kind: 'neutral' }, { x: 0.82, y: -0.56, kind: 'neutral' }] });
    flt(S, { mode: 'CRUISE', frame: 'HIGHPORT', frameKm: '3.0', speed: '0.4', dv: 7007, dvFull: 9200, home: 410 });
    hnt(S, [['W S', 'THRUST'], ['A D', 'TURN'], ['M', 'MAP'], ['Q', 'DESTINATION'], ['F1', 'CONTROLS']]);
    S.finish();
  };

  // ================================================================== Gunnery in combat
  SCREENS['tk-gunnery-combat'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 44 });
    await K.hull(S, 'raider', 842, 268, 0.08, 170);
    await K.hull(S, 'corvette', 640, 400, 0.09, -12);
    // the lock on Vex: zinc square brackets over red hostile corners, its name only; the lead reticle
    K.brackets(S, 842, 268, 30, 'tri', { pitch: 2, len: 5, color: 'red', level: 'norm' });
    K.brackets(S, 842, 268, 40, 'square', { pitch: 2, len: 6, level: 'full', ticks: true });
    K.label(S, 892, 214, ["RAIDER 'VEX'"], { pitch: 2, level: 'norm' });
    // 42 m/s relative for the coilgun's 1.1 s is 46 m of lead, 3 units at this zoom: the reticle sits on the
    // target, inside the lock mark, the smallest mark there
    K.reticle(S, 839, 270, { r: 8, level: 'norm', onTarget: true });
    // two harpoons 1.4 km out, nearly dead ahead, their tracks and the impact mark: where they come from is drawn
    // here and on the scope, never given as a bearing, and the seconds are the band's alone. Thrown from the
    // raider 3.2 km off, under a harpoon's 30 m/s² closing burn they are about 4 s from impact
    K.missile(S, 742, 375); K.missile(S, 752, 396);
    K.track(S, [[742, 375], [681, 386]], { step: 7 }); K.track(S, [[752, 396], [682, 391]], { step: 7 });
    K.cross(S, 678, 388);
    S.projectNow();

    // warp is held by the game's own guard reason, the fire in the hold (no guard holds it for hostiles)
    cluster(S, { chr: { day: '3', time: '14:22:05', warp: '1', held: 'FIRE ABOARD' }, ann: { FIRE: 'caution', CREW: 'caution' } });
    sch(S, { states: { HOLD: 'fire' }, crew: [[9, 2, false, true], [9, 1], [6, 2, true], [13, 3]] });
    band(S, {
      warning: { title: 'MISSILE', detail: '2 INBOUND', label: 'IMPACT', number: '4', unit: 'S', key: 'F', action: 'DECOY', note: 'POINT DEFENSE ENGAGING' },
      cautions: [
        { full: ['FIRE  HOLD', 'ADA ON IT'], short: ['FIRE HOLD', 'ADA'] },
        { full: ['WOUNDED', 'COLE, NO MEDIC FREE'], short: ['WOUNDED COLE', 'NO MEDIC'] },
      ],
    });
    obj(S, [{ title: "BOUNTY: RAIDER 'VEX'", left: '2d21h' }]);
    tgt(S, { rel: 'HOSTILE', name: "RAIDER 'VEX'", cls: 'CORVETTE, PIRATE', firing: true, locked: false, state: 'FIGHTING',
      hull: 3, hullWord: 'DAMAGED', range: '3.2', closing: 'CLOSING', rate: '41', cpa: '0.6', cpaT: '1:15', contract: 'BOUNTY' });
    weaponsPod(S);
    wpn(S, {
      head: 'GUNS IN HAND, PD AUTOMATIC',
      mounts: [
        { pos: 'NOSE', gun: 'COILGUN', who: 'HAND', state: 'READY', tof: '1.1S' },
        { pos: 'FWD PORT', gun: 'AUTOCAN', who: 'AUTO', state: 'ENGAGING 2' },
        { pos: 'FWD STBD', gun: 'AUTOCAN', who: 'AUTO', state: 'IDLE' },
        { pos: 'AFT STBD', gun: 'LASER', who: 'HAND', state: 'OUT OF RANGE' },
        { pos: 'AFT PORT', gun: 'HARPOON', who: 'HAND', state: '3/4 READY', tof: 'LOCK' },
      ],
      decoy: 'READY',
    });
    // the 5 km step holds the raider (3.2 km) and both harpoons (1.4 km) with room to spare; the station is past
    // the rim, so it sits on it
    sns(S, { heading: -12, range: '5 KM', auto: true, hostiles: 1, contacts: [{ x: 0.537, y: -0.349, kind: 'hostile', lock: true }, { x: 0.27, y: -0.07, kind: 'missile' }, { x: 0.3, y: -0.01, kind: 'missile' }, { x: -0.83, y: 0.55, kind: 'station' }] });
    // a shot at the ship drops every order but hold position and match velocity (HelmOrders.threat)
    hlm(S, 340, 718, 340, { who: 'BEA', order: 'HOLDING HEADING' });
    hnt(S, [['SPACE', 'FIRE'], ['R', 'MISSILE'], ['TAB', 'NEXT TARGET'], ['V', 'STAND UP'], ['F1', 'CONTROLS']]);
    S.finish();
  };

  // ================================================================== Deck, damage control
  SCREENS['tk-deck-dc'] = async (canvas) => {
    const S = K.scene(canvas);
    // the deck as the game draws it, the corvette centred: tile tx's left edge at TX(tx), chassis row ty's top at TY(ty)
    const DX = -61, DY = -73;
    K.space(S, { seed: 5, stars: 200 });
    await K.world(S, 'bg/deck-lit.jpg', { x: DX, y: DY, w: 1280, h: 800 });
    const TX = tx => 382 + DX + (tx - 2) * 39.8, TY = ty => 365 + DY + (5 - ty) * 39.6;
    // the fire in the hold, drawn as the game would: a glow over the burning tiles
    const fx = TX(6) + 20, fy = TY(3) + 26;
    glow(S, fx, fy, 70, 'rgba(255,110,25,0.5)'); glow(S, fx + 12, fy - 6, 34, 'rgba(255,180,70,0.6)'); glow(S, fx - 18, fy + 8, 26, 'rgba(255,160,50,0.55)'); glow(S, fx + 2, fy + 2, 12, 'rgba(255,235,170,0.8)');
    // the conduit short: sparks on the run up the forward beams (x 14)
    const sx = TX(14) + 20, sy = TY(3) + 20;
    glow(S, sx, sy, 22, 'rgba(150,200,255,0.55)');
    const rr = K.rng(9);
    for (let i = 0; i < 14; i++) { const a = rr() * Math.PI * 2, d = 3 + rr() * 14; S.base.fillStyle = 'rgba(220,235,255,0.9)'; S.base.fillRect(sx + Math.cos(a) * d, sy + Math.sin(a) * d, 1.2, 1.2); }
    // the breach in the starboard hull: air venting out in a fan
    const vx = TX(14.5), vy = TY(0) + 39.6;
    for (let i = 0; i < 70; i++) { const a = (62 + rr() * 56) * R, d = 3 + rr() * 64; S.base.fillStyle = `rgba(228,234,242,${0.75 - d / 100})`; S.base.beginPath(); S.base.arc(vx + Math.cos(a) * d, vy + Math.sin(a) * d, 0.8 + d / 26, 0, Math.PI * 2); S.base.fill(); }
    // the overlays, projected: each hazard tagged with the progress of whoever works it, or NOBODY
    hazard(S, fx, fy - 50, 'FIRE (MEDIUM)', 'caution', 0.45);
    hazard(S, sx, sy - 45, 'CONDUIT SHORT', 'caution', 0.6);
    hazard(S, vx, vy - 54, 'BREACH, VENTING', 'caution', null);
    S.projectNow();
    // what the captain can do where they stand: a small slab by the console (guide 7.10), its name in legend type,
    // then each verb with its key cap
    prompt(S, TX(9) + 4, TY(3) + 46, 'WEAPONS CONSOLE', [['V', 'SIT']]);

    cluster(S, { chr: { day: '3', time: '14:22:05', warp: '1', held: 'FIRE ABOARD' }, ann: { FIRE: 'caution', AIR: 'caution', POWER: 'caution', CREW: 'advisory' } });
    per(S, { health: 'LIGHT WOUND', suit: 'PRESSURE SUIT', here: 'BRIDGE: THIN AIR', air: '7:40', alert: false });
    band(S, {
      cautions: [
        { full: ['FIRE  HOLD', 'COLE ON IT'], short: ['FIRE HOLD', 'COLE'] },
        { full: ['VENTING  BRIDGE', 'NOBODY: NO PATCHES LEFT (Y AT A YARD)'], short: ['VENTING BRIDGE', 'NOBODY'] },
        { full: ['POWER  BRIDGE CONDUIT SHORT', 'ADA RESETTING'], short: ['POWER BRIDGE', 'ADA'] },
      ],
    });
    const oy = obj(S, [{ title: 'DELIVERY: WATER, FREEHOLD', left: '1d04h' }]);
    ntf(S, oy + 12, ['PAYDAY: 2,000 CR']);
    hlm(S, 420, 718, 440, { who: 'BEA', order: 'HOLDING HEADING' });
    hnt(S, [['W A S D', 'WALK'], ['O', 'LIGHTS'], ['F1', 'CONTROLS']]);
    S.finish();
  };

  // ================================================================== Screens (functional-task.md section 7)
  // Every screen keeps CHR and the message centre above it (Z1 and Z2); the rest of the screen is the
  // screen's. Section 18 of the guide: the map is a plot, the terminal and the panels are terminals, the
  // book is the captain's LCD slate.
  const SC = SCR;
  function screenTop(S, d = {}) {
    K.plate(S, LX, 14, CW, 52, { pad: 6, radius: 8 });
    chr(S, d.chr || { day: '3', time: '14:22:05', warp: '1' });
    band(S, d.band || {});
  }
  // a panel of the map: a module with its name lit at L1 in its first row and its keys in its last
  function pane(S, x, y, w, h, name, o = {}) {
    K.plate(S, x, y, w, h, { pad: 5, radius: 6 });
    S.module(x, y, w, h, { filaments: Math.max(1, Math.round(h / 60)) });
    S.legend(x + 8, y + 17, name, { size: 13, level: 'dim', ghost: false });
    if (o.keys) { let kx = x + 8; for (const [k, t] of o.keys) kx = SC.capWord(S, kx, y + h - 24, k, t, { level: 'norm' }) - 6; }
  }
  const T = (S, x, y, str, o = {}) => S.dm(x, y, str, { pitch: P, level: o.level ?? 'norm', ghost: o.ghost ?? false, cells: o.cells, align: o.align, color: o.color, inverse: o.inverse });

  // ------------------------------------------------------------------ MAP
  SCREENS['tk-map'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 12, stars: 200 });
    screenTop(S);
    const px = 16, py = 124, pw = 932, ph = 556;
    K.plate(S, px, py, pw, ph, { pad: 6, radius: 8 });
    SC.orbitTransfer(S, px, py, pw, ph, { sel: 2, proposal: true, ex: 0.36, ey: 0.6 });
    // the header row on the plot's glass: where, and the close keys
    S.seg14(px + 14, py + 12, 13, 'EARTH SYSTEM', { level: 'norm' });
    let kx = px + pw - 14 - 150;
    kx = SC.capWord(S, kx, py + 10, ['M'], 'OR');
    SC.capWord(S, kx - 8, py + 10, ['ESC'], 'CLOSE');
    // MAP.6 the layers: toggle keys with their LEDs, green while the layer shows
    const lw = 404, lx = px + pw - lw - 12, ly = py + ph - 48;
    K.plate(S, lx, ly, lw, 38, { pad: 4, radius: 5 });
    S.module(lx, ly, lw, 14, { filaments: 0, glare: false });
    [['STATIONS', true], ['TRAFFIC', true], ['PIRATES', true], ['DERELICTS', false], ['TENSION', false]].forEach(([t, on], i) => {
      const sx = lx + i * (lw / 5), kw = lw / 5 - 8;
      T(S, sx + (lw / 5 - SC.width(t.length)) / 2, ly + 3, t, { level: on ? 'norm' : 'dim' });
      S.key(sx + 4, ly + 18, kw, 18, '', { size: 12 });
      S.led(sx + kw - 2, ly + 27, 'green', on);
    });

    // the right column: MAP.1 to MAP.4
    const x = RX, w = CW, cx = x + 8;
    pane(S, x, 124, w, 150, 'DESTINATION', { keys: [[['Q'], 'NEXT'], [['SHIFT', 'Q'], 'BACK'], [['L'], 'LIST']] });
    S.dm(cx, 146, 'SHACKLETON ICE', { pitch: 2, level: 'full', ghost: false });
    T(S, cx, 168, 'SELENE COMBINE   NEUTRAL', { level: 'dim' });
    T(S, cx, 182, 'SERVES  DELIVERY CONTRACT', { level: 'dim' });
    S.legend(cx, 216, 'DIST', { size: 13, level: 'dim' });
    S.seg7(cx + 40, 199, 17, '385,479', { cells: 7, level: 'norm' });
    S.legend(cx + 40 + S.seg7Width(17, 7) + 6, 216, 'KM', { size: 13, level: 'dim' });

    pane(S, x, 286, w, 214, 'PLAN', { keys: [[[';'], 'PLAN'], [['ENTER'], 'ACCEPT'], [['BKSP'], 'DROP']] });
    T(S, cx + 44, 290, 'PROPOSAL: 2 BURNS', { level: 'norm' });
    S.legend(cx, 322, 'ΔV', { size: 13, level: 'dim' });
    S.seg7(cx + 24, 306, 16, '3,912', { cells: 4, level: 'norm' });
    S.legend(cx + 24 + S.seg7Width(16, 4) + 6, 322, 'OF', { size: 13, level: 'dim' });
    S.seg7(cx + 24 + S.seg7Width(16, 4) + 28, 306, 16, '7,007', { cells: 4, level: 'norm' });
    S.legend(x + w - 8, 322, 'M/S', { size: 13, level: 'dim', align: 'right' });
    S.bar(cx, 330, w - 16, 5, 30, 3912 / 7007, { level: 'norm', gap: 1.4 });
    T(S, cx, 342, 'AFTER IT 3,095    HOME THEN 520', { level: 'dim' });
    T(S, cx, 358, 'ARRIVES  DAY 7 09:40');
    T(S, cx, 372, 'DEADLINE DAY 6 11:00');
    K.pip(S, cx + 3, 391, 'amber', true); K.pip(S, cx + 12, 391, 'red', false);
    T(S, cx + 22, 386, 'LATE BY 22H 40M', { level: 'full' });
    T(S, cx, 408, '1  T-1:29:51   3,084  PRO', {});
    T(S, cx, 422, '2  T-3D 17H      828  RETRO', { level: 'norm', inverse: [0, 28] });
    T(S, cx, 444, 'PG 1/1', { level: 'dim' });

    pane(S, x, 512, w, 84, 'FLY IT');
    T(S, cx + 56, 516, 'BURN 1 FLOWN BY BEA');
    SC.capWord(S, cx, 536, ['/'], 'WARP TO BURN 1');
    SC.capWord(S, cx, 560, ['6'], 'AUTOPILOT');
    T(S, x + w - 8 - SC.width(5), 564, 'READY', { level: 'norm' });

    pane(S, x, 608, w, 72, 'SELECTED');
    T(S, cx + 76, 612, 'BURN 2', { level: 'full' });
    S.legend(cx, 646, 'PRO', { size: 13, level: 'dim' });
    S.seg7(cx + 30, 631, 15, '-828.0', { cells: 6, level: 'norm' });
    S.legend(cx + 150, 646, 'RAD', { size: 13, level: 'dim' });
    S.seg7(cx + 180, 631, 15, '+0.0', { cells: 4, level: 'norm' });
    T(S, cx, 656, 'DRAG A HANDLE · SHIFT FINER', { level: 'dim' });

    // MAP.5 the plan's timeline
    K.plate(S, 16, 694, 1248, 92, { pad: 6, radius: 8 });
    S.module(16, 694, 1248, 92, { filaments: 2 });
    S.legend(24, 711, 'TIMELINE', { size: 13, level: 'dim', ghost: false });
    T(S, 104, 701, 'LOG SCALE, NOW TO ARRIVAL · CLICK CENTRES · DOUBLE-CLICK WARPS', { level: 'dim' });
    SC.timeline(S, 24, 722, 1232, [
      { t: 1.5, label: 'BURN 1  1.5H' }, { t: 69.6, label: 'DEADLINE 2.9D', deadline: true },
      { t: 79.2, label: 'SOI MOON 3.3D' }, { t: 88.8, label: 'BURN 2 3.7D' }, { t: 91.2, label: 'ARRIVE 3.8D' },
    ], { t0: 6 });
    S.finish();
  };

  // ------------------------------------------------------------------ TRM, the station terminal
  // One terminal for everything the station offers: page keys along the top edge (a page this station
  // doesn't offer is a blank key in its fixed home), the berth on the header row, the list and the detail
  // on a ghost grid of cells, action keys along the bottom edge.
  SCREENS['tk-terminal'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 12, stars: 200 });
    screenTop(S);
    const g = SC.terminal(S, 16, 124, 1248, 664, {
      pages: [{ t: 'CONTRACTS', k: 'J', active: true }, { t: 'MARKET', k: 'T' }, { t: 'YARD', k: 'Y' }, null, { t: 'CREW', k: 'I' }], pageSlots: 8,
      keys: [{ t: 'TAKE', k: 'ENTER' }, { t: 'SHOW ROUTE', k: 'R' }, { t: 'SORT', k: 'S' }, null, null, null, null, { t: 'CLOSE', k: 'ESC' }],
    });
    const x0 = g.x + 14, top = g.top + 8;
    // the header row: where, whose, and the figures that bound every choice here
    S.seg14(x0, top, 16, 'HIGHPORT', { level: 'full' });
    T(S, x0 + S.seg14Width(16, 8) + 14, top + 3, 'TERRAN PATROL AUTHORITY · YOUR SPONSOR', { level: 'dim' });
    let fx = g.x + g.w - 14;
    // a figure: its legend, the seven-segment value and its unit; "6 OF 40" for a count against a limit
    const fig = (label, val, cells, unit, of) => {
      const lg = (t2) => { fx -= S.legendWidth(t2, { size: 13 }); S.legend(fx, top + 16, t2, { size: 13, level: 'dim' }); };
      if (unit) { lg(unit); fx -= 5; }
      if (of) { fx -= S.seg7Width(16, of[1]); S.seg7(fx, top, 16, of[0], { cells: of[1], level: 'norm' }); fx -= 5; lg('OF'); fx -= 5; }
      fx -= S.seg7Width(16, cells); S.seg7(fx, top, 16, val, { cells, level: 'norm' }); fx -= 6;
      lg(label); fx -= 22;
    };
    fig('CONTRACTS', '1', 1, null, ['3', 1]); fig('HOLD', '6', 2, 'M3', ['40', 2]); fig('CREDITS', '18,450', 6, 'CR'); fig('STANDING', '22', 3);
    S.rectLit(g.x + 8, top + 26, g.w - 16, 0.8, { ghostOnly: true, k: 2.2 });
    // the list: offers, operations first; every column counted in cells, numbers right-aligned
    const ly = top + 40, lg = SC.grid(S, x0, ly, 84, 18), dv = x0 + 86 * 9;
    lg.text(2, 0, 'OFFERS  5 TODAY, 1 OPERATION', { level: 'norm' });
    lg.text(62, 0, 'SORT ◀ PAY ▶', { level: 'dim', cells: 22, align: 'right' });
    const C = [['TYPE', 2, 7], ['JOB', 10, 30], ['PAY', 41, 7, 'r'], ['STDG', 49, 5, 'r'], ['TIME', 55, 7], ['REACH', 63, 21]];
    C.forEach(([h, c, n, a]) => S.legend(lg.cx(a ? c + n : c) - (a ? 1.5 : 0), lg.ry(2) + 9, h, { size: 13, level: 'dim', align: a ? 'right' : 'left', ghost: false }));
    const rows = [
      [null, ['OPER.', 'CLEAR THE LANES (1/3)', '8,000', '+6', '--', 'LOCKED: LIEUTENANT'], 'dim'],
      [null, ['BOUNTY', "RAIDER 'VEX' NEAR HIGHPORT", '5,000', '+6', '3D', 'HERE  OK']],
      ['amber', ['DELIV.', 'FOOD TO SHACKLETON ICE', '1,240+', '+3', '2D 20H', '3.8 D  LATE 23H'], 'focus'],
      [null, ['ESCORT', 'HAULER KESTREL TO ANVIL YARDS', '1,500', '+4', '2D', '2.1 H  OK  ΔV 3,000']],
      [null, ['PATROL', 'DEEPWELL DEPOT AND BACK', '1,000', '+3', '2D', '2 H  OK  ΔV 1,860']],
    ];
    rows.forEach(([sev, cells, st], i) => {
      const r = 4 + i * 2;
      lg.pips(0, r, sev);
      const line = SC.cols([['', 1], [cells[0], 8], [cells[1], 31], [cells[2], 7, 'r'], ['', 1], [cells[3], 5, 'r'], ['', 1], [cells[4], 8], [cells[5], 21]]);
      if (st === 'focus') T(S, lg.cx(2), lg.ry(r), line.slice(0, 82), { level: 'norm', inverse: [0, 82] });
      else T(S, lg.cx(2), lg.ry(r), line, { level: st === 'dim' ? 'dim' : 'norm' });
    });
    lg.text(2, 15, 'PG 1/1', { level: 'dim' });
    lg.text(2, 17, "REACH: TRAVEL TIME AND ΔV FROM HERE, AGAINST ΔV LEFT AND THE DEADLINE", { level: 'dim' });
    // the detail: the focused offer's full terms
    S.rectLit(dv - 6, ly, 0.8, g.bottom - ly - 10, { ghostOnly: true, k: 2.2 });
    const dx = dv + 8, dg = SC.grid(S, dx, ly, Math.floor((g.x + g.w - 14 - dx) / 9), 30);
    S.dm(dx, ly, 'DELIVERY: FOOD TO SHACKLETON ICE', { pitch: 2, level: 'full', ghost: false });
    const F = (r, label, val, o = {}) => {
      S.legend(dx, dg.ry(r) + 9.5, label, { size: 13, level: 'dim', ghost: false });
      dg.text(11, r, val, { level: o.level ?? 'norm' });
    };
    F(3, 'GIVER', 'HIGHPORT (SPONSOR)'); F(5, 'CARGO', '18 M3 FOOD, LOADED HERE'); F(7, 'PAY', '1,240 CR ON DELIVERY + SALE');
    F(9, 'STANDING', '+3   FAIL -1.5'); F(11, 'DEADLINE', 'DAY 6 11:00  (2D 20H)');
    F(14, 'ROUTE', 'SHACKLETON ICE, AT THE MOON'); dg.text(11, 15.5, 'TRANSFER ABOUT 3,900 M/S, 3.8 D', { level: 'dim' });
    dg.text(11, 17, 'ARRIVE DAY 7 09:40', {}); dg.pips(31, 17, 'amber'); dg.text(34, 17, '22H 40M LATE', { level: 'full' });
    F(19, 'HOLD', 'NEEDS 18 M3, 34 FREE: FITS');
    // the decision's two numbers, big: what it pays, and the margin against its deadline
    const by = dg.ry(23);
    S.legend(dx, by + 26, 'PAY', { size: 13, level: 'dim' });
    S.seg7(dx + 34, by, 26, '1,240', { cells: 5, level: 'norm' });
    S.legend(dx + 34 + S.seg7Width(26, 5) + 6, by + 26, 'CR', { size: 13, level: 'dim' });
    const mx = dx + 230;
    S.legend(mx, by + 26, 'SPARE', { size: 13, level: 'dim' });
    S.seg7(mx + 52, by, 26, '-22:40', { cells: 5, level: 'norm', color: 'amber' });
    S.frameLit(mx + 46, by - 6, S.seg7Width(26, 5, { colons: 1 }) + 14, 38, 1.1, { color: 'amber', level: 'dim' });
    S.finish();
  };

  // ------------------------------------------------------------------ BOOK, the ship's book
  // The captain's own records: an LCD slate that reflects, its tabs on the side keys, dark on grey-green.
  SCREENS['tk-book'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 12, stars: 200 });
    screenTop(S);
    const L = SC.slate(S, 16, 124, 1248, 664, {
      brand: "SHIP'S BOOK", owner: 'CORVETTE · TERRAN PATROL', keysBottom: true, keyPitch: 44,
      keysLeft: [{ k: '', t: 'SHIP' }, { k: 'I', t: 'CREW', active: true }, { k: 'J', t: 'CONTRACTS' }, { k: '', t: 'CAREER' }, { k: 'L', t: 'LOG' }],
    });
    const D = (x, y, str, o = {}) => SC.lcdDm(S, x, y, str, o);
    const x0 = L.x + 104, y0 = L.y + 12;
    SC.lcdRule(S, x0 - 10, L.y + 8, L.h - 16, true);
    // the list: everyone aboard, the focused one inverse
    S.lcdText(x0, y0 + 11, 'CREW', { size: 15 });
    D(x0 + 56, y0 + 2, '4 ABOARD · 6 BERTHS · 630 CR A WEEK', { level: 'dim' });
    const hy = y0 + 40, C = [['NAME', 0], ['ROLE', 6], ['CONDITION', 16], ['MORALE', 29], ['JOB NOW', 37], ['WAGE', 63]];
    C.forEach(([h, c]) => S.lcdText(x0 + 22 + c * 9, hy, h, { size: 12, ink: 'rgba(26,32,22,0.6)' }));
    const crew = [
      ['ADA', 'ENGINEER', 'FIT', 0.72, 'REPAIRING THE REACTOR', '180', null],
      ['BEA', 'HELMSMAN', 'FIT', 0.68, 'AT THE HELM', '160', null],
      ['COLE', 'MARINE', 'LIGHT WOUND', 0.55, 'RESTING, BERTH 2', '150', 'caution'],
      ['DANA', 'GUNNER', 'FIT', 0.8, 'AT THE FWD GUN', '140', null],
    ];
    crew.forEach((r, i) => {
      const ry = hy + 10 + i * 22, focus = i === 2;
      SC.lcdPip(S, x0 + 4, ry + 5, r[6] === 'caution', false); SC.lcdPip(S, x0 + 13, ry + 5, r[6] === 'warning', true);
      const line = SC.cols([[r[0], 6], [r[1], 10], [r[2], 13], ['', 8], [r[4], 26], [r[5], 4, 'r']]);
      D(x0 + 22, ry, line, { inverse: focus ? [0, 67] : null });
      SC.lcdBar(S, x0 + 22 + 29 * 9, ry + 1.5, 54, 7.5, 10, r[3], { inverse: focus });
    });
    const sy = hy + 10 + 4 * 22 + 16;
    SC.lcdRule(S, x0, sy - 8, 620);
    D(x0 + 22, sy, 'EVA        HELD, NOBODY OUTSIDE');
    D(x0 + 22, sy + 16, 'SUITS      ON AT BATTLE STATIONS');
    D(x0 + 22, sy + 32, 'PRISONERS  NONE ABOARD', { level: 'dim' });
    D(x0 + 22, sy + 56, 'PG 1/1', { level: 'dim' });
    // the detail: the focused person
    const dx = x0 + 650;
    SC.lcdRule(S, dx - 16, L.y + 8, L.h - 16, true);
    D(dx, y0 + 2, 'COLE', { pitch: 2.5 });
    D(dx + 70, y0 + 6, 'MARINE · SIGNED AT HIGHPORT', { level: 'dim' });
    const F = (r, label, val, o = {}) => { S.lcdText(dx, y0 + 50 + r * 22 + 9, label, { size: 12, ink: 'rgba(26,32,22,0.6)' }); D(dx + 96, y0 + 50 + r * 22, val, o); };
    F(0, 'GUNNERY', ''); SC.lcdBar(S, dx + 96, y0 + 51.5, 90, 7.5, 5, 0.6); D(dx + 196, y0 + 50, '3');
    F(1, 'MEDIC', ''); SC.lcdBar(S, dx + 96, y0 + 73.5, 90, 7.5, 5, 0.4); D(dx + 196, y0 + 72, '2');
    F(2, 'ENGINES', ''); SC.lcdBar(S, dx + 96, y0 + 95.5, 90, 7.5, 5, 0.2); D(dx + 196, y0 + 94, '1');
    F(4, 'CONDITION', 'LIGHT WOUND, HEALS IN 6 H'); SC.lcdPip(S, dx + 88, y0 + 50 + 4 * 22 + 5, true, false);
    F(5, 'MORALE', '55, PAID UP TO DAY 7');
    F(6, 'JOB NOW', 'RESTING, BERTH 2');
    F(7, 'SUIT', 'STOWED, LOCKER 1');
    F(8, 'WAGE', '150 CR A WEEK');
    F(10, 'HISTORY', 'TWO BOARDINGS; WOUNDED ON');
    D(dx + 96, y0 + 50 + 11 * 22, "DAY 2 TAKING THE RAIDER 'VEX'");
    // the slate's keys, printed with their actions
    SC.slateKeys(S, L.x + 104, L.y + L.h + 12, [{ k: 'O', t: 'SEND OUT' }, { k: 'C', t: 'CALL IN', dim: true }, { k: 'G', t: 'LET GO', dim: true }, { k: 'PG DN', t: 'NEXT PAGE', dim: true }]);
    S.silk(L.x + L.w - 4, L.y + L.h + 18, 'LET GO WHILE DOCKED', { size: 12, color: '#5b6167', align: 'right' });
    S.finish();
  };

  // ------------------------------------------------------------------ BRD, the boarding panel
  // A terminal slab down the right side, so the latched ship stays in sight while the boarders work. It
  // follows the boarding through its phases; here the raider is taken and the party is stripping it.
  SCREENS['tk-boarding'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 44 });
    await K.hull(S, 'raider', 470, 318, 0.1, 180, { filter: 'brightness(0.8)' });
    await K.hull(S, 'corvette', 500, 396, 0.115, 0);
    // the party, as world tags over the raider's deck where they work
    K.label(S, 330, 228, ['ADA', 'STRIPPING THE LASER'], { pitch: 1.5, level: 'norm' });
    K.label(S, 520, 262, ['COLE', 'CARRYING ORE'], { pitch: 1.5, level: 'norm' });
    S.dotLine(352, 252, 380, 300, { layer: 'proj', step: 3, size: 1.1, level: 'dim' });
    S.dotLine(540, 286, 520, 318, { layer: 'proj', step: 3, size: 1.1, level: 'dim' });
    S.projectNow();
    cluster(S, { chr: { day: '3', time: '14:22:05', warp: '1' }, ann: {} });
    band(S, {});
    hnt(S, [['K', 'BOARDING'], ['U', 'CAST OFF'], ['F1', 'CONTROLS']]);

    const g = SC.terminal(S, 900, 124, 364, 628, {
      keys: [{ t: 'STRIP', k: 'ENTER' }, { t: 'CARRY', k: 'C' }, { t: 'RECALL', k: 'R' }, { t: 'CLOSE', k: 'K' }],
    });
    const x0 = g.x + 10, gr = SC.grid(S, x0, g.top, 38, 40);
    S.legend(x0, g.top + 11, 'BOARDING', { size: 13, level: 'dim', ghost: false });
    S.dm(x0 + 84, g.top, "RAIDER 'VEX'", { pitch: 2, level: 'full', ghost: false });
    const row = (r, a, b, o = {}) => { gr.text(0, r, a, { level: 'dim' }); gr.text(7, r, b, { level: o.level ?? 'norm' }); };
    row(2, 'STATE', 'TAKEN (WAS: REACTOR DOWN)'); row(3, 'LINK', 'LATCHED, PORT TO PORT'); row(4, 'COST', 'NONE (OUTLAW)');
    const head = (r, t) => { S.legend(x0, gr.ry(r) + 9.5, t, { size: 13, level: 'norm', ghost: false }); S.rectLit(x0 + S.legendWidth(t, { size: 13 }) + 8, gr.ry(r) + 5, g.w - 28 - S.legendWidth(t, { size: 13 }), 0.8, { ghostOnly: true, k: 2.2 }); };
    head(6, 'PARTY');
    gr.text(0, 7.5, 'ADA (ENGINEER)'); gr.text(17, 7.5, 'STRIPPING THE LASER', { level: 'dim' });
    gr.text(0, 9, 'COLE (MARINE)'); gr.text(17, 9, 'CARRYING ORE ACROSS', { level: 'dim' });
    head(11, 'SALVAGE');
    S.legend(gr.cx(11) - 1.5 + 5 * 9, gr.ry(12.5) + 9, 'VALUE', { size: 13, level: 'dim', ghost: false, align: 'right' });
    S.legend(gr.cx(23) - 1.5, gr.ry(12.5) + 9, 'WORK', { size: 13, level: 'dim', ghost: false, align: 'right' });
    S.legend(gr.cx(29) - 1.5, gr.ry(12.5) + 9, 'HOLD', { size: 13, level: 'dim', ghost: false, align: 'right' });
    const parts = [['LASER MK1', '2,250', '40 S', '1 M3', 'STRIP', true], ['HARPOON', '1,100', '30 S', '1 M3', 'MARKED'], ['REACTOR M', '1,800', '60 S', '2 M3', 'STRIP']];
    parts.forEach(([n, v, w, h, st, f], i) => {
      const line = SC.cols([[n, 11], [v, 5, 'r'], ['', 2], [w, 5, 'r'], ['', 1], [h, 5, 'r'], ['', 2], [st, 7]]);
      gr.row(14 + i * 1.5, line, { cursor: f, focus: f, cells: 38 });
    });
    head(19, 'CARGO');
    gr.text(0, 20.5, SC.cols([['ORE', 11], ['8 M3', 5, 'r'], ['', 2], ['960 CR', 6, 'r']]));
    gr.text(0, 22, 'HOLD AFTER ALL OF IT', { level: 'dim' }); gr.text(21, 22, '18 OF 40 M3');
    head(24, 'PRISONERS');
    gr.text(0, 25.5, '3 ABOARD THE RAIDER'); gr.text(21, 25.5, 'BERTHS FREE 4', { level: 'dim' });
    gr.text(0, 27, 'ALL FIT: B BRINGS THEM ABOARD', { level: 'dim' });
    head(29, 'PRIZE');
    gr.text(0, 30.5, 'HAND OVER AT A SHIPYARD', { level: 'dim' });
    S.legend(x0, gr.ry(32.5) + 16, 'SALE', { size: 13, level: 'dim' });
    S.seg7(x0 + 40, gr.ry(32.5), 16, '21,000', { cells: 5, level: 'norm' });
    S.legend(x0 + 40 + S.seg7Width(16, 5) + 5, gr.ry(32.5) + 16, 'CR', { size: 13, level: 'dim' });
    S.legend(x0 + 172, gr.ry(32.5) + 16, 'OR +', { size: 13, level: 'dim' });
    S.seg7(x0 + 206, gr.ry(32.5), 16, '8', { cells: 1, level: 'norm' });
    S.legend(x0 + 206 + S.seg7Width(16, 1) + 5, gr.ry(32.5) + 16, 'STANDING', { size: 13, level: 'dim' });
    S.finish();
  };

  Object.assign(K, { tk: { cluster, chr, ann, lamp, per, band, obj, ntf, tgt, wpn, sns, sch, flt, hlm, hnt } });
})();
