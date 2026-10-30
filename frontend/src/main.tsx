import React from "react";
import ReactDOM from "react-dom/client";
import {
  BrowserRouter,
  Link,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  BellSimple,
  CalendarBlank,
  CaretDown,
  CirclesThreePlus,
  ChartBar,
  CheckSquare,
  FileText,
  FolderSimple,
  GearSix,
  House,
  Kanban,
  List,
  MagnifyingGlass,
  SignOut,
  TrendDown,
  Users,
  X,
} from "@phosphor-icons/react";
import * as Dialog from "@radix-ui/react-dialog";
import { downloadFile, fetchPage, submitForm } from "./api";
import type { Page } from "./api";
import { PageContent } from "./pages";
import { Render } from "./renderer";
import { Alert, Skeleton } from "./ui";
import { DesignSystem } from "./design-system";
import "./tokens.css";
import "./styles.css";
import "./product-theme.css";

const client = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 15_000, refetchOnWindowFocus: false },
  },
});

function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const cache = useQueryClient();
  const url = location.pathname + location.search;
  const designSystem = import.meta.env.DEV && location.pathname === "/dev/design-system";
  const query = useQuery({
    enabled: !designSystem,
    queryKey: ["page", url],
    queryFn: ({ signal }) => fetchPage(url, undefined, signal),
  });
  const [menu, setMenu] = useState(false);
  const [account, setAccount] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const main = useRef<HTMLElement>(null);
  const search = useRef<HTMLInputElement>(null);
  const page = query.data;
  const waitForForm =
    query.isFetching &&
    (page?.login || /\/(new|edit|delete)$/.test(location.pathname));

  useEffect(() => {
    setMenu(false);
    setAccount(false);
    setMessage("");
    window.scrollTo(0, 0);
    main.current?.focus({ preventScroll: true });
  }, [url]);
  useEffect(() => {
    if (!page) return;
    document.title = `${page.title} — FlowStaff`;
    if (page.url !== url) navigate(page.url, { replace: true });
  }, [page, url, navigate]);
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.key === "Escape") setAccount(false);
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        search.current?.focus();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  async function submit(event: React.FormEvent<HTMLDivElement>) {
    const form = event.target as HTMLFormElement;
    if (
      !(form instanceof HTMLFormElement) ||
      form.hasAttribute("data-react-form") ||
      !page
    )
      return;
    event.preventDefault();
    if (busy || !form.reportValidity()) return;
    const start = form.querySelector<HTMLInputElement>(
      '[name="start"],[name="from"],[name="plannedStartDate"]',
    );
    const end = form.querySelector<HTMLInputElement>(
      '[name="end"],[name="to"],[name="plannedEndDate"]',
    );
    if (start?.value && end?.value && end.value < start.value) {
      setMessage("Окончание периода должно быть не раньше начала.");
      return;
    }
    setBusy(true);
    setMessage("");
    try {
      const result = await submitForm(
        form,
        (event.nativeEvent as SubmitEvent).submitter,
        page,
      );
      if (result.download) {
        await downloadFile(result.url);
        return;
      }
      cache.removeQueries({
        predicate: (item) => item.queryKey[1] !== result.url,
      });
      if (result.page) cache.setQueryData(["page", result.url], result.page);
      navigate(result.url);
      if (result.url === url && !result.page) await query.refetch();
    } catch (error) {
      setMessage((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const refresh = () => cache.invalidateQueries({ queryKey: ["page"] });
  const authenticated = page && !page.login;
  const nav = page
    ? [...page.document.querySelectorAll<HTMLAnchorElement>(".app-nav a")].map(
        (a) => ({
          href: a.getAttribute("href")!,
          label:
            a.querySelector(".nav-label")?.textContent ?? a.textContent ?? "",
        }),
      )
    : [];
  const username =
    page?.document.querySelector(".account-copy strong")?.textContent ||
    "Аккаунт";
  const role =
    page?.document.querySelector(".account-copy small")?.textContent || "";
  const canNotify = !!page?.document.querySelector(".topbar-notifications");
  const unread = page?.document.querySelector(
    ".notification-count",
  )?.textContent;
  const navGroups = [
    { title: "Работа", routes: ["/dashboard", "/projects", "/tasks", "/kanban"] },
    { title: "Команда", routes: ["/employees", "/workload"] },
    { title: "Кадровая аналитика", routes: ["/attrition", "/reports"] },
    { title: "Система", routes: ["/admin"] },
  ];
  const navItems = <>
    <Link className="brand" to="/dashboard"><CirclesThreePlus size={29} className="brand-symbol" aria-hidden /><span>FlowStaff<span className="brand-ai">AI</span></span></Link>
    <nav aria-label="Основная навигация">{navGroups.map(group => {
      const items = group.routes.flatMap(route => nav.filter(item => item.href === route));
      if (!items.length) return null;
      return <div className="nav-group" key={group.title}><span className="nav-group-label">{group.title}</span>{items.map(item => {
        const Icon = ({ "/dashboard": House, "/tasks": CheckSquare, "/projects": FolderSimple, "/kanban": Kanban, "/employees": Users, "/workload": ChartBar, "/attrition": TrendDown, "/reports": FileText, "/admin": GearSix } as Record<string, typeof House>)[item.href] || FolderSimple;
        return <Link key={item.href} to={item.href} aria-current={location.pathname === item.href || location.pathname.startsWith(item.href + "/") ? "page" : undefined}><Icon size={19} aria-hidden /><span>{({ Главная: "Обзор", Команда: "Сотрудники", Доски: "Доска", Аналитика: "Загрузка", Администрирование: "Управление" } as Record<string, string>)[item.label] || item.label}</span></Link>;
      })}</div>;
    })}</nav>
    <div className="sidebar-bottom"><CirclesThreePlus size={22} aria-hidden /><strong>Данные для решений.</strong><span>Люди — в центре внимания.</span>{import.meta.env.DEV && <Link to="/dev/design-system">Дизайн-система <ArrowRight size={14} aria-hidden /></Link>}</div>
  </>;
  if (designSystem) return <DesignSystem />;

  return (
    <div
      className={authenticated ? "app" : "guest-app"}
      onSubmitCapture={submit}
      aria-busy={busy || query.isFetching}
    >
      <a className="skip-link" href="#workspace">
        К содержимому
      </a>
      {authenticated && (
        <>
          <aside className="sidebar">{navItems}</aside>
          <Dialog.Root open={menu} onOpenChange={setMenu}>
            <Dialog.Portal>
              <Dialog.Overlay className="modal-overlay" />
              <Dialog.Content
                className="mobile-nav"
                onCloseAutoFocus={(event) => {
                  event.preventDefault();
                  document
                    .querySelector<HTMLButtonElement>(".menu-button")
                    ?.focus();
                }}
              >
                <Dialog.Title className="sr-only">Навигация</Dialog.Title>
                <Dialog.Description className="sr-only">
                  Разделы рабочего пространства
                </Dialog.Description>
                <Dialog.Close
                  className="icon-button mobile-close"
                  aria-label="Закрыть меню"
                >
                  <X size={20} />
                </Dialog.Close>
                {navItems}
              </Dialog.Content>
            </Dialog.Portal>
          </Dialog.Root>
          <header className="topbar">
            <div className="topbar-leading">
              <button
                className="icon-button menu-button"
                aria-label="Открыть меню"
                onClick={() => setMenu(true)}
              >
                <List size={23} />
              </button>
              <button
                className="icon-button back-button"
                aria-label="Назад"
                onClick={() => navigate(-1)}
              >
                <ArrowLeft size={19} />
              </button>
              <span className="workspace-label">Рабочее пространство</span>
            </div>
            <form
              className="global-search"
              data-react-form
              onSubmit={(event) => {
                event.preventDefault();
                const value = search.current?.value.trim();
                navigate(
                  "/tasks" + (value ? "?q=" + encodeURIComponent(value) : ""),
                );
              }}
            >
              <MagnifyingGlass size={19} />
              <input
                ref={search}
                aria-label="Поиск задач"
                placeholder="Найти задачу"
                type="search"
              />
              <kbd>
                {navigator.userAgent.includes("Mac") ? "⌘ K" : "Ctrl K"}
              </kbd>
            </form>
            <div className="topbar-trailing">
              <Link className="context-period" to="/workload"><CalendarBlank size={17} aria-hidden /><span>Ближайшие 7 дней</span><CaretDown size={12} aria-hidden /></Link>
              {canNotify && (
                <Link
                  to="/notifications"
                  className="icon-button notification-button"
                  aria-label={unread ? `Уведомления: ${unread}` : "Уведомления"}
                >
                  <BellSimple size={21} />
                  {unread && <span className="unread-dot" />}
                </Link>
              )}
              <div className="account-wrap">
                <button
                  className="account-trigger"
                  aria-expanded={account}
                  aria-label={`Аккаунт ${username}`}
                  onClick={() => setAccount(!account)}
                >
                  <span className="account-avatar">{username.slice(0, 2).toUpperCase()}</span><span className="account-summary"><strong>{username}</strong><small>{role}</small></span><CaretDown size={13} aria-hidden />
                </button>
                {account && (
                  <>
                    <button
                      className="account-dismiss"
                      aria-label="Закрыть меню аккаунта"
                      onClick={() => setAccount(false)}
                    />
                    <div className="account-menu">
                      <strong>{username}</strong>
                      <small>{role}</small>
                      {page?.document.querySelector(".dropdown-menu form") && (
                        <Render
                          node={page.document.querySelector(
                            ".dropdown-menu form",
                          )!}
                          page={page}
                          refresh={refresh}
                        />
                      )}
                      <SignOut className="account-exit-icon" size={18} />
                    </div>
                  </>
                )}
              </div>
            </div>
          </header>
        </>
      )}
      <main
        id="workspace"
        className={authenticated ? "workspace" : "login-workspace"}
        ref={main}
        tabIndex={-1}
      >
        {message && (
          <Alert tone="danger">
            {message}
            <button
              className="icon-button"
              aria-label="Закрыть сообщение"
              onClick={() => setMessage("")}
            >
              <X size={16} />
            </button>
          </Alert>
        )}
        {(query.isPending || waitForForm) && (
          <Skeleton label="Загружаем рабочее пространство…" />
        )}
        {query.isError && (
          <div className="error-state">
            <p className="eyebrow">FLOWSTAFF</p>
            <h1>Не удалось открыть страницу</h1>
            <p role="alert">{query.error.message}</p>
            <button className="btn btn-primary" onClick={() => query.refetch()}>
              Повторить
            </button>
            <Link className="btn" to="/login">
              Войти заново
            </Link>
          </div>
        )}
        {page && !waitForForm && (
          <PageContent
            key={url + ":" + query.dataUpdatedAt}
            page={page}
            refresh={refresh}
          />
        )}
      </main>
      {busy && (
        <div className="save-indicator" role="status">
          Сохраняем изменения…
        </div>
      )}
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={client}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
