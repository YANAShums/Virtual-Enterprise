async (page) => {
  const result={};
  await page.goto('http://localhost:8080/employees');
  await page.locator('#employeeSearch').fill('NO_SUCH_EMPLOYEE_934');
  result.employeeSearchEmpty=await page.locator('.employee-row:visible').count()===0 && await page.locator('[data-filter-empty]').isVisible();
  await page.locator('#employeeSearch').fill('');
  result.employeeSearchReset=await page.locator('.employee-row:visible').count()>0;
  await page.goto('http://localhost:8080/workload');
  const cell=page.locator('.day-cell').first();
  const expected=await cell.getAttribute('data-employee');
  await cell.click();
  await page.locator('#dayDetails.show').waitFor();
  result.dayPanel=await page.locator('#dayEmployee').textContent()===expected;
  await page.screenshot({path:'output/playwright/workload-panel.png'});
  await page.keyboard.press('Escape');
  await page.setViewportSize({width:390,height:844});
  await page.goto('http://localhost:8080/employees');
  await page.getByRole('button',{name:'Открыть меню'}).click();
  await page.locator('#appSidebar.show').waitFor();
  result.mobileMenu=await page.locator('#appSidebar').isVisible();
  await page.screenshot({path:'output/playwright/mobile-menu.png'});
  await page.keyboard.press('Escape');
  await page.locator('#appSidebar.show').waitFor({state:'hidden'});
  result.mobileEscape=true;
  await page.setViewportSize({width:1440,height:1000});
  await page.goto('http://localhost:8080/kanban');
  const planned=page.locator('.task-card[data-status="PLANNED"]').first();
  if(await planned.count()) {
    await planned.locator('select').selectOption('DONE');
    await page.locator('#boardAlert.alert-danger').waitFor();
    result.invalidTransitionRejected=(await planned.getAttribute('data-status'))==='PLANNED';
  }
  return result;
}
