import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight, ArrowRight, UserCircle } from "@phosphor-icons/react";
import type { Page } from "./api";
import { Render } from "./renderer";
import { textOf } from "./ui";

export function People({ page, refresh }: { page: Page; refresh: () => unknown }) {
  const rows = [...page.document.querySelectorAll<HTMLTableRowElement>(".employee-row")];
  const [selected, setSelected] = useState(rows[0]?.dataset.employeeId || "");
  const row = rows.find(r => r.dataset.employeeId === selected);
  const main = page.document.querySelector("main")!;
  const team = main.querySelector(".team-layout")!;
  const table = team.querySelector("table")!;
  const children = [...main.childNodes];
  return <div className="people-content">{children.filter(n => n !== team).map((node, i) => <Render key={i} node={node} page={page} refresh={refresh} />)}<div className="people-master-detail"><section aria-label="Список сотрудников" className="table-responsive"><table className="table employee-table"><Render node={table.querySelector("thead")!} page={page} refresh={refresh} /><tbody>{[...table.querySelectorAll("tbody > tr")].map((record, i) => {
    const tr = record as HTMLTableRowElement;
    if (!tr.dataset.employeeId) return <Render key={i} node={tr} page={page} refresh={refresh} />;
    return <tr key={tr.dataset.employeeId} className={`employee-row ${selected === tr.dataset.employeeId ? "is-selected" : ""}`} data-employee-id={tr.dataset.employeeId}>{[...tr.cells].map((cell, j) => j === tr.cells.length - 1 ? <td key={j} className="text-end"><div className="d-flex gap-1">{cell.querySelector("a") && <Render node={cell.querySelector("a")!} page={page} refresh={refresh} />}<button className="icon-button" aria-label={`Быстрый просмотр: ${textOf(tr, ".employee-name")}`} aria-pressed={selected === tr.dataset.employeeId} onClick={() => setSelected(tr.dataset.employeeId!)}><ArrowRight size={18} aria-hidden /></button></div></td> : <Render key={j} node={cell} page={page} refresh={refresh} />)}</tr>;
  })}</tbody></table></section><aside className="person-inspector" aria-label="Выбранный сотрудник" aria-live="polite">{row ? <><span className="inspector-label">Карточка сотрудника</span><span className="avatar avatar-lg">{textOf(row, ".avatar")}</span><h2>{textOf(row, ".employee-name")}</h2><p>{row.cells[1]?.querySelector("span")?.textContent}</p><dl><dt>Подразделение</dt><dd>{row.cells[2]?.textContent}</dd><dt>Контакт</dt><dd>{textOf(row, "td:first-child .small")}</dd><dt>Загрузка</dt><dd className="data">{row.cells[3]?.textContent}</dd><dt>Статус</dt><dd>{row.cells[4]?.textContent}</dd></dl><Link className="btn btn-secondary" to={`/employees/${selected}`}>Открыть профиль <ArrowUpRight size={17} aria-hidden /></Link><Link className="text-link" to={`/employees/${selected}#employeeTasks`}>Задачи сотрудника <ArrowRight size={16} aria-hidden /></Link></> : <><UserCircle size={36} aria-hidden /><h2>Сотрудник не выбран</h2><p>Выберите запись в списке.</p></>}</aside></div></div>;
}
