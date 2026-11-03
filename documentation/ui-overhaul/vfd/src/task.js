// FILAMENT x Task-driven design (functional-task.md): the HUD as a late-90s digital dash. No racks span
// the view; each corner carries its own moulded pod, like an instrument cluster, a trip computer and a
// message centre mounted on the glass. The annunciator becomes a cluster of tell-tales, the alert band a
// message centre whose rows are filtered red (warning) and amber (cautions), the hint line a printed
// legend strip whose legends go dark as the player learns them.
(() => {
  const K = KIT, V = VFD;
  const R = Math.PI / 180;

  // ------------------------------------------------------------------ small parts
  // An icon cut out of whatever is lit (a tell-tale's icon in a red block).
  function knockIcon(S, name, x, y, size) {
    const parts = V.ICONS[name]; if (!parts) return;
    const ctx = S.glass, k = size / 16;
    ctx.save(); ctx.globalCompositeOperation = 'destination-out'; ctx.translate(x, y); ctx.scale(k, k);
    ctx.fillStyle = '#000'; ctx.strokeStyle = '#000'; ctx.lineWidth = 1.7;
    for (const pt of parts) {
      if (pt[0] === 'p') { ctx.beginPath(); pt[1].forEach((q, i) => (i ? ctx.lineTo(q[0], q[1]) : ctx.moveTo(q[0], q[1]))); ctx.closePath(); ctx.fill(); }
      else if (pt[0] === 'l') { ctx.beginPath(); pt[1].forEach((q, i) => (i ? ctx.lineTo(q[0], q[1]) : ctx.moveTo(q[0], q[1]))); ctx.stroke(); }
      else if (pt[0] === 'c') { ctx.beginPath(); ctx.arc(pt[1], pt[2], pt[3], (pt[4] - 90) * R, (pt[5] - 90) * R); ctx.stroke(); }
      else if (pt[0] === 'd') { ctx.beginPath(); ctx.arc(pt[1], pt[2], pt[3], 0, Math.PI * 2); ctx.fill(); }
    }
    ctx.restore();
  }
  // A graphic dot panel's grid, seen unlit through the glass.
  function grid(S, x, y, w, h, p = 4) {
    for (let yy = y + p / 2; yy < y + h; yy += p) for (let xx = x + p / 2; xx < x + w; xx += p) S.dot(xx, yy, { size: 1.05, ghostOnly: true, k: 0.9 });
  }
  function ring(S, cx, cy, r, o = {}) {
    const n = Math.max(12, Math.round(2 * Math.PI * r / (o.step ?? 4)));
    for (let i = 0; i < n; i++) { const a = i / n * Math.PI * 2; S.dot(cx + Math.cos(a) * r, cy + Math.sin(a) * r, { size: o.size ?? 1.2, level: o.level ?? 'dim', color: o.color }); }
  }

  // ------------------------------------------------------------------ Z1 + Z4: the cluster (CHR over ANN)
  const CL = { x: 16, y: 12, w: 244 };
  function cluster(S, d) {
    K.plate(S, CL.x, CL.y, CL.w, 124, { pad: 6, radius: 8 });
    chr(S, d.chr);
    ann(S, d.ann || {});
  }
  // CHR: day and time big, then warp, and the next stop or why warp is held.
  function chr(S, d) {
    const x = CL.x + 2, y = CL.y + 2, w = CL.w - 4, h = 50;
    S.module(x, y, w, h, { filaments: 3 });
    S.legend(x + 7, y + 21, 'DAY', { size: 8.5, level: 'dim' });
    S.seg7(x + 28, y + 6, 18, d.day, { cells: 3, level: 'norm' });
    S.seg7(x + 98, y + 6, 18, d.time, { cells: 6, level: 'norm' });
    // what the window below holds: the next stop, or why warp is held
    S.legend(x + 232, y + 14, 'NEXT', { size: 7.5, lit: !!d.next, level: 'dim', align: 'right' });
    S.legend(x + 232, y + 25, 'HELD', { size: 7.5, lit: !!d.held, level: 'norm', align: 'right' });
    S.legend(x + 7, y + 45, 'WARP', { size: 8.5, level: 'dim' });
    S.seg7(x + 33, y + 35, 10, d.warp, { cells: 5, level: d.warp === '1' ? 'dim' : 'norm' });
    S.legend(x + 75, y + 45, 'X', { size: 9, level: d.warp === '1' ? 'dim' : 'norm' });
    S.legend(x + 84, y + 45, 'RAILS', { size: 7.5, lit: !!d.rails, level: 'norm' });
    S.dm(x + 110, y + 36, d.held || d.next || '', { pitch: 1.5, cells: 13, level: d.held ? 'norm' : 'dim' });
  }
  // ANN: ten tell-tales, each an icon and a word in a cell filtered both ways (amber frame, red block).
  const LAMPS = [['HULL', 'breach'], ['FIRE', 'fire'], ['AIR', 'o2'], ['POWER', 'power'], ['PROP', 'prop'],
    ['DRIVE', 'drive'], ['ARMS', 'missile'], ['CREW', 'crew'], ['EVA', 'eva'], ['SHLD', 'shield']];
  function ann(S, st) {
    const x = CL.x + 2, y = CL.y + 58, w = CL.w - 4, h = 62;
    S.module(x, y, w, h, { filaments: 0 });
    const cw = 44.4, ch = 26, gx = 3.5, gy = 3;
    LAMPS.forEach(([tag, icon], i) => {
      const cx = x + 3.5 + (i % 5) * (cw + gx), cy = y + 3.5 + Math.floor(i / 5) * (ch + gy);
      lamp(S, cx, cy, cw, ch, tag, icon, st[tag]);
    });
  }
  function lamp(S, x, y, w, h, tag, icon, sev) {
    const ix = x + 4, iy = y + h / 2 - 5.5, tx = x + 18, ty = y + h / 2 + 3.2;
    if (sev === 'warning') {
      S.rectLit(x + 1.5, y + 1.5, w - 3, h - 3, { color: 'red', level: 'norm', r: 1.2 });
      knockIcon(S, icon, ix, iy, 11);
      S.knockout(tx, ty, tag, { size: 8.5 });
      return;
    }
    if (sev === 'caution') {
      S.frameLit(x, y, w, h, 1.1, { color: 'amber', level: 'norm' });
      S.icon(icon, ix, iy, 11, { color: 'amber', level: 'norm', stroke: 1.6 });
      S.legend(tx, ty, tag, { size: 8.5, color: 'amber', level: 'full' });
      return;
    }
    S.frameLit(x, y, w, h, 1.1, { ghostOnly: true, color: 'amber', k: 1.2 });
    S.icon(icon, ix, iy, 11, { lit: false, stroke: 1.6 });
    S.legend(tx, ty, tag, { size: 8.5, lit: false });
  }

  // ------------------------------------------------------------------ Z4: PER, the captain's own kit (LCD)
  function per(S, d) {
    const x = CL.x, y = 148, w = CL.w, h = 64;
    K.plate(S, x, y, w, h, { pad: 6, radius: 8 });
    S.silk(x + 2, y - 0.5, 'You', { size: 6.5 });
    S.lcdPanel(x + 2, y + 4, w - 4, h - 6, { backlight: d.alert });
    S.lcdText(x + 10, y + 19, d.health, { size: 9 });
    S.lcdText(x + 10, y + 34, d.suit, { size: 9 });
    S.lcdText(x + 10, y + 49, d.here, { size: 9 });
    S.lcdText(x + 158, y + 16, 'SUIT AIR', { size: 8 });
    S.lcdSeg7(x + 158, y + 22, 26, d.air, { slant: 6 });
  }

  // ------------------------------------------------------------------ Z2: the message centre (ALR)
  // The warning window sits behind red glass, the caution row behind amber: a warning can only ever be red
  // because there is nowhere else for it to light, and a caution can only ever be amber.
  const AL = { x: 362, w: 556 };
  function alrWarning(S, d) {
    const x = AL.x, y = 12, w = AL.w, h = 48;
    K.slab(S, x, y, w, h, { pad: 5, title: 'Warning' });
    S.seg14(x + 8, y + 8, 18, d.title, { cells: 8, level: 'full', color: 'red' });
    S.seg7(x + 160, y + 8, 18, d.count, { cells: 1, level: 'norm', color: 'red' });
    S.legend(x + 176, y + 25, 'INBOUND', { size: 9.5, color: 'red', level: 'norm' });
    S.legend(x + 236, y + 25, 'BRG', { size: 8.5, color: 'red', level: 'dim' });
    S.seg7(x + 256, y + 13, 12, d.brg, { cells: 3, level: 'norm', color: 'red' });
    // the one number that matters, biggest, at the end of the row where the eye lands
    S.legend(x + 404, y + 25, 'IMPACT', { size: 10, color: 'red', level: 'norm' });
    S.seg7(x + 450, y + 4, 30, d.t, { cells: 2, level: 'full', color: 'red' });
    S.legend(x + 500, y + 32, 'S', { size: 11, color: 'red', level: 'norm' });
    // the action that fits the post, with its key
    let ax = x + 8;
    ax += K.keycap(S, ax, y + 32, d.key, { h: 12 }) + 4;
    ax += S.dm(ax, y + 33, d.action, { pitch: 1.5, color: 'red', level: 'norm', ghost: false }) + 14;
    S.dm(ax, y + 33, d.note, { pitch: 1.5, color: 'red', level: 'dim', ghost: false });
  }
  // Up to three cautions, each two lines: what and where, then who is on it.
  function alrCautions(S, list) {
    const x = AL.x, y = 72, w = AL.w, h = 32;
    K.slab(S, x, y, w, h, { pad: 5, title: 'Cautions' });
    const cw = (w - 8) / 3;
    for (let i = 0; i < 3; i++) {
      const c = list[i], cx = x + 4 + i * (cw + 4);
      if (i) S.rectLit(cx - 2.5, y + 5, 0.8, h - 10, { ghostOnly: true, color: 'amber', k: 2 });
      S.dm(cx + 3, y + 4, c ? c.what : '', { pitch: 1.5, cells: 19, color: 'amber', level: 'norm' });
      S.dm(cx + 3, y + 17, c ? c.who : '', { pitch: 1.5, cells: 19, color: 'amber', level: 'dim' });
    }
  }

  // ------------------------------------------------------------------ Z3: OBJ and NTF
  function obj(S, rows, y = 12) {
    const x = 1026, w = 240, h = 6 + rows.length * 31;
    K.slab(S, x, y, w, h, { pad: 5, title: 'Objectives' });
    let ry = y + 5;
    for (const r of rows) {
      S.dm(x + 6, ry, r.l1, { pitch: 1.5, cells: 20, level: 'norm' });
      S.seg7(x + 193, ry + 0.5, 10, r.left, { cells: 5, level: 'norm' });
      S.dm(x + 15, ry + 14, r.l2 || '', { pitch: 1.5, cells: 21, level: 'dim' });
      // LATE has its own amber place at the row's end
      S.legend(x + 234, ry + 23.5, 'LATE', { size: 7.5, color: 'amber', lit: !!r.late, level: 'norm', align: 'right' });
      ry += 31;
    }
    return y + h;
  }
  function ntf(S, y, lines) {
    const x = 1026, w = 240, h = 6 + lines.length * 15;
    K.slab(S, x, y, w, h, { pad: 5, title: 'News' });
    lines.forEach((l, i) => S.dm(x + 6, y + 4 + i * 15, l, { pitch: 1.5, cells: 25, level: 'dim' }));
  }

  // ------------------------------------------------------------------ Z6: TGT
  function tgt(S, d) {
    const x = 1018, y = 352, w = 248, h = 196;
    K.slab(S, x, y, w, h, { pad: 5, title: 'Target' });
    // relation: five fixed words, only the true one lit, HOSTILE behind red glass
    K.states(S, x + 7, y + 14, [['OWN', false], ['FRIEND', false], ['NEUTRAL', false], ['HOSTILE', d.rel === 'HOSTILE' && 'norm', 'red'], ['INERT', false]], { size: 8, gap: 7 });
    S.dm(x + 7, y + 21, d.name, { pitch: 2, cells: 14, level: 'norm' });
    S.dm(x + 7, y + 39, d.cls, { pitch: 1.5, cells: 25, level: 'dim' });
    K.states(S, x + 7, y + 63, [['FIRING ON YOU', d.firing && 'full', 'red'], ['HAS YOU LOCKED', d.locked && 'norm', 'red']], { size: 8.5, gap: 10 });
    const lab = (t, ly) => S.legend(x + 7, ly, t, { size: 8, level: 'dim' });
    lab('STATE', y + 82); S.dm(x + 52, y + 73, d.state, { pitch: 1.5, cells: 21, level: 'norm' });
    // hull as the sensors guess it: four steps, never a percentage the sensors can't know
    lab('HULL', y + 100);
    S.bar(x + 52, y + 92, 60, 8, 4, d.hull / 4, { level: 'norm', gap: 2 });
    S.dm(x + 120, y + 91, d.hullWord, { pitch: 1.5, cells: 9, level: 'norm' });
    S.legend(x + 206, y + 100, 'EST', { size: 7.5, level: 'dim' });
    lab('RANGE', y + 124);
    S.seg7(x + 52, y + 109, 17, d.range, { cells: 3, level: 'norm' }); S.legend(x + 100, y + 124, 'KM', { size: 8, level: 'dim' });
    K.states(S, x + 124, y + 113, [['CLOSING', d.closing === 'closing' && 'dim'], ['OPENING', d.closing === 'opening' && 'dim']], { size: 7.5, gap: 5 });
    S.seg7(x + 124, y + 116, 11, d.rate, { cells: 3, level: 'norm' }); S.legend(x + 156, y + 126, 'M/S', { size: 7.5, level: 'dim' });
    lab('CPA', y + 148);
    S.seg7(x + 52, y + 137, 12, d.cpa, { cells: 3, level: 'norm' }); S.legend(x + 90, y + 148, 'KM IN', { size: 8, level: 'dim' });
    S.seg7(x + 120, y + 137, 12, d.cpaT, { cells: 3, level: 'norm' });
    S.rectLit(x + 7, y + 158, w - 14, 0.7, { ghostOnly: true, k: 2 });
    S.icon('target', x + 8, y + 165, 11, { level: 'dim', lit: !!d.contract });
    S.dm(x + 24, y + 166, d.contract || '', { pitch: 1.5, cells: 24, level: 'dim' });
  }

  // ------------------------------------------------------------------ Z7: the weapons pod (WPN + SNS)
  function weaponsPod(S) {
    K.rackShape(S, [[794, 552], [1086, 552], [1086, 594], [1290, 594], [1290, 808], [794, 808]], { radius: 8 });
  }
  function scopePod(S) {
    K.rackShape(S, [[1078, 594], [1290, 594], [1290, 808], [1078, 808]], { radius: 8 });
  }
  // WPN expanded: a row per mount, then launcher, decoys, shields with their sectors, heat.
  function wpn(S, d) {
    const x = 802, y = 560, w = 278, h = 228;
    S.silk(x + 1, y - 3.5, 'Weapons', { size: 6.5 });
    S.module(x, y, w, h, { filaments: 4 });
    const hd = (t, hx) => S.silk(hx, y + 9, t, { size: 6, color: '#7b8288' });
    hd('MOUNT', x + 16); hd('GUN', x + 60); hd('WHO', x + 113); hd('STATE', x + 156); hd('TOF', x + 249);
    d.mounts.forEach((m, i) => {
      const ry = y + 14 + i * 15;
      // pips first: amber for a gun that can't fire for now, red for one that's broken
      K.pip(S, x + 6, ry + 5.2, 'amber', m.sev === 'caution', { r: 1.5 }); K.pip(S, x + 11, ry + 5.2, 'red', m.sev === 'warning', { r: 1.5 });
      S.dm(x + 16, ry, m.pos, { pitch: 1.5, cells: 4, level: 'norm' });
      S.dm(x + 60, ry, m.gun, { pitch: 1.5, cells: 5, level: 'dim' });
      S.dm(x + 113, ry, m.who, { pitch: 1.5, cells: 4, level: 'dim' });
      S.dm(x + 156, ry, m.state, { pitch: 1.5, cells: 10, level: 'norm' });
      S.seg7(x + 249, ry + 0.5, 10, m.tof || '', { cells: 3, level: 'dim' });
    });
    const by = y + 14 + d.mounts.length * 15 + 4;
    S.rectLit(x + 6, by - 2, w - 12, 0.7, { ghostOnly: true, k: 2 });
    const lab = (t, ly) => S.legend(x + 7, ly, t, { size: 8.5, level: 'dim' });
    // launcher and decoys
    lab('MSL', by + 11); S.dm(x + 36, by + 2, d.msl.type, { pitch: 1.5, cells: 7, level: 'dim' });
    S.seg7(x + 104, by + 1.5, 11, d.msl.left, { cells: 1, level: 'norm' }); S.legend(x + 114, by + 11, '/', { size: 9, level: 'dim' }); S.seg7(x + 120, by + 1.5, 11, d.msl.of, { cells: 1, level: 'dim' });
    K.states(S, x + 142, by + 11, [['READY', d.msl.ready && 'norm'], ['LOCK', d.msl.lock && 'norm']], { size: 8.5, gap: 8 });
    lab('DECOY', by + 27);
    K.states(S, x + 50, by + 27, [['READY', d.decoy === 'READY' && 'norm']], { size: 8.5 });
    S.seg7(x + 88, by + 17.5, 11, d.decoy === 'READY' ? '' : d.decoy, { cells: 1, level: 'norm' }); S.legend(x + 98, by + 27, 'S', { size: 8, level: 'dim' });
    // shields: state, cloud share, trim, and the four sectors as a ring of rails (thickness in three steps)
    lab('SHLD', by + 45);
    K.states(S, x + 40, by + 45, [['UP', d.shld.state === 'UP' && 'norm'], ['DOWN', d.shld.state === 'DOWN' && 'norm'], ['STOW', d.shld.state === 'STOW' && 'norm']], { size: 8.5, gap: 6 });
    S.seg7(x + 40, by + 52, 15, d.shld.cloud, { cells: 3, level: 'norm' }); S.legend(x + 80, by + 66, '%', { size: 8, level: 'dim' });
    S.legend(x + 96, by + 66, 'TRIM', { size: 8, level: 'dim' }); S.dm(x + 122, by + 57, d.shld.trim, { pitch: 1.5, cells: 4, level: 'norm' });
    const rcx = x + 228, rcy = by + 46;
    // sector order: bow (right, as the ship is drawn), starboard (down), stern, port (up)
    const sec = [d.shld.bow, d.shld.stbd, d.shld.stern, d.shld.port];
    for (let rail = 0; rail < 3; rail++) {
      const r0 = 9 + rail * 6, r1 = r0 + 4.2;
      S.ring(rcx, rcy, r0, r1, 4, i => (sec[i] > [0, 34, 67][rail] ? { level: rail === 2 ? 'norm' : 'dim' } : null), { start: 45, gapDeg: 10 });
    }
    S.sprite(rcx, rcy, ['.#.', '###', '.#.'], { center: true, pitch: 1.4, level: 'dim' });
    S.silk(rcx, rcy + 35, 'SECTORS', { size: 5.5, align: 'center', color: '#7b8288' });
    lab('HEAT', by + 86);
    S.bar(x + 40, by + 80, 120, 6, 20, d.heat, { zone: K.RISING, level: 'dim' });
  }
  // SNS: a graphic dot panel, square, north up, ranges as dotted rings.
  function sns(S, d) {
    const x = 1086, y = 602, w = 178, h = 168;
    S.silk(x + 1, y - 3.5, 'Scope', { size: 6.5 });
    S.module(x, y, w, h, { filaments: 3 });
    grid(S, x + 2, y + 2, w - 4, h - 4, 4);
    const cx = x + w / 2, cy = y + 86, r = 74;
    ring(S, cx, cy, r * 0.5, { level: 'dim' }); ring(S, cx, cy, r, { level: 'dim' });
    S.dotLine(cx, cy, cx + Math.cos(d.heading * R) * (r - 6), cy + Math.sin(d.heading * R) * (r - 6), { step: 3, size: 1.2, level: 'dim', layer: 'glass' });
    S.sprite(cx, cy, ['.#.', '###', '.#.'], { center: true, pitch: 1.6, level: 'norm' });
    for (const c of d.contacts) {
      const px = cx + c.x * r, py = cy + c.y * r;
      if (c.kind === 'hostile') S.sprite(px, py, ['..#..', '.#.#.', '#####'], { center: true, pitch: 1.6, color: 'red', level: 'norm' });
      if (c.kind === 'missile') S.sprite(px, py, ['.#.', '###', '.#.'], { center: true, pitch: 1.3, color: 'red', level: 'full' });
      if (c.kind === 'neutral') S.sprite(px, py, ['..#..', '.#.#.', '#...#', '.#.#.', '..#..'], { center: true, pitch: 1.4, level: 'dim' });
      if (c.kind === 'station') S.sprite(px, py, ['#####', '#...#', '#.#.#', '#...#', '#####'], { center: true, pitch: 1.4, level: 'norm' });
      if (c.lock) for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) for (const [ax, ay] of [[0, 0], [-sx, 0], [0, -sy]]) S.dot(px + sx * 7 + ax * 2, py + sy * 7 + ay * 2, { size: 1.3, level: 'full' });
    }
    // range and its mode, and below it the hostile count
    S.legend(x + 6, y + h - 5, d.range, { size: 8.5, level: 'norm' });
    S.legend(x + 46, y + h - 5, 'AUTO', { size: 8, lit: !!d.auto, level: 'dim' });
    S.key(x + w - 50, y + h + 6, 20, 11, '−', {}); S.key(x + w - 25, y + h + 6, 20, 11, '+', {});
    if (d.hostiles != null) {
      S.seg7(x + w - 66, y + 6, 11, String(d.hostiles), { cells: 1, level: 'norm', color: 'red' });
      S.legend(x + w - 6, y + 15, d.hostiles === 1 ? 'HOSTILE' : 'HOSTILES', { size: 8, color: 'red', level: 'norm', align: 'right' });
    }
  }

  // ------------------------------------------------------------------ Z5: SCH, the ship schematic
  function sch(S, d) {
    const x = 16, y = 606, w = 244, h = 178;
    K.plate(S, x, y, w, h, { pad: 6, radius: 8 });
    S.silk(x + 2, y - 0.5, 'Ship', { size: 6.5 });
    S.module(x + 2, y + 4, w - 4, h - 6, { filaments: 3 });
    grid(S, x + 4, y + 6, w - 8, h - 10, 4);
    K.shipPlan(S, x + 8, y + 14, w - 16, h - 36, { states: d.states, crew: d.crew, labels: true });
  }

  // ------------------------------------------------------------------ Z8: FLT and HLM
  const FL = { x: 420, y: 662, w: 440, h: 98 };
  function flt(S, d) {
    const { x, y, w, h } = FL;
    K.plate(S, x, y, w, h, { pad: 6, radius: 9 });
    S.module(x, y, w, h, { filaments: 4 });
    // the five modes as fixed legends, the one in use lit inverse: like a deck's PLAY / REC / FF row
    let mx = x + 10;
    for (const m of ['DEPART', 'DOCK', 'BURN', 'WARP', 'CRUISE']) {
      const mw = S.legendWidth(m, { size: 9 });
      if (m === d.mode) { S.rectLit(mx - 3.5, y + 5, mw + 7, 13, { level: 'norm', r: 1 }); S.knockout(mx, y + 15, m, { size: 9 }); }
      else S.legend(mx, y + 15, m, { size: 9, lit: false });
      mx += mw + 16;
    }
    // CRUISE: the frame and the speed in it
    S.legend(x + 10, y + 36, 'FRAME', { size: 8, level: 'dim' });
    S.dm(x + 10, y + 41, d.frame, { pitch: 1.5, cells: 13, level: 'norm' });
    S.seg7(x + 140, y + 24, 30, d.speed, { cells: 5, level: 'norm' });
    S.legend(x + 262, y + 52, 'M/S', { size: 9.5, level: 'dim' });
    // assist and the drives
    K.states(S, x + 316, y + 34, [['ASSIST', d.assist && 'norm']], { size: 9.5, box: true });
    K.states(S, x + 316, y + 54, [['MAIN', d.main && 'norm'], ['RCS', d.rcs && 'norm']], { size: 9, gap: 9 });
    // delta-v left, with what home takes as a notch and what the plan needs as a bracket
    S.legend(x + 10, y + 88, 'ΔV', { size: 9, level: 'dim' });
    S.seg7(x + 26, y + 74, 15, d.dv, { cells: 5, level: 'norm' });
    S.legend(x + 92, y + 88, 'M/S', { size: 8, level: 'dim' });
    const bx = x + 118, bw = 232, by = y + 78;
    S.bar(bx, by, bw, 8, 48, d.dv / d.dvFull, { level: 'dim', gap: 1.1 });
    const hx = bx + bw * d.home / d.dvFull;
    S.rectLit(hx - 0.7, by + 10, 1.4, 6, { level: 'norm' });
    S.dot(hx - 2.2, by + 12, { size: 1.1, level: 'norm' }); S.dot(hx + 2.2, by + 12, { size: 1.1, level: 'norm' });
    S.legend(x + 360, y + 85, 'HOME', { size: 7.5, level: 'dim' });
    S.seg7(x + 386, y + 77, 9.5, String(d.home), { cells: 4, level: 'dim' });
  }
  // HLM: who flies and the standing order, one dot-matrix line.
  function hlm(S, y, d) {
    const x = 420, w = d.w ?? 360, h = 22;
    K.slab(S, x, y, w, h, { pad: 4 });
    S.legend(x + 6, y + 14.5, 'HELM', { size: 8.5, level: 'dim' });
    S.dm(x + 36, y + 5.5, d.who, { pitch: 1.5, cells: 5, level: 'norm' });
    S.dm(x + 86, y + 5.5, d.order, { pitch: 1.5, cells: Math.floor((w - 92) / 9), level: d.dimOrder ? 'dim' : 'norm' });
  }

  // ------------------------------------------------------------------ Z9: HNT, the legend strip
  function hnt(S, items) {
    const x = 322, y = 772, h = 20;
    const w = 8 + items.reduce((a, [k, word]) => a + Math.max(12, S.legendWidth(k, { size: 7, weight: 600, condense: 0.86, track: 0.1 }) + 7) + 5 + S.legendWidth(word, { size: 9 }) + 16, 0) - 8;
    K.plate(S, x, y, w, h, { pad: 4, radius: 6 });
    let cx = x + 8;
    for (const [k, word, learned] of items) {
      cx += K.keycap(S, cx, y + 4, k, { h: 12, size: 7 }) + 5;
      cx += S.legend(cx, y + 13.5, word, { size: 9, lit: !learned, level: 'dim' }) + 16;
    }
  }

  // ------------------------------------------------------------------ world helpers
  function tag(S, x, y, lines, o = {}) {
    const w = Math.max(...lines.map(l => l.length)) * 9, h = lines.length * 14.2;
    scrim(S, x - 4, y - 4, w + 8, h + 4);
    K.label(S, x, y, lines, { pitch: 1.5, color: o.color, level: o.level ?? 'norm' });
  }
  // The deck view darkens a soft pill under each tag (the world is dimmed, the projector can't draw black).
  function scrim(S, x, y, w, h) {
    const b = S.base; b.save(); b.filter = 'blur(4px)'; b.fillStyle = 'rgba(0,0,0,0.62)';
    b.beginPath(); b.roundRect(x, y, w, h, 4); b.fill(); b.restore();
  }

  // ================================================================== Helm at rest
  SCREENS['tk-helm-quiet'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 12 });
    const k = 0.085;
    await K.hull(S, 'station_hub', 452, 286, k, 0);
    await K.hull(S, 'freighter', 812, 498, k, 150);
    await K.hull(S, 'corvette', 640, 400, k, -4);
    // glyphs only: nothing is targeted, so nothing is labelled
    K.sprite(S, 'station', 452, 286, { pitch: 2, level: 'dim' });
    K.sprite(S, 'neutral', 812, 498, { pitch: 2, level: 'dim' });
    const a = -4 * R;
    K.velMark(S, 'pro', 640 + Math.cos(a) * 92, 400 + Math.sin(a) * 92, { level: 'norm' });
    S.projectNow();

    cluster(S, { chr: { day: '3', time: '14:22:05', warp: '1' }, ann: {} });
    scopePod(S);
    sns(S, { heading: -4, range: '10 KM', auto: true, contacts: [{ x: -0.38, y: -0.24, kind: 'station' }, { x: 0.36, y: 0.2, kind: 'neutral' }, { x: 0.82, y: -0.6, kind: 'neutral' }] });
    flt(S, { mode: 'CRUISE', frame: 'ORBIT  EARTH', speed: '7672', assist: true, dv: 7007, dvFull: 9200, home: 410 });
    hnt(S, [['W S', 'THRUST'], ['A D', 'TURN'], ['M', 'MAP'], ['Q', 'DESTINATION'], ['F1', 'CONTROLS']]);
    S.finish();
  };

  // ================================================================== Gunnery in combat
  SCREENS['tk-gunnery-combat'] = async (canvas) => {
    const S = K.scene(canvas);
    K.space(S, { seed: 44 });
    await K.hull(S, 'raider', 842, 268, 0.08, 170);
    await K.hull(S, 'corvette', 640, 400, 0.1, -12);
    // the lock on Vex: zinc square brackets over red hostile corners, the lead reticle for the guns in hand
    K.brackets(S, 842, 268, 30, 'tri', { pitch: 2, len: 5, color: 'red', level: 'norm' });
    K.brackets(S, 842, 268, 40, 'square', { pitch: 2, len: 6, level: 'full', ticks: true });
    K.label(S, 892, 224, ["RAIDER 'VEX'", '3.2 KM'], { pitch: 1.5, level: 'norm' });
    K.reticle(S, 862, 300, { r: 8, level: 'norm' });
    // two missiles inbound from the port quarter, their track and the impact mark, seconds at the head
    K.missile(S, 452, 236); K.missile(S, 470, 222);
    K.track(S, [[452, 236], [604, 380]], { step: 7 }); K.track(S, [[470, 222], [612, 372]], { step: 7 });
    K.cross(S, 612, 382);
    K.label(S, 396, 206, ['14 S'], { pitch: 1.5, color: 'red', level: 'norm' });
    S.projectNow();

    cluster(S, { chr: { day: '3', time: '14:22:05', warp: '1', held: 'HOSTILES NEAR' }, ann: { FIRE: 'caution', POWER: 'caution', ARMS: 'caution' } });
    sch(S, { states: { GALLEY: 'fire' }, crew: [[14, 2, true], [6, 3], [22, 4], [18, 5]] });
    alrWarning(S, { title: 'MISSILE', count: '2', brg: '080', t: '14', key: 'F', action: 'DECOY', note: 'POINT DEFENCE ENGAGING' });
    alrCautions(S, [{ what: 'FIRE  GALLEY', who: 'BO ON IT' }, { what: 'POWER  STORE EMPTY', who: 'GUNS STARVED' }]);
    obj(S, [{ l1: "BOUNTY: RAIDER 'VEX'", left: '2d21h', l2: 'TARGET IS HERE' }]);
    tgt(S, { rel: 'HOSTILE', name: "RAIDER 'VEX'", cls: 'CORVETTE, PIRATE', firing: true, locked: false, state: 'FIGHTING',
      hull: 3, hullWord: 'DAMAGED', range: '3.2', closing: 'closing', rate: '41', cpa: '0.6', cpaT: '1:12', contract: 'BOUNTY CONTRACT' });
    weaponsPod(S);
    wpn(S, {
      mounts: [
        { pos: 'NOSE', gun: 'COIL', who: 'HAND', state: 'READY', tof: '2.1' },
        { pos: 'PORT', gun: 'AUTO', who: 'HAND', state: 'NO POWER', sev: 'caution' },
        { pos: 'STBD', gun: 'AUTO', who: 'HAND', state: 'RELOAD .8' , tof: '1.6' },
        { pos: 'TOP', gun: 'LASER', who: 'HAND', state: 'OUT OF ARC' },
        { pos: 'PD', gun: 'PD', who: 'AUTO', state: '2 TRACKS' },
      ],
      msl: { type: 'HARPOON', left: '4', of: '6', ready: true, lock: true },
      decoy: 'READY',
      shld: { state: 'UP', cloud: '62', trim: 'BOW', bow: 80, port: 55, stern: 40, stbd: 70 },
      heat: 0.46,
    });
    sns(S, { heading: -12, range: '10 KM', auto: true, hostiles: 1, contacts: [{ x: 0.28, y: -0.18, kind: 'hostile', lock: true }, { x: -0.25, y: -0.22, kind: 'missile' }, { x: -0.22, y: -0.25, kind: 'missile' }, { x: -0.7, y: 0.5, kind: 'station' }] });
    hlm(S, 736, { who: 'ADA', order: 'HOLDING 2,000 M OFF RAIDER' });
    hnt(S, [['SPACE', 'FIRE'], ['R', 'MISSILE'], ['F', 'DECOY'], ['9', 'SHIELDS'], ['TAB', 'NEXT TARGET'], ['V', 'STAND']]);
    S.finish();
  };

  // ================================================================== Deck, damage control
  SCREENS['tk-deck-dc'] = async (canvas) => {
    const S = K.scene(canvas);
    // the deck at 1.6x: the whole corvette across the view
    const Z = 1.6, cx = 701, cy = 483, X = ux => 640 + Z * (ux - cx), Y = uy => 400 + Z * (uy - cy);
    await K.world(S, 'bg/deck-lit.jpg', { x: 640 - Z * cx, y: 400 - Z * cy, w: 1280 * Z, h: 800 * Z });
    // the fire in the crew spaces, drawn as the game would: a glow over the burning tiles
    const b = S.base;
    for (const g of [{ x: X(842), y: Y(520), r: 54, c: 'rgba(255,120,30,0.42)' }, { x: X(860), y: Y(508), r: 30, c: 'rgba(255,190,80,0.5)' }, { x: X(826), y: Y(530), r: 22, c: 'rgba(255,170,60,0.45)' }]) {
      b.save(); b.globalCompositeOperation = 'lighter';
      const rg = b.createRadialGradient(g.x, g.y, 0, g.x, g.y, g.r); rg.addColorStop(0, g.c); rg.addColorStop(1, 'rgba(0,0,0,0)');
      b.fillStyle = rg; b.fillRect(g.x - g.r, g.y - g.r, g.r * 2, g.r * 2); b.restore();
    }
    // deck overlays, projected: each hazard tagged with who is on it
    S.icon('fire', X(842) - 40, Y(520) - 76, 12, { layer: 'proj', level: 'norm' });
    tag(S, X(842) - 24, Y(520) - 76, ['FIRE (MED.)', 'BO ON IT']);
    S.icon('power', X(940) - 52, Y(444) - 58, 12, { layer: 'proj', level: 'norm' });
    tag(S, X(940) - 36, Y(444) - 58, ['SHORT: REACTOR', 'ADA: RESET 6 S']);
    S.icon('breach', X(560) - 40, Y(470) - 44, 12, { layer: 'proj', level: 'norm' });
    tag(S, X(560) - 24, Y(470) - 44, ['BREACH', 'NOBODY']);
    // the captain, and what they can use from where they stand
    K.ringDots(S, X(684), Y(530), 9, { level: 'norm', step: 3 });
    tag(S, X(684) + 14, Y(530) - 4, ['YOU']);
    const px = X(684) - 70, py = Y(530) - 38;
    scrim(S, px - 4, py - 4, 136, 21);
    K.keycap(S, px, py, 'E', { h: 12 });
    S.dm(px + 16, py + 1.5, 'LIFE SUPPORT', { pitch: 1.5, layer: 'proj', level: 'norm', ghost: false });
    S.projectNow();

    cluster(S, { chr: { day: '3', time: '14:22:05', warp: '1', held: 'FIRE ABOARD' }, ann: { FIRE: 'caution', AIR: 'caution', POWER: 'caution', CREW: 'caution' } });
    per(S, { health: 'LIGHT WOUND', suit: 'PRESSURE SUIT', here: 'CREW DECK: THIN AIR', air: '7:40', alert: false });
    alrCautions(S, [{ what: 'FIRE  GALLEY', who: 'BO ON IT' }, { what: 'POWER REACTOR SHORT', who: 'ADA RESETTING' }]);
    const oy = obj(S, [{ l1: 'DELIVERY: ICE', left: '1d04h', l2: 'TO FREEHOLD' }]);
    ntf(S, oy + 16, ['PAYDAY: 2,000 CR', 'CONTRACT: ICE TO FREEHOLD']);
    hlm(S, 740, { who: 'CY', order: 'HOLDS THE HEADING  (NO ORDER)', dimOrder: true, w: 440 });
    hnt(S, [['W A S D', 'WALK'], ['E', 'USE'], ['V', 'SIT'], ['O', 'LIGHTS'], ['F1', 'CONTROLS', true]]);
    S.finish();
  };

  Object.assign(K, { tk: { cluster, chr, ann, lamp, per, alrWarning, alrCautions, obj, ntf, tgt, wpn, sns, sch, flt, hlm, hnt } });
})();
