async (page) => {
  await page.getByRole('textbox',{name:'Логин',exact:true}).fill('admin');
  await page.getByRole('textbox',{name:'Пароль',exact:true}).fill('admin123');
  await page.getByRole('button',{name:'Войти',exact:true}).click();
  await page.waitForURL('**/dashboard');
  return {title:await page.title(),heading:await page.locator('h1').textContent()};
}
