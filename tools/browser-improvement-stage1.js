async page => {
  await page.goto('http://localhost:18080/login');
  await page.getByRole('textbox',{name:'Логин'}).fill('admin');
  await page.getByRole('textbox',{name:'Пароль'}).fill('admin123');
  await page.getByRole('button',{name:'Войти',exact:true}).click();
  const results=[]; const errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  for (const [width,height] of [[1440,900],[1280,720],[390,844]]) {
    await page.setViewportSize({width,height});
    const response=await page.goto('http://localhost:18080/admin/ml/models');
    await page.getByRole('button',{name:'Обучить кандидата',exact:true}).focus();
    const detail=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth+1,
      focused:document.activeElement.tagName==='BUTTON'}));
    if(response.status()!==200 || detail.overflow || !detail.focused) throw new Error(JSON.stringify({width,status:response.status(),...detail}));
    await page.screenshot({path:'output/playwright/ml-acceptance/models-'+width+'.png',fullPage:true});
    results.push({width,height,status:response.status(),...detail});
  }
  if(errors.length) throw new Error(errors.join('\n'));
  return {results,errors};
}
