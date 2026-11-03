// The Nasa-punk element kit, shared by every variation's mockups. Needs lib.js first.
// All sizes in UI units (1280 x 800). Each function returns an SVG string.

const SEV = { warning: C.red, caution: C.amber, advisory: C.white, nominal: C.white };

// ---------- backgrounds ----------

function space(o = {}) {
  let s = rect(0, 0, 1280, 800, { fill: '#010204' });
  if (o.planet !== false) {
    const px = o.px ?? 160, py = o.py ?? 1620, pr = o.pr ?? 1000;
    s += `<circle cx="${px}" cy="${py}" r="${pr}" fill="#07101A"/><circle cx="${px}" cy="${py}" r="${pr}" fill="none" stroke="#1B3550" stroke-width="2" opacity="0.7"/>`;
  }
  return s + stars(1280, 800, o.stars ?? 240, o.seed ?? 7);
}

// Stand-in hull, nose up at hdg 0, length about 86 * k.
function hull(x, y, hdg, k = 1, fill = '#5C626B') {
  return `<g transform="translate(${x} ${y}) rotate(${hdg}) scale(${k})">
    <path d="M0 -42 L12 -30 L14 14 L22 28 L22 38 L-22 38 L-22 28 L-14 14 L-12 -30 Z" fill="${fill}" stroke="#2A2E33" stroke-width="1"/>
    <rect x="-7" y="-22" width="14" height="20" fill="#474C54"/><rect x="-10" y="38" width="8" height="6" fill="#3A3E44"/><rect x="2" y="38" width="8" height="6" fill="#3A3E44"/></g>`;
}
function raiderHull(x, y, hdg, k = 0.6) {
  return `<g transform="translate(${x} ${y}) rotate(${hdg}) scale(${k})"><path d="M0 -34 L18 10 L10 26 L-10 26 L-18 10 Z" fill="#6A5A58" stroke="#2A2422" stroke-width="1"/></g>`;
}
function stationHull(x, y, k = 1) {
  return `<g transform="translate(${x} ${y}) scale(${k})"><rect x="-60" y="-14" width="120" height="28" fill="#4A5058" stroke="#2A2E33"/><rect x="-14" y="-60" width="28" height="120" fill="#4A5058" stroke="#2A2E33"/>
    <rect x="-24" y="-24" width="48" height="48" fill="#565D66" stroke="#2A2E33"/><rect x="-90" y="-8" width="30" height="16" fill="#2C4766"/><rect x="60" y="-8" width="30" height="16" fill="#2C4766"/></g>`;
}

// ---------- world symbology (always haloed) ----------

const W = {
  pro: (x, y, c = C.green, tag) => haloed((cc, w) => sym.prograde(x, y, cc, w), c) + (tag ? text(x + 16, y - 8, tag, { size: 9, bold: true, fill: c, track: 0.6 }) : ''),
  retro: (x, y, c = C.green) => haloed((cc, w) => sym.retrograde(x, y, cc, w), c),
  trend: (x1, y1, x2, y2) => haloed((c, w) => line(x1, y1, x2, y2, c, w), C.white, 2.5),
  bracket: (x, y, r, c, k = 6) => haloed((cc, w) => path(`M${x - r} ${y - r + k} V${y - r} H${x - r + k} M${x + r - k} ${y - r} H${x + r} V${y - r + k} M${x + r} ${y + r - k} V${y + r} H${x + r - k} M${x - r + k} ${y + r} H${x - r} V${y + r - k}`, cc, w), c),
  navRing: (x, y, r = 17) => haloed((c, w) => `<circle cx="${x}" cy="${y}" r="${r}" fill="none" stroke="${c}" stroke-width="${w}"/>`, C.magenta),
  contact: (kind, x, y, o = {}) => {
    const col = { hostile: C.red, friendly: C.cyan, neutral: C.white, unknown: C.amber, station: C.white, derelict: C.dim, beaten: C.dim }[kind];
    const f = o.locked ? col : null;
    const shape = { hostile: 'diamond', friendly: 'circle', neutral: 'square', unknown: 'quatrefoil', station: 'station', derelict: 'square', beaten: 'diamond' }[kind];
    let s = '';
    if (o.leader) s += haloed((c, w) => line(x, y, x + o.leader[0], y + o.leader[1], c, w), col);
    if (kind === 'derelict' || kind === 'beaten') s += haloed((c, w) => (shape === 'diamond' ? path(`M${x} ${y - 9} L${x + 9} ${y} L${x} ${y + 9} L${x - 9} ${y} Z`, c, w, { dash: '3 2.5' }) : rect(x - 7, y - 7, 14, 14, { stroke: c, sw: w, dash: '3 2.5' })), col);
    else s += haloed((c, w) => sym[shape](x, y, shape === 'quatrefoil' ? 9 : 7, c, w, f), col);
    if (o.locked) s += W.bracket(x, y, 17, col);
    if (o.nav) s += W.navRing(x, y, o.locked ? 23 : 17);
    if (o.label) {
      const lx = x + (o.lx ?? 24), ly = y + (o.ly ?? -6);
      o.label.forEach((t, i) => {
        const isName = i === 0;
        s += text(lx, ly + i * 13, t, { size: isName ? 11 : 10.5, bold: isName, fill: isName ? (o.nameColor || col) : (o.lineColor || C.white), track: isName ? 0.6 : 0, anchor: o.anchor || 'start' });
      });
    }
    return s;
  },
  // Lead reticle: cyan pipper; hit chance as three pips (filled = good) plus the number.
  lead: (x, y, pct, o = {}) => {
    const pips = pct >= 60 ? 3 : pct >= 30 ? 2 : 1;
    let s = haloed((c, w) => `<circle cx="${x}" cy="${y}" r="9" fill="none" stroke="${c}" stroke-width="${w}"/>` + line(x - 14, y, x - 9, y, c, w) + line(x + 9, y, x + 14, y, c, w) + line(x, y - 14, x, y - 9, c, w) + line(x, y + 9, x, y + 14, c, w), C.cyan);
    for (let i = 0; i < 3; i++) s += `<circle cx="${x + 20 + i * 7}" cy="${y + 12}" r="2.3" fill="${i < pips ? C.cyan : 'none'}" stroke="${C.cyan}" stroke-width="1"/>`;
    s += text(x + 18, y + 4, `${pct}%`, { size: 11, bold: true, fill: C.cyan });
    return s;
  },
  hitLane: (x1, y1, x2, y2) => haloed((c, w) => line(x1, y1, x2, y2, c, w, { dash: '1 4' }), C.cyan, 1.5),
  fireArc: (x, y, a0, a1, r) => {
    const p = (a, rr) => [x + rr * Math.sin(a * Math.PI / 180), y - rr * Math.cos(a * Math.PI / 180)];
    const [x0, y0] = p(a0, r), [x1, y1] = p(a1, r);
    return haloed((c, w) => path(`M${x} ${y} L${x0} ${y0} A${r} ${r} 0 0 1 ${x1} ${y1} Z`, c, w), 'rgba(60,203,230,0.55)', 1);
  },
  track: (d, c = C.red) => haloed((cc, w) => path(d, cc, w, { dash: '6 5' }), c, 1.5),
  missile: (x, y, a, c = C.red, label) => haloed((cc, w) => sym.chevron(x, y, a, cc, w, 6), c) + (label ? text(x + 11, y + 4, label, { size: 10.5, bold: true, fill: c }) : ''),
  impact: (x, y, r = 8) => haloed((c, w) => line(x - r * 0.62, y - r * 0.62, x + r * 0.62, y + r * 0.62, c, w) + line(x - r * 0.62, y + r * 0.62, x + r * 0.62, y - r * 0.62, c, w) + `<circle cx="${x}" cy="${y}" r="${r}" fill="none" stroke="${c}" stroke-width="${w}"/>`, C.red, 1.8),
  pdKill: (x, y) => { let s = ''; for (let i = 0; i < 8; i++) { const a = i * Math.PI / 4; s += line(x + 5 * Math.cos(a), y + 5 * Math.sin(a), x + 10 * Math.cos(a), y + 10 * Math.sin(a), C.white, 1.5); } return s + text(x + 14, y + 4, 'PD KILL', { size: 9.5, bold: true, fill: C.white, track: 0.5 }); },
  burnArrow: (x, y, deg, len, label) => {
    const r = deg * Math.PI / 180, ex = x + len * Math.sin(r), ey = y - len * Math.cos(r);
    const hx = (a, l) => [ex - l * Math.sin(r + a), ey + l * Math.cos(r + a)];
    const [ax, ay] = hx(0.45, 10), [bx, by] = hx(-0.45, 10);
    return haloed((c, w) => line(x, y, ex, ey, c, w) + path(`M${ax} ${ay} L${ex} ${ey} L${bx} ${by}`, c, w), C.magenta, 2.5)
      + text(ex + 8, ey - 6, label, { size: 11, bold: true, fill: C.magenta });
  },
  collision: (x, y, label) => haloed((c, w) => path(`M${x} ${y - 10} L${x + 10} ${y} L${x} ${y + 10} L${x - 10} ${y} Z`, c, w) + line(x - 4, y, x + 4, y, c, w) + line(x, y - 4, x, y + 4, c, w), C.amber) + text(x + 14, y + 4, label, { size: 10.5, bold: true, fill: C.amber }),
  edge: (x, y, deg, glyph, label, c) => {
    const r = deg * Math.PI / 180;
    let s = `<g transform="rotate(${deg} ${x} ${y})">` + haloed((cc, w) => path(`M${x - 7} ${y + 6} L${x} ${y - 6} L${x + 7} ${y + 6}`, cc, w), c, 2) + '</g>';
    const tx = x - 18 * Math.sin(r), ty = y + 18 * Math.cos(r);
    s += text(tx, ty + 4, label, { size: 10.5, bold: true, fill: c, anchor: Math.sin(r) > 0.3 ? 'end' : Math.sin(r) < -0.3 ? 'start' : 'middle' });
    return s;
  },
  corridor: (x1, y1, x2, y2, half, state) => {
    const c = { safe: C.green, fast: C.amber, danger: C.red }[state];
    const dx = x2 - x1, dy = y2 - y1, L = Math.hypot(dx, dy), nx = -dy / L * half, ny = dx / L * half;
    let s = haloed((cc, w) => line(x1 + nx, y1 + ny, x2 + nx * 2.2, y2 + ny * 2.2, cc, w) + line(x1 - nx, y1 - ny, x2 - nx * 2.2, y2 - ny * 2.2, cc, w), c);
    for (let t = 0.2; t < 1; t += 0.2) { const px = x1 + dx * t, py = y1 + dy * t, k = 1 + 1.2 * t; s += line(px + nx * k * 0.6, py + ny * k * 0.6, px + nx * k, py + ny * k, c, 1.5) + line(px - nx * k * 0.6, py - ny * k * 0.6, px - nx * k, py - ny * k, c, 1.5); }
    return s;
  },
  // Long-range optics as a CCTV feed: monochrome picture, white camera marks, data in the corners.
  cctv: (x, y, w, h, o = {}) => {
    let s = rect(x, y, w, h, { fill: '#0B0D0F' });
    s += `<g clip-path="url(#cctv${o.id})">${o.picture ? o.picture(x + w / 2, y + h / 2) : ''}</g>`;
    s = `<clipPath id="cctv${o.id}"><rect x="${x}" y="${y}" width="${w}" height="${h}"/></clipPath>` + s;
    s += rect(x, y, w, h, { stroke: 'rgba(238,241,243,0.55)', sw: 1 });
    const k = 7, cx = x + w / 2, cy = y + h / 2;
    s += path(`M${cx - 14} ${cy} h8 M${cx + 6} ${cy} h8 M${cx} ${cy - 14} v8 M${cx} ${cy + 6} v8`, C.white, 1);
    s += path(`M${x + 4} ${y + 4 + k} V${y + 4} H${x + 4 + k} M${x + w - 4 - k} ${y + 4} H${x + w - 4} V${y + 4 + k} M${x + w - 4} ${y + h - 4 - k} V${y + h - 4} H${x + w - 4 - k} M${x + 4 + k} ${y + h - 4} H${x + 4} V${y + h - 4 - k}`, C.white, 1);
    s += text(x + 6, y + 14, o.tl || '', { size: 9, bold: true, fill: C.white, track: 0.6 });
    s += text(x + w - 6, y + h - 7, o.br || '', { size: 9, bold: true, fill: C.white, anchor: 'end', track: 0.4 });
    if (o.tr) s += text(x + w - 6, y + 14, o.tr, { size: 9, bold: true, fill: o.trColor || C.dim, anchor: 'end', track: 0.6 });
    return s;
  },
};

// ---------- overlay: tiles, lamps, callouts, prompts ----------

const O = {
  // Annunciator tile, lit; width from the text. Returns { svg, w }.
  tile: (x, y, tag, value, sev, o = {}) => {
    const h = o.h || 24, size = 12;
    const tw = (tag.length * 7.6 + (value ? value.length * 7.2 + 10 : 0)) + 16;
    const fill = sev === 'warning' ? C.red : C.amber;
    let s = rect(x - 1, y - 1, tw + 2, h + 2, { fill: '#151719', r: 2 }) + rect(x, y, tw, h, { fill: o.clearing ? C.glass : fill, stroke: o.clearing ? fill : 'none', sw: 1.5, r: 1.5 });
    if (!o.clearing) s += rect(x, y, tw, h * 0.38, { fill: 'rgba(255,255,255,0.13)', r: 1.5 });
    const ink = o.clearing ? fill : '#0A0A0A';
    s += text(x + 8, y + h / 2 + 4.5, tag, { size, bold: true, fill: ink, track: 0.5 });
    if (value) s += text(x + 8 + tag.length * 7.6 + 8, y + h / 2 + 4.5, value, { size, fill: ink });
    return { svg: s, w: tw };
  },
  tiles: (cx, y, list, gap = 6) => {
    const built = list.map(t => O.tile(0, 0, ...t));
    const total = built.reduce((a, b) => a + b.w, 0) + gap * (list.length - 1);
    let x = cx - total / 2, s = '';
    list.forEach((t, i) => { s += O.tile(x, y, ...t).svg; x += built[i].w + gap; });
    return { svg: s, x0: cx - total / 2, x1: cx + total / 2 };
  },
  master: (x, y, kind) => lens(x, y, 56, 30, kind === 'warning' ? 'MASTER\nWARN' : 'MASTER\nCAUT', kind === 'warning' ? C.red : C.amber),
  // A callout or message line on its own glass slip: loop tag box, speaker, text.
  callout: (x, y, tag, speaker, msg, sev = 'advisory', o = {}) => {
    const col = SEV[sev];
    const tagW = tag.length * 7.4 + 10, spW = speaker ? speaker.length * 7.0 + 6 : 0;
    const w = o.w || (tagW + 8 + spW + msg.length * 6.9 + 14);
    let s = rect(x, y, w, 20, { fill: 'rgba(5,7,10,0.92)' });
    s += rect(x + 4, y + 3, tagW, 14, { stroke: sev === 'advisory' ? C.dim : col, sw: 1 }) + text(x + 4 + tagW / 2, y + 14, tag, { size: 10, bold: true, fill: sev === 'advisory' ? C.dim : col, anchor: 'middle', track: 0.6 });
    let tx = x + tagW + 12;
    if (speaker) { s += text(tx, y + 14.5, speaker, { size: 12, bold: true, fill: C.white }); tx += spW; }
    s += text(tx, y + 14.5, msg, { size: 12, fill: col });
    if (o.count) s += text(x + w - 6, y + 14.5, o.count, { size: 11, bold: true, fill: C.dim, anchor: 'end' });
    return s;
  },
  // Console prompt: a small hardware plate with key caps.
  prompt: (cx, y, name, verbs) => {
    const parts = verbs.map(([k, v]) => ({ k, v, w: k.length * 7 + 10 + v.length * 7.2 + 6 }));
    const nameW = name.length * 8 + 14;
    const w = nameW + parts.reduce((a, p) => a + p.w + 10, 0) + 6;
    const x = cx - w / 2;
    let s = bezelBody(x, y, w, 26, 3) + rect(x + 3, y + 3, 2, 20, { fill: C.kapton });
    s += text(x + 11, y + 17.5, name, { size: 12, bold: true, fill: C.stencil, track: 1 });
    let px = x + nameW + 4;
    parts.forEach(p => {
      const kw = p.k.length * 7 + 10;
      s += softKey(px, y + 5, kw, 16, false) + text(px + kw / 2, y + 17, p.k, { size: 10.5, bold: true, fill: C.stencil, anchor: 'middle' });
      s += text(px + kw + 5, y + 17.5, p.v, { size: 11.5, fill: '#C9CCC6' });
      px += p.w + 10;
    });
    return s;
  },
  // A response: the answer to a key that cannot work here. Glass slip, white, beside the prompt.
  response: (cx, y, msg) => { const w = msg.length * 6.9 + 20; return rect(cx - w / 2, y, w, 22, { fill: 'rgba(5,7,10,0.92)', stroke: C.dim, sw: 1 }) + text(cx, y + 15, msg, { size: 12, fill: C.white, anchor: 'middle' }); },
  // Suit display (the EMU's chest DCM): light suit-grey bezel, small glass.
  dcm: (x, y, rows, o = {}) => {
    const w = o.w || 220, h = 34 + rows.length * 18;
    let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="6" fill="#B9BDB8"/>` + `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="6" fill="none" stroke="#8E928D" stroke-width="1"/>`;
    s += text(x + 10, y + 13, 'DCM  SUIT', { size: 8.5, bold: true, fill: '#26282A', track: 1.4 }) + rect(x + w - 30, y + 6, 20, 8, { fill: C.orange, r: 1 });
    s += rect(x + 8, y + 20, w - 16, h - 28, { fill: C.glass });
    rows.forEach((r, i) => {
      const yy = y + 36 + i * 18;
      s += text(x + 16, yy, r[0], { size: 10, bold: true, fill: C.dim, track: 0.6 });
      s += text(x + w - 16, yy, r[1], { size: 13, bold: true, fill: SEV[r[2] || 'nominal'], anchor: 'end' });
    });
    return s;
  },
  // Out of fiction: a Flight Data File cue card (paper), for guided play.
  cueCard: (x, y, w, title, lines) => {
    const h = 30 + lines.length * 17;
    let s = rect(x + 2, y + 3, w, h, { fill: 'rgba(0,0,0,0.5)' }) + rect(x, y, w, h, { fill: '#F2F0E9' }) + rect(x, y, 6, h, { fill: '#2F6FB2' });
    s += text(x + 14, y + 17, title, { size: 11, bold: true, fill: '#1A1A1A', track: 0.8 }).replace(/B612, sans-serif/, 'Inter, sans-serif');
    s += line(x + 14, y + 23, x + w - 10, y + 23, '#1A1A1A', 0.8);
    lines.forEach((l, i) => { s += text(x + 14, y + 39 + i * 17, l, { size: 12.5, fill: '#1A1A1A' }).replace(/B612, sans-serif/, 'Inter, sans-serif'); });
    return s;
  },
};

// ---------- instruments ----------

const I = {
  // Plan-view scope. contacts: [{kind, x, y (in -1..1 of range), locked, nav, label}]
  scope: (cx, cy, r, contacts, o = {}) => {
    let s = '';
    [r * 0.5, r].forEach(rr => s += `<circle cx="${cx}" cy="${cy}" r="${rr}" fill="none" stroke="${C.graticule}" stroke-width="1"/>`);
    s += line(cx - r, cy, cx + r, cy, C.graticule, 1) + line(cx, cy - r, cx, cy + r, C.graticule, 1);
    if (o.heading != null) { const a = o.heading * Math.PI / 180; s += line(cx, cy, cx + r * 0.42 * Math.sin(a), cy - r * 0.42 * Math.cos(a), C.cyan, 1); }
    s += sym.chevron(cx, cy, o.heading || 0, C.cyan, 1.5, 4.5);
    contacts.forEach(k => {
      const x = cx + k.x * r, y = cy + k.y * r;
      const col = { hostile: C.red, friendly: C.cyan, neutral: C.white, unknown: C.amber, station: C.white, derelict: C.dim, missile: C.red, ownmissile: C.cyan }[k.kind];
      if (k.kind === 'missile' || k.kind === 'ownmissile') s += sym.chevron(x, y, k.a || 0, col, 1.5, 3.5);
      else if (k.kind === 'station') s += sym.station(x, y, 3.5, col, 1.2);
      else if (k.kind === 'derelict') s += rect(x - 3, y - 3, 6, 6, { stroke: col, sw: 1.2, dash: '2 1.5' });
      else s += sym[{ hostile: 'diamond', friendly: 'circle', neutral: 'square', unknown: 'quatrefoil' }[k.kind]](x, y, k.kind === 'unknown' ? 5 : 3.5, col, 1.3, k.locked ? col : null);
      if (k.locked) s += path(`M${x - 8} ${y - 5} V${y - 8} H${x - 5} M${x + 5} ${y - 8} H${x + 8} V${y - 5} M${x + 8} ${y + 5} V${y + 8} H${x + 5} M${x - 5} ${y + 8} H${x - 8} V${y + 5}`, col, 1);
      if (k.nav) s += `<circle cx="${x}" cy="${y}" r="8" fill="none" stroke="${C.magenta}" stroke-width="1.2"/>`;
      if (k.label) s += text(x + 8, y - 5, k.label, { size: 9, bold: true, fill: col, track: 0.4 });
    });
    if (o.range) s += text(cx + r - 2, cy + r - 2, o.range, { size: 9.5, fill: C.dim, anchor: 'end' });
    return s;
  },
  // Horizontal tape: value pointer, caution/warning bands, optional notch and bracket.
  hbar: (x, y, w, frac, o = {}) => {
    let s = line(x, y, x + w, y, C.white, 1.5);
    for (let k = 0; k <= 4; k++) s += line(x + w * k / 4, y, x + w * k / 4, y + 5, C.white, 1);
    (o.bands || []).forEach(([a, b, c]) => s += rect(x + w * a, y - 4, w * (b - a), 3, { fill: c }));
    if (o.bracket) { const [a, b] = o.bracket; s += path(`M${x + w * a} ${y - 10} v-4 H${x + w * b} v4`, C.magenta, 1.5); }
    if (o.notch != null) s += line(x + w * o.notch, y - 9, x + w * o.notch, y + 7, o.notchColor || C.white, 2) + (o.notchLabel ? text(x + w * o.notch, y + 17, o.notchLabel, { size: 9, bold: true, fill: o.notchColor || C.dim, anchor: 'middle', track: 0.4 }) : '');
    const px = x + w * frac;
    s += path(`M${px} ${y - 1} l-5 -8 h10 z`, o.color || C.white, 1, { fill: o.color || C.white });
    return s;
  },
  // Vertical strip gauge with quarter ticks and bands on the scale.
  strip: (x, y, h, val, label, o = {}) => {
    const col = val <= (o.warn ?? 10) ? C.red : val <= (o.caut ?? 25) ? C.amber : C.white;
    let s = rect(x - 4, y + h * 0.75, 3, h * 0.15, { fill: C.amber }) + rect(x - 4, y + h * 0.9, 3, h * 0.1, { fill: C.red });
    s += line(x, y, x, y + h, C.white, 1.5);
    for (let k = 0; k <= 4; k++) s += line(x, y + h * k / 4, x - 6, y + h * k / 4, C.white, 1);
    const py = y + h * (1 - val / 100);
    s += path(`M${x + 2} ${py} l8 -5 v10 z`, col, 1, { fill: col }) + text(x + 14, py + 5, o.text || String(val), { size: 13, bold: true, fill: col });
    s += text(x - 6, y + h + 14, label, { size: 10, bold: true, fill: col === C.white ? C.dim : col, track: 0.6 });
    return s;
  },
  // Timeline: now at x, the future to the right. events: [{t: 0..1, label, sub, color, shape}]
  timeline: (x, y, w, events, o = {}) => {
    let s = line(x, y, x + w, y, C.dim, 1);
    for (let k = 1; k < 8; k++) s += line(x + w * k / 8, y - 2, x + w * k / 8, y + 2, C.dim, 1);
    s += line(x, y - 8, x, y + 8, C.white, 2.5) + text(x + 3, y + 18, o.now || 'NOW', { size: 9, bold: true, fill: C.dim, track: 0.4 });
    events.forEach(e => {
      const ex = x + w * e.t, c = e.color || C.magenta;
      if (e.shape === 'tick') s += line(ex, y - 6, ex, y + 6, c, 2);
      else s += path(`M${ex} ${y - 6} l6 6 l-6 6 l-6 -6 z`, c, 1.5, { fill: e.fill ? c : C.glass });
      if (e.label) s += text(ex, y - 10, e.label, { size: 10, bold: true, fill: c, anchor: e.anchor || 'middle', track: 0.3 });
      if (e.sub) s += text(ex, y + 19, e.sub, { size: 9.5, fill: C.dim, anchor: e.anchor || 'middle' });
    });
    if (o.end) s += text(x + w, y + 18, o.end, { size: 9, fill: C.dim, anchor: 'end' });
    return s;
  },
  // Status box (EICAS style): label inside an outline in its state colour; filled when engaged.
  box: (x, y, w, h, t, state = 'normal', o = {}) => {
    const col = { normal: C.white, engaged: C.green, caution: C.amber, warning: C.red, off: C.dim, selected: C.cyan }[state];
    const dash = state === 'off' ? '3 2' : '';
    let s = rect(x, y, w, h, { stroke: col, sw: state === 'selected' ? 1.8 : 1.2, dash, fill: o.fill ? col : 'none' });
    s += text(x + w / 2, y + h / 2 + 4, t, { size: o.size || 10.5, bold: true, fill: o.fill ? '#05070A' : col, anchor: 'middle', track: 0.5 });
    return s;
  },
  // Shield ring of n segments around a hull outline; thick[i] in 0..1; trim index of the side leaned.
  shieldRing: (cx, cy, r, thick, o = {}) => {
    let s = '';
    const n = thick.length, gap = n > 16 ? 0.6 : 3;
    for (let i = 0; i < n; i++) {
      const a0 = (i / n) * 360 + gap / 2, a1 = ((i + 1) / n) * 360 - gap / 2, t = thick[i];
      const p = (a, rr) => [cx + rr * Math.sin(a * Math.PI / 180), cy - rr * Math.cos(a * Math.PI / 180)];
      const w = 2 + t * 7, rr = r + w / 2;
      const [x0, y0] = p(a0, rr), [x1, y1] = p(a1, rr);
      const col = t <= 0.05 ? C.red : t < 0.35 ? C.amber : C.white;
      if (t <= 0.05) s += path(`M${x0} ${y0} A${rr} ${rr} 0 0 1 ${x1} ${y1}`, col, 1, { dash: '2 2' });
      else s += `<path d="M${x0} ${y0} A${rr} ${rr} 0 0 1 ${x1} ${y1}" fill="none" stroke="${col}" stroke-width="${w}"/>`;
    }
    if (o.trim != null) { const a = o.trim * Math.PI / 180, R = r + 14; s += path(`M${cx + R * Math.sin(a - 0.25)} ${cy - R * Math.cos(a - 0.25)} A${R} ${R} 0 0 1 ${cx + R * Math.sin(a + 0.25)} ${cy - R * Math.cos(a + 0.25)}`, C.cyan, 2); }
    s += `<g transform="translate(${cx} ${cy}) scale(${r / 60})"><path d="M0 -42 L12 -30 L14 14 L22 28 L22 38 L-22 38 L-22 28 L-14 14 L-12 -30 Z" fill="none" stroke="${C.dim}" stroke-width="${1.2 * 60 / r}"/></g>`;
    return s;
  },
  // Ship plan synoptic. rooms: [{x, y, w, h, name, state: 'ok'|'fire'|'breach'|'smoke'|'vacuum'|'low', crew:[[dx,dy]] }] in plan units, scaled by k.
  plan: (x, y, k, rooms, o = {}) => {
    let s = '', defs = '';
    const pat = (id, c) => `<pattern id="${id}" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><line x1="0" y1="0" x2="0" y2="5" stroke="${c}" stroke-width="1.6" opacity="0.7"/></pattern>`;
    defs += pat(`hw${o.id}`, C.red) + pat(`hc${o.id}`, C.amber);
    rooms.forEach(rm => {
      const rx = x + rm.x * k, ry = y + rm.y * k, rw = rm.w * k, rh = rm.h * k;
      const st = rm.state || 'ok';
      const col = { ok: C.dim, fire: C.red, breach: C.red, vacuum: C.red, smoke: C.amber, low: C.amber }[st];
      if (st !== 'ok') s += rect(rx, ry, rw, rh, { fill: `url(#${['fire', 'breach', 'vacuum'].includes(st) ? 'hw' : 'hc'}${o.id})` });
      s += rect(rx, ry, rw, rh, { stroke: st === 'ok' ? '#5B636D' : col, sw: st === 'ok' ? 1 : 1.5 });
      if (rm.name && rw > 30) s += text(rx + rw / 2, ry + rh / 2 + 3.5, rm.name, { size: o.fs || 8.5, bold: true, fill: st === 'ok' ? C.dim : col, anchor: 'middle', track: 0.4 });
      (rm.crew || []).forEach(([dx, dy]) => s += `<circle cx="${rx + dx * k}" cy="${ry + dy * k}" r="2.4" fill="${C.white}"/>`);
      if (rm.breach) { const [bx, by] = rm.breach; s += path(`M${x + bx * k - 4} ${y + by * k - 4} l8 8 M${x + bx * k + 4} ${y + by * k - 4} l-8 8`, C.red, 1.8); }
    });
    return `<defs>${defs}</defs>` + s;
  },
  // Table: cols [{t, w, align}], rows [[cells]] with cell = string or {t, c, bold}
  table: (x, y, cols, rows, o = {}) => {
    let s = '', cx = x;
    const rh = o.rh || 17;
    cols.forEach(c => { s += text(c.align === 'right' ? cx + c.w : cx, y, c.t, { size: 9.5, bold: true, fill: C.dim, anchor: c.align === 'right' ? 'end' : 'start', track: 0.6 }); cx += c.w; });
    s += line(x, y + 5, x + cols.reduce((a, c) => a + c.w, 0), y + 5, C.graticule, 1);
    rows.forEach((r, i) => {
      let cx2 = x;
      const yy = y + 5 + rh * (i + 1) - 4;
      if (o.sel === i) s += rect(x - 4, yy - 12, cols.reduce((a, c) => a + c.w, 0) + 8, rh - 1, { stroke: C.cyan, sw: 1.2 });
      r.forEach((cell, j) => {
        const c = typeof cell === 'string' ? { t: cell } : cell;
        s += text(cols[j].align === 'right' ? cx2 + cols[j].w : cx2, yy, c.t, { size: o.fs || 12, bold: c.bold, fill: c.c || C.white, anchor: cols[j].align === 'right' ? 'end' : 'start', track: c.bold ? 0.4 : 0 });
        cx2 += cols[j].w;
      });
      if (o.rule && (i + 1) % o.rule === 0 && i < rows.length - 1) s += line(x, yy + 5, x + cols.reduce((a, c) => a + c.w, 0), yy + 5, C.graticule, 1);
    });
    return s;
  },
  // RCS / thruster activity pictogram: small hull with jets lit (white) or idle (dim).
  jets: (cx, cy, lit = {}) => {
    let s = `<g transform="translate(${cx} ${cy}) scale(0.36)"><path d="M0 -42 L12 -30 L14 14 L22 28 L22 38 L-22 38 L-22 28 L-14 14 L-12 -30 Z" fill="none" stroke="${C.dim}" stroke-width="3.5"/></g>`;
    const jet = (x1, y1, x2, y2, on) => line(cx + x1, cy + y1, cx + x2, cy + y2, on ? C.white : '#2A333D', on ? 2.5 : 1.5);
    s += jet(-4, 16, -4, 23, lit.main) + jet(4, 16, 4, 23, lit.main);
    s += jet(0, -16, 0, -22, lit.rev);
    s += jet(-6, -9, -12, -9, lit.left) + jet(6, 9, 12, 9, lit.left);
    s += jet(6, -9, 12, -9, lit.right) + jet(-6, 9, -12, 9, lit.right);
    return s;
  },
};

// A side or full panel: a tall DU with tab keys along the top and footer keys along the bottom.
function consoleFace(x, y, w, h, o) {
  return du(x, y, w, h, { id: o.id, b: o.b, plate: o.plate, title: o.title, keys: { top: o.tabs, bottom: o.footer, left: o.left, right: o.right }, content: o.content });
}

// ---------- deck view (a stand-in for the game's interior art) ----------
// A corvette's deck plan in tiles, nose to the right. o: { fire, breach, smoke, avatar:[tx,ty], crew:[[tx,ty,name,bubble]], alarm, outline }
function deckView(x0, y0, k, o = {}) {
  const T = (tx) => x0 + tx * k, U = (ty) => y0 + ty * k;
  let s = '<defs><radialGradient id="flame"><stop offset="0" stop-color="#FFE7A0"/><stop offset="0.35" stop-color="#FF9A2E"/><stop offset="0.75" stop-color="#E5401A" stop-opacity="0.8"/><stop offset="1" stop-color="#E5401A" stop-opacity="0"/></radialGradient>'
    + '<radialGradient id="beam"><stop offset="0" stop-color="#FF5030" stop-opacity="0.55"/><stop offset="1" stop-color="#FF5030" stop-opacity="0"/></radialGradient>'
    + '<radialGradient id="core"><stop offset="0" stop-color="#BFF3FF"/><stop offset="0.5" stop-color="#3CCBE6" stop-opacity="0.6"/><stop offset="1" stop-color="#3CCBE6" stop-opacity="0"/></radialGradient>'
    + `<pattern id="tiles" width="${k}" height="${k}" patternUnits="userSpaceOnUse" x="${x0}" y="${y0}"><rect width="${k}" height="${k}" fill="#1D2125"/><path d="M0 0 H${k} M0 0 V${k}" stroke="#262B30" stroke-width="1"/></pattern></defs>`;
  // hull
  s += `<path d="M${T(-0.4)} ${U(-0.4)} H${T(28)} L${T(31.4)} ${U(6)} L${T(28)} ${U(12.4)} H${T(-0.4)} Z" fill="#3A3F46" stroke="#2A2E33" stroke-width="2"/>`;
  s += `<path d="M${T(0)} ${U(0)} H${T(28)} L${T(31)} ${U(6)} L${T(28)} ${U(12)} H${T(0)} Z" fill="url(#tiles)"/>`;
  const rooms = {
    engines: [0, 2, 3, 8, 'ENGINES'], cargo2: [3, 0, 6, 5, 'CARGO 2'], cargo1: [3, 7, 6, 5, 'CARGO 1'], reactor: [9, 0, 5, 5, 'REACTOR'],
    life: [9, 7, 5, 5, 'LIFE SUPPORT'], quarters: [14, 0, 5, 5, 'QUARTERS'], galley: [14, 7, 5, 5, 'GALLEY'], medbay: [19, 0, 4, 5, 'MEDBAY'],
    gun: [19, 7, 4, 5, 'GUN DECK'], bridge: [23, 0, 5, 12, 'BRIDGE'],
  };
  // room tints for hazards
  if (o.breach) s += rect(T(3), U(0), 6 * k, 5 * k, { fill: 'rgba(10,20,40,0.55)' });
  if (o.fire) s += rect(T(14), U(7), 5 * k, 5 * k, { fill: 'rgba(255,60,20,0.16)' });
  // crates in the holds
  [[4, 1], [5.2, 1], [4, 2.4], [7, 3.2], [4.2, 8.4], [5.6, 8.4], [7.2, 9.8], [4.2, 9.8]].forEach(([tx, ty]) => s += rect(T(tx), U(ty), k * 1.1, k * 1.1, { fill: '#4B4436', stroke: '#2E2A22', sw: 1 }));
  // reactor core
  s += `<circle cx="${T(11.5)}" cy="${U(2.8)}" r="${k * 1.3}" fill="url(#core)"/><circle cx="${T(11.5)}" cy="${U(2.8)}" r="${k * 0.55}" fill="#2B3138" stroke="#5B636D" stroke-width="1.5"/>`;
  // consoles: [tx, ty, w, h, alert]
  const consoles = [[10.6, 0.15, 1.8, 0.6, o.reactorAlert], [10.6, 11.25, 1.8, 0.6], [26.6, 5.2, 0.6, 1.6], [24.6, 1.0, 1.8, 0.6], [24.6, 10.4, 1.8, 0.6], [20.2, 11.25, 1.6, 0.6], [15.8, 11.25, 1.6, 0.6, o.fire ? 'warning' : null]];
  // o.alerts: { consoleIndex: 'caution' | 'warning' } overrides, so a station's console glows in its alert role (11.6)
  consoles.forEach(([tx, ty, w, h, al0], i) => {
    const al = (o.alerts || {})[i] !== undefined ? o.alerts[i] : al0;
    s += rect(T(tx), U(ty), w * k, h * k, { fill: '#2C3238', stroke: '#15181B', sw: 1 });
    const glow = al === 'warning' ? C.red : al === 'caution' ? C.amber : '#2B6F80';
    s += rect(T(tx) + 2, U(ty) + 2, w * k - 4, h * k - 4, { fill: glow, r: 1 });
    if (al) s += `<circle cx="${T(tx + w / 2)}" cy="${U(ty + h / 2)}" r="${k * 1.1}" fill="${glow}" opacity="0.18"/>`;
  });
  // walls (corridor spine y 5..7 from x 3 to 23)
  const wall = (x1, y1, x2, y2) => line(T(x1), U(y1), T(x2), U(y2), '#59616A', 4);
  s += wall(3, 0, 3, 5) + wall(3, 7, 3, 12) + wall(9, 0, 9, 5) + wall(9, 7, 9, 12) + wall(14, 0, 14, 5) + wall(14, 7, 14, 12) + wall(19, 0, 19, 5) + wall(19, 7, 19, 12) + wall(23, 0, 23, 4.6) + wall(23, 7.4, 23, 12);
  // corridor walls with door gaps at room centres
  const doors = [[5.5, 5, 'cargo2'], [5.5, 7, 'cargo1'], [11, 5, 'reactor'], [11, 7, 'life'], [16, 5, 'quarters'], [16, 7, 'galley'], [20.5, 5, 'medbay'], [20.5, 7, 'gun']];
  [5, 7].forEach(yy => { let xs = [3, ...doors.filter(d => d[1] === yy).flatMap(d => [d[0] - 0.5, d[0] + 0.5]), 23]; for (let i = 0; i < xs.length; i += 2) s += wall(xs[i], yy, xs[i + 1], yy); });
  s += wall(0, 2, 3, 2) + wall(0, 10, 3, 10);
  doors.forEach(([tx, ty, room]) => {
    const bad = (room === 'cargo2' && o.breach) || (room === 'galley' && o.fire);
    const shut = room === 'cargo2' && o.breach && o.breachDoor !== 'open';
    if (shut) s += line(T(tx - 0.5), U(ty), T(tx + 0.5), U(ty), '#8A6A2A', 4);
    s += `<circle cx="${T(tx + 0.75)}" cy="${U(ty) + (ty === 5 ? 5 : -5)}" r="2.6" fill="${bad ? '#FF3B30' : '#3DDC7A'}"/>`;
    if (bad) s += `<circle cx="${T(tx + 0.75)}" cy="${U(ty) + (ty === 5 ? 5 : -5)}" r="7" fill="#FF3B30" opacity="0.25"/>`;
  });
  // hull outline over the floor
  s += `<path d="M${T(0)} ${U(0)} H${T(28)} L${T(31)} ${U(6)} L${T(28)} ${U(12)} H${T(0)} Z" fill="none" stroke="#6A727C" stroke-width="5"/>`;
  // breach: a hole in the outer wall, venting streaks
  if (o.breach) {
    s += rect(T(5.6), U(-0.3), k * 0.9, 9, { fill: '#000' });
    for (let i = 0; i < 6; i++) s += line(T(5.2 + i * 0.3), U(1.6 - (i % 2) * 0.4), T(5.8 + i * 0.08), U(-0.9 - (i % 3) * 0.5), 'rgba(220,230,240,0.55)', 1.2);
  }
  // fire and smoke
  if (o.fire) {
    [[15.2, 9.2, 1.3], [16.4, 10.2, 1.6], [17.6, 9.0, 1.1], [16.6, 8.2, 0.9]].forEach(([tx, ty, r]) => s += `<circle cx="${T(tx)}" cy="${U(ty)}" r="${r * k}" fill="url(#flame)"/>`);
  }
  if (o.smoke) [[15.5, 6, 1.6], [17.5, 5.8, 1.4], [13.6, 6.2, 1.2], [18, 3.6, 1.3]].forEach(([tx, ty, r]) => s += `<circle cx="${T(tx)}" cy="${U(ty)}" r="${r * k}" fill="rgba(150,150,150,0.22)"/>`);
  // alarm beacons: a red lamp with a sweeping beam
  (o.alarm || []).forEach(([tx, ty, a]) => {
    s += `<path d="M${T(tx)} ${U(ty)} L${T(tx) + 3.4 * k * Math.cos(a - 0.35)} ${U(ty) + 3.4 * k * Math.sin(a - 0.35)} A${3.4 * k} ${3.4 * k} 0 0 1 ${T(tx) + 3.4 * k * Math.cos(a + 0.35)} ${U(ty) + 3.4 * k * Math.sin(a + 0.35)} Z" fill="url(#beam)"/>`;
    s += `<circle cx="${T(tx)}" cy="${U(ty)}" r="3.5" fill="#FF5030" stroke="#3A0E08" stroke-width="1"/>`;
  });
  // people: top-down shoulders and head; crew in orange suits, the captain in a blue-grey suit with a cyan ring
  const person = (tx, ty, suit, helmet) => `<ellipse cx="${T(tx)}" cy="${U(ty)}" rx="${k * 0.42}" ry="${k * 0.3}" fill="${suit}" stroke="#1A1A1A" stroke-width="1"/><circle cx="${T(tx)}" cy="${U(ty)}" r="${k * 0.2}" fill="${helmet ? '#DDE3E8' : '#C99A78'}" stroke="#1A1A1A" stroke-width="0.8"/>`;
  (o.crew || []).forEach(([tx, ty, name, bubble]) => {
    s += person(tx, ty, '#E06A2C');
    if (bubble) {
      const bw = bubble.length * 6.6 + 16, bx = T(tx) - bw / 2, by = U(ty) - k * 1.6 - 18;
      s += rect(bx, by, bw, 18, { fill: 'rgba(5,7,10,0.94)', stroke: C.dim, sw: 1 }) + path(`M${T(tx) - 4} ${by + 18} L${T(tx)} ${by + 24} L${T(tx) + 4} ${by + 18}`, C.dim, 1, { fill: 'rgba(5,7,10,0.94)' });
      s += text(T(tx), by + 13, bubble, { size: 10.5, bold: true, fill: C.white, anchor: 'middle', track: 0.3 });
    }
  });
  if (o.avatar) {
    const [tx, ty] = o.avatar;
    s += `<circle cx="${T(tx)}" cy="${U(ty)}" r="${k * 0.62}" fill="none" stroke="${C.cyan}" stroke-width="1.5"/>` + person(tx, ty, '#5D7086', true);
  }
  if (o.outline) { const r = rooms[o.outline[0]]; s += rect(T(r[0]) + 3, U(r[1]) + 3, r[2] * k - 6, r[3] * k - 6, { stroke: o.outline[1] === 'warning' ? C.red : C.amber, sw: 2, dash: '7 4' }); }
  if (o.labels) Object.values(rooms).forEach(([tx, ty, w, h, n]) => s += text(T(tx + w / 2), ty >= 7 ? U(ty) + 14 : U(ty + h) - 6, n, { size: 8, bold: true, fill: 'rgba(200,205,210,0.35)', anchor: 'middle', track: 1 }));
  return s;
}
