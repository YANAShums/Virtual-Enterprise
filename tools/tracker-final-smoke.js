async (page) => {
  const results=[];
  for(const width of [768,320]) {
    await page.setViewportSize({width,height:900});
    for(const path of ['/dashboard','/kanban?projectId=1','/employees','/projects/1','/tasks/2','/workload']) {
      const response=await page.goto('http://localhost:8080'+path);
      results.push({path,width,status:response.status(),overflow:await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)});
    }
  }
  await page.setViewportSize({width:1480,height:923});
  await page.goto('http://localhost:8080/kanban?projectId=1');
  await page.screenshot({path:'output/playwright/tracker-final/board-comparison.png'});
  await page.locator('#sidebarToggle').click();
  results.push({check:'desktop menu expands',pass:await page.locator('body').evaluate(el=>el.classList.contains('sidebar-expanded'))});
  await page.reload();
  results.push({check:'menu preference persists',pass:await page.locator('body').evaluate(el=>el.classList.contains('sidebar-expanded'))});
  await page.locator('#sidebarToggle').click();
  await page.locator('#projectFilter').selectOption('2');
  await page.waitForURL('**/kanban?projectId=2');
  results.push({check:'project selector navigates',pass:(await page.locator('h1').textContent()).includes('CRM')});
  await page.goto('http://localhost:8080/kanban?projectId=1');
  return results;
}
