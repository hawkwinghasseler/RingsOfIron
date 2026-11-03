// FILAMENT x Ship Stations (functional-stations.md, round 3): the seated band as one console rack,
// the top column as plates that power up in fixed slots, the deck left to the deck.
(() => {
  const K = KIT;
  const R = Math.PI / 180;

  // ------------------------------------------------------------------ geometry (6.2, band flush)
  const BAND = { x: 96, y: 616, w: 1088, mfdW: 288, clW: 480, h: 160, boardY: 781 };
  const LMFD = BAND.x, CL = BAND.x + BAND.mfdW + 16, RMFD = CL + BAND.clW + 16;
  const RAIL_Y = 750; // a folded MFD keeps its soft-key rail: label glass 750..764, keys 767..777
  const TOP = { x: 280, w: 720, banner: 16, row1: 44, row2: 84, warp: 124, ticker: 148 };

  // The console the seated band is built into. A folded MFD sinks into the rack and leaves its rail.
  function bandRack(S, leftOpen, rightOpen, name) {
    const top = BAND.y - 10, wing = RAIL_Y - 8, x0 = BAND.x - 10, x1 = BAND.x + BAND.w + 10;
    const lx = LMFD + BAND.mfdW + 8, rx = RMFD - 8;
    const pts = [[x0, 812]];
    pts.push([x0, leftOpen ? top : wing]);
    if (!leftOpen) pts.push([lx, wing], [lx, top]);
    if (!rightOpen) pts.push([rx, top], [rx, wing]);
    pts.push([x1, rightOpen ? top : wing], [x1, 812]);
    K.rackShape(S, pts, { radius: 7 });
    // the board's shelf: a step in the moulding under the instruments
    S.silkRule(BAND.x - 4, BAND.boardY - 2.5, BAND.x + BAND.w + 4, { color: '#08090a', width: 1.2 });
    S.silkRule(BAND.x - 4, BAND.boardY - 1.3, BAND.x + BAND.w + 4, { color: '#26282b', width: 0.6 });
    if (name) S.silk(CL + 1, BAND.y - 3.5, name, { size: 6.5 });
  }

  // A folded MFD: its rail of soft-key labels and keys. items: [{t, active}]
  function rail(S, x, items, o = {}) {
    S.module(x, RAIL_Y, BAND.mfdW, 14, { filaments: 0, glare: false, radius: 2 });
    const n = items.length, slot = BAND.mfdW / n;
    items.forEach((it, i) => {
      const cx = x + slot * (i + 0.5), cells = it.t.length, w = cells * 9 - 1.5;
      S.dm(cx - w / 2, RAIL_Y + 1.75, it.t, { pitch: 1.5, level: it.active ? 'full' : 'norm', inverse: it.active ? [0, cells] : null, ghost: false });
      S.key(cx - 17, RAIL_Y + 17, 34, 9.5, '', {});
      if (i) S.rectLit(x + slot * i - 0.4, RAIL_Y + 2, 0.8, 10, { ghostOnly: true, k: 1.6 });
    });
    if (o.title) S.silk(x + 1, RAIL_Y - 3.5, o.title, { size: 6.5 });
  }

  // An open MFD: glass with a title row, its page, and the soft-key labels in the bottom row; keys under the glass.
  function mfd(S, x, title, keys, drawPage, o = {}) {
    const y = BAND.y, w = BAND.mfdW, gh = 134;
    S.module(x, y, w, gh, { filaments: 4 });
    S.dm(x + 7, y + 5, title, { pitch: 1.5, level: 'norm', ghost: false });
    if (o.owner) S.dm(x + w - 7 - S.dmWidth(o.owner.length, { pitch: 1.5 }), y + 5, o.owner, { pitch: 1.5, level: 'dim', ghost: false });
    if (o.station) S.silk(x + 1, y - 3.5, o.station, { size: 6.5 });
    S.rectLit(x + 6, y + 18, w - 12, 0.7, { ghostOnly: true, k: 2 });
    drawPage(S, x, y + 22, w, gh - 40);
    // soft-key labels: the glass's bottom row; the keys sit on the bezel underneath
    const n = keys.length, slot = w / n, ly = y + gh - 14;
    S.rectLit(x + 6, ly - 4, w - 12, 0.7, { ghostOnly: true, k: 2 });
    keys.forEach((it, i) => {
      const cx = x + slot * (i + 0.5), cells = it.t.length, lw = cells * 9 - 1.5;
      S.dm(cx - lw / 2, ly, it.t, { pitch: 1.5, level: it.active ? 'full' : 'norm', inverse: it.active ? [0, cells] : null, ghost: false });
      S.key(cx - 17, y + gh + 4, 34, 11, '', {});
      if (i) S.rectLit(x + slot * i - 0.4, ly - 1, 0.8, 12.5, { ghostOnly: true, k: 1.6 });
    });
  }

  // ------------------------------------------------------------------ station board (8.5)
  // tiles: [{n, t, who, you, alert: 'caution'|'warning'}]. Occupancy lights the zinc lamp cap; an alert
  // lights the tile's own red or amber pip beside it (a filter is a place, so the cap never changes colour).
  function board(S, tiles, clock) {
    const y = BAND.boardY, h = 16;
    tiles.forEach((tl, i) => {
      const x = BAND.x + i * 150;
      S.lampKey(x + 1, y, 56, h, tl.n + ' ' + tl.t, null, 'zinc', !!tl.you);
      S.module(x + 61, y, 74, h, { filaments: 0, glare: false, radius: 1.5 });
      S.dm(x + 62.75, y + 2.75, tl.who || '', { pitch: 1.5, cells: 8, level: tl.you ? 'norm' : 'dim' });
      K.pip(S, x + 142, y + 4.5, 'red', tl.alert === 'warning');
      K.pip(S, x + 142, y + 11.5, 'amber', tl.alert === 'caution');
    });
    // right end: the chronometer (188)
    const rx = BAND.x + 900 + 4;
    S.module(rx, y, 184, h, { filaments: 0, glare: false, radius: 1.5 });
    S.legend(rx + 8, y + 11.5, 'DAY', { size: 8, level: 'dim' });
    S.seg7(rx + 28, y + 2.5, 11, clock[0], { cells: 2, level: 'norm' });
    S.legend(rx + 60, y + 11.5, 'TIME', { size: 8, level: 'dim' });
    S.seg7(rx + 84, y + 2.5, 11, clock[1], { cells: 6, level: 'norm' });
  }

  // ------------------------------------------------------------------ top column (6.2, 7.1, 7.2)
  // One annunciator row: master lamps at the ends, six tile windows between. The three tiles by the
  // WARN lamp sit behind a red filter and the three by the CAUT lamp behind amber: warnings fill from
  // the left lamp inward, cautions from the right lamp inward. warns, cauts: [[tag, value]] in order.
  function annRow(S, y, warns, cauts, o = {}) {
    const x = TOP.x, lampW = 56, tileW = 94, gap = (TOP.w - 2 * lampW - 6 * tileW) / 7;
    K.plate(S, x, y, TOP.w, 36, { pad: 4 });
    if (o.lamps !== false) {
      S.lampKey(x, y, lampW, 36, 'MASTER', 'WARN', 'red', !!o.warnOn);
      S.lampKey(x + TOP.w - lampW, y, lampW, 36, 'MASTER', 'CAUT', 'amber', !!o.cautOn);
    }
    for (let i = 0; i < 6; i++) {
      const tx = x + lampW + gap + i * (tileW + gap), zone = i < 3 ? 'red' : 'amber';
      const item = i < 3 ? warns[i] : cauts[5 - i];
      S.module(tx, y, tileW, 36, { filaments: 1, radius: 2 });
      const lx = tx + (tileW - 70.5) / 2, l1 = y + 5.5, l2 = y + 20;
      if (!item) {
        S.dm(lx, l1, '', { pitch: 1.5, cells: 8, color: zone }); S.dm(lx, l2, '', { pitch: 1.5, cells: 8, color: zone });
        continue;
      }
      const [tag, val] = item;
      if (zone === 'red') {
        S.rectLit(tx + 3, y + 3, tileW - 6, 30, { color: 'red', level: 'norm', r: 1.2 });
        K.knock(S, lx, l1, tag.padEnd(8).slice(0, 8)); K.knock(S, lx, l2, (val || '').padEnd(8).slice(0, 8));
      } else {
        S.frameLit(tx + 2.5, y + 2.5, tileW - 5, 31, 1.1, { color: 'amber', level: 'norm' });
        S.dm(lx, l1, tag, { pitch: 1.5, cells: 8, color: 'amber', level: 'full' });
        S.dm(lx, l2, val || '', { pitch: 1.5, cells: 8, color: 'amber', level: 'norm' });
      }
    }
  }
  // The status strip (7.7): condition cells behind their own filters, the state legends, and a window
  // for the emergency text. Dark (gone) in Green with no state and no emergency.
  function statusStrip(S, o) {
    const x = TOP.x, y = TOP.banner, h = 24;
    K.plate(S, x, y, TOP.w, h, { pad: 4 });
    const cell = (cx, w, word, col, on) => {
      S.module(cx, y, w, h, { filaments: 0, glare: false, radius: 2 });
      if (on) { S.rectLit(cx + 2, y + 2, w - 4, h - 4, { color: col, level: 'norm', r: 1.2 }); S.knockout(cx + w / 2, y + 16, word, { size: 11, align: 'center', condense: 0.78 }); }
      else S.legend(cx + w / 2, y + 16, word, { size: 11, align: 'center', condense: 0.78, color: col, lit: false });
    };
    cell(x, 64, 'YELLOW', 'amber', o.condition === 'YELLOW');
    cell(x + 68, 52, 'RED', 'red', o.condition === 'RED');
    S.module(x + 124, y, 92, h, { filaments: 0, glare: false, radius: 2 });
    S.legend(x + 170, y + 15.5, 'BATTLE STATIONS', { size: 8.5, align: 'center', color: 'red', lit: !!o.battle, level: 'norm' });
    S.module(x + 220, y, 92, h, { filaments: 0, glare: false, radius: 2 });
    K.states(S, x + 226, y + 15.5, [['DOCKED', o.state === 'DOCKED' && 'norm'], ['LATCHED', o.state === 'LATCHED' && 'norm']], { size: 8.5, gap: 7 });
    S.module(x + 316, y, TOP.w - 316, h, { filaments: 1, glare: false, radius: 2 });
    S.dm(x + 322, y + 6.75, o.emerg || '', { pitch: 1.5, cells: 44, color: 'zinc', level: 'norm' });
  }
  // The callout ticker: three fixed lines, newest at the bottom. lines: [{sev, tag, text}] (null = empty).
  // Text is zinc; severity lights the line's fixed red or amber pip.
  function ticker(S, lines) {
    const x = TOP.x, y = TOP.ticker, cells = 72;
    K.plate(S, x, y, TOP.w, 60, { pad: 4 });
    S.module(x, y, TOP.w, 60, { filaments: 2, radius: 2 });
    lines.forEach((ln, i) => {
      const ly = y + 5 + i * 18.5;
      K.pip(S, x + 9, ly + 5.25, 'red', ln && ln.sev === 'warning');
      K.pip(S, x + 17, ly + 5.25, 'amber', ln && ln.sev === 'caution');
      const lv = ln ? (i === lines.length - 1 ? 'norm' : 'dim') : 'dim';
      S.dm(x + 26, ly, ln ? ln.tag : '', { pitch: 1.5, cells: 5, level: lv });
      S.dm(x + 26 + 6 * 9, ly, ln ? ln.text : '', { pitch: 1.5, cells, level: lv });
    });
  }

  // ------------------------------------------------------------------ helm cluster (8.1, five fixed slots)
  function slotLabel(S, x, y, t, live) { S.legend(x, y, t, { size: 7.5, level: 'dim', lit: live }); }
  function helmCluster(S, d) {
    const gx = CL, gy = BAND.y;
    S.module(gx, gy, 480, 160, { filaments: 4 });
    const rule = (x, y, w, h) => S.rectLit(x, y, w, h, { ghostOnly: true, k: 2.2 });
    rule(gx + 190, gy + 8, 0.8, 108); rule(gx + 198, gy + 48, 274, 0.8); rule(gx + 198, gy + 84, 274, 0.8); rule(gx + 8, gy + 120, 464, 0.8);

    // Velocity: frame, speed, control chip, assist, thrust lamps (always lit while seated)
    slotLabel(S, gx + 9, gy + 16, 'VEL', true);
    K.states(S, gx + 34, gy + 16, d.frames, { size: 8.5, gap: 6 });
    S.seg7(gx + 12, gy + 26, 42, d.speed, { cells: 4, level: 'norm' });
    S.legend(gx + 144, gy + 46, 'KM/S', { size: 10.5, lit: d.unit === 'KM/S', level: 'norm' });
    S.legend(gx + 144, gy + 61, 'M/S', { size: 10.5, lit: d.unit === 'M/S', level: 'norm' });
    S.legend(gx + 144, gy + 78, 'ROT', { size: 7, lit: !!d.rot, level: 'dim' });
    S.seg7(gx + 162, gy + 71, 8, d.rot || '', { cells: 2, level: 'norm' });
    S.seg14(gx + 12, gy + 98, 12, d.control, { cells: 8, level: 'norm', color: d.controlColor });
    K.states(S, gx + 103, gy + 108, [['ASSIST', d.assist], ['THR', d.thr], ['RCS', d.rcs]], { size: 8.5, gap: 7 });
    if (d.assist) S.frameLit(gx + 100, gy + 98.5, 33, 13, 0.8, { level: 'dim', gap: 0.8 });

    // Reference: name, range, closing, CPA (in that order of priority)
    const rx = gx + 200;
    slotLabel(S, rx, gy + 16, 'REF', !!d.ref);
    const ref = d.ref || {};
    S.dm(rx + 24, gy + 8, ref.name || '', { pitch: 1.5, cells: 14, level: 'norm' });
    S.seg7(rx + 158, gy + 7, 15, ref.range || '', { cells: 4, level: 'norm' });
    S.legend(rx + 210, gy + 14, 'KM', { size: 8, lit: ref.unit === 'KM', level: 'dim' });
    S.legend(rx + 210, gy + 23, 'M', { size: 8, lit: ref.unit === 'M', level: 'dim' });
    K.states(S, rx + 24, gy + 39, [['CLOSING', ref.closing === 'closing' && 'dim'], ['OPENING', ref.closing === 'opening' && 'dim']], { size: 7.5, gap: 4 });
    S.seg7(rx + 90, gy + 30, 10, ref.speed || '', { cells: 4, level: 'dim' });
    S.legend(rx + 125, gy + 39, 'M/S', { size: 7.5, lit: !!ref.speed, level: 'dim' });
    S.legend(rx + 152, gy + 39, 'CPA', { size: 7.5, lit: !!ref.cpa, level: 'dim' });
    S.seg7(rx + 170, gy + 30, 10, ref.cpa || '', { cells: 3, level: 'dim' });
    S.legend(rx + 196, gy + 39, 'M', { size: 7.5, lit: !!ref.cpa, level: 'dim' });
    S.legend(rx + 207, gy + 39, 'IN', { size: 7.5, lit: !!ref.cpaT, level: 'dim' });
    S.seg7(rx + 220, gy + 30, 10, ref.cpaT || '', { cells: 3, level: 'dim' });

    // Next event: what, when, how much
    const nx = d.next || {};
    slotLabel(S, rx, gy + 70, 'NEXT', !!d.next);
    S.seg14(rx + 24, gy + 58, 14, nx.what || '', { cells: 7, level: 'norm' });
    S.seg7(rx + 112, gy + 57, 16, nx.when || '', { cells: 4, level: 'norm' });
    S.seg7(rx + 180, gy + 61, 11, nx.dv || '', { cells: 3, level: 'dim' });
    S.legend(rx + 211, gy + 72, 'M/S', { size: 7.5, lit: !!nx.dv, level: 'dim' });
    S.legend(rx + 236, gy + 72, nx.dir || 'PRO', { size: 7.5, lit: !!nx.dir, level: 'dim' });

    // Trigger: guns, missiles, decoys, shields lowered (dark unless a target is locked or in Red)
    const tg = d.trig;
    slotLabel(S, rx, gy + 106, 'TRIG', !!tg);
    let tx = rx + 26;
    const tf = (name, val, cells) => {
      tx += S.legend(tx, gy + 106, name, { size: 8.5, lit: !!tg, level: 'dim' }) + 4;
      tx += S.seg7(tx, gy + 97, 11, tg ? val : '', { cells, level: 'norm' }) + 10;
    };
    tf('GUNS', tg && tg.guns, 3); tf('MSL', tg && tg.msl, 1); tf('DECOY', tg && tg.decoy, 1);
    S.legend(tx, gy + 106, 'SHLD LOW', { size: 8.5, lit: !!(tg && tg.shieldsDown), color: 'amber', level: 'norm' });

    // Delta-v: left in the tanks, the home notch, the plan bracket
    const dv = d.dv;
    slotLabel(S, gx + 9, gy + 142, 'ΔV', true);
    S.seg7(gx + 28, gy + 129, 19, String(dv.left), { cells: 4, level: 'norm' });
    S.legend(gx + 92, gy + 147, 'M/S', { size: 8, level: 'dim' });
    const bx = gx + 120, bw = 268, by = gy + 133;
    S.bar(bx, by, bw, 9, 54, dv.left / dv.full, { level: 'dim', gap: 1.1 });
    const hx = bx + bw * dv.home / dv.full;
    S.rectLit(hx - 0.7, by + 11, 1.4, 6, { level: 'norm' });
    S.dot(hx - 2.2, by + 13, { size: 1.1, level: 'norm' }); S.dot(hx + 2.2, by + 13, { size: 1.1, level: 'norm' });
    if (dv.plan) {
      const p0 = bx + bw * (dv.left - dv.plan) / dv.full, p1 = bx + bw * dv.left / dv.full;
      S.rectLit(p0, by - 4, p1 - p0, 0.9, { level: 'dim' }); S.rectLit(p0, by - 4, 0.9, 3, { level: 'dim' }); S.rectLit(p1 - 0.9, by - 4, 0.9, 3, { level: 'dim' });
    }
    S.legend(gx + 398, gy + 139, 'HOME', { size: 7.5, level: 'dim' });
    S.seg7(gx + 426, gy + 132, 9, String(dv.home), { cells: 4, level: 'dim' });
    S.legend(gx + 398, gy + 154, 'PLAN', { size: 7.5, level: 'dim', lit: !!dv.plan });
    S.seg7(gx + 426, gy + 147, 9, dv.plan ? String(dv.plan) : '', { cells: 4, level: 'dim' });
  }

  // ------------------------------------------------------------------ tactical cluster (8.3)
  // mounts: [{pos, who, state: 'READY'|seconds|'NO PWR', mode: 'AT WILL'|'HELD'|'AUTO'|'YOURS', inHand}]
  function tacCluster(S, d) {
    const gx = CL, gy = BAND.y;
    S.module(gx, gy, 480, 160, { filaments: 4 });
    const rule = (x, y, w, h) => S.rectLit(x, y, w, h, { ghostOnly: true, k: 2.2 });
    rule(gx + 8, gy + 50, 464, 0.8); rule(gx + 8, gy + 88, 464, 0.8); rule(gx + 236, gy + 56, 0.8, 26);
    // Mount chips: one window-within-the-window per gun
    d.mounts.forEach((m, i) => {
      const cx = gx + 8 + i * 117, cy = gy + 7, cw = 111, ch = 38;
      S.frameLit(cx, cy, cw, ch, 0.7, { ghostOnly: true, k: 2 });
      if (m.inHand) S.frameLit(cx, cy, cw, ch, 0.8, { level: 'dim' });
      S.legend(cx + 5, cy + 12, m.pos, { size: 9, level: 'norm' });
      S.dm(cx + cw - 5 - 45 + 0.75 * 1.5, cy + 4, m.who, { pitch: 1.5, cells: 5, align: 'right', level: m.who === 'YOU' ? 'norm' : 'dim' });
      if (/^[0-9.]+$/.test(m.state)) {
        S.seg7(cx + 5, cy + 20, 12, m.state, { cells: 3, level: 'norm' });
        S.legend(cx + 41, cy + 32, 'S', { size: 8, level: 'dim' });
      } else S.dm(cx + 5, cy + 22, m.state, { pitch: 1.5, cells: 6, level: m.state === 'READY' ? 'norm' : 'norm', color: m.fault ? 'zinc' : 'zinc' });
      S.legend(cx + cw - 5, cy + 32, m.mode, { size: 8, level: m.mode === 'YOURS' ? 'norm' : 'dim', align: 'right' });
    });
    // Launchers and countermeasures
    slotLabel(S, gx + 10, gy + 73, 'MSL', true);
    S.seg7(gx + 32, gy + 59, 20, d.msl, { cells: 1, level: 'norm' });
    K.states(S, gx + 52, gy + 73, [['READY', d.mslReady && 'norm']], { size: 9 });
    S.seg7(gx + 92, gy + 62, 12, d.mslReload || '', { cells: 2, level: 'norm' });
    slotLabel(S, gx + 132, gy + 73, 'DECOY', true);
    K.states(S, gx + 164, gy + 73, [['READY', d.decoy === 'READY' && 'norm']], { size: 9 });
    S.seg7(gx + 200, gy + 62, 12, d.decoy === 'READY' ? '' : d.decoy, { cells: 1, level: 'norm' });
    // Shields: mode, mass share, trim rose
    slotLabel(S, gx + 246, gy + 73, 'SHLD', true);
    K.states(S, gx + 272, gy + 66, [['RAISED', d.shield === 'RAISED' && 'norm'], ['LOWERED', d.shield === 'LOWERED' && 'norm']], { size: 7.5, gap: 4 });
    S.legend(gx + 272, gy + 80, 'STOWING', { size: 7.5, lit: d.shield === 'STOWING' });
    S.seg7(gx + 346, gy + 59, 20, d.mass, { cells: 2, level: 'norm' });
    S.legend(gx + 376, gy + 79, '%', { size: 9, level: 'dim' });
    // the trim rose: four petals and a centre, the lit one is the trim
    const rcx = gx + 412, rcy = gy + 69;
    const petal = (dx, dy, on) => S.rectLit(rcx + dx - (dx ? 2.5 : 5), rcy + dy - (dy ? 2.5 : 5), dx ? 5 : 10, dy ? 5 : 10, on ? { level: 'norm' } : { ghostOnly: true, k: 2 });
    petal(0, -9, d.trim === 'BOW'); petal(0, 9, d.trim === 'STERN'); petal(-9, 0, d.trim === 'PORT'); petal(9, 0, d.trim === 'STBD');
    S.rectLit(rcx - 2, rcy - 2, 4, 4, d.trim === 'EVEN' ? { level: 'norm' } : { ghostOnly: true, k: 2 });
    S.dm(gx + 428, gy + 64, d.trim, { pitch: 1.5, cells: 5, level: 'norm' });
    // Target: name, class, range, closing, time of flight, hit chance, known state
    const t = d.target;
    slotLabel(S, gx + 10, gy + 106, 'TGT', true);
    S.dm(gx + 34, gy + 96, t.name, { pitch: 2, cells: 8, level: 'norm' });
    S.dm(gx + 34, gy + 115, t.cls, { pitch: 1.5, cells: 10, level: 'dim' });
    S.seg7(gx + 136, gy + 96, 24, t.range, { cells: 3, level: 'norm' });
    S.legend(gx + 194, gy + 106, 'KM', { size: 9, level: 'dim' });
    S.legend(gx + 194, gy + 119, 'M', { size: 9, lit: false });
    K.states(S, gx + 220, gy + 106, [['CLOSING', 'dim'], ['OPENING', false]], { size: 7.5, gap: 4 });
    S.seg7(gx + 220, gy + 112, 11, t.closing, { cells: 3, level: 'norm' });
    S.legend(gx + 252, gy + 123, 'M/S', { size: 7.5, level: 'dim' });
    S.legend(gx + 288, gy + 106, 'TOF', { size: 7.5, level: 'dim' });
    S.seg7(gx + 288, gy + 112, 11, t.tof, { cells: 3, level: 'norm' });
    S.legend(gx + 320, gy + 123, 'S', { size: 7.5, level: 'dim' });
    S.legend(gx + 340, gy + 106, 'HIT', { size: 7.5, level: 'dim' });
    S.seg7(gx + 340, gy + 112, 24, t.hit, { cells: 2, level: 'norm' });
    S.legend(gx + 381, gy + 136, '%', { size: 9, level: 'dim' });
    S.legend(gx + 402, gy + 108, 'GOOD', { size: 8, lit: t.grade === 'GOOD' });
    S.legend(gx + 402, gy + 122, 'FAIR', { size: 8, lit: t.grade === 'FAIR' });
    S.legend(gx + 402, gy + 136, 'POOR', { size: 8, lit: t.grade === 'POOR' });
    K.states(S, gx + 34, gy + 150, [['STRUCK', t.state === 'STRUCK'], ['DISABLED', t.state === 'DISABLED'], ['VENTING', t.state === 'VENTING']], { size: 7.5, gap: 6 });
  }

  // ------------------------------------------------------------------ MFD pages (9.2)
  // SENSORS: the scope and the contact list sorted by threat; hostile is a fixed red pip column.
  function sensorsPage(d) {
    return (S, x, y, w, h) => {
      K.sectorScope(S, x + 50, y + h / 2 + 1, 42, { contacts: d.contacts });
      S.legend(x + 6, y + 9, d.range, { size: 7.5, level: 'dim' });
      const lx = x + 102;
      d.rows.forEach((r, i) => {
        const ry = y + 4 + i * 17;
        if (r.lock) S.dm(lx - 10, ry, '▶', { pitch: 1.5, level: 'norm', ghost: false });
        S.dm(lx, ry, r.name, { pitch: 1.5, cells: 7, level: r.lock ? 'norm' : 'dim' });
        S.dm(lx + 8 * 9, ry, r.value, { pitch: 1.5, cells: 9, align: 'right', level: r.lock || r.missile ? 'norm' : 'dim' });
        K.pip(S, x + w - 8, ry + 5.25, 'red', r.hostile);
      });
      for (let i = d.rows.length; i < 5; i++) { const ry = y + 4 + i * 17; S.dm(lx, ry, '', { pitch: 1.5, cells: 16 }); K.pip(S, x + w - 8, ry + 5.25, 'red', false); }
    };
  }
  // DEFENSE: the shield ring around the hull, 96 segments by cloud thickness; mode, mass, trim, emitters.
  function defensePage(d) {
    return (S, x, y, w, h) => {
      const cx = x + 56, cy = y + h / 2 + 1;
      S.ring(cx, cy, 36, 43, 96, (i) => {
        const a = i / 96 * 360; // 0 = bow (up), clockwise
        const th = d.thickness(a);
        return th > 0.75 ? { level: 'norm' } : th > 0.45 ? { level: 'dim' } : th > 0.15 ? { level: 'dim', color: 'zinc' } && null : null;
      }, { gapDeg: 1.2 });
      // the hull, bow up, as a dotted outline
      const hullPts = [[0, -26], [7, -16], [8, 18], [4, 26], [-4, 26], [-8, 18], [-7, -16]];
      for (let i = 0; i < hullPts.length; i++) { const a = hullPts[i], b = hullPts[(i + 1) % hullPts.length]; S.dotLine(cx + a[0], cy + a[1], cx + b[0], cy + b[1], { step: 2.4, size: 1.1, level: 'dim' }); }
      S.legend(cx - 52, cy + 3, 'P', { size: 8, level: 'dim' }); S.legend(cx + 47, cy + 3, 'S', { size: 8, level: 'dim' });
      const tx = x + 118;
      K.states(S, tx, y + 10, [['RAISED', d.mode === 'RAISED' && 'norm'], ['LOWERED', d.mode === 'LOWERED'], ['STOWING', d.mode === 'STOWING']], { size: 7.5, gap: 5 });
      S.legend(tx, y + 30, 'MASS', { size: 7.5, level: 'dim' });
      S.seg7(tx + 26, y + 19, 14, d.mass, { cells: 2, level: 'norm' }); S.legend(tx + 50, y + 33, '%', { size: 8, level: 'dim' });
      S.legend(tx + 72, y + 30, 'TRIM', { size: 7.5, level: 'dim' });
      S.dm(tx + 96, y + 21, d.trim, { pitch: 1.5, cells: 5, level: 'norm' });
      d.emitters.forEach((e, i) => {
        const ey = y + 44 + i * 13;
        S.dm(tx, ey, e.name, { pitch: 1.5, cells: 3, level: 'dim' });
        S.bar(tx + 30, ey + 1, 80, 8, 16, e.heat, { zone: K.RISING, level: 'norm' });
        S.dm(tx + 118, ey, e.state, { pitch: 1.5, cells: 4, level: 'dim' });
      });
      S.legend(tx, y + h - 2, 'DECOY', { size: 7.5, level: 'dim' }); S.legend(tx + 28, y + h - 2, 'READY', { size: 7.5, lit: d.decoy === 'READY', level: 'norm' });
      S.legend(tx + 70, y + h - 2, 'PD', { size: 7.5, level: 'dim' }); S.dm(tx + 84, y + h - 11, d.pd, { pitch: 1.5, cells: 6, level: 'norm' });
    };
  }

  // ------------------------------------------------------------------ world helpers
  // The next burn's cue: a dotted arrow from the ship along the burn's direction, its label off the head.
  function burnCue(S, x, y, deg, r0, r1, lines) {
    const b = deg * R, c = { layer: 'proj', size: 1.7, level: 'norm' };
    S.dotLine(x + Math.cos(b) * r0, y + Math.sin(b) * r0, x + Math.cos(b) * r1, y + Math.sin(b) * r1, { ...c, step: 4 });
    const tx = x + Math.cos(b) * r1, ty = y + Math.sin(b) * r1;
    for (const s of [-1, 1]) for (let i = 1; i <= 4; i++) {
      const a = b + Math.PI + s * 0.62; S.dot(tx + Math.cos(a) * i * 3, ty + Math.sin(a) * i * 3, c);
    }
    const w = Math.max(...lines.map(l => l.length)) * 9 - 1.5, cb = Math.cos(b), sb = Math.sin(b);
    const lx = tx + cb * 12 - (cb < -0.3 ? w : cb > 0.3 ? 0 : w / 2), ly = ty + sb * 12 - (sb < -0.3 ? 12 : sb > 0.3 ? -2 : 5);
    K.label(S, lx, ly, lines, { pitch: 1.5, level: 'norm' });
  }
  // A hostile's marks: red corner triangles; the lock adds zinc square brackets outside them.
  function hostile(S, x, y, half, o = {}) {
    K.brackets(S, x, y, half, 'tri', { pitch: 2, len: 5, color: 'red', level: 'norm' });
    if (o.lock) K.brackets(S, x, y, half + 10, 'square', { pitch: 2, len: 6, level: 'full', ticks: true });
  }

  // ------------------------------------------------------------------ the prompt line (Z7): learning hints
  function hints(S, y, items) {
    let w = 0;
    for (const [k, word] of items) w += Math.max(12.5, S.legendWidth(k, { size: 6.6, weight: 600, condense: 0.86, track: 0.1 }) + 7) + 4 + word.length * 9 - 1.5 + 14;
    w -= 14;
    const x = 640 - w / 2;
    K.slab(S, x - 6, y, w + 12, 13.5, { pad: 3, filaments: 1, glare: false });
    let cx = x;
    for (const [k, word] of items) {
      cx += K.keycap(S, cx, y + 0.75, k, { h: 12 }) + 4;
      cx += S.dm(cx, y + 1.5, word, { pitch: 1.5, level: 'dim', ghost: false }) + 14;
    }
  }

  // ================================================================== C. Helm, cruising (Green)
  SCREENS['st-helm'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 11 });
    // world: the ship sits in the middle of the view above the band (camera shifted up 92)
    await K.hull(S, 'freighter', 236, 176, 0.042, 162, { filter: 'brightness(0.8)' });
    await K.hull(S, 'station_hub', 1012, 204, 0.06, 0);
    await K.hull(S, 'corvette', 640, 308, 0.115, -20);

    // symbology: neutral traffic as a glyph only; the nav target by name only (the cluster has its numbers)
    K.sprite(S, 'neutral', 236, 150, { pitch: 2, level: 'dim' });
    K.brackets(S, 1012, 204, 78, 'diamond', { pitch: 2, len: 6, level: 'norm' });
    K.label(S, 1012 - 4 * 9 + 0.75, 312, ['HIGHPORT'], { pitch: 1.5, level: 'norm' });
    // velocity markers 70 from the ship, in the ORBIT frame; the next burn's cue with its countdown
    const a = -140 * R;
    K.velMark(S, 'pro', 640 + Math.cos(a) * 70, 308 + Math.sin(a) * 70, { level: 'norm' });
    K.velMark(S, 'retro', 640 - Math.cos(a) * 70, 308 - Math.sin(a) * 70, { level: 'dim' });
    burnCue(S, 640, 308, -158, 104, 156, ['1  12:04']);
    S.projectNow();

    // the band: both MFDs folded (Green), the cluster, the board
    bandRack(S, false, false, 'Helm');
    rail(S, LMFD, [{ t: 'NAV' }, { t: 'PLAN' }, { t: 'PIN' }, { t: 'FOLD', active: true }], { title: 'Navigation' });
    rail(S, RMFD, [{ t: 'SNS' }, { t: 'WPN' }, { t: 'DEF' }, { t: 'PIN' }, { t: 'FOLD', active: true }], { title: 'Tactical' });
    helmCluster(S, {
      frames: [['ORBIT', 'norm'], ['TARGET', false], ['PORT', false], ['DOCKED', false]],
      speed: '7.67', unit: 'KM/S', rot: '', control: 'MANUAL', assist: 'norm', thr: false, rcs: false,
      ref: { name: 'HIGHPORT', range: '1.2', unit: 'KM', closing: 'closing', speed: '3.9', cpa: '140', cpaT: '2:10' },
      next: { what: 'BURN 1', when: '12:04', dv: '210', dir: 'PRO' },
      trig: null,
      dv: { left: 1840, full: 2600, home: 1210, plan: 420 },
    });
    board(S, [
      { n: 1, t: 'HELM', who: 'YOU', you: true }, { n: 2, t: 'NAV', who: 'YOU', you: true },
      { n: 3, t: 'TAC', who: '--' }, { n: 4, t: 'ENG', who: 'OKAFOR' },
      { n: 5, t: 'COMMS', who: 'YOU', you: true }, { n: 6, t: 'CMD', who: '' },
    ], ['3', '14:22:05']);
    hints(S, 588, [['N', 'PLAN A BURN'], ['M', 'PLOT'], ['TAB', 'LOCK']]);
    S.finish();
  };

  // ================================================================== E. Weapons console, in a fight (Red)
  SCREENS['st-weapons'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 23 });
    const sx = 640, sy = 308, heading = -12;
    await K.hull(S, 'interceptor', 402, 262, 0.05, 18, { filter: 'brightness(0.85)' });
    await K.hull(S, 'raider', 1084, 214, 0.095, 168);
    await K.hull(S, 'corvette', sx, sy, 0.115, heading);

    // fire arcs of the guns in hand (nose, port), dim; the intercept line and lead reticle on the lock
    K.arc(S, sx, sy, 100, 250, heading - 24, heading + 18, { level: 'dim', step: 6 });
    K.arc(S, sx, sy, 44, 130, heading - 126, heading - 64, { level: 'dim', step: 6 });
    S.dotLine(sx + 96, sy - 22, 1112, 200, { layer: 'proj', step: 7, size: 1.3, level: 'dim' });
    K.reticle(S, 1118, 199, { level: 'norm' });
    // the lock and the hostiles: the Raider by name only (its numbers are in the Target slot)
    hostile(S, 1084, 214, 34, { lock: true });
    K.label(S, 1084 - 3 * 9, 266, ['RAIDER'], { pitch: 1.5, level: 'norm' });
    hostile(S, 402, 262, 22);
    K.label(S, 402 - 4 * 9, 292, ['DART 2.1'], { pitch: 1.5, color: 'red', level: 'norm' });
    // two missiles inbound: red glyphs and predicted tracks; one impact mark for the port side, with its count
    K.missile(S, 948, 240); K.missile(S, 912, 268);
    K.track(S, [[948, 240], [800, 252], [652, 278]], { step: 7 });
    K.track(S, [[912, 268], [790, 270], [655, 283]], { step: 7 });
    K.cross(S, 640, 276);
    S.dm(652, 256, '2', { pitch: 1.5, layer: 'proj', color: 'red', level: 'full', ghost: false });
    // a point-defence kill: a burst where a third one died
    K.ringDots(S, 832, 228, 9, { color: 'red', level: 'dim', step: 4.5 }); K.ringDots(S, 832, 228, 3.5, { color: 'red', level: 'norm', step: 3.5 });

    S.projectNow();

    // the top column: status strip, tiles in their zones, the ticker; row 2 and warp are dark (retracted)
    statusStrip(S, { condition: 'RED' });
    annRow(S, TOP.row1, [['MISSILE', '2  0:37']], [['HOSTILE', 'FIRING']], { warnOn: true, cautOn: true });
    ticker(S, [null, { tag: 'TAC', text: 'CHEN: DART DOWN.' }, { tag: 'HELM', text: 'RUIZ: HOLDING POSITION UNDER FIRE.' }]);

    // the band: both MFDs open, the tactical cluster, the board
    bandRack(S, true, true, 'Weapons');
    mfd(S, LMFD, 'SENSORS', [{ t: 'SNS', active: true }, { t: 'WPN' }, { t: 'DEF' }, { t: 'PIN' }, { t: 'FOLD' }], sensorsPage({
      range: '10 KM',
      contacts: [{ brg: 66, ring: 1, missile: true }, { brg: 80, ring: 3, hostile: true, lock: true }, { brg: 262, ring: 1, hostile: true }],
      rows: [
        { name: 'MSL X2', value: '0:37', hostile: true, missile: true },
        { name: 'RAIDER', value: '7.7 KM', hostile: true, lock: true },
        { name: 'DART', value: '2.1 KM', hostile: true },
      ],
    }), { owner: 'TAC', station: 'Tactical' });
    mfd(S, RMFD, 'DEFENSE', [{ t: 'SNS' }, { t: 'WPN' }, { t: 'DEF', active: true }, { t: 'PIN' }, { t: 'FOLD' }], defensePage({
      mode: 'RAISED', mass: '82', trim: 'BOW', decoy: 'READY', pd: 'AUTO',
      thickness: a => (a > 215 && a < 320 ? 0.35 + 0.1 * Math.cos((a - 268) * R * 3) : a < 30 || a > 335 ? 0.95 : 0.8),
      emitters: [{ name: 'E1', heat: 0.55, state: 'HOLD' }, { name: 'E2', heat: 0.72, state: 'HOT' }],
    }), { owner: 'TAC', station: 'Tactical' });
    tacCluster(S, {
      mounts: [
        { pos: 'NOSE', who: 'YOU', state: 'READY', mode: 'YOURS', inHand: true },
        { pos: 'PORT', who: 'YOU', state: '1.2', mode: 'YOURS', inHand: true },
        { pos: 'STBD', who: 'CHEN', state: 'READY', mode: 'AT WILL' },
        { pos: 'PD', who: '', state: 'READY', mode: 'AUTO' },
      ],
      msl: '4', mslReady: true, decoy: 'READY', shield: 'RAISED', mass: '82', trim: 'BOW',
      target: { name: 'RAIDER', cls: 'CORVETTE', range: '7.7', closing: '2', tof: '1.3', hit: '64', grade: 'FAIR', state: '' },
    });
    board(S, [
      { n: 1, t: 'HELM', who: 'RUIZ' }, { n: 2, t: 'NAV', who: '--' },
      { n: 3, t: 'TAC', who: 'YOU', you: true, alert: 'warning' }, { n: 4, t: 'ENG', who: 'OKAFOR' },
      { n: 5, t: 'COMMS', who: '--' }, { n: 6, t: 'CMD', who: '' },
    ], ['3', '15:02:44']);
    S.finish();
  };

  // ------------------------------------------------------------------ deck helpers
  // The deck view as the game draws it (a capture), with T0 in-world cues painted on for the mockup:
  // battle lighting, alarm beacons in rooms with an alert, a console glowing in its alert role.
  async function deck(S, src, o = {}) {
    await K.world(S, src);
    const b = S.base;
    if (o.battle) { b.save(); b.globalCompositeOperation = 'multiply'; b.fillStyle = 'rgba(255,150,140,1)'; b.fillRect(0, 0, S.W, S.H); b.restore(); }
    for (const g of o.glows || []) {
      b.save(); b.globalCompositeOperation = 'lighter';
      const rg = b.createRadialGradient(g.x, g.y, 0, g.x, g.y, g.r);
      rg.addColorStop(0, g.color); rg.addColorStop(1, 'rgba(0,0,0,0)');
      b.fillStyle = rg; b.fillRect(g.x - g.r, g.y - g.r, g.r * 2, g.r * 2); b.restore();
    }
  }
  // The suit readout (Z6): the captain's own kit, so it is an LCD that reflects, backlit while it warns.
  function suit(S, d) {
    const x = 16, y = 704, w = 200, h = 80;
    K.plate(S, x, y, w, h, { pad: 5, radius: 7 });
    S.silk(x + 2, y - 0.5, 'Suit', { size: 6.5 });
    S.lcdPanel(x + 2, y + 4, w - 4, h - 6, { backlight: d.alert });
    S.lcdText(x + 10, y + 18, 'AIR', { size: 8 });
    S.lcdSeg7(x + 10, y + 24, 30, d.air, { slant: 6 });
    S.lcdText(x + 104, y + 18, 'ROOM', { size: 8 });
    S.lcdSeg7(x + 104, y + 24, 17, d.kpa, { slant: 6 });
    S.lcdText(x + 136, y + 39, 'KPA', { size: 8 });
    S.lcdText(x + 104, y + 58, d.o2, { size: 8 });
    if (d.temp) S.lcdText(x + 150, y + 58, d.temp, { size: 8 });
    if (d.wound) S.lcdText(x + 10, y + 70, d.wound, { size: 8 });
  }

  // ================================================================== B. On deck, fire and missiles inbound
  SCREENS['st-deck'] = async (canvas) => {
    const S = K.scene(canvas);
    await deck(S, 'bg/deck-lit.jpg', {
      battle: true,
      glows: [
        { x: 556, y: 436, r: 64, color: 'rgba(255,40,20,0.30)' },   // alarm beacon, cargo 1
        { x: 452, y: 540, r: 46, color: 'rgba(255,40,20,0.36)' },   // alarm beacon, aft starboard
        { x: 922, y: 512, r: 26, color: 'rgba(255,50,30,0.45)' },   // the reactor console in its alert role
      ],
    });
    // the top column: RED (missiles inbound), warnings from the WARN lamp, the caution from the CAUT lamp
    statusStrip(S, { condition: 'RED' });
    annRow(S, TOP.row1, [['MISSILE', '2  0:37'], ['BREACH', 'AFT STBD']], [['FIRE', 'CARGO 1']], { warnOn: true, cautOn: true });
    ticker(S, [
      { sev: 'caution', tag: 'ENG', text: "OKAFOR: FIRE IN CARGO 1, I'M ON IT." },
      { sev: 'warning', tag: 'TAC', text: 'SHIP: TWO MISSILES INBOUND, PORT SIDE, 37 SECONDS.' },
      { sev: 'warning', tag: 'ENG', text: 'SHIP: AFT STARBOARD VENTING, NOBODY ON IT.' },
    ]);
    suit(S, { air: '4:10', kpa: '31', o2: 'O2 LOW', alert: true });
    S.finish();
  };

  Object.assign(K, { st: { deck, suit, statusStrip, burnCue, hostile, bandRack, rail, mfd, board, annRow, ticker, helmCluster, tacCluster, sensorsPage, defensePage, hints, BAND, TOP, LMFD, CL, RMFD } });
})();
