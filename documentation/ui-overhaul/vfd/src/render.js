// Renders FILAMENT mockups to images: node render.js <page.html> <screen> <out.png> [W] [H]
// The page draws on a canvas at 2 pixels per UI unit and sets window.__done; the canvas is captured at that size.
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const [page, screen, out, W, H] = process.argv.slice(2);
  const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
  const tab = await browser.newPage({ viewport: { width: +(W || 1280), height: +(H || 800) }, deviceScaleFactor: 2 });
  tab.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') console.log('page:', m.text()); });
  tab.on('pageerror', e => { console.log('page error:', e.message); process.exit(1); });
  await tab.goto('file://' + path.resolve(page) + '?s=' + encodeURIComponent(screen));
  await tab.waitForFunction(() => window.__done === true, null, { timeout: 60000 });
  await tab.locator('canvas').screenshot({ path: out });
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
