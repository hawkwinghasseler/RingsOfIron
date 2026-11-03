// Renders Nasa-punk mockup pages (HTML, 1280 x 800 UI units) to PNGs beside them at 2x.
// Usage: node documentation/ui-overhaul/nasapunk/mockups/render.js [file.html | dir ...]
// With no arguments it renders every page in this folder.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const args = process.argv.slice(2).length ? process.argv.slice(2) : [__dirname];
  const pages = [];
  for (const a of args) {
    const p = path.resolve(a);
    if (fs.statSync(p).isDirectory()) fs.readdirSync(p).filter(f => f.endsWith('.html')).forEach(f => pages.push(path.join(p, f)));
    else pages.push(p);
  }
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 2 });
  page.on('pageerror', e => console.error('page error:', e.message));
  for (const file of pages) {
    await page.goto('file://' + file);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(150);
    const out = file.replace(/\.html$/, '.png');
    await page.screenshot({ path: out, clip: { x: 0, y: 0, width: 1280, height: 800 } });
    console.log('wrote', path.relative(process.cwd(), out));
  }
  await browser.close();
})();
