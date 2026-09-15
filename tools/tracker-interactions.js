async (page) => {
  const report=[],errors=[]; page.on('pageerror',error=>errors.push(error.message));
  await page.setViewportSize({width:1440,height:960});
  await page.goto('http://localhost:8080/kanban?projectId=1');
  await page.locator('#taskSearch').fill('запрос без совпадений');
  report.push({check:'board empty search',pass:await page.locator('.task-card:visible').count()===0});
  await page.locator('#taskSearch').fill('');
  const count=await page.locator('.task-card:visible').count();
  report.push({check:'board clear search',pass:count>0});
  const review=page.locator('.task-card[data-status="IN_PROGRESS"]').first();
  await review.locator('select').selectOption('PLANNED');
  await page.locator('.modal.show').waitFor();
  await page.getByRole('button',{name:'Отмена',exact:true}).click();
  await page.locator('.modal.show').waitFor({state:'hidden'});
  report.push({check:'cancel status change restores selection',pass:await review.locator('select').inputValue()==='IN_PROGRESS'});
  await page.getByRole('button',{name:'Создать задачу',exact:true}).click();
  await page.locator('#taskOffcanvas.show').waitFor();
  report.push({check:'create disabled before preview',pass:await page.locator('#createTaskButton').isDisabled()});
  await page.screenshot({path:'output/playwright/tracker-final/add-task.png'});
  await page.locator('#previewButton').click();
  await page.locator('.candidate-option').first().waitFor({timeout:20000});
  const available=page.locator('.candidate-option input:not(:disabled)');
  report.push({check:'real workload candidates',pass:await available.count()>0,count:await available.count()});
  if(await available.count()) {
    await available.first().check();
    report.push({check:'candidate selected',pass:await page.locator('.candidate-option.selected').count()===1});
    await page.locator('#taskHours').fill('48');
    report.push({check:'planning resets candidate',pass:await page.locator('.candidate-option.selected').count()===0 && await page.locator('#createTaskButton').isDisabled()});
    await page.locator('.candidate-option.status-critical input:not(:disabled)').first().waitFor({timeout:20000});
    await page.locator('.candidate-option.status-critical input:not(:disabled)').first().check();
    report.push({check:'critical requires confirmation',pass:await page.locator('#criticalConfirmation').isVisible() && await page.locator('#createTaskButton').isDisabled()});
    await page.locator('#overloadConfirmed').check();
    report.push({check:'critical confirmed',pass:!await page.locator('#createTaskButton').isDisabled()});
    await page.locator('#criticalConfirmation').scrollIntoViewIfNeeded();
    await page.screenshot({path:'output/playwright/tracker-final/add-task-critical.png'});
    const footerClear=await page.evaluate(()=>{
      const body=document.querySelector('#taskOffcanvas .offcanvas-body').getBoundingClientRect();
      const footer=document.querySelector('#taskOffcanvas .task-form-footer').getBoundingClientRect();
      return body.bottom<=footer.top+1 && footer.bottom<=innerHeight+1;
    });
    report.push({check:'footer never overlays form',pass:footerClear});
  }
  await page.keyboard.press('Escape'); await page.locator('#taskOffcanvas.show').waitFor({state:'hidden'});
  await page.goto('http://localhost:8080/employees/1');
  await page.getByRole('tab',{name:'Задачи',exact:true}).click();
  report.push({check:'employee task tab',pass:await page.locator('#employeeTasks').isVisible() && !await page.locator('#profileOverview').isVisible()});
  await page.getByRole('tab',{name:'Задачи',exact:true}).press('ArrowRight');
  report.push({check:'keyboard tabs',pass:await page.locator('#employeeSchedule').isVisible()});
  await page.goto('http://localhost:8080/tasks?q=PORTAL&projectId=1');
  report.push({check:'server task search',pass:(await page.locator('tbody tr').count())===4});
  await page.goto('http://localhost:8080/tasks?q=несуществующее');
  report.push({check:'server empty search',pass:await page.getByText('Задачи не найдены',{exact:true}).isVisible()});
  await page.goto('http://localhost:8080/workload');
  await page.locator('button.day-cell').first().click();
  await page.locator('#dayDetails.show').waitFor();
  report.push({check:'real daily detail',pass:(await page.locator('#dayEmployee').textContent()).length>0 && (await page.locator('#dayEmployeeLink').getAttribute('href')).startsWith('/employees/')});
  await page.screenshot({path:'output/playwright/tracker-final/workload-day.png'});
  await page.keyboard.press('Escape');
  await page.setViewportSize({width:390,height:844});
  const mobile=[];
  for(const path of ['/dashboard','/kanban?projectId=1','/projects','/projects/1','/tasks','/tasks/2','/employees','/employees/1','/workload','/projects/new','/employees/new','/admin']) {
    const response=await page.goto('http://localhost:8080'+path);
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
    mobile.push({path,status:response.status(),overflow});
    await page.screenshot({path:'output/playwright/tracker-final/mobile-'+path.slice(1).replace(/[/?=]/g,'-')+'.png',fullPage:true});
  }
  await page.getByRole('button',{name:'Открыть меню',exact:true}).click();
  await page.locator('#appSidebar.show').waitFor();
  report.push({check:'mobile menu',pass:await page.locator('#appSidebar.show').isVisible()});
  await page.screenshot({path:'output/playwright/tracker-final/mobile-menu.png'});
  await page.keyboard.press('Escape');
  await page.goto('http://localhost:8080/kanban?projectId=1&create=1');
  await page.locator('#taskOffcanvas.show').waitFor();
  await page.screenshot({path:'output/playwright/tracker-final/mobile-add-task.png'});
  report.push({check:'create deep link',pass:await page.locator('#taskOffcanvas.show').isVisible()});
  return {report,mobile,errors};
}
