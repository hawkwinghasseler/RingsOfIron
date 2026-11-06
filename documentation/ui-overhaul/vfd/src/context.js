// FILAMENT x Context-driven design (functional-context.md, version 10): two racks frame the view (the
// status strip on top, the seat's instruments along the bottom) and everything that comes and goes is a
// slab that powers up in a fixed slot of its zone. Laid at the minimum sizes (guide section 15, spec 12.4):
// column text is 5x7 at pitch 1.5, so a column's 34 characters are 34 cells of 9 units.
(() => {
  const K = KIT;
  const R = Math.PI / 180;
  const LX = 12, RX = 960, CW = 308;
  const P = 1.5, CELL = 9;
  // A slab row is 20 units: legends sit on its baseline, numbers and dot matrix stand on it.
  const ROW = 20, B = ry => ry + 14.5;
  const glyph = (S, x, ry, sev) => { // the severity columns: a red diamond, then an amber triangle
    S.dm(x, B(ry) - 10.5, sev === 'warning' ? '♦' : '', { pitch: P, cells: 1, color: 'red', level: 'full' });
    S.dm(x + CELL, B(ry) - 10.5, sev === 'caution' ? '▲' : '', { pitch: P, cells: 1, color: 'amber', level: 'full' });
  };

  // ------------------------------------------------------------------ P1 status strip (the top rack)
  function topRack(S, d) {
    K.rackShape(S, [[-8, -8], [1288, -8], [1288, 29], [-8, 29]], { radius: 6, shadowY: 3 });
    const y = 4, h = 20, b = y + 14.75;
    const win = (x, w) => S.module(x, y, w, h, { filaments: 0, glare: false, radius: 2 });
    // left: the seat, who flies, battle stations, the full-screen chip
    let x = 8;
    win(x, 14 * CELL + 9); S.dm(x + 6, b - 10.5, d.seat, { pitch: P, cells: 14, level: 'norm' }); x += 14 * CELL + 15;
    win(x, 36 * CELL + 9); S.dm(x + 6, b - 10.5, d.authority || '', { pitch: P, cells: 36, level: d.authHeld ? 'full' : 'norm' }); x += 36 * CELL + 15;
    const bw = S.legendWidth('BATTLE STATIONS') + 14;
    win(x, bw); S.legend(x + 7, b, 'BATTLE STATIONS', { color: 'amber', lit: !!d.battle, level: 'norm' }); x += bw + 6;
    // the caution-and-warning chip is the master alert cue while a full-screen mode is open; dark otherwise
    win(x, 66);
    glyph(S, x + 5, y + 4.25 - 4, d.chip && 'warning');
    S.seg7(x + 16, b - 12, 12, d.chip ? d.chip[0] : '', { cells: 1, color: 'red', level: 'norm' });
    S.dm(x + 36, b - 10.5, d.chip ? '▲' : '', { pitch: P, cells: 1, color: 'amber', level: 'full' });
    S.seg7(x + 47, b - 12, 12, d.chip ? d.chip[1] : '', { cells: 1, color: 'amber', level: 'norm' });
    const leftEnd = x + 66;
    // right, from the edge in: paused, warp, clock
    let rx = 1272;
    const pw = S.legendWidth('PAUSED') + 14; rx -= pw;
    win(rx, pw); S.legend(rx + 7, b, 'PAUSED', { lit: !!d.paused, level: 'norm' });
    const one = d.warp === '1';
    const ww = 6 + S.seg7Width(12, 5) + 3 + S.legendWidth('X') + 8 + S.legendWidth('RAILS') + 8 + S.dmWidth(20, { pitch: P }) + 6;
    rx -= ww + 6; win(rx, ww);
    let wx = rx + 6;
    wx += S.seg7(wx, b - 12, 12, d.warp, { cells: 5, level: one ? 'dim' : 'norm' }) + 3;
    wx += S.legend(wx, b, 'X', { level: one ? 'dim' : 'norm' }) + 8;
    wx += S.legend(wx, b, 'RAILS', { lit: !!d.rails, level: 'norm' }) + 8;
    S.dm(wx, b - 10.5, d.hold || '', { pitch: P, cells: 20, level: 'full' });
    const cw = 7 + S.legendWidth('DAY') + 5 + S.seg7Width(12, 3) + 10 + S.seg7Width(12, 4, { colons: 1 }) + 7;
    rx -= cw + 6; win(rx, cw);
    let cx = rx + 7;
    cx += S.legend(cx, b, 'DAY', { level: 'dim' }) + 5;
    cx += S.seg7(cx, b - 12, 12, d.day, { cells: 3, level: 'norm' }) + 10;
    S.seg7(cx, b - 12, 12, d.time, { cells: 4, level: 'norm' });
    K.fits(leftEnd, rx - 6, 'P1 strip');
  }

  // ------------------------------------------------------------------ the helm's rack along the bottom
  // H4's lights on the left wing, H1 in the tower with the master lamps on its shelf, H5 on the right wing.
  // Docked or latched the flight instruments retire: the tower sinks into the rack and only the shelf with
  // the master lamps stays proud of it.
  function bottomRack(S, o = {}) {
    if (o.tower === false) K.rackShape(S, [[-8, 808], [-8, 736], [516, 736], [516, 700], [764, 700], [764, 736], [1288, 736], [1288, 808]], { radius: 7 });
    else K.rackShape(S, [[-8, 808], [-8, 736], [424, 736], [424, 640], [856, 640], [856, 736], [1288, 736], [1288, 808]], { radius: 7 });
  }
  // The master alert cue: MASTER WARN with the newest warning's code, MASTER CAUT. Lamp keys: a click (or \)
  // acknowledges. On the tower's shelf at the helm; on deck, docked or latched, on a plate of their own.
  function cue(S, cx, y, d = {}, o = {}) {
    const w = 104, h = 30;
    if (o.plate) K.plate(S, cx - w - 10, y - 6, 2 * w + 20, h + 12, { pad: 0, radius: 6 });
    S.lampKey(cx - w - 4, y, w, h, 'MASTER WARN', d.warn || null, 'red', !!d.warn);
    S.lampKey(cx + 4, y, w, h, 'MASTER CAUT', null, 'amber', !!d.caut);
  }

  // ------------------------------------------------------------------ H4 ship condition
  // The compact face is hardware: a lamp cell per light on the rack's wing, so the lights never move.
  function h4Lights(S, x, y, lights) {
    const w = 300, h = 38, n = lights.length, gap = 4, tw = (w - 8 - gap * (n - 1)) / n;
    S.module(x, y, w, h, { filaments: 0, glare: false, radius: 2 });
    lights.forEach(([tag, sev], i) => K.tile(S, x + 4 + i * (tw + gap), y + 4, tw, h - 8, tag, null, sev || 'off', { tagSize: 13 }));
  }
  // The expanded face rises above them: the plan, then a row per light, its severity leading.
  function h4Face(S, x, y, d) {
    const planH = d.plan ? d.planH : 0, h = (d.plan ? planH + 10 : 4) + d.rows.length * ROW + 2;
    K.slab(S, x, y, CW, h, { pad: 5 });
    if (d.plan) K.shipPlan(S, x + 6, y + 6, CW - 12, planH, { plan: d.plan.plan, states: d.plan.states, crew: d.plan.crew, marks: d.plan.marks, labels: true, legendLabels: true });
    let ry = y + (d.plan ? planH + 10 : 4);
    for (const r of d.rows) { h4Row(S, x, ry, r); ry += ROW; }
    return h;
  }
  function h4Row(S, x, ry, r) {
    const b = B(ry), lv = r.sev ? 'norm' : 'dim', bx = x + 88, vx = x + 168;
    glyph(S, x + 5, ry, r.sev);
    S.legend(x + 27, b, r.label, { level: lv });
    if (r.text) S.dm(bx, b - 10.5, r.text, { pitch: P, cells: 9, level: 'full' });
    else K.dbar(S, bx, b - 9, 72, 7, 22, r.frac, { level: lv });
    let cx = vx + S.seg7(vx, b - 12, 12, r.value, { cells: 3, level: lv }) + 5;
    cx += S.legend(cx, b, r.unit, { level: 'dim' }) + 8;
    if (r.out != null) { // power: the reactors' output, and their rating beside it only while damage holds the
      // output under it (version 8); otherwise OF and the rating stay dark glass in their place
      cx += S.seg7(cx, b - 12, 12, r.out, { cells: 2, level: lv }) + 5;
      cx += S.legend(cx, b, 'OF', { level: 'dim', lit: r.rating != null }) + 4;
      cx += S.seg7(cx, b - 12, 12, r.rating ?? '', { cells: 2, level: lv }) + 5;
      cx += S.legend(cx, b, 'MW', { level: 'dim' });
    }
    K.fits(cx, x + CW - 4, 'H4 row ' + r.label);
  }

  // ------------------------------------------------------------------ H1 flight display (the tower)
  function h1(S, d) {
    const x = 440, y = 688, w = 400, h = 96;
    S.module(x, y, w, h, { filaments: 3 });
    if (d.off) return; // retired (docked or latched): the glass stays, unpowered
    // the frame: the tag in use lit, AUTO or pinned, and the frame's name
    const ya = y + 20;
    let ax = K.states(S, x + 10, ya, d.frames, { size: 13, gap: 8 });
    ax += S.legend(ax + 2, ya, 'AUTO', { lit: !!d.auto, level: 'dim', box: true });
    const nx = x + w - 10 - S.dmWidth(16, { pitch: P });
    K.fits(ax + 6, nx, 'H1 frame row');
    S.dm(nx, ya - 10.5, d.name || '', { pitch: P, cells: 16, level: 'norm' });
    // speed in the frame
    const yb = y + 30;
    const sw = S.seg7(x + 10, yb, 34, d.speed, { cells: 5, level: 'norm' });
    S.legend(x + 16 + sw, yb + 34, 'M/S', { level: 'dim' });
    // thrust: six lamps around a hull glyph (main fore and aft, turn, strafe)
    thrust(S, x + 238, yb + 17, d.thrust || {});
    S.legend(x + 306, yb + 22, 'ASSIST', { lit: !!d.assist, level: 'norm', box: true });
    // delta-v, the propellant share as the bar
    const yc = y + 88;
    let cx = x + 10;
    cx += S.legend(cx, yc, 'ΔV', { level: 'dim' }) + 5;
    cx += S.seg7(cx, yc - 15, 15, d.dv, { cells: 4, level: 'norm' }) + 5;
    cx += S.legend(cx, yc, 'M/S', { level: 'dim' }) + 16;
    const ex = x + w - 10 - 38, bw = ex - 12 - cx;
    const ps = K.dbar(S, cx, yc - 9, bw, 7, 36, d.prop, { level: 'dim', notches: [{ at: 0.25, color: 'amber' }, { at: 0.1, color: 'red' }] });
    K.pip(S, cx - 10, yc - 5.5, 'amber', ps === 'amber', { r: 1.5 }); K.pip(S, cx - 5, yc - 5.5, 'red', ps === 'red', { r: 1.5 });
    const pw = S.seg7(ex, yc - 12, 12, String(Math.round(d.prop * 100)), { cells: 3, level: 'dim' });
    S.legend(ex + pw + 3, yc, '%', { level: 'dim' });
  }
  function thrust(S, tx, ty, th) {
    const tri = { r: ['#..', '##.', '###', '##.', '#..'], l: ['..#', '.##', '###', '.##', '..#'], u: ['..#..', '.###.', '#####'], d: ['#####', '.###.', '..#..'] };
    S.icon('ship', tx - 10, ty - 10, 20, { level: 'dim', lit: false });
    const t = (px, py, k, on) => { if (on) S.sprite(px, py, tri[k], { center: true, pitch: 1.8, level: 'norm' }); else ghostSprite(S, px, py, tri[k], 1.8); };
    t(tx - 20, ty, 'r', th.main); t(tx + 22, ty, 'l', th.retro);
    t(tx, ty - 15, 'd', th.strafeR); t(tx, ty + 15, 'u', th.strafeL);
    t(tx + 18, ty - 13, 'd', th.turnL); t(tx + 18, ty + 13, 'u', th.turnR);
  }
  function ghostSprite(S, x, y, rows, p) {
    const d = p * 0.8, cx = x - rows[0].length * p / 2, cy = y - rows.length * p / 2;
    rows.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === '#') S.dot(cx + c * p + p / 2, cy + r * p + p / 2, { size: d, ghostOnly: true, k: 1.6 }); }));
  }

  // ------------------------------------------------------------------ H5 contact scope
  // Collapsed: a key with a window showing the count of contacts beyond 50 km. Open: a pod with a plotting scope.
  function h5Key(S, n) {
    const x = 1216, y = 740, w = 48, h = 48;
    S.key(x, y, w, h, '', {});
    S.module(x + 9, y + 5, 30, 24, { filaments: 0, glare: false, radius: 1.5 });
    S.seg7(x + 18.5, y + 9, 16, String(n), { cells: 1, level: 'dim' });
    S.silk(x + w / 2, y + 43, 'SCOPE', { align: 'center' });
  }
  function h5Scope(S, d) {
    const cx = 1168, cy = 684, r = 86;
    S.pod(cx, cy, r + 10);
    S.roundModule(cx, cy, r, { filaments: 3 });
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
      if (c.kind === 'station') S.sprite(px, py, ['###', '#.#', '###'], { center: true, pitch: 1.6, level: 'dim' });
      if (c.lock) for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) { S.dot(px + sx * 7, py + sy * 7, { size: 1.3, level: 'full' }); S.dot(px + sx * 5, py + sy * 7, { size: 1.3, level: 'full' }); S.dot(px + sx * 7, py + sy * 5, { size: 1.3, level: 'full' }); }
    }
    S.legend(cx, cy + r - 12, d.range, { align: 'center', level: 'dim' });
    S.key(cx - r - 8, cy + r - 16, 24, 24, '−', { size: 14 });
    S.key(cx + r - 16, cy + r - 16, 24, 24, '+', { size: 14 });
  }

  // ------------------------------------------------------------------ P2 caution and warning
  // Six rows of 24 in fixed fields, a cell apart where a full code meets its detail: severity (two filtered
  // columns), code 11 cells, detail 10, handling 3, timer 5. Handling (version 11, four states): YOU inverse;
  // ‖ while the crew hold back from it (held: at battle stations they keep clear of where crew were hit,
  // and stay inside), lit at L2 since it is a fact about the crew, not a call on the captain; a dot per crew
  // member on it, from four a dot and the count; BACK and the recovery time across handling and timer.
  function p2(S, rows, more) {
    const n = rows.length + (more ? 1 : 0), x = LX, y = 38, rh = 24;
    K.slab(S, x, y, CW, n * rh + 4, { pad: 5 });
    rows.forEach((r, i) => {
      const ry = y + 2 + i * rh, ty = ry + (rh - 7 * P) / 2, sy = ry + (rh - 12) / 2;
      glyph(S, x + 5, ry + (rh - ROW) / 2, r.sev);
      K.fits(r.code.length, 11, 'P2 code');
      S.dm(x + 27, ty, r.code, { pitch: P, cells: 11, level: 'norm' });
      S.dm(x + 133.5, ty, r.detail || '', { pitch: P, cells: 10, level: 'dim' });
      const hx = x + 226.5, tx = x + 256;
      if (r.you) S.dm(hx, ty, 'YOU', { pitch: P, cells: 3, inverse: [0, 3], level: 'norm' });
      else if (r.held) S.dm(hx, ty, '‖', { pitch: P, cells: 3, level: 'norm' });
      else if (r.crew) S.dm(hx, ty, r.crew > 3 ? '●' + r.crew : '●'.repeat(r.crew), { pitch: P, cells: 3, level: 'norm' });
      else if (r.back) S.legend(hx, sy + 12, 'BACK', { size: 12, level: 'dim' });
      else S.dm(hx, ty, '', { pitch: P, cells: 3 });
      const tw = S.seg7(tx, sy, 12, r.timer || r.back || '', { cells: 4, level: r.back ? 'dim' : 'norm' });
      K.fits(tx + tw, x + CW - 4, 'P2 timer');
    });
    if (more) S.dm(x + 27, y + 2 + rows.length * rh + (rh - 7 * P) / 2, more, { pitch: P, cells: 30, level: 'dim' });
  }

  // ------------------------------------------------------------------ P3 threat alert, P4 feed, P5 objective
  function p3(S, d) {
    const x = 390, y = 38, w = 500, h = 60;
    K.slab(S, x, y, w, h, { pad: 5 });
    // line 1 at P3's size, behind red glass: the count, MISSILES INBOUND, the soonest impact
    let cx = x + 10;
    cx += S.seg7(cx, y + 5, 22, d.count, { cells: 1, color: 'red', level: 'full' }) + 10;
    cx += S.seg14(cx, y + 8, 16, 'MISSILES INBOUND', { color: 'red', level: 'full' }) + 14;
    cx += S.legend(cx, y + 26, 'T-', { size: 18, color: 'red', level: 'norm' }) + 2;
    cx += S.seg7(cx, y + 5, 22, d.t, { cells: 2, color: 'red', level: 'full' }) + 4;
    cx += S.legend(cx, y + 26, 'S', { size: 18, color: 'red', level: 'norm' });
    const dx = x + w - 10 - S.dmWidth(6, { pitch: 2 });
    K.fits(cx + 8, dx, 'P3 line 1');
    S.dm(dx, y + 10, d.down ? '1 DOWN' : '', { pitch: 2, cells: 6, color: 'red', level: 'norm' });
    // line 2: the decoy (not on deck), then point defense
    const b = y + 52;
    let lx = x + 10;
    if (d.decoy) {
      lx += K.keycap(S, lx, b - 14, 'F', { h: 18 }) + 6;
      lx += S.legend(lx, b, 'DECOY', { level: 'dim' }) + 6;
      lx += S.legend(lx, b, 'READY', { lit: d.decoy === 'READY', level: 'norm' }) + 6;
      if (d.decoy !== 'READY') { lx += S.seg7(lx, b - 12, 12, d.decoy, { cells: 1, level: 'norm' }) + 3; lx += S.legend(lx, b, 'S', { level: 'dim' }); }
      lx += 20;
    }
    S.legend(lx, b, 'POINT DEFENSE ENGAGING', { lit: !!d.pd, level: 'norm' });
  }
  function p4(S, lines) {
    const x = RX, y = 38, rh = 24;
    K.slab(S, x, y, CW, lines.length * rh + 4, { pad: 5 });
    lines.forEach(([src, text], i) => {
      const ty = y + 2 + i * rh + (rh - 7 * P) / 2;
      S.dm(x + 6, ty, '', { pitch: P, cells: 33 });
      S.dm(x + 6, ty, src + ':', { pitch: P, level: 'dim', ghost: false });
      S.dm(x + 6 + (src.length + 2) * CELL, ty, text, { pitch: P, level: 'norm', ghost: false });
      K.fits(x + 6 + (src.length + 2 + text.length) * CELL, x + CW - 4, 'P4 line');
    });
  }
  function p5(S, d) {
    const x = LX, y = 194, h = 48;
    K.slab(S, x, y, CW, h, { pad: 5 });
    S.dm(x + 6, y + 6, d.l1, { pitch: P, cells: 33, level: 'norm' });
    const b = y + 40;
    S.dm(x + 6, b - 10.5, d.l2, { pitch: P, cells: d.left ? 21 : 33, level: 'dim' });
    if (d.left) {
      const tx = x + 6 + 21 * CELL + 4;
      const w = S.seg7(tx, b - 13, 13, d.left, { cells: 3, level: 'norm' });
      K.fits(tx + w + 6 + S.legend(tx + w + 6, b, d.unit, { level: 'dim' }), x + CW - 4, 'P5 time left');
    }
    return y + h;
  }

  // ------------------------------------------------------------------ H2 navigation
  // Labels are legends at L1, words dot matrix, numbers seven segments with their units as legends; a
  // number past 999 carries the tube's thousands mark. The face is a list of rows, since which rows show
  // depends on the plan (version 10): an apsis due after the next burn is left out, and a Nav around
  // another body names the body and gives the arrival where one around this body gives closing and the
  // closest approach. rows: [[label or null, parts], ...]; a part is ['l', text] a legend, ['n', digits,
  // cells] seven segments, ['t', text, cells] dot matrix, ['gap', units].
  function h2(S, y, rows) {
    const x = LX;
    K.slab(S, x, y, CW, rows.length * ROW + 6, { pad: 5 });
    const vx = x + 52;
    let ry = y + 3;
    for (const [lab, parts] of rows) {
      if (lab) S.legend(x + 6, B(ry), lab, { level: 'dim' });
      let cx = vx;
      for (const [k, v, n, o = {}] of parts) {
        if (k === 'l') cx += S.legend(cx, B(ry), v, { level: 'dim', ...o }) + 4;
        else if (k === 'n') cx += S.seg7(cx, B(ry) - 12, 12, v, { cells: n, level: 'norm', ...o }) + 6;
        else if (k === 't') cx += S.dm(cx, B(ry) - 10.5, v, { pitch: P, cells: n, level: 'norm', ...o }) + 6;
        else if (k === 'gap') cx += v;
      }
      K.fits(cx, x + CW - 2, 'H2 row ' + (lab || ry));
      ry += ROW;
    }
    return y + rows.length * ROW + 6;
  }

  // ------------------------------------------------------------------ H6 lock card, H7 armament
  function h6(S, y, d) {
    const x = RX, h = 4 * 24 + 6;
    K.slab(S, x, y, CW, h, { pad: 5 });
    const b = i => y + 3 + i * 24 + 17;
    S.legend(x + 6, b(0), 'LOCK', { level: 'dim' });
    S.dm(x + 52, b(0) - 14, d.name, { pitch: 2, cells: 16, level: 'norm' });
    // the state in one word (hostile behind red), and FIRING ON YOU as a flag of its own
    S.dm(x + 52, b(1) - 10.5, d.state, { pitch: P, cells: 11, level: 'norm', color: d.state === 'HOSTILE' ? 'red' : undefined });
    const fw = S.legendWidth('FIRING ON YOU') + 12, fx = x + CW - 6 - fw;
    if (d.firing) { S.rectLit(fx, b(1) - 14, fw, 18, { color: 'red', level: 'norm', r: 1.2 }); S.knockout(fx + 6, b(1), 'FIRING ON YOU'); }
    else { S.frameLit(fx, b(1) - 14, fw, 18, 1, { ghostOnly: true, color: 'red' }); S.legend(fx + 6, b(1), 'FIRING ON YOU', { color: 'red', lit: false }); }
    // range, closing or opening, the rate
    let cx = x + 52;
    cx += S.seg7(cx, b(2) - 15, 15, d.range, { cells: 3, level: 'norm' }) + 6;
    cx += S.legend(cx, b(2), d.unit, { level: 'dim' }) + 10;
    cx = K.states(S, cx, b(2), [['CLOSING', d.closing === 'closing' && 'norm'], ['OPENING', d.closing === 'opening' && 'norm']], { size: 13, gap: 6 });
    cx += S.seg7(cx, b(2) - 12, 12, d.rate, { cells: 3, level: 'norm' }) + 5;
    K.fits(cx + S.legend(cx, b(2), 'M/S', { level: 'dim' }), x + CW - 4, 'H6 range row');
    // time of flight for the guns, the best hit chance and its word
    cx = x + 52;
    cx += S.legend(cx, b(3), 'SHOT', { level: 'dim' }) + 4;
    cx += S.seg7(cx, b(3) - 12, 12, d.shot, { cells: 2, level: 'norm' }) + 5;
    cx += S.legend(cx, b(3), 'S', { level: 'dim' }) + 12;
    cx += S.legend(cx, b(3), 'HIT', { level: 'dim' }) + 4;
    cx += S.seg7(cx, b(3) - 12, 12, d.hit, { cells: 2, level: 'norm' }) + 5;
    cx += S.legend(cx, b(3), '%', { level: 'dim' }) + 10;
    cx = K.states(S, cx, b(3), [['GOOD', d.word === 'GOOD' && 'norm'], ['FAIR', d.word === 'FAIR' && 'norm'], ['POOR', d.word === 'POOR' && 'norm']], { size: 13, gap: 6 });
    K.fits(cx - 6, x + CW - 4, 'H6 shot row');
    return y + h;
  }
  function h7(S, y, d) {
    const x = RX, h = 3 * ROW + 6;
    K.slab(S, x, y, CW, h, { pad: 5 });
    let ry = y + 3, cx;
    S.legend(x + 6, B(ry), 'GUNS', { level: 'dim' });
    S.dm(x + 52, B(ry) - 10.5, d.guns, { pitch: P, cells: 8, level: 'norm' });
    cx = K.states(S, x + 52 + 8 * CELL + 8, B(ry), [['YOU', d.who === 'YOU' && 'norm'], ['GUNNER', d.who === 'GUNNER' && 'norm'], ['PD', d.who === 'PD' && 'norm']], { size: 13, gap: 7 });
    K.fits(cx - 7, x + CW - 4, 'H7 guns');
    ry += ROW;
    // missiles left across every launcher, ready while any can fire (version 7): two cells, since a total can
    // pass 9 (two dart racks hold 12)
    S.legend(x + 6, B(ry), 'MSL', { level: 'dim' });
    cx = x + 52;
    cx += S.seg7(cx, B(ry) - 12, 12, d.msl, { cells: 2, level: 'norm' }) + 6;
    cx += S.legend(cx, B(ry), 'LEFT', { level: 'dim' }) + 12;
    cx = K.states(S, cx, B(ry), [['READY', !d.reload && 'norm'], ['RELOAD', !!d.reload && 'norm']], { size: 13, gap: 6 });
    cx += S.seg7(cx, B(ry) - 12, 12, d.reload || '', { cells: 2, level: 'norm' }) + 5;
    K.fits(cx + S.legend(cx, B(ry), 'S', { level: 'dim' }), x + CW - 4, 'H7 missiles');
    ry += ROW;
    S.legend(x + 6, B(ry), 'SHLD', { level: 'dim' });
    cx = K.states(S, x + 52, B(ry), [['RAISED', d.shields === 'RAISED' && 'norm'], ['LOWERED', d.shields === 'LOWERED' && 'norm']], { size: 13, gap: 6 }) + 6;
    cx += S.legend(cx, B(ry), 'TRIM', { level: 'dim' }) + 4;
    K.fits(cx + S.dm(cx, B(ry) - 10.5, d.trim, { pitch: P, cells: 5, level: 'norm' }), x + CW - 4, 'H7 shields');
  }

  // ------------------------------------------------------------------ X2 boarding, X3 emergency
  // X2: the boarding at a glance in six rows of glass, and its two keys under it, each printed with its
  // action as the station board's are (7 rows in all, 308 x 155: the spec's 150 within its 10%).
  function x2(S, y, d) {
    const x = RX, rows = 6, gh = rows * ROW + 6, h = gh + 5 + 24;
    K.plate(S, x, y, CW, h, { pad: 5 });
    S.module(x, y, CW, gh, { radius: 2.5 });
    let ry = y + 3;
    const lab = t => S.legend(x + 6, B(ry), t, { level: 'dim' });
    const vx = x + 56;
    lab('BOARD');
    S.dm(vx, B(ry) - 10.5, d.name, { pitch: P, cells: 16, level: 'norm' });
    K.fits(K.states(S, vx + 16 * CELL + 6, B(ry), [['LATCHED', d.latched && 'norm'], ['EVA', !d.latched && 'norm']], { size: 13, gap: 6 }) - 6, x + CW - 4, 'X2 board');
    ry += ROW;
    S.dm(vx, B(ry) - 10.5, d.state, { pitch: P, cells: 25, level: 'norm' });
    ry += ROW;
    lab('PARTY');
    let cx = vx + S.seg7(vx, B(ry) - 12, 12, d.party, { cells: 1, level: 'norm' }) + 8;
    K.fits(cx + S.dm(cx, B(ry) - 10.5, d.partyState, { pitch: P, cells: 16, level: 'norm' }), x + CW - 4, 'X2 party');
    ry += ROW;
    lab('SALV');
    cx = vx + S.seg7(vx, B(ry) - 12, 12, d.marked, { cells: 1, level: 'norm' }) + 6;
    cx += S.legend(cx, B(ry), 'PARTS MARKED', { level: 'dim' }) + 10;
    cx += S.seg7(cx, B(ry) - 12, 12, d.off, { cells: 1, level: 'norm' }) + 6;
    S.legend(cx, B(ry), 'OFF', { level: 'dim' });
    ry += ROW;
    lab('CARGO');
    cx = vx + S.seg7(vx, B(ry) - 12, 12, d.lots, { cells: 1, level: 'norm' }) + 6;
    S.legend(cx, B(ry), 'LOTS COMING ACROSS', { level: 'dim' });
    ry += ROW;
    S.dm(vx, B(ry) - 10.5, d.prisoners ? 'PRISONERS TO BRING ABOARD' : '', { pitch: P, cells: 25, level: 'norm' });
    // the two keys under the glass, each printed with its key and action
    const ky = y + gh + 5, kw = (CW - 6) / 2;
    S.key(x, ky, kw, 24, 'K  BOARDING');
    S.key(x + kw + 6, ky, kw, 24, 'U  CAST OFF');
  }
  // X3: a red bar with the state cut out of it; each way out on its own row, its key on the bezel beside
  // it with an LED that blinks amber while the first press holds (guide 7.7).
  function x3(S, d) {
    const x = 390, y = 38, w = 500, gw = 444, h = 34 + d.rows.length * 30 + 4;
    K.plate(S, x, y, w, h, { pad: 5 });
    S.module(x, y, gw, h, { radius: 2.5 });
    S.rectLit(x + 4, y + 4, gw - 8, 28, { color: 'red', level: 'norm', r: 1.5 });
    S.knockout(x + gw / 2, y + 26, d.title, { size: 22, align: 'center' });
    d.rows.forEach((r, i) => {
      const ry = y + 36 + i * 30, b = ry + 20;
      S.dm(x + 10, b - 14, r.what, { pitch: 2, cells: 12, level: 'norm' });
      const cx = x + 10 + S.dmWidth(12, { pitch: 2 }) + 14;
      const tx = x + gw - 8 - S.legendWidth('TWICE');
      K.fits(cx + S.dm(cx, b - 10.5, r.cost || '', { pitch: P, cells: 21, level: 'dim' }) + 8, tx, 'X3 row');
      S.legend(tx, b, 'TWICE', { level: 'dim' });
      S.key(x + gw + 12, ry + 2, 40, 24, r.key, { led: 'amber', ledOn: !!r.armed });
    });
  }

  // ------------------------------------------------------------------ D1 captain vitals (LCD: the captain's kit)
  function d1(S, y, d) {
    const x = LX, w = CW, h = 86;
    K.plate(S, x, y, w, h, { pad: 5, radius: 7 });
    S.lcdPanel(x + 2, y + 2, w - 4, h - 4, { backlight: d.alert });
    const rows = [['YOU', d.you], ['SUIT', d.suit], ['HERE', d.here]];
    rows.forEach(([k, v], i) => {
      const b = y + 22 + i * 22;
      S.lcdText(x + 12, b, k, { size: 13 });
      S.lcdText(x + 64, b, v, { size: 13 });
    });
    if (d.air) { S.lcdText(x + 170, y + 44, 'AIR', { size: 13 }); S.lcdSeg7(x + 198, y + 30, 16, d.air, { slant: 6 }); }
  }

  // ================================================================== 11.1 Helm, cruise
  SCREENS['cx-helm-cruise'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 5 });
    await K.hull(S, 'corvette', 660, 404, 0.115, -6);
    const a = -6 * R, sx = 638, sy = 404;
    K.velMark(S, 'pro', sx + Math.cos(a) * 110, sy + Math.sin(a) * 110, { level: 'norm' });
    K.velMark(S, 'retro', sx - Math.cos(a) * 110, sy - Math.sin(a) * 110, { level: 'dim' });
    // the Nav off screen: an edge marker with its name (its range is H2's)
    K.edge(S, 1252, 318, 'r', ['SHACKLETON ICE'], { level: 'norm' });
    S.projectNow();

    topRack(S, { seat: 'HELM', day: '0', time: '00:33', warp: '1' });
    bottomRack(S);
    const p5y = p5(S, { l1: 'DELIVER 40 FOOD', l2: '→ SHACKLETON ICE', left: '5.2', unit: 'D LEFT' });
    // version 10: the Ap is left out (it comes 46 minutes after the Pe, past burn 1), and the Nav orbits
    // the Moon, so its rows name the body and give the plan's arrival; the plan is one leg, one burn
    h2(S, p5y + 12, [
      ['ORBIT', [['t', 'EARTH', 7], ['l', 'ALT'], ['n', '400', 3], ['l', 'KM']]],
      [null, [['l', 'PE'], ['n', '397', 3], ['l', 'KM'], ['gap', 10], ['l', 'T-'], ['n', '8:40', 4]]],
      ['NAV', [['t', 'SHACKLETON ICE', 16]]],
      [null, [['n', '384,000', 6], ['l', 'KM'], ['gap', 6], ['t', 'AT THE MOON', 11]]],
      [null, [['l', 'ARRIVAL'], ['l', 'T-'], ['n', '4.2', 3], ['l', 'D']]],
      ['NEXT', [['t', 'BURN 1/1', 8], ['gap', 6], ['l', 'T-'], ['n', '12:05', 4]]],
      [null, [['n', '3,084', 4], ['l', 'M/S'], ['gap', 6], ['t', 'HELM FLIES IT', 13]]],
      ['PLAN', [['l', 'ΔV'], ['n', '3,877', 4], ['gap', 6], ['l', 'LEAVES'], ['n', '1,729', 4], ['l', 'M/S'], ['gap', 6], ['l', 'SHORT', 0, { color: 'amber', lit: false, level: 'norm' }]]],
    ]);
    h4Lights(S, 16, 744, [['HULL'], ['POWER'], ['LIFE SUP']]);
    cue(S, 640, 648, {});
    h1(S, { frames: [['ORBIT', 'norm'], ['NAV', false], ['LOCK', false], ['PORT', false]], auto: true, speed: '7,672', assist: true, dv: '5,606', prop: 0.72 });
    // the ship has just left Highport: it and the Hauler Kestrel, 3 km off, hold the scope open, and
    // version 12's cruise range is the smallest step that holds every contact inside 50 km, so 5 km, with
    // them 60% out (Highport astern, the hauler on the port quarter)
    h5Scope(S, { heading: -6, range: '5 KM', contacts: [{ x: -0.597, y: -0.063, kind: 'station' }, { x: -0.475, y: -0.398, kind: 'neutral' }] });
    S.finish();
  };

  // ================================================================== 11.4 Helm, combat (a gunship)
  SCREENS['cx-helm-combat'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 31 });
    await K.hull(S, 'raider', 880, 290, 0.09, 160);
    await K.hull(S, 'gunship', 640, 404, 0.1, -14);
    // world: the lock (zinc brackets) on a hostile (red corners), labelled with its name (its range is H6's)
    K.brackets(S, 880, 290, 30, 'tri', { pitch: 2, len: 5, color: 'red', level: 'norm' });
    K.brackets(S, 880, 290, 40, 'square', { pitch: 2, len: 6, level: 'full', ticks: true });
    K.label(S, 846, 236, ['RAIDER'], { pitch: 2, level: 'norm' });
    // the lead reticle: 12 m/s along the line of sight for a 2.2 s shot is 26 m of lead, 2 units at this zoom, so
    // it sits on the target inside the lock mark, the smallest mark there (W5)
    K.reticle(S, 878, 291, { level: 'norm', onTarget: true });
    // the nose gun's arc, and the missiles with their predicted tracks to the hull. The raider is 3.4 km off and
    // threw them 11 s ago: under a harpoon's 30 m/s² closing burn they are 1.6 km out, about halfway, 4 s from impact,
    // swinging in below the line of sight from the launchers on the raider's flank
    const n = -14 * R, nx = 640 + Math.cos(n) * 90, ny = 404 + Math.sin(n) * 90;
    K.arc(S, nx, ny, 20, 150, -14 - 22, -14 + 22, { level: 'dim' });
    K.missile(S, 761, 374); K.missile(S, 769, 381);
    K.track(S, [[761, 374], [728, 378]], { step: 6 }); K.track(S, [[769, 381], [732, 384]], { step: 6 });
    K.cross(S, 722, 380);
    // in the LOCK frame the gunship closes along the line of sight at 12 m/s (H1, H6), so prograde points at the
    // raider and retrograde away from it
    const a = Math.atan2(290 - 404, 880 - 640);
    K.velMark(S, 'pro', 640 + Math.cos(a) * 112, 404 + Math.sin(a) * 112, { level: 'norm', tag: 'LOCK' });
    K.velMark(S, 'retro', 640 - Math.cos(a) * 112, 404 - Math.sin(a) * 112, { level: 'dim', tag: 'LOCK', tagLeft: true });
    S.projectNow();

    topRack(S, { seat: 'HELM', battle: true, day: '0', time: '00:41', warp: '1' });
    bottomRack(S);
    p2(S, [{ sev: 'warning', code: 'BREACH', detail: 'ENGINES', crew: 1 }]);
    p3(S, { count: '2', t: '4', decoy: 'READY', pd: true });
    p4(S, [['GUNNERY', '3 HITS ON RAIDER']]);
    // compact: no event is due within 30 minutes, so the Nav and its distance
    h2(S, 194, [['NAV', [['t', 'SHACKLETON ICE', 16], ['n', '384,000', 6], ['l', 'KM']]]]);
    const h7y = h6(S, 194, { name: 'RAIDER', state: 'HOSTILE', firing: true, range: '3.4', unit: 'KM', closing: 'closing', rate: '12', shot: '2.2', hit: '62', word: 'FAIR' });
    h7(S, h7y + 12, { guns: 'READY', who: 'YOU', msl: '7', reload: null, shields: 'RAISED', trim: 'BOW' });
    // H4 rose by itself: the breach lit the hull light. The plan is the gunship's own deck.
    h4Face(S, LX, 556, { plan: { plan: K.GUNSHIP, states: { ENGINES: 'breach' }, crew: [[6, 4, true], [19, 3, false, true], [18, 5, true], [13, 4]], marks: [[8, 5, 'x']] }, planH: 88,
      rows: [
        { label: 'HULL', frac: 0.81, value: '81', unit: '%', sev: 'warning' },
        { label: 'SHIELDS', frac: 0.77, value: '77', unit: '%' },
        { label: 'POWER', frac: 0.92, value: '92', unit: '%', out: '3.0' },
        { label: 'LIFE SUP', frac: 1, value: '100', unit: '%' },
      ] });
    h4Lights(S, 16, 744, [['HULL', 'warning'], ['SHIELDS'], ['POWER'], ['LIFE SUP']]);
    cue(S, 640, 648, { warn: 'BREACH' });
    h1(S, { frames: [['ORBIT', false], ['NAV', false], ['LOCK', 'norm'], ['PORT', false]], auto: true, name: 'RAIDER', speed: '12', assist: true, dv: '6,831', prop: 0.72, thrust: { main: true } });
    // the 5 km step holds the raider (3.4 km) and the missiles (1.6 km) with room to spare
    h5Scope(S, { heading: -14, range: '5 KM', contacts: [{ x: 0.613, y: -0.292, kind: 'hostile', lock: true }, { x: 0.31, y: -0.077, kind: 'missile' }, { x: 0.331, y: -0.058, kind: 'missile' }, { x: -0.5, y: 0.45, kind: 'neutral' }] });
    S.finish();
  };

  // ================================================================== 11.7 Deck, emergency
  SCREENS['cx-deck-emergency'] = async (canvas) => {
    const S = K.scene(canvas);
    // the whole corvette at the default deck zoom, unpowered: the capture is the ship with its reactor down
    await K.world(S, 'bg/deck-dark.jpg', { filter: 'brightness(1.55) contrast(1.05)' });
    const b = S.base;
    const glow = (x, y, r, c) => { b.save(); b.globalCompositeOperation = 'lighter'; const g = b.createRadialGradient(x, y, 0, x, y, r); g.addColorStop(0, c); g.addColorStop(1, 'rgba(0,0,0,0)'); b.fillStyle = g; b.fillRect(x - r, y - r, 2 * r, 2 * r); b.restore(); };
    // the fire, forward by the reactor (flames as the game draws them would be here), and its smoke aft of it
    glow(900, 444, 70, 'rgba(255,110,25,0.5)');
    for (const [fx, fy, r] of [[880, 452, 16], [902, 436, 20], [926, 448, 14], [912, 462, 12], [868, 434, 10]]) { glow(fx, fy, r * 1.6, 'rgba(255,150,50,0.55)'); glow(fx, fy, r * 0.6, 'rgba(255,225,140,0.75)'); }
    b.save(); b.filter = 'blur(14px)'; b.fillStyle = 'rgba(140,140,140,0.24)';
    for (const [sx, sy, r] of [[862, 520, 34], [904, 528, 38], [946, 516, 28], [884, 548, 22]]) { b.beginPath(); b.arc(sx, sy, r, 0, Math.PI * 2); b.fill(); }
    b.restore();
    // D3: a tag on each hazard in view (the whole ship is in view, so no pointers)
    K.deckTag(S, 478, 424, 'VACUUM', 'warning');
    K.deckTag(S, 572, 520, 'ADA', 'warning');
    K.deckTag(S, 900, 392, 'FIRE', 'warning');
    K.deckTag(S, 900, 540, 'SMOKE', 'caution');
    S.projectNow();

    topRack(S, { seat: 'DECK', authority: 'HELM: NOBODY', battle: true, day: '0', time: '01:12', warp: '1' });
    // version 11's order: what needs the captain, then what the crew hold back from (Ada was hit in the
    // HOLD, so its vacuum waits for the fight to end), then what crew are on (Ada's bleed-out is under the
    // two minutes that send them in anyway)
    p2(S, [
      { sev: 'warning', code: 'EMERG O2', you: true },
      { sev: 'warning', code: 'NO POWER', detail: 'DAMAGED', you: true },
      { sev: 'warning', code: 'VACUUM', detail: 'HOLD', held: true },
      { sev: 'warning', code: 'CREW CRIT', detail: 'ADA', crew: 1, timer: '1:40' },
      { sev: 'warning', code: 'FIRE', detail: 'BRIDGE', crew: 1 },
    ], '+1 MORE: SMOKE');
    x3(S, { title: 'SHIP DISABLED: NO POWER', rows: [
      { what: 'ABANDON SHIP', key: 'P' },
      { what: 'CALL A TOW', key: 'B', cost: '3,400 CR · 3 STANDING' },
    ] });
    // While X3 shows, everything but X3, P2, P3, H4, D1 and D3 dims: here, the strip.
    S.dimRegion(0, 0, 1280, 30, 0.45);
    h4Face(S, LX, 618, { rows: [
      { label: 'HULL', frac: 0.81, value: '81', unit: '%' },
      { label: 'POWER', frac: 0, value: '0', unit: '%', out: '0', rating: '3.0', sev: 'warning' },
      { label: 'LIFE SUP', text: 'EMERGENCY', value: '42', unit: 'KG', sev: 'warning' },
    ] });
    d1(S, 702, { you: 'LIGHT WOUND', suit: 'ON', air: '9:40', here: 'THIN AIR', alert: true });
    cue(S, 640, 750, { warn: 'VACUUM' }, { plate: true });
    S.finish();
  };

  // ================================================================== 11.13 Boarding, latched
  SCREENS['cx-boarding'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 44 });
    // latched: the raider alongside, port to port; the ship can't fly
    await K.hull(S, 'raider', 628, 328, 0.1, 180, { filter: 'brightness(0.8)' });
    await K.hull(S, 'corvette', 660, 404, 0.115, 0);
    // the raider is still the Lock, beaten: its corners at L1, the lock brackets, its name
    K.brackets(S, 628, 328, 30, 'tri', { pitch: 2, len: 5, level: 'dim' });
    K.brackets(S, 628, 328, 40, 'square', { pitch: 2, len: 6, level: 'full', ticks: true });
    K.label(S, 628 - 36, 266, ['RAIDER'], { pitch: 2, level: 'norm' });
    S.projectNow();

    topRack(S, { seat: 'HELM', day: '0', time: '01:30', warp: '1' });
    bottomRack(S, { tower: false });
    p4(S, [['BOARDING', 'THE RAIDER IS TAKEN']]);
    p5(S, { l1: 'BOUNTY: RAIDER · TAKEN', l2: '→ TURN IN AT HIGHPORT' });
    x2(S, 194, { name: 'RAIDER', latched: true, state: 'TAKEN: CREW SURRENDERED', party: '1', partyState: 'COMING HOME', marked: '3', off: '1', lots: '2', prisoners: true });
    h4Lights(S, 16, 744, [['HULL'], ['POWER'], ['LIFE SUP']]);
    cue(S, 640, 705, {});
    S.finish();
  };

  // ================================================================== the full-screen modes (section 10)
  // Every mode has the same frame: a title row with its tabs, the content, and a foot row with the feed's
  // newest line and the keys that close it; P1, the top rack, stays above. FILAMENT houses each by what it
  // is (guide section 18): the map is a plot with its panes, the station and boarding modes are terminals.
  // The frame is one plate: the title row a strip of glass across its top, each tab a key in the bezel over
  // the strip (its cap the tab's number, its label in the strip under it, inverse while its tab shows), and
  // the foot a strip across its bottom with the close keys printed at its right.
  const SC = SCR, KH = SC.KEY_H;
  const T = (S, x, y, str, o = {}) => S.dm(x, y, str, { pitch: P, level: o.level ?? 'norm', ghost: o.ghost ?? false, cells: o.cells, align: o.align, color: o.color, inverse: o.inverse });
  const rule = (S, x, y, w) => S.rectLit(x, y, w, 0.8, { ghostOnly: true, k: 2.2 });
  const capsW = (S, caps) => [].concat(caps).reduce((a, c) => a + Math.max(18, S.legendWidth(c, { size: 12, weight: 600, condense: 0.86, track: 0.1 }) + 7) + 3, 0) + 3;
  function modeFrame(S, x, y, w, h, o) {
    const top = o.tabs ? KH + 9 : 0;
    K.plate(S, x, y, w, h, { pad: 7, radius: 8 });
    const ty = y + top, th = 30;
    S.module(x, ty, w, th, { filaments: 0, glare: false, radius: 2 });
    let tx = x + 12;
    tx += S.seg14(tx, ty + 7, 16, o.title, { level: 'full' }) + 14;
    if (o.sub) tx += T(S, tx, ty + 10, o.sub, { level: 'dim' }) + 34;
    for (const tab of o.tabs || []) {
      const lw = SC.width(tab.t.length), kw = Math.max(44, lw), slot = kw + 26;
      S.key(tx + (slot - kw) / 2, y, kw, KH, tab.k, { size: 12 });
      T(S, tx + (slot - lw) / 2, ty + 10, tab.t, { level: tab.active ? 'full' : 'norm', inverse: tab.active ? [0, tab.t.length] : null });
      tx += slot;
    }
    K.fits(tx, x + w - 8, 'mode title row');
    const fy = y + h - 26;
    S.module(x, fy, w, 26, { filaments: 0, glare: false, radius: 2 });
    const fx = x + 12 + T(S, x + 12, fy + 8, o.feed[0] + ':', { level: 'dim' }) + 9;
    const fe = fx + T(S, fx, fy + 8, o.feed[1]);
    // the keys that close it, right-aligned: its own key, or Esc
    let kx = x + w - 12 - (capsW(S, o.close) + SC.width(2) + 14 + capsW(S, 'ESC') + SC.width(5));
    K.fits(fe + 20, kx, 'mode foot row');
    kx = SC.capWord(S, kx, fy + 4, [o.close], 'OR');
    SC.capWord(S, kx, fy + 4, ['ESC'], 'CLOSE');
    return { y: ty + th + 6, bottom: fy - 6 };
  }
  // a panel of glass in the frame, its name a legend in its first row
  function pane(S, x, y, w, h, name) {
    S.module(x, y, w, h, { filaments: Math.max(1, Math.round(h / 140)) });
    if (name) S.legend(x + 10, y + 18, name, { size: 13, level: 'dim', ghost: false });
  }
  // a pane's section heading: the legend, then a ghost rule to the pane's edge
  function head(S, x, y, x1, t) {
    const lw = S.legendWidth(t, { size: 13 });
    S.legend(x, y + 9.5, t, { size: 13, level: 'dim', ghost: false });
    rule(S, x + lw + 8, y + 5, x1 - x - lw - 8);
  }
  // a printed key and its order, and what it is doing at the row's right end
  function order(S, x, y, x1, cap, word, state, o = {}) {
    SC.capWord(S, x, y, [].concat(cap), word, { level: o.level ?? 'norm' });
    if (state) T(S, x1 - SC.width(state.length), y + 4, state, { level: o.on ? 'full' : 'dim' });
  }

  // a system's footprint on a ship plan: a box of dots inset from its tiles, one every `step` units
  function planBox(S, pl, tx, ty, tw, th, lv, step = 4) {
    const bx = pl.X(tx) + 4, by = pl.Y(ty) + 4, bw = tw * pl.t - 8, bh = th * pl.t - 8, nx = Math.round(bw / step), ny = Math.round(bh / step);
    for (let i = 0; i <= nx; i++) { S.dot(bx + i * bw / nx, by, { size: 1, level: lv }); S.dot(bx + i * bw / nx, by + bh, { size: 1, level: lv }); }
    for (let i = 1; i < ny; i++) { S.dot(bx, by + i * bh / ny, { size: 1, level: lv }); S.dot(bx + bw, by + i * bh / ny, { size: 1, level: lv }); }
  }
  // corner brackets at L3 round a footprint: a part marked to strip, or the system in the list's focus
  function planMark(S, pl, tx, ty, tw, th) {
    const bx = pl.X(tx), by = pl.Y(ty), bw = tw * pl.t, bh = th * pl.t;
    for (const [cx, cy, dx, dy] of [[bx, by, 1, 1], [bx + bw, by, -1, 1], [bx, by + bh, 1, -1], [bx + bw, by + bh, -1, -1]]) {
      S.dotLine(cx + dx * 2, cy + dy * 2, cx + dx * 12, cy + dy * 2, { size: 1.6, step: 2.2, level: 'full' });
      S.dotLine(cx + dx * 2, cy + dy * 2, cx + dx * 2, cy + dy * 12, { size: 1.6, step: 2.2, level: 'full' });
    }
  }
  // a person on a plan: 5x5 dots with the corners off, about a quarter of a tile, and a tick each side
  // while at work; hollow at L1, someone who surrendered
  const body = (i, j) => Math.abs(i) < 2 || Math.abs(j) < 2;
  function person(S, cx, cy, o = {}) {
    for (let i = -2; i <= 2; i++) for (let j = -2; j <= 2; j++) {
      if (!body(i, j) || (o.hollow && Math.abs(i) < 2 && Math.abs(j) < 2 && !(Math.abs(i) === 1 && Math.abs(j) === 1))) continue;
      S.dot(cx + i * 2.4, cy + j * 2.4, { size: o.hollow ? 1.6 : 2, level: o.hollow ? 'dim' : 'full' });
    }
    if (o.working) for (const sx of [-1, 1]) S.dot(cx + sx * 10, cy, { size: 1.8, level: 'norm' });
  }

  // ------------------------------------------------------------------ M1 Map (11.9)
  // The plan to Shackleton Ice at the moment of 11.1: burn 1 planned, the capture at the Moon an estimate
  // until the crossing plans it. The view row's keys sit in the bezel over the plot; the timeline runs to
  // the contract's deadline. The map has no tabs, so the number keys keep the helm orders (1 and 6 here).
  SCREENS['cx-map'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 12, stars: 200 });
    topRack(S, { seat: 'HELM', day: '0', time: '00:33', warp: '1' });
    const f = modeFrame(S, 12, 40, 1256, 748, { title: 'EARTH SYSTEM', feed: ['LOG', 'AUTOSAVED'], close: 'M' });
    const top = f.y, bot = f.bottom;
    // the plot, the view keys over it and the timeline under it
    const px = 338, pw = 604, py = top + KH + 6, ph = bot - 90 - 6 - py;
    SC.orbitLeg(S, px, py, pw, ph, { clearTop: 22 });
    SC.keyStrip(S, px + 8, top, pw - 16, [{ t: 'CENTRE: EARTH' }, { t: 'FIT' }, { t: 'ALL TRAFFIC' }], { labelY: py + 6 });
    const ty = bot - 90;
    pane(S, px, ty, pw, 90, 'TIMELINE');
    const tn = 'CLICK CENTRES THE MAP ON IT · » WARPS';
    T(S, px + pw - 10 - SC.width([...tn].length), ty + 9, tn, { level: 'dim' });
    SC.timeline(S, px + 10, ty + 28, pw - 20, [
      { t: 0.2, label: ['BURN 1', 'T-12:05 »'] }, { t: 79.2, label: ['MOON SOI', 'T-3.3 D »'] },
      { t: 98.4, label: ['CAPTURE', 'T-4.1 D »'], estimate: true }, { t: 100.8, label: ['ARRIVAL', 'T-4.2 D »'] },
      { t: 124.8, label: ['DEADLINE', 'T-5.2 D »'], deadline: true },
    ], { t0: 6 });

    // the Plan pane, left
    let x = 12, w = 320, x0 = x + 10, x1 = x + w - 10, y = top;
    pane(S, x, y, w, bot - top, 'PLAN');
    y += 30;
    S.legend(x0, y + 9.5, 'ORBIT NOW', { size: 13, level: 'dim', ghost: false }); T(S, x0 + 96, y, 'EARTH');
    T(S, x0, y + 18, 'PE 397 KM   T-8:40');
    T(S, x0, y + 34, 'PERIOD 1 H 32 M', { level: 'dim' });
    y += 58; head(S, x0, y, x1, 'BURNS'); T(S, x1 - SC.width(6), y, '1 OF 1', { level: 'dim' });
    y += 20;
    const bg = SC.grid(S, x0, y + 16, 33, 2);
    [['#', 1], ['WHEN', 3], ['ΔV', 18, 'r'], ['PRO', 25, 'r'], ['RAD', 31, 'r']].forEach(([h, c, a]) =>
      S.legend(bg.cx(a ? c + 1 : c) - (a ? 1.5 : 0), y + 9.5, h, { size: 13, level: 'dim', align: a ? 'right' : 'left', ghost: false }));
    bg.row(0, SC.cols([['1', 2], ['T-12:05', 8], ['3,084', 7, 'r'], ['3,084', 7, 'r'], ['0', 6, 'r']]), { focus: true, cursor: true });
    y += 40;
    // the leg still to come: its capture, an estimate until the crossing plans it
    T(S, x0, y, 'THEN AT THE MOON: CAPTURE,', { level: 'dim' });
    T(S, x0, y + 14, 'ABOUT 793 M/S AT T-4.1 D,', { level: 'dim' });
    T(S, x0, y + 28, 'PLANNED AT THE CROSSING', { level: 'dim' });
    y += 52; head(S, x0, y, x1, 'SELECTED');
    y += 20;
    T(S, x0, y, 'BURN 1', { level: 'full' }); T(S, x0 + 62, y, '→ AP 378,000 KM');
    T(S, x0 + 62, y + 16, 'MOON SOI T-3.3 D');
    y += 40; head(S, x0, y, x1, 'PLAN');
    y += 22;
    let fx = SC.figure(S, x0, y, 18, '3,877', null, { label: 'ΔV', cells: 4 });
    K.fits(SC.figure(S, fx + 8, y + 2, 16, '5,606', 'M/S', { label: 'OF', cells: 4 }), x1, 'M1 plan total');
    y += 28;
    S.bar(x0, y, x1 - x0, 6, 40, 3877 / 5606, { level: 'norm', gap: 1.2 });
    y += 16;
    T(S, x0, y, 'LEAVES 1,729 M/S', { level: 'dim' });
    T(S, x0, y + 16, 'ARRIVAL T-4.2 D');
    // the orders, at the pane's foot: the helm order running, the autopilot off
    y = bot - 10 - 5 * 26;
    head(S, x0, y - 22, x1, 'ORDERS');
    order(S, x0, y, x1, '1', 'HELM FLIES BURNS', 'ON', { on: true });
    order(S, x0, y + 26, x1, '6', 'AUTOPILOT', 'OFF');
    order(S, x0, y + 52, x1, '/', 'WARP TO NEXT BURN');
    order(S, x0, y + 78, x1, 'DEL', 'DELETE BURN');
    order(S, x0, y + 104, x1, '', 'CLEAR ALL');

    // the Target and traffic pane, right
    x = 948; x0 = x + 10; x1 = x + w - 10; y = top;
    pane(S, x, y, w, bot - top, 'TARGET AND TRAFFIC');
    y += 30;
    S.legend(x0, y + 12, 'NAV', { size: 13, level: 'dim', ghost: false });
    S.dm(x0 + 40, y, 'SHACKLETON ICE', { pitch: 2, level: 'full', ghost: false });
    T(S, x0, y + 24, 'ICE MINE · 384,000 KM');
    T(S, x0, y + 38, 'AT THE MOON · ARRIVAL T-4.2 D', { level: 'dim' });
    order(S, x0, y + 58, x1, 'Q', 'NEXT TARGET');
    y += 94; head(S, x0, y, x1, 'TRANSFER');
    y += 22;
    let kx = SC.capWord(S, x0, y, [';'], 'PLAN');
    kx = SC.capWord(S, kx, y, ['ENTER'], 'ACCEPT', { level: 'dim' });
    K.fits(SC.capWord(S, kx, y, ['BKSP'], 'DISCARD', { level: 'dim' }) - 14, x1, 'M1 transfer keys');
    T(S, x0, y + 26, 'ACCEPT, DISCARD: NOTHING PROPOSED', { level: 'dim' });
    y += 58; head(S, x0, y, x1, 'CONTACTS');
    T(S, x1 - SC.width(16), y, 'SORT ◀ DISTANCE ▶', { level: 'dim' });
    y += 22;
    const cg = SC.grid(S, x0, y, 33, 6);
    [['HIGHPORT', '3.0'], ['HAULER KESTREL', '3.1'], ['DEEPWELL DEPOT', '9,800'], ['ANVIL YARDS', '18,400']].forEach(([n, d], i) =>
      cg.text(0, i * 1.5, SC.cols([[n, 18], [d, 10, 'r'], ['', 1], ['KM', 2]])));
    y += 6 * 13.5 + 10; head(S, x0, y, x1, 'LAYERS');
    y += 24;
    // each layer a key with its LED, lit while the layer shows; its name in the glass over it
    [['STATIONS', true], ['HAULERS', true], ['PIRATES', true], ['DERELICTS', true], ['OBJECTIVES', true], ['TENSION', false]].forEach(([t, on], i) => {
      const sw = (x1 - x0) / 3, sx = x0 + (i % 3) * sw, sy = y + Math.floor(i / 3) * 52;
      T(S, sx + (sw - SC.width(t.length)) / 2, sy, t, { level: on ? 'norm' : 'dim' });
      S.key(sx + 8, sy + 16, sw - 16, 22, '', { size: 12 });
      S.led(sx + sw - 20, sy + 27, 'green', on);
    });
    S.finish();
  };

  // ------------------------------------------------------------------ M3 Station (11.10)
  // A panel 1,120 wide over the docked view, as tall as its tab: Highport's Market is three rows (version
  // 11: nothing salvaged on day 0, nothing on order), the delivery this market supplies under the table,
  // the rail on the right. The trade keys act on the focused row; each tab's key is its number, and T, Y
  // and J switch to their tab too (T closes the panel from the Market).
  SCREENS['cx-station'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 4, stars: 260 });
    await K.hull(S, 'station_hub', 640, 556, 0.1, 0);
    await K.hull(S, 'corvette', 619, 692, 0.115, 0);
    K.label(S, 640 - 54, 418, ['HIGHPORT'], { pitch: 2, level: 'norm' });
    S.projectNow();
    topRack(S, { seat: 'HELM', day: '0', time: '00:15', warp: '1' });
    const X = 80, W = 1120, H = 316;
    const f = modeFrame(S, X, 40, W, H, {
      title: 'HIGHPORT', sub: 'TERRAN PATROL AUTHORITY',
      tabs: [{ t: 'MARKET', k: '1', active: true }, { t: 'YARD', k: '2' }, { t: 'MISSIONS', k: '3' }, { t: 'HIRE', k: '4' }],
      feed: ['STATION', 'CLEARANCE GRANTED'], close: 'T',
    });
    // the Market, its trade keys under it
    const mw = 792, gy = f.y, gh = f.bottom - KH - 9 - gy;
    pane(S, X, gy, mw, gh);
    SC.keyStrip(S, X + 8, gy + gh + 5, mw - 16, [{ t: 'BUY 1' }, { t: 'BUY 10' }, { t: 'SELL 1' }, { t: 'SELL ALL' }], { slots: 6, labelY: gy + gh - 7 * P - 6 });
    const x0 = X + 12;
    T(S, x0, gy + 8, 'PRICES IN CR, AMOUNTS IN M3', { level: 'dim' });
    const lg = SC.grid(S, x0, gy + 42, 85, 4.5);
    [['COMMODITY', 1], ['STOCK', 38, 'r'], ['BUY', 48, 'r'], ['SELL', 58, 'r'], ['HELD', 68, 'r'], ['ON ORDER', 73]].forEach(([h, c, a]) =>
      S.legend(lg.cx(a ? c + 1 : c) - (a ? 1.5 : 0), gy + 35, h, { size: 13, level: 'dim', align: a ? 'right' : 'left', ghost: false }));
    const line = (n, st, b, sl, h, oo) => SC.cols([[n, 28], [st, 10, 'r'], [b, 10, 'r'], [sl, 10, 'r'], [h, 10, 'r'], ['', 4], [oo, 12]]);
    lg.row(0, line('WATER', '420', '14', '12', '0', '-'));
    lg.row(1.5, line('FOOD', '2,600', '36', '32', '12', '-'), { focus: true, cursor: true });
    lg.row(3, line('MACHINERY', '20', '680', '620', '0', '-'));
    const cy = gy + 42 + 4.5 * 13.5 + 8;
    head(S, x0, cy, X + mw - 12, 'FOR YOUR CONTRACTS');
    T(S, x0, cy + 20, 'DELIVER 40 FOOD → SHACKLETON ICE · 12 OF 40 ABOARD · 5.2 D LEFT');
    // the rail: what bounds every choice on every tab
    const rx = X + mw + 8, rw = X + W - rx, r0 = rx + 12, r1 = X + W - 12;
    pane(S, rx, gy, rw, gh + KH + 9);
    let ry = gy + 8;
    const R = (label, val, o = {}) => { S.legend(r0, ry + 9.5, label, { size: 13, level: 'dim', ghost: false }); if (val) T(S, r0 + 100, ry, val, o); ry += o.step ?? 20; };
    S.legend(r0, ry + 14, 'CREDITS', { size: 13, level: 'dim', ghost: false });
    K.fits(SC.figure(S, r0 + 100, ry, 16, '5,000', 'CR', { cells: 5 }), r1, 'M3 credits'); ry += 26;
    R('STANDING', 'TRUSTED  +42');
    const bx = r0 + 100 + SC.width(14) + 10, bar = (f) => S.bar(bx, ry + 2, r1 - bx, 6, 12, f, { level: 'norm', gap: 1 });
    R('HOLD', '12 OF 40 M3', { step: 0 }); bar(12 / 40); ry += 20;
    R('TANKS', 'PROPELLANT 72%', { step: 0 }); bar(0.72); ry += 16;
    T(S, r0 + 100, ry, 'OXYGEN    100%'); bar(1); ry += 26;
    R('INFIRMARY', 'TREATING 1', { step: 16 });
    T(S, r0 + 100, ry, 'BILL 150 CR', { level: 'dim' });
    K.fits(r0 + 100 + SC.width(14), r1, 'M3 rail');
    S.finish();
  };

  // ------------------------------------------------------------------ M4 Boarding (10.4), at 11.13
  // The raider taken and latched alongside: today's BoardingWindow in a terminal, with a plan of the other
  // ship. The party (Cole, the ship's marine) stands down once the craft is taken and is on the way home;
  // Ada strips the reactor and Bea carries the chaingun home; the three who surrendered wait aboard. The
  // part list is the game's (every system but the hold and the port, then what is already off). The
  // party's orders keep their keys though none is open once the ship is taken, so the keys never move.
  const RAIDER = { // the light raider (chassis.raider): 15 x 5 tiles of hull, one space inside, port up
    w: 17, h: 5,
    hull: [[2, 0], [17, 0], [17, 5], [2, 5]],
    rooms: [{ id: 'DECK', x: 3, y: 1, w: 13, h: 3 }],
    walls: [],
    engines: [[0, 1, 2, 1], [0, 3, 2, 1]],
  };
  SCREENS['cx-board'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 44 });
    topRack(S, { seat: 'HELM', day: '0', time: '01:30', warp: '1' });
    const f = modeFrame(S, 12, 40, 1256, 748, { title: 'RAIDER', sub: 'LATCHED · TAKEN: CREW SURRENDERED', feed: ['BOARDING', 'THE RAIDER IS TAKEN'], close: 'K' });
    const top = f.y, bot = f.bottom, lw = 840, rx = 12 + lw + 8, rw = 1268 - rx;
    // the plan of the raider, port (and the latch) up, the bow to the right: its hull and engines, every
    // system's place faint, the parts marked to strip in brackets, the gun's place empty
    const ph = 242;
    pane(S, 12, top, lw, ph, 'PLAN');
    const pl = K.shipPlan(S, 12 + 44, top + 26, lw - 88, ph - 48, { plan: RAIDER, wallLevel: 'dim' });
    const at = (tx, ty) => [pl.X(tx), pl.Y(ty)], t = pl.t;
    const box = (tx, ty, tw, th2, lv, step = 4) => planBox(S, pl, tx, ty, tw, th2, lv, step);
    // the systems as chassis.raider mounts them (drawn port up): x, y, w, h
    for (const [tx, ty, tw, th2] of [[11, 1, 1, 1], [14, 1, 1, 1], [3, 2, 1, 1], [5, 2, 1, 1], [7, 2, 1, 1], [11, 2, 1, 1], [13, 1, 1, 2], [6, 3, 1, 1]]) box(tx, ty, tw, th2, 'dim', 5);
    const mark = (tx, ty, tw, th2) => planMark(S, pl, tx, ty, tw, th2);
    mark(0, 1, 2, 1); mark(0, 3, 2, 1); mark(8, 1, 2, 2);
    box(15, 2, 1, 1, 'norm', 3);                                                    // the gun, already off
    const [ex0, ey0] = at(0, 4);
    T(S, ex0, ey0 + 10, 'ENGINES', { level: 'norm' }); T(S, ex0, ey0 + 24, 'MARKED', { level: 'dim' });
    const [rx0, ry0] = at(8, 1);
    T(S, rx0 + 8, ry0 + 8, 'REACTOR'); T(S, rx0 + 8, ry0 + 22, '40%', { level: 'dim' });
    const [gx0, gy0] = at(17, 2);
    T(S, gx0 + 8, gy0 + 6, 'GUN'); T(S, gx0 + 8, gy0 + 20, 'OFF', { level: 'dim' });
    // the latch on the port side: lit in the hull, and where it leads
    const [lx0, ly0] = at(5, 0);
    S.rectLit(lx0 + 3, ly0 - 1.5, t - 6, 3, { level: 'full' });
    T(S, lx0 - 30, ly0 - 18, 'LATCH · THE CORVETTE LIES OVER THE PORT SIDE', { level: 'dim' });
    // our crew aboard, a block of dots each (working: its side ticks), and the three who surrendered, hollow
    const who = (tx, ty, name, working, dx, dy) => { const [cx, cy] = at(tx, ty); person(S, cx, cy, { working }); T(S, cx + dx, cy + dy, name); };
    who(5.5, 1.5, 'COLE', false, -12, 15);
    who(6.6, 1.5, 'BEA', true, 14, -16);
    who(9, 2.55, 'ADA', true, -13, -19);
    for (const tx of [11.5, 12.5, 13.5]) { const [cx, cy] = at(tx, 3.5); person(S, cx, cy, { hollow: true }); }
    const [px0, py0] = at(11, 5);
    T(S, px0, py0 + 10, '3 SURRENDERED', { level: 'dim' });

    // the party, and its orders (none open: the ship is taken)
    const pgh = ph - KH - 9;
    pane(S, rx, top, rw, pgh, 'PARTY');
    const q0 = rx + 12, q1 = rx + rw - 12;
    let y = top + 30;
    let cx = q0 + S.seg7(q0, y, 16, '1', { cells: 1, level: 'norm' }) + 10;
    T(S, cx, y + 3, 'COMING HOME', { level: 'full' });
    y += 28;
    T(S, q0, y, SC.cols([['COLE', 6], ['MARINE', 9], ['AT THE LATCH', 18]]));
    y += 22;
    T(S, q0, y, 'TAKEN: THE PARTY STANDS DOWN', { level: 'dim' });
    T(S, q0, y + 14, 'AND COMES HOME BY ITSELF', { level: 'dim' });
    SC.keyStrip(S, rx + 8, top + pgh + 5, rw - 16, [{ t: 'BOARD', dim: true }, { t: 'STORM', dim: true }, { t: 'RECALL', dim: true }], { labelY: top + pgh - 7 * P - 6 });

    // the parts to strip, in the game's order; the focused one is marked, so its key leaves it
    const sy = top + ph + 6, sh = bot - KH - 9 - sy;
    pane(S, 12, sy, lw, sh, 'SALVAGE');
    T(S, 12 + lw - 12 - SC.width(25), sy + 9, '3 MARKED · 1 OFF · NO FEE', { level: 'dim' });
    const sg = SC.grid(S, 22, sy + 50, 60, 16);
    [['SYSTEM', 1], ['SELLS FOR', 30, 'r'], ['STATUS', 34]].forEach(([h, c, a]) =>
      S.legend(sg.cx(a ? c + 1 : c) - (a ? 1.5 : 0), sy + 43, h, { size: 13, level: 'dim', align: a ? 'right' : 'left', ghost: false }));
    const parts = [['ENGINE', '1,250', 'MARKED'], ['HELM', '400', ''], ['ESCAPE POD', '0', ''], ['TANK', '200', ''], ['TANK', '200', ''], ['LIFE SUPPORT', '750', ''],
      ['REACTOR', '2,000', 'STRIPPING 40%'], ['WEAPONS', '400', ''], ['ENGINE', '1,250', 'MARKED'], ['TANK', '200', ''], ['CHAINGUN MK1', '900', 'CARRYING IT HOME']];
    parts.forEach(([n, v, st], i) => {
      const focus = n === 'REACTOR';
      sg.row(i * 1.45, SC.cols([[n, 18], [v, 11, 'r'], ['', 4], [st, 26]]), { focus, cursor: focus, cells: 60, level: st ? 'norm' : 'dim' });
    });
    SC.keyStrip(S, 20, sy + sh + 5, lw - 16, [{ t: 'LEAVE IT', k: 'ENTER' }], { slots: 5, labelY: sy + sh - 7 * P - 6 });

    // the cargo and the prisoners
    pane(S, rx, sy, rw, sh, 'CARGO');
    y = sy + 30;
    S.legend(q0 + 9, y + 9.5, 'LOT', { size: 13, level: 'dim', ghost: false });
    S.legend(q0 + 9 * 17 + 8, y + 9.5, 'UNITS', { size: 13, level: 'dim', ghost: false, align: 'right' });
    y += 18;
    T(S, q0, y, SC.cols([['', 1], ['FOOD', 12], ['6', 4, 'r'], ['', 2], ['6 COMING OVER', 16]]));
    T(S, q0, y + 18, SC.cols([['', 1], ['MACHINERY', 12], ['4', 4, 'r'], ['', 2], ['4 COMING OVER', 16]]));
    T(S, q0, y + 40, 'TAKE ALL: BOTH LOTS ARE ON THEIR WAY', { level: 'dim' });
    y += 72; head(S, q0, y, q1, 'PRISONERS');
    y += 22;
    T(S, q0, y, '3 SURRENDERED, ABOARD THE RAIDER');
    T(S, q0, y + 16, 'BROUGHT ABOARD, THEY ARE TURNED IN', { level: 'dim' });
    T(S, q0, y + 30, 'AT A STATION FOR STANDING', { level: 'dim' });
    SC.keyStrip(S, rx + 8, sy + sh + 5, rw - 16, [{ t: 'TAKE ALL', dim: true }, { t: 'BRING ABOARD' }], { slots: 2, labelY: sy + sh - 7 * P - 6 });
    S.finish();
  };

  // ------------------------------------------------------------------ M2 Ship, the Systems tab (11.11)
  // Battle stations aboard the patrol corvette, every system where chassis.corvette mounts it (port up, the
  // bow to the right). One hit wrecked the hull tile on the hold's port quarter: a wrecked hull tile is a
  // breach (Tile.isHullBreach), and the RCS quad bolted to it stops until the tile is framed up again
  // (RcsThruster.fieldRepairable), so the breach, the stopped thruster and Cole's light wound in the HOLD
  // are one hit. Every RCS block stands on a hull tile that borders the interior, so a stopped block always
  // comes with a breach. Damage has the FWD PORT autocannon at 20%, under the 30% where a system stops, and
  // Ada repairs it; the conduit under the FWD STBD autocannon is cut in its tile, which strands it. Bea
  // flies and Cole mans the weapons console. The list's focus is the gun Ada is on, so the plan brackets it.
  // The plan's marks: a system a box of dots at L1, one out of action a solid box at L3; the conduits a fine
  // chain at L1, a cut one broken; the breach an X on its hull tile, the hull open there; a crew member a
  // block of dots with a tick each side at work. What is wrong is called out above and below the hull,
  // its severity glyph leading as P2's do.
  const SHIP_MOUNTS = [ // drawn tiles [x, y, w, h]: a chassis tile (x, y) is drawn at row 5 - y
    [7, 0, 3, 1],                                     // docking port, in the port hull
    [3, 2, 2, 1], [3, 3, 2, 1],                       // nitrogen and oxygen tanks
    [5, 1, 3, 2], [5, 3, 3, 2],                       // the hold's two cargo bays
    [8, 3, 1, 2],                                     // propellant tank, on the walkway
    [9, 1, 2, 1], [9, 2, 2, 1], [9, 4, 2, 1],         // helm, weapons console, life support
    [11, 1, 1, 1], [11, 4, 1, 1],                     // harpoon (AFT PORT), laser (AFT STBD)
    [12, 1, 2, 1], [15, 4, 2, 1],                     // the two small bays forward
    [12, 4, 1, 2],                                    // escape pod, its hatch in the starboard hull
    [15, 1, 2, 2], [16, 3, 1, 1],                     // reactor M, coilgun (NOSE)
  ];
  const SHIP_DOWN = [[14, 1, 1, 1], [14, 4, 1, 1]];  // the autocannons, FWD PORT (damaged) and FWD STBD (cut off)
  const SHIP_CONDUITS = [[[8, 4], [9, 4]], [[14, 4], [15, 4]], [[11, 2], [14, 2]], [[3, 1], [11, 1]], [[3, 1], [3, 4]],
    [[8, 4], [8, 5]], [[9, 1], [9, 4]], [[11, 1], [11, 4]], [[14, 1], [14, 4]], [[16, 2], [16, 3]]]; // chassis tiles
  const SHIP_RCS = [[3, 0, 'quad', true], [16, 0, 'quad'], [3, 5, 'quad'], [16, 5, 'quad'], [10, 0], [10, 5], [17, 2], [17, 3]];
  SCREENS['cx-ship'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 21 });
    topRack(S, { seat: 'DECK', authority: 'HELM: BEA · HOLDING HEADING', battle: true, chip: ['1', '2'], day: '0', time: '00:20', warp: '1' });
    const f = modeFrame(S, 12, 40, 1256, 748, {
      title: 'PATROL CORVETTE',
      tabs: [{ t: 'SYSTEMS', k: '1', active: true }, { t: 'CREW', k: '2' }, { t: 'CARGO', k: '3' }, { t: 'LOADOUT', k: '4' }],
      feed: ['CREW', 'COLE IS LIGHTLY WOUNDED AND WORKING SLOWER'], close: 'I',
    });
    const top = f.y, bot = f.bottom, lw = 750, rx = 12 + lw + 6, rw = 1268 - rx, mid = top + 380;

    // the plan
    pane(S, 12, top, lw, mid - top, 'PLAN');
    const pl = K.shipPlan(S, 30, top + 81, 714, 204, { states: { HOLD: 'open' }, gaps: [[3, 0, 4, 1]], wallLevel: 'dim' });
    const X = pl.X, Y = pl.Y, at = ([cx, cy]) => [X(cx + 0.5), Y(5.5 - cy)];
    const fine = { size: 1, step: 3, level: 'dim' }, cap = (x, y) => S.dotLine(x - 5, y, x + 5, y, { size: 1.6, step: 2.5, level: 'full' });
    for (const [a, b] of SHIP_CONDUITS) {
      const [x0, y0] = at(a), [x1, y1] = at(b);
      if (a[0] === 14 && a[1] === 1) { // cut in the gun's tile: the run stops short of it, a stub in it
        S.dotLine(x1, y1, x0, y0 - 30, fine); cap(x0, y0 - 30);
        S.dotLine(x0, y0 - 9, x0, y0, fine); cap(x0, y0 - 9);
      } else S.dotLine(x0, y0, x1, y1, fine);
    }
    for (const m of SHIP_MOUNTS) planBox(S, pl, ...m, 'dim', 4);
    for (const m of SHIP_DOWN) planBox(S, pl, ...m, 'full', 2);
    planMark(S, pl, 14, 1, 1, 1);
    // the RCS blocks on the hull: a quad a cross of dots, a linear block a bar; none on the wrecked tile
    for (const [tx, ty, quad, wrecked] of SHIP_RCS) {
      if (wrecked) continue;
      const cx = X(tx + 0.5), cy = Y(ty + 0.5), o = { size: 1.6, level: 'dim' };
      for (const [dx, dy] of quad ? [[0, 0], [-1, 0], [1, 0], [0, -1], [0, 1]] : [[-1, 0], [0, 0], [1, 0]]) S.dot(cx + dx * 3, cy + dy * 3, o);
    }
    // the breach: an X on the wrecked tile, the hull open there
    const wx = X(3.5), wy = Y(0.5);
    for (let i = -4; i <= 4; i++) { S.dot(wx + i * 2.6, wy + i * 2.6, { size: 1.6, level: 'full' }); if (i) S.dot(wx + i * 2.6, wy - i * 2.6, { size: 1.6, level: 'full' }); }
    T(S, X(3) + 6, Y(1) + 3, 'HOLD', { level: 'dim' });
    T(S, X(12) - 6, Y(3) - 2, 'BRIDGE', { level: 'dim' });
    // the crew, each at work: Bea at the helm, Cole at the weapons console, Ada at the gun she repairs
    for (const [tx, ty, name, dx] of [[9.5, 1.5, 'BEA', 14], [9.5, 2.5, 'COLE', -50], [14.5, 2.5, 'ADA', -41]]) {
      person(S, X(tx), Y(ty), { working: true });
      T(S, X(tx) + dx, Y(ty) - 5, name);
    }
    // what is wrong, called out: its severity glyph, then the words; the leader to the mark
    const call = (x, y, sev, lines, from, below) => {
      S.dm(x, y, sev === 'warning' ? '♦' : '▲', { pitch: P, color: sev === 'warning' ? 'red' : 'amber', level: 'full', ghost: false });
      lines.forEach((ln, i) => T(S, x + 2 * CELL, y + i * 13.5, ln, { level: i ? 'dim' : 'norm' }));
      S.dotLine(from[0], from[1], x - 3, below ? y + 5 : y + (lines.length - 1) * 13.5 + 13, { size: 1, step: 2, level: 'dim' });
    };
    call(X(3.5) + 3, top + 24, 'warning', ['BREACH', 'RCS THRUSTER STOPPED', 'COLE LIGHTLY WOUNDED'], [X(3.5), Y(0) - 4]);
    call(X(14.5) + 8, top + 24, 'caution', ['AUTOCANNON (FWD PORT)', '20% · BEING REPAIRED'], [X(14.5), Y(1) - 4]);
    call(X(14.5) + 8, Y(6) + 20, 'caution', ['AUTOCANNON (FWD STBD)', 'CONDUIT CUT · NO POWER'], [X(14.5), Y(5) + 4], true);
    // the plan's key, printed along the pane's foot
    let kx = 22;
    const ky = mid - 22, key = (draw, word) => { draw(kx, ky); kx += 26; kx += T(S, kx, ky - 5, word, { level: 'dim' }) + 22; };
    const sq = (lv, step) => (x, y) => planBox(S, { X: () => x - 4, Y: () => y - 13, t: 26 }, 0, 0, 1, 1, lv, step);
    key(sq('dim', 4), 'SYSTEM');
    key(sq('full', 2), 'OUT OF ACTION');
    key((x, y) => { for (let i = -2; i <= 2; i++) { S.dot(x + 9 + i * 2.6, y + i * 2.6, { size: 1.6, level: 'full' }); if (i) S.dot(x + 9 + i * 2.6, y - i * 2.6, { size: 1.6, level: 'full' }); } }, 'BREACH');
    key((x, y) => { S.dotLine(x, y, x + 8, y, fine); cap(x + 10, y); cap(x + 16, y); }, 'CONDUIT, CUT');
    key((x, y) => person(S, x + 9, y, { working: true }), 'CREW AT WORK');

    // the systems, grouped by type, a dash where the game has no such value. Every system has its switch, and
    // the keys act on the focused row: SWITCH, its LED green while the system is on (on the reactor's row the
    // first press sets it blinking amber and the second switches); OPEN lists a group's members under its row,
    // each with its own switch, so on a single system's row it is dim. RCS blocks are not systems: no switch.
    const sh = mid - top - KH - 9, s0 = rx + 12;
    pane(S, rx, top, rw, sh, 'SYSTEMS');
    SC.pager(S, rx + rw - 12 - 80, top + 8, 1, 2);
    const sg = SC.grid(S, s0, top + 46, 53, 11 * 1.5);
    [['SYSTEM', 3], ['HEALTH', 28, 'r'], ['POWER', 31], ['STATE', 37]].forEach(([h, c, a]) =>
      S.legend(sg.cx(a ? c + 1 : c) - (a ? 1.5 : 0), top + 39, h, { size: 13, level: 'dim', align: a ? 'right' : 'left', ghost: false }));
    [['REACTOR M', '100%', '-', 'ON'], ['ENGINES ×2', '100%', 'OK', 'ON'], ['RCS ×8', '-', '-', '7 ON · 1 STOPPED'],
      ['COILGUN (NOSE)', '100%', 'OK', 'ON'], ['AUTOCANNON (FWD PORT)', '20%', 'OK', 'DAMAGED', 'amber', true],
      ['AUTOCANNON (FWD STBD)', '100%', 'NONE', 'CUT OFF', 'amber'], ['HARPOON (AFT PORT)', '100%', 'OK', 'ON'],
      ['LASER (AFT STBD)', '100%', 'OK', 'ON'], ['LIFE SUPPORT', '100%', 'OK', 'ON'], ['HELM', '100%', 'OK', 'ON'],
      ['WEAPONS CONSOLE', '100%', 'OK', 'ON'],
    ].forEach(([n, h, pw, st, sev, focus], i) => {
      sg.pips(0, i * 1.5, sev);
      sg.row(i * 1.5, SC.cols([[n, 21], ['', 1], [h, 4, 'r'], ['', 2], [pw, 4], ['', 2], [st, 16]]), { c: 2, focus, cursor: focus });
    });
    T(S, sg.cx(3), top + 46 + 11 * 20.25 + 12, 'PG 2: TANKS, CARGO BAYS, ESCAPE POD, DOCKING PORT', { level: 'dim' });
    SC.keyStrip(S, rx + 8, top + sh + 5, rw - 16, [{ t: 'SWITCH', k: 'ENTER', led: 'green', ledOn: true }, { t: 'OPEN', k: '→', dim: true }], { slots: 4, labelY: top + sh - 7 * P - 6 });

    // damage control: the crew's jobs as the game posts them, and who has each or why it waits
    const dy0 = mid + 6, dh = bot - dy0;
    pane(S, 12, dy0, lw, dh, 'DAMAGE CONTROL');
    T(S, 12 + lw - 12 - SC.width(15), dy0 + 9, '3 JOBS · 2 WAIT', { level: 'dim' });
    const dg = SC.grid(S, 22, dy0 + 46, 74, 3 * 1.5);
    [['TASK', 3], ['WHERE', 35], ['WHO', 47]].forEach(([h, c]) => S.legend(dg.cx(c), dy0 + 39, h, { size: 13, level: 'dim', ghost: false }));
    [['REPAIR AUTOCANNON (FWD PORT)', 'BRIDGE', 'ADA', 'amber'], ['SEAL BREACH', 'HOLD', '‖ WAITS: UNDER FIRE', 'red'],
      ['REPAIR RCS THRUSTER', 'OUTSIDE', '‖ WAITS: BATTLE STATIONS']].forEach(([task, where, who, sev], i) => {
      dg.pips(0, i * 1.5, sev);
      dg.text(3, i * 1.5, SC.cols([[task, 30], ['', 2], [where, 10], ['', 2], [who, 24]]));
    });
    let ny = dy0 + 46 + 3 * 20.25 + 16;
    for (const [term, why] of [['ONE HIT:', "THE BREACH, THE STOPPED THRUSTER AND COLE'S LIGHT WOUND"],
      ['UNDER FIRE:', 'WITHIN 2 TILES OF WHERE CREW WERE HIT THIS FIGHT'],
      ['BATTLE STATIONS:', 'WORK OUTSIDE WAITS FOR SOMEONE SENT OUT'],
      ['CUT CONDUIT:', 'ONLY A YARD REPAIRS ONE, SO IT SHOWS UNDER POWER']]) {
      const tw = T(S, dg.cx(3), ny, term);
      K.fits(dg.cx(3) + tw + CELL + T(S, dg.cx(3) + tw + CELL, ny, why, { level: 'dim' }), 12 + lw - 12, 'M2 note');
      ny += 18;
    }

    // power: each circuit's reactors, output against rating, the store; what no reactor reaches
    pane(S, rx, dy0, rw, dh, 'POWER');
    const p0 = rx + 12, pv = p0 + 104;
    let py = dy0 + 36;
    const lab = (t2, y2) => S.legend(p0, y2, t2, { size: 13, level: 'dim', ghost: false });
    lab('CIRCUIT 1', py + 14);
    let px = pv + T(S, pv, py + 4, 'REACTOR M') + 14;
    px = SC.figure(S, px, py, 14, '3.0', null, { cells: 2 });
    K.fits(SC.figure(S, px + 2, py, 14, '3.0', 'MW', { label: 'OF', cells: 2 }), rx + rw - 12, 'M2 circuit');
    py += 30; lab('STORE', py + 12);
    S.bar(pv, py + 4, 200, 7, 20, 0.8, { level: 'norm', gap: 1.2 });
    SC.figure(S, pv + 212, py, 12, '80', '%', { cells: 3 });
    py += 32; lab('CUT OFF', py + 10);
    K.pip(S, pv + 3, py + 4.5, 'amber', true); K.pip(S, pv + 12, py + 4.5, 'red', false);
    T(S, pv + 2 * CELL + 4, py, 'AUTOCANNON (FWD STBD)');
    T(S, pv + 2 * CELL + 4, py + 15, 'NO REACTOR REACHES IT', { level: 'dim' });
    py += 42; lab('CONDUITS', py + 10);
    T(S, pv, py, '1 CUT, IN THE BRIDGE');
    T(S, pv, py + 15, 'THE CREW CAN ONLY RESET A SHORT;', { level: 'dim' });
    T(S, pv, py + 30, 'A YARD REPAIRS A CUT: 400 CR', { level: 'dim' });
    S.finish();
  };

  Object.assign(K, { cx: { topRack, bottomRack, cue, h4Lights, h4Face, h1, h5Key, h5Scope, p2, p3, p4, p5, h2, h6, h7, x2, x3, d1 } });
})();
