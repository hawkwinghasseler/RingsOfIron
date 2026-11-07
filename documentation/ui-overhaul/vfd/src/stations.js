// FILAMENT x Ship Stations (functional-stations.md, round 14): the seated band as one console rack,
// the top column as plates that power up in fixed slots, the deck left to the deck.
// Round 2: every size at or above the guide's minimums (legends 13, segments 12, dots 1.5, the ticker
// at body pitch 2, keys 24 tall); what no longer fits was cut by priority, not shrunk.
(() => {
  const K = KIT;
  const R = Math.PI / 180;

  // ------------------------------------------------------------------ geometry (6.2, band flush)
  const BAND = { x: 96, y: 616, w: 1088, mfdW: 288, clW: 480, h: 160, boardY: 780 };
  const LMFD = BAND.x, CL = BAND.x + BAND.mfdW + 16, RMFD = CL + BAND.clW + 16;
  const GLASS_B = 740;            // an MFD's glass ends here; its bottom row holds the soft-key labels
  const RAIL_Y = 722;             // a folded MFD keeps only that row: the rail
  const KEY_Y = 746, KEY_H = 24;  // the keys under it
  const TOP = { x: 280, w: 720, banner: 16, row1: 44, warp: 84, ticker: 108 }; // round 6: one annunciator row
  const dim = { level: 'dim' };

  // The console the seated band is built into. A folded MFD sinks into the rack and leaves its rail.
  function bandRack(S, leftOpen, rightOpen) {
    const top = BAND.y - 10, wing = RAIL_Y - 8, x0 = BAND.x - 10, x1 = BAND.x + BAND.w + 10;
    const lx = LMFD + BAND.mfdW + 8, rx = RMFD - 8;
    const pts = [[x0, 812]];
    pts.push([x0, leftOpen ? top : wing]);
    if (!leftOpen) pts.push([lx, wing], [lx, top]);
    if (!rightOpen) pts.push([rx, top], [rx, wing]);
    pts.push([x1, rightOpen ? top : wing], [x1, 812]);
    K.rackShape(S, pts, { radius: 7 });
    // the board's shelf: a step in the moulding under the instruments
    S.silkRule(BAND.x - 4, BAND.boardY - 3.5, BAND.x + BAND.w + 4, { color: '#08090a', width: 1.2 });
    S.silkRule(BAND.x - 4, BAND.boardY - 2.3, BAND.x + BAND.w + 4, { color: '#26282b', width: 0.6 });
  }

  // The soft-key row (9.1): three page keys, each carrying its page's full name in the glass's bottom row
  // over a blank rubber key (SENSORS, never SNS; the longest, CONTRACTS, takes nine cells), then one split
  // key whose halves are pin and fold, each half's glyph in the glass over it. A folded MFD shows only this
  // row, every label at L1; an open one lights its page inverse, and the pin glyph lights while pinned.
  const SLOT = 80;
  const PIN = ['.###.', '.###.', '.###.', '#####', '..#..', '..#..', '..#..'];
  const FOLD = ['.....', '#...#', '.#.#.', '..#..', '.....', '#####', '.....'];
  function keyRow(S, x, items, folded, o = {}) {
    items.forEach((it, i) => {
      const cx = x + SLOT * (i + 0.5), cells = it.t.length, w = cells * 9 - 1.5;
      const active = it.active && !folded;
      K.fits(cells, 9, 'soft-key label cells');
      S.dm(cx - w / 2, RAIL_Y + 3.75, it.t, { pitch: 1.5, level: active ? 'norm' : 'dim', inverse: active ? [0, cells] : null, ghost: false });
    });
    for (let i = 0; i < 3; i++) S.key(x + SLOT * i + 3, KEY_Y, SLOT - 6, KEY_H, '', {});
    // pin | fold: one 48-unit cap with a groove down the middle, each half a 24-unit target
    const sx = x + 3 * SLOT, sw = BAND.mfdW - 3 * SLOT;
    S.rectLit(sx - 0.4, RAIL_Y + 3, 0.8, 12, { ghostOnly: true, k: 1.6 });
    S.sprite(sx + sw / 4, RAIL_Y + 9, PIN, { center: true, pitch: 1.5, level: o.pinned ? 'norm' : 'dim' });
    S.sprite(sx + sw * 3 / 4, RAIL_Y + 9, FOLD, { center: true, pitch: 1.5, level: 'dim' });
    S.key(sx, KEY_Y, sw, KEY_H, '', {});
    const b = S.base; b.save(); b.fillStyle = '#050506'; b.fillRect(sx + sw / 2 - 0.7, KEY_Y + 1.5, 1.4, KEY_H - 3); b.restore();
  }
  function rail(S, x, items) {
    S.module(x, RAIL_Y, BAND.mfdW, GLASS_B - RAIL_Y, { filaments: 0, glare: false, radius: 2 });
    keyRow(S, x, items, true);
  }
  // A blacked-out instrument (5.1, guide 7.11): drawn unpowered, so no tube lights and every ghost shows, and
  // behind the smoked glass the backup LCD, its backlight on from the emergency bus, reading NO POWER.
  function noPower(S, draw, x, y, w, h, fy = y + h / 2) {
    S.dark = true; draw(); S.dark = false;
    const lw = 86, lh = 17;
    S.lcdPanel(x + w / 2 - lw / 2, fy - lh / 2, lw, lh, { backlight: true });
    S.lcdText(x + w / 2, fy + 4.5, 'NO POWER', { size: 12, align: 'center' });
  }

  // An open MFD: glass with a title row (page, its setting, the owning station), the page, and the rail.
  function mfd(S, x, title, keys, drawPage, o = {}) {
    const y = BAND.y, w = BAND.mfdW, gh = GLASS_B - y;
    S.module(x, y, w, gh, { filaments: 4 });
    const tw = S.dm(x + 8, y + 5, title, { pitch: 1.5, level: 'norm', ghost: false });
    if (o.setting) S.legend(x + 8 + tw + 10, y + 15, o.setting, dim);
    if (o.owner) S.dm(x + w - 8 - S.dmWidth(o.owner.length, { pitch: 1.5 }), y + 5, o.owner, { pitch: 1.5, level: 'dim', ghost: false });
    S.rectLit(x + 6, y + 20, w - 12, 0.7, { ghostOnly: true, k: 2 });
    drawPage(S, x, y + 24, w, 78);
    S.rectLit(x + 6, RAIL_Y - 1.5, w - 12, 0.7, { ghostOnly: true, k: 2 });
    keyRow(S, x, keys, false);
  }

  // ------------------------------------------------------------------ station board (8.5)
  // tiles: [{n, t, who, you, alert: 'caution'|'warning'}]. In Yellow and Red the player's tile lights its
  // zinc lamp cap; in Green (o.green) the whole board is dim, "you" included. A crew member's name sits at
  // L1 (crewed is normal); a vacant station is ghost; an alert lights the tile's own red or amber pip, in
  // any condition, and raises the name to L2. Command has no occupancy (it is the captain's slate, always
  // with the player): its window holds only the printed LATCHED badge, lit while latched (8.5, 14.2).
  function board(S, tiles, clock, o = {}) {
    const y = BAND.boardY, h = 18;
    tiles.forEach((tl, i) => {
      const x = BAND.x + i * 150;
      S.lampKey(x + 1, y, 60, h, tl.n + ' ' + tl.t, null, 'zinc', !!tl.you && !o.green);
      S.module(x + 66, y, 74, h, { filaments: 0, glare: false, radius: 1.5 });
      if (tl.t === 'CMD') S.legend(x + 66 + 37, y + 13.5, 'LATCHED', { size: 12, align: 'center', lit: !!tl.latched, level: 'norm' });
      else S.dm(x + 67.75, y + 3.75, tl.who || '', { pitch: 1.5, cells: 8, level: tl.alert ? 'norm' : 'dim' });
      K.pip(S, x + 145, y + 5, 'red', tl.alert === 'warning');
      K.pip(S, x + 145, y + 13, 'amber', tl.alert === 'caution');
    });
    // right end: the chronometer
    const rx = BAND.x + 904;
    S.module(rx, y, 184, h, { filaments: 0, glare: false, radius: 1.5 });
    let cx = rx + 8;
    cx += S.legend(cx, y + 13.5, 'DAY', { size: 12, level: 'dim' }) + 5;
    cx += S.seg7(cx, y + 3, 12, clock[0], { cells: 2, level: 'dim' }) + 12;
    cx += S.legend(cx, y + 13.5, 'TIME', { size: 12, level: 'dim' }) + 5;
    cx += S.seg7(cx, y + 3, 12, clock[1], { cells: 6, level: 'dim' });
    K.fits(cx, rx + 180, 'board chronometer');
  }

  // ------------------------------------------------------------------ top column (6.2, 7.1, 7.2)
  // One annunciator row: master lamps at the ends, six tile windows between. Every tile is dual-filtered
  // (its frame and dots behind amber, its block behind red), so the row has no fixed split: warnings fill
  // inward from the WARN lamp, cautions inward from the CAUT lamp. Past six, the tile where they meet counts
  // the rest (+n / MORE) in the role of the worst it hides, so what it hides is the oldest cautions (7.1).
  // warns, cauts: [[tag, value]], most severe or newest first.
  function annRow(S, y, warns, cauts, o = {}) {
    const x = TOP.x, lampW = 56, tileW = 94, gap = (TOP.w - 2 * lampW - 6 * tileW) / 7;
    K.plate(S, x, y, TOP.w, 36, { pad: 4 });
    if (o.lamps !== false) {
      S.lampKey(x, y, lampW, 36, 'MASTER', 'WARN', 'red', !!o.warnOn);
      S.lampKey(x + TOP.w - lampW, y, lampW, 36, 'MASTER', 'CAUT', 'amber', !!o.cautOn);
    }
    const slots = new Array(6).fill(null), W = warns.length, C = cauts.length;
    if (W + C <= 6) {
      warns.forEach((it, i) => { slots[i] = [it, 'red']; });
      cauts.forEach((it, i) => { slots[5 - i] = [it, 'amber']; });
    } else if (W >= 6) {
      warns.slice(0, 5).forEach((it, i) => { slots[i] = [it, 'red']; });
      slots[5] = [['+' + (W - 5 + C), 'MORE'], 'red'];
    } else {
      warns.forEach((it, i) => { slots[i] = [it, 'red']; });
      cauts.slice(0, 5 - W).forEach((it, i) => { slots[5 - i] = [it, 'amber']; });
      slots[W] = [['+' + (C - (5 - W)), 'MORE'], 'amber'];
    }
    for (let i = 0; i < 6; i++) {
      const tx = x + lampW + gap + i * (tileW + gap);
      S.module(tx, y, tileW, 36, { filaments: 1, radius: 2 });
      const lx = tx + (tileW - 70.5) / 2, l1 = y + 5.5, l2 = y + 20;
      if (!slots[i]) {
        S.dm(lx, l1, '', { pitch: 1.5, cells: 8, color: 'amber' }); S.dm(lx, l2, '', { pitch: 1.5, cells: 8, color: 'amber' });
        continue;
      }
      const [[tag, val], role] = slots[i];
      if (role === 'red') {
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
    let cx = x;
    const cell = (word, col, on) => {
      const w = S.legendWidth(word) + 14;
      S.module(cx, y, w, h, { filaments: 0, glare: false, radius: 2 });
      if (on) { S.rectLit(cx + 2, y + 2, w - 4, h - 4, { color: col, level: 'norm', r: 1.2 }); S.knockout(cx + w / 2, y + 16.7, word, { align: 'center' }); }
      else S.legend(cx + w / 2, y + 16.7, word, { align: 'center', color: col, lit: false });
      cx += w + 4;
    };
    cell('YELLOW', 'amber', o.condition === 'YELLOW');
    cell('RED', 'red', o.condition === 'RED');
    const bw = S.legendWidth('BATTLE STATIONS') + 14;
    S.module(cx, y, bw, h, { filaments: 0, glare: false, radius: 2 });
    S.legend(cx + bw / 2, y + 16.7, 'BATTLE STATIONS', { align: 'center', color: 'red', lit: !!o.battle, level: 'norm' });
    cx += bw + 4;
    const sw = S.legendWidth('DOCKED') + S.legendWidth('LATCHED') + 20;
    S.module(cx, y, sw, h, { filaments: 0, glare: false, radius: 2 });
    K.states(S, cx + 6, y + 16.7, [['DOCKED', o.state === 'DOCKED' && 'norm'], ['LATCHED', o.state === 'LATCHED' && 'norm']], { size: 13, gap: 8 });
    cx += sw + 4;
    const ew = x + TOP.w - cx, cells = Math.floor((ew - 10) / 9);
    S.module(cx, y, ew, h, { filaments: 1, glare: false, radius: 2 });
    // The emergency text: the state and the options, keys first, at most 44 characters as the spec counts
    // them. A key it names is one inverse cell, the display's own key cap, so the field stays on its cell
    // grid: 'DISABLED {P} {P} ABANDON {B} {B} TOW' takes 28 of the 39 cells.
    const em = [];
    for (const m of String(o.emerg || '').matchAll(/\{(.)\}|([^{]+)/g)) {
      if (m[1]) em.push([m[1], true]); else for (const ch of m[2]) em.push([ch, false]);
    }
    S.dm(cx + 6, y + 6.75, '', { pitch: 1.5, cells, color: 'zinc' });
    K.fits(em.length, cells, 'emergency text cells');
    for (let i = 0; i < em.length;) {
      let j = i; while (j < em.length && em[j][1] === em[i][1] && !em[i][1]) j++;
      if (em[i][1]) { S.dm(cx + 6 + i * 9, y + 6.75, em[i][0], { pitch: 1.5, inverse: [0, 1], level: 'norm', ghost: false }); i++; }
      else { S.dm(cx + 6 + i * 9, y + 6.75, em.slice(i, j).map(c => c[0]).join(''), { pitch: 1.5, level: 'norm', ghost: false }); i = j; }
    }
  }
  // The callout ticker: three fixed lines at body pitch 2, newest at the bottom. lines: [{sev, tag, text}]
  // (null = empty). Text is zinc; severity lights the line's fixed red or amber pip.
  // A line longer than the field marquees (guide 9.3); the mockup shows it at the start of its hold.
  function ticker(S, lines) {
    const x = TOP.x, y = TOP.ticker, tx = x + 68, cells = Math.floor((TOP.w - 68 - 2) / 12);
    K.plate(S, x, y, TOP.w, 60, { pad: 4 });
    S.module(x, y, TOP.w, 60, { filaments: 2, radius: 2 });
    lines.forEach((ln, i) => {
      const ly = y + 4 + i * 18;
      K.pip(S, x + 10, ly + 7, 'red', ln && ln.sev === 'warning');
      K.pip(S, x + 19, ly + 7, 'amber', ln && ln.sev === 'caution');
      const lv = ln ? (i === lines.length - 1 ? 'norm' : 'dim') : 'dim';
      if (ln) S.legend(x + 28, ly + 11.75, ln.tag, { level: lv, ghost: false });
      S.dm(tx, ly, ln ? ln.text : '', { pitch: 2, cells, level: lv });
    });
  }

  // ------------------------------------------------------------------ helm cluster (8.1, five fixed slots)
  // Velocity on the left, then reference, next event and trigger stacked on the right, delta-v along the
  // bottom. Closing speed is signed (the lexicon's CLS: negative is opening), which saves a pair of legends.
  function helmCluster(S, d) {
    const gx = CL, gy = BAND.y, L = 192, rx = gx + L + 10, lim = gx + 472;
    S.module(gx, gy, 480, 160, { filaments: 4 });
    const rule = (x, y, w, h) => S.rectLit(x, y, w, h, { ghostOnly: true, k: 2.2 });
    rule(gx + L, gy + 8, 0.8, 104); rule(rx - 2, gy + 47, lim - rx + 2, 0.8); rule(rx - 2, gy + 80, lim - rx + 2, 0.8); rule(gx + 8, gy + 118, 464, 0.8);

    // Velocity: frame, speed, rotation, control chip, assist, thrust lamps (always lit while seated)
    K.fits(K.states(S, gx + 10, gy + 19, d.frames, { size: 13, gap: 6 }) - 6, gx + L - 4, 'helm frames');
    const sw = S.seg7(gx + 12, gy + 27, 38, d.speed, { cells: 4, level: 'norm' });
    S.legend(gx + 20 + sw, gy + 45, 'KM/S', { lit: d.unit === 'KM/S', level: 'norm' });
    S.legend(gx + 20 + sw, gy + 63, 'M/S', { lit: d.unit === 'M/S', level: 'norm' });
    S.seg14(gx + 12, gy + 74, 14, d.control, { cells: 7, level: 'norm', color: d.controlColor });
    let x = gx + 112;
    x += S.legend(x, gy + 87.5, 'ROT', { lit: !!d.rot, level: 'dim' }) + 5;
    K.fits(x + S.seg7(x, gy + 76, 12, d.rot || '', { cells: 2, level: 'norm' }), gx + L - 4, 'helm rot');
    const ax = K.states(S, gx + 12, gy + 108, [['ASSIST', d.assist], ['THR', d.thr], ['RCS', d.rcs]], { size: 13, gap: 10 });
    K.fits(ax - 10, gx + L - 4, 'helm modes');
    if (d.assist) S.frameLit(gx + 8.5, gy + 96, S.legendWidth('ASSIST') + 7, 16, 0.8, { level: 'dim', gap: 0.8 });

    // Reference: name, range, closing, CPA (in that order of priority)
    const ref = d.ref || {}, on = !!d.ref;
    S.legend(rx, gy + 21, 'REF', { lit: on, level: 'dim' });
    S.dm(rx + 34, gy + 10.5, ref.name || '', { pitch: 1.5, cells: 12, level: 'norm' });
    x = rx + 34 + 12 * 9 + 6;
    x += S.seg7(x, gy + 7, 16, ref.range || '', { cells: 4, level: 'norm' }) + 5;
    x += S.legend(x, gy + 21, 'M', { lit: ref.unit === 'M', level: 'dim' }) + 6;
    K.fits(x + S.legend(x, gy + 21, 'KM', { lit: ref.unit === 'KM', level: 'dim' }), lim, 'helm ref 1');
    x = rx + 34;
    x += S.legend(x, gy + 41, 'CLS', { lit: !!ref.speed, level: 'dim' }) + 4;
    x += S.seg7(x, gy + 29, 12, ref.speed || '', { cells: 4, level: 'dim' }) + 6;
    x += S.legend(x, gy + 41, 'M/S', { lit: !!ref.speed, level: 'dim' }) + 12;
    x += S.legend(x, gy + 41, 'CPA', { lit: !!ref.cpa, level: 'dim' }) + 4;
    x += S.seg7(x, gy + 29, 12, ref.cpa || '', { cells: 3, level: 'dim' }) + 5;
    x += S.legend(x, gy + 41, 'M', { lit: !!ref.cpa, level: 'dim' }) + 6;
    x += S.legend(x, gy + 41, 'IN', { lit: !!ref.cpaT, level: 'dim' }) + 4;
    K.fits(x + S.seg7(x, gy + 29, 12, ref.cpaT || '', { cells: 3, level: 'dim' }), lim, 'helm ref 2');

    // Next event: what, when, how much
    const nx = d.next || {};
    S.legend(rx, gy + 68, 'NEXT', { lit: !!d.next, level: 'dim' });
    x = rx + 42;
    x += S.seg14(x, gy + 55, 14, nx.what || '', { cells: 6, level: 'norm' }) + 8;
    x += S.seg7(x, gy + 54, 16, nx.when || '', { cells: 4, level: 'norm' }) + 9;
    x += S.seg7(x, gy + 58, 12, nx.dv || '', { cells: 3, level: 'dim' }) + 6;
    x += S.legend(x, gy + 70, 'M/S', { lit: !!nx.dv, level: 'dim' }) + 6;
    K.fits(x + S.legend(x, gy + 70, nx.dir || 'PRO', { lit: !!nx.dir, level: 'dim' }), lim, 'helm next');

    // Trigger, on two lines: the guns' state and how they fire besides the trigger (AT WILL, HOLD,
    // TRIGGER); missiles left and ready; the decoy; shields lowered, only on a hull with an emitter.
    // Dark unless a target is locked or in Red.
    const tg = d.trig;
    x = rx;
    const tf = (yy, name, val, cells) => {
      x += S.legend(x, yy + 10.5, name, { lit: !!tg, level: 'dim' }) + 4;
      x += S.dm(x, yy, tg ? val : '', { pitch: 1.5, cells, level: 'norm' }) + 10;
    };
    tf(gy + 86, 'GUNS', tg && tg.guns, 3);
    K.fits(x + S.seg14(x, gy + 85, 12, tg ? tg.mode : '', { cells: 7, level: 'dim' }), lim, 'helm trigger 1');
    x = rx;
    tf(gy + 101, 'MSL', tg && tg.msl, 5); tf(gy + 101, 'DCY', tg && tg.decoy, 3);
    if (d.shields) S.legend(x, gy + 111.5, 'SHLD DN', { lit: !!(tg && tg.shieldsDown), color: 'amber', level: 'norm' });
    K.fits(x + (d.shields ? S.legendWidth('SHLD DN') : 0), lim, 'helm trigger 2');

    // Delta-v: left in the tanks, the home notch, the plan bracket
    const dv = d.dv;
    S.legend(gx + 10, gy + 144, 'ΔV', dim);
    x = gx + 34;
    x += S.seg7(x, gy + 126, 18, dv.left.toLocaleString('en-US'), { cells: 4, level: 'norm' }) + 7;
    x += S.legend(x, gy + 144, 'M/S', dim) + 10;
    const bx = x, bw = gx + 384 - bx, by = gy + 131;
    S.bar(bx, by, bw, 9, 50, dv.left / dv.full, { level: 'dim', gap: 1.1 });
    const hx = bx + bw * dv.home / dv.full;
    S.rectLit(hx - 0.7, by + 11, 1.4, 6, { level: 'norm' });
    S.dot(hx - 2.2, by + 13, { size: 1.1, level: 'norm' }); S.dot(hx + 2.2, by + 13, { size: 1.1, level: 'norm' });
    if (dv.plan) {
      const p0 = bx + bw * (dv.left - dv.plan) / dv.full, p1 = bx + bw * dv.left / dv.full;
      S.rectLit(p0, by - 4, p1 - p0, 0.9, { level: 'dim' }); S.rectLit(p0, by - 4, 0.9, 3, { level: 'dim' }); S.rectLit(p1 - 0.9, by - 4, 0.9, 3, { level: 'dim' });
    }
    x = gx + 394;
    const hw = S.legend(x, gy + 133, 'HOME', dim) + 5;
    K.fits(x + hw + S.seg7(x + hw, gy + 122, 12, dv.home.toLocaleString('en-US'), { cells: 4, level: 'dim' }), lim, 'helm home');
    S.legend(x, gy + 151, 'PLAN', { level: 'dim', lit: !!dv.plan });
    S.seg7(x + hw, gy + 140, 12, dv.plan ? dv.plan.toLocaleString('en-US') : '', { cells: 4, level: 'dim' });
  }

  // ------------------------------------------------------------------ tactical cluster (8.3)
  // At the weapons console every gun but point defense is in hand, so the seat is the mode and it is said
  // once. Rows: the target; a chip per gun in hand (state, its own hit chance, the crew member seated at
  // it) and one for point defense; the launcher, the decoy and the shields (blank glass on a hull with no emitter).
  function chip(S, x, y, w, h, o = {}) {
    S.frameLit(x, y, w, h, 0.7, { ghostOnly: true, k: 2 });
    if (o.lit) S.frameLit(x, y, w, h, 0.8, { level: 'dim' });
  }
  function tacCluster(S, d) {
    const gx = CL, gy = BAND.y, lim = gx + 472;
    S.module(gx, gy, 480, 160, { filaments: 4 });
    const rule = (x, y, w, h) => S.rectLit(x, y, w, h, { ghostOnly: true, k: 2.2 });
    rule(gx + 8, gy + 49, 464, 0.8); rule(gx + 8, gy + 99, 464, 0.8);
    // Target: name, class, range, closing, known state
    const t = d.target;
    S.legend(gx + 10, gy + 21, 'TGT', dim);
    S.dm(gx + 44, gy + 8, t.name, { pitch: 2, cells: 8, level: 'norm' });
    S.dm(gx + 44, gy + 29, t.cls, { pitch: 1.5, cells: 15, level: 'dim' });
    let x = gx + 192;
    x += S.seg7(x, gy + 8, 26, t.range, { cells: 3, level: 'norm' }) + 4;
    S.legend(x, gy + 20, 'KM', { lit: t.unit !== 'M', level: 'dim' }); S.legend(x, gy + 34, 'M', { lit: t.unit === 'M', level: 'dim' });
    x += S.legendWidth('KM') + 16;
    x += S.legend(x, gy + 20, 'CLS', dim) + 5;
    x += S.seg7(x, gy + 8, 12, t.closing, { cells: 3, level: 'norm' }) + 6;
    K.fits(x + S.legend(x, gy + 20, 'M/S', dim), lim, 'tac target 1');
    K.fits(K.states(S, gx + 286, gy + 41, [['STRUCK', t.state === 'STRUCK'], ['DISABLED', t.state === 'DISABLED'], ['VENTING', t.state === 'VENTING']], { size: 13, gap: 8 }) - 8, lim, 'tac target 2');
    // Guns in hand and point defense: three chips
    const cw = 150, ch = 40, cy = gy + 54;
    d.guns.forEach((g, i) => {
      const cx = gx + 8 + i * (cw + 7);
      chip(S, cx, cy, cw, ch, { lit: true });
      S.legend(cx + 6, cy + 15, g.pos, { level: 'norm' });
      S.dm(cx + cw - 6 - 43.5, cy + 5, g.who || '', { pitch: 1.5, cells: 5, align: 'right', level: 'dim' });
      S.dm(cx + 6, cy + 24, g.state, { pitch: 1.5, cells: 8, level: g.state === 'READY' ? 'dim' : 'norm' });
      let hx = cx + cw - 6 - S.seg7Width(14, 2) - 2 - S.legendWidth('%');
      S.legend(hx - 4, cy + 35, 'HIT', { level: 'dim', align: 'right' });
      hx += S.seg7(hx, cy + 22, 14, g.hit, { cells: 2, level: 'norm' }) + 2;
      S.legend(hx, cy + 35, '%', dim);
    });
    const px = gx + 8 + 2 * (cw + 7), pd = d.pd;
    chip(S, px, cy, cw, ch);
    S.legend(px + 6, cy + 15, 'PD', { level: 'norm' });
    S.legend(px + 6 + S.legendWidth('PD') + 6, cy + 15, 'AUTO', { level: 'dim' });
    S.dm(px + cw - 6 - 43.5, cy + 5, pd.count || '', { pitch: 1.5, cells: 5, align: 'right', level: 'dim' });
    S.dm(px + 6, cy + 24, pd.state, { pitch: 1.5, cells: 8, level: pd.state === 'IDLE' ? 'dim' : 'norm' });
    // Launcher (missiles left of the magazine, ready or seconds, how many one press throws), decoy, shields
    const ly = gy + 104, l = d.launcher, lw = 228;
    chip(S, gx + 8, ly, lw, 48);
    x = gx + 14;
    x += S.legend(x, ly + 15, l.pos, { level: 'norm' }) + 10;
    x += S.legend(x, ly + 15, 'MSL', dim) + 5;
    x += S.seg7(x, ly + 3, 14, l.left, { cells: 1, level: 'norm' }) + 5;
    K.fits(x + S.legend(x, ly + 15, 'OF ' + l.size, dim), gx + 8 + lw - 6, 'tac launcher 1');
    x = gx + 14;
    x += S.legend(x, ly + 39, 'READY', { lit: l.ready, level: 'norm' }) + 4;
    x += S.seg7(x, ly + 27, 12, l.ready ? '' : l.secs, { cells: 2, level: 'norm' }) + 14;
    // a salvo that empties the magazine sits behind an amber filter: one press throws all of them
    x += S.legend(x, ly + 39, 'SALVO', { color: 'amber', level: 'norm', lit: l.salvo >= l.left }) + 5;
    K.fits(x + S.seg7(x, ly + 27, 12, String(l.salvo), { cells: 1, level: 'norm', color: l.salvo >= l.left ? 'amber' : 'zinc' }), gx + 8 + lw - 6, 'tac launcher 2');
    const dx = gx + 8 + lw + 7, dw = 96;
    chip(S, dx, ly, dw, 48);
    S.legend(dx + 6, ly + 15, 'DECOY', dim);
    S.legend(dx + 6, ly + 39, 'READY', { lit: d.decoy === 'READY', level: 'norm' });
    S.seg7(dx + 10 + S.legendWidth('READY'), ly + 27, 12, d.decoy === 'READY' ? '' : d.decoy, { cells: 1, level: 'norm' });
    const sx = dx + dw + 7, sw = gx + 472 - sx, sh = d.shields;
    chip(S, sx, ly, sw, 48);
    // a hull with no shield emitter has blank glass here, not a ghosted SHLD (guide 7.8): nothing to switch on
    if (!sh) return;
    S.legend(sx + 6, ly + 15, 'SHLD', { level: 'dim' });
    S.legend(sx + 6, ly + 39, 'RAISED', { lit: sh.mode === 'RAISED', level: 'norm' });
    S.seg7(sx + sw - 6 - S.seg7Width(20, 2) - 12, ly + 14, 20, sh.mass, { cells: 2, level: 'norm' });
    S.legend(sx + sw - 16, ly + 34, '%', { level: 'dim' });
  }

  // ------------------------------------------------------------------ MFD pages (9.2)
  // SENSORS: the scope and the contact list sorted by threat; hostile is a fixed red pip column.
  function sensorsPage(d) {
    return (S, x, y, w, h) => {
      K.sectorScope(S, x + 44, y + h / 2, 36, { contacts: d.contacts });
      const lx = x + 100;
      for (let i = 0; i < 4; i++) {
        const r = d.rows[i], ry = y + 2 + i * 19, lv = r && (r.lock || r.missile) ? 'norm' : 'dim';
        if (r && r.lock) S.dm(lx - 11, ry, '▶', { pitch: 1.5, level: 'norm', ghost: false });
        S.dm(lx, ry, r ? r.name : '', { pitch: 1.5, cells: 7, level: lv });
        S.dm(lx + 8 * 9, ry, r ? r.range : '', { pitch: 1.5, cells: 6, align: 'right', level: lv });
        S.dm(lx + 15 * 9, ry, r ? r.time || '' : '', { pitch: 1.5, cells: 4, align: 'right', level: lv });
        K.pip(S, x + w - 9, ry + 5.25, 'red', r && r.hostile);
      }
    };
  }
  // DAMAGE: the ship plan with what is wrong in its room, and who is on it. With nothing wrong it shows the
  // plan's walls at L1 and the room names, and nothing else.
  function damagePage(d) {
    return (S, x, y, w, h) => {
      K.shipPlan(S, x + 8, y + 2, w - 16, h - 6, { states: d.states, crew: d.crew, labels: 'all', pitch: 2 });
    };
  }
  // DEFENSE: the shield ring around the hull, 96 segments by cloud thickness; mode, mass, trim, emitters.
  // Decoy and point defense are the tactical cluster's, beside it, so the page leaves them out.
  function defensePage(d) {
    return (S, x, y, w, h) => {
      const cx = x + 42, cy = y + h / 2;
      S.ring(cx, cy, 28, 35, 96, (i) => {
        const th = d.thickness(i / 96 * 360); // 0 = bow (up), clockwise
        return th > 0.75 ? { level: 'norm' } : th > 0.45 ? { level: 'dim' } : null;
      }, { gapDeg: 1.2 });
      // the hull, bow up, as a dotted outline
      const hullPts = [[0, -22], [6, -13], [7, 15], [3.5, 22], [-3.5, 22], [-7, 15], [-6, -13]];
      for (let i = 0; i < hullPts.length; i++) { const a = hullPts[i], b = hullPts[(i + 1) % hullPts.length]; S.dotLine(cx + a[0], cy + a[1], cx + b[0], cy + b[1], { step: 2.4, size: 1.1, level: 'dim' }); }
      const tx = x + 92, lim = x + w - 8;
      K.fits(K.states(S, tx, y + 13, [['RAISED', d.mode === 'RAISED' && 'norm'], ['LOWERED', d.mode === 'LOWERED'], ['STOWING', d.mode === 'STOWING']], { size: 13, gap: 8 }) - 8, lim, 'defense modes');
      let xx = tx;
      xx += S.legend(xx, y + 37, 'MASS', dim) + 5;
      xx += S.seg7(xx, y + 21, 16, d.mass, { cells: 2, level: 'norm' }) + 2;
      xx += S.legend(xx, y + 37, '%', dim) + 14;
      xx += S.legend(xx, y + 37, 'TRIM', dim) + 5;
      K.fits(xx + S.dm(xx, y + 26.5, d.trim, { pitch: 1.5, cells: 5, level: 'norm' }), lim, 'defense mass');
      d.emitters.forEach((e, i) => {
        const ey = y + 46 + i * 17;
        S.dm(tx, ey, e.name, { pitch: 1.5, cells: 2, level: 'dim' });
        S.bar(tx + 24, ey + 1.5, 120, 8, 20, e.heat, { zone: K.RISING, level: 'norm' });
        if (e.state) S.dm(tx + 152, ey, e.state, { pitch: 1.5, cells: 3, level: 'norm' });
      });
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
    const w = Math.max(...lines.map(l => l.length)) * 12 - 2, cb = Math.cos(b), sb = Math.sin(b);
    const lx = tx + cb * 12 - (cb < -0.3 ? w : cb > 0.3 ? 0 : w / 2), ly = ty + sb * 12 - (sb < -0.3 ? 16 : sb > 0.3 ? -2 : 7);
    K.label(S, lx, ly, lines, { level: 'norm' });
  }
  // A hostile's marks: red corner triangles; the lock adds zinc square brackets outside them.
  function hostile(S, x, y, half, o = {}) {
    K.brackets(S, x, y, half, 'tri', { pitch: 2, len: 5, color: 'red', level: 'norm' });
    if (o.lock) K.brackets(S, x, y, half + 10, 'square', { pitch: 2, len: 6, level: 'full', ticks: true });
  }
  // A world label centred under a point, at the projector's pitch.
  function nameplate(S, x, y, text, o = {}) { K.label(S, x - (text.length * 12 - 2) / 2, y, [text], o); }

  // ------------------------------------------------------------------ the prompt line (Z7): learning hints
  function hints(S, y, items) {
    let w = 0;
    for (const [k, word] of items) w += Math.max(18, S.legendWidth(k, { size: 12, weight: 600, condense: 0.86, track: 0.1 }) + 7) + 5 + word.length * 9 - 1.5 + 16;
    w -= 16;
    const x = 640 - w / 2;
    K.slab(S, x - 8, y, w + 16, 24, { pad: 3, filaments: 1, glare: false });
    let cx = x;
    for (const [k, word] of items) {
      cx += K.keycap(S, cx, y + 3, k) + 5;
      cx += S.dm(cx, y + 6.75, word, { pitch: 1.5, level: 'dim', ghost: false }) + 16;
    }
  }

  // ================================================================== C. Helm, cruising (Green)
  // Walkthrough C's world: the ship in the middle of the view above the band (camera shifted up 92), and
  // dx more to the left when a side panel is open (6.2). A label that would sit under the panel goes to
  // its contact's edge marker instead (6.3 rule 4).
  async function cruiseWorld(S, dx = 0) {
    await K.hull(S, 'freighter', 236 + dx, 176, 0.042, 162, { filter: 'brightness(0.8)' });
    await K.hull(S, 'station_hub', 1012 + dx, 204, 0.06, 0);
    await K.hull(S, 'corvette', 640 + dx, 308, 0.115, -20);

    // symbology: neutral traffic as a glyph only; the nav target by name only (the cluster has its numbers)
    if (236 + dx > 16) K.sprite(S, 'neutral', 236 + dx, 150, { pitch: 2, level: 'dim' });
    K.brackets(S, 1012 + dx, 204, 78, 'diamond', { pitch: 2, len: 6, level: 'norm' });
    if (1012 + dx + 47 < 784 - 16 || !dx) nameplate(S, 1012 + dx, 314, 'HIGHPORT', { level: 'norm' });
    else K.edge(S, 784 - 16, 314, 'r', ['HIGHPORT'], { level: 'norm' }); // at the label's height, clear of the hull
    // velocity markers 70 from the ship, in the ORBIT frame; the next burn's cue with its countdown
    const a = -140 * R, sx = 640 + dx;
    K.velMark(S, 'pro', sx + Math.cos(a) * 70, 308 + Math.sin(a) * 70, { level: 'norm' });
    K.velMark(S, 'retro', sx - Math.cos(a) * 70, 308 - Math.sin(a) * 70, { level: 'dim' });
    burnCue(S, sx, 308, -158, 104, 156, ['1']);
    S.projectNow();
  }

  SCREENS['st-helm'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 11 });
    await cruiseWorld(S);
    cruiseBand(S);
    hints(S, 580, [['N', 'PLAN A BURN'], ['M', 'PLOT'], ['TAB', 'LOCK']]);
    S.finish();
  };

  // The band in walkthrough C: both MFDs folded (Green), the cluster, the board.
  function cruiseBand(S) {
    bandRack(S, false, false);
    rail(S, LMFD, [{ t: 'NAV' }, { t: 'PLAN' }]);
    rail(S, RMFD, [{ t: 'SENSORS' }, { t: 'WEAPONS' }, { t: 'DEFENSE' }]);
    helmCluster(S, {
      frames: [['ORBIT', 'norm'], ['TARGET', false], ['PORT', false], ['DOCKED', false]],
      speed: '7.67', unit: 'KM/S', rot: '', control: 'MANUAL', assist: 'norm', thr: false, rcs: false,
      ref: { name: 'HIGHPORT', range: '1.2', unit: 'KM', speed: '3.9', cpa: '140', cpaT: '5:03' },
      next: { what: 'BURN 1', when: '12:04', dv: '210', dir: 'PRO' },
      trig: null,
      dv: { left: 1840, full: 2600, home: 1210, plan: 420 },
    });
    board(S, [
      { n: 1, t: 'HELM', who: 'YOU', you: true }, { n: 2, t: 'NAV', who: 'YOU', you: true },
      { n: 3, t: 'TAC', who: 'COLE' }, { n: 4, t: 'ENG', who: '' },
      { n: 5, t: 'COMMS', who: 'YOU', you: true }, { n: 6, t: 'CMD' },
    ], ['3', '14:22:05'], { green: true });
  }

  // ================================================================== E. Weapons console, in a fight (Red)
  // The First Play fight: the starting corvette against the pirates' corvette (labelled Raider), which
  // fires harpoons. NOSE (coilgun) and AFT STBD (laser) are in hand; the beam autocannons are PD.
  SCREENS['st-weapons'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 23 });
    const sx = 600, sy = 316, heading = -12;
    await K.hull(S, 'raider', 1060, 214, 0.095, 168);
    await K.hull(S, 'corvette', sx, sy, 0.115, heading);

    // fire arcs of the guns in hand (nose forward, the laser to starboard), dim; the lead line and reticle
    K.arc(S, sx, sy, 100, 250, heading - 24, heading + 18, { level: 'dim', step: 6 });
    K.arc(S, sx, sy, 40, 120, heading + 62, heading + 128, { level: 'dim', step: 6 });
    // 40 m/s closing for the coilgun's 0.9 s is 37 m of lead, 6 units at this zoom: the reticle sits on the Raider,
    // inside the lock mark, and the intercept line stops at its edge
    S.dotLine(sx + 96, sy - 22, 1046, 217, { layer: 'proj', step: 7, size: 1.3, level: 'dim' });
    K.reticle(S, 1054, 215, { level: 'norm', onTarget: true });
    // the lock: the Raider by name only (its numbers are in the Target slot)
    hostile(S, 1060, 214, 34, { lock: true });
    nameplate(S, 1060, 278, 'RAIDER', { level: 'norm' });
    // two harpoons thrown 5 s and 4 s ago, 2.4 and 2.5 km out and swinging wide to port, 8 and 9 s from impact
    // under their 30 m/s² burn: red glyphs and predicted tracks to one impact mark on the port side, with its
    // count; point defense's tracer reaching for the nearer one
    K.missile(S, 984, 195); K.missile(S, 1004, 204);
    K.track(S, [[984, 195], [880, 178], [770, 196], [680, 238], [626, 282]], { step: 7 });
    K.track(S, [[1004, 204], [896, 188], [782, 206], [688, 246], [630, 288]], { step: 7 });
    K.cross(S, 618, 280);
    S.dm(632, 254, '2', { pitch: 2, layer: 'proj', color: 'red', level: 'full', ghost: false });
    S.dotLine(sx + 20, sy - 16, 984, 195, { layer: 'proj', step: 5, size: 1.2, level: 'norm' });

    S.projectNow();

    // the top column: status strip, tiles in their zones, the ticker (warp strip dark)
    statusStrip(S, { condition: 'RED' });
    annRow(S, TOP.row1, [['MISSILE', '2  0:08']], [['HOSTILE', 'FIRING']], { warnOn: true, cautOn: true });
    ticker(S, [null, { tag: 'TAC', text: 'COLE: HARPOONS FROM PORT. PD HAS THEM.' }, { tag: 'HELM', text: 'BEA: HOLDING POSITION.' }]);

    // the band: SENSORS and DAMAGE open (no shield emitter, so the right MFD starts on DAMAGE), the cluster
    bandRack(S, true, true);
    mfd(S, LMFD, 'SENSORS', [{ t: 'SENSORS', active: true }, { t: 'WEAPONS' }, { t: 'DEFENSE' }], sensorsPage({
      // the 5 km step (rings of 1.25 km) holds the Raider and both harpoons
      contacts: [{ brg: 66, ring: 1, missile: true }, { brg: 70, ring: 2, missile: true }, { brg: 78, ring: 2, hostile: true, lock: true }],
      rows: [
        { name: 'HARPOON', range: '2.4 KM', time: '0:08', hostile: true, missile: true },
        { name: 'HARPOON', range: '2.5 KM', time: '0:09', hostile: true, missile: true },
        { name: 'RAIDER', range: '2.8 KM', hostile: true, lock: true },
      ],
    }), { owner: 'TAC', setting: '5 KM' });
    mfd(S, RMFD, 'DAMAGE', [{ t: 'DAMAGE', active: true }, { t: 'POWER' }, { t: 'ATMOS' }], damagePage({ states: {}, crew: [] }), { owner: 'ENG' });
    tacCluster(S, {
      target: { name: 'RAIDER', cls: 'PIRATE CORVETTE', range: '2.8', closing: '40', state: '' },
      guns: [
        { pos: 'NOSE', who: 'COLE', state: 'CHARGING', hit: '81' },
        { pos: 'AFT STBD', who: '', state: 'BURN 60%', hit: '58' },
      ],
      pd: { state: 'ENGAGING', count: '' },
      launcher: { pos: 'AFT PORT', left: '4', size: 4, ready: true, salvo: 4 },
      decoy: 'READY', shields: null,
    });
    board(S, [
      { n: 1, t: 'HELM', who: 'BEA' }, { n: 2, t: 'NAV', who: 'BEA' },
      { n: 3, t: 'TAC', who: 'YOU', you: true, alert: 'warning' }, { n: 4, t: 'ENG', who: '' },
      { n: 5, t: 'COMMS', who: '' }, { n: 6, t: 'CMD' },
    ], ['3', '15:02:44']);
    S.finish();
  };

  // ================================================================== Blackout at the helm (5.1 rule 4; the kit's
  // blacked-out seat, 17). After the First Play fight: a harpoon got through, the reactor is damaged and no
  // reactor runs, so the ship is disabled. The Raider broke off and is still inside 50 km (Yellow); nothing is
  // inbound, so the beacon would answer and the strip offers the tow. The seat's instruments are dark on their
  // NO POWER face; the overlay runs on the emergency bus. The projector is the seat's, so the world is bare.
  SCREENS['st-blackout'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 17 });
    await K.hull(S, 'corvette', 640, 308, 0.115, -34);

    // the top column: the emergency text with its keys, the cause on its own tiles, the callouts
    statusStrip(S, { condition: 'YELLOW', emerg: 'DISABLED {P} {P} ABANDON {B} {B} TOW' });
    annRow(S, TOP.row1, [['POWER', 'NONE'], ['HULL', '52%']], [['WOUNDED', '1']], { warnOn: true, cautOn: true });
    ticker(S, [
      { sev: 'warning', tag: 'CMD', text: 'SHIP: DISABLED, REACTOR DOWN.' },
      { sev: 'warning', tag: 'ENG', text: 'ADA: REPAIRING THE REACTOR.' },
      { sev: 'caution', tag: 'CMD', text: 'SHIP: COLE IS WOUNDED, NOBODY ON HIM.' },
    ]);

    // the band as the seat stood in Yellow (SENSORS open, NAV folded), every tube dark, each instrument on
    // its NO POWER face; the board is the seat's too, so it goes dark with it
    bandRack(S, true, false);
    noPower(S, () => mfd(S, LMFD, 'SENSORS', [{ t: 'SENSORS', active: true }, { t: 'WEAPONS' }, { t: 'DEFENSE' }], sensorsPage({ contacts: [], rows: [] }), { owner: 'TAC' }),
      LMFD, BAND.y, BAND.mfdW, GLASS_B - BAND.y, BAND.y + 63);
    noPower(S, () => rail(S, RMFD, [{ t: 'NAV' }, { t: 'PLAN' }]), RMFD, RAIL_Y, BAND.mfdW, GLASS_B - RAIL_Y);
    noPower(S, () => helmCluster(S, {
      frames: [['ORBIT', 'norm'], ['TARGET', false], ['PORT', false], ['DOCKED', false]],
      speed: '', unit: '', rot: '', control: '', assist: false, thr: false, rcs: false,
      ref: null, next: null, trig: null,
      dv: { left: 0, full: 2600, home: 1210, plan: 0 },
    }), CL, BAND.y, BAND.clW, 160);
    S.dark = true;
    board(S, [
      { n: 1, t: 'HELM' }, { n: 2, t: 'NAV' }, { n: 3, t: 'TAC' }, { n: 4, t: 'ENG' }, { n: 5, t: 'COMMS' }, { n: 6, t: 'CMD' },
    ], ['', '']);
    S.dark = false;
    S.finish();
  };

  // ------------------------------------------------------------------ deck helpers
  // The deck view as the game draws it (a capture), with T0 in-world cues painted on for the mockup:
  // battle lighting, alarm beacons in rooms with an alert, a console glowing in its alert role.
  async function deck(S, src, o = {}) {
    await K.world(S, src, { x: o.dx ?? 0 }); // dx: the camera's shift left while a side panel is open
    const b = S.base;
    if (o.battle) { b.save(); b.globalCompositeOperation = 'multiply'; b.fillStyle = 'rgba(255,150,140,1)'; b.fillRect(0, 0, S.W, S.H); b.restore(); }
    for (const g of o.glows || []) {
      b.save(); b.globalCompositeOperation = 'lighter';
      const rg = b.createRadialGradient(g.x, g.y, 0, g.x, g.y, g.r);
      rg.addColorStop(0, g.color); rg.addColorStop(1, 'rgba(0,0,0,0)');
      b.fillStyle = rg; b.fillRect(g.x - g.r, g.y - g.r, g.r * 2, g.r * 2); b.restore();
    }
    // a fire as the game draws it: an orange glow over the burning tiles, a hot core
    for (const f of o.fires || []) for (const [dx, dy, r, c] of [[0, 0, 62, 'rgba(255,110,25,0.5)'], [12, -6, 30, 'rgba(255,180,70,0.6)'], [-16, 8, 24, 'rgba(255,160,50,0.55)'], [2, 2, 11, 'rgba(255,235,170,0.8)']]) {
      b.save(); b.globalCompositeOperation = 'lighter';
      const rg = b.createRadialGradient(f.x + dx, f.y + dy, 0, f.x + dx, f.y + dy, r);
      rg.addColorStop(0, c); rg.addColorStop(1, 'rgba(0,0,0,0)');
      b.fillStyle = rg; b.fillRect(f.x + dx - r, f.y + dy - r, r * 2, r * 2); b.restore();
    }
    // air venting through a breach: a fan of grey puffs out of the hull (deg: the direction it blows)
    const rr = K.rng(9);
    for (const v of o.vents || []) for (let i = 0; i < 70; i++) {
      const a = (v.deg - 28 + rr() * 56) * Math.PI / 180, d = 3 + rr() * 64;
      b.fillStyle = `rgba(228,234,242,${0.75 - d / 100})`; b.beginPath(); b.arc(v.x + Math.cos(a) * d, v.y + Math.sin(a) * d, 0.8 + d / 26, 0, Math.PI * 2); b.fill();
    }
  }
  // The suit readout (Z6): the captain's own kit, so it is an LCD that reflects, backlit while it warns.
  function suit(S, d) {
    const x = 16, y = 704, w = 200, h = 80;
    K.plate(S, x, y, w, h, { pad: 5, radius: 7 });
    S.lcdPanel(x + 2, y + 2, w - 4, h - 4, { backlight: d.alert });
    S.lcdText(x + 10, y + 17, 'AIR');
    S.lcdSeg7(x + 10, y + 23, 32, d.air, { slant: 6 });
    S.lcdText(x + 108, y + 17, 'ROOM');
    S.lcdSeg7(x + 108, y + 23, 18, d.kpa, { slant: 6 });
    S.lcdText(x + 140, y + 40, 'KPA');
    S.lcdText(x + 108, y + 66, d.o2);
    if (d.wound) S.lcdText(x + 10, y + 72, d.wound);
  }

  // ================================================================== B. On deck, fire and missiles inbound
  SCREENS['st-deck'] = async (canvas) => {
    const S = K.scene(canvas);
    await deck(S, 'bg/deck-lit.jpg', {
      battle: true,
      glows: [
        { x: 760, y: 444, r: 90, color: 'rgba(255,40,20,0.42)' },   // alarm beacon, the bridge (fire)
        { x: 760, y: 444, r: 7, color: 'rgba(255,120,90,0.95)' },
        { x: 520, y: 484, r: 80, color: 'rgba(255,40,20,0.46)' },   // alarm beacon, the hold (breach)
        { x: 520, y: 484, r: 7, color: 'rgba(255,120,90,0.95)' },
        { x: 938, y: 444, r: 34, color: 'rgba(255,50,30,0.6)' },    // the reactor console in its alert role
      ],
      fires: [{ x: 836, y: 474 }],          // the fire Ada is on, in the bridge
      vents: [{ x: 540, y: 366, deg: -90 }], // the hold venting through its port hull
    });
    // the top column: RED (missiles inbound), warnings from the WARN lamp, the caution from the CAUT lamp
    statusStrip(S, { condition: 'RED' });
    annRow(S, TOP.row1, [['MISSILE', '2  0:11'], ['BREACH', 'HOLD']], [['FIRE', 'BRIDGE']], { warnOn: true, cautOn: true });
    ticker(S, [
      { sev: 'caution', tag: 'ENG', text: "ADA: FIRE IN THE BRIDGE, I'M ON IT." },
      { sev: 'warning', tag: 'TAC', text: 'SHIP: MISSILES INBOUND FROM PORT.' },
      { sev: 'warning', tag: 'ENG', text: 'SHIP: THE HOLD IS VENTING, NOBODY ON IT.' },
    ]);
    suit(S, { air: '4:10', kpa: '31', o2: 'O2 LOW', alert: true });
    S.finish();
  };

  // ================================================================== Panels (10; style-vfd.md section 18)
  // A side panel is a terminal 480 wide in Z4: on deck it runs the full height, seated it stops 8 above
  // the band. The Plot, the Yard and the Shipyard are full screen: they hide the band and bring their own
  // annunciator and ticker in Z1 and Z2 (dark in Green, as here). The slate is the captain's own LCD.
  const SC = SCR, P = 1.5, Z4 = { x: 784, w: 480 };
  const T = (S, x, y, str, o = {}) => S.dm(x, y, str, { pitch: P, level: o.level ?? 'norm', ghost: o.ghost ?? false, cells: o.cells, align: o.align, color: o.color, inverse: o.inverse });
  const rule = (S, x, y, w) => S.rectLit(x, y, w, 0.8, { ghostOnly: true, k: 2.2 });
  // The delta-v bar as the helm cluster draws it: what is left of full, the home notch, the plan's bracket.
  function dvBar(S, x, y, w, dv) {
    S.bar(x, y, w, 9, 50, dv.left / dv.full, { level: 'dim', gap: 1.1 });
    const hx = x + w * dv.home / dv.full;
    S.rectLit(hx - 0.7, y + 11, 1.4, 6, { level: 'norm' });
    S.dot(hx - 2.2, y + 13, { size: 1.1, level: 'norm' }); S.dot(hx + 2.2, y + 13, { size: 1.1, level: 'norm' });
    S.legend(hx, y + 31, 'HOME', { size: 13, level: 'dim', align: 'center' });
    if (!dv.plan) return;
    const p0 = x + w * (dv.left - dv.plan) / dv.full, p1 = x + w * dv.left / dv.full;
    S.rectLit(p0, y - 4, p1 - p0, 0.9, { level: 'norm' }); S.rectLit(p0, y - 4, 0.9, 3, { level: 'norm' }); S.rectLit(p1 - 0.9, y - 4, 0.9, 3, { level: 'norm' });
    S.legend((p0 + p1) / 2, y - 8, 'PLAN', { size: 13, level: 'dim', align: 'center' });
  }

  // ------------------------------------------------------------------ the Plot (10.2), full screen
  // Walkthrough C's plan on the PLAN page: a climb from Highport's 400 km orbit to 1,200 km, burn 1 in
  // 12:04 and burn 2 half an orbit later at the top of the climb, 210 m/s prograde each: 420 of the
  // 1,840 m/s left. The plot is to scale (28 km a unit); Highport, 1.2 km off, is spread clear of the ship.
  SCREENS['st-plot'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 11, stars: 160 });
    SC.orbitClimb(S, 16, 16, 752, 768, {
      kpu: 28, alt0: 400, alt1: 1197, shipDeg: 133, navAhead: 7,
      b1: ['BURN 1', 'T-12:04'], b2: ['BURN 2', 'T-1:02:25'], nav: ['HIGHPORT', '1.2 KM'],
      now: ['ORBIT NOW', 'AP 403  PE 397'], after: ['AFTER BURN 2', 'AP 1,220  PE 1,197'],
    });
    const g = SC.terminal(S, Z4.x, 16, Z4.w, 768, {
      pages: [{ t: 'MAP' }, { t: 'PLAN', active: true }, { t: 'TARGETS' }], pageSlots: 3,
      keys: [{ t: 'NEW BURN', k: 'N' }, { t: 'DELETE', k: 'DEL' }, { t: 'WARP TO', k: '/' }, null, { t: 'CLOSE', k: 'ESC' }], keySlots: 5,
    });
    const x0 = g.x + 14, x1 = g.x + g.w - 14, top = g.top + 6;
    // the title bar: the station and the console it is at
    S.seg14(x0, top, 16, 'PLOT', { level: 'full' });
    T(S, x0 + S.seg14Width(16, 4) + 12, top + 4, 'NAVIGATION · HELM CONSOLE', { level: 'dim' });
    rule(S, g.x + 8, top + 26, g.w - 16);
    // the burns in order; the one selected on the plot is inverse here too
    let y = top + 40;
    S.dm(x0, y, 'PLAN', { pitch: 2, level: 'full', ghost: false });
    T(S, x1 - SC.width(7), y + 3, '2 BURNS', { level: 'dim' });
    y += 34;
    const lg = SC.grid(S, x0, y + 16, 50, 4);
    [['#', 1], ['WHEN', 4], ['ΔV M/S', 21, 'r'], ['DIR', 22], ['FLOWN BY', 27]].forEach(([h, c, a]) =>
      S.legend(lg.cx(c) - (a ? 1.5 : 0), y + 9, h, { size: 13, level: 'dim', align: a ? 'right' : 'left', ghost: false }));
    const burn = (n, when, dv, dir, by) => SC.cols([[n, 3], [when, 11], [dv, 6, 'r'], ['', 1], [dir, 5], [by, 15]]);
    lg.row(0, burn('1', 'T-12:04', '210', 'PRO', 'YOU'), { focus: true, cursor: true });
    lg.row(1.5, burn('2', 'T-1:02:25', '210', 'PRO', 'YOU'));
    // the total against what is left, then the tank as the cluster draws it
    y = lg.ry(3.5) + 8;
    rule(S, g.x + 8, y - 8, g.w - 16);
    let fx = SC.figure(S, x0, y + 6, 26, '420', null, { label: 'TOTAL ΔV', cells: 4 });
    K.fits(SC.figure(S, fx + 8, y + 16, 16, '1,840', 'M/S LEFT', { label: 'OF', cells: 4 }), x1, 'plot total');
    y += 62;
    dvBar(S, x0, y, x1 - x0, { left: 1840, full: 2600, home: 1210, plan: 420 });
    y += 46;
    const row = (label, val, o = {}) => { S.legend(x0, y + 9.5, label, { size: 13, level: 'dim', ghost: false }); T(S, x0 + 138, y, val, o); y += 20; };
    row('LEFT AFTER IT', '1,420 M/S');
    row('HOME NEEDS', '1,210 M/S  FROM 1,200 KM', { level: 'dim' });
    row('MARGIN', '210 M/S');
    y += 6; rule(S, g.x + 8, y - 6, g.w - 16); y += 6;
    row('ORBIT NOW', 'AP 403  PE 397 KM');
    row('AFTER BURN 2', 'AP 1,220  PE 1,197 KM');
    row('PERIOD', '1:32:25, THEN 1:49:27', { level: 'dim' });
    y += 6; rule(S, g.x + 8, y - 6, g.w - 16); y += 6;
    row('TRANSFER', 'NONE PROPOSED: HIGHPORT IS HERE', { level: 'dim' });
    row('AUTOPILOT', 'OFF', { level: 'dim' });
    // the selected burn, over the keys that move it: its two handles and its time
    y = g.bottom - 100;
    rule(S, g.x + 8, y - 12, g.w - 16);
    T(S, x0, y, 'BURN 1  AT T-12:04, ON THE ORBIT NOW');
    fx = SC.figure(S, x0, y + 22, 16, '210.0', 'M/S', { label: 'PRO', cells: 5 });
    K.fits(SC.figure(S, fx + 22, y + 22, 16, '0.0', 'M/S', { label: 'RAD', cells: 5 }), x1, 'plot selected');
    let kx = x0;
    kx = SC.capWord(S, kx, y + 56, ['↑', '↓'], 'PRO') - 4;
    kx = SC.capWord(S, kx, y + 56, ['←', '→'], 'RADIAL') - 4;
    K.fits(SC.capWord(S, kx, y + 56, ['[', ']'], 'TIME') - 14, x1, 'plot handle keys');
    S.finish();
  };

  // ------------------------------------------------------------------ Station link (10.3), on deck
  // Walkthrough G: docked at Highport, the link open on its Market from the deck. The camera has moved
  // left by half the panel so the ship stays in the middle of what is left of the world, and the status
  // strip (DOCKED) has recentred in that space. Highport has no shipyard, so that key's glass is blank.
  SCREENS['st-link'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 4, stars: 420 });
    await deck(S, 'bg/deck-lit.jpg', { dx: -240 });
    const tx = TOP.x; TOP.x = 32; // the top-centre column, recentred left of the panel (6.3 rule 2)
    statusStrip(S, { state: 'DOCKED' });
    TOP.x = tx;
    const g = SC.terminal(S, Z4.x, 16, Z4.w, 768, {
      side: true, sideTop: 76, sidePitch: 40,
      pages: [{ t: 'MARKET', k: 'T', active: true }, { t: 'YARD', k: 'Y' }, null, { t: 'BOARD', k: 'J' }, { t: 'HIRING' }, { t: 'INFIRMARY' }],
      keys: [{ t: 'BUY', k: 'ENTER' }, { t: 'SELL', dim: true }, null, { t: 'CLOSE', k: 'ESC' }], keySlots: 4,
    });
    const x0 = g.x + 12, x1 = g.x + g.w - 12, top = g.y + 10;
    // the title bar: the berth, its owner, and the two numbers every trade here is bounded by
    S.seg14(x0, top, 16, 'HIGHPORT', { level: 'full' });
    T(S, x0 + S.seg14Width(16, 8) + 12, top + 4, 'TERRAN PATROL AUTHORITY', { level: 'dim' });
    let fx = SC.figure(S, x0, top + 30, 16, '5,000', 'CR', { label: 'CREDITS', cells: 5 });
    fx = SC.figure(S, fx + 22, top + 30, 16, '12', null, { label: 'HOLD', cells: 2 });
    K.fits(SC.figure(S, fx + 6, top + 30, 16, '40', 'M3', { label: 'OF', cells: 2 }), x1, 'link title bar');
    rule(S, g.x + 8, top + 56, g.w - 16);
    // the market, beside the page keys: catalog order, prices in credits, amounts in m3
    const cx0 = g.left, cells = Math.floor((x1 - cx0 + 1.5) / 9);
    let y = g.y + 84;
    T(S, cx0, y, 'PRICES IN CR, AMOUNTS IN M3', { level: 'dim' });
    y += 26;
    const lg = SC.grid(S, cx0, y + 16, cells, 7.5);
    [['COMMODITY', 1], ['STOCK', 16, 'r'], ['BUY', 21, 'r'], ['SELL', 26, 'r'], ['HELD', 31, 'r']].forEach(([h, c, a]) =>
      S.legend(lg.cx(c + (a ? 1 : 0)) - (a ? 1.5 : 0), y + 9, h, { size: 13, level: 'dim', align: a ? 'right' : 'left', ghost: false }));
    const line = (n, st, b, s, h) => SC.cols([[n, 11], [st, 5, 'r'], [b, 5, 'r'], [s, 5, 'r'], [h, 5, 'r']]);
    lg.row(0, line('WATER', '80', '15', '13', '4'));
    lg.row(1.5, line('FOOD', '420', '38', '34', '0'), { focus: true, cursor: true });
    lg.row(3, line('MACHINERY', '12', '672', '608', '0'));
    S.legend(lg.cx(1), lg.ry(5) + 9.5, 'NOT TRADED HERE', { size: 13, level: 'dim', ghost: false });
    lg.row(6, line('IRON ORE', '', '', '', '8'), { level: 'dim' });
    K.fits(lg.cx(cells), x1 + 2, 'market columns');
    // under the page keys the glass is the page's: the trade as it stands, before Enter confirms it
    y = g.y + 76 + 6 * 40 + 18;
    rule(S, g.x + 8, y - 10, g.w - 16);
    S.dm(x0, y, 'FOOD', { pitch: 2, level: 'full', ghost: false });
    T(S, x0 + SC.width(4, 2) + 12, y + 3, 'MADE HERE, 420 IN STOCK', { level: 'dim' });
    y += 30;
    S.legend(x0, y + 9.5, 'QTY', { size: 13, level: 'dim', ghost: false });
    let qx = x0 + 44;
    for (const [q, on] of [['1', true], ['10', false], ['ALL', false]]) {
      T(S, qx, y, ` ${q} `, { level: on ? 'full' : 'norm', inverse: on ? [0, q.length + 2] : null });
      qx += SC.width(q.length + 2) + 10;
    }
    K.fits(SC.capWord(S, qx + 10, y - 3, ['←', '→'], 'CHANGES IT', { level: 'dim' }), x1 + 14, 'link qty');
    y += 30;
    T(S, x0, y + 9, 'BUY 1 FOOD FOR');
    fx = S.seg7(x0 + 150, y, 26, '38', { cells: 4, level: 'norm' }) + x0 + 150 + 5;
    S.legend(fx, y + 26, 'CR', { size: 13, level: 'dim' });
    y += 46;
    T(S, x0, y, 'CREDITS AFTER   4,962', { level: 'dim' });
    T(S, x0, y + 20, 'HOLD AFTER      13 OF 40 M3', { level: 'dim' });
    T(S, x0, y + 40, 'SELL: NONE HELD', { level: 'dim' });
    // the footer's response line, over the soft keys: what the last trade did
    T(S, x0, g.bottom - 22, 'SOLD 2 WATER FOR 26 CR', { level: 'norm' });
    rule(S, g.x + 8, g.bottom - 2, g.w - 16);
    S.finish();
  };

  // ------------------------------------------------------------------ the slate (10.4), seated
  // Seated at the helm in walkthrough C, the slate open on Career: it is a side panel, so it stops 8 above
  // the band and the camera moves 240 left; Highport's name, which would now sit under it, goes to its edge
  // marker. The slate is the captain's own kit, so it is LCD that reflects; the backlight comes on for 3 s
  // after a key press, and is off here.
  SCREENS['st-slate'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 11 });
    await cruiseWorld(S, -240);
    cruiseBand(S);
    const L = SC.slate(S, Z4.x, 16, Z4.w, 592, {
      brand: 'SLATE', owner: 'CAPTAIN', keyTop: 40, keyPitch: 50,
      keysLeft: [
        { k: '', t: 'SHIP' }, { k: 'I', t: 'CREW' }, { k: '', t: 'MEDICAL' }, { k: '', t: 'EVA' }, { k: 'K', t: 'BOARDING' },
        { k: '', t: 'PRISONERS' }, { k: 'J', t: 'CONTRACTS' }, { k: '', t: 'CAREER', active: true }, { k: 'L', t: 'LOG' },
      ],
    });
    const D = (x, y, str, o = {}) => SC.lcdDm(S, x, y, str, o);
    const ink = 'rgba(26,32,22,0.6)';
    // the title bar across the glass: the captain's rank, the ship, the credits
    D(L.x + 6, L.y + 8, 'ENSIGN · CORVETTE');
    D(L.x + L.w - 6 - SC.width(8), L.y + 8, '5,000 CR');
    SC.lcdRule(S, L.x + 4, L.y + 26, L.w - 8);
    const cx = L.x + 100, cw = L.x + L.w - 8 - cx;
    SC.lcdRule(S, cx - 10, L.y + 32, L.h - 40, true);
    let y = L.y + 40;
    S.lcdText(cx, y + 12, 'CAREER', { size: 15 });
    D(cx + 72, y + 3, 'TERRAN PATROL', { level: 'dim' });
    y += 30;
    const F = (label, val, o = {}) => { S.lcdText(cx, y + 9, label, { size: 12, ink }); if (val) D(cx + 100, y, val, o); y += o.step ?? 20; };
    F('RANK', 'ENSIGN');
    F('NEXT RANK', 'LIEUTENANT AT 15');
    F('STANDING', '8 OF 15', { step: 0 });
    SC.lcdBar(S, cx + 100 + SC.width(8) + 10, y + 1.5, 90, 7.5, 15, 8 / 15); y += 20;
    F('BUDGET', '2,000 CR A WEEK');
    F('PAYDAY', 'DAY 7, IN 3D 9H');
    F('MAY FLY', 'UP TO A CORVETTE');
    y += 4; SC.lcdRule(S, cx, y, cw); y += 10;
    // standing with every faction, the sponsor first; the pirates are at war with everyone
    S.lcdText(cx, y + 9, 'STANDING BY FACTION', { size: 12, ink }); y += 22;
    for (const [name, v, war] of [['TERRAN PATROL', 8], ['SELENE COMBINE', 2], ['FREE TRADERS', 0], ['VOID RAIDERS', 0, true]]) {
      D(cx, y, name, { cells: 15 });
      if (war) D(cx + 15 * 9, y, 'AT WAR');
      else { D(cx + 15 * 9, y, String(v), { cells: 3, align: 'right' }); SC.lcdBar(S, cx + 19 * 9, y + 1.5, 90, 7.5, 20, v / 100); }
      y += 18;
    }
    y += 4; SC.lcdRule(S, cx, y, cw); y += 10;
    S.lcdText(cx, y + 9, 'RECORD', { size: 12, ink }); y += 22;
    F('CONTRACTS', '2 DONE, NONE FAILED');
    F('PRIZES', 'NONE YET', { level: 'dim' });
    y += 4; SC.lcdRule(S, cx, y, cw); y += 10;
    S.lcdText(cx, y + 9, 'TENSION', { size: 12, ink }); y += 22;
    D(cx, y, 'PATROL AND SELENE', { cells: 18 }); D(cx + 18 * 9, y, '12', { cells: 3, align: 'right' });
    SC.lcdBar(S, cx + 22 * 9, y + 1.5, 70, 7.5, 10, 0.12); y += 18;
    D(cx, y, 'COMBAT JOBS FROM 50', { level: 'dim' });
    K.fits(cx + 22 * 9 + 70, L.x + L.w - 6, 'slate tension bar');
    S.finish();
  };

  Object.assign(K, { st: { deck, suit, statusStrip, burnCue, hostile, nameplate, bandRack, rail, mfd, board, annRow, ticker, helmCluster, tacCluster, sensorsPage, defensePage, hints, BAND, TOP, LMFD, CL, RMFD } });
})();
