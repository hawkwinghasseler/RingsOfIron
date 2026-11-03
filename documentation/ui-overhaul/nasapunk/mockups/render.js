// Renders each mockup HTML in this folder to a PNG beside it at 2x.
// Usage: node documentation/ui-overhaul/nasapunk/mockups/render.js [name ...]
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const dir = __dirname;
  const names = process.argv.slice(2).length ? process.argv.slice(2)
    : fs.readdirSync(dir).filter(f => f.endsWith('.html')).map(f => f.replace(/\.html$/, ''));
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 2 });
  for (const n of names) {
    await page.goto('file://' + path.join(dir, n + '.html'));
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(200);
    await page.screenshot({ path: path.join(dir, n + '.png'), clip: { x: 0, y: 0, width: 1280, height: 800 } });
    console.log('wrote', n + '.png');
  }
  await browser.close();
})();
