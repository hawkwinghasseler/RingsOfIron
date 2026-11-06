// Nasa-punk x Task-driven: the pieces this combination adds to the kit.
// Zones from functional-task.md (round 5), section 5 and its wireframes. Z9 is a full-width key
// strip along the bottom edge, 24 tall, and every other zone ends above it, at y 760.
//
// The left column is widened inside the spec's allowance of a quarter, to 272 (the spec's 240 plus
// 13%), so the lamps' 12-unit legends fit lenses 49 wide. Z2 is the spec's 640 (320 to 960): three
// cautions sized to their text, then the reserved +n slot, 44 wide, at the row's end.
const ZT = {
  chr: { x: 16, y: 16, w: 272, h: 48 }, alr: { x: 320, y: 16, w: 640 }, inc: { x: 1024, y: 16, w: 240 },
  self: { x: 16, y: 72, w: 272 }, sch: { x: 16, y: 610, w: 300, h: 150 }, oth: { x: 1024, y: 214, w: 240 },
  arms: { x: 720, y: 594, w: 360, h: 166 }, sns: { x: 1088, y: 584, s: 176 }, flt: { x: 420, y: 644, w: 440, h: 116 },
  hnt: { x: 0, y: 776, w: 1280, h: 24 },
};
const TB = 12.5, TV = 15;
// The spec's floor (section 10): nothing the player must read under 12 units; numbers read under
// time pressure (FLT, WPN, ALR, the target card's range and closing) at body size, 15.
TEXT_FLOOR = 12;

// CHR: the mission clock. A small hardware block: the time large, warp and the next stop under it.
function chronometer(o) {
  const { x, y, w, h } = ZT.chr;
  let s = bezelBody(x, y, w, h, 3) + fastener(x + 6, y + 6) + fastener(x + w - 6, y + h - 6);
  s += rect(x + 10, y + 6, w - 20, h - 12, { fill: C.glass });
  s += text(x + 18, y + 22, o.day, { size: 12, bold: true, fill: C.dim, track: 0.6 }) + text(x + 72, y + 23, o.time, { size: 16, bold: true, fill: C.white });
  s += text(x + 18, y + 38.5, 'WARP', { size: 12, bold: true, fill: C.dim, track: 0.6 }) + text(x + 64, y + 38.5, o.warp, { size: 12, bold: true, fill: C.white });
  if (o.note) s += text(x + w - 16, y + 38.5, o.note, { size: 12, bold: true, fill: o.noteColor || C.white, anchor: 'end', track: 0.2 });
  return s;
}

// ANN: ten lamps in two rows of five, a caution-and-warning lens panel, dark when well. A lamp is
// dark, advisory (lit softly: a light legend on a grey lens), caution (amber) or warning (red, with
// a ring, so the level reads without colour). A ship without shields has no SHIELD lamp: its place
// is a blanking plate, as on a panel built for a system this ship doesn't carry.
function annunciatorPanel(lit, o = {}) {
  const { x, y, w } = ZT.self, h = 64;
  let s = bezelBody(x, y, w, h, 3) + fastener(x + 6, y + 6) + fastener(x + w - 6, y + 6) + fastener(x + 6, y + h - 6) + fastener(x + w - 6, y + h - 6);
  const names = ['HULL', 'FIRE', 'AIR', 'POWER', 'PROP', 'DRIVE', 'ARMS', 'CREW', 'EVA', 'SHIELD'];
  names.forEach((n, i) => {
    const col = i % 5, row = Math.floor(i / 5), lx = x + 11 + col * 51, ly = y + 8 + row * 26;
    if (n === 'SHIELD' && !o.shields) {
      s += rect(lx - 1, ly - 1, 51, 24, { fill: '#151719', r: 2 }) + rect(lx, ly, 49, 22, { fill: '#26292D', r: 1.5 });
      [lx + 7, lx + 42].forEach(fx => s += `<circle cx="${fx}" cy="${ly + 11}" r="2" fill="#1A1C1F" stroke="${C.edgeHi}" stroke-width="0.5"/>`);
      return;
    }
    const st = lit[n], c = st === 'warning' ? C.red : st === 'caution' ? C.amber : st === 'advisory' ? 'advisory' : null;
    s += lens(lx, ly, 49, 22, n, c);
    if (st === 'warning') s += rect(lx - 2, ly - 2, 53, 26, { stroke: C.red, sw: 1.2, r: 2 });
  });
  return s;
}

// ALR: two rows that keep their places (section 9.1). The warning row is the top row: a red-framed
// glass with a lens, the number that matters large, the action on a key cap; with no warning it is
// empty. The caution row is always the second: up to three amber strips, fitted to their text, each
// naming who handles it (NOBODY in amber).
function alertBand(o) {
  const { x, y, w } = ZT.alr, wh = 40, cy = y + 46, ch = 30;
  let s = '';
  if (o.warning) {
    const W0 = o.warning;
    s += rect(x - 2, y - 2, w + 4, wh + 4, { fill: '#151719', r: 3 }) + rect(x, y, w, wh, { fill: C.glass, stroke: C.red, sw: 2, r: 2 });
    s += lens(x + 5, y + 5, 74, wh - 10, W0.tag, C.red);
    s += text(x + 90, y + 20, W0.big, { size: 20, bold: true, fill: C.red, track: 0.5 });
    if (W0.big2) s += text(x + 90 + tw(W0.big, 20, true, 0.5) + 16, y + 20, W0.big2, { size: TV, bold: true, fill: C.white, track: 0.3 });
    s += text(x + 90, y + 34.5, W0.line2, { size: 12, bold: true, fill: C.white, track: 0.3 });
    let kx = x + w - 12;
    (W0.actions || []).slice().reverse().forEach(([k, v]) => {
      const vw = tw(v, 12.5, true, 0.4), kw = tw(k, 12, true) + 14;
      s += text(kx, y + 25, v, { size: 12.5, bold: true, fill: C.white, anchor: 'end', track: 0.4 });
      kx -= vw + 7;
      s += softKey(kx - kw, y + 10, kw, 20, false) + text(kx - kw / 2, y + 24.5, k, { size: 12, bold: true, fill: C.stencil, anchor: 'middle' });
      kx -= kw + 18;
    });
  }
  // Cautions are sized to their text (the spec leaves it to the style) and fill from the left; the
  // last 44 units are the +n slot, reserved so that no caution moves or shrinks when it lights.
  let cx = x;
  const nx = x + w - 44;
  (o.cautions || []).slice(0, 3).forEach(([code, where, who]) => {
    const cw0 = tw(code, 12, true, 0.6), ww = tw(where, 12), hw = tw(who, 12, true, 0.2);
    const cw = 14 + cw0 + 10 + ww + 14 + hw + 10;
    s += rect(cx - 1, cy - 1, cw + 2, ch + 2, { fill: '#151719', r: 2 }) + rect(cx, cy, cw, ch, { fill: C.glass, stroke: C.amber, sw: 1.4, r: 1.5 });
    s += rect(cx, cy, 6, ch, { fill: C.amber, r: 1.5 });
    s += text(cx + 14, cy + 19.5, code, { size: 12, bold: true, fill: C.amber, track: 0.6 });
    s += text(cx + 14 + cw0 + 10, cy + 19.5, where, { size: 12, fill: C.white });
    s += text(cx + cw - 10, cy + 19.5, who, { size: 12, bold: true, fill: who === 'NOBODY' ? C.amber : C.white, anchor: 'end', track: 0.2 });
    cx += cw + 8;
  });
  if (o.more) s += rect(nx - 1, cy - 1, 46, ch + 2, { fill: '#151719', r: 2 }) + rect(nx, cy, 44, ch, { fill: C.glass, stroke: C.amber, sw: 1.4, r: 1.5 }) + text(nx + 22, cy + 19.5, o.more, { size: 12, bold: true, fill: C.amber, anchor: 'middle', track: 0.2 });
  return s;
}

// OBJ: contract rows on a slip with a kapton tab (magenta for the navigation target's row, which
// carries a second line only when it has a next step).
function objectives(rows) {
  const { x, y, w } = ZT.inc;
  let s = '', yy = y;
  rows.forEach(r => {
    // a title too long to sit beside its time left (a delivery's, in Z3's 240) takes a second
    // line, broken where the two are most even; the time stays on the first
    let lines = [r.t];
    if (tw(r.t, TB, true) > w - 34 - tw(r.left, TB, true)) {
      const words = r.t.split(' ');
      let best = 1, bw = Infinity;
      for (let i = 1; i < words.length; i++) {
        const m = Math.max(tw(words.slice(0, i).join(' '), TB, true), tw(words.slice(i).join(' '), TB, true));
        if (m < bw) { bw = m; best = i; }
      }
      lines = [words.slice(0, best).join(' '), words.slice(best).join(' ')];
    }
    const h = 26 + (lines.length - 1) * 16 + (r.sub ? 16 : 0);
    s += rect(x, yy, w, h, { fill: 'rgba(5,7,10,0.92)', stroke: '#2A333D', sw: 1 }) + rect(x, yy, 4, h, { fill: r.nav ? C.magenta : C.kapton });
    lines.forEach((t, i) => { s += text(x + 12, yy + 17.5 + i * 16, t, { size: TB, bold: true, fill: C.white }); });
    s += text(x + w - 8, yy + 17.5, r.left, { size: TB, bold: true, fill: r.late ? C.amber : C.white, anchor: 'end' });
    if (r.sub) s += text(x + 24, yy + h - 7, r.sub, { size: 12, bold: true, fill: r.nav ? C.magenta : C.dim, track: 0.4 });
    yy += h + 6;
  });
  return { svg: s, y: yy };
}

// NTF: news on comm-loop slips under the objectives. The game's notices run long for Z3's 240
// (a payday is 384 on one line), so a notice that doesn't fit takes a second line under its text
// rather than losing its end.
function news(y, items) {
  const { x, w } = ZT.inc;
  let s = '', yy = y;
  items.forEach(([tag, msg]) => {
    const tx = tw(tag, fs(10), true, 0.6) + 22, room = w - tx - 8;
    if (tw(msg, 12) <= room) { s += O.callout(x, yy, tag, '', msg, 'advisory', { w }); yy += 24; return; }
    // the break that makes the two lines most even, so no word is left alone on the second
    const words = msg.split(' ');
    let best = 1, bw = Infinity;
    for (let i = 1; i < words.length; i++) {
      const m = Math.max(tw(words.slice(0, i).join(' '), 12), tw(words.slice(i).join(' '), 12));
      if (m < bw) { bw = m; best = i; }
    }
    s += O.callout(x, yy, tag, '', words.slice(0, best).join(' '), 'advisory', { w }) + rect(x, yy + 20, w, 15, { fill: 'rgba(5,7,10,0.92)' });
    s += text(x + tx, yy + 29.5, words.slice(best).join(' '), { size: 12, fill: C.white });
    yy += 39;
  });
  return s;
}

// HNT: the full-width key strip (Z9): caps with their verbs, centred; dimmed once learned.
function hintLine(keys, o = {}) {
  const { x, y, w, h } = ZT.hnt;
  const kwOf = k => tw(k, 12, true) + 14, vwOf = v => tw(v, 12, true, 0.4);
  let s = rect(x, y, w, h, { fill: 'rgba(5,7,10,0.88)' }) + line(x, y + 0.5, x + w, y + 0.5, '#1E262E', 1);
  let total = keys.reduce((a, [k, v]) => a + kwOf(k) + 7 + vwOf(v) + 26, 0) - 26, kx = x + (w - total) / 2;
  keys.forEach(([k, v]) => {
    const kw = kwOf(k);
    s += softKey(kx, y + 3, kw, 18, false) + text(kx + kw / 2, y + 16.5, k, { size: 12, bold: true, fill: C.stencil, anchor: 'middle' });
    s += text(kx + kw + 7, y + 16.5, v, { size: 12, bold: true, fill: o.faded ? '#4C5864' : C.dim, track: 0.4 });
    kx += kw + 7 + vwOf(v) + 26;
  });
  return s;
}

// HLM: who has the helm and the standing order, as an autopilot mode strip (FMA style: the order
// boxed in green while it flies, white while it waits).
function helmStatus(x, y, w, who, order, state = 'engaged', note) {
  let s = rect(x - 1, y - 1, w + 2, 26, { fill: '#151719', r: 2 }) + rect(x, y, w, 24, { fill: C.glass, r: 1.5 });
  s += text(x + 10, y + 16.5, 'HELM', { size: 12, bold: true, fill: C.dim, track: 0.6 }) + text(x + 58, y + 16.5, who, { size: TB, bold: true, fill: C.white, track: 0.4 });
  const ow = tw(order, 12, true, 0.5) + 16;
  s += I.box(x + w - ow - 4, y + 3, ow, 18, order, state, { size: 12 });
  if (note) s += text(x + w - ow - 14, y + 16.5, note, { size: 12, bold: true, fill: C.dim, anchor: 'end', track: 0.4 });
  return s;
}

// Deck hazard tag: a glass slip on a leader from the hazard. It says what the hazard is and shows
// the progress of whoever works it (the bar the crew already show over their work); the worker is
// the figure standing there, and the alert band names them. Nobody on it: NOBODY, in amber.
function hazardTag(ax, ay, x, y, sev, what, o = {}) {
  const col = sev === 'warning' ? C.red : C.amber, h = 30;
  const w = o.w || Math.round(tw(what, 12, true, 0.4) + (o.nobody ? tw('NOBODY', 12, true, 0.2) + 14 : 0) + 24);
  const ex = ax < x ? x : ax > x + w ? x + w : ax, ey = ay < y ? y : y + h;
  let s = haloed((c, ww) => line(ax, ay, ex, ey, c, ww), col, 1.2) + `<circle cx="${ax}" cy="${ay}" r="2.5" fill="${col}"/>`;
  s += rect(x, y, w, h, { fill: 'rgba(5,7,10,0.93)', stroke: col, sw: 1, r: 1.5 }) + rect(x, y, 5, h, { fill: col, r: 1 });
  s += text(x + 12, y + (o.nobody ? 19.5 : 15.5), what, { size: 12, bold: true, fill: col, track: 0.4 });
  if (o.nobody) s += text(x + w - 8, y + 19.5, 'NOBODY', { size: 12, bold: true, fill: C.amber, anchor: 'end', track: 0.2 });
  else s += rect(x + 12, y + 21, w - 22, 4, { fill: '#2A333D' }) + rect(x + 12, y + 21, (w - 22) * (o.progress || 0), 4, { fill: C.white });
  return s;
}

// SNS: the scope DU in Z7's corner. The range is automatic (spec round 6), and the − and + caps
// either side of its figure override it until the post changes, as RadarScope's buttons sit either
// side of the range today (− steps out, + in). The count of hostiles, when there are any, sits
// under the scope on a row of its own, so nothing moves when one appears. Contacts sit at their
// world offsets as fractions of the range; past 1 they are ticks on the rim.
function scopeDU(contacts, o = {}) {
  return du(ZT.sns.x, ZT.sns.y, ZT.sns.s, ZT.sns.s, {
    id: 'sns', b: 8, title: 'SCOPE',
    content: (gx, gy, gw, gh) => {
      const cx = gx + gw / 2, cy = gy + 60, r = 54, ky = gy + gh - 24;
      let c = I.scope(cx, cy, r, contacts, { heading: o.heading });
      if (o.hostiles) c += text(gx + 6, gy + 129, o.hostiles, { size: 12, bold: true, fill: C.red, track: 0.3 });
      [['−', gx + 4], ['+', gx + gw - 26]].forEach(([k, bx]) => {
        c += rect(bx, ky, 22, 22, { fill: '#0E141A', stroke: '#3A434D', sw: 1 }) + text(bx + 11, ky + 16.5, k, { size: TV, bold: true, fill: C.white, anchor: 'middle' });
      });
      c += text(cx, ky + 16, o.range, { size: 12, fill: C.dim, anchor: 'middle', track: 0.3 });
      return c;
    },
  }).svg;
}

// Deck work tag (OVL's deck set, for a crew member at work: a boarder, a salvage hand): a glass
// slip on a leader from the worker, white because work under way is the normal state, not an
// alert. Who, then what, and the work's progress under them when it has one (the bar the crew
// already show over their work).
function workTag(ax, ay, x, y, who, what, o = {}) {
  const h = o.progress != null ? 30 : 24, ww = tw(who, 12, true, 0.4);
  const w = o.w || Math.round(ww + 10 + tw(what, 12, false, 0.2) + 22);
  const ex = ax < x ? x : ax > x + w ? x + w : ax, ey = ay < y ? y : y + h;
  let s = haloed((c, lw) => line(ax, ay, ex, ey, c, lw), C.white, 1) + `<circle cx="${ax}" cy="${ay}" r="2.5" fill="${C.white}"/>`;
  s += rect(x, y, w, h, { fill: 'rgba(5,7,10,0.93)', stroke: '#5B636D', sw: 1, r: 1.5 }) + rect(x, y, 5, h, { fill: C.white, r: 1 });
  s += text(x + 12, y + 16, who, { size: 12, bold: true, fill: C.white, track: 0.4 }) + text(x + 12 + ww + 10, y + 16, what, { size: 12, fill: C.white, track: 0.2 });
  if (o.progress != null) s += rect(x + 12, y + 21, w - 22, 4, { fill: '#2A333D' }) + rect(x + 12, y + 21, (w - 22) * o.progress, 4, { fill: C.white });
  return s;
}
