// The Nasa-punk screen kit: the full-screen modes, the panels and the shell (style guide section
// 15). Needs lib.js and kit.js first. Every size is in UI units (1280 x 800); each function returns
// an SVG string unless it says otherwise.

// Type on screens: page titles 15 bold, data on a line-select row 15 bold, labels 12 bold caps,
// table cells 13, the scratchpad 13 in B612 Mono.
const S_TITLE = 15, S_DATA = 15, S_LABEL = 12, S_CELL = 13, S_PAD = 13;

// ---------- hardware ----------

// A console body: machined hardware filling a screen area, its stencilled name plate across the top
// (a 2-unit kapton stripe, then the name), fasteners at the corners. DUs are set into it. o.plate is
// the name; o.sub a second, smaller stencil after it (the console's station number or berth).
function consoleBody(x, y, w, h, o = {}) {
  let s = bezelBody(x, y, w, h, 4);
  s += fastener(x + 7, y + 7) + fastener(x + w - 7, y + 7) + fastener(x + 7, y + h - 7) + fastener(x + w - 7, y + h - 7);
  if (o.plate) {
    const px = x + 18, py = y + 6;
    s += rect(px, py + 2, 3, 12, { fill: C.kapton });
    s += text(px + 9, py + 12, o.plate, { size: 11, bold: true, fill: C.stencil, track: 1.6, legend: true });
    if (o.sub) s += text(px + 9 + tw(o.plate, 11, true, 1.6) + 14, py + 12, o.sub, { size: 9, bold: true, fill: '#9EA29C', track: 1.4, legend: true });
  }
  if (o.right) s += text(x + w - 18, y + 18, o.right, { size: 9, bold: true, fill: '#9EA29C', anchor: 'end', track: 1.4, legend: true });
  return s;
}

// A safety guard over a key that does something that can't be undone: a hinged cover in
// safety-orange with black stripes. Closed, it covers the cap; open (the first press lifted it), it
// stands on its hinge along the cap's top edge, seen foreshortened above the cap, and the cap is
// free for the second press.
function guard(kx, ky, kw, kh, open, o = {}) {
  const id = o.id || `g${Math.round(kx)}x${Math.round(ky)}`;
  const stripes = `<pattern id="${id}" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="6" height="6" fill="${C.orange}"/><rect width="2.4" height="6" fill="#1A1410"/></pattern>`;
  if (!open) return `<defs>${stripes}</defs>` + rect(kx - 2, ky - 2, kw + 4, kh + 4, { fill: `url(#${id})`, stroke: '#7A2E10', sw: 1, r: 1.5 });
  return `<defs>${stripes}</defs>` + `<path d="M${kx - 2} ${ky - 2.5} L${kx - 4.5} ${ky - 14} L${kx + kw + 4.5} ${ky - 14} L${kx + kw + 2} ${ky - 2.5} Z" fill="url(#${id})" stroke="#7A2E10" stroke-width="1"/>`
    + line(kx - 2, ky - 1.8, kx + kw + 2, ky - 1.8, '#0C0D0E', 1.4);
}

// The EXEC key's light bar, above the cap: lit white while a modification waits to be executed.
function execBar(kx, ky, kw, lit) {
  return rect(kx + 3, ky - 5, kw - 6, 3, { fill: lit ? C.white : C.lensOff, r: 1 });
}

// A hardware key on a unit's keyboard: a cap in its well, the legend engraved on it in one or two
// lines (split at \n), and the keyboard key that does the same printed on the bezel under it (o.k).
// o.bar: the EXEC key's light bar across the top of the cap, 'lit' (white, while a modification
// waits) or 'dark'.
function hardKey(x, y, w, h, legend, o = {}) {
  let s = rect(x - 1.5, y - 1.5, w + 3, h + 3, { fill: '#141618', r: 3 });
  s += rect(x, y, w, h, { fill: o.pressed ? '#24272B' : '#3A3E44', r: 2 });
  s += `<path d="M${x + 2} ${y + 0.5} H${x + w - 2}" stroke="#565B62" stroke-width="1"/>`;
  s += `<path d="M${x + 2} ${y + h - 0.5} H${x + w - 2}" stroke="#1C1E21" stroke-width="1"/>`;
  let top = y;
  if (o.bar) {
    if (o.bar === 'lit') s += rect(x + 4, y + 1, w - 8, 5.5, { fill: 'rgba(238,241,243,0.18)', r: 2 });
    s += rect(x + 6, y + 2.5, w - 12, 2.5, { fill: o.bar === 'lit' ? C.white : '#4C5056', r: 1 });
    top = y + 5;
  }
  const lines = String(legend).split('\n'), size = 7, pitch = 8, cy = top + (y + h - top) / 2;
  lines.forEach((l, i) => { s += text(x + w / 2, cy + size * 0.36 + (i - (lines.length - 1) / 2) * pitch, l, { size, bold: true, fill: C.stencil, anchor: 'middle', track: 0.5, legend: true }); });
  if (o.k) s += text(x + w / 2, y + h + 8.5, o.k, { size: 6, bold: true, fill: '#8E9298', anchor: 'middle', track: 0.8, legend: true });
  return s;
}

// ---------- glass parts ----------

// Words in a row from parts [[text, colour, size, bold]]: the CDU's option select (the live
// option large in cyan, the other small and dim) and a value with a dim separator. Returns
// { svg, w }; anchor 'end' draws them ending at x.
function parts(x, y, list, o = {}) {
  const gap = o.gap ?? 5;
  const ws = list.map(([t, , size, bold]) => tw(t, fs(size || S_DATA), bold !== false, bold !== false ? 0.4 : 0.2));
  const total = ws.reduce((a, b) => a + b, 0) + gap * (list.length - 1);
  let px = o.anchor === 'end' ? x - total : o.anchor === 'middle' ? x - total / 2 : x, s = '';
  list.forEach(([t, c, size, bold], i) => {
    s += text(px, y, t, { size: size || S_DATA, bold: bold !== false, fill: c || C.white, track: bold !== false ? 0.4 : 0.2 });
    px += ws[i] + gap;
  });
  return { svg: s, w: total };
}

// An option select as parts(): the live option large in cyan, the others small and dim, a slash
// between them; the option's key steps to the next.
function optSel(options, live) {
  const out = [];
  options.forEach((t, i) => { if (i) out.push(['/', C.dim, 12]); out.push(i === live ? [t, C.cyan] : [t, C.dim, 12]); });
  return out;
}

// A cyan cursor box around a span of glass: the keyboard's or the pad's selection.
function cursor(x, y, w, h) { return rect(x, y, w, h, { stroke: C.cyan, sw: 1.5 }); }

// The shade behind a modified value on a page, waiting for EXEC: the flight computer's reverse
// video. x, y: the value's start and baseline; w its width.
function modShade(x, y, w) { return rect(x - 4, y - 15, w + 8, 20, { fill: '#3B4858', r: 1.5 }); }

// A divider across the glass: a dashed rule with a word in its middle, as a flight computer heads a
// part of a page (----- OR WRITE OVER -----). y is the word's baseline.
function divider(gx, y, gw, t) {
  const w0 = tw(t, S_LABEL, true, 0.6), cx = gx + gw / 2;
  return line(gx + 10, y - 4.5, cx - w0 / 2 - 10, y - 4.5, C.dim, 1, { dash: '6 4' })
    + line(cx + w0 / 2 + 10, y - 4.5, gx + gw - 10, y - 4.5, C.dim, 1, { dash: '6 4' })
    + text(cx, y, t, { size: S_LABEL, bold: true, fill: C.dim, anchor: 'middle', track: 0.6 });
}

// The scratchpad: the glass foot's last line, a rule above it, typed text in cyan B612 Mono with a
// block caret. An empty pad shows nothing; o.prompt is a dim hint in it (what typing goes to).
function scratchpad(gx, gy, gw, gh, t, o = {}) {
  const y = gy + gh - 9;
  let s = line(gx + 6, y - 17, gx + gw - 6, y - 17, C.graticule, 1);
  if (t) {
    s += text(gx + 10, y, t, { size: S_PAD, mono: true, fill: C.cyan });
    if (o.caret !== false) s += rect(gx + 10 + tw(t, S_PAD) + 1.5, y - 10.5, 7.5, 12.5, { fill: C.cyan });
  } else if (o.prompt) s += text(gx + 10, y, o.prompt, { size: S_LABEL, bold: true, fill: o.promptColor || C.dim, track: 0.6 });
  if (o.right) s += text(gx + gw - 10, y, o.right, { size: S_LABEL, bold: true, fill: C.dim, anchor: 'end', track: 0.6 });
  return s;
}

// A table on the glass. cols: [{t, w, align: 'right'}]; rows: arrays of cells (a cell is a string or
// {t, c, bold, dim}) or {cells, c, dim, cursor}; header in 12 bold dim caps, cells in 13, a rule
// every o.rule rows (default 4), the cursor row boxed in cyan. Returns { svg, h, rowY(i) }.
function grid(x, y, cols, rows, o = {}) {
  const rh = o.rh || 20, size = o.size || S_CELL, W = cols.reduce((a, c) => a + c.w, 0);
  let s = '', cx = x;
  if (o.head !== false) {
    cols.forEach(c => { if (c.t) s += text(c.align === 'right' ? cx + c.w : c.align === 'middle' ? cx + c.w / 2 : cx, y, c.t, { size: S_LABEL, bold: true, fill: C.dim, anchor: c.align === 'right' ? 'end' : c.align === 'middle' ? 'middle' : 'start', track: 0.6 }); cx += c.w; });
    s += line(x - 2, y + 6, x + W + 2, y + 6, C.graticule, 1);
  }
  const top = o.head === false ? y - rh + 4 : y + 6;
  const rowY = i => top + rh * (i + 1) - 5;
  rows.forEach((r0, i) => {
    const r = Array.isArray(r0) ? { cells: r0 } : r0;
    const yy = rowY(i);
    if (r.cursor) s += cursor(x - 6, yy - size - 1, W + 12, rh - 1);
    let cx2 = x;
    r.cells.forEach((cell, j) => {
      const c = typeof cell === 'string' || typeof cell === 'number' ? { t: String(cell) } : cell;
      const col = cols[j], anchor = col.align === 'right' ? 'end' : col.align === 'middle' ? 'middle' : 'start';
      const px = col.align === 'right' ? cx2 + col.w : col.align === 'middle' ? cx2 + col.w / 2 : cx2;
      if (c.t) s += text(px, yy, c.t, { size: c.size || size, bold: c.bold, fill: c.c || (r.dim || c.dim ? C.dim : r.c || C.white), anchor, track: c.bold ? 0.4 : 0.1 });
      if (c.flag) s += flag(px + (anchor === 'end' ? 6 : tw(c.t, size, c.bold) + 6), yy, c.flag[0], c.flag[1]);
      cx2 += col.w;
    });
    const rule = o.rule ?? 4;
    if (rule && (i + 1) % rule === 0 && i < rows.length - 1) s += line(x - 2, yy + 6, x + W + 2, yy + 6, C.graticule, 1);
  });
  return { svg: s, h: (o.head === false ? 0 : 6) + rh * rows.length, rowY };
}

// A flag: a short boxed word in its severity's colour, for data that is missing, refused or wrong.
function flag(x, y, t, c = C.amber, o = {}) {
  const size = o.size || 11.5, w = tw(t, fs(size), true, 0.5) + 8;
  const bx = o.anchor === 'end' ? x - w : x;
  return rect(bx, y - size + 0.5, w, size + 4.5, { stroke: c, sw: 1 }) + text(bx + 4, y + 1.5, t, { size, bold: true, fill: c, track: 0.5 });
}

// A page title, top centre of the glass, with the page count at the right and a MOD mark at the left
// while a modification waits for EXEC.
function cduHead(gx, gy, gw, title, o = {}) {
  let s = text(gx + gw / 2, gy + 21, title, { size: S_TITLE, bold: true, fill: C.white, anchor: 'middle', track: 0.8 });
  if (o.mod) s += text(gx + gw / 2 - tw(title, S_TITLE, true, 0.8) / 2 - 10, gy + 21, 'MOD', { size: S_TITLE, bold: true, fill: C.white, anchor: 'end', track: 0.8 });
  if (o.page) s += text(gx + gw - 10, gy + 21, o.page, { size: S_LABEL, bold: true, fill: C.white, anchor: 'end', track: 0.6 });
  if (o.left) s += text(gx + 10, gy + 21, o.left, { size: S_LABEL, bold: true, fill: o.leftColor || C.dim, track: 0.6 });
  return s;
}

// ---------- line-select pages (the CDU) ----------

// A display unit whose side keys line up with its rows, as a flight computer's line-select keys do.
// o.rows lines are spaced o.pitch apart from o.top (glass units); each row has a left and a right
// field: { label, data, c, prompt (draw the < or > caret), dim (not offered: no caret), parts (the
// data as parts()), cursor, mod (a modified value waiting for EXEC: shaded, as reverse video),
// guard: 'closed'|'open', key: printed on the cap }. A field with no data
// leaves its key bare. o.foot: { msg, pad, padPrompt, padRight }; o.top, o.bottom: page keys on the
// glass edge, as du() takes them; o.hard: the keyboard under the glass, hardware keys with engraved
// legends ({ t, k: the keyboard key printed under it, bar, right: set from the right-hand end }).
// Returns { svg, gx, gy, gw, gh, rowY(i) }.
function cdu(x, y, w, h, o = {}) {
  const b = o.b || 12, kw = 12, kh = 16;
  const top = o.firstRow ?? 46, pitch = o.pitch ?? 44;
  let keys = '', rowY = null, G = null;
  // du() with empty side key lists reserves the key rows beside the glass and draws no keys there;
  // the line-select keys are drawn here instead, each level with its row
  const unit = du(x, y, w, h, {
    id: o.id, b, plate: o.plate, title: o.title, titleSize: o.titleSize, footRoom: o.hard ? 34 : 0,
    keys: { top: o.top, bottom: o.bottom, left: [], right: [] },
    content: (gx, gy, gw, gh) => {
      G = { gx, gy, gw, gh };
      rowY = i => gy + top + i * pitch;
      let g = '';
      if (o.head) g += cduHead(gx, gy, gw, o.head, { page: o.page, mod: o.mod, left: o.headLeft, leftColor: o.headLeftColor });
      const nRows = o.rows ?? Math.max(6, (o.lines || []).length);
      for (let i = 0; i < nRows; i++) {
        const row = (o.lines || [])[i] || {};
        const dy = rowY(i);
        ['L', 'R'].forEach(side => {
          const f = row[side];
          if (o.keysOn === undefined || o.keysOn.includes(side)) {
            const kx = side === 'L' ? x + b - 1 : gx + gw + 3, ky = dy - 12;
            keys += softKey(kx, ky, kw, kh, f && f.pressed);
            if (f && f.key) keys += text(kx + kw / 2, ky + kh - 2.5, f.key, { size: 6.5, bold: true, fill: C.stencil, anchor: 'middle', legend: true });
            if (f && f.guard) keys += guard(kx, ky, kw, kh, f.guard === 'open', { id: `${o.id}g${side}${i}` });
          }
          if (!f) return;
          const ax = side === 'L' ? gx + 10 : gx + gw - 10, anchor = side === 'L' ? 'start' : 'end';
          if (f.label) g += text(ax, dy - 18, f.label, { size: S_LABEL, bold: true, fill: f.labelColor || C.dim, anchor, track: 0.6 });
          if (f.parts) {
            const p = parts(ax, dy, f.parts, { anchor: side === 'L' ? 'start' : 'end' });
            if (f.mod) g += modShade(side === 'L' ? ax : ax - p.w, dy, p.w);
            g += p.svg;
            if (f.cursor) g += cursor(side === 'L' ? ax - 5 : ax - p.w - 5, dy - 16, p.w + 10, 22);
          } else if (f.data) {
            const caret = f.prompt && !f.dim;
            const t = caret ? (side === 'L' ? '<' + f.data : f.data + '>') : f.data;
            const size = f.size || S_DATA, tw0 = tw(t, fs(size), true, 0.5);
            if (f.mod) g += modShade(side === 'L' ? ax : ax - tw0, dy, tw0);
            g += text(ax, dy, t, { size, bold: true, fill: f.dim ? C.dim : f.c || C.white, anchor, track: 0.5 });
            if (f.cursor) g += cursor(side === 'L' ? ax - 5 : ax - tw0 - 5, dy - 16, tw0 + 10, 22);
            if (f.after) g += f.after(side === 'L' ? ax + tw0 : ax - tw0, dy);
          }
        });
      }
      if (o.body) g += o.body(gx, gy, gw, gh, rowY);
      if (o.foot) {
        if (o.foot.msg) g += text(gx + 10, gy + gh - 34, o.foot.msg, { size: S_LABEL, bold: true, fill: o.foot.msgColor || C.white, track: 0.5 });
        g += scratchpad(gx, gy, gw, gh, o.foot.pad, { prompt: o.foot.padPrompt, promptColor: o.foot.padColor, right: o.foot.padRight, caret: o.foot.caret });
      }
      return g;
    },
  });
  if (o.hard) {
    const hw = 56, hh = 21, ky = G.gy + G.gh + 6;
    let lx = G.gx + 2, rx = G.gx + G.gw - 2;
    o.hard.forEach(k => {
      if (!k) { lx += hw + 10; return; }
      const kx = k.right ? (rx -= hw) : lx;
      if (k.right) rx -= 10; else lx += hw + 10;
      keys += hardKey(kx, ky, hw, hh, k.t, { k: k.k, bar: k.bar, pressed: k.pressed });
    });
  }
  return { svg: unit.svg + keys, gx: G.gx, gy: G.gy, gw: G.gw, gh: G.gh, rowY };
}

// ---------- the plot (plan view) ----------

// A point on an ellipse with semi-axes a and b, rotated rot degrees, centred at (cx, cy), at
// eccentric anomaly t (radians); y down.
function ellPt(cx, cy, a, b, rot, t) {
  const r = rot * Math.PI / 180, x = a * Math.cos(t), y = b * Math.sin(t);
  return [cx + x * Math.cos(r) - y * Math.sin(r), cy + x * Math.sin(r) + y * Math.cos(r)];
}
// An orbit drawn from eccentric anomaly t0 to t1 (whole when omitted).
function orbitPath(cx, cy, a, b, rot, color, w = 1.5, o = {}) {
  const t0 = o.t0 ?? 0, t1 = o.t1 ?? Math.PI * 2, n = 90;
  let d = '';
  for (let i = 0; i <= n; i++) { const [px, py] = ellPt(cx, cy, a, b, rot, t0 + (t1 - t0) * i / n); d += (i ? ' L' : 'M') + px.toFixed(1) + ' ' + py.toFixed(1); }
  return path(d, color, w, { dash: o.dash });
}
// A body: a dark disc with a dim limb and its name.
function bodyDisc(cx, cy, r, name, o = {}) {
  let s = `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${o.fill || '#0B1622'}" stroke="${o.limb || '#2C4A66'}" stroke-width="1.5"/>`;
  if (name) s += text(o.lx ?? cx, o.ly ?? cy + 4, name, { size: o.size || S_LABEL, bold: true, fill: C.dim, anchor: o.anchor || 'middle', track: 0.8 });
  return s;
}
// A burn on the path: a diamond, hollow and magenta while planned, cyan when selected (with its
// handles), white dashed while a modification waits for EXEC.
function burnMark(x, y, label, state = 'planned', o = {}) {
  const c = state === 'selected' ? C.cyan : state === 'mod' ? C.white : C.magenta;
  let s = path(`M${x} ${y - 7} L${x + 7} ${y} L${x} ${y + 7} L${x - 7} ${y} Z`, c, 1.8, { fill: C.glass, dash: state === 'mod' ? '3 2' : '' });
  if (state === 'selected' && o.handles) o.handles.forEach(([hx, hy, ht]) => { s += line(x, y, x + hx, y + hy, C.cyan, 1) + rect(x + hx - 3.5, y + hy - 3.5, 7, 7, { fill: C.glass, stroke: C.cyan, sw: 1.5 }); if (ht) s += text(x + hx + (hx >= 0 ? 7 : -7), y + hy + 4, ht, { size: 11.5, bold: true, fill: C.cyan, anchor: hx >= 0 ? 'start' : 'end', track: 0.3 }); });
  if (label) s += text(x + (o.lx ?? 11), y + (o.ly ?? -9), label, { size: o.size || 12, bold: true, fill: c, anchor: o.anchor || 'start', track: 0.3 });
  return s;
}
// An apsis tick across the path with its label: AP or PE, height and the time to it.
function apsisMark(x, y, ang, label, c = C.white, o = {}) {
  const r = ang * Math.PI / 180, ux = Math.cos(r) * 6, uy = Math.sin(r) * 6;
  return line(x - ux, y - uy, x + ux, y + uy, c, 2) + text(x + (o.lx ?? 9), y + (o.ly ?? 4), label, { size: o.size || 11.5, bold: true, fill: o.labelColor || c, anchor: o.anchor || 'start', track: 0.3 });
}
// The scale bar: a 100-unit rule with its distance.
function scaleBar(x, y, w, t) {
  return line(x, y, x + w, y, C.dim, 1.5) + line(x, y - 4, x, y + 1, C.dim, 1.5) + line(x + w, y - 4, x + w, y + 1, C.dim, 1.5) + text(x + w / 2, y - 7, t, { size: S_LABEL, bold: true, fill: C.dim, anchor: 'middle', track: 0.5 });
}
// The cursor on a plot: a cyan cross, with the readout of what is under it.
function plotCursor(x, y, t) {
  let s = line(x - 10, y, x - 3, y, C.cyan, 1.5) + line(x + 3, y, x + 10, y, C.cyan, 1.5) + line(x, y - 10, x, y - 3, C.cyan, 1.5) + line(x, y + 3, x, y + 10, C.cyan, 1.5);
  if (t) s += text(x + 13, y + 16, t, { size: 11.5, bold: true, fill: C.cyan, track: 0.3 });
  return s;
}

// ---------- the system plot ----------

// A circle outline.
function ring(cx, cy, r, c, w = 1, o = {}) { return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${o.fill || 'none'}" stroke="${c}" stroke-width="${w}" ${o.dash ? `stroke-dasharray="${o.dash}"` : ''}/>`; }

// The Earth and Moon system as the map draws it at system scale, north up: the Moon's orbit, the
// bodies where they are now, the transfer from the ship's orbit to the Moon (patched at the Moon's
// sphere of influence, which is drawn round the Moon where it will be at the encounter), the burns,
// and the stations, traffic and pirates of the layers that are on. Bodies and the orbits close to
// them have a least size, as on the map, since the Earth is 4 units across at this scale.
// o: { ex, ey (the Earth), R (the Moon's orbit, units), now, at (the Moon's angle now and at the
// encounter, degrees counter-clockwise from east), plan: 'planned' | 'mod' | null, selected: burn
// index, layers: { stations, traffic, pirates }, prop (burn 1's prograde label) }.
// Returns { svg, earth, moonNow, moonAt, burns: [[x, y]], pathAt(f): a point a share f along the
// transfer }.
function systemPlot(o) {
  const ex = o.ex, ey = o.ey, R = o.R, k = R / 384400, L = o.layers || {};
  const rad = d => d * Math.PI / 180;
  const polar = (r, deg) => [ex + r * Math.cos(rad(deg)), ey - r * Math.sin(rad(deg))];
  const pc = o.plan === 'mod' ? C.white : C.magenta, pw = o.plan === 'mod' ? 2 : 2.5, pdash = o.plan === 'mod' ? '7 4' : '';
  let s = '';
  // other bodies' orbits: the Moon's, Freehold's
  s += ring(ex, ey, R, C.graticule, 1.2) + ring(ex, ey, 200000 * k, '#101820', 1);
  // the Earth, the ship's orbit (400 km, drawn at the least size) and the ship on it
  const rOrb = 9, A = rad(o.at), u = [Math.cos(A), Math.sin(A)], v = [-Math.sin(A), Math.cos(A)];
  s += `<circle cx="${ex}" cy="${ey}" r="5.5" fill="#0B1622" stroke="#2C6A96" stroke-width="1.5"/>`;
  s += ring(ex, ey, rOrb, C.white, 1.5);
  // the transfer: an ellipse with its periapsis on the ship's orbit and its apoapsis at the Moon's
  // orbit where the Moon will be, run from periapsis to the sphere of influence
  const rp = rOrb, ra = R, a = (rp + ra) / 2, c = a - rp, b = Math.sqrt(rp * ra);
  const Pm = t => [c * u[0] + a * Math.cos(t) * u[0] + b * Math.sin(t) * v[0], c * u[1] + a * Math.cos(t) * u[1] + b * Math.sin(t) * v[1]];
  const P = t => { const [X, Y] = Pm(t); return [ex + X, ey - Y]; };
  // rc: the orbit the plan leaves the ship in round the Moon (100 km), drawn at its least size
  const moonAt = polar(R, o.at), moonNow = polar(R, o.now), rSoi = 66100 * k, rc = 15;
  let lo = Math.PI, hi = 2 * Math.PI;
  for (let i = 0; i < 40; i++) { const m = (lo + hi) / 2, [px, py] = P(m); if (Math.hypot(px - moonAt[0], py - moonAt[1]) > rSoi) lo = m; else hi = m; }
  const tSoi = lo;
  const pathAt = f => P(Math.PI + (tSoi - Math.PI) * f);
  // the Moon now, the Moon at the encounter (dashed) and its sphere of influence
  s += ring(moonAt[0], moonAt[1], rSoi, '#2A3B4C', 1, { dash: '4 3' });
  s += ring(moonAt[0], moonAt[1], 4.5, '#4A6276', 1.2, { dash: '2 2' });
  s += `<circle cx="${moonNow[0]}" cy="${moonNow[1]}" r="4.5" fill="#141A20" stroke="#6E7C88" stroke-width="1.3"/>`;
  // traffic and pirates under the plan
  if (L.traffic) (o.haulers || []).forEach(([hx, hy, dx, dy]) => { s += line(hx, hy, hx + dx, hy + dy, C.dim, 1) + sym.square(hx, hy, 3, C.white, 1.2); });
  if (L.pirates) (o.pirates || []).forEach(([px, py, name]) => { s += sym.diamond(px, py, 4, C.red, 1.4) + (name ? text(px + 9, py + 4, name, { size: S_LABEL, bold: true, fill: C.red, track: 0.5 }) : ''); });
  if (L.stations) {
    const fh = polar(200000 * k, o.freehold ?? o.now + 150);
    s += sym.station(fh[0], fh[1], 4, C.white, 1.2) + text(fh[0] + 9, fh[1] + 4, 'FREEHOLD', { size: S_LABEL, bold: true, fill: C.white, track: 0.5 });
  }
  // the planned path
  if (o.plan) {
    let d = '';
    for (let i = 0; i <= 120; i++) { const [px, py] = P(Math.PI + (tSoi - Math.PI) * i / 120); d += (i ? ' L' : 'M') + px.toFixed(1) + ' ' + py.toFixed(1); }
    // inside the sphere of influence: round the Moon to the capture burn on its far side, then the
    // orbit the plan leaves the ship in. o.leg 'soi': the plan holds only the leg to the crossing,
    // as the game plans a trip to another body a leg at a time (the capture is planned there), so
    // the path stops at the sphere with a bar across its end
    const [sx, sy] = P(tSoi), q = [moonAt[0] + rc * Math.cos(A), moonAt[1] - rc * Math.sin(A)];
    const cpt = [moonAt[0] - 20 * v[0] + 22 * u[0], moonAt[1] + 20 * v[1] - 22 * u[1]];
    if (o.leg !== 'soi') d += ` Q${cpt[0].toFixed(1)} ${cpt[1].toFixed(1)} ${q[0].toFixed(1)} ${q[1].toFixed(1)}`;
    s += (o.plan === 'mod' ? '' : path(d, C.halo, pw + 2)) + path(d, pc, pw, { dash: pdash });
    if (o.leg !== 'soi') s += ring(moonAt[0], moonAt[1], rc, pc, 1.5, { dash: pdash });
    else {
      const [qx, qy] = P(tSoi - 0.01), tl = Math.hypot(sx - qx, sy - qy), nx = -(sy - qy) / tl, ny = (sx - qx) / tl;
      s += line(sx - 6 * nx, sy - 6 * ny, sx + 6 * nx, sy + 6 * ny, pc, 2.5);
    }
  }
  // the burns: burn 1 at periapsis on the ship's orbit, burn 2 at the capture
  const b1 = P(Math.PI), b2 = [moonAt[0] + rc * Math.cos(A), moonAt[1] - rc * Math.sin(A)];
  if (o.plan) {
    const st = i => o.selected === i ? 'selected' : o.plan === 'mod' ? 'mod' : 'planned';
    // burn 1's handles: prograde along the path's direction there, radial out from the Earth
    const [nx, ny] = P(Math.PI + 0.02), tl = Math.hypot(nx - b1[0], ny - b1[1]), tx = (nx - b1[0]) / tl, ty = (ny - b1[1]) / tl;
    const rx = (b1[0] - ex) / rOrb, ry = (b1[1] - ey) / rOrb;
    s += burnMark(b1[0], b1[1], o.b1label ?? 'BURN 1', st(0), { handles: [[tx * 34, ty * 34, o.prop], [rx * 26, ry * 26, o.rad]], lx: o.b1lx ?? -12, ly: o.b1ly ?? -12, anchor: 'end' });
    if (o.leg !== 'soi') s += burnMark(b2[0], b2[1], o.b2label ?? 'BURN 2', st(1), { lx: o.b2lx ?? 12, ly: o.b2ly ?? -10 });
  }
  // the ship now, a little past burn 1's point (burn 1 is most of an orbit away); o.shipLead: the
  // ship's angle from burn 1's point along the orbit, degrees (negative: the burn is still ahead)
  const sh = polar(rOrb, o.at + 180 + (o.shipLead ?? 14));
  s += `<circle cx="${sh[0]}" cy="${sh[1]}" r="2.6" fill="${C.white}"/>`;
  return { svg: s, earth: [ex, ey], moonNow, moonAt, burns: [b1, b2], pathAt, rSoi, soi: P(tSoi) };
}

// A plan timeline: now at x, then the plan's events to its end (o.end, hours) on a log scale (o.t0:
// the hours that take about the first tenth). events: [{ h: hours from now, label, c, shape:
// 'burn' | 'tick' | 'flag', dash, row }]: row 0 puts the label over its mark; otherwise it goes
// under the axis at e.lx (units from x), on a leader that leaves the axis, runs across at e.level
// units under it and drops to the label, so events that crowd at the end keep readable names.
// ticks: [[hours, label]] over the axis.
function timelineStrip(x, y, w, events, o = {}) {
  const T = o.end, t0 = o.t0 || 6, f = h => Math.log(1 + h / t0) / Math.log(1 + T / t0);
  let s = line(x, y, x + w, y, C.dim, 1.2);
  (o.ticks || []).forEach(([h, t]) => { const tx = x + w * f(h); s += line(tx, y - 4, tx, y + 4, C.dim, 1) + text(tx, y - 10, t, { size: S_LABEL, bold: true, fill: '#5C6670', anchor: 'middle', track: 0.4 }); });
  s += line(x, y - 9, x, y + 9, C.white, 2.5) + text(x + 4, y + 24, 'NOW', { size: S_LABEL, bold: true, fill: C.white, track: 0.6 });
  events.forEach(e => {
    const px = x + w * f(e.h), c = e.c || C.magenta;
    if (e.shape === 'tick') s += line(px, y - 7, px, y + 7, c, 2.2);
    else if (e.shape === 'flag') s += path(`M${px} ${y + 7} V${y - 10} L${px + 8} ${y - 6.5} L${px} ${y - 3}`, c, 1.6);
    else s += path(`M${px} ${y - 6} L${px + 6} ${y} L${px} ${y + 6} L${px - 6} ${y} Z`, c, 1.6, { fill: C.glass, dash: e.dash });
    if (e.row === 0) { s += text(px, y - 12, e.label, { size: S_LABEL, bold: true, fill: c, anchor: 'middle', track: 0.5 }); return; }
    const lx = e.lx == null ? px : x + e.lx, lv = y + (e.level ?? 12);
    s += path(`M${px} ${y + 8} V${lv} H${lx} V${y + 27}`, '#3E4A56', 1);
    s += text(lx, y + 40, e.label, { size: S_LABEL, bold: true, fill: c, anchor: 'middle', track: 0.5 });
  });
  return s;
}

// ---------- dimming ----------

// Everything behind an interrupt or a menu drawn at a share of its light: the context spec's X3
// dims to 40%, the pause menu the same.
function dimmed(svg, k = 0.4) { return `<g opacity="${k}">${svg}</g>`; }
