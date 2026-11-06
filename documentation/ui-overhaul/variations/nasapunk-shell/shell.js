// The Nasa-punk shell: every menu is a page of one flight computer, CDU-1. Needs lib.js, kit.js and
// screens.js. Out of a game the unit sits in a console with the game's name engraved on it; in a
// game (pause, ship lost) it stands alone in front of the deck.

TEXT_FLOOR = 12;

// The main menu's backdrop, the game's own: stars and the Earth's limb.
function backdrop() { return space({ px: 640, py: 1853, pr: 1258, seed: 11 }); }

// The console that carries the game's name, with CDU-1 set in it. o: the cdu() options for the page.
// Returns the svg.
function shellConsole(o) {
  const X0 = 392, Y0 = 99, W0 = 496, H0 = 602;
  let s = bezelBody(X0, Y0, W0, H0, 4) + fastener(X0 + 8, Y0 + 8) + fastener(X0 + W0 - 8, Y0 + 8) + fastener(X0 + 8, Y0 + H0 - 8) + fastener(X0 + W0 - 8, Y0 + H0 - 8);
  s += text(640, Y0 + 46, 'RINGS OF IRON', { size: 30, bold: true, fill: C.stencil, anchor: 'middle', track: 7, legend: true });
  s += rect(X0 + 150, Y0 + 58, 196, 1, { fill: '#55595F' });
  s += text(640, Y0 + 74, 'A NEWTONIAN SHIP SIM', { size: 10, bold: true, fill: '#9EA29C', anchor: 'middle', track: 3.2, legend: true });
  return s + shellCdu(X0 + 14, Y0 + 92, o).svg;
}

// CDU-1 itself, 468 x 494: six line-select keys a side, each level with its row, and under the glass
// the keyboard: PREV PAGE and NEXT PAGE (Page Up and Page Down), and EXEC (F10), its light bar lit
// while a modification waits. o.exec: 'lit' while a change is pending.
function shellCdu(x, y, o) {
  const hard = [{ t: 'PREV\nPAGE', k: 'PG UP' }, { t: 'NEXT\nPAGE', k: 'PG DN' }, { t: 'EXEC', k: 'F10', bar: o.exec === 'lit' ? 'lit' : 'dark', right: true }];
  return cdu(x, y, 468, 494, Object.assign({ id: 'cdu1', title: 'FLIGHT OPERATIONS', plate: 'CDU-1   v1.0.0 · cb68b558', firstRow: 60, pitch: 57, hard }, o));
}

// The flight deck behind a menu shown in a game: the world, the glareshield and the two DUs of the
// style's default layout (section 9), each with a page title and a few values. o.failed draws every
// display's red X (a lost ship); o.dim the share of light it keeps.
function deckBehind(o = {}) {
  let s = space({ px: 160, py: 1620, pr: 1000, seed: 3 });
  s += stationHull(860, 300, 0.9) + hull(612, 452, 38, 0.9);
  if (!o.failed) {
    s += haloed((c, w) => sym.station(860, 300, 8, c, w), C.white) + text(834, 350, 'HIGHPORT', { size: 12, bold: true, fill: C.white, anchor: 'end', track: 0.6 });
    s += W.pro(690, 380);
  }
  // glareshield
  s += bezelBody(0, 0, 1280, 44, 0);
  s += lens(14, 6, 64, 32, 'MASTER\nWARN', o.failed ? C.red : null) + lens(86, 6, 64, 32, 'MASTER\nCAUT', null);
  s += rect(166, 7, 694, 30, { fill: C.glass }) + rect(872, 7, 250, 30, { fill: C.glass });
  if (o.failed) { s += path('M166 7 L860 37 M166 37 L860 7', C.red, 2) + path('M872 7 L1122 37 M872 37 L1122 7', C.red, 2); }
  else {
    [['ATT', 'HOLD', C.green], ['TRANS', 'MANUAL', C.white], ['FRAME', 'STN', C.white], ['AP', 'OFF', C.dim], ['WARP', '1×', C.white]].forEach((f, i) => {
      const cx = 166 + 694 / 5 * (i + 0.5);
      if (i) s += line(166 + 694 / 5 * i, 11, 166 + 694 / 5 * i, 33, C.graticule, 1);
      s += text(cx - 10, 27, f[0], { size: 12, bold: true, fill: C.dim, anchor: 'end', track: 0.6 }) + text(cx, 27, f[1], { size: 13, bold: true, fill: f[2], track: 0.6 });
    });
    s += text(882, 27, 'DAY', { size: 12, bold: true, fill: C.dim, track: 0.6 }) + text(914, 27, '3 · 14:22:05', { size: 14, bold: true, fill: C.white });
  }
  s += lens(1130, 6, 64, 32, 'PROP\nLOW', null) + lens(1202, 6, 64, 32, 'DOCK\nCLEARED', null);
  const page = (gx, gy, gw, gh, title, rows) => {
    if (o.failed) return path(`M${gx} ${gy} L${gx + gw} ${gy + gh} M${gx} ${gy + gh} L${gx + gw} ${gy}`, C.red, 2.5);
    let c = text(gx + gw / 2, gy + 18, title, { size: 12, bold: true, fill: C.white, anchor: 'middle', track: 0.8 });
    rows.forEach(([l, v, u], i) => { c += text(gx + 12, gy + 52 + i * 22, l, { size: 12, bold: true, fill: C.dim, track: 0.6 }) + value(gx + gw - 12, gy + 52 + i * 22, v, u, { size: 16 }); });
    return c;
  };
  s += du(8, 56, 312, 452, { id: 'bk1', plate: 'DU-1  PFD  P/N 4410-06', keys: { bottom: [{ t: 'FLIGHT', state: 'cur' }, 'DCLTR', 'TAPE', 'BRT', 'XFR'] }, content: (gx, gy, gw, gh) => page(gx, gy, gw, gh, 'FLIGHT', [['V ORBIT', '7 672.6', 'M/S'], ['CLOSING', '0.4', 'M/S'], ['RNG', '3.0', 'KM'], ['ΔV', '7 007', 'M/S']]) }).svg;
  s += du(960, 56, 312, 452, { id: 'bk2', plate: 'DU-2  MFD R  P/N 4410-07', keys: { bottom: [{ t: 'NAV', state: 'cur' }, 'TARGET', 'SYSTEMS', 'CREW', 'COMMS'] }, content: (gx, gy, gw, gh) => page(gx, gy, gw, gh, 'NAV', [['AP', '400', 'KM'], ['PE', '398', 'KM'], ['PERIOD', '1:32:24', ''], ['NAV', 'HIGHPORT', '']]) }).svg;
  return o.dim ? dimmed(s, o.dim) : s;
}
