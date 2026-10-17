async (page) => {
  const results = [];
  const errors = [];
  page.on('pageerror', error => errors.push(String(error)));
  for (const [width, height] of [[1440,900], [1280,720], [390,844]]) {
    await page.setViewportSize({width, height});
    for (const path of ['/dashboard','/tasks','/kanban','/employees','/projects','/workload','/attrition','/reports','/admin']) {
      const response = await page.goto('http://localhost:18080' + path);
      await page.evaluate(() => document.fonts.ready);
      const details = await page.evaluate(() => ({
        overflow: document.documentElement.scrollWidth > innerWidth + 1,
        brokenImages: Array.from(document.images).filter(i => !i.complete || i.naturalWidth === 0).length,
        title: document.title
      }));
      results.push({width, height, path, status: response.status(), ...details});
      if (width === 1440 && path === '/dashboard')
        await page.screenshot({path:'output/playwright/ml-acceptance/dashboard-desktop.png',fullPage:true});
      if (width === 390 && path === '/attrition')
        await page.screenshot({path:'output/playwright/ml-acceptance/attrition-mobile.png',fullPage:true});
    }
  }
  return {results, errors};
}
