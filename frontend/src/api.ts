export interface Page {
  document: Document;
  url: string;
  title: string;
  login: boolean;
  csrf: string;
  csrfHeader: string;
}

let current: Page | undefined;
export const backend = (path: string) =>
  (import.meta.env.DEV ? "/backend" : "") + path;
const localUrl = (url: string) => {
  const parsed = new URL(url, location.origin);
  return (
    parsed.pathname.replace(/^\/backend(?=\/)/, "") +
    parsed.search +
    parsed.hash
  );
};

/** MVC is a read/form contract, never inserted as executable HTML. */
export async function fetchPage(
  url: string,
  init?: RequestInit,
  signal?: AbortSignal,
): Promise<Page> {
  const response = await fetch(backend(url.split("#")[0]), {
    ...init,
    signal,
    credentials: "same-origin",
    headers: { Accept: "text/html", "X-FlowStaff-View": "1", ...init?.headers },
  });
  const html = await response.text();
  if (!response.ok) {
    throw new Error(
      response.status === 403
        ? "Доступ к этому действию запрещён. Проверьте права или войдите заново."
        : response.status === 404
          ? "Страница не найдена."
          : "Не удалось загрузить данные. Повторите запрос.",
    );
  }
  const document = new DOMParser().parseFromString(html, "text/html");
  const resolved = localUrl(response.url);
  if (!document.querySelector("main, .login-stage"))
    throw new Error("Сервер вернул неожиданный ответ. Повторите запрос.");
  const page: Page = {
    document,
    url: resolved,
    title: document.title.split(" — ")[0],
    login: new URL(resolved, location.origin).pathname === "/login",
    csrf:
      document.querySelector<HTMLMetaElement>('meta[name="csrf-token"]')
        ?.content ?? "",
    csrfHeader:
      document.querySelector<HTMLMetaElement>('meta[name="csrf-header"]')
        ?.content ?? "X-XSRF-TOKEN",
  };
  current = page;
  return page;
}

export async function api<T = unknown>(
  url: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body) headers.set("Content-Type", "application/json");
  if (current?.csrf) headers.set(current.csrfHeader, current.csrf);
  const response = await fetch(backend(url), {
    ...init,
    headers,
    credentials: "same-origin",
  });
  if (response.redirected && new URL(response.url).pathname.endsWith("/login"))
    throw new Error("Сессия завершилась. Войдите заново.");
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(
      body.message ||
        (response.status === 409
          ? "Данные уже изменены. Обновите страницу."
          : "Не удалось выполнить действие."),
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

export async function submitForm(
  form: HTMLFormElement,
  submitter: HTMLElement | null,
  page: Page,
): Promise<{ url: string; page?: Page; download?: boolean }> {
  const data = new FormData(form, submitter as HTMLButtonElement | null);
  const action = new URL(
    submitter?.getAttribute("formaction") ||
      form.getAttribute("action") ||
      page.url,
    location.origin,
  );
  if (action.origin !== location.origin)
    throw new Error("Недопустимый адрес формы.");
  if (form.method.toLowerCase() === "get") {
    const params = new URLSearchParams();
    data.forEach((value, key) => {
      if (typeof value === "string") params.append(key, value);
    });
    return {
      url: action.pathname + "?" + params.toString(),
      download: action.pathname === "/reports/export",
    };
  }
  // Preserve Spring's masked token, version and checkbox markers from the contract.
  if (!data.has("_csrf")) data.set("_csrf", page.csrf);
  const body =
    form.enctype === "multipart/form-data"
      ? data
      : new URLSearchParams(data as unknown as Record<string, string>);
  const result = await fetchPage(action.pathname + action.search, {
    method: "POST",
    body,
  });
  return { url: result.url, page: result };
}

export async function downloadFile(url: string) {
  const response = await fetch(backend(url), { credentials: "same-origin" });
  if (
    !response.ok ||
    response.headers.get("content-type")?.includes("text/html")
  )
    throw new Error(
      "Не удалось скачать отчёт. Проверьте доступ и повторите запрос.",
    );
  const filename =
    response.headers
      .get("content-disposition")
      ?.match(/filename=\"?([^\";]+)/)?.[1] || "flowstaff-report";
  const objectUrl = URL.createObjectURL(await response.blob());
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = filename;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}
