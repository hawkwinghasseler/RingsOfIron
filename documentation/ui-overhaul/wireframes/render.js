// Renders every layout in wireframes.html to a PNG beside it.
// Usage: node documentation/ui-overhaul/wireframes/render.js [scale]
const { chromium } = require('playwright');
const path = require('path');
const names = {
  '01': 'helm-cruise', '02': 'helm-warp', '03': 'helm-approach', '04': 'helm-combat',
  '05': 'fire-control', '06': 'deck', '07': 'deck-emergency', '08': 'docked',
  '09': 'map', '10': 'station', '11': 'ship-systems',
};
(async () => {
  const scale = Number(process.argv[2] || 2);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 856 }, deviceScaleFactor: scale });
  const file = 'file://' + path.join(__dirname, 'wireframes.html');
  for (const [n, name] of Object.entries(names)) {
    await page.goto(`${file}?n=${n}`);
    await page.screenshot({ path: path.join(__dirname, `${n}-${name}.png`) });
  }
  await browser.close();
})();
