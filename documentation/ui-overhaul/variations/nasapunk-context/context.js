// Nasa-punk x Context-driven: the pieces this combination adds to the kit.
// Zones from functional-context.md (version 2), section 5.1. Sizes follow its 12.4:
// body text at least 13 units, values at least 15, the threat alert at least 22.

const ZC = {
  strip: { x: 0, y: 0, w: 1280, h: 26 },
  ltop: { x: 12, y: 38, w: 308, h: 132 }, lmid: { x: 12, y: 176, w: 308, h: 376 }, lbot: { x: 12, y: 558, w: 308, h: 230 },
  topc: { x: 390, y: 38, w: 500, h: 174 }, centre: { x: 360, y: 220, w: 560, h: 370 }, botc: { x: 330, y: 598, w: 620, h: 190 },
  rtop: { x: 960, y: 38, w: 308, h: 132 }, rmid: { x: 960, y: 176, w: 308, h: 376 }, rbot: { x: 960, y: 558, w: 308, h: 230 },
};
const B = 13, V = 15; // body and value sizes

// A label and a value on one baseline: dim small-caps label, white value.
const lv = (x, y, label, val, o = {}) => text(x, y, label, { size: 11, bold: true, fill: C.dim, track: 0.6 })
  + text(x + (o.dx ?? label.length * 7.4 + 8), y, val, { size: o.size || V, bold: true, fill: o.fill || C.white, anchor: o.anchor || 'start' });

// P1, the glareshield: the one strip always on screen. Engraved hardware with glass windows.
function glareStrip(o) {
  let s = bezelBody(0, 0, 1280, 26, 0) + rect(0, 25, 1280, 1, { fill: '#0B0C0E' });
  const win = (x, w, t, fill = C.white, label) => {
    let r = '';
    if (label) r += text(x, 17.5, label, { size: 8.5, bold: true, fill: C.stencil, track: 1.2 });
    const gx = x + (label ? label.length * 6.6 + 8 : 0);
    r += rect(gx, 4, w, 18, { fill: C.glass, r: 1 }) + text(gx + 7, 17.5, t, { size: B, bold: true, fill, track: 0.5 });
    return r;
  };
  s += win(14, 132, o.seat, C.white, 'SEAT');
  if (o.authority) s += win(196, 372, o.authority, o.authCaution ? C.amber : C.white, 'HELM');
  if (o.battle) s += lens(620, 4, 148, 18, 'BATTLE STATIONS', C.amber);
  if (o.cw) s += o.cw;
  s += win(1000, 150, o.clock, C.white, 'MET');
  s += win(1196, 70, o.warp, o.warpHeld ? C.white : C.white, 'TW');
  return s;
}

// P2, the caution and warning list: an EICAS display. Single-line rows, warnings over cautions.
// rows: [sev, code, detail, crew (number on it, 0 = nobody), timer]
// Handling is drawn as crew dots, one per person on it (the same dot as the ship plan's crew);
// nobody on it is a boxed NO CREW flag in the row's colour.
function eicas(x, y, rows, o = {}) {
  const rh = o.more ? 19 : 21, n = rows.length + (o.more ? 1 : 0), h = 16 + 8 + n * rh + 4;
  return du(x, y, 308, h, {
    id: o.id || 'cw', b: 8, title: 'CAUTION  ·  WARNING',
    content: (gx, gy, gw) => {
      let c = '';
      rows.forEach(([sev, code, detail, crew, timer], i) => {
        const yy = gy + 18 + i * rh, col = sev === 'warning' ? C.red : C.amber;
        c += sev === 'warning' ? sym.diamond(gx + 10, yy - 4.5, 5, col, 1.5, col) : path(`M${gx + 10} ${yy - 10} L${gx + 15.5} ${yy} L${gx + 4.5} ${yy} Z`, col, 1.5, { fill: col });
        c += text(gx + 22, yy, code, { size: B, bold: true, fill: col, track: 0.4 });
        c += text(gx + 126, yy, detail, { size: B, fill: C.white });
        const hx = gx + 222;
        if (crew > 0) for (let k = 0; k < crew; k++) c += `<circle cx="${hx + 4 + k * 10}" cy="${yy - 4.5}" r="3.6" fill="${C.white}"/>`;
        else if (crew === 0) c += rect(hx - 2, yy - 12, 54, 15, { stroke: col, sw: 1.2 }) + text(hx + 25, yy - 1, 'NO CREW', { size: 9.5, bold: true, fill: col, anchor: 'middle', track: 0.4 });
        if (timer) c += text(gx + gw - 6, yy, timer, { size: B, bold: true, fill: C.white, anchor: 'end' });
      });
      if (o.more) c += text(gx + 22, gy + 18 + rows.length * rh, `+${o.more} MORE`, { size: 11.5, bold: true, fill: C.dim, track: 0.8 }) + text(gx + gw - 6, gy + 18 + rows.length * rh, 'SHIP › SYSTEMS', { size: 10, bold: true, fill: C.dim, anchor: 'end', track: 0.6 });
      return c;
    },
  }).svg;
}

// P3, the threat alert: one interrupt, a red warning lens and a glass with time to impact as a tape.
function threatAlert(o) {
  const { x, y } = ZC.topc, w = 500, h = 64;
  let s = rect(x - 2, y - 2, w + 4, h + 4, { fill: '#151719', r: 3 }) + rect(x, y, w, h, { fill: C.glass, stroke: C.red, sw: 2, r: 2 });
  s += lens(x + 6, y + 6, 92, h - 12, `MISSILE\n${o.count} IN`, C.red);
  s += text(x + 110, y + 28, `IMPACT  T−${o.tti}`, { size: 22, bold: true, fill: C.red, track: 0.6 });
  // the tape: time to impact against a 60 s scale, the pointer runs to the left as it nears
  const tx = x + 110, tw = 230, ty = y + 44;
  s += line(tx, ty, tx + tw, ty, C.red, 1.5);
  for (let k = 0; k <= 6; k++) s += line(tx + tw * k / 6, ty, tx + tw * k / 6, ty + (k % 3 === 0 ? 6 : 3), C.red, 1);
  const px = tx + tw * Math.min(o.ttiS, 60) / 60;
  s += path(`M${px} ${ty - 1} l-5 -8 h10 z`, C.red, 1, { fill: C.red });
  s += text(tx, ty + 17, '0', { size: 9.5, fill: C.dim }) + text(tx + tw, ty + 17, '60 S', { size: 9.5, fill: C.dim, anchor: 'end' });
  const R = x + w - 10;
  (o.lines || []).forEach((l, i) => s += text(R, y + 26 + i * 20, l, { size: B, bold: true, fill: C.white, anchor: 'end', track: 0.4 }));
  return s;
}

// P4, the message feed: comm-loop slips, newest on top, right-aligned in R-TOP.
function feed(items) {
  const { x, y, w } = ZC.rtop;
  return items.map(([tag, msg], i) => O.callout(x, y + i * 24, tag, '', msg, 'advisory', { w })).join('');
}

// P5, the objective: a strip of glass with a kapton tab, compact face: the heading on two lines
// (what, then where) and the time left.
function objective(x, y, w, tag, what, where, right) {
  const h = where ? 44 : 30;
  let s = rect(x, y, w, h, { fill: 'rgba(5,7,10,0.92)', stroke: '#2A333D', sw: 1 }) + rect(x, y, 4, h, { fill: C.kapton });
  s += text(x + 12, y + 20, tag, { size: 11, bold: true, fill: C.dim, track: 0.8 });
  s += text(x + 50, y + 20, what, { size: B, bold: true, fill: C.white });
  if (where) s += text(x + 50, y + 37, where, { size: B, bold: true, fill: C.white });
  if (right) s += text(x + w - 8, y + 20, right, { size: B, bold: true, fill: C.white, anchor: 'end' });
  return s;
}

// H4 compact: four annunciator lenses in a small bezel, unlit when nominal.
// lights: { HULL: null | 'caution' | 'warning', ... }
function condLights(x, y, lights, o = {}) {
  let s = bezelBody(x, y, 300, 40, 3) + fastener(x + 6, y + 6) + fastener(x + 294, y + 34);
  const names = ['HULL', 'SHIELDS', 'POWER', 'LIFE SUP'];
  names.forEach((n, i) => {
    const st = lights[n], lit = st === 'warning' ? C.red : st === 'caution' ? C.amber : null;
    s += lens(x + 12 + i * 70, y + 8, 66, 24, (lit ? (st === 'warning' ? '◆ ' : '▲ ') : '') + n + (lights[n + '_v'] ? '\n' + lights[n + '_v'] : ''), lit);
  });
  return s;
}

// The master alert cue (P2's behaviour) as hardware: a hood across the top of the seat's primary
// instrument with MASTER WARN and MASTER CAUT lenses at its ends, in the pilot's eyeline. A new
// warning lights WARN with its code; a new caution lights CAUT without one. Unlit otherwise.
function masterHood(x, y, w, cue, title) {
  let s = bezelBody(x + 6, y - 16, w - 12, 20, 3);
  const warn = cue && cue.sev === 'warning', caut = cue && cue.sev === 'caution';
  s += lens(x + 12, y - 13, 96, 14, warn ? '◆ ' + cue.code : 'MASTER WARN', warn ? C.red : null);
  s += lens(x + w - 108, y - 13, 96, 14, caut ? '▲ MASTER CAUT' : 'MASTER CAUT', caut ? C.amber : null);
  if (title) s += text(x + w / 2, y - 3, title, { size: 8.5, bold: true, fill: C.stencil, anchor: 'middle', track: 1.4 });
  return s;
}

// H1, the flight display: a primary-flight strip. Master warning and caution lenses sit in its
// bezel: the "master alert cue" in the pilot's eyeline (P2's behaviour).
function flightDisplay(x, y, o) {
  const w = 400, h = 100;
  let s = du(x, y, w, h, {
    id: 'h1', b: 10,
    content: (gx, gy, gw, gh) => {
      let c = '';
      const L = gx + 10, R = gx + gw - 10;
      // speed in the frame, with the frame's mode box (FMA style: green when automatic)
      c += I.box(L, gy + 8, 118, 20, o.frame, o.frameAuto ? 'engaged' : 'normal', { size: 11 });
      c += text(L + 128, gy + 25, o.speed, { size: 20, bold: true, fill: C.white }) + text(L + 128 + o.speed.length * 12.4, gy + 25, o.speedUnit, { size: 11, fill: C.dim });
      if (o.speedNote) c += text(L + 128 + o.speed.length * 12.4 + 34, gy + 25, o.speedNote, { size: B, bold: true, fill: C.white });
      c += I.box(R - 64, gy + 8, 64, 20, 'ASSIST', o.assist ? 'engaged' : 'caution', { size: 11 });
      c += line(gx + 4, gy + 36, gx + gw - 4, gy + 36, C.graticule, 1);
      // thrusters firing, as a pictogram; delta-v with the propellant share on a strip
      c += I.jets(L + 22, gy + 58, o.jets || {});
      c += text(L + 48, gy + 52, 'THRUST', { size: 9.5, bold: true, fill: C.dim, track: 0.6 }) + text(L + 48, gy + 67, o.thrustText || 'NONE', { size: 11, bold: true, fill: o.jets && Object.values(o.jets).some(Boolean) ? C.white : C.dim, track: 0.4 });
      c += text(L + 150, gy + 60, 'ΔV', { size: 11, bold: true, fill: C.dim, track: 0.6 }) + text(L + 226, gy + 61, o.dv, { size: V + 1, bold: true, fill: C.white, anchor: 'end' }) + text(L + 230, gy + 61, 'M/S', { size: 10, fill: C.dim });
      c += I.hbar(L + 266, gy + 62, 90, o.prop, {});
      c += text(L + 266 + 45, gy + 79, `PROP ${Math.round(o.prop * 100)}%`, { size: 9.5, bold: true, fill: C.dim, anchor: 'middle', track: 0.4 });
      return c;
    },
  }).svg;
  s += masterHood(x, y, w, o.cue, 'FLIGHT');
  return s;
}

// H5 collapsed: a single soft key with the count of contacts beyond.
function scopeKey(x, y, count) {
  let s = bezelBody(x, y, 48, 48, 4) + rect(x + 6, y + 6, 36, 26, { fill: C.glass });
  s += text(x + 24, y + 18, 'SCOPE', { size: 7.5, bold: true, fill: C.dim, anchor: 'middle', track: 0.6 }) + text(x + 24, y + 30, String(count), { size: 12, bold: true, fill: C.white, anchor: 'middle' });
  s += softKey(x + 11, y + 36, 26, 7, false);
  return s;
}

// D3, an off-screen pointer: a chevron on the screen edge, the glyph's code and the distance.
function offPointer(x, y, deg, label, sev) {
  const col = sev === 'warning' ? C.red : C.amber;
  return W.edge(x, y, deg, null, label, col);
}

// X3, the emergency card: a guarded emergency procedure, safety orange framing, the ways out with
// their keys and costs. The pending second press shows as an armed (boxed) key.
function emergencyCard(x, y, w, o) {
  const h = 30 + o.rows.length * 26 + 10;
  let s = rect(x - 2, y - 2, w + 4, h + 4, { fill: '#151719', r: 3 });
  s += `<defs><pattern id="hazard" width="12" height="12" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="6" height="12" fill="${C.orange}"/><rect x="6" width="6" height="12" fill="#1A1A1A"/></pattern></defs>`;
  s += rect(x, y, w, h, { fill: 'url(#hazard)', r: 2 }) + rect(x + 6, y + 6, w - 12, h - 12, { fill: C.glass });
  s += text(x + 18, y + 27, o.title, { size: 17, bold: true, fill: C.red, track: 0.8 });
  o.rows.forEach(([what, keys, cost], i) => {
    const yy = y + 54 + i * 26;
    s += text(x + 18, yy, what, { size: B, bold: true, fill: C.white, track: 0.4 });
    let kx = x + 196;
    keys.forEach(k => { s += softKey(kx, yy - 13, 22, 17, false) + text(kx + 11, yy, k, { size: 11, bold: true, fill: C.stencil, anchor: 'middle' }); kx += 26; });
    s += text(kx + 4, yy, 'TWICE', { size: 11, bold: true, fill: C.dim, track: 0.6 });
    if (cost) s += text(x + w - 18, yy, cost, { size: B, bold: true, fill: C.white, anchor: 'end' });
  });
  return s;
}
