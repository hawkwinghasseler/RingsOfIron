// Nasa-punk x Task-driven: the pieces this combination adds to the kit.
// Zones from functional-task.md (round 1), section 5 and its wireframes.

const ZT = {
  chr: { x: 16, y: 16, w: 240, h: 48 }, alr: { x: 360, y: 16, w: 560 }, inc: { x: 1024, y: 16, w: 240 },
  self: { x: 16, y: 72, w: 240 }, sch: { x: 16, y: 520, w: 300, h: 264 }, oth: { x: 1024, y: 214, w: 240 },
  arms: { x: 800, y: 560 }, sns: { x: 1088, y: 608, s: 176 }, flt: { x: 420, y: 660, w: 440, h: 100 }, hnt: { x: 320, y: 768, w: 640 },
};
const TB = 12.5, TV = 15;

// CHR: the mission clock. A small hardware block: the time large, warp and the next stop under it.
function chronometer(o) {
  const { x, y, w, h } = ZT.chr;
  let s = bezelBody(x, y, w, h, 3) + fastener(x + 6, y + 6) + fastener(x + w - 6, y + h - 6);
  s += rect(x + 10, y + 6, w - 20, h - 12, { fill: C.glass });
  s += text(x + 18, y + 22, o.day, { size: 10, bold: true, fill: C.dim, track: 0.8 }) + text(x + 62, y + 23, o.time, { size: 16, bold: true, fill: C.white });
  s += text(x + 18, y + 38, 'TW', { size: 9.5, bold: true, fill: C.dim, track: 0.8 }) + text(x + 40, y + 38.5, o.warp, { size: 11.5, bold: true, fill: C.white });
  if (o.note) s += text(x + w - 18, y + 38.5, o.note, { size: 11, bold: true, fill: o.noteColor || C.white, anchor: 'end', track: 0.4 });
  return s;
}

// ANN: ten lamps in two rows of five, a classic caution-and-warning lens panel. Unlit when well.
// lit: { FIRE: 'caution' | 'warning', ... }; a ship without shields omits SHLD (o.noShields).
function annunciatorPanel(lit, o = {}) {
  const { x, y, w } = ZT.self, h = 64;
  let s = bezelBody(x, y, w, h, 3) + fastener(x + 6, y + 6) + fastener(x + w - 6, y + 6) + fastener(x + 6, y + h - 6) + fastener(x + w - 6, y + h - 6);
  const names = ['HULL', 'FIRE', 'AIR', 'POWER', 'PROP', 'DRIVE', 'ARMS', 'CREW', 'EVA', 'SHLD'];
  names.forEach((n, i) => {
    const col = i % 5, row = Math.floor(i / 5), lx = x + 14 + col * 43, ly = y + 9 + row * 25;
    if (n === 'SHLD' && o.noShields) return;
    const st = lit[n], c = st === 'warning' ? C.red : st === 'caution' ? C.amber : null;
    s += lens(lx, ly, 40, 21, n, c);
    if (st === 'warning') s += rect(lx - 2, ly - 2, 44, 25, { stroke: C.red, sw: 1.2, r: 2 });
  });
  return s;
}

// ALR: one warning (a red-framed glass with a lens, the number that matters and the action's key),
// then up to three cautions as amber lens strips, each naming its action.
function alertBand(o) {
  const { x, y, w } = ZT.alr;
  let s = '';
  if (o.warning) {
    const W0 = o.warning, h = 46;
    s += rect(x - 2, y - 2, w + 4, h + 4, { fill: '#151719', r: 3 }) + rect(x, y, w, h, { fill: C.glass, stroke: C.red, sw: 2, r: 2 });
    s += lens(x + 5, y + 5, 88, h - 10, W0.tag, C.red);
    s += text(x + 104, y + 22, W0.line1, { size: 17, bold: true, fill: C.red, track: 0.5 });
    s += text(x + 104, y + 39, W0.line2, { size: TB, bold: true, fill: C.white, track: 0.3 });
    let kx = x + w - 12;
    (W0.actions || []).slice().reverse().forEach(([k, v]) => {
      const vw = v.length * 7.6, kw = k.length * 7 + 12;
      s += text(kx, y + 29, v, { size: TB, bold: true, fill: C.white, anchor: 'end', track: 0.4 });
      kx -= vw + 6;
      s += softKey(kx - kw, y + 15, kw, 19, false) + text(kx - kw / 2, y + 29, k, { size: 11, bold: true, fill: C.stencil, anchor: 'middle' });
      kx -= kw + 18;
    });
  }
  const cy = y + (o.warning ? 52 : 0);
  (o.cautions || []).slice(0, 3).forEach((c, i) => {
    const cw = (w - 12) / 2, cx = x + (i % 2) * (cw + 12), yy = cy + Math.floor(i / 2) * 28;
    s += rect(cx - 1, yy - 1, cw + 2, 26, { fill: '#151719', r: 2 }) + rect(cx, yy, cw, 24, { fill: C.glass, stroke: C.amber, sw: 1.4, r: 1.5 });
    s += rect(cx, yy, 8, 24, { fill: C.amber, r: 1.5 });
    s += text(cx + 16, yy + 16.5, c[0], { size: TB, bold: true, fill: C.amber, track: 0.6 });
    s += text(cx + 16 + c[0].length * 9.4 + 10, yy + 16.5, c[1], { size: TB, fill: C.white });
    if (c[2]) s += text(cx + cw - 8, yy + 16.5, c[2], { size: 11.5, bold: true, fill: C.white, anchor: 'end', track: 0.4 });
  });
  return s;
}

// OBJ: contract rows on a slip with a kapton tab; the navigation target's row carries its second line.
function objectives(rows) {
  const { x, y, w } = ZT.inc;
  let s = '', yy = y;
  rows.forEach(r => {
    const h = r.sub ? 40 : 24;
    s += rect(x, yy, w, h, { fill: 'rgba(5,7,10,0.92)', stroke: '#2A333D', sw: 1 }) + rect(x, yy, 4, h, { fill: r.nav ? C.magenta : C.kapton });
    s += text(x + 12, yy + 16.5, r.t, { size: TB, bold: true, fill: C.white }) + text(x + w - 8, yy + 16.5, r.left, { size: TB, bold: true, fill: r.late ? C.amber : C.white, anchor: 'end' });
    if (r.sub) s += text(x + 24, yy + 33, r.sub, { size: 11, bold: true, fill: r.nav ? C.magenta : C.dim, track: 0.4 });
    yy += h + 6;
  });
  return { svg: s, y: yy };
}

// NTF: news on comm-loop slips under the objectives.
function news(y, items) {
  const { x, w } = ZT.inc;
  return items.map(([tag, msg], i) => O.callout(x, y + i * 24, tag, '', msg, 'advisory', { w })).join('');
}

// HNT: keys for what can be done here, as caps with their verbs; dimmed once learned.
function hintLine(keys, o = {}) {
  const { x, y, w } = ZT.hnt;
  let s = '', total = keys.reduce((a, [k, v]) => a + k.length * 7 + 14 + v.length * 7.4 + 22, 0), kx = x + (w - total) / 2;
  keys.forEach(([k, v]) => {
    const kw = k.length * 7 + 12;
    s += softKey(kx, y + 2, kw, 17, false) + text(kx + kw / 2, y + 14.5, k, { size: 10, bold: true, fill: C.stencil, anchor: 'middle' });
    s += text(kx + kw + 6, y + 15, v, { size: 11.5, bold: true, fill: o.faded ? '#4C5864' : C.dim, track: 0.5 });
    kx += kw + 6 + v.length * 7.4 + 22;
  });
  return s;
}

// HLM: who has the helm and the standing order, as an autopilot mode strip (FMA style: the order
// boxed in green while it flies, white while it waits).
function helmStatus(x, y, w, who, order, state = 'engaged', note) {
  let s = rect(x - 1, y - 1, w + 2, 26, { fill: '#151719', r: 2 }) + rect(x, y, w, 24, { fill: C.glass, r: 1.5 });
  s += text(x + 10, y + 16.5, 'HELM', { size: 10, bold: true, fill: C.dim, track: 0.8 }) + text(x + 52, y + 16.5, who, { size: TB, bold: true, fill: C.white, track: 0.4 });
  const ow = order.length * 7.4 + 14;
  s += I.box(x + w - ow - 6, y + 4, ow, 16, order, state, { size: 10.5 });
  if (note) s += text(x + w - ow - 16, y + 16.5, note, { size: 10.5, bold: true, fill: C.dim, anchor: 'end', track: 0.5 });
  return s;
}

// Deck hazard tag: a glass slip on a leader from the hazard, its severity bar, what it is and who
// is working it (or NOBODY, in the caution colour). o.progress draws the job's bar under the name.
function hazardTag(ax, ay, x, y, sev, what, who, o = {}) {
  const col = sev === 'warning' ? C.red : C.amber, h = o.progress != null ? 28 : 22;
  const w = o.w || Math.round(what.length * 7.6 + who.length * 7.6 + 36);
  const ex = ax < x ? x : ax > x + w ? x + w : ax, ey = ay < y ? y : y + h;
  let s = haloed((c, ww) => line(ax, ay, ex, ey, c, ww), col, 1.2) + `<circle cx="${ax}" cy="${ay}" r="2.5" fill="${col}"/>`;
  s += rect(x, y, w, h, { fill: 'rgba(5,7,10,0.93)', stroke: col, sw: 1, r: 1.5 }) + rect(x, y, 5, h, { fill: col, r: 1 });
  s += text(x + 12, y + 15.5, what, { size: 11, bold: true, fill: col, track: 0.5 });
  s += text(x + w - 8, y + 15.5, who, { size: 11.5, bold: true, fill: who === 'NOBODY' ? C.amber : C.white, anchor: 'end', track: 0.3 });
  if (o.progress != null) s += rect(x + 12, y + 21, w - 20, 3, { fill: '#2A333D' }) + rect(x + 12, y + 21, (w - 20) * o.progress, 3, { fill: C.white });
  return s;
}
