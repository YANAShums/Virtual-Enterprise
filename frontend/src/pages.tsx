import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, ArrowUpRight, Eye, EyeSlash, CirclesThreePlus } from "@phosphor-icons/react";
import type { Page } from "./api";
import { PageUI, Render } from "./renderer";
import { Overview } from "./overview";
import { Empty } from "./ui";
import { People } from "./people";

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
        <Overview {...props} />
      ) : path === "/projects" ? (
        <Projects {...props} />
      ) : path === "/employees" ? (
        <People {...props} />
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
        <CirclesThreePlus size={32} className="brand-symbol" aria-hidden />
        <div>FlowStaff<span className="brand-ai">AI</span><small>Люди и работа в одном фокусе</small></div>
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

function Projects(props: Props) {
  const doc = props.page.document;
  const rows = [...doc.querySelectorAll<HTMLTableRowElement>(".project-item")];
  const [search, setSearch] = useState("");
  const [view, setView] = useState<"list" | "cards">("list");
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
        <div className="project-view-switch" role="group" aria-label="Вид проектов"><button className="btn btn-sm" aria-pressed={view === "list"} onClick={() => setView("list")}>Список</button><button className="btn btn-sm" aria-pressed={view === "cards"} onClick={() => setView("cards")}>Карточки</button></div>
      </div>
      {view === "list" ? <div className="table-responsive"><table className="table projects-table"><Render node={doc.querySelector(".projects-table thead")!} {...props} /><tbody>{visible.map((row, i) => <Render key={i} node={row} {...props} />)}</tbody></table></div> : <div className="project-library">
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
      </div>}
      {!visible.length && (
        <Empty title={rows.length ? "Ничего не найдено" : "Первый проект — впереди"}>
          {rows.length ? "Измените поисковый запрос, чтобы увидеть проекты." : "Проекты появятся в списке после создания. Доступные действия указаны вверху страницы."}
        </Empty>
      )}
    </div>
  );
}
