// FILAMENT x Context-driven design (functional-context.md, version 2): two racks frame the view (the
// status strip on top, the seat's instruments along the bottom) and everything that comes and goes is a
// slab that powers up in a fixed slot of its zone.
(() => {
  const K = KIT;
  const R = Math.PI / 180;
  const LX = 12, RX = 960, CW = 308;

  // ------------------------------------------------------------------ racks
  function topRack(S, d) {
    K.rackShape(S, [[-8, -8], [1288, -8], [1288, 30], [-8, 30]], { radius: 6, shadowY: 3 });
    const y = 4, h = 19, win = (x, w) => S.module(x, y, w, h, { filaments: 0, glare: false, radius: 2 });
    // seat: fixed legends, the seat in use lit inverse
    win(8, 214);
    let x = 15;
    for (const seat of ['HELM', 'FIRE CTL', 'GUN', 'DECK']) {
      const w = S.legendWidth(seat, { size: 9.5 });
      if (seat === d.seat) { S.rectLit(x - 3.5, y + 3, w + 7, h - 6, { level: 'norm', r: 1 }); S.knockout(x, y + 13.2, seat, { size: 9.5 }); }
      else S.legend(x, y + 13.2, seat, { size: 9.5, lit: false });
      x += w + 16;
    }
    // helm authority: who or what flies when it isn't the captain at the helm; a caution pip when nobody does
    win(228, 372);
    K.pip(S, 236, y + h / 2, 'amber', !!d.authCaution);
    S.dm(244, y + 4.75, d.authority || '', { pitch: 1.5, cells: 39, level: 'norm' });
    win(606, 110);
    S.legend(661, y + 13, 'BATTLE STATIONS', { size: 8.5, align: 'center', color: 'amber', lit: !!d.battle, level: 'norm' });
    // the caution-and-warning chip, lit only over full-screen modes
    win(722, 74);
    K.pip(S, 731, y + h / 2, 'red', false); S.seg7(737, y + 4, 11, '', { cells: 1 });
    K.pip(S, 760, y + h / 2, 'amber', false); S.seg7(766, y + 4, 11, '', { cells: 1 });
    S.silk(722, 30 + 7, '', {});
    // clock and warp
    win(946, 146);
    S.legend(953, y + 13, 'DAY', { size: 8.5, level: 'dim' });
    S.seg7(973, y + 4, 11, d.day, { cells: 2, level: 'norm' });
    S.seg7(1012, y + 4, 11, d.time, { cells: 4, level: 'norm' });
    S.legend(1062, y + 13, '', { size: 8.5 });
    win(1098, 104);
    S.seg7(1104, y + 4, 11, d.warp, { cells: 5, level: d.warp === '1' ? 'dim' : 'norm' });
    S.legend(1155, y + 13, 'X', { size: 9, level: d.warp === '1' ? 'dim' : 'norm' });
    S.legend(1166, y + 13, 'RAILS', { size: 8.5, lit: !!d.rails, level: 'norm' });
    win(1208, 64);
    S.legend(1240, y + 13, 'PAUSED', { size: 8.5, align: 'center', lit: !!d.paused, level: 'norm' });
  }
  // The seat's rack along the bottom: H4's lights on the left wing, H1 in the tower, H5's key on the right.
  function bottomRack(S) {
    K.rackShape(S, [[-8, 808], [-8, 742], [430, 742], [430, 678], [850, 678], [850, 742], [1288, 742], [1288, 808]], { radius: 7 });
  }

  // ------------------------------------------------------------------ H4 ship condition
  // Four lights: each a cell built for alerting, its frame behind amber and its block behind red.
  function h4Lights(S, x, y, states) {
    S.silk(x + 1, y - 3.5, 'Ship', { size: 6.5 });
    [['HULL', states.hull], ['SHIELDS', states.shields], ['POWER', states.power], ['LIFE SUP', states.life]].forEach(([tag, sev], i) => {
      const cx = x + i * 76;
      S.module(cx, y, 72, 30, { filaments: 0, glare: false, radius: 2 });
      K.tile(S, cx + 2, y + 2, 68, 26, tag, null, sev || 'off', { tagSize: 10 });
    });
  }
  // The expanded face: the plan, then a bar per light.
  function h4Face(S, x, y, w, h, d) {
    K.slab(S, x, y, w, h, { pad: 5, title: d.title || 'Ship condition' });
    let by = y + 8;
    if (d.plan) {
      K.shipPlan(S, x + 6, y + 6, w - 12, 104, { states: d.plan.states, crew: d.plan.crew, labels: true, blinkOn: true });
      by = y + 128;
    }
    for (const r of d.bars) {
      S.legend(x + 8, by + 8, r.label, { size: 8.5, level: 'dim' });
      if (r.text) S.dm(x + 70, by, r.text, { pitch: 1.5, cells: 15, level: 'norm' });
      else S.bar(x + 70, by + 1, 150, 8, 30, r.frac, { zone: K.DEPLETE, level: 'norm' });
      if (r.value != null) S.seg7(x + 228, by - 1, 11, r.value, { cells: r.cells || 3, level: 'norm' });
      if (r.unit) S.legend(x + 272, by + 9, r.unit, { size: 7.5, level: 'dim' });
      by += 20;
    }
  }

  // ------------------------------------------------------------------ H1 flight display (the tower)
  function h1(S, d) {
    const x = 440, y = 688, w = 400, h = 96;
    S.silk(x + 1, y - 3.5, 'Flight', { size: 6.5 });
    S.module(x, y, w, h, { filaments: 3 });
    // frame: the reference the speed is in, AUTO or pinned
    K.states(S, x + 10, y + 15, d.frames, { size: 9, gap: 7 });
    S.legend(x + 172, y + 15, 'AUTO', { size: 9, lit: d.auto, level: 'dim', box: true });
    // the master alert cue: a cell built for it, in the pilot's eyeline
    S.module(x + w - 100, y + 4, 94, 17, { filaments: 0, glare: false, radius: 1.5 });
    if (d.cue && d.cue.sev === 'warning') { S.rectLit(x + w - 98, y + 6, 90, 13, { color: 'red', level: 'norm', r: 1 }); K.knock(S, x + w - 94, y + 9, d.cue.code.padEnd(9).slice(0, 9)); }
    else if (d.cue) S.frameLit(x + w - 98, y + 6, 90, 13, 1, { color: 'amber', level: 'norm' });
    else S.dm(x + w - 94, y + 9, '', { pitch: 1.5, cells: 9, color: 'red' });
    // speed in the frame
    S.seg7(x + 10, y + 26, 34, d.speed, { cells: 5, level: 'norm' });
    S.legend(x + 162, y + 44, 'M/S', { size: 10, level: 'dim' });
    K.states(S, x + 162, y + 58, [['CLOSING', d.closing === 'closing' && 'dim'], ['OPENING', d.closing === 'opening' && 'dim']], { size: 7.5, gap: 4 });
    // thrust: six lamps around a hull glyph (main fore and aft, turn, strafe)
    const tx = x + 262, ty = y + 44;
    S.legend(tx - 12, ty - 14, 'THRUST', { size: 7, level: 'dim' });
    S.icon('ship', tx - 2, ty - 8, 16, { level: 'dim', lit: false });
    const lamp = (px, py, rows, on) => S.sprite(px, py, rows, { center: true, pitch: 1.4, level: on ? 'norm' : 'norm', color: 'zinc', layer: on ? 'glass' : 'ghost' });
    const tri = { r: ['#..', '##.', '###', '##.', '#..'], l: ['..#', '.##', '###', '.##', '..#'], u: ['..#..', '.###.', '#####'], d: ['#####', '.###.', '..#..'] };
    const th = d.thrust || {};
    const t = (px, py, k, on) => { if (on) S.sprite(px, py, tri[k], { center: true, pitch: 1.4, level: 'norm' }); else ghostSprite(S, px, py, tri[k], 1.4); };
    t(tx - 14, ty, 'r', th.main); t(tx + 22, ty, 'l', th.retro); t(tx + 6, ty - 13, 'd', th.strafeR); t(tx + 6, ty + 13, 'u', th.strafeL);
    t(tx + 22, ty - 12, 'd', th.turnL); t(tx + 22, ty + 12, 'u', th.turnR);
    S.legend(x + 312, y + 50, 'ASSIST', { size: 9.5, lit: d.assist, level: 'norm', box: true });
    // delta-v left, propellant share as the bar
    S.legend(x + 10, y + 86, 'ΔV', { size: 9, level: 'dim' });
    S.seg7(x + 26, y + 72, 15, d.dv, { cells: 4, level: 'norm' });
    S.legend(x + 80, y + 86, 'M/S', { size: 8, level: 'dim' });
    S.bar(x + 106, y + 77, 230, 9, 40, d.prop, { zone: K.DEPLETE, level: 'dim' });
    S.seg7(x + 344, y + 74, 12, String(Math.round(d.prop * 100)), { cells: 3, level: 'dim' });
    S.legend(x + 382, y + 86, '%', { size: 8, level: 'dim' });
  }
  function ghostSprite(S, x, y, rows, p) {
    const d = p * 0.8, cx = x - rows[0].length * p / 2, cy = y - rows.length * p / 2;
    rows.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === '#') S.dot(cx + c * p + p / 2, cy + r * p + p / 2, { size: d, ghostOnly: true, k: 1.6 }); }));
  }

  // ------------------------------------------------------------------ H5 contact scope
  // Collapsed: a key with the count of contacts beyond 50 km. Open: a pod with a graphic dot panel.
  function h5Key(S, n) {
    const x = 1212, y = 748;
    S.silk(x + 1, y - 3.5, 'Scope', { size: 6.5 });
    S.module(x, y, 56, 22, { filaments: 0, glare: false, radius: 2 });
    S.seg7(x + 6, y + 4, 14, String(n), { cells: 1, level: 'dim' });
    S.dm(x + 24, y + 6, '>', { pitch: 1.5, level: 'dim', ghost: false });
    S.legend(x + 38, y + 15, 'KM', { size: 7, lit: false });
    S.key(x + 8, y + 26, 40, 11, '', {});
  }
  function h5Scope(S, d) {
    const cx = 1176, cy = 646, r = 86;
    S.pod(cx, cy, r + 10);
    S.roundModule(cx, cy, r, { filaments: 3 });
    // the dot grid of the graphic panel, rings and heading
    for (let yy = -r; yy <= r; yy += 4) for (let xx = -r; xx <= r; xx += 4) if (xx * xx + yy * yy < (r - 3) * (r - 3)) S.dot(cx + xx, cy + yy, { size: 1.1, ghostOnly: true, k: 0.9 });
    K.ringDots(S, cx, cy, r * 0.5, { level: 'dim', step: 4, size: 1.2 });
    K.ringDots(S, cx, cy, r - 6, { level: 'dim', step: 4, size: 1.2 });
    S.dotLine(cx, cy, cx + Math.cos(d.heading * R) * (r - 10), cy + Math.sin(d.heading * R) * (r - 10), { step: 3, size: 1.2, level: 'dim', layer: 'glass' });
    S.sprite(cx, cy, ['.#.', '###', '.#.'], { center: true, pitch: 1.6, level: 'norm' });
    for (const c of d.contacts) {
      const px = cx + c.x * (r - 6), py = cy + c.y * (r - 6);
      if (c.kind === 'hostile') S.sprite(px, py, ['..#..', '.#.#.', '#####'], { center: true, pitch: 1.6, color: 'red', level: 'norm' });
      if (c.kind === 'missile') S.sprite(px, py, ['.#.', '###', '.#.'], { center: true, pitch: 1.3, color: 'red', level: 'full' });
      if (c.kind === 'neutral') S.sprite(px, py, ['.#.', '#.#', '.#.'], { center: true, pitch: 1.6, level: 'dim' });
      if (c.kind === 'station') S.sprite(px, py, ['###', '#.#', '###'], { center: true, pitch: 1.6, level: 'norm' });
      if (c.lock) for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) { S.dot(px + sx * 7, py + sy * 7, { size: 1.3, level: 'full' }); S.dot(px + sx * 5, py + sy * 7, { size: 1.3, level: 'full' }); S.dot(px + sx * 7, py + sy * 5, { size: 1.3, level: 'full' }); }
    }
    S.legend(cx, cy + r + 18, d.range, { size: 8.5, align: 'center', level: 'dim' });
    S.key(cx - 52, cy + r + 10, 22, 11, '−', {}); S.key(cx + 30, cy + r + 10, 22, 11, '+', {});
  }

  // ------------------------------------------------------------------ the persistent layer: P2 to P5
  // P2: severity pips in fixed columns, then code, detail, who is handling it, timer.
  function p2(S, rows, o = {}) {
    const x = LX, y = 38, h = 10 + rows.length * 17;
    K.slab(S, x, y, CW, h, { pad: 5, title: 'Caution and warning' });
    rows.forEach((r, i) => {
      const ry = y + 7 + i * 17;
      K.pip(S, x + 8, ry + 5.25, 'red', r.sev === 'warning');
      K.pip(S, x + 15, ry + 5.25, 'amber', r.sev === 'caution');
      S.dm(x + 22, ry, r.code, { pitch: 1.5, cells: 11, level: r.sev === 'warning' ? 'norm' : 'norm' });
      S.dm(x + 125, ry, r.detail, { pitch: 1.5, cells: 8, level: 'dim' });
      // handling: the crew glyph and how many, or a ghost glyph and dashes when nobody is
      S.icon('crew', x + 202, ry - 0.5, 11, { level: 'norm', lit: !!r.crew });
      S.seg7(x + 215, ry + 0.5, 10, r.crew ? String(r.crew) : '-', { cells: 1, level: r.crew ? 'norm' : 'dim' });
      S.seg7(x + 236, ry + 0.5, 10, r.timer || '', { cells: 4, level: 'norm' });
    });
  }
  function p3(S, d) {
    const x = 390, y = 38, w = 500, h = 60;
    K.slab(S, x, y, w, h, { pad: 5, title: 'Threat' });
    S.seg14(x + 10, y + 7, 22, 'MISSILES', { level: 'full', color: 'red' });
    S.seg7(x + 172, y + 6, 26, d.count, { cells: 1, level: 'full', color: 'red' });
    S.legend(x + 206, y + 26, 'IMPACT', { size: 11, color: 'red', level: 'norm' });
    S.seg7(x + 254, y + 6, 26, d.t, { cells: 3, level: 'full', color: 'red' });
    S.legend(x + 318, y + 30, 'S', { size: 11, color: 'red', level: 'norm' });
    let cx = x + 10;
    cx += K.keycap(S, cx, y + 41, 'F', { h: 12 }) + 4;
    cx += S.legend(cx, y + 51, 'DECOY READY', { size: 9.5, lit: d.decoy, level: 'norm' }) + 18;
    S.legend(cx, y + 51, 'POINT DEFENCE ENGAGING', { size: 9.5, lit: d.pd, level: 'norm' });
    S.dm(x + 352, y + 10, d.down ? '1 DOWN' : '', { pitch: 2, cells: 6, color: 'red', level: 'norm' });
  }
  function p4(S, lines) {
    const x = RX, y = 38, h = 8 + lines.length * 17;
    K.slab(S, x, y, CW, h, { pad: 5, title: 'Messages' });
    lines.forEach(([src, text], i) => {
      S.dm(x + 6, y + 5 + i * 17, src, { pitch: 1.5, cells: 9, level: 'dim' });
      S.dm(x + 6 + 10 * 9, y + 5 + i * 17, text, { pitch: 1.5, cells: 23, level: 'norm' });
    });
  }
  function p5(S, d) {
    const x = LX, y = 176, h = 36;
    K.slab(S, x, y, CW, h, { pad: 5, title: 'Objective' });
    S.dm(x + 6, y + 4, d.l1, { pitch: 1.5, cells: 24, level: 'norm' });
    S.dm(x + 6, y + 20, d.l2, { pitch: 1.5, cells: 24, level: 'dim' });
    S.seg7(x + 236, y + 6, 22, d.left, { cells: 4, level: 'norm' });
  }

  // ------------------------------------------------------------------ H2 navigation
  function h2(S, d, compact) {
    const x = LX, y = compact ? 176 : 228;
    const rowH = 17, rows = compact ? 1 : 7, h = 8 + rows * rowH;
    K.slab(S, x, y, CW, h, { pad: 5, title: 'Navigation' });
    const lab = (t, ry) => S.legend(x + 6, ry + 9.5, t, { size: 8.5, level: 'dim' });
    const T = (tx, ry, v, cells = 4) => { S.legend(tx, ry + 9.5, 'T-', { size: 8, level: 'dim' }); return S.seg7(tx + 12, ry + 0.5, 10, v, { cells, level: 'norm' }); };
    let ry = y + 5;
    if (!compact) {
      lab('ORBIT', ry); S.dm(x + 46, ry, d.body, { pitch: 1.5, cells: 6, level: 'norm' });
      S.legend(x + 160, ry + 9.5, 'ALT', { size: 8, level: 'dim' }); S.seg7(x + 180, ry + 0.5, 10, d.alt, { cells: 4, level: 'norm' }); S.legend(x + 216, ry + 9.5, 'KM', { size: 8, level: 'dim' });
      ry += rowH;
      S.legend(x + 46, ry + 9.5, 'AP', { size: 8, level: 'dim' }); S.seg7(x + 62, ry + 0.5, 10, d.ap, { cells: 4, level: 'dim' }); S.legend(x + 100, ry + 9.5, 'KM', { size: 7.5, level: 'dim' }); T(x + 160, ry, d.apT, 5);
      ry += rowH;
      S.legend(x + 46, ry + 9.5, 'PE', { size: 8, level: 'dim' }); S.seg7(x + 62, ry + 0.5, 10, d.pe, { cells: 4, level: 'dim' }); S.legend(x + 100, ry + 9.5, 'KM', { size: 7.5, level: 'dim' }); T(x + 160, ry, d.peT, 5);
      ry += rowH;
      lab('NAV', ry); S.dm(x + 46, ry, d.nav, { pitch: 1.5, cells: 14, level: 'norm' });
      S.seg7(x + 176, ry + 0.5, 10, d.navRange, { cells: 5, level: 'norm' }); S.legend(x + 222, ry + 9.5, 'KM', { size: 7.5, level: 'dim' });
      S.legend(x + 240, ry + 9.5, 'OPENING', { size: 7.5, lit: d.opening, level: 'dim' });
      ry += rowH;
      S.legend(x + 46, ry + 9.5, 'CPA', { size: 8, level: 'dim' }); S.seg7(x + 66, ry + 0.5, 10, d.cpa, { cells: 3, level: 'dim' }); S.legend(x + 96, ry + 9.5, 'KM', { size: 7.5, level: 'dim' });
      T(x + 160, ry, d.cpaT, 5);
      ry += rowH;
    }
    lab('NEXT', ry);
    S.seg14(x + 46, ry + 1, 10, d.next, { cells: 8, level: 'norm' });
    T(x + 132, ry, d.nextT, 4);
    S.seg7(x + 196, ry + 0.5, 10, d.nextDv, { cells: 3, level: 'norm' }); S.legend(x + 224, ry + 9.5, 'M/S', { size: 7.5, level: 'dim' });
    S.legend(x + 260, ry + 9.5, d.by, { size: 8, level: 'norm' });
    if (!compact) {
      ry += rowH;
      lab('PLAN', ry);
      S.legend(x + 46, ry + 9.5, 'ΔV NEED', { size: 8, level: 'dim' }); S.seg7(x + 88, ry + 0.5, 10, d.need, { cells: 4, level: 'norm' });
      S.legend(x + 132, ry + 9.5, 'HAVE', { size: 8, level: 'dim' }); S.seg7(x + 158, ry + 0.5, 10, d.have, { cells: 4, level: 'norm' });
      S.legend(x + 198, ry + 9.5, 'M/S', { size: 7.5, level: 'dim' });
      S.legend(x + 230, ry + 9.5, 'SHORT', { size: 8, color: 'amber', lit: !!d.short, level: 'norm' });
    }
  }

  // ------------------------------------------------------------------ H6 lock card, H7 armament
  function h6(S, d) {
    const x = RX, y = 176, h = 100;
    K.slab(S, x, y, CW, h, { pad: 5, title: 'Lock' });
    S.legend(x + 6, y + 15, 'LOCK', { size: 8.5, level: 'dim' });
    S.dm(x + 36, y + 4, d.name, { pitch: 2, cells: 10, level: 'norm' });
    K.states(S, x + 166, y + 15, [['FRIEND', false], ['NEUTRAL', false], ['HOSTILE', d.hostile && 'norm', 'red']], { size: 8.5, gap: 6 });
    K.states(S, x + 6, y + 34, [['FIRING ON YOU', d.firing && 'full', 'red'], ['FLEEING', false], ['DISABLED', false], ['SURRENDERED', false], ['WRECK', false]], { size: 8, gap: 7 });
    S.seg7(x + 6, y + 44, 22, d.range, { cells: 3, level: 'norm' }); S.legend(x + 58, y + 64, 'KM', { size: 9, level: 'dim' });
    K.states(S, x + 84, y + 52, [['CLOSING', d.closing === 'closing' && 'dim'], ['OPENING', d.closing === 'opening' && 'dim']], { size: 7.5, gap: 4 });
    S.seg7(x + 84, y + 56, 12, d.rate, { cells: 3, level: 'norm' }); S.legend(x + 114, y + 67, 'M/S', { size: 7.5, level: 'dim' });
    S.legend(x + 160, y + 52, 'SHOT', { size: 7.5, level: 'dim' });
    S.seg7(x + 160, y + 56, 12, d.shot, { cells: 3, level: 'norm' }); S.legend(x + 191, y + 67, 'S', { size: 7.5, level: 'dim' });
    S.legend(x + 214, y + 52, 'BEST HIT', { size: 7.5, level: 'dim' });
    K.states(S, x + 214, y + 67, [['GOOD', d.hit === 'GOOD' && 'norm'], ['FAIR', d.hit === 'FAIR' && 'norm'], ['POOR', d.hit === 'POOR' && 'norm']], { size: 8, gap: 5 });
    S.rectLit(x + 6, y + 78, CW - 12, 0.7, { ghostOnly: true, k: 2 });
    S.dm(x + 6, y + 84, '', { pitch: 1.5, cells: 33 });
  }
  function h7(S, d) {
    const x = RX, y = 290, h = 60;
    K.slab(S, x, y, CW, h, { pad: 5, title: 'Armament' });
    S.legend(x + 6, y + 14, 'GUNS', { size: 8.5, level: 'dim' });
    K.states(S, x + 40, y + 14, [['READY', d.guns === 'READY' && 'norm'], ['RELOAD', d.guns === 'RELOAD' && 'norm'], ['NO PWR', false], ['ARC', false]], { size: 8.5, gap: 6 });
    K.states(S, x + 200, y + 14, [['YOU', d.who === 'YOU' && 'norm'], ['GUNNER', d.who === 'GUNNER' && 'norm'], ['PD', d.who === 'PD' && 'norm']], { size: 8.5, gap: 6 });
    S.legend(x + 6, y + 33, 'MSL', { size: 8.5, level: 'dim' }); S.seg7(x + 28, y + 23, 12, d.msl, { cells: 1, level: 'norm' });
    S.legend(x + 44, y + 33, 'RELOAD', { size: 8.5, lit: !!d.reload, level: 'dim' }); S.seg7(x + 82, y + 23, 12, d.reload || '', { cells: 2, level: 'norm' }); S.legend(x + 104, y + 33, 'S', { size: 8, level: 'dim' });
    S.legend(x + 130, y + 33, 'DECOY', { size: 8.5, level: 'dim' }); S.seg7(x + 164, y + 23, 12, d.decoy, { cells: 1, level: 'norm' });
    S.legend(x + 6, y + 52, 'SHIELDS', { size: 8.5, level: 'dim' });
    K.states(S, x + 52, y + 52, [['UP', d.shields === 'UP' && 'norm'], ['DOWN', d.shields === 'DOWN' && 'norm']], { size: 8.5, gap: 6 });
    S.legend(x + 120, y + 52, 'TRIM', { size: 8.5, level: 'dim' }); S.dm(x + 146, y + 43, d.trim, { pitch: 1.5, cells: 5, level: 'norm' });
  }

  // ------------------------------------------------------------------ X3 emergency card, D1, D3
  function x3(S, d) {
    const x = 390, y = 38, w = 500, h = 92;
    K.slab(S, x, y, w, h, { pad: 5, title: 'Emergency' });
    S.rectLit(x + 4, y + 4, w - 8, 24, { color: 'red', level: 'norm', r: 1.5 });
    S.knockout(x + w / 2, y + 22, d.title, { size: 15, align: 'center', condense: 0.8 });
    d.rows.forEach((r, i) => {
      const ry = y + 38 + i * 26;
      S.dm(x + 12, ry + 2, r.what, { pitch: 2, cells: 13, level: 'norm' });
      let kx = x + 182;
      kx += K.keycap(S, kx, ry, r.key, { h: 15, size: 8 }) + 6;
      S.dm(kx, ry + 2.5, r.how, { pitch: 1.5, cells: 6, level: 'norm' });
      S.dm(x + 300, ry + 2.5, r.cost || '', { pitch: 1.5, cells: 21, level: 'dim' });
    });
  }
  // D1: the captain's own vitals are the captain's own kit: an LCD.
  function d1(S, y, d) {
    const x = LX, w = CW, h = 74;
    K.plate(S, x, y, w, h, { pad: 5, radius: 7 });
    S.silk(x + 2, y - 0.5, 'Captain', { size: 6.5 });
    S.lcdPanel(x + 2, y + 4, w - 4, h - 6, { backlight: d.alert });
    S.lcdText(x + 12, y + 20, d.condition, { size: 9 });
    S.lcdText(x + 12, y + 36, 'SUIT ON', { size: 9 });
    S.lcdText(x + 12, y + 52, 'HERE: ' + d.here, { size: 9 });
    S.lcdText(x + 158, y + 20, 'SUIT AIR', { size: 8 });
    S.lcdSeg7(x + 158, y + 26, 34, d.air, { slant: 6 });
  }

  // ================================================================== 11.1 Helm, cruise
  SCREENS['cx-helm-cruise'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 5 });
    await K.hull(S, 'corvette', 640, 404, 0.115, -6);
    const a = -8 * R;
    K.velMark(S, 'pro', 640 + Math.cos(a) * 92, 404 + Math.sin(a) * 92, { level: 'norm' });
    K.velMark(S, 'retro', 640 - Math.cos(a) * 92, 404 - Math.sin(a) * 92, { level: 'dim' });
    K.edge(S, 1250, 330, 'r', ['SHACKLETON ICE', '1,240 KM'], { level: 'norm' });
    S.projectNow();

    topRack(S, { seat: 'HELM', day: '0', time: '00:33', warp: '1' });
    bottomRack(S);
    p5(S, { l1: 'DELIVER 120 WATER', l2: 'TO SHACKLETON ICE', left: '1.4d' });
    h2(S, { body: 'EARTH', alt: '400', ap: '403', apT: '24:10', pe: '397', peT: '1h10', nav: 'SHACKLETON ICE', navRange: '1240', opening: 'dim', cpa: '3.2', cpaT: '2h05',
      next: 'BURN 1/2', nextT: '12:05', nextDv: '420', by: 'HELM', need: '860', have: '1240' });
    h4Lights(S, 16, 752, {});
    h1(S, { frames: [['ORBIT', 'norm'], ['NAV', false], ['LOCK', false], ['PORT', false]], auto: 'dim', speed: '7672', assist: 'norm', dv: '1240', prop: 0.72, thrust: {} });
    h5Key(S, 3);
    S.finish();
  };

  // ================================================================== 11.4 Helm, combat
  SCREENS['cx-helm-combat'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 31 });
    await K.hull(S, 'raider', 846, 300, 0.09, 160);
    await K.hull(S, 'corvette', 640, 404, 0.115, -14);
    // world: the lock (zinc square brackets) on a hostile (red corners), labelled with name and range
    K.brackets(S, 846, 300, 30, 'tri', { pitch: 2, len: 5, color: 'red', level: 'norm' });
    K.brackets(S, 846, 300, 40, 'square', { pitch: 2, len: 6, level: 'full', ticks: true });
    K.label(S, 896, 262, ['RAIDER', '7.7 KM'], { pitch: 1.5, level: 'norm' });
    // missiles: glyphs and their predicted tracks to the hull
    K.missile(S, 790, 336); K.missile(S, 812, 352);
    K.track(S, [[790, 336], [700, 378]], { step: 7 }); K.track(S, [[812, 352], [706, 392]], { step: 7 });
    K.cross(S, 690, 386);
    const a = -20 * R;
    K.velMark(S, 'pro', 640 + Math.cos(a) * 92, 404 + Math.sin(a) * 92 + 26, { level: 'norm' });
    K.velMark(S, 'retro', 640 - Math.cos(a) * 92, 404 - Math.sin(a) * 92 - 26, { level: 'dim' });
    S.projectNow();

    topRack(S, { seat: 'HELM', battle: true, day: '0', time: '00:41', warp: '1' });
    bottomRack(S);
    p2(S, [{ sev: 'warning', code: 'HULL BREACH', detail: 'CARGO', crew: 1 }]);
    p3(S, { count: '2', t: '37', decoy: true, pd: true });
    p4(S, [['GUNNERY', 'PD HIT 1 MISSILE']]);
    h2(S, { next: 'BURN 1/2', nextT: '12:05', nextDv: '420', by: 'HELM' }, true);
    h6(S, { name: 'RAIDER', hostile: true, firing: true, range: '7.7', closing: 'closing', rate: '12', shot: '1.3', hit: 'FAIR' });
    h7(S, { guns: 'READY', who: 'YOU', msl: '3', reload: '4', decoy: '2', shields: 'UP', trim: 'BOW' });
    h4Face(S, LX, 532, CW, 198, {
      plan: { states: { 'CARGO 2': 'breach' }, crew: [[10, 6, true], [22, 4], [6, 3], [15, 6]] },
      bars: [
        { label: 'HULL', frac: 0.81, value: '81', unit: '%' },
        { label: 'SHIELDS', frac: 0.77, value: '230', unit: 'KG' },
        { label: 'POWER', frac: 0.9, value: '3.0', unit: 'MW' },
      ],
    });
    h4Lights(S, 16, 752, { hull: 'warning' });
    h1(S, { frames: [['ORBIT', false], ['NAV', false], ['LOCK', 'norm'], ['PORT', false]], auto: 'dim', speed: '12', closing: 'closing', assist: 'norm', dv: '1240', prop: 0.72,
      thrust: { main: true }, cue: { sev: 'warning', code: 'BREACH' } });
    h5Scope(S, { heading: -14, range: '10 KM', contacts: [{ x: 0.62, y: -0.3, kind: 'hostile', lock: true }, { x: 0.36, y: -0.12, kind: 'missile' }, { x: 0.4, y: -0.02, kind: 'missile' }, { x: -0.5, y: 0.45, kind: 'neutral' }] });
    S.finish();
  };

  // ================================================================== 11.7 Deck, emergency
  SCREENS['cx-deck-emergency'] = async (canvas) => {
    const S = K.scene(canvas);
    await K.world(S, 'bg/deck-dark.jpg');
    // D3: off-screen pointers, projected at the edges
    K.edge(S, 1252, 452, 'r', ['FIRE', '14 TILES'], { color: 'red', level: 'norm' });
    K.edge(S, 640, 782, 'd', ['ADA DOWN 9 TILES'], { color: 'red', level: 'norm' });
    S.projectNow();

    topRack(S, { seat: 'DECK', authority: 'HELM: NOBODY, DRIFTING', authCaution: true, battle: true, day: '0', time: '01:12', warp: '1' });
    p2(S, [
      { sev: 'warning', code: 'FIRE', detail: 'GALLEY', crew: 2 },
      { sev: 'warning', code: 'CREW CRIT', detail: 'ADA', crew: 1, timer: '3:40' },
      { sev: 'warning', code: 'VACUUM', detail: 'CARGO', crew: 0 },
      { sev: 'warning', code: 'NO POWER', detail: 'STUNNED', crew: 1, timer: '0:12' },
      { sev: 'caution', code: 'SMOKE', detail: 'CORRIDOR', crew: 0 },
    ]);
    x3(S, { title: 'SHIP DISABLED: NO POWER', rows: [
      { what: 'ABANDON SHIP', key: 'P', how: 'TWICE' },
      { what: 'CALL A TOW', key: 'B', how: 'TWICE', cost: '3,400 CR, -3 STANDING' },
    ] });
    p4(S, [['CREW', 'BEA TO THE REACTOR']]);
    // the context dims everything but H4 and D1 while X3 shows: on the ship's batteries, that is a brownout
    S.dimRegion(0, 0, 1280, 140, 0.55);
    h4Face(S, LX, 548, CW, 102, { title: 'Ship condition', bars: [
      { label: 'HULL', frac: 0.54, value: '54', unit: '%' },
      { label: 'POWER', text: 'NO POWER' },
      { label: 'LIFE SUP', text: 'EMERGENCY 42 KG' },
      { label: 'WORST', text: 'VACUUM' },
    ] });
    h4Lights(S, 16, 664, { hull: 'warning', power: 'warning', life: 'caution' });
    d1(S, 716, { condition: 'LIGHT WOUND', here: 'THIN AIR', air: '3:20', alert: true });
    S.finish();
  };

  Object.assign(K, { cx: { topRack, bottomRack, h4Lights, h4Face, h1, h5Key, h5Scope, p2, p3, p4, p5, h2, h6, h7, x3, d1 } });
})();
