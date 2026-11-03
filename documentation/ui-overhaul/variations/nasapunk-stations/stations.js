// Nasa-punk x Ship stations: the pieces this combination adds to the kit.
// Zones from functional-stations.md (round 3), section 6.2. The band sits flush with the bottom
// edge (the style's choice in round 3): hardware stands on the bottom of the screen, it doesn't float.

const Z = {
  annunciator: { x: 280, y: 44, w: 720 },       // top column: banner 16, tiles 44 and 84, warp 124, ticker 148
  ticker: { y: 148 },
  band: { x: 96, y: 616, w: 1088 },             // MFDs 288 x 160, cluster 480 x 160, 16 apart
  mfdL: { x: 96, y: 616 }, cluster: { x: 400, y: 616 }, mfdR: { x: 896, y: 616 },
  board: { x: 96, y: 780, w: 1088, h: 20 },
  prompt: { y: 584 },                           // 8 above the band
};

// The annunciator: master lamps at the ends (only when lit), two-line lens tiles 94 x 36 between.
// tiles: [[tag, value, 'warning'|'caution', {clearing}]]; cx: the column's centre.
function annunciator(cx, y, tiles, lamps = {}) {
  // Fixed places: the lamps keep their ends, tiles fill from the left in priority order,
  // so the eye learns where the worst problem will be.
  const tw = 94, gap = 6;
  let s = '', x = cx - 720 / 2 + 56 + gap;
  tiles.slice(0, 6).forEach(([tag, val, sev, o = {}]) => {
    const fill = sev === 'warning' ? C.red : C.amber;
    s += rect(x - 1.5, y - 1.5, tw + 3, 39, { fill: '#151719', r: 2.5 });
    if (o.clearing) s += rect(x, y, tw, 36, { fill: C.glass, stroke: fill, sw: 1.5, r: 1.5 });
    else s += rect(x, y, tw, 36, { fill, r: 1.5 }) + rect(x, y, tw, 13, { fill: 'rgba(255,255,255,0.14)', r: 1.5 });
    const ink = o.clearing ? fill : '#0A0A0A';
    s += text(x + tw / 2, y + 15, tag, { size: 12, bold: true, fill: ink, anchor: 'middle', track: 0.8 });
    s += text(x + tw / 2, y + 30, val, { size: 12, bold: true, fill: ink, anchor: 'middle', track: 0.5 });
    x += tw + gap;
  });
  const half = 720 / 2;
  if (lamps.warning) s += lens(cx - half, y, 56, 36, 'MASTER\nWARN', C.red);
  if (lamps.caution) s += lens(cx + half - 56, y, 56, 36, 'MASTER\nCAUT', C.amber);
  return s;
}

// Callout ticker: up to three lines in the 720-wide column, left-aligned, newest at the bottom.
function ticker(x, y, lines) {
  return lines.map((l, i) => O.callout(x, y + i * 22, ...l)).join('');
}

// Station board (round 3): six lens keys of 136, then the right end (272): chronometer, state lamp,
// condition. tiles: [num, name, who, state, alert]; state 'you' | 'crew' | 'empty' | 'struck';
// alert 'caution' | 'warning' lights the lens in that role (a tile can be yours and alerting at once).
function stationBoard(tiles, o) {
  const { x, y, w, h } = Z.board;
  let s = bezelBody(x, y, w, h, 3);
  const condColor = { GREEN: null, YELLOW: C.amber, RED: C.red }[o.condition];
  if (condColor) s += rect(x + 4, y - 3, w - 8, 2, { fill: condColor });
  const tw = 132, ty = y + 3, th = h - 6;
  tiles.forEach(([num, name, who, state, alert], i) => {
    const tx = x + 4 + i * 136;
    const lit = alert === 'caution' ? C.amber : alert === 'warning' ? C.red : null;
    s += rect(tx, ty, tw, th, { fill: lit || C.lensOff, r: 1.5 });
    if (lit) s += rect(tx, ty, tw, th * 0.4, { fill: 'rgba(255,255,255,0.14)', r: 1.5 });
    const ink = lit ? '#0A0A0A' : state === 'empty' || state === 'struck' ? C.lensLegend : C.stencil;
    s += text(tx + 7, ty + 10.5, String(num), { size: 9, bold: true, fill: lit ? '#0A0A0A' : C.lensLegend });
    s += text(tx + 18, ty + 10.5, (lit ? (alert === 'warning' ? '◆ ' : '▲ ') : '') + name, { size: 9.5, bold: true, fill: ink, track: 0.8 });
    const whoX = tx + tw - 4;
    if (state === 'you') s += rect(whoX - 30, ty + 2, 30, th - 4, { fill: C.cyan, r: 1 }) + text(whoX - 15, ty + 10.5, 'YOU', { size: 8.5, bold: true, fill: '#05070A', anchor: 'middle', track: 0.6 });
    else s += text(whoX - 3, ty + 10.5, who, { size: 9.5, bold: true, fill: ink, anchor: 'end', track: 0.4 });
    if (state === 'struck') s += line(tx + 4, ty + th / 2, tx + tw - 4, ty + th / 2, C.lensLegend, 1.5);
  });
  // right end: chronometer (a small glass), the state lamp (unlit when no state holds), condition
  const rx = x + 820;
  s += rect(rx, ty, 128, th, { fill: C.glass, r: 1 });
  s += text(rx + 8, ty + 10.5, o.day, { size: 9.5, bold: true, fill: C.dim, track: 0.6 }) + text(rx + 120, ty + 11, o.time, { size: 11, bold: true, fill: C.white, anchor: 'end' });
  s += rect(rx + 132, ty, 60, th, { fill: o.lamp === 'EMERG' ? C.red : C.lensOff, r: 1.5 });
  if (o.lamp) s += text(rx + 162, ty + 10.5, o.lamp, { size: 9.5, bold: true, fill: o.lamp === 'EMERG' ? '#0A0A0A' : C.white, anchor: 'middle', track: 1 });
  s += rect(rx + 196, ty, 68, th, { fill: condColor || C.lensOff, r: 1.5 });
  if (condColor) s += rect(rx + 196, ty, 68, th * 0.4, { fill: 'rgba(255,255,255,0.14)', r: 1.5 });
  s += text(rx + 230, ty + 10.5, o.condition, { size: 9.5, bold: true, fill: condColor ? '#0A0A0A' : C.stencil, anchor: 'middle', track: 1 });
  return s;
}

// A folded MFD: its soft-key rail only, 288 x 24, keys lit by their legends.
function foldedRail(x, y, legends) {
  let s = bezelBody(x, y + 136, 288, 24, 3);
  s += rect(x + 6, y + 139, 276, 14, { fill: C.glass });
  const n = legends.length, step = 276 / n;
  legends.forEach((L, i) => {
    const l = typeof L === 'string' ? { t: L } : L, cx = x + 6 + step * (i + 0.5);
    const fill = l.state === 'on' ? C.green : l.state === 'cur' ? C.white : C.dim;
    s += text(cx, y + 150, l.t, { size: 10.5, bold: true, fill, anchor: 'middle', track: 0.6 });
    if (l.state === 'on') s += rect(cx - l.t.length * 3.9 - 4, y + 140, l.t.length * 7.8 + 8, 12, { stroke: C.green, sw: 1 });
    s += softKey(cx - 13, y + 155, 26, 3, false);
  });
  return s;
}

// An open MFD: 288 x 160, page keys along the bottom edge (page legends, then PIN and FOLD).
function mfd(x, y, id, keys, content, o = {}) {
  return du(x, y, 288, 160, { id, b: 8, keys: { bottom: keys }, content, title: o.title }).svg;
}

// Page header inside an MFD glass: page name left, status right.
function pageHead(gx, gy, gw, name, status, statusColor = C.dim) {
  return text(gx + 6, gy + 12, name, { size: 11, bold: true, fill: C.white, track: 0.8 })
    + (status ? text(gx + gw - 6, gy + 12, status, { size: 10, bold: true, fill: statusColor, anchor: 'end', track: 0.5 }) : '')
    + line(gx + 4, gy + 17, gx + gw - 4, gy + 17, C.graticule, 1);
}
