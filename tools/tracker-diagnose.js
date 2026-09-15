async (page) => {
  const result=[];
  for(const path of ['/kanban?projectId=1','/employees']) {
    await page.goto('http://localhost:8080'+path);
    result.push({path,metrics:await page.evaluate(()=>({viewport:innerWidth,root:document.documentElement.scrollWidth,body:document.body.scrollWidth,main:document.querySelector('main').scrollWidth})),overflow:await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(el=>{
      const rect=el.getBoundingClientRect();
      return rect.width && rect.right>innerWidth+1;
    }).slice(0,20).map(el=>({tag:el.tagName,class:el.className,position:getComputedStyle(el).position,overflow:getComputedStyle(el).overflow,width:el.getBoundingClientRect().width,right:el.getBoundingClientRect().right,text:el.textContent.slice(0,30)})))});
  }
  return result;
}
