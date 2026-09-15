async (page) => {
  const errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto('http://localhost:8080/login');
  if(await page.locator('#username').count()) {
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('admin123');
    await page.getByRole('button',{name:'Войти',exact:true}).click();
    await page.waitForURL('**/dashboard');
  }
  await page.setViewportSize({width:1440,height:960});
  const paths=['/dashboard','/kanban','/projects','/projects/1','/tasks','/tasks/1','/employees','/employees/1','/workload','/projects/new','/employees/new',
    ...['employees','departments','positions','users','schedules','absences','settings'].map(section=>'/admin?section='+section)];
  const results=[];
  for(const path of paths) {
    const response=await page.goto('http://localhost:8080'+path);
    if(response.status()!==200) { results.push({path,status:response.status()});continue; }
    await page.locator('h1').first().waitFor();
    await page.evaluate(()=>document.fonts.ready);
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
    const name=path.slice(1).replace(/[/?=]/g,'-');
    if(!path.includes('section=') || path.endsWith('employees')) await page.screenshot({path:'output/playwright/tracker-final/'+name+'.png',fullPage:true});
    results.push({path,status:response.status(),heading:await page.locator('h1').first().textContent(),overflow});
  }
  await page.goto('http://localhost:8080/kanban');
  return {results,errors};
}
