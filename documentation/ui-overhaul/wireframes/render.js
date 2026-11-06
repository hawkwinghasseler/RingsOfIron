// Renders every layout in wireframes.html to a PNG beside it, and checks that no text runs past
// its box: the wireframes draw text at the spec's 13-unit floor, so a line that overflows here
// breaks a text budget in functional-context.md section 5.6.
// Usage: node documentation/ui-overhaul/wireframes/render.js [scale]
const { chromium } = require('playwright');
const path = require('path');
const names = {
  '01': 'helm-cruise', '02': 'helm-warp', '03': 'helm-approach', '04': 'helm-combat',
  '05': 'fire-control', '06': 'deck', '07': 'deck-emergency', '08': 'docked',
  '09': 'map', '10': 'station', '11': 'ship-systems', '12': 'orders-menu', '13': 'boarding',
};
(async () => {
  const scale = Number(process.argv[2] || 2);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 856 }, deviceScaleFactor: scale });
  const file = 'file://' + path.join(__dirname, 'wireframes.html');
  let broken = 0;
  for (const [n, name] of Object.entries(names)) {
    await page.goto(`${file}?n=${n}`);
    for (const o of await page.evaluate(() => window.overflows())) {
      console.log(`${n} ${name}: overflows ${o}`);
      broken++;
    }
    await page.screenshot({ path: path.join(__dirname, `${n}-${name}.png`) });
  }
  await browser.close();
  if (broken) {
    console.log(`${broken} line(s) run past their box`);
    process.exitCode = 1;
  }
})();
