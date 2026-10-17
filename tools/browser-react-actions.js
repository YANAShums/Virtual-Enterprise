async (page) => {
  const base = "http://localhost:18080",
    stamp = Date.now().toString().slice(-8),
    checks = [];
  const errors = [];
  page.on("pageerror", (error) => errors.push(String(error)));
  const go = async (path) => {
    await page.goto(base + path);
    await page.waitForFunction(
      () =>
        document.querySelector("#workspace h1") &&
        !document.querySelector(".page-loading"),
    );
  };
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(base + "/login");
  await page.getByLabel("Логин", { exact: true }).fill("admin");
  await page.getByLabel("Пароль", { exact: true }).fill("wrong-password");
  await page.getByRole("button", { name: "Войти в пространство" }).click();
  await page.getByText("Неверный логин или пароль.", { exact: true }).waitFor();
  await page.getByLabel("Логин", { exact: true }).fill("admin");
  await page.getByLabel("Пароль", { exact: true }).fill("admin123");
  await page.getByRole("button", { name: "Войти в пространство" }).click();
  await page.getByRole("heading", { name: "Обзор команды" }).waitFor();
  checks.push("Ошибка входа и успешная повторная авторизация");
  await go("/kanban?projectId=1");
  await page
    .getByRole("button", { name: "Создать задачу", exact: true })
    .click();
  await page.getByRole("dialog", { name: "Новая задача" }).waitFor();
  await page
    .getByLabel("Название", { exact: true })
    .fill("Проверка React " + stamp);
  await page.locator("#createHours").fill("400");
  await page.getByRole("button", { name: "Рассчитать загрузку" }).click();
  await page.locator(".candidate input:not(:disabled)").first().waitFor();
  await page.locator(".candidate input:not(:disabled)").first().check();
  const saveTask = page.getByRole("dialog").getByRole("button", { name: "Создать задачу", exact: true });
  const confirmation = page.getByLabel("Подтверждаю критическую перегрузку");
  await confirmation.waitFor();
  if (!await saveTask.isDisabled()) throw new Error("Critical overload not blocked");
  await confirmation.check();
  if (await saveTask.isDisabled()) throw new Error("Confirmed assignment still blocked");
  await confirmation.uncheck();
  if (!await saveTask.isDisabled()) throw new Error("Unchecked overload confirmation ignored");
  await page.locator("#createHours").fill("8");
  if (await page.locator(".candidate").count() || !await saveTask.isDisabled()) throw new Error("Changed parameters did not invalidate preview");
  await page.getByRole("button", { name: "Рассчитать загрузку" }).click();
  await page.locator(".candidate input:not(:disabled)").first().waitFor();
  await page.locator(".candidate input:not(:disabled)").first().check();
  checks.push("Подтверждение критической перегрузки и сброс устаревшего расчёта");
  await page
    .getByRole("button", { name: "Создать задачу", exact: true })
    .last()
    .click();
  await page.waitForURL(/\/tasks\/\d+$/);
  await page
    .getByRole("heading", { name: "Проверка React " + stamp })
    .waitFor();
  const taskPath = new URL(page.url()).pathname;
  checks.push("Создание задачи с реальным расчётом загрузки");

  await page.getByLabel("Изменить статус задачи").selectOption("IN_PROGRESS");
  await page.waitForFunction(
    () =>
      document
        .querySelector("[data-task-record]")
        ?.getAttribute("data-status") === "IN_PROGRESS",
  );
  await page.getByLabel("Изменить статус задачи").selectOption("PLANNED");
  await page.getByRole("dialog", { name: "Изменение статуса" }).waitFor();
  await page
    .getByLabel("Причина", { exact: true })
    .fill("Проверка обратного перехода React");
  await page
    .getByRole("button", { name: "Изменить статус", exact: true })
    .click();
  await page.waitForFunction(
    () =>
      document
        .querySelector("[data-task-record]")
        ?.getAttribute("data-status") === "PLANNED",
  );
  checks.push("Смена статуса и обратный переход с обязательной причиной");

  await page
    .getByLabel("Новый пункт", { exact: true })
    .fill("Проверить интерфейс");
  await page
    .locator(".inline-add-form")
    .getByRole("button", { name: "Добавить" })
    .click();
  await page.getByText("Проверить интерфейс", { exact: true }).waitFor();
  await page
    .getByRole("button", { name: "Отметить пункт выполненным" })
    .click();
  await page.locator(".checklist-item.is-complete").waitFor();
  await page
    .getByLabel("Добавить комментарий", { exact: true })
    .fill("Комментарий из React " + stamp);
  await page.getByRole("button", { name: "Отправить", exact: true }).click();
  await page
    .getByText("Комментарий из React " + stamp, { exact: true })
    .waitFor();
  checks.push("Добавление и выполнение чек-листа, отправка комментария");
  await go("/kanban?projectId=1");
  const taskId = taskPath.split("/").at(-1);
  await page.locator("#taskSearch").fill("Проверка React " + stamp);
  await page.locator(`[data-task-id="${taskId}"]`).dragTo(page.locator('.kanban-column[data-status="IN_PROGRESS"]'), { sourcePosition: { x: 12, y: 12 }, targetPosition: { x: 20, y: 20 } });
  await page.locator(`.kanban-dropzone[data-status="IN_PROGRESS"] [data-task-id="${taskId}"]`).waitFor();
  checks.push("Перетаскивание карточки между колонками Kanban");

  await go("/employees/1");
  await page.getByRole("tab", { name: "Кадровая история" }).click();
  if (
    !(await page.locator("#employeeHistory").isVisible()) ||
    (await page.locator("#profileOverview").isVisible())
  )
    throw new Error("Employee tabs do not switch");
  await page.getByRole("tab", { name: "Кадровая история" }).press("Home");
  if (!(await page.locator("#profileOverview").isVisible()))
    throw new Error("Employee keyboard tabs failed");
  checks.push("Вкладки сотрудника и клавиатурное переключение");
  await go("/workload");
  await page.locator("button.day-cell").first().click();
  await page.getByRole("dialog", { name: "Загрузка за день" }).waitFor();
  await page.keyboard.press("Escape");
  checks.push("Дневная детализация загрузки");

  await go("/reports");
  const csvPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "CSV", exact: true }).click();
  const csv = await csvPromise;
  if (!csv.suggestedFilename().endsWith(".csv"))
    throw new Error("CSV download failed");
  const xlsxPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "XLSX", exact: true }).click();
  const xlsx = await xlsxPromise;
  if (!xlsx.suggestedFilename().endsWith(".xlsx"))
    throw new Error("XLSX download failed");
  checks.push("Экспорт CSV и XLSX с параметрами формы");

  await go("/projects/new");
  await page.locator("#code").fill("REACT" + stamp);
  await page.locator("#name").fill("Проверка проекта React " + stamp);
  const managerId = await page
    .locator("#managerId option")
    .nth(1)
    .getAttribute("value");
  await page.locator("#managerId").selectOption(managerId);
  await page.locator("#plannedStartDate").fill("2026-10-18");
  await page.locator("#plannedEndDate").fill("2026-11-18");
  await page
    .getByRole("button", { name: "Создать проект", exact: true })
    .click();
  await page.waitForURL(/\/projects\/\d+\?created/);
  const projectPath = new URL(page.url()).pathname;
  await page
    .getByRole("heading", { name: "Проверка проекта React " + stamp })
    .waitFor();
  await go(projectPath + "/edit");
  await page.locator("#name").fill("Обновлён проект React " + stamp);
  await page
    .getByRole("button", { name: "Сохранить изменения", exact: true })
    .click();
  await page
    .getByRole("heading", { name: "Обновлён проект React " + stamp })
    .waitFor();
  checks.push("Создание и редактирование проекта через серверные формы");

  await go("/employees/new");
  await page.locator("#personnelNumber").fill("REACT" + stamp);
  await page.locator("#lastName").fill("Проверка");
  await page.locator("#firstName").fill("React" + stamp);
  for (const id of ["departmentId", "positionId", "workScheduleId"]) {
    const option = await page.locator(`#${id} option`).nth(1).getAttribute("value");
    await page.locator(`#${id}`).selectOption(option);
  }
  await page.locator("#hireDate").fill("2026-10-01");
  await page.locator("#employmentRate").fill("1");
  await page.getByRole("button", { name: "Создать сотрудника", exact: true }).click();
  await page.waitForURL(/\/employees\/\d+\?created/);
  const employeePath = new URL(page.url()).pathname;
  await go(employeePath + "/edit");
  await page.locator("#email").fill("react" + stamp + "@example.test");
  await page.getByRole("button", { name: "Сохранить изменения", exact: true }).click();
  await page.waitForURL(/\/employees\/\d+\?updated/);
  await page.getByRole("link", { name: "react" + stamp + "@example.test", exact: true }).waitFor();
  checks.push("Создание и редактирование сотрудника");

  await go("/admin/departments/new");
  await page.locator("#code").fill("RU" + stamp);
  await page.locator("#name").fill("Проверка отдела React " + stamp);
  await page.getByRole("button", { name: "Сохранить", exact: true }).click();
  await page
    .getByText("Проверка отдела React " + stamp, { exact: true })
    .waitFor();
  checks.push("Сохранение справочника администрирования");
  await go("/admin/departments/new");
  await page.locator("#code").fill("RU" + stamp);
  await page.locator("#name").fill("Проверка сохранения ошибки " + stamp);
  await page.getByRole("button", { name: "Сохранить", exact: true }).click();
  await page.locator("#workspace .alert-danger").first().waitFor();
  if (await page.locator("#name").inputValue() !== "Проверка сохранения ошибки " + stamp) throw new Error("Validation discarded form values");
  checks.push("Серверная валидация и сохранение введённых значений");

  await page.getByRole("button", { name: "Аккаунт admin" }).click();
  await page.getByRole("button", { name: "Выйти из аккаунта" }).click();
  await page.getByLabel("Логин", { exact: true }).waitFor();
  await page.getByLabel("Логин", { exact: true }).fill("employee");
  await page.getByLabel("Пароль", { exact: true }).fill("employee123");
  await page.getByRole("button", { name: "Войти в пространство" }).click();
  await page.getByRole("heading", { name: "Обзор команды" }).waitFor();
  if (
    await page
      .locator(
        '.sidebar a[href="/admin"],.sidebar a[href="/attrition"],.sidebar a[href="/reports"]',
      )
      .count()
  )
    throw new Error("Employee sees restricted navigation");
  await go("/tasks");
  if (await page.getByRole("link", { name: "Создать задачу" }).count())
    throw new Error("Employee sees task creation");
  checks.push("Выход, повторный вход и ограниченная навигация сотрудника");
  for (const path of ["/admin", "/attrition", "/reports/export?format=csv"]) {
    const response = await page.request.get(base + path, { headers: { "X-FlowStaff-View": "1", Accept: "text/html" } });
    if (response.status() !== 403) throw new Error("Restricted route allowed: " + path + " status " + response.status());
  }
  checks.push("Серверный запрет административных и кадровых страниц для сотрудника");
  if (errors.length) throw new Error(JSON.stringify(errors));
  return { checks, taskPath, projectPath, employeePath, errors };
}
