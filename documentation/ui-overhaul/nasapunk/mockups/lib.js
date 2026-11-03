// Shared drawing helpers for the Nasa-punk mockups. Every size is in UI units (1280 x 800).
const C = {
  glass: '#05070A', graticule: '#16202A', dim: '#7E8792', white: '#EEF1F3', cyan: '#3CCBE6',
  green: '#3DDC7A', magenta: '#F052D2', amber: '#FFAE1F', red: '#FF3B30', halo: 'rgba(0,0,0,0.7)',
  bezel: '#2B2E33', bezelTop: '#2F3238', edgeHi: '#40444B', edgeLo: '#1A1C1F', stencil: '#D6D8D2',
  key: '#383C42', lensOff: '#1C1E21', lensLegend: '#5A5E63', kapton: '#C8962E', orange: '#E5531A',
};
const SANS = "B612, sans-serif", MONO = "'B612 Mono', monospace";

function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;'); }

// The smallest glass text a page allows: a functional spec's legibility floor, set by the page.
// Hardware legends (bezel titles, part plates, printed key letters, floor stencils) pass legend: true.
let TEXT_FLOOR = 0;

// Text width in UI units, from B612's advance widths (measured per character at 100 px), so a
// layout can fit its text before the font has loaded. Kerning is ignored; B612 has little.
const B612_CHARS = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~·‘’Δ−… ×▲◆›°—–";
const B612_ADV = { r: [350,350,400,850,550,900,700,200,450,450,550,600,250,450,300,550,650,650,650,650,650,650,650,650,650,650,300,250,650,500,650,550,1000,650,650,600,650,600,550,650,700,300,450,600,550,900,750,800,600,800,650,550,600,700,650,950,650,600,600,450,550,500,750,550,550,525,600,500,600,550,400,600,600,300,300,550,350,900,600,600,600,600,400,450,400,600,550,750,550,550,500,649,200,600,750,250,350,350,643,550,750,200,800,990,1051,450,300,1050,550], b: [350,350,400,850,550,900,700,200,450,450,550,600,250,450,300,550,650,650,650,650,650,650,650,650,650,650,300,250,650,500,650,550,1000,650,650,600,650,600,550,650,700,300,450,600,550,900,750,800,600,800,650,550,600,700,650,950,650,600,600,450,550,500,750,550,550,525,600,500,600,550,400,600,600,300,300,550,350,900,600,600,600,600,400,450,400,600,550,750,550,550,500,649,200,600,750,250,350,350,627,550,750,200,800,990,1051,450,300,1050,550] };
function tw(s, size = 12, bold = false, track = 0) {
  const t = B612_ADV[bold ? 'b' : 'r'];
  let w = 0;
  for (const ch of String(s)) { const i = B612_CHARS.indexOf(ch); w += (i >= 0 ? t[i] : 650) / 1000 * size; }
  return w + track * String(s).length;
}

// The size a text call draws at once the page's floor applies (hardware legends are exempt).
function fs(size, legend) { return Math.max(size, legend ? 0 : TEXT_FLOOR); }

function text(x, y, s, o = {}) {
  const size = Math.max(o.size || 12, o.legend ? 0 : TEXT_FLOOR), fill = o.fill || C.white, anchor = o.anchor || 'start';
  const family = o.mono ? MONO : SANS, weight = o.bold ? 700 : 400;
  const ls = o.track ? `letter-spacing="${o.track}"` : '';
  const lg = o.legend ? ' data-legend="1"' : '';
  return `<text x="${x}" y="${y}" font-family="${family}" font-weight="${weight}" font-size="${size}" fill="${fill}" text-anchor="${anchor}" ${ls}${lg}>${esc(s)}</text>`;
}

// A value with its unit in dim, right-aligned at x.
function value(x, y, v, unit, o = {}) {
  const size = o.size || 15;
  const unitW = unit ? unit.length * (size - 4) * 0.66 + 3 : 0;
  return text(x - unitW, y, v, { size, mono: o.mono, fill: o.fill || C.white, anchor: 'end', bold: o.bold })
    + (unit ? text(x, y, unit, { size: size - 4, fill: C.dim, anchor: 'end' }) : '');
}

function line(x1, y1, x2, y2, stroke, w = 1.5, o = {}) {
  const dash = o.dash ? `stroke-dasharray="${o.dash}"` : '';
  return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${stroke}" stroke-width="${w}" ${dash} stroke-linecap="butt"/>`;
}
function rect(x, y, w, h, o = {}) {
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${o.r || 0}" fill="${o.fill || 'none'}" stroke="${o.stroke || 'none'}" stroke-width="${o.sw || 1.5}" ${o.dash ? `stroke-dasharray="${o.dash}"` : ''}/>`;
}
function path(d, stroke, w = 1.5, o = {}) {
  return `<path d="${d}" fill="${o.fill || 'none'}" stroke="${stroke}" stroke-width="${w}" ${o.dash ? `stroke-dasharray="${o.dash}"` : ''} stroke-linejoin="miter"/>`;
}
// Draw a symbol twice: halo first, then colour (for anything over the world view).
function haloed(fn, color, w = 1.5) { return fn(C.halo, w + 2) + fn(color, w); }

function fastener(x, y) {
  return `<circle cx="${x}" cy="${y}" r="2.6" fill="#24272B" stroke="${C.edgeHi}" stroke-width="0.6"/>`
    + line(x - 1.8, y + 1.2, x + 1.8, y - 1.2, '#15171A', 0.8);
}

// Hardware bezel body (no glass).
function bezelBody(x, y, w, h, r = 4) {
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="url(#bezelShade)"/>`
    + `<path d="M${x + r} ${y + h - 0.5} H${x + w - r} M${x + w - 0.5} ${y + r} V${y + h - r}" stroke="${C.edgeLo}" stroke-width="1"/>`
    + `<path d="M${x + r} ${y + 0.5} H${x + w - r} M${x + 0.5} ${y + r} V${y + h - r}" stroke="${C.edgeHi}" stroke-width="1"/>`;
}

function softKey(x, y, w, h, pressed) {
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="2" fill="${pressed ? '#24272B' : C.key}"/>`
    + `<path d="M${x + 1} ${y + h - 0.5} H${x + w - 1}" stroke="${pressed ? C.edgeHi : C.edgeLo}" stroke-width="1"/>`
    + `<path d="M${x + 1} ${y + 0.5} H${x + w - 1}" stroke="${pressed ? C.edgeLo : '#4A4F56'}" stroke-width="1"/>`;
}

// A display unit. keys: {bottom:[legends], left:[...], right:[...], top:[...]}; a legend is
// a string, or {t, state} with state 'cur' (white), 'on' (boxed green), 'na' (hidden), default dim.
// Returns svg; o.content(gx, gy, gw, gh) draws the glass content.
function du(x, y, w, h, o = {}) {
  const b = o.b || 12, keyRow = 14;
  const kb = o.keys || {};
  const padT = kb.top ? keyRow : 0, padB = kb.bottom ? keyRow : 0, padL = kb.left ? keyRow : 0, padR = kb.right ? keyRow : 0;
  const gx = x + b + padL, gy = y + b + padT, gw = w - 2 * b - padL - padR, gh = h - 2 * b - padT - padB - (o.plate ? 10 : 0);
  let s = bezelBody(x, y, w, h);
  s += fastener(x + 6, y + 6) + fastener(x + w - 6, y + 6) + fastener(x + 6, y + h - 6) + fastener(x + w - 6, y + h - 6);
  s += rect(gx, gy, gw, gh, { fill: C.glass });
  s += rect(gx + 0.5, gy + 0.5, gw - 1, gh - 1, { stroke: '#0E141A', sw: 1 });
  const legends = [];
  const edge = (list, side) => {
    const n = list.length, long = side === 'top' || side === 'bottom';
    const span = long ? gw : gh, step = span / n, kw = long ? 26 : 10, kh = long ? 10 : 22;
    list.forEach((lg, i) => {
      const c = (long ? gx : gy) + step * (i + 0.5);
      let kx, ky, lx, ly, anchor;
      if (side === 'bottom') { kx = c - kw / 2; ky = gy + gh + (o.plate ? 2 : 3); lx = c; ly = gy + gh - 6; anchor = 'middle'; }
      if (side === 'top') { kx = c - kw / 2; ky = y + b - 1; lx = c; ly = gy + 15; anchor = 'middle'; }
      if (side === 'left') { kx = x + b - 2; ky = c - kh / 2; lx = gx + 6; ly = c + 4; anchor = 'start'; }
      if (side === 'right') { kx = gx + gw + 3; ky = c - kh / 2; lx = gx + gw - 6; ly = c + 4; anchor = 'end'; }
      const L = typeof lg === 'string' ? { t: lg } : lg;
      s += softKey(kx, ky, kw, kh, L.state === 'on');
      // A slot's fixed keyboard key, printed on the cap where the spec wants bindings visible.
      if (L.k) s += text(kx + kw / 2, ky + kh - 2.2, L.k, { size: 7, bold: true, fill: C.stencil, anchor: 'middle', track: 0.4, legend: true });
      if (!L.t || L.state === 'na') return;
      const fill = L.state === 'cur' ? C.white : L.state === 'on' ? C.green : C.dim;
      legends.push(text(lx, ly, L.t, { size: 11, bold: true, fill, anchor, track: 0.6 }));
      if (L.state === 'on' || L.state === 'cur') {
        const tw = L.t.length * 7.4 + 8, bx = anchor === 'middle' ? lx - tw / 2 : anchor === 'end' ? lx - tw + 4 : lx - 4;
        if (L.state === 'on') legends.push(rect(bx, ly - 11, tw, 15, { stroke: C.green, sw: 1 }));
      }
    });
  };
  ['top', 'bottom', 'left', 'right'].forEach(sd => kb[sd] && edge(kb[sd], sd));
  if (o.title) s += text(x + w / 2, y + b - 3, o.title, { size: o.titleSize || 8.5, bold: true, fill: C.stencil, anchor: 'middle', track: 1.4, legend: true });
  if (o.plate) {
    const py = y + h - 13;
    s += rect(x + 18, py, 3, 8, { fill: C.kapton });
    s += text(x + 25, py + 7, o.plate, { size: 7.5, bold: true, fill: C.stencil, track: 1, legend: true });
  }
  s += `<g clip-path="url(#clip${o.id})">${o.content ? o.content(gx, gy, gw, gh) : ''}</g>`;
  s += legends.join('');
  s = `<clipPath id="clip${o.id}"><rect x="${gx}" y="${gy}" width="${gw}" height="${gh}"/></clipPath>` + s;
  return { svg: s, gx, gy, gw, gh };
}

// A lens annunciator. lit: a colour (lit hard: dark legend on the colour), 'advisory' (lit softly:
// a light legend on a grey lens) or nothing (dark).
function lens(x, y, w, h, legend, lit) {
  const adv = lit === 'advisory';
  const fill = adv ? '#3D454E' : lit ? lit : C.lensOff, ink = adv ? C.white : lit ? '#0A0A0A' : C.lensLegend;
  const lines = legend.split('\n');
  let s = rect(x - 1, y - 1, w + 2, h + 2, { fill: '#151719', r: 2 }) + rect(x, y, w, h, { fill, r: 1.5 });
  if (lit) s += rect(x, y, w, h * 0.35, { fill: adv ? 'rgba(255,255,255,0.06)' : 'rgba(255,255,255,0.12)', r: 1.5 });
  const size = Math.max(lines.length > 1 ? 8.5 : 10, TEXT_FLOOR), pitch = Math.max(10, size + 1);
  lines.forEach((l, i) => { s += text(x + w / 2, y + h / 2 + size * 0.36 + (i - (lines.length - 1) / 2) * pitch, l, { size, bold: true, fill: ink, anchor: 'middle', track: TEXT_FLOOR ? 0.2 : 0.5 }); });
  return s;
}

function defs() {
  return `<defs><linearGradient id="bezelShade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${C.bezelTop}"/><stop offset="1" stop-color="${C.bezel}"/></linearGradient></defs>`;
}

function stars(w, h, n, seed = 7) {
  let s = '', r = seed;
  const rnd = () => (r = (r * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < n; i++) {
    const a = 0.25 + rnd() * 0.6;
    s += `<circle cx="${(rnd() * w).toFixed(1)}" cy="${(rnd() * h).toFixed(1)}" r="${(0.4 + rnd() * 0.8).toFixed(2)}" fill="rgba(220,228,240,${a.toFixed(2)})"/>`;
  }
  return s;
}

// Contact symbols (outline; filled when locked).
const sym = {
  circle: (x, y, r, c, w, f) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${f || 'none'}" stroke="${c}" stroke-width="${w}"/>`,
  square: (x, y, r, c, w, f) => rect(x - r, y - r, 2 * r, 2 * r, { stroke: c, sw: w, fill: f }),
  diamond: (x, y, r, c, w, f) => path(`M${x} ${y - r * 1.25} L${x + r * 1.25} ${y} L${x} ${y + r * 1.25} L${x - r * 1.25} ${y} Z`, c, w, { fill: f }),
  quatrefoil: (x, y, r, c, w) => { const q = r * 0.55; return path(`M${x - q} ${y - q} A${q} ${q} 0 1 1 ${x + q} ${y - q} A${q} ${q} 0 1 1 ${x + q} ${y + q} A${q} ${q} 0 1 1 ${x - q} ${y + q} A${q} ${q} 0 1 1 ${x - q} ${y - q} Z`, c, w); },
  station: (x, y, r, c, w) => rect(x - r, y - r, 2 * r, 2 * r, { stroke: c, sw: w }) + `<circle cx="${x}" cy="${y}" r="1.6" fill="${c}"/>`,
  prograde: (x, y, c, w) => `<circle cx="${x}" cy="${y}" r="7" fill="none" stroke="${c}" stroke-width="${w}"/>` + line(x, y - 7, x, y - 13, c, w) + line(x - 7, y, x - 13, y, c, w) + line(x + 7, y, x + 13, y, c, w) + `<circle cx="${x}" cy="${y}" r="1.2" fill="${c}"/>`,
  retrograde: (x, y, c, w) => `<circle cx="${x}" cy="${y}" r="7" fill="none" stroke="${c}" stroke-width="${w}"/>` + line(x - 5, y - 5, x + 5, y + 5, c, w) + line(x - 5, y + 5, x + 5, y - 5, c, w),
  chevron: (x, y, a, c, w, size = 8) => { const rot = `rotate(${a} ${x} ${y})`; return `<g transform="${rot}">${path(`M${x - size} ${y + size * 0.7} L${x} ${y - size} L${x + size} ${y + size * 0.7} L${x} ${y + size * 0.25} Z`, c, w)}</g>`; },
};

function svgPage(body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="800" viewBox="0 0 1280 800">${defs()}${body}</svg>`;
}
