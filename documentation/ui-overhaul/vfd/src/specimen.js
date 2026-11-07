// The FILAMENT specimen sheet: every part of the kit on one page.
SCREENS.specimen = async (canvas) => {
  const W = 1280, H = 1200;
  const S = KIT.scene(canvas, W, H);
  S.fill(0, 0, W, H, '#0b0c0d');
  S.rack(8, 8, W - 16, H - 16, { radius: 10 });

  // ---------------------------------------------------------------- title
  S.module(28, 28, 560, 64, { filaments: 2 });
  S.seg14(44, 42, 30, 'FILAMENT', { level: 'full' });
  S.legend(372, 54, 'VFD DESIGN LANGUAGE', { level: 'norm' });
  S.legend(372, 76, 'RINGS OF IRON  ·  UI OVERHAUL', { level: 'dim' });
  S.silk(604, 46, 'Specimen sheet', { size: 12, upper: false, color: '#b9bfc4' });
  S.silk(604, 62, 'Every part of the kit, drawn by vfd.js at 2 px per UI unit.', { size: 7.5, upper: false, small: true });
  S.silk(604, 75, 'Ghosted segments are the unlit glass; nothing here comes from a font', { size: 7.5, upper: false, small: true });
  S.silk(604, 88, 'except the fixed legends.', { size: 7.5, upper: false, small: true });

  // ---------------------------------------------------------------- drive levels
  KIT.titled(S, 28, 120, 380, 196, 'Phosphors and drive levels');
  const levels = [['L0', 'ghost'], ['L1', 'dim'], ['L2', 'norm'], ['L3', 'full']];
  ['zinc', 'amber', 'red'].forEach((c, r) => {
    const y = 136 + r * 58;
    S.legend(40, y + 22, c.toUpperCase(), { color: c, level: 'norm' });
    levels.forEach(([lv, name], i) => {
      const x = 104 + i * 74;
      if (name === 'ghost') S.seg7(x, y, 30, '8', { color: c, ghost: true, level: 'norm', dp: false, lit: false, dead: () => true });
      else S.seg7(x, y, 30, '8', { color: c, level: name, dp: false });
      S.bar(x + 24, y + 2, 22, 26, 4, name === 'ghost' ? 0 : 1, { vertical: true, color: c, level: name === 'ghost' ? 'norm' : name, gap: 1.6 });
      if (r === 0) S.silk(x + 8, 310, lv + ' ' + name, { size: 6.5, small: true });
    });
  });

  // ---------------------------------------------------------------- non-emissive inks
  KIT.titled(S, 428, 120, 160, 196, 'Inks that do not glow');
  const inks = [['#08100e', 'glass'], ['#141517', 'bezel'], ['#8d949a', 'silk'], ['#26282c', 'key'], ['#a7b294', 'lcd'], ['#e9e6dc', 'card']];
  inks.forEach(([hex, name], i) => {
    const x = 440 + (i % 2) * 72, y = 134 + Math.floor(i / 2) * 58;
    S.base.fillStyle = hex; S.base.beginPath(); S.base.roundRect(x, y, 62, 32, 3); S.base.fill();
    S.base.strokeStyle = '#2b2d30'; S.base.lineWidth = 0.8; S.base.stroke();
    S.silk(x, y + 44, name + ' ' + hex, { size: 6, upper: false, small: true });
  });

  // ---------------------------------------------------------------- seven segments
  KIT.titled(S, 608, 120, 644, 196, 'Seven segments: every number that changes');
  S.seg7(624, 136, 40, '0123456789', { level: 'norm' });
  S.seg7(624, 196, 26, '-1.24', { level: 'norm', cells: 5 });
  S.legend(744, 222, 'KM', { level: 'norm' });
  S.legend(768, 222, 'M', { lit: false });
  S.silk(624, 236, 'autoranging field', { size: 6.5, upper: false, small: true });
  S.seg7(808, 196, 26, '----', { level: 'dim' });
  S.silk(808, 236, 'no data (L1)', { size: 6.5, upper: false, small: true });
  S.seg7(906, 196, 26, ' OL', { level: 'norm', cells: 3 });
  S.silk(906, 236, 'over range', { size: 6.5, upper: false, small: true });
  S.seg7(986, 196, 26, '012', { level: 'norm' });
  S.seg7(1046, 196, 26, '14:37:22', { level: 'norm' });
  S.silk(986, 236, 'clock: day, hh:mm:ss, colon at 1 Hz', { size: 6.5, upper: false, small: true });
  S.seg7(624, 252, 18, '3.80', { level: 'full' }); S.legend(676, 270, 'L3 FULL', { level: 'dim' });
  S.seg7(744, 252, 18, '3.80', { level: 'norm' }); S.legend(796, 270, 'L2', { level: 'dim' });
  S.seg7(834, 252, 18, '3.80', { level: 'dim' }); S.legend(886, 270, 'L1', { level: 'dim' });
  S.seg7(920, 252, 12, '88888', { level: 'norm' }); S.seg7(990, 256, 12, '12', { level: 'norm', cells: 2 });
  S.silk(920, 284, 'sizes S 12, M 18, L 26, XL 40', { size: 6.5, upper: false, small: true });
  S.seg7(1100, 252, 18, '8.8.8', { level: 'norm', dead: (i, k) => (i === 1 && (k === 'b' || k === 'g')) || (i === 2 && k === 'e') });
  S.silk(1100, 284, 'damaged: dead segments', { size: 6.5, upper: false, small: true });

  // ---------------------------------------------------------------- fourteen segments
  KIT.titled(S, 28, 340, 600, 116, 'Fourteen segments: short words that change');
  S.seg14(42, 354, 20, 'ABCDEFGHIJKLM', { level: 'norm' });
  S.seg14(42, 384, 20, 'NOPQRSTUVWXYZ', { level: 'norm' });
  S.seg14(42, 414, 20, '0123456789-+/', { level: 'dim' });
  S.seg14(430, 414, 20, '<>*=', { level: 'dim' });

  // ---------------------------------------------------------------- dot matrix
  KIT.titled(S, 648, 340, 604, 116, 'Dot matrix: the 5x7 character ROM, upper case only');
  const rom = Object.keys(VFD.ROM).filter(k => k !== ' ');
  const perLine = 46;
  for (let i = 0; i < rom.length; i += perLine) S.dm(662, 354 + (i / perLine) * 17, rom.slice(i, i + perLine).join(''), { pitch: 1.5, level: 'norm' });
  S.dm(662, 410, '▶ HIGHPORT  ICE  420  12  10', { pitch: 1.5, level: 'full', inverse: [2, 28] });
  S.dm(662, 428, 'PITCH 1.5', { pitch: 1.5, level: 'norm' });
  S.dm(770, 426, 'PITCH 2', { pitch: 2, level: 'norm' });
  S.dm(880, 422, 'PITCH 3', { pitch: 3, level: 'norm' });

  // ---------------------------------------------------------------- legends and icons
  KIT.titled(S, 28, 480, 600, 150, 'Legends and icons: lit or ghosted, never redrawn');
  const legends = ['ASSIST', 'AUTO', 'ORB', 'TGT', 'PORT', 'DOCKED', 'PIN', 'EST', 'HOLD', 'M/S', 'KM', 'CR'];
  legends.forEach((l, i) => S.legend(42 + (i % 6) * 96, 502 + Math.floor(i / 6) * 20, l, { lit: i % 3 !== 2, level: i % 3 === 0 ? 'norm' : 'dim' }));
  const icons = Object.keys(VFD.ICONS);
  icons.forEach((n, i) => S.icon(n, 42 + (i % 14) * 41, 548 + Math.floor(i / 14) * 38, 18, { lit: i % 4 !== 3, level: i % 5 === 0 ? 'full' : 'norm' }));
  icons.forEach((n, i) => S.silk(42 + (i % 14) * 41 + 9, 548 + Math.floor(i / 14) * 38 + 28, n, { size: 5.2, align: 'center', upper: false, small: true }));

  // ---------------------------------------------------------------- bars and rings
  KIT.titled(S, 648, 480, 604, 150, 'Bars, rails and rings: discrete steps, colour by place');
  KIT.barRow(S, 662, 494, 'PROP', 0.42, { w: 180, n: 30, value: '42%', labelSize: 13, labelW: 50 });
  KIT.barRow(S, 662, 513, 'HEAT', 0.7, { w: 180, n: 30, zone: KIT.RISING, value: '70%', peak: 0.77, labelSize: 13, labelW: 50 });
  S.legend(662, 539, 'ΔV', { level: 'dim' });
  S.bar(712, 532, 180, 6, 30, 0.62, { color: 'zinc', level: 'norm' });
  S.bar(712, 540, 180, 2.5, 30, 0, { color: 'zinc' });
  for (let i = 9; i < 15; i++) S.rectLit(712 + i * (180 - 29 * 1.2) / 30 + i * 1.2, 540, (180 - 29 * 1.2) / 30, 2.5, { level: 'norm' });
  S.rectLit(712 + 11 * 6.03, 529, 4.8, 1.6, { level: 'full' });
  S.silk(662, 556, 'two rails: Δv left, home notch, plan bracket', { size: 6.5, upper: false, small: true });
  S.ring(984, 556, 40, 50, 32, (i) => (i === 3 ? { level: 'full' } : i === 2 || i === 4 ? { level: 'dim' } : null), {});
  S.seg7(966, 548, 14, '045', { level: 'norm' });
  S.silk(984, 622, 'bearing ring, 32 sectors', { size: 6.5, align: 'center', upper: false, small: true });
  KIT.sectorScope(S, 1130, 556, 58, { contacts: [{ brg: 60, ring: 2, hostile: true, lock: true }, { brg: 75, ring: 1, missile: true }, { brg: 200, ring: 3 }, { brg: 300, ring: 0 }] });
  S.silk(1196, 622, 'sector scope', { size: 6.5, align: 'right', upper: false, small: true });

  // ---------------------------------------------------------------- annunciator and keys
  KIT.titled(S, 28, 656, 600, 168, 'Annunciator tiles, master lamps and soft keys');
  KIT.tile(S, 42, 672, 104, 24, 'MSL 2', '0:09', 'warning', { tagSize: 13 });
  KIT.tile(S, 150, 672, 104, 24, 'FIRE', 'HOLD', 'warning', { tagSize: 13 });
  KIT.tile(S, 258, 672, 104, 24, 'PROP', 'LOW', 'caution', { tagSize: 13 });
  KIT.tile(S, 366, 672, 104, 24, 'STRAIN', '', 'off', { tagSize: 13 });
  S.silk(42, 708, 'warning: red, inverse, 2 Hz until acknowledged · caution: amber, outlined, 1 Hz · off: ghost', { size: 6.5, upper: false, small: true });
  S.lampKey(480, 668, 68, 30, 'MASTER', 'WARN', 'red', true);
  S.lampKey(554, 668, 68, 30, 'MASTER', 'CAUT', 'amber', false);
  S.module(42, 718, 572, 46, { filaments: 1 });
  S.dm(54, 726, 'SOFT KEYS NAME THE PAGES', { pitch: 1.5, level: 'dim' });
  KIT.softKeys(S, 42, 718, 572, 46, [{ t: 'NAV', k: '1', active: true }, { t: 'PLAN', k: '2' }, { t: 'SENSORS', k: '3' }, { t: 'DAMAGE', k: '4' }, { t: 'PIN', k: '0', led: 'green', ledOn: true }, { t: 'ACK', k: 'O' }]);
  S.silk(42, 818, 'active page inverse · toggles carry an LED · keys print their keyboard shortcut', { size: 6.5, upper: false, small: true });

  // ---------------------------------------------------------------- LCD and card
  KIT.titled(S, 648, 656, 290, 168, 'Personal kit: reflective LCD');
  S.lcdPanel(662, 672, 262, 64);
  S.lcdText(670, 688, 'SUIT AIR', { size: 12 });
  S.lcdSeg7(670, 696, 30, '4:10', { slant: 6 });
  S.lcdText(780, 688, 'ROOM', { size: 12 });
  S.lcdSeg7(780, 696, 18, '31', { slant: 6 });
  S.lcdText(818, 714, 'KPA', { size: 12 });
  S.lcdText(856, 714, 'O2 LOW', { size: 12 });
  S.lcdPanel(662, 746, 262, 40, { backlight: true });
  S.lcdText(670, 770, 'BACKLIGHT ON ALERT', { size: 12 });
  S.lcdSeg7(840, 758, 18, '12:04', { slant: 6 });
  S.silk(662, 806, 'the ship glows; the captain\'s kit reflects', { size: 6.5, upper: false, small: true });

  KIT.titled(S, 958, 656, 294, 168, 'Out of fiction: printed card');
  const b = S.base; b.save(); b.fillStyle = '#e9e6dc'; b.beginPath(); b.roundRect(972, 670, 266, 112, 4); b.fill(); b.restore();
  S.silk(984, 692, 'QUICK REFERENCE  ·  FIRST FLIGHT', { size: 12, color: '#1c1c1c' });
  S.silk(984, 712, 'Undock, then burn prograde.', { size: 12, color: '#1c1c1c', upper: false, condense: 0.95 });
  let kx = 984;
  kx += KIT.keycap(S, kx, 722, 'U') + 4; kx += S.silk(kx, 736, 'undock', { size: 12, color: '#1c1c1c', upper: false }) + 12;
  kx += KIT.keycap(S, kx, 722, 'W') + 4; kx += S.silk(kx, 736, 'thrust', { size: 12, color: '#1c1c1c', upper: false }) + 12;
  kx += KIT.keycap(S, kx, 722, 'M') + 4; S.silk(kx, 736, 'map', { size: 12, color: '#1c1c1c', upper: false });
  S.silk(984, 768, 'Cards and F1 are paper, not ship.', { size: 12, color: '#55524a', upper: false, condense: 0.95 });

  // ---------------------------------------------------------------- projected HUD
  KIT.titled(S, 28, 850, 1224, 320, 'Projected HUD: dot sprites over the outside view (zinc, red for threats, amber for collision only)');
  const view = { x: 42, y: 864, w: 1196, h: 292 };
  const bb = S.base; bb.save(); bb.fillStyle = '#05070c'; bb.fillRect(view.x, view.y, view.w, view.h);
  for (let i = 0; i < 220; i++) { bb.fillStyle = `rgba(255,255,255,${0.15 + 0.5 * Math.abs(Math.sin(i * 7.31))})`; bb.fillRect(view.x + (i * 97.3) % view.w, view.y + (i * 53.7) % view.h, 0.8, 0.8); }
  bb.restore();
  const rowY = 950, items = [
    ['prograde', 'PROGRADE'], ['retrograde', 'RETROGRADE'], ['lead', 'LEAD'], ['neutral', 'NEUTRAL'], ['station', 'STATION'],
  ];
  const at = i => 100 + i * 118, under = (cx, y, lines, o) => KIT.label(S, cx - (Math.max(...lines.map(l => l.length)) * 12 - 2) / 2, y, lines, o);
  items.forEach(([n, l], i) => { KIT.sprite(S, n, at(i), rowY); under(at(i), rowY + 22, [l]); });
  KIT.brackets(S, at(5), rowY, 14, 'square'); under(at(5), rowY + 26, ['FRIENDLY']);
  KIT.brackets(S, at(6), rowY, 14, 'tri', { color: 'red' }); under(at(6), rowY + 26, ['RAIDER 6', '2.1 KM'], { color: 'red' });
  KIT.brackets(S, at(7), rowY, 14, 'square', { level: 'full', ticks: true }); under(at(7), rowY + 30, ['LOCK']);
  KIT.brackets(S, at(8), rowY, 12, 'diamond'); under(at(8), rowY + 26, ['NAV']);
  KIT.sprite(S, 'neutral', at(9), rowY, { level: 'full' }); S.dotLine(at(9), rowY + 6, at(9), rowY + 30, { layer: 'proj', step: 4, level: 'dim' }); under(at(9), rowY + 40, ['WAYPOINT']);
  // a missile track to an impact cross, an edge marker, and a collision cue
  S.dotLine(140, 1080, 560, 1060, { layer: 'proj', color: 'red', step: 6, level: 'norm' });
  KIT.sprite(S, 'missile', 560, 1060, { color: 'red', level: 'full' });
  KIT.sprite(S, 'impact', 140, 1080, { color: 'red', level: 'full' });
  KIT.label(S, 520, 1072, ['MSL 12S'], { color: 'red' });
  KIT.label(S, 100, 1100, ['THREAT TRACK, IMPACT CROSS'], { color: 'red' });
  KIT.sprite(S, 'chevronR', 1226, 1080, { color: 'red', level: 'full' });
  KIT.label(S, 1214 - 70, 1068, ['MSL', '8S'], { color: 'red' });
  KIT.sprite(S, 'impact', 820, 1084, { color: 'amber', level: 'full' });
  S.dotLine(700, 1110, 814, 1088, { layer: 'proj', color: 'amber', step: 5, level: 'norm' });
  KIT.label(S, 780, 1100, ['CPA 140 M'], { color: 'amber' });
  // a burn arrow
  S.dotLine(940, 1120, 1060, 1080, { layer: 'proj', step: 4 });
  KIT.sprite(S, 'arrowR', 1064, 1078, { level: 'norm' });
  KIT.label(S, 1000, 1112, ['BRN 1  12:04']);

  S.finish();
};
