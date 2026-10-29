import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ArrowUpRight, CalendarBlank, CheckSquare, FolderSimple, Sparkle, Timer, Users } from "@phosphor-icons/react";
import { fetchPage } from "./api";
import type { Page } from "./api";
import { Empty, Panel, StatusBadge, textOf, toneFor } from "./ui";

function LoadChart({ page, loading, error }: { page?: Page; loading: boolean; error: boolean }) {
  const [selected, setSelected] = useState<number | null>(null);
  const days = new Map<string, number[]>();
  page?.document.querySelectorAll<HTMLElement>(".day-cell").forEach(cell => {
    if (cell.dataset.status === "UNAVAILABLE") return;
    const percent = Number(cell.dataset.percent);
    if (cell.dataset.date && Number.isFinite(percent)) days.set(cell.dataset.date, [...(days.get(cell.dataset.date) || []), percent]);
  });
  const values = [...days].sort(([a], [b]) => a.localeCompare(b)).map(([date, v]) => ({ date, value: v.reduce((a, b) => a + b, 0) / v.length }));
  if (loading) return <Empty title="Загружаем плановые данные…" />;
  if (error || page?.document.querySelector(".alert-danger")) return <Empty title="Данные загрузки недоступны">Проверьте доступность сервиса в разделе «Загрузка».</Empty>;
  if (!values.length) return <Empty title="За период нет данных">Настройте графики и назначения сотрудников.</Empty>;
  const top = Math.max(100, Math.ceil(Math.max(...values.map(v => v.value)) / 25) * 25);
  const point = (i: number) => ({ x: 45 + i * 660 / Math.max(1, values.length - 1), y: 160 - values[i].value / top * 130 });
  const active = selected ?? 0;
  const label = (date: string) => new Date(date + "T12:00:00").toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
  return <div className="load-chart"><div className="chart-legend"><span><i className="legend-plan" />Плановая загрузка</span><span><i className="legend-current" />Выбранный день</span></div><svg viewBox="0 0 740 200" role="group" aria-label="Средняя плановая загрузка сотрудников по дням"><title>Плановая загрузка команды, проценты</title>{[0, 25, 50, 75, 100].map(v => <g key={v}><line x1="45" x2="705" y1={160 - v / 100 * 130} y2={160 - v / 100 * 130} className="chart-grid-line" /><text x="4" y={164 - v / 100 * 130}>{Math.round(v / 100 * top)}%</text></g>)}<polyline className="plan-line" points={values.map((_, i) => `${point(i).x},${point(i).y}`).join(" ")} /><line className="selected-line" x1={point(active).x} x2={point(active).x} y1="20" y2="160" />{values.map((v, i) => <g key={v.date}><g tabIndex={0} role="button" aria-pressed={active === i} aria-label={`${label(v.date)}: ${v.value.toFixed(1)}%`} onFocus={() => setSelected(i)} onMouseEnter={() => setSelected(i)} onClick={() => setSelected(i)} onKeyDown={event => { const next = event.key === "ArrowRight" ? Math.min(values.length - 1, i + 1) : event.key === "ArrowLeft" ? Math.max(0, i - 1) : null; if (next !== null) { event.preventDefault(); event.currentTarget.ownerSVGElement?.querySelectorAll<SVGGElement>('g[role="button"]')[next]?.focus(); setSelected(next); } }}><circle cx={point(i).x} cy={point(i).y} r="14" className="chart-hit-target" /><circle cx={point(i).x} cy={point(i).y} r={active === i ? 6 : 4} className={active === i ? "current-point" : "plan-point"} aria-hidden="true" /><title>{label(v.date)} · {v.value.toFixed(1)}%</title></g><text x={point(i).x} y="187" textAnchor="middle">{label(v.date)}</text></g>)}</svg><div className="chart-readout" aria-live="polite"><span>{label(values[active].date)}</span><strong className="data">{values[active].value.toFixed(1)}%</strong><span>средняя плановая загрузка</span></div></div>;
}

export function Overview({ page }: { page: Page }) {
  const doc = page.document;
  const stats = [...doc.querySelectorAll<HTMLAnchorElement>(".dashboard-stat")];
  const workload = useQuery({ queryKey: ["page", "/workload", "overview"], queryFn: ({ signal }) => fetchPage("/workload", undefined, signal) });
  const projects = useQuery({ queryKey: ["page", "/projects", "overview"], queryFn: ({ signal }) => fetchPage("/projects", undefined, signal) });
  const tasks = useQuery({ queryKey: ["page", "/tasks?overdue=true", "overview"], queryFn: ({ signal }) => fetchPage("/tasks?overdue=true", undefined, signal) });
  const projectRows = [...(projects.data?.document.querySelectorAll<HTMLTableRowElement>(".project-item") || [])];
  const taskRows = [...(tasks.data?.document.querySelectorAll<HTMLTableRowElement>("tbody tr") || [])].filter(row => row.querySelector(".task-title-link"));
  const risks = [...doc.querySelectorAll<HTMLAnchorElement>("a.insight-message")];
  const canRisk = !!doc.querySelector(".risk-stat");
  const employeeCount = textOf([...doc.querySelectorAll(".status-card dl > div")][2], "dd") || "—";
  const metrics = [
    { label: "Сотрудники", value: employeeCount, href: "/employees", note: "Состав организации", Icon: Users },
    { label: "Активные задачи", value: textOf(stats[0], "strong"), href: "/tasks", note: "Текущая рабочая очередь", Icon: CheckSquare },
    { label: "Проекты", value: textOf(stats[3], "strong"), href: "/projects", note: "Активные проекты", Icon: FolderSimple },
    { label: "Средняя загрузка", value: textOf(stats[1], "strong"), href: "/workload", note: "На ближайшие 7 дней", Icon: Timer },
  ];
  const deadlines = [...doc.querySelectorAll<HTMLAnchorElement>(".compact-task")];
  return <div className="executive-overview" aria-busy={workload.isPending || projects.isPending || tasks.isPending}><header className="page-heading"><div><h1>Обзор организации</h1><p className="page-subtitle">Ключевые показатели, текущая работа и кадровая аналитика вашей команды</p></div>{doc.querySelector(".rail-create") && <Link className="btn btn-primary" to="/kanban?create=1">Новая задача <ArrowUpRight size={17} aria-hidden /></Link>}</header>
    <div className="executive-metrics">{metrics.map(({ label, value, href, note, Icon }) => <Link className="executive-metric" key={href} to={href}><span className="metric-symbol"><Icon size={23} aria-hidden /></span><div><span className="metric-label">{label}<ArrowUpRight size={12} aria-hidden /></span><strong className="data">{value || "—"}</strong><small>{note}</small></div></Link>)}</div>
    <div className="overview-chart-grid"><Panel title="Плановая загрузка команды" note="Расчёт на ближайшие 7 дней по графикам и назначениям" action={{ href: "/workload", label: "Подробнее" }}><LoadChart page={workload.data} loading={workload.isPending} error={workload.isError} /></Panel><section className="intelligence-panel"><div className="intelligence-label"><Sparkle size={21} aria-hidden /><h2>AI-инсайты</h2><span>ML · 90 дней</span></div>{canRisk ? risks.length ? <><h3>Обратите внимание на кадровый риск</h3><p>Сохранённые прогнозы помогают определить, с кем стоит обсудить загрузку и условия работы.</p><div className="insight-person"><span className="avatar">{textOf(risks[0], "strong").slice(0, 2)}</span><div><strong>{textOf(risks[0], "strong")}</strong><span className="data">{textOf(risks[0], "p")}</span></div></div><Link className="btn btn-primary" to={risks[0].getAttribute("href")!}>Разобрать прогноз <ArrowUpRight size={16} aria-hidden /></Link></> : <><h3>Прогнозов пока нет</h3><p>После расчёта здесь появятся сотрудники с повышенным риском и переход к факторам модели.</p><Link className="btn btn-ai" to="/attrition">Открыть прогнозы <ArrowUpRight size={16} aria-hidden /></Link></> : <><h3>Баланс команды начинается с загрузки</h3><p>Кадровая аналитика доступна уполномоченным руководителям. Используйте плановые данные для распределения задач.</p><Link className="btn btn-secondary" to="/workload">Посмотреть загрузку</Link></>}</section></div>
    <div className={`overview-detail-grid ${canRisk ? "" : "without-risk"}`}><Panel title="Активные проекты" action={{ href: "/projects", label: "Все проекты" }}>{projects.isError ? <Empty title="Проекты не удалось загрузить" /> : projectRows.length ? <div className="compact-table-wrap"><table className="compact-table"><thead><tr><th>Проект</th><th>Прогресс</th><th>Срок</th></tr></thead><tbody>{projectRows.slice(0, 5).map(row => { const link = row.querySelector<HTMLAnchorElement>(".project-title a")!; return <tr key={link.href}><td><Link to={link.getAttribute("href")!}>{link.textContent}</Link><small>{textOf(row, ".project-title .small")}</small></td><td><span className="data">{textOf(row, ".project-progress > span")}</span><div className="mini-progress"><span style={{ width: row.querySelector<HTMLElement>(".progress-bar")?.style.width || "0%" }} /></div></td><td className="data">{row.cells[3]?.textContent}</td></tr>; })}</tbody></table></div> : <Empty title={projects.isPending ? "Загружаем проекты…" : "Нет активных проектов"} />}</Panel>
    <Panel title="Задачи, требующие внимания" action={{ href: "/tasks?overdue=true", label: "Все задачи" }}>{tasks.isError ? <Empty title="Задачи не удалось загрузить" /> : taskRows.length ? <div className="attention-tasks">{taskRows.slice(0, 5).map(row => { const link = row.querySelector<HTMLAnchorElement>(".task-title-link")!; return <Link key={link.href} to={link.getAttribute("href")!}><span className="attention-dot" aria-hidden /><span>{link.textContent}<small className="data">{textOf(row, ".task-number")}</small></span><span className="deadline-danger data">{row.cells[3]?.textContent}<small>Просрочено</small></span></Link>; })}</div> : <Empty title={tasks.isPending ? "Загружаем задачи…" : "Просроченных задач нет"}>Текущая очередь доступна в разделе «Задачи».</Empty>}</Panel>
    {canRisk && <Panel title="Риск текучести сотрудников" action={{ href: "/attrition", label: "Все прогнозы" }}>{risks.length ? <div className="risk-preview-list">{risks.map(row => <Link key={row.href} to={row.getAttribute("href")!}><span className="avatar">{textOf(row, "strong").slice(0, 2)}</span><div><strong>{textOf(row, "strong")}</strong><StatusBadge tone={toneFor(textOf(row, "p"))}>{textOf(row, "p")}</StatusBadge></div><ArrowUpRight size={16} aria-hidden /></Link>)}</div> : <Empty title="Нет сохранённых прогнозов">Рассчитайте риск в кадровой аналитике. Значения появятся после успешного ответа модели.</Empty>}</Panel>}</div>
    <Panel title="Ближайшие сроки" note="Текущие этапы задач команды" action={{ href: "/tasks", label: "Открыть очередь" }}><div className="deadline-stream">{deadlines.length ? deadlines.slice(0, 5).map(link => <Link key={link.href} to={link.getAttribute("href")!}><CalendarBlank size={19} aria-hidden /><span><strong>{textOf(link, "span")}</strong><small>Срок <time className="data">{textOf(link, "small")}</time></small></span></Link>) : <Empty title="Ближайших задач пока нет" />}</div></Panel>
  </div>;
}
