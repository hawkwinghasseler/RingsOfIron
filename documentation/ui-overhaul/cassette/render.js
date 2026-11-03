// Renders the Yard Standard mockups to PNG: node render.js (from this folder or anywhere).
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 2300 }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.join(__dirname, 'mockups.html'));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(500);
  for (const id of ['flight', 'components']) {
    const el = await page.$('#' + id);
    await el.screenshot({ path: path.join(__dirname, `mockup-${id}.png`) });
  }
  await browser.close();
})();
