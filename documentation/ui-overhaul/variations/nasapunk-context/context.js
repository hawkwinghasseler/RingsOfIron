// Nasa-punk x Context-driven: the pieces this combination adds to the kit.
// Zones from functional-context.md (version 4), section 5.1. Sizes follow its 12.4: labels (legends,
// units, the key names on caps) at least 12 units, body text 13, values 15, P3's first line 22, and
// anything that can be clicked at least 24 in its smaller dimension. That floor leaves no room for
// bezel titles, so a widget here is known by its zone and its face, not by a title.

TEXT_FLOOR = 12;
const ZC = {
  strip: { x: 0, y: 0, w: 1280, h: 26 },
  ltop: { x: 12, y: 38, w: 308, h: 150 }, lmid: { x: 12, y: 194, w: 308, h: 358 }, lbot: { x: 12, y: 558, w: 308, h: 230 },
  topc: { x: 390, y: 38, w: 500, h: 174 }, centre: { x: 360, y: 220, w: 560, h: 370 }, botc: { x: 330, y: 598, w: 620, h: 190 },
  rtop: { x: 960, y: 38, w: 308, h: 150 }, rmid: { x: 960, y: 194, w: 308, h: 358 }, rbot: { x: 960, y: 558, w: 308, h: 230 },
};
const LB = 12, B = 13, V = 15; // label, body and value sizes
// Thousands take a thin space, never a comma (the style guide, 4).
const num = n => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');

// ---------- text pieces ----------

// A label: dim bold caps at 12.
const lab = (x, y, t, o = {}) => text(x, y, t, { size: LB, bold: true, fill: o.fill || C.dim, track: 0.6, anchor: o.anchor || 'start' });
// A value with its unit in dim at 12, a few units after it; anchor 'start' (default) or 'end'.
function val(x, y, v, unit, o = {}) {
  const size = o.size || V, vw = tw(v, size, true), uw = unit ? 4 + tw(unit, LB, true, 0.3) : 0;
  const x0 = o.anchor === 'end' ? x - vw - uw : x;
  return text(x0, y, v, { size, bold: true, fill: o.fill || C.white }) + (unit ? text(x0 + vw + 4, y, unit, { size: LB, bold: true, fill: C.dim, track: 0.3 }) : '');
}
const valW = (v, unit, size = V) => tw(v, size, true) + (unit ? 4 + tw(unit, LB, true, 0.3) : 0);
// A keyboard key, drawn as a cap with its name on it (a hint, not a button).
const capW = k => tw(k, LB, true) + 10;
const capKey = (x, y, k, h = 17) => softKey(x, y, capW(k), h, false) + text(x + capW(k) / 2, y + h / 2 + 4.3, k, { size: LB, bold: true, fill: C.stencil, anchor: 'middle' });

// ---------- P1, the status strip ----------
// One long glass across the top. Fields sit at fixed places with their widths reserved (the spec's
// 5.3), so a field that empties leaves dark glass and nothing beside it moves.
function statusStrip(o) {
  let s = bezelBody(0, 0, 1280, 26, 0) + rect(0, 25, 1280, 1, { fill: '#0B0C0E' }) + rect(4, 3, 1272, 20, { fill: C.glass, r: 1 });
  const y = 17.7;
  s += text(14, y, o.seat, { size: B, bold: true, fill: C.white, track: 0.6 });                 // 14 characters
  if (o.authority) s += text(158, y, o.authority, { size: B, fill: C.white, track: 0.3 });     // 36, to x 474
  if (o.battle) s += lens(486, 5, 142, 16, 'BATTLE STATIONS', C.amber);
  if (o.paused) s += lens(752, 5, 64, 16, 'PAUSED', 'advisory');
  s += text(966, y, o.clock, { size: B, bold: true, fill: C.white, anchor: 'end', track: 0.4 }); // 16, from x 830
  s += text(1266, y, o.warp, { size: B, bold: true, fill: C.white, anchor: 'end', track: 0.4 }); // 34, from x 979
  return s;
}

// ---------- P2, the caution and warning panel ----------
// An EICAS display of rows 24 tall, five fields at fixed places (the spec's row anatomy):
// severity shape, code, detail, handling, timer. rows: [sev, code, detail, handling, timer] with
// handling 'you' (a cyan YOU flag: it needs the captain), a number (a dot per crew member on it),
// or 'back' (it clears by itself: BACK and the time until it does). o.more: the hidden codes' line.
function cwPanel(rows, o = {}) {
  const { x, y } = ZC.ltop, rh = 24, shown = rows.slice(0, o.more ? 5 : 6), n = shown.length + (o.more ? 1 : 0);
  return du(x, y, 308, 12 + n * rh, {
    id: 'p2', b: 6,
    content: (gx, gy, gw) => {
      let c = '';
      const row = (i, f) => { const top = gy + i * rh; if (i) c += line(gx + 4, top, gx + gw - 4, top, C.graticule, 1); f(top, top + 16.7); };
      shown.forEach(([sev, code, detail, hand, timer], i) => row(i, (top, yy) => {
        const col = sev === 'warning' ? C.red : C.amber;
        c += sev === 'warning' ? sym.diamond(gx + 11, yy - 4.7, 4.6, col, 1.2, col) : path(`M${gx + 11} ${yy - 10.5} L${gx + 16.6} ${yy} L${gx + 5.4} ${yy} Z`, col, 1.2, { fill: col });
        c += text(gx + 22, yy, code, { size: B, bold: true, fill: col, track: 0.3 });
        if (detail) c += text(gx + 118, yy, detail, { size: B, fill: C.white, track: 0.2 });
        const hx = gx + 206;
        if (hand === 'you') c += rect(hx, top + 4, 38, 16, { stroke: C.cyan, sw: 1.2 }) + text(hx + 19, yy - 0.5, 'YOU', { size: LB, bold: true, fill: C.cyan, anchor: 'middle', track: 0.4 });
        else if (hand === 'back') c += text(hx, yy, 'BACK', { size: LB, bold: true, fill: C.dim, track: 0.6 });
        else if (hand >= 4) c += `<circle cx="${hx + 6}" cy="${yy - 4.5}" r="3.8" fill="${C.white}"/>` + text(hx + 13, yy, String(hand), { size: B, bold: true, fill: C.white });
        else if (hand > 0) for (let k = 0; k < hand; k++) c += `<circle cx="${hx + 6 + k * 11}" cy="${yy - 4.5}" r="3.8" fill="${C.white}"/>`;
        // a harm timer counts down to harm, in white; a recovery timer is BACK's, in dim
        if (timer) c += text(gx + gw - 6, yy, timer, { size: B, bold: true, fill: hand === 'back' ? C.dim : C.white, anchor: 'end' });
      }));
      // the overflow row opens Ship › Systems at the damage control board
      if (o.more) row(shown.length, (top, yy) => {
        c += text(gx + 22, yy, `+${o.more.length} MORE: ${o.more.join(', ')}`, { size: B, bold: true, fill: C.dim, track: 0.3 });
        c += path(`M${gx + gw - 14} ${top + 7} l5 5 l-5 5`, C.dim, 1.6);
      });
      return c;
    },
  }).svg;
}

// The master alert cue (P2's) on deck, docked or latched: the hood's two lenses standing alone at
// the centre of the bottom-centre zone. Drawn only while one is lit; dark there, it would be
// hardware with nothing to say over the deck.
function masterCue(cx, y, cue) {
  const lw = 132, w = 2 * lw + 22;
  let s = bezelBody(cx - w / 2, y, w, 36, 3);
  s += lens(cx - w / 2 + 7, y + 6, lw, 24, cue.sev === 'warning' ? '◆ ' + cue.code : 'MASTER WARN', cue.sev === 'warning' ? C.red : null);
  s += lens(cx + w / 2 - 7 - lw, y + 6, lw, 24, cue.sev === 'caution' ? '▲ ' + cue.code : 'MASTER CAUT', cue.sev === 'caution' ? C.amber : null);
  return s;
}
// The same two lenses as a hood across the top of the seat's primary instrument (H1, H3, F2), in
// the pilot's eyeline. Lit, a lens shows the newest code; a click on it acknowledges.
function masterHood(x, y, w, cue) {
  const lw = 132;
  let s = bezelBody(x + 4, y - 30, w - 8, 32, 3);
  const warn = cue && cue.sev === 'warning', caut = cue && cue.sev === 'caution';
  s += lens(x + 11, y - 27, lw, 24, warn ? '◆ ' + cue.code : 'MASTER WARN', warn ? C.red : null);
  s += lens(x + w - 11 - lw, y - 27, lw, 24, caut ? '▲ ' + cue.code : 'MASTER CAUT', caut ? C.amber : null);
  return s;
}

// ---------- P3, the threat alert ----------
// The one interrupt: a red-framed glass with a lit lamp. line1 at 22 (the count, and at a seat
// without F4 the soonest impact); parts of line 2: strings, or {key} for a key cap.
function threatAlert(line1, parts) {
  const { x, y } = ZC.topc, w = 500, h = 60;
  let s = rect(x - 2, y - 2, w + 4, h + 4, { fill: '#151719', r: 3 }) + rect(x, y, w, h, { fill: C.glass, r: 2 });
  s += path(`M${x + 1} ${y + 1} H${x + w - 1} V${y + h - 1} H${x + 1} Z`, C.red, 2);
  s += rect(x + 6, y + 6, 46, h - 12, { fill: C.red, r: 1.5 }) + rect(x + 6, y + 6, 46, 15, { fill: 'rgba(255,255,255,0.14)', r: 1.5 });
  s += path(`M${x + 29} ${y + 15} L${x + 41} ${y + 41} L${x + 29} ${y + 34} L${x + 17} ${y + 41} Z`, '#0A0A0A', 1.5, { fill: '#0A0A0A' });
  s += text(x + 66, y + 29, line1, { size: 22, bold: true, fill: C.red, track: 0.5 });
  let px = x + 66;
  parts.forEach(p => {
    if (p.key) { s += capKey(px, y + 37, p.key, 17); px += capW(p.key) + 6; }
    else { s += text(px, y + 50, p, { size: B, bold: true, fill: C.white, track: 0.4 }); px += tw(p, B, true, 0.4) + 6; }
  });
  return s;
}

// ---------- P4, the message feed ----------
// Comm-loop slips, newest on top: a source tag, then the message in sentence case.
function feed(items) {
  const { x, y, w } = ZC.rtop;
  return items.map(([tag, msg, sev], i) => O.callout(x, y + i * 24, tag, '', msg, sev || 'advisory', { w, fs: B })).join('');
}

// ---------- P5, the objective ----------
// A strip of glass with a kapton tab. Compact face: the job, then where and the time left.
function objective(lines, y = ZC.lmid.y) {
  const { x } = ZC.lmid, w = 308, h = 14 + lines.length * 19;
  let s = rect(x, y, w, h, { fill: 'rgba(5,7,10,0.92)', stroke: '#2A333D', sw: 1 }) + rect(x, y, 4, h, { fill: C.kapton });
  lines.forEach((l, i) => s += text(x + 14, y + 22 + i * 19, l, { size: B, bold: i === 0, fill: C.white, track: 0.3 }));
  return { svg: s, h };
}

// ---------- H1, the flight display ----------
// A primary-flight strip under its hood. Top row: the frame as an FMA box (green: picked
// automatically; white: pinned; a click pins it, so the box is 24 tall), the speed in that frame,
// rotation assist. Bottom row: the thrusters firing, delta-v left, the propellant share.
function flightDisplay(x, y, o) {
  const w = 400, h = 100;
  let s = masterHood(x, y, w, o.cue);
  s += du(x, y, w, h, {
    id: 'h1', b: 10,
    content: (gx, gy, gw) => {
      let c = '';
      const L = gx + 8, R = gx + gw - 8;
      c += I.box(L, gy + 4, 126, 24, o.frame, o.frameAuto ? 'engaged' : 'normal', { size: LB });
      c += val(L + 140, gy + 23, o.speed, 'M/S', { size: 20 });
      c += I.box(R - 78, gy + 4, 78, 24, o.assist ? 'ASSIST' : 'ASSIST OFF', o.assist ? 'engaged' : 'caution', { size: LB });
      c += line(gx + 4, gy + 36, gx + gw - 4, gy + 36, C.graticule, 1);
      const firing = o.jets && Object.values(o.jets).some(Boolean);
      c += I.jets(L + 16, gy + 58, o.jets || {});
      c += lab(L + 40, gy + 53, 'THRUST') + text(L + 40, gy + 71, o.thrust || 'NONE', { size: B, bold: true, fill: firing ? C.white : C.dim, track: 0.4 });
      c += lab(L + 132, gy + 53, 'ΔV') + val(L + 132, gy + 72, o.dv, 'M/S');
      const bx = L + 236, bw = R - bx;
      c += lab(bx, gy + 53, 'PROP') + val(R, gy + 53, Math.round(o.prop * 100) + '%', null, { anchor: 'end', size: B });
      c += I.hbar(bx, gy + 70, bw, o.prop, { bands: [[0, 0.1, C.red], [0.1, 0.2, C.amber]] });
      return c;
    },
  }).svg;
  return s;
}

// ---------- H4, ship condition ----------
// Compact: the status lights in a small bezel, unlit while nominal (the dark cockpit), lit by
// severity with the severity's shape. names: the ship's lights (three on a ship with no emitter).
function condLights(x, y, names, lit = {}) {
  let s = bezelBody(x, y, 308, 40, 3);
  const n = names.length, gap = 6, lw = (308 - 16 - gap * (n - 1)) / n;
  names.forEach((nm, i) => {
    const st = lit[nm], col = st === 'warning' ? C.red : st === 'caution' ? C.amber : null;
    s += lens(x + 8 + i * (lw + gap), y + 8, lw, 24, (col ? (st === 'warning' ? '◆ ' : '▲ ') : '') + nm, col);
  });
  return s;
}

// ---------- H5, the contact scope ----------
// 200 x 200, north up; − and + set the range by hand (24 each). contacts as in I.scope, in -1..1
// of the range; off-scale ones get a tick on the rim at their bearing (o.rim: [[deg, kind]]).
function contactScope(contacts, o) {
  const x = 1268 - 200, y = 788 - 200;
  return du(x, y, 200, 200, {
    id: 'h5', b: 8,
    content: (gx, gy, gw, gh) => {
      const cx = gx + gw / 2, cy = gy + gh / 2 - 4, r = 78;
      let c = I.scope(cx, cy, r, contacts, { heading: o.heading });
      (o.rim || []).forEach(([deg, kind]) => {
        const a = deg * Math.PI / 180, col = kind === 'hostile' ? C.red : C.white;
        c += line(cx + (r - 2) * Math.sin(a), cy - (r - 2) * Math.cos(a), cx + (r + 7) * Math.sin(a), cy - (r + 7) * Math.cos(a), col, 2.5);
      });
      const by = gy + gh - 28;
      [['−', gx + 4], ['+', gx + 32]].forEach(([k, bx]) => { c += rect(bx, by, 24, 24, { fill: '#0E141A', stroke: '#3A434D', sw: 1 }) + text(bx + 12, by + 17.5, k, { size: V, bold: true, fill: C.white, anchor: 'middle' }); });
      c += val(gx + gw - 6, gy + gh - 10, o.range, 'KM', { anchor: 'end', size: B });
      return c;
    },
  }).svg;
}
// Collapsed (cruise with nothing inside 50 km): a 48 x 48 key showing the count of contacts beyond.
function scopeKey(count) {
  const x = 1268 - 48, y = 788 - 48;
  let s = bezelBody(x, y, 48, 48, 4) + rect(x + 5, y + 5, 38, 38, { fill: C.glass });
  s += `<circle cx="${x + 24}" cy="${y + 24}" r="15" fill="none" stroke="${C.graticule}" stroke-width="1.2"/>` + line(x + 9, y + 24, x + 39, y + 24, C.graticule, 1) + line(x + 24, y + 9, x + 24, y + 39, C.graticule, 1);
  s += text(x + 24, y + 29.5, String(count), { size: V, bold: true, fill: C.white, anchor: 'middle' });
  return s;
}

// ---------- the ships, from outside ----------
// The game's hull outlines (64 units a tile, nose toward +x), and each outline's extent in x.
const HULL_ART = {
  corvette: { d: 'M' + CORVETTE.hull.replace(/ /g, ' L') + ' Z', x0: -780.8, x1: 460.8 },
  gunship: { d: 'M-640 57.6L-704 44.8L-704 -44.8L-640 -57.6L-640 -256L102.4 -256L134.4 -294.4L294.4 -294.4L326.4 -256L640 -256L678.4 -300.8L748.8 -288L819.2 -128L844.8 0L819.2 128L748.8 288L678.4 300.8L640 256L326.4 256L294.4 294.4L134.4 294.4L102.4 256L-640 256Z', x0: -704, x1: 844.8 },
  raider: { d: 'M-480 -160L-556.8 -275.2L-486.4 -275.2L-307.2 -160L480 -160L665.6 -57.6L588.8 0L665.6 57.6L480 160L-307.2 160L-486.4 275.2L-556.8 275.2L-480 160Z', x0: -556.8, x1: 665.6 },
};
// A hull in plate grey: the outline, an inner seam, the spine and the bridge window. (x, y) is the
// centre of the outline's length; hdg in degrees clockwise from the nose pointing right.
// o.side: own | hostile | beaten; o.outline: the outline alone (a synoptic's ship); o.raw: the
// drawing in the hull's own units, untransformed, for placing one hull against another.
function hullArt(kind, x, y, hdg, k, o = {}) {
  const H = HULL_ART[kind], u = 1 / k, cx = (H.x0 + H.x1) / 2;
  if (o.outline) return `<g transform="translate(${x} ${y}) rotate(${hdg}) scale(${k}) translate(${-cx} 0)"><path d="${H.d}" fill="none" stroke="${o.stroke || C.dim}" stroke-width="${(o.sw || 1.2) * u}"/></g>`;
  const fill = { own: '#5C626B', hostile: '#6B5D5A', beaten: '#47423F' }[o.side || 'own'];
  let g = `<path d="${H.d}" fill="${fill}" stroke="#202327" stroke-width="${1.4 * u}"/>`;
  g += `<path d="${H.d}" fill="none" stroke="rgba(255,255,255,0.10)" stroke-width="${1.2 * u}" transform="translate(${cx} 0) scale(0.86 0.66) translate(${-cx} 0)"/>`;
  g += line(H.x0 + 90, 0, H.x1 - 150, 0, 'rgba(0,0,0,0.28)', 1.4 * u);
  g += rect(H.x1 - 190, -34, 70, 68, { fill: o.side === 'beaten' ? '#151719' : '#1B2A36', r: 6 });
  if (o.raw) return g;
  return `<g transform="translate(${x} ${y}) rotate(${hdg}) scale(${k}) translate(${-cx} 0)">${g}</g>`;
}

// ---------- controls: C1's orders, and the same guarded orders on X3 ----------
// Patterns for the marks: the barber pole (an order that can't be given now) and a guard's hazard rim.
function controlDefs() {
  return `<defs><pattern id="barber" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="3" height="6" fill="#D6D8D2"/><rect x="3" width="3" height="6" fill="#16181B"/></pattern>`
    + `<pattern id="guardrim" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="4" height="8" fill="${C.orange}"/><rect x="4" width="4" height="8" fill="#1A1A1A"/></pattern></defs>`;
}
// The barber-pole strip: on a button's foot it says the order can't be given now; at the menu's
// foot it heads the reason.
const barber = (x, y, w = 14) => rect(x, y, w, 4, { fill: 'url(#barber)' });
// A lit push button with a two-line legend, the order and then its state. state: 'idle' (legend
// engraved, unlit), 'on' (the state lit green with a bar under it: the order is running), 'na'
// (it can't be given now: legend grey, a barber-pole strip along its foot), 'armed' (a guarded
// order's first press taken: the state lit amber). o.name: both lines are the order's name (a
// helm order, ABANDON SHIP), so running lights the whole legend instead of a state line.
function korry(x, y, w, h, l1, l2, state = 'idle', o = {}) {
  const na = state === 'na', lit = state === 'on' ? C.green : state === 'armed' ? C.amber : null;
  const ink = na ? C.dim : o.name && lit ? lit : '#D2D5CF';
  let s = rect(x - 1.5, y - 1.5, w + 3, h + 3, { fill: '#121416', r: 2.5 }) + rect(x, y, w, h, { fill: '#1F2226', r: 1.5 }) + rect(x, y, w, h * 0.45, { fill: 'rgba(255,255,255,0.04)', r: 1.5 });
  s += text(x + w / 2, l2 ? y + h / 2 - 3 : y + h / 2 + 4.3, l1, { size: LB, bold: true, fill: ink, anchor: 'middle', track: 0.4 });
  if (l2) s += text(x + w / 2, y + h / 2 + 11, l2, { size: LB, bold: true, fill: o.name ? ink : lit || C.dim, anchor: 'middle', track: 0.4 });
  if (lit) s += rect(x + 5, y + h - 5, w - 10, 2.5, { fill: lit });
  if (na) s += barber(x + 5, y + h - 6, w - 10);
  return s;
}
// A guarded order: the button under a hinged clear cover, its rim striped. Lifting the guard is
// the first press; open (o.open), the cover stands on its hinge above the button, seen edge on.
function guarded(x, y, w, h, l1, l2, state, o = {}) {
  let s = rect(x - 5, y - 5, w + 10, h + 10, { fill: 'url(#guardrim)', r: 3 });
  s += korry(x, y, w, h, l1, l2, state, o);
  if (!o.open) s += rect(x - 1, y - 1, w + 2, h + 2, { fill: 'rgba(200,220,235,0.08)', r: 2 });
  else s += rect(x - 3, y - 11, w + 6, 5, { fill: 'rgba(200,220,235,0.22)', r: 1 }) + line(x - 3, y - 6, x + w + 3, y - 6, '#5A646E', 1);
  return s;
}

// The shield trim as a five-way selector, one control 100 x 40: TRIM, and the hull in miniature with
// a mark on each side it can lean to and a dot for even; the side chosen is lit (cyan, as commanded).
// Key 8 steps it; a click on a side sets it. state 'na' dims it and strips its foot.
function trimSelector(x, y, cur, state = 'idle') {
  const w = 100, h = 40, na = state === 'na', ink = na ? C.dim : '#D2D5CF';
  let s = rect(x - 1.5, y - 1.5, w + 3, h + 3, { fill: '#121416', r: 2.5 }) + rect(x, y, w, h, { fill: '#1F2226', r: 1.5 }) + rect(x, y, w, h * 0.45, { fill: 'rgba(255,255,255,0.04)', r: 1.5 });
  s += text(x + 8, y + 24, 'TRIM', { size: LB, bold: true, fill: ink, track: 0.4 });
  const cx = x + 70, cy = y + 19;
  s += path(`M${cx - 20} ${cy - 5} H${cx + 10} L${cx + 19} ${cy} L${cx + 10} ${cy + 5} H${cx - 20} Z`, ink, 1.2);
  const lit = k => !na && cur === k ? C.cyan : ink;
  s += path(`M${cx - 12} ${cy - 9} H${cx + 6}`, lit('port'), cur === 'port' && !na ? 2.5 : 1.5) + path(`M${cx - 12} ${cy + 9} H${cx + 6}`, lit('stbd'), cur === 'stbd' && !na ? 2.5 : 1.5);
  s += path(`M${cx + 24} ${cy - 5} L${cx + 26} ${cy} L${cx + 24} ${cy + 5}`, lit('bow'), cur === 'bow' && !na ? 2.5 : 1.5) + path(`M${cx - 25} ${cy - 5} V${cy + 5}`, lit('stern'), cur === 'stern' && !na ? 2.5 : 1.5);
  s += `<circle cx="${cx - 4}" cy="${cy}" r="2.2" fill="${lit('even')}"/>`;
  if (na) s += barber(x + 5, y + h - 6, w - 10);
  return s;
}
