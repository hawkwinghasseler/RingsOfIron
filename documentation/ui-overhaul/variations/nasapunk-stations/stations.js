// Nasa-punk x Ship stations: the pieces this combination adds to the kit.
// Zones from functional-stations.md (round 7), section 6.2. The band sits flush with the bottom
// edge (the style's choice): hardware stands on the bottom of the screen, it doesn't float.

const Z = {
  strip: { y: 16 }, annunciator: { x: 280, y: 44, w: 720 }, warp: { y: 84 }, ticker: { y: 108 },
  band: { x: 96, y: 616, w: 1088 },             // MFDs 288 x 160, cluster 480 x 160, 16 apart
  mfdL: { x: 96, y: 616 }, cluster: { x: 400, y: 616 }, mfdR: { x: 896, y: 616 },
  board: { x: 96, y: 780, w: 1088, h: 20 },
  prompt: { y: 584 },                           // 8 above the band seated; y 760 on deck
};
// The spec's floor (section 17): body text at least 13 units, annunciator text at least 12.
// Labels, keys and legends on glass stay at 12; hardware legends (bezel titles, plates) are exempt.
TEXT_FLOOR = 12;
const SB = 13;

// The annunciator: one row (7.1). Master lamps 56 x 36 at the ends, lit only while they flash;
// six two-line lens tiles of 94 x 36 between, filled from the left, warnings first.
// tiles: [[tag, value, 'warning'|'caution', {clearing}]]; cx: the column's centre.
function annunciator(cx, y, tiles, lamps = {}) {
  const tw0 = 94, gap = 6;
  let s = '', x = cx - 720 / 2 + 56 + gap;
  tiles.slice(0, 6).forEach(([tag, val, sev, o = {}]) => {
    const fill = sev === 'warning' ? C.red : C.amber;
    s += rect(x - 1.5, y - 1.5, tw0 + 3, 39, { fill: '#151719', r: 2.5 });
    if (o.clearing) s += rect(x, y, tw0, 36, { fill: C.glass, stroke: fill, sw: 1.5, r: 1.5 });
    else s += rect(x, y, tw0, 36, { fill, r: 1.5 }) + rect(x, y, tw0, 13, { fill: 'rgba(255,255,255,0.14)', r: 1.5 });
    const ink = o.clearing ? fill : '#0A0A0A';
    s += text(x + tw0 / 2, y + 15.5, tag, { size: 12, bold: true, fill: ink, anchor: 'middle', track: 0.8 });
    s += text(x + tw0 / 2, y + 30, val, { size: 12, bold: true, fill: ink, anchor: 'middle', track: 0.5 });
    x += tw0 + gap;
  });
  const half = 720 / 2;
  if (lamps.warning) s += lens(cx - half, y, 56, 36, 'MASTER\nWARN', C.red);
  if (lamps.caution) s += lens(cx + half - 56, y, 56, 36, 'MASTER\nCAUT', C.amber);
  return s;
}

// Callout ticker (7.2): up to three lines in the 720-wide column, left-aligned, newest at the
// bottom, at body size. lines: [tag, speaker, text, severity].
function ticker(x, y, lines) {
  return lines.map((l, i) => O.callout(x, y + i * 20, l[0], l[1], l[2], l[3], { fs: SB })).join('');
}

// Status strip (7.7): Z1s, top centre at y 16, 720 x 24. The condition lens at the left (dark in
// Green), BATTLE STATIONS beside it when called, the state lamp (DOCKED, LATCHED) at the right,
// and in Emergency the cause and the options across the middle. Dark when there is nothing.
function statusStrip(o = {}) {
  const x = o.cx ? o.cx - 360 : 280, y = 16, w = 720, h = 24;
  const condColor = { YELLOW: C.amber, RED: C.red }[o.condition];
  if (!condColor && !o.state && !o.emergency) return '';
  let s = rect(x - 1.5, y - 1.5, w + 3, h + 3, { fill: '#151719', r: 2.5 }) + rect(x, y, w, h, { fill: C.glass, r: 1.5 });
  if (condColor) {
    s += rect(x + 3, y + 3, 92, h - 6, { fill: condColor, r: 1.5 }) + rect(x + 3, y + 3, 92, 7, { fill: 'rgba(255,255,255,0.14)', r: 1.5 });
    s += text(x + 49, y + 16.5, o.condition, { size: 12, bold: true, fill: '#0A0A0A', anchor: 'middle', track: 1.2 });
    if (o.battle) s += text(x + 106, y + 16.5, 'BATTLE STATIONS', { size: 12, bold: true, fill: condColor, track: 1 });
  }
  if (o.emergency) s += text(x + w / 2 + 40, y + 16.5, o.emergency, { size: 12, bold: true, fill: C.red, anchor: 'middle', track: 0.4 });
  if (o.state) s += rect(x + w - 95, y + 3, 92, h - 6, { stroke: C.white, sw: 1.2, r: 1.5 }) + text(x + w - 49, y + 16.5, o.state, { size: 12, bold: true, fill: C.white, anchor: 'middle', track: 1 });
  return s;
}

// Station board (8.5): six lens keys of 150, then the chronometer (188) at the right end.
// tiles: [num, name, who, state, alert]; state 'you' | 'crew' | 'empty' | 'struck';
// alert 'caution' | 'warning' lights the lens in that role (a tile can be yours and alerting at once).
// o.dim: Green with nothing alerting, the whole board unlit, "you" included, legends low.
function stationBoard(tiles, o) {
  const { x, y, w, h } = Z.board;
  let s = bezelBody(x, y, w, h, 3);
  const tw0 = 146, ty = y + 3, th = h - 6, low = '#4A5058';
  tiles.forEach(([num, name, who, state, alert], i) => {
    const tx = x + 4 + i * 150;
    const lit = alert === 'caution' ? C.amber : alert === 'warning' ? C.red : null;
    s += rect(tx, ty, tw0, th, { fill: lit || C.lensOff, r: 1.5 });
    if (lit) s += rect(tx, ty, tw0, th * 0.4, { fill: 'rgba(255,255,255,0.14)', r: 1.5 });
    const ink = lit ? '#0A0A0A' : o.dim ? low : state === 'empty' || state === 'struck' ? C.lensLegend : C.stencil;
    s += text(tx + 7, ty + 11.5, String(num), { size: 12, bold: true, fill: lit ? '#0A0A0A' : o.dim ? low : C.lensLegend });
    s += text(tx + 22, ty + 11.5, (lit ? (alert === 'warning' ? '◆ ' : '▲ ') : '') + name, { size: 12, bold: true, fill: ink, track: 0.6 });
    const whoX = tx + tw0 - 4;
    if (state === 'you' && !o.dim) s += rect(whoX - 34, ty + 1, 34, th - 2, { fill: C.cyan, r: 1 }) + text(whoX - 17, ty + 11, 'YOU', { size: 12, bold: true, fill: '#05070A', anchor: 'middle', track: 0.3 });
    else s += text(whoX - 3, ty + 11.5, state === 'you' ? 'YOU' : who, { size: 12, bold: true, fill: ink, anchor: 'end', track: 0.3 });
    if (state === 'struck') s += line(tx + 4, ty + th / 2, tx + tw0 - 4, ty + th / 2, C.lensLegend, 1.5);
  });
  // right end: the chronometer, a small glass
  const rx = x + 4 + 6 * 150, rw = w - 8 - 6 * 150;
  s += rect(rx, ty, rw, th, { fill: C.glass, r: 1 });
  s += text(rx + 8, ty + 11.5, o.day, { size: 12, bold: true, fill: o.dim ? low : C.dim, track: 0.4 }) + text(rx + rw - 8, ty + 11.5, o.time, { size: 12, bold: true, fill: o.dim ? '#7E8792' : C.white, anchor: 'end' });
  return s;
}

// A folded MFD: its soft-key rail only, 288 x 24, keys lit by their legends.
function foldedRail(x, y, legends) {
  let s = bezelBody(x, y + 136, 288, 24, 3);
  s += rect(x + 6, y + 138, 276, 16, { fill: C.glass });
  const n = legends.length, step = 276 / n;
  legends.forEach((L, i) => {
    const l = typeof L === 'string' ? { t: L } : L, cx = x + 6 + step * (i + 0.5);
    const fill = l.state === 'on' ? C.green : l.state === 'cur' ? C.white : C.dim;
    s += text(cx, y + 150.5, l.t, { size: 12, bold: true, fill, anchor: 'middle', track: 0.5 });
    if (l.state === 'on') { const w0 = tw(l.t, 12, true, 0.5) + 8; s += rect(cx - w0 / 2, y + 139, w0, 14, { stroke: C.green, sw: 1 }); }
    s += softKey(cx - 13, y + 155.5, 26, 3, false);
  });
  return s;
}

// An open MFD: 288 x 160, page keys along the bottom edge (page legends, then PIN and FOLD).
function mfd(x, y, id, keys, content, o = {}) {
  return du(x, y, 288, 160, { id, b: 8, keys: { bottom: keys }, content, title: o.title }).svg;
}

// Page header inside an MFD glass: page name left, status right.
function pageHead(gx, gy, gw, name, status, statusColor = C.dim) {
  return text(gx + 6, gy + 13, name, { size: 12, bold: true, fill: C.white, track: 0.8 })
    + (status ? text(gx + gw - 6, gy + 13, status, { size: 12, bold: true, fill: statusColor, anchor: 'end', track: 0.4 }) : '')
    + line(gx + 4, gy + 18, gx + gw - 4, gy + 18, C.graticule, 1);
}

// Learning hints on the prompt line (7.3): at most three key caps with verbs, quiet.
function promptHints(cx, y, hints) {
  const kwOf = k => tw(k, 12, true) + 10, wOf = ([k, t]) => kwOf(k) + 6 + tw(t, 12, true, 0.5);
  let total = hints.reduce((a, h) => a + wOf(h) + 18, -18), hx = cx - total / 2, s = '';
  hints.forEach(([k, t]) => { const kw = kwOf(k); s += softKey(hx, y + 3, kw, 17, false) + text(hx + kw / 2, y + 16, k, { size: 12, bold: true, fill: C.stencil, anchor: 'middle' }) + text(hx + kw + 6, y + 16, t, { size: 12, bold: true, fill: C.dim, track: 0.5 }); hx += wOf([k, t]) + 18; });
  return s;
}
