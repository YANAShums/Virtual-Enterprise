import {
  createContext,
  createElement,
  useContext,
  useRef,
  useState,
} from "react";
import type { ComponentType, ReactNode } from "react";
import { Link } from "react-router-dom";
import * as Dialog from "@radix-ui/react-dialog";
import {
  ArrowRight,
  ArrowUpRight,
  Check,
  CheckCircle,
  Circle,
  PencilSimple,
  TrashSimple,
  X,
} from "@phosphor-icons/react";
import { api } from "./api";
import type { Page } from "./api";
import { TaskCreate } from "./task-create";

type UI = {
  page: Page;
  refresh: () => unknown;
  filters: Record<string, string>;
  filter: (key: string, value: string) => void;
  tabs: Record<string, string>;
  tab: (group: string, value: string) => void;
  open: (id: string, data?: DOMStringMap) => void;
  status: (record: Element, target: string) => Promise<void>;
  drag: Element | null;
  setDrag: (record: Element | null) => void;
};
const Context = createContext<UI>(null!);
export function PageUI({
  page,
  refresh,
  children,
}: {
  page: Page;
  refresh: () => unknown;
  children: ReactNode;
}) {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [tabs, setTabs] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      [...page.document.querySelectorAll("[data-tabs]")].map((group, index) => {
        group.setAttribute("data-group", String(index));
        const buttons = [
          ...group.querySelectorAll<HTMLElement>("[data-tab-target]"),
        ];
        return [
          String(index),
          buttons.find((b) => "#" + b.dataset.tabTarget === location.hash)
            ?.dataset.tabTarget ??
            buttons[0]?.dataset.tabTarget ??
            "",
        ];
      }),
    ),
  );
  const [modal, setModal] = useState<{
    id: string;
    data?: DOMStringMap;
  } | null>(() =>
    new URL(page.url, location.origin).searchParams.get("create") === "1" &&
    page.document.querySelector(
      '[data-bs-target="#taskOffcanvas"]:not([disabled])',
    )
      ? { id: "taskOffcanvas" }
      : null,
  );
  const [notice, setNotice] = useState("");
  const draggedRecord = useRef<Element | null>(null);
  const [pending, setPending] = useState(false);
  const opener = useRef<HTMLElement | null>(null);
  const commentOpener = useRef<HTMLElement | null>(null);
  const [comment, setComment] = useState<{
    record: Element;
    target: string;
  } | null>(null);
  async function update(record: Element, target: string, reason?: string) {
    if (pending) return;
    setPending(true);
    setNotice("");
    try {
      await api(`/api/v1/tasks/${record.getAttribute("data-task-id")}/status`, {
        method: "PATCH",
        body: JSON.stringify({
          targetStatusCode: target,
          version: Number(record.getAttribute("data-version")),
          comment: reason || null,
          kanbanOrder: Math.floor(Date.now() / 1000),
        }),
      });
      setComment(null);
      await refresh();
    } catch (error) {
      setNotice((error as Error).message);
    } finally {
      setPending(false);
    }
  }
  async function status(record: Element, target: string) {
    const old = record.getAttribute("data-status") || "";
    if (pending || old === target || record.querySelector("select:disabled"))
      return;
    if (!(transitions[old] || []).includes(target)) {
      setNotice("Этот переход статуса недоступен.");
      return;
    }
    if (
      target === "CANCELLED" ||
      (old === "IN_PROGRESS" && target === "PLANNED") ||
      (old === "REVIEW" && target === "IN_PROGRESS")
    ) {
      commentOpener.current = document.activeElement as HTMLElement;
      setComment({ record, target });
      return;
    }
    await update(record, target);
  }
  const value: UI = {
    page,
    refresh,
    filters,
    filter: (key, v) => setFilters((old) => ({ ...old, [key]: v })),
    tabs,
    tab: (key, v) => {
      setTabs((old) => ({ ...old, [key]: v }));
      history.replaceState(
        null,
        "",
        location.pathname + location.search + "#" + v,
      );
    },
    open: (id, data) => {
      opener.current = document.activeElement as HTMLElement;
      setModal({ id, data });
    },
    status,
    get drag() {
      return draggedRecord.current;
    },
    setDrag: (record) => {
      draggedRecord.current = record;
    },
  };
  return (
    <Context.Provider value={value}>
      {notice && (
        <div className="alert alert-danger" role="alert">
          {notice}
          <button
            className="icon-button"
            aria-label="Закрыть"
            onClick={() => setNotice("")}
          >
            <X size={16} />
          </button>
        </div>
      )}
      {children}
      <Dialog.Root
        open={!!modal}
        onOpenChange={(open) => {
          if (!open) setModal(null);
        }}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="modal-overlay" />
          <Dialog.Content
            className="drawer"
            onCloseAutoFocus={(event) => {
              event.preventDefault();
              const target = opener.current?.isConnected
                ? opener.current
                : document.querySelector<HTMLElement>(
                    '[data-task-record] select, [aria-label="Открыть меню"], [data-bs-target]',
                  );
              target?.focus();
            }}
          >
            <Dialog.Title>
              {modal?.id === "taskOffcanvas"
                ? "Новая задача"
                : modal?.id === "employeePreview"
                  ? "Сотрудник"
                  : "Загрузка за день"}
            </Dialog.Title>
            <Dialog.Description className="drawer-description">
              {modal?.id === "taskOffcanvas"
                ? "Сначала параметры, затем исполнитель."
                : "Данные рабочего пространства"}
            </Dialog.Description>
            <Dialog.Close
              className="icon-button drawer-close"
              aria-label="Закрыть"
            >
              <X size={22} />
            </Dialog.Close>
            {modal?.id === "taskOffcanvas" && (
              <TaskCreate
                page={page}
                close={() => setModal(null)}
                refresh={refresh}
              />
            )}
            {modal?.id === "dayDetails" && (
              <div className="day-detail">
                <p>{modal.data?.date}</p>
                <h2>{modal.data?.employee}</h2>
                <strong className="big-number">{modal.data?.percent}%</strong>
                <dl>
                  <dt>Доступно</dt>
                  <dd>{modal.data?.available} ч</dd>
                  <dt>Назначено</dt>
                  <dd>{modal.data?.assigned} ч</dd>
                  <dt>Состояние</dt>
                  <dd>
                    {loadLabels[modal.data?.status || ""] || modal.data?.status}
                  </dd>
                </dl>
                <Link
                  className="btn btn-primary"
                  to={`/employees/${modal.data?.employeeId}`}
                >
                  Карточка сотрудника <ArrowRight size={17} />
                </Link>
              </div>
            )}
            {modal?.id === "employeePreview" && (
              <div className="preview-detail">
                <h2>{modal.data?.name}</h2>
                <p>{modal.data?.position}</p>
                <dl>
                  <dt>Подразделение</dt>
                  <dd>{modal.data?.department}</dd>
                  <dt>Контакт</dt>
                  <dd>{modal.data?.contact}</dd>
                  <dt>Загрузка</dt>
                  <dd>{modal.data?.load}</dd>
                </dl>
                <Link
                  className="btn btn-primary"
                  to={`/employees/${modal.data?.employeeId}`}
                >
                  Открыть карточку <ArrowRight size={17} />
                </Link>
              </div>
            )}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
      <Dialog.Root
        open={!!comment}
        onOpenChange={(open) => {
          if (!open && !pending) setComment(null);
        }}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="modal-overlay" />
          <Dialog.Content
            className="confirm-dialog"
            onCloseAutoFocus={(event) => {
              event.preventDefault();
              commentOpener.current?.focus();
            }}
          >
            <Dialog.Title>Изменение статуса</Dialog.Title>
            <Dialog.Description>
              Укажите причину. Она сохранится в истории задачи.
            </Dialog.Description>
            <form
              data-react-form
              onSubmit={(event) => {
                event.preventDefault();
                const text = String(
                  new FormData(event.currentTarget).get("comment") || "",
                ).trim();
                if (text && comment)
                  void update(comment.record, comment.target, text);
              }}
            >
              <label htmlFor="statusReason">Причина</label>
              <textarea
                id="statusReason"
                name="comment"
                required
                maxLength={2000}
                rows={4}
              />
              <div className="form-actions">
                <Dialog.Close className="btn" disabled={pending}>
                  Отмена
                </Dialog.Close>
                <button className="btn btn-primary" disabled={pending}>
                  {pending ? "Сохраняем…" : "Изменить статус"}
                </button>
              </div>
            </form>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </Context.Provider>
  );
}

export const transitions: Record<string, string[]> = {
  PLANNED: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["PLANNED", "REVIEW", "CANCELLED"],
  REVIEW: ["IN_PROGRESS", "DONE"],
  DONE: ["IN_PROGRESS"],
  CANCELLED: ["PLANNED"],
};
export const loadLabels: Record<string, string> = {
  SEVERE_UNDERLOAD: "Сильно ниже нормы",
  UNDERLOAD: "Ниже нормы",
  NORMAL: "Норма",
  WARNING: "Повышенная",
  CRITICAL: "Критическая",
  UNAVAILABLE: "Нет данных",
};
const icons: Record<
  string,
  ComponentType<{ size?: number; "aria-hidden"?: boolean }>
> = {
  check: Check,
  pencil: PencilSimple,
  "trash-2": TrashSimple,
  "circle-check-big": CheckCircle,
  circle: Circle,
  "columns-3": ArrowUpRight,
  "arrow-right": ArrowRight,
  "arrow-up-right": ArrowUpRight,
  "panel-right-open": ArrowUpRight,
  "circle-check": CheckCircle,
};
const booleanAttributes = new Set([
  "disabled",
  "required",
  "multiple",
  "readOnly",
  "hidden",
  "open",
  "noValidate",
  "autoFocus",
  "draggable",
]);
const names: Record<string, string> = {
  class: "className",
  for: "htmlFor",
  tabindex: "tabIndex",
  colspan: "colSpan",
  rowspan: "rowSpan",
  maxlength: "maxLength",
  minlength: "minLength",
  readonly: "readOnly",
  autocomplete: "autoComplete",
  autofocus: "autoFocus",
  novalidate: "noValidate",
  enctype: "encType",
  formaction: "formAction",
  inputmode: "inputMode",
};

export function Render({
  node,
  page,
  refresh,
}: {
  node: Node;
  page: Page;
  refresh: () => unknown;
}) {
  const ui = useContext(Context);
  return render(node, ui || ({ page, refresh } as UI));
}

function render(node: Node, ui: UI, key?: number): ReactNode {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent;
  if (!(node instanceof Element)) return null;
  const element = node as HTMLElement;
  const tag = element.tagName.toLowerCase();
  const visibleCard = (card: HTMLElement) => {
    const search = (ui.filters?.taskSearch || "")
      .trim()
      .toLocaleLowerCase("ru");
    return !(
      (search &&
        !card.dataset.search?.toLocaleLowerCase("ru").includes(search)) ||
      (ui.filters?.priorityFilter &&
        card.dataset.priority !== ui.filters.priorityFilter) ||
      (ui.filters?.overdueFilter === "true" && card.dataset.overdue !== "true")
    );
  };
  if (
    ["script", "style", "link", "meta", "iframe", "object"].includes(tag) ||
    element.matches(
      ".offcanvas, #employeePreview, .scroll-hint, .widget-icon, .kpi-icon, .form-section-icon, .project-mark:has(i), .heading-title > .project-mark",
    )
  )
    return null;
  if (tag === "i" && element.dataset.lucide) {
    if (
      element.dataset.priority === "HIGH" ||
      element.dataset.priority === "CRITICAL"
    )
      return (
        <span key={key} className="priority-flag" title={element.title}>
          {element.dataset.priority === "CRITICAL" ? "Срочно" : "Высокий"}
        </span>
      );
    const Icon = icons[element.dataset.lucide];
    if (!Icon) return null;
    return <Icon key={key} size={17} aria-hidden />;
  }
  if (tag === "img") return null;
  if (element.matches(".assignee-icon")) {
    const name = element.title || "Не назначен";
    const parts = name.split(" ");
    return (
      <span key={key} className="card-assignee" title={name} aria-label={name}>
        {parts[0]}
        {parts[1] ? " " + parts[1][0] + "." : ""}
      </span>
    );
  }
  const props: Record<string, unknown> = { key };
  for (const attr of [...element.attributes]) {
    if (
      /^on/i.test(attr.name) ||
      ["style", "value", "checked", "selected"].includes(attr.name) ||
      attr.name.startsWith("data-bs-")
    )
      continue;
    const name = names[attr.name] || attr.name;
    props[name] = booleanAttributes.has(name)
      ? attr.value !== "false"
      : attr.value;
  }
  if (
    element.style.width &&
    element.matches(
      ".progress-bar, .bar-fill, .risk-meter-fill, .risk-probability-fill",
    )
  )
    props.style = { width: element.style.width };
  if (
    element.matches(".risk-donut") &&
    element.style.background.startsWith("conic-gradient")
  )
    props.style = { background: element.style.background };
  if (element.matches(".history-risk-bar"))
    props.style = { height: element.style.height };
  if (element.matches(".history-load-marker"))
    props.style = { bottom: element.style.bottom };
  if (tag === "input") {
    if (element.getAttribute("type") !== "file")
      props.defaultValue = (element as HTMLInputElement).value;
    if (["checkbox", "radio"].includes(element.getAttribute("type") || ""))
      props.defaultChecked = (element as HTMLInputElement).checked;
    if (element.getAttribute("type") === "date")
      props.onInput = (event: React.FormEvent<HTMLInputElement>) =>
        event.currentTarget.setCustomValidity("");
  }
  if (tag === "textarea") props.defaultValue = element.textContent;
  if (tag === "select")
    props.defaultValue = (element as HTMLSelectElement).multiple
      ? [...(element as HTMLSelectElement).selectedOptions].map((o) => o.value)
      : (element as HTMLSelectElement).value;
  if (tag === "option") props.value = (element as HTMLOptionElement).value;
  if (tag === "button" && element.hasAttribute("value"))
    props.value = element.getAttribute("value");
  if (element.hasAttribute("data-filter-target")) {
    props.onChange = (event: React.ChangeEvent<HTMLInputElement>) =>
      ui.filter(element.dataset.filterTarget!, event.target.value);
  }
  if (element.matches("#taskSearch, #priorityFilter, #overdueFilter"))
    props.onChange = (event: React.ChangeEvent<HTMLInputElement>) =>
      ui.filter(
        element.id,
        event.target.type === "checkbox"
          ? String(event.target.checked)
          : event.target.value,
      );
  if (element.getAttribute("onchange")?.includes("form.submit"))
    props.onChange = (event: React.ChangeEvent<HTMLSelectElement>) =>
      event.currentTarget.form?.requestSubmit();
  for (const [selector, query] of Object.entries(ui.filters || {})) {
    if (
      !["taskSearch", "priorityFilter", "overdueFilter"].includes(selector) &&
      query &&
      element.matches(selector)
    )
      props.hidden = !element.textContent
        ?.toLocaleLowerCase("ru")
        .includes(query.trim().toLocaleLowerCase("ru"));
  }
  if (element.matches("[data-filter-empty]")) {
    const selectors = Object.entries(ui.filters || {}).filter(
      ([s, q]) =>
        !["taskSearch", "priorityFilter", "overdueFilter"].includes(s) &&
        q.trim(),
    );
    props.hidden = !selectors.some(([selector, query]) => {
      const rows = [...ui.page.document.querySelectorAll(selector)];
      return (
        rows.length > 0 &&
        !rows.some((row) =>
          row.textContent
            ?.toLocaleLowerCase("ru")
            .includes(query.trim().toLocaleLowerCase("ru")),
        )
      );
    });
  }
  if (element.dataset.tabTarget) {
    const group =
      element.closest("[data-tabs]")?.getAttribute("data-group") || "0";
    const active = ui.tabs[group] === element.dataset.tabTarget;
    props.className = active ? "active" : "";
    props["aria-selected"] = active;
    props.tabIndex = active ? 0 : -1;
    props.onClick = () => ui.tab(group, element.dataset.tabTarget!);
    props.onKeyDown = (event: React.KeyboardEvent) => {
      const buttons = [
        ...element
          .closest("[data-tabs]")!
          .querySelectorAll<HTMLElement>("[data-tab-target]"),
      ];
      const index = buttons.indexOf(element);
      const next =
        event.key === "ArrowRight"
          ? buttons[(index + 1) % buttons.length]
          : event.key === "ArrowLeft"
            ? buttons[(index + buttons.length - 1) % buttons.length]
            : event.key === "Home"
              ? buttons[0]
              : event.key === "End"
                ? buttons.at(-1)
                : undefined;
      if (next) {
        event.preventDefault();
        ui.tab(group, next.dataset.tabTarget!);
        document.getElementById(next.id)?.focus();
      }
    };
  }
  if (element.matches('[role="tabpanel"]')) {
    const button = ui.page.document.querySelector(
      `[data-tab-target="${element.id}"]`,
    );
    if (button)
      props.hidden =
        ui.tabs[
          button.closest("[data-tabs]")?.getAttribute("data-group") || "0"
        ] !== element.id;
  }
  if (tag === "details" && element.hasAttribute("data-responsive-filters"))
    delete props.open;
  if (element.matches(".employee-preview-button")) {
    delete props["aria-controls"];
    props["aria-haspopup"] = "dialog";
    props.onClick = () => {
      const row = element.closest<HTMLTableRowElement>("tr")!;
      ui.open("employeePreview", {
        employeeId: row.dataset.employeeId,
        name: row.querySelector(".employee-name")?.textContent || "",
        contact: row.cells[0].querySelector(".small")?.textContent || "",
        position: row.cells[1].textContent || "",
        department: row.cells[2].textContent || "",
        load: row.cells[3].textContent || "",
      });
    };
  }
  if (element.dataset.bsTarget)
    props.onClick = () =>
      ui.open(element.dataset.bsTarget!.replace("#", ""), element.dataset);
  if (element.matches(".task-status-select")) {
    const record = element.closest("[data-task-record]")!;
    props.onChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
      const next = event.target.value;
      event.target.value = (element as HTMLSelectElement).value;
      void ui.status(record, next);
    };
  }
  if (tag === "option" && element.closest(".task-status-select")) {
    const current =
      element.closest("[data-task-record]")!.getAttribute("data-status") || "";
    props.disabled =
      (element as HTMLOptionElement).value !== current &&
      !(transitions[current] || []).includes(
        (element as HTMLOptionElement).value,
      );
  }
  if (element.matches(".task-card")) {
    props.hidden = !visibleCard(element);
    props.draggable = !element.querySelector("select:disabled");
    props.onDragStart = (event: React.DragEvent) => {
      if ((event.target as Element).closest("select,button")) {
        event.preventDefault();
        return;
      }
      ui.setDrag(element);
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", element.dataset.taskId || "");
    };
    props.onDragEnd = () => ui.setDrag(null);
  }
  if (element.matches(".kanban-column")) {
    props.onDragOver = (event: React.DragEvent) => {
      if (ui.drag) {
        event.preventDefault();
        event.dataTransfer.dropEffect = "move";
      }
    };
    props.onDrop = (event: React.DragEvent) => {
      event.preventDefault();
      if (ui.drag) void ui.status(ui.drag, element.dataset.status!);
      ui.setDrag(null);
    };
  }
  let children: ReactNode =
    tag === "input" || tag === "textarea" || ["br", "hr", "img"].includes(tag)
      ? undefined
      : [...node.childNodes].map((child, index) => render(child, ui, index));
  if (tag === "a") {
    if (element.closest(".task-card")) props.draggable = false;
    const href = element.getAttribute("href");
    if (href?.startsWith("/") && !href.startsWith("//")) {
      if (href.startsWith("/reports/export") || href.endsWith(".csv"))
        props.href = (import.meta.env.DEV ? "/backend" : "") + href;
      else {
        delete props.href;
        return createElement(Link, { ...props, to: href }, children);
      }
    }
    if (!href) props.role = "presentation";
  }
  if (element.matches(".btn-close")) children = <X size={20} />;
  if (element.matches(".kanban-column-header .badge")) {
    children = [
      ...element
        .closest(".kanban-column")!
        .querySelectorAll<HTMLElement>(".task-card"),
    ].filter(visibleCard).length;
  }
  if (element.matches(".kanban-empty")) {
    const column = element.closest(".kanban-column");
    if (column?.querySelector(".task-card"))
      props.className = "kanban-empty only-if-empty";
  }
  return createElement(tag, props, children);
}
