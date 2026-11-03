// Checks Nasa-punk mockup pages for text problems: text over text, text cut by its display's glass,
// text off the 1280 x 800 page, and glass text under a size floor (hardware legends excepted).
// Usage: node nasapunk/mockups/check.js [--floor 12] [file.html | dir ...]
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  let args = process.argv.slice(2), floor = 12;
  const fi = args.indexOf('--floor');
  if (fi >= 0) { floor = +args[fi + 1]; args.splice(fi, 2); }
  const pages = [];
  for (const a of args.length ? args : [__dirname]) {
    const p = path.resolve(a);
    if (fs.statSync(p).isDirectory()) fs.readdirSync(p).filter(f => f.endsWith('.html')).sort().forEach(f => pages.push(path.join(p, f)));
    else pages.push(p);
  }
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  let total = 0;
  for (const file of pages) {
    await page.goto('file://' + file);
    await page.evaluate(() => document.fonts.ready);
    const found = await page.evaluate((floor) => {
      const out = [];
      const texts = [...document.querySelectorAll('svg text')].filter(t => t.textContent.trim());
      const box = t => { const r = t.getBoundingClientRect(); return { x: r.left, y: r.top, w: r.width, h: r.height, t: t.textContent, el: t }; };
      const boxes = texts.map(box);
      const clipOf = el => {
        for (let n = el.parentElement; n && n.tagName !== 'svg'; n = n.parentElement) {
          const cp = n.getAttribute && n.getAttribute('clip-path');
          if (cp) { const id = cp.match(/#([^)]+)/)[1]; const r = document.getElementById(id).querySelector('rect'); return { x: +r.getAttribute('x'), y: +r.getAttribute('y'), w: +r.getAttribute('width'), h: +r.getAttribute('height') }; }
        }
        return null;
      };
      // tight vertical extent: the font box is taller than the ink, so shrink it to the cap height
      const ink = b => { const fs = parseFloat(b.el.getAttribute('font-size')); const base = parseFloat(b.el.getAttribute('y')); return { x: b.x, x2: b.x + b.w, y: base - fs * 0.72, y2: base + fs * 0.08 }; };
      for (let i = 0; i < boxes.length; i++) {
        const a = boxes[i], A = ink(a);
        const fs = parseFloat(a.el.getAttribute('font-size'));
        if (fs < floor && !a.el.hasAttribute('data-legend')) out.push(`small ${fs}: "${a.t}" at ${Math.round(a.x)},${Math.round(A.y2)}`);
        if (!a.el.hasAttribute('data-legend') && (A.x < 0 || A.x2 > 1280 || A.y < 0 || A.y2 > 800)) out.push(`off page: "${a.t}" at ${Math.round(a.x)},${Math.round(A.y2)}`);
        const c = clipOf(a.el);
        if (c && (A.x < c.x - 0.5 || A.x2 > c.x + c.w + 0.5 || A.y < c.y - 0.5 || A.y2 > c.y + c.h + 0.5)) out.push(`cut by glass: "${a.t}" at ${Math.round(a.x)},${Math.round(A.y2)} (glass ${c.x},${c.y} ${c.w}x${c.h})`);
        for (let j = i + 1; j < boxes.length; j++) {
          const b = boxes[j], B = ink(b);
          if (a.t === b.t && Math.abs(a.x - b.x) < 1 && Math.abs(a.y - b.y) < 1) continue; // halo pass
          const ox = Math.min(A.x2, B.x2) - Math.max(A.x, B.x), oy = Math.min(A.y2, B.y2) - Math.max(A.y, B.y);
          if (ox > 1 && oy > 1) out.push(`overlap: "${a.t}" / "${b.t}" at ${Math.round(Math.max(A.x, B.x))},${Math.round(Math.max(A.y, B.y))} (${ox.toFixed(1)} x ${oy.toFixed(1)})`);
          // two texts on one line closer than a quarter of their size read as one word
          else if (oy > 0.5 * Math.min(A.y2 - A.y, B.y2 - B.y) && ox > -0.25 * Math.min(fs, parseFloat(b.el.getAttribute('font-size'))) && ox <= 1) out.push(`crowded: "${a.t}" / "${b.t}" at ${Math.round(Math.min(A.x2, B.x2))},${Math.round(Math.max(A.y, B.y))} (gap ${(-ox).toFixed(1)})`);
        }
      }
      return out;
    }, floor);
    console.log(`${path.relative(process.cwd(), file)}: ${found.length ? found.length + ' problem(s)' : 'clean'}`);
    found.forEach(f => console.log('  ' + f));
    total += found.length;
  }
  await browser.close();
  process.exitCode = total ? 1 : 0;
})();
