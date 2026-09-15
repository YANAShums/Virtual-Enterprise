async (page) => {
  const report=[];
  for (const width of [1440,390]) {
    await page.setViewportSize({width,height:width===1440?1000:844});
    for (const route of ['dashboard','employees','projects','kanban','workload','admin']) {
      const response=await page.goto('http://localhost:8080/'+route);
      await page.locator('h1').waitFor();
      await page.screenshot({path:`output/playwright/${route}-${width}.png`,fullPage:true});
      report.push({route,width,status:response.status(),overflow:await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),unlabelled:await page.locator('input:not([type=hidden]),select,textarea').evaluateAll(elements=>elements.filter(e=>!e.labels?.length&&!e.getAttribute('aria-label')).map(e=>e.id))});
    }
  }
  await page.setViewportSize({width:1440,height:1000});
  return report;
}
