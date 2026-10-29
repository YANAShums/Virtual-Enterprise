async (page) => {
  const base = "http://localhost:18080";
  const results = [],
    errors = [],
    consoleErrors = [];
  page.on("pageerror", (error) => errors.push(String(error)));
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(base + "/login");
  await page.getByLabel("Логин", { exact: true }).waitFor();
  await page.screenshot({ path: "output/react-ui/login-desktop.png" });
  for (const [width, height] of [[1280, 720], [390, 844]]) {
    await page.setViewportSize({ width, height });
    if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1)) throw new Error("Login horizontal overflow");
    await page.screenshot({ path: `output/react-ui/login-${width}.png`, fullPage: true });
  }
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByLabel("Логин", { exact: true }).fill("admin");
  await page.getByLabel("Пароль", { exact: true }).fill("admin123");
  await page.getByRole("button", { name: "Войти в пространство" }).click();
  await page.waitForURL("**/dashboard");
  await page.getByRole("heading", { name: "Обзор организации", exact: true }).waitFor();
  const routes = [
    "/dashboard",
    "/tasks",
    "/tasks/1",
    "/tasks/1/edit",
    "/kanban",
    "/projects",
    "/projects/1",
    "/projects/new",
    "/projects/1/edit",
    "/employees",
    "/employees/1",
    "/employees/new",
    "/employees/1/edit",
    "/employees/1/absences/new",
    "/employees/1/skills/new",
    "/workload",
    "/attrition",
    "/reports",
    "/notifications",
    "/admin",
    "/admin?section=departments",
    "/admin?section=positions",
    "/admin?section=users",
    "/admin?section=schedules",
    "/admin?section=roles",
    "/admin?section=calendar",
    "/admin/settings/edit",
    "/admin/ml/models",
  ];
  for (const [width, height] of [
    [1440, 900],
    [1280, 720],
    [390, 844],
  ]) {
    await page.setViewportSize({ width, height });
    for (const path of routes) {
      const response = await page.goto(base + path);
      await page.waitForFunction(
        () =>
          document.querySelector("#workspace h1") &&
          !document.querySelector(".page-loading"),
      );
      await page.evaluate(() => document.fonts.ready);
      const state = await page.evaluate(() => ({
        overflow: document.documentElement.scrollWidth > innerWidth + 1,
        error: document.querySelector(".error-state")?.textContent || "",
        heading: document.querySelector("#workspace h1")?.textContent,
        react: !!document.getElementById("root")?.firstElementChild,
        legacyScripts: [...document.scripts].some((script) =>
          /bootstrap|lucide|kanban\.js|task-status\.js/.test(script.src),
        ),
      }));
      results.push({
        width,
        height,
        path,
        status: response.status(),
        ...state,
      });
      if (
        state.overflow ||
        state.error ||
        !state.react ||
        state.legacyScripts ||
        response.status() !== 200
      )
        throw new Error(JSON.stringify(results.at(-1)));
      if (
        [
          "/dashboard",
          "/tasks",
          "/kanban",
          "/projects",
          "/employees",
          "/attrition",
        ].includes(path)
      )
        await page.screenshot({
          path: `output/react-ui/${path.slice(1)}-${width}.png`,
          fullPage: true,
        });
    }
  }
  await page.goto(base + "/dashboard");
  await page.getByRole("heading", { name: "Обзор организации", exact: true }).waitFor();
  await page.getByRole("button", { name: "Открыть меню", exact: true }).click();
  await page.getByRole("dialog").waitFor();
  await page.keyboard.press("Escape");
  await page.getByRole("dialog").waitFor({ state: "hidden" });
  if (
    !(await page
      .getByRole("button", { name: "Открыть меню", exact: true })
      .evaluate((el) => el === document.activeElement))
  )
    throw new Error("Mobile navigation did not restore focus");
  if (errors.length || consoleErrors.length)
    throw new Error(JSON.stringify({ errors, consoleErrors }));
  return { checked: results.length, results, errors, consoleErrors };
}
