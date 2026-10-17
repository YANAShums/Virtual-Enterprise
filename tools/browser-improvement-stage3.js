async page => {
  await page.goto('http://localhost:18080/login');
  if(await page.getByRole('textbox',{name:'Логин'}).count()) {
    await page.getByRole('textbox',{name:'Логин'}).fill('admin');
    await page.getByRole('textbox',{name:'Пароль'}).fill('admin123');
    await page.getByRole('button',{name:'Войти',exact:true}).click();
  }
  const results=[];
  for(const [width,height] of [[1440,900],[1280,720],[390,844]]) {
    await page.setViewportSize({width,height});
    const response=await page.goto('http://localhost:18080/employees/1/edit');
    const category=page.getByLabel('Категория увольнения'); await category.focus();
    if(response.status()!==200 || await category.locator('option').count()!==4) throw new Error('Employee form unavailable');
    const state=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth+1,focus:document.activeElement.id==='terminationCategory'}));
    if(state.overflow || !state.focus) throw new Error(JSON.stringify(state));
    results.push({width,height,...state});
    await page.screenshot({path:'output/playwright/ml-acceptance/employee-'+width+'.png',fullPage:true});
  }
  return results;
}
