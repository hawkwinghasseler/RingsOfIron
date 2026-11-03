// Renders every <svg id="..."> in a mockup page to an image next to the page.
//   node render.js                      -> cassette/mockups.html, PNG at 1x
//   node render.js <page.html> [scale] [png|jpeg]
// Uses the pre-installed Playwright (NODE_PATH=$(npm root -g) if it is not found).
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const file = path.resolve(process.argv[2] || path.join(__dirname, 'mockups.html'));
  const scale = +(process.argv[3] || 1);
  const type = process.argv[4] || 'png';
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 1200 }, deviceScaleFactor: scale });
  await page.goto('file://' + file);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
  const ids = await page.$$eval('svg[id]', els => els.map(e => e.id));
  for (const id of ids) {
    const el = await page.$('#' + id);
    const out = path.join(path.dirname(file), `${id}.${type === 'jpeg' ? 'jpg' : 'png'}`);
    await el.screenshot(type === 'jpeg' ? { path: out, type, quality: 88 } : { path: out });
    console.log(out);
  }
  await browser.close();
})();
