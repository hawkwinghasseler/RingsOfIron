// FILAMENT x Task-driven design (functional-task.md, round 4): the HUD as a late-90s digital dash. No racks
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
  // warning lights the red block with the icon and word cut out of it. No SHLD on a ship without an emitter:
  // that place is blank glass.
  const LAMPS = [['HULL', 'breach'], ['FIRE', 'fire'], ['AIR', 'o2'], ['POWER', 'power'], ['PROP', 'prop'],
    ['DRIVE', 'drive'], ['ARMS', 'missile'], ['CREW', 'crew'], ['EVA', 'eva'], ['SHLD', 'shield']];
  function ann(S, st, o = {}) {
    const x = LX, y = 74, w = CW, h = 80, gap = 4;
    S.module(x, y, w, h, { filaments: 0 });
    const cw = (w - 8 - gap * 4) / 5, ch = (h - 8 - gap) / 2;
    LAMPS.forEach(([tag, icon], i) => {
      if (tag === 'SHLD' && !o.shields) return;
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
    vx += S.seg7(vx, b - 15, 15, String(d.dv), { cells: 5, level: 'norm' }) + 8;
    vx += S.legend(vx, b, 'M/S', { level: 'dim' }) + 10;
    const hx0 = x + w - 10 - S.seg7Width(12, 4), hl = S.legendWidth('HOME');
    const bx = vx, bw = hx0 - 6 - hl - 12 - bx, by = b - 9;
    S.bar(bx, by, bw, 8, 40, d.dv / d.dvFull, { level: 'dim', gap: 1.1 });
    const hx = bx + bw * d.home / d.dvFull;
    S.rectLit(hx - 0.7, by + 10, 1.4, 5, { level: 'norm' });
    S.dot(hx - 2.2, by + 12, { size: 1.1, level: 'norm' }); S.dot(hx + 2.2, by + 12, { size: 1.1, level: 'norm' });
    S.legend(hx0 - 6 - hl, b, 'HOME', { level: 'dim' });
    S.seg7(hx0, b - 12, 12, String(d.home), { cells: 4, level: 'dim' });
  }
  // HLM: who flies and the standing order, one line; never a countdown.
  function hlm(S, x, y, w, d) {
    K.slab(S, x, y, w, 24, { pad: 4 });
    let cx = x + 6 + S.legend(x + 6, y + 17, 'HELM', { level: 'dim' }) + 8;
    S.dm(cx, y + 6.75, d.who, { pitch: P, cells: 5, level: 'norm' }); cx += S.dmWidth(5, { pitch: P }) + 8;
    S.dm(cx, y + 6.75, d.order, { pitch: P, cells: fit(cx, x + w - 6), level: 'norm' });
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
    sns(S, { heading: -4, range: '10 KM', auto: true, contacts: [{ x: -0.38, y: -0.24, kind: 'station' }, { x: 0.36, y: 0.2, kind: 'neutral' }, { x: 0.82, y: -0.56, kind: 'neutral' }] });
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
    K.reticle(S, 862, 300, { r: 8, level: 'norm' });
    // two missiles from the port bow, their tracks and the impact mark; the seconds are the band's alone
    K.missile(S, 452, 236); K.missile(S, 470, 222);
    K.track(S, [[452, 236], [604, 380]], { step: 7 }); K.track(S, [[470, 222], [612, 372]], { step: 7 });
    K.cross(S, 612, 382);
    S.projectNow();

    cluster(S, { chr: { day: '3', time: '14:22:05', warp: '1', held: 'HOSTILES NEAR' }, ann: { FIRE: 'caution', CREW: 'caution' } });
    sch(S, { states: { HOLD: 'fire' }, crew: [[9, 2, false, true], [9, 1], [6, 2, true], [13, 3]] });
    band(S, {
      warning: { title: 'MISSILE', detail: '2 INBOUND  BRG 080', label: 'IMPACT', number: '14', unit: 'S', key: 'F', action: 'DECOY', note: 'POINT DEFENCE ENGAGING' },
      cautions: [
        { full: ['FIRE  HOLD', 'ADA ON IT'], short: ['FIRE HOLD', 'ADA'] },
        { full: ['WOUNDED', 'COLE, NO MEDIC FREE'], short: ['WOUNDED COLE', 'NO MEDIC'] },
      ],
    });
    obj(S, [{ title: "BOUNTY: RAIDER 'VEX'", left: '2d21h' }]);
    tgt(S, { rel: 'HOSTILE', name: "RAIDER 'VEX'", cls: 'CORVETTE, PIRATE', firing: true, locked: false, state: 'FIGHTING',
      hull: 3, hullWord: 'DAMAGED', range: '3.2', closing: 'CLOSING', rate: '41', cpa: '0.6', cpaT: '1:12', contract: 'BOUNTY' });
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
    sns(S, { heading: -12, range: '10 KM', auto: true, hostiles: 1, contacts: [{ x: 0.3, y: -0.2, kind: 'hostile', lock: true }, { x: -0.27, y: -0.22, kind: 'missile' }, { x: -0.23, y: -0.27, kind: 'missile' }, { x: -0.72, y: 0.48, kind: 'station' }] });
    hlm(S, 340, 734, 340, { who: 'BEA', order: 'HOLDING 2,000 M OFF RAIDER' });
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
    hlm(S, 420, 734, 440, { who: 'BEA', order: 'HOLDS THE HEADING' });
    hnt(S, [['W A S D', 'WALK'], ['O', 'LIGHTS'], ['F1', 'CONTROLS']]);
    S.finish();
  };

  Object.assign(K, { tk: { cluster, chr, ann, lamp, per, band, obj, ntf, tgt, wpn, sns, sch, flt, hlm, hnt } });
})();
