import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, ArrowUpRight, Eye, EyeSlash } from "@phosphor-icons/react";
import type { Page } from "./api";
import { PageUI, Render } from "./renderer";

type Props = { page: Page; refresh: () => unknown };
const text = (root: ParentNode, selector: string) =>
  root.querySelector(selector)?.textContent?.trim() || "";

export function PageContent(props: Props) {
  const { page } = props;
  if (page.login) return <Login {...props} />;
  const path = new URL(page.url, location.origin).pathname;
  return (
    <PageUI {...props}>
      {path === "/dashboard" ? (
        <Dashboard {...props} />
      ) : path === "/projects" ? (
        <Projects {...props} />
      ) : (
        <div className={`page-content ${path.replaceAll("/", "-").slice(1)}`}>
          {[...page.document.querySelector("main")!.childNodes].map(
            (node, index) => (
              <Render key={index} node={node} {...props} />
            ),
          )}
        </div>
      )}
    </PageUI>
  );
}

function Login({ page }: Props) {
  const [visible, setVisible] = useState(false);
  const error = page.document.querySelector(".alert-danger")?.textContent;
  const logout = page.document.querySelector(".alert-success")?.textContent;
  return (
    <div className="login-layout">
      <div className="login-brand">
        flowstaff<span>Люди · задачи · баланс</span>
      </div>
      <section className="login-story">
        <p className="eyebrow">МЕНЬШЕ ШУМА. БОЛЬШЕ ФОКУСА.</p>
        <h1>
          В ритме
          <br />
          вашей команды<span className="accent-period">.</span>
        </h1>
        <p>
          Задачи, люди и загрузка.
          <br />
          Всё, что нужно для спокойной работы.
        </p>
        <div className="login-story-footer">
          <span className="workspace-dot" />
          Одно пространство. Общий фокус.
        </div>
      </section>
      <section className="login-panel" aria-labelledby="loginTitle">
        <div className="login-panel-content">
          <p className="eyebrow">ВАШЕ РАБОЧЕЕ ПРОСТРАНСТВО</p>
          <h2 id="loginTitle">С возвращением</h2>
          <p className="muted">Войдите, чтобы продолжить.</p>
          {error && (
            <div className="alert alert-danger" role="alert">
              {error}
            </div>
          )}
          {logout && (
            <div className="alert alert-success" role="status">
              {logout}
            </div>
          )}
          <form action="/login" method="post">
            <input type="hidden" name="_csrf" value={page.csrf} />
            <label htmlFor="username">Логин</label>
            <input
              id="username"
              name="username"
              autoComplete="username"
              placeholder="Ваш логин"
              required
              autoFocus
            />
            <label htmlFor="password">Пароль</label>
            <div className="password-field">
              <input
                id="password"
                name="password"
                type={visible ? "text" : "password"}
                autoComplete="current-password"
                placeholder="Введите пароль"
                required
              />
              <button
                type="button"
                className="icon-button"
                aria-label={visible ? "Скрыть пароль" : "Показать пароль"}
                aria-pressed={visible}
                onClick={() => setVisible(!visible)}
              >
                {visible ? <EyeSlash size={20} /> : <Eye size={20} />}
              </button>
            </div>
            <button className="btn btn-primary login-submit">
              Войти в пространство <ArrowRight size={19} />
            </button>
          </form>
          <p className="login-help">
            Нет доступа? Обратитесь к администратору вашей команды.
          </p>
        </div>
      </section>
      <span className="login-copyright">FlowStaff AI</span>
    </div>
  );
}

function Dashboard({ page, refresh }: Props) {
  const doc = page.document;
  const stats = [...doc.querySelectorAll<HTMLAnchorElement>(".dashboard-stat")];
  const primary = stats.slice(0, 4);
  const lanes = [...doc.querySelectorAll(".task-lane")];
  const departments = [...doc.querySelectorAll(".department-bar")];
  const risks = [
    ...doc.querySelectorAll<HTMLAnchorElement>("a.insight-message"),
  ];
  const hasRisk = !!doc.querySelector("a.risk-stat");
  const canCreate = !!doc.querySelector(".rail-create");
  const date = new Intl.DateTimeFormat("ru-RU", {
    day: "numeric",
    month: "long",
    weekday: "long",
  }).format(new Date());
  return (
    <div className="dashboard-content">
      <div className="page-heading">
        <div>
          <p className="eyebrow">{date}</p>
          <h1>Обзор команды</h1>
          <p className="page-subtitle">Ваш рабочий ритм — в одном месте.</p>
        </div>
        {canCreate && (
          <Link to="/kanban?create=1" className="btn btn-primary">
            Новая задача <ArrowUpRight size={18} />
          </Link>
        )}
      </div>
      <div className="overview-metrics">
        {primary.map((stat, index) => (
          <Link
            key={index}
            to={stat.getAttribute("href")!}
            className={`metric metric-${index}`}
          >
            <span>{text(stat, "span")}</span>
            <strong>{text(stat, "strong")}</strong>
            <small>{text(stat, "small")}</small>
            <ArrowUpRight className="metric-arrow" size={18} />
          </Link>
        ))}
      </div>
      {doc.querySelector(".alert") && (
        <Render
          node={doc.querySelector(".alert")!}
          page={page}
          refresh={refresh}
        />
      )}
      <section className="focus-section">
        <div className="section-header">
          <div>
            <p className="eyebrow">В ФОКУСЕ</p>
            <h2>Ближайшие задачи</h2>
          </div>
          <Link className="text-link" to="/tasks">
            Все задачи <ArrowRight size={17} />
          </Link>
        </div>
        <div className="focus-lanes">
          {lanes.map((lane, index) => (
            <div className="focus-lane" key={index}>
              <div className="lane-label">
                <span className={`status-dot status-dot-${index}`} />
                {text(lane, "h3").replace(/\d+$/, "").trim()}
                <span className="count">
                  {lane.querySelectorAll(".compact-task").length}
                </span>
              </div>
              {[
                ...lane.querySelectorAll<HTMLAnchorElement>(".compact-task"),
              ].map((task) => (
                <Link
                  key={task.getAttribute("href")}
                  className="focus-task"
                  to={task.getAttribute("href")!}
                >
                  <span>{text(task, "span")}</span>
                  <time>{text(task, "small")}</time>
                  <ArrowUpRight size={17} />
                </Link>
              ))}
              {!lane.querySelector(".compact-task") && (
                <p className="quiet-empty">Здесь пока спокойно</p>
              )}
            </div>
          ))}
        </div>
      </section>
      <div className="overview-bottom">
        <section className="surface team-load">
          <div className="section-header">
            <div>
              <p className="eyebrow">БАЛАНС КОМАНДЫ</p>
              <h2>Загрузка подразделений</h2>
            </div>
            <Link
              className="icon-button"
              aria-label="Открыть загрузку"
              to="/workload"
            >
              <ArrowUpRight size={20} />
            </Link>
          </div>
          {departments.map((department, index) => (
            <div className="load-row" key={index}>
              <div>
                <span>{text(department, "span")}</span>
                <strong>
                  {department.querySelectorAll("span")[1]?.textContent}
                </strong>
              </div>
              <div className="load-track">
                <span
                  style={{
                    width:
                      department.querySelector<HTMLElement>(".progress-bar")
                        ?.style.width || "0%",
                  }}
                />
              </div>
            </div>
          ))}
          {!departments.length && (
            <p className="quiet-empty">
              Нет данных о загрузке. Проверьте выбранный период и доступность
              сервиса.
            </p>
          )}
          <p className="section-footnote">
            Плановая загрузка на ближайшие 7 дней
          </p>
        </section>
        <section className="surface attention-card">
          <div className="section-header">
            <div>
              <p className="eyebrow">ВНИМАНИЕ К ЛЮДЯМ</p>
              <h2>{hasRisk ? "Кадровый риск" : "Состояние команды"}</h2>
            </div>
            <span className="workspace-dot" />
          </div>
          {hasRisk ? (
            <>
              <div className="risk-totals">
                {stats.slice(4).map((stat, index) => (
                  <Link to={stat.getAttribute("href")!} key={index}>
                    <strong>{text(stat, "strong")}</strong>
                    <span>{text(stat, "span")}</span>
                  </Link>
                ))}
              </div>
              {risks.slice(0, 3).map((risk, index) => (
                <Link
                  className="risk-row"
                  key={index}
                  to={risk.getAttribute("href")!}
                >
                  <span>{text(risk, "strong")}</span>
                  <small>{text(risk, "p")}</small>
                  <ArrowUpRight size={16} />
                </Link>
              ))}
              {!risks.length && (
                <p className="quiet-empty">
                  Сохранённых прогнозов пока нет. Расчёт доступен в кадровой
                  аналитике.
                </p>
              )}
              <Link to="/attrition" className="text-link">
                Открыть прогнозы <ArrowRight size={17} />
              </Link>
            </>
          ) : (
            <>
              <h3 className="team-state">{text(doc, ".dashboard-hero h1")}</h3>
              <p className="muted">
                {text(doc, ".dashboard-hero-copy > p:not(.eyebrow)")}
              </p>
              <Link to="/workload" className="text-link">
                Посмотреть загрузку <ArrowRight size={17} />
              </Link>
            </>
          )}
        </section>
      </div>
    </div>
  );
}

function Projects(props: Props) {
  const doc = props.page.document;
  const rows = [...doc.querySelectorAll<HTMLTableRowElement>(".project-item")];
  const [search, setSearch] = useState("");
  const visible = rows.filter((row) =>
    row.textContent
      ?.toLocaleLowerCase("ru")
      .includes(search.trim().toLocaleLowerCase("ru")),
  );
  return (
    <div className="projects-content">
      <div className="page-heading">
        <div>
          <p className="eyebrow">ОБЩИЙ ФОКУС</p>
          <h1>
            Проекты <span className="heading-count">{rows.length}</span>
          </h1>
          <p className="page-subtitle">От замысла к результату.</p>
        </div>
        {doc.querySelector(".page-heading .btn") && (
          <Render node={doc.querySelector(".page-heading .btn")!} {...props} />
        )}
      </div>
      <div className="library-toolbar">
        <label className="sr-only" htmlFor="projectSearch">
          Поиск проекта
        </label>
        <input
          id="projectSearch"
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Найти проект"
        />
        <span className="muted">Все проекты</span>
      </div>
      <div className="project-library">
        {visible.map((row, index) => {
          const link =
            row.querySelector<HTMLAnchorElement>(".project-title a")!;
          return (
            <article className="project-tile" key={link.href}>
              <div className="project-tile-top">
                <span className={`project-initial project-color-${index % 4}`}>
                  {text(row, ".project-mark")}
                </span>
                <span>{row.cells[1]?.textContent}</span>
                <Link
                  className="icon-button"
                  to={
                    row
                      .querySelector('a[href^="/kanban"]')
                      ?.getAttribute("href") || "/kanban"
                  }
                  aria-label={`Доска: ${link.textContent}`}
                >
                  <ArrowUpRight size={21} />
                </Link>
              </div>
              <Link
                className="project-tile-title"
                to={link.getAttribute("href")!}
              >
                {link.textContent}
              </Link>
              <p className="muted">
                {text(row, ".project-title .small")} ·{" "}
                {row.cells[2]?.textContent}
              </p>
              <div className="project-tile-footer">
                <span>{row.cells[3]?.textContent}</span>
                <strong>{text(row, ".project-progress > span")}</strong>
              </div>
              <div className="load-track">
                <span
                  style={{
                    width:
                      row.querySelector<HTMLElement>(".progress-bar")?.style
                        .width || "0%",
                  }}
                />
              </div>
              <div className="project-risk">
                Риск: {row.cells[5]?.textContent}
              </div>
            </article>
          );
        })}
      </div>
      {!visible.length && (
        <div className="empty-state">
          <h2>
            {rows.length ? "Ничего не найдено" : "Первый проект — впереди"}
          </h2>
          <p>
            {rows.length
              ? "Попробуйте изменить запрос."
              : "Создайте проект, чтобы собрать команду и начать работу."}
          </p>
        </div>
      )}
    </div>
  );
}
