// FILAMENT x Context-driven design (functional-context.md, version 7): two racks frame the view (the
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
    if (r.out != null) { // power: the reactors' output against their rating
      cx += S.seg7(cx, b - 12, 12, r.out, { cells: 2, level: lv }) + 5;
      cx += S.legend(cx, b, 'OF', { level: 'dim' }) + 4;
      cx += S.seg7(cx, b - 12, 12, r.rating, { cells: 2, level: lv }) + 5;
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
      if (c.lock) for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) { S.dot(px + sx * 7, py + sy * 7, { size: 1.3, level: 'full' }); S.dot(px + sx * 5, py + sy * 7, { size: 1.3, level: 'full' }); S.dot(px + sx * 7, py + sy * 5, { size: 1.3, level: 'full' }); }
    }
    S.legend(cx, cy + r - 12, d.range, { align: 'center', level: 'dim' });
    S.key(cx - r - 8, cy + r - 16, 24, 24, '−', { size: 14 });
    S.key(cx + r - 16, cy + r - 16, 24, 24, '+', { size: 14 });
  }

  // ------------------------------------------------------------------ P2 caution and warning
  // Six rows of 24 in fixed fields, a cell apart where a full code meets its detail: severity (two filtered
  // columns), code 11 cells, detail 10, handling 3, timer 5. Handling: YOU inverse; a dot per crew member on it, from four a dot and the count; BACK and the
  // recovery time across handling and timer.
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
  // Labels are legends at L1, words dot matrix, numbers seven segments with their units as legends.
  function h2(S, y, face, d) {
    const x = LX, rows = face === 'compact' ? 1 : 9;
    K.slab(S, x, y, CW, rows * ROW + 6, { pad: 5 });
    const vx = x + 52;
    let ry = y + 3;
    const lab = t => S.legend(x + 6, B(ry), t, { level: 'dim' });
    // a run of parts along the row: ['l', text] legend, ['n', digits, cells] seven segments, ['t', text, cells] dot matrix
    const run = (parts, at = vx) => {
      let cx = at;
      for (const [k, v, n, o = {}] of parts) {
        if (k === 'l') cx += S.legend(cx, B(ry), v, { level: 'dim', ...o }) + 4;
        else if (k === 'n') cx += S.seg7(cx, B(ry) - 12, 12, v, { cells: n, level: 'norm', ...o }) + 6;
        else if (k === 't') cx += S.dm(cx, B(ry) - 10.5, v, { pitch: P, cells: n, level: 'norm', ...o }) + 6;
        else if (k === 'gap') cx += v;
      }
      K.fits(cx, x + CW - 2, 'H2 row ' + ry);
      return cx;
    };
    if (face === 'compact') {
      lab('NAV'); run([['t', d.nav, 16], ['n', d.navRange, 4], ['l', 'KM']]);
      return;
    }
    lab('ORBIT'); run([['t', d.body, 7], ['l', 'ALT'], ['n', d.alt, 3], ['l', 'KM']]); ry += ROW;
    run([['l', 'AP'], ['n', d.ap, 3], ['l', 'KM'], ['gap', 10], ['l', 'T-'], ['n', d.apT, 4]]); ry += ROW;
    run([['l', 'PE'], ['n', d.pe, 3], ['l', 'KM'], ['gap', 10], ['l', 'T-'], ['n', d.peH, 1], ['l', 'H'], ['n', d.peM, 2], ['l', 'M']]); ry += ROW;
    lab('NAV'); run([['t', d.nav, 16]]); ry += ROW;
    run([['n', d.navRange, 4], ['l', 'KM'], ['gap', 6], ['l', 'CLOSING', 0, { lit: !d.opening, level: 'norm' }], ['l', 'OPENING', 0, { lit: !!d.opening, level: 'norm' }], ['n', d.rate, 3], ['l', 'M/S']]); ry += ROW;
    run([['l', 'CLOSEST'], ['n', d.cpa, 3], ['l', 'KM'], ['gap', 6], ['l', 'T-'], ['n', d.cpaH, 1], ['l', 'H'], ['n', d.cpaM, 2], ['l', 'M']]); ry += ROW;
    lab('NEXT'); run([['t', d.next, 8], ['gap', 6], ['l', 'T-'], ['n', d.nextT, 4]]); ry += ROW;
    run([['n', d.nextDv, 3], ['l', 'M/S'], ['gap', 6], ['t', d.by, 13]]); ry += ROW;
    lab('PLAN'); run([['l', 'ΔV'], ['n', d.need, 4], ['gap', 6], ['l', 'LEAVES'], ['n', d.leaves, 4], ['l', 'M/S'], ['gap', 6], ['l', 'SHORT', 0, { color: 'amber', lit: !!d.short, level: 'norm' }]]);
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
    let cx = vx + S.seg7(vx, B(ry) - 12, 12, d.party, { cells: 1, level: 'norm' }) + 6;
    cx += S.legend(cx, B(ry), 'ABOARD', { level: 'dim' }) + 8;
    S.dm(cx, B(ry) - 10.5, d.partyState, { pitch: P, cells: 8, level: 'norm' });
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
    const p5y = p5(S, { l1: 'DELIVER 40 FOOD', l2: '→ SHACKLETON ICE', left: '1.4', unit: 'D LEFT' });
    h2(S, p5y + 12, 'full', { body: 'EARTH', alt: '400', ap: '403', apT: '24:10', pe: '397', peH: '1', peM: '10',
      nav: 'SHACKLETON ICE', navRange: '1240', opening: true, rate: '220', cpa: '3.2', cpaH: '2', cpaM: '05',
      next: 'BURN 1/2', nextT: '12:05', nextDv: '420', by: 'HELM FLIES IT', need: '860', leaves: '380' });
    h4Lights(S, 16, 744, [['HULL'], ['POWER'], ['LIFE SUP']]);
    cue(S, 640, 648, {});
    h1(S, { frames: [['ORBIT', 'norm'], ['NAV', false], ['LOCK', false], ['PORT', false]], auto: true, speed: '7672', assist: true, dv: '1240', prop: 0.72 });
    h5Key(S, 3);
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
    K.reticle(S, 858, 306, { level: 'norm' });
    // the nose gun's arc, and the missiles with their predicted tracks to the hull. The raider is 3.4 km off and
    // threw them 11 s ago: under a harpoon's 30 m/s² closing burn they are 1.6 km out, about halfway, 4 s from impact
    const n = -14 * R, nx = 640 + Math.cos(n) * 90, ny = 404 + Math.sin(n) * 90;
    K.arc(S, nx, ny, 20, 150, -14 - 22, -14 + 22, { level: 'dim' });
    K.missile(S, 753, 350); K.missile(S, 765, 357);
    K.track(S, [[753, 350], [726, 375]], { step: 6 }); K.track(S, [[765, 357], [731, 384]], { step: 6 });
    K.cross(S, 722, 380);
    const a = -20 * R;
    K.velMark(S, 'pro', 640 + Math.cos(a) * 112, 404 + Math.sin(a) * 112 + 34, { level: 'norm', tag: 'LOCK' });
    K.velMark(S, 'retro', 640 - Math.cos(a) * 112, 404 - Math.sin(a) * 112 - 34, { level: 'dim', tag: 'LOCK', tagLeft: true });
    S.projectNow();

    topRack(S, { seat: 'HELM', battle: true, day: '0', time: '00:41', warp: '1' });
    bottomRack(S);
    p2(S, [{ sev: 'warning', code: 'BREACH', detail: 'ENGINES', crew: 1 }]);
    p3(S, { count: '2', t: '4', decoy: 'READY', pd: true });
    p4(S, [['GUNNERY', '3 HITS ON RAIDER']]);
    h2(S, 194, 'compact', { nav: 'SHACKLETON ICE', navRange: '1240' });
    const h7y = h6(S, 194, { name: 'RAIDER', state: 'HOSTILE', firing: true, range: '3.4', unit: 'KM', closing: 'closing', rate: '85', shot: '2.1', hit: '62', word: 'FAIR' });
    h7(S, h7y + 12, { guns: 'READY', who: 'YOU', msl: '7', reload: null, shields: 'RAISED', trim: 'BOW' });
    // H4 rose by itself: the breach lit the hull light. The plan is the gunship's own deck.
    h4Face(S, LX, 556, { plan: { plan: K.GUNSHIP, states: { ENGINES: 'breach' }, crew: [[6, 4, true], [19, 3, false, true], [18, 5, true], [13, 4]], marks: [[8, 5, 'x']] }, planH: 88,
      rows: [
        { label: 'HULL', frac: 0.81, value: '81', unit: '%', sev: 'warning' },
        { label: 'SHIELDS', frac: 0.77, value: '77', unit: '%' },
        { label: 'POWER', frac: 0.92, value: '92', unit: '%', out: '3.0', rating: '3.0' },
        { label: 'LIFE SUP', frac: 1, value: '100', unit: '%' },
      ] });
    h4Lights(S, 16, 744, [['HULL', 'warning'], ['SHIELDS'], ['POWER'], ['LIFE SUP']]);
    cue(S, 640, 648, { warn: 'BREACH' });
    h1(S, { frames: [['ORBIT', false], ['NAV', false], ['LOCK', 'norm'], ['PORT', false]], auto: true, name: 'RAIDER', speed: '38', assist: true, dv: '1240', prop: 0.72, thrust: { main: true } });
    // the 5 km step holds the raider (3.4 km) and the missiles (1.6 km) with room to spare
    h5Scope(S, { heading: -14, range: '5 KM', contacts: [{ x: 0.613, y: -0.292, kind: 'hostile', lock: true }, { x: 0.289, y: -0.137, kind: 'missile' }, { x: 0.31, y: -0.12, kind: 'missile' }, { x: -0.5, y: 0.45, kind: 'neutral' }] });
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
    p2(S, [
      { sev: 'warning', code: 'EMERG O2', you: true },
      { sev: 'warning', code: 'VACUUM', detail: 'HOLD', you: true },
      { sev: 'warning', code: 'NO POWER', detail: 'DAMAGED', you: true },
      { sev: 'warning', code: 'CREW CRIT', detail: 'ADA', crew: 1, timer: '3:40' },
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
    x2(S, 194, { name: 'RAIDER', latched: true, state: 'TAKEN: CREW SURRENDERED', party: '2', partyState: 'HOLDING', marked: '3', off: '1', lots: '2', prisoners: true });
    h4Lights(S, 16, 744, [['HULL'], ['POWER'], ['LIFE SUP']]);
    cue(S, 640, 705, {});
    S.finish();
  };

  Object.assign(K, { cx: { topRack, bottomRack, cue, h4Lights, h4Face, h1, h5Key, h5Scope, p2, p3, p4, p5, h2, h6, h7, x2, x3, d1 } });
})();
