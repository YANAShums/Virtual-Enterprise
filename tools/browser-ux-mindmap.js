async page => {
  const errors=[];page.on('pageerror',error=>errors.push(String(error)));
  await page.goto('http://localhost:18765/docs/ux/flowstaff-ux-mindmap.html');
  await page.setViewportSize({width:1440,height:900});
  if(await page.locator('#pages button').count()!==15)throw new Error('Missing map pages');
  for(const id of ['01','02','03','04','05','06','07','08','09','10','11','12','13','14']) {
    await page.locator('#pages button[data-id="'+id+'"]').click();
    const check=await page.locator('#map svg').evaluate(svg=>{
      const bounds=svg.viewBox.baseVal;
      const outside=[...svg.querySelectorAll('text')].filter(text=>{
        const box=text.getBBox();return box.x<0||box.y<0||box.x+box.width>bounds.width+1||box.y+box.height>bounds.height+1;
      }).map(text=>text.textContent);
      return {outside,rectangles:svg.querySelectorAll('rect').length};
    });
    if(check.outside.length||check.rectangles<12)throw new Error(JSON.stringify({id,...check}));
  }
  await page.getByRole('button',{name:'Обзор',exact:true}).click();
  await page.locator('a[data-page="11"]').click();
  if(!await page.locator('#status').innerText().then(value=>value.startsWith('11')))throw new Error('Overview link failed');
  await page.getByRole('button',{name:'Увеличить карту'}).click();
  if(!await page.locator('#status').innerText().then(value=>value.includes('125%')))throw new Error('Zoom failed');
  await page.getByRole('button',{name:'По ширине',exact:true}).click();
  await page.getByRole('button',{name:'Обзор',exact:true}).click();
  await page.getByRole('searchbox').fill('увольнение');
  const matches=await page.locator('#pages button').count();
  if(matches<1||matches===15)throw new Error('Search did not narrow the map');
  await page.getByRole('searchbox').fill('__нет_функции__');
  if(!await page.locator('#mapEmpty').isVisible())throw new Error('No empty search state');
  await page.getByRole('button',{name:'Обзор',exact:true}).click();
  const sizes=[];
  for(const [width,height] of [[1440,900],[1280,720],[390,844]]) {
    await page.setViewportSize({width,height});
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
    if(overflow)throw new Error('Viewer page overflow at '+width);
    await page.screenshot({path:'output/ux-mindmap-'+width+'.png',fullPage:true});
    sizes.push({width,height,overflow});
  }
  await page.getByRole('combobox',{name:'Раздел карты',exact:true}).selectOption('08');
  if(!await page.locator('#status').innerText().then(value=>value.startsWith('08')))throw new Error('Mobile navigation failed');
  await page.getByText('Функции раздела текстом',{exact:true}).click();
  if(!await page.locator('#detailsList').isVisible())throw new Error('Text view missing');
  await page.screenshot({path:'output/ux-mindmap-attrition-mobile.png',fullPage:true});
  await page.setViewportSize({width:1440,height:900});
  await page.getByRole('button',{name:'Обзор',exact:true}).click();
  await page.locator('a[data-page="08"]').click();
  await page.locator('#map svg').screenshot({path:'output/ux-mindmap-attrition.png'});
  if(errors.length)throw new Error(errors.join('\n'));
  return {pages:15,svg_text_bounds:true,overview_link:true,zoom:true,search:true,mobile_navigation:true,sizes,errors};
}
