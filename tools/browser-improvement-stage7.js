async page => {
  const errors=[];page.on('pageerror',error=>errors.push(String(error)));
  async function login(user,password) {
    await page.goto('http://localhost:18080/login');
    await page.getByRole('textbox',{name:'Логин'}).fill(user);
    await page.getByRole('textbox',{name:'Пароль'}).fill(password);
    await page.getByRole('button',{name:'Войти',exact:true}).click();
  }
  await login('admin','admin123');
  await page.emulateMedia({reducedMotion:'reduce'});
  const results=[];
  for(const [width,height] of [[1440,900],[1280,720],[390,844]]) {
    await page.setViewportSize({width,height});
    let response=await page.goto('http://localhost:18080/dashboard');
    if(response.status()!==200)throw new Error('Dashboard HTTP '+response.status());
    const grid=await page.locator('.dashboard-stats').evaluate(element=>({
      cards:element.children.length,columns:getComputedStyle(element).gridTemplateColumns.split(' ').length,
      overflow:document.documentElement.scrollWidth>innerWidth+1,
      rows:[...element.children].reduce((out,child)=>{const top=Math.round(child.getBoundingClientRect().top);out[top]=(out[top]||0)+1;return out;},{})
    }));
    if(grid.cards!==6 || grid.columns!==(width>900?3:1) || grid.overflow)throw new Error(JSON.stringify(grid));
    await page.locator('.dashboard-stat').first().focus();
    await page.screenshot({path:'output/playwright/ml-acceptance/dashboard-final-'+width+'.png',fullPage:true});
    response=await page.goto('http://localhost:18080/attrition');
    if(response.status()!==200)throw new Error('Attrition HTTP '+response.status());
    const panel=page.locator('[data-responsive-filters]'),summary=panel.locator('summary');
    if(await panel.evaluate(element=>element.open)!==(width>600))throw new Error('Wrong initial disclosure state');
    if(!await summary.getByText('Активно: 0',{exact:true}).count())throw new Error('Wrong empty filter count');
    await page.keyboard.press('Tab');await summary.focus();
    const focus=await summary.evaluate(element=>getComputedStyle(element).outlineStyle!=='none');
    if(!focus)throw new Error('No keyboard focus indication');
    await page.screenshot({path:'output/playwright/ml-acceptance/attrition-final-'+width+'.png',fullPage:true});
    if(width<=600)await summary.press('Enter');
    await page.getByRole('combobox',{name:'Риск',exact:true}).selectOption('HIGH');
    await page.getByRole('button',{name:'Применить',exact:true}).click();
    if(!page.url().includes('riskLevel=HIGH'))throw new Error('Filter query lost');
    if(!await summary.getByText('Активно: 2',{exact:true}).count())throw new Error('Wrong applied filter count');
    if(await page.getByRole('combobox',{name:'Риск',exact:true}).inputValue()!=='HIGH')throw new Error('Selected risk lost');
    const bands=await page.locator('.attrition-table .risk-badge').allTextContents();
    if(bands.some(band=>band.trim()!=='HIGH'))throw new Error('Risk filter not applied');
    if(width<=600)await summary.press('Enter');
    await page.getByRole('link',{name:'Сбросить',exact:true}).click();
    if(new URL(page.url()).search)throw new Error('Reset retained filters');
    if(!await summary.getByText('Активно: 0',{exact:true}).count())throw new Error('Reset count wrong');
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
    if(overflow)throw new Error('Attrition overflow');
    results.push({width,height,grid,focus,filter_apply_and_reset:true,mobile_collapsed:width<=600,overflow});
  }
  // A manager sees four authorized KPIs, in a balanced 2x2 grid.
  await page.goto('http://localhost:18080/dashboard');
  const logout=page.locator('form[action="/logout"]');
  await logout.evaluate(form=>form.requestSubmit());
  await page.waitForURL('**/login?logout');
  await login('manager','manager123');await page.setViewportSize({width:1440,height:900});
  await page.goto('http://localhost:18080/dashboard');
  const manager=await page.locator('.dashboard-stats').evaluate(element=>({cards:element.children.length,
    columns:getComputedStyle(element).gridTemplateColumns.split(' ').length}));
  if(manager.cards!==4||manager.columns!==2)throw new Error(JSON.stringify(manager));
  if(errors.length)throw new Error(errors.join('\n'));
  return {results,manager,errors,reducedMotion:true};
}
