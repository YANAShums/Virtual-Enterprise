async (page) => {
  const reports=[];
  if(!await page.locator('#taskOffcanvas.show').count()) await page.getByRole('button',{name:'Добавить задачу'}).click();
  await page.locator('#taskOffcanvas.show').waitFor();
  await page.screenshot({path:'output/playwright/add-task.png'});
  await page.locator('#previewButton').click();
  await page.waitForFunction(()=>document.querySelectorAll('.candidate-option').length>0 || document.querySelector('#taskFormAlert .alert'));
  reports.push({candidates:await page.locator('.candidate-option').count(),error:await page.locator('#taskFormAlert').textContent()});
  if(await page.locator('.candidate-option input:not(:disabled)').count()) {
    await page.locator('.candidate-option input:not(:disabled)').first().check();
    reports.push({selected:await page.locator('.candidate-option.selected').count()});
    await page.locator('#taskHours').fill('100');
    reports.push({selectionReset:await page.locator('.candidate-option.selected').count()===0});
    await page.waitForFunction(()=>document.querySelectorAll('.candidate-option').length>0 || document.querySelector('#taskFormAlert .alert'));
    const critical=page.locator('.candidate-option.status-critical input:not(:disabled)');
    if(await critical.count()) {
      await critical.first().check();
      reports.push({criticalWarning:await page.locator('#criticalConfirmation').isVisible(),confirmationUnchecked:!await page.locator('#overloadConfirmed').isChecked()});
      await page.screenshot({path:'output/playwright/add-task-critical.png'});
    }
  }
  await page.keyboard.press('Escape');
  return reports;
}
