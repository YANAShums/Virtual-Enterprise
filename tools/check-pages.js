async (page) => {
  const report = [];
  const urls = ['/dashboard','/employees','/employees/new','/projects','/projects/new','/kanban','/workload','/employees/1','/projects/1',...['employees','departments','positions','users','schedules','absences','settings'].map(s=>'/admin?section='+s)];
  for (const path of urls) {
    const response = await page.goto('http://localhost:8080'+path);
    await page.locator('body').waitFor();
    report.push({path,status:response.status(),heading:await page.locator('h1').first().textContent(),title:await page.title(),unrendered:await page.locator('[th\\:field],[th\\:text]').count()});
  }
  return report;
}
