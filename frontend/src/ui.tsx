import type { ReactNode, ButtonHTMLAttributes } from "react";
import { Link } from "react-router-dom";
import { ArrowRight } from "@phosphor-icons/react";

export function Button({ variant = "primary", className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "ai" | "danger" | "ghost" }) {
  return <button className={`btn btn-${variant} ${className}`} {...props} />;
}
export function Panel({ title, note, action, children, className = "" }: { title: string; note?: string; action?: { href: string; label: string }; children: ReactNode; className?: string }) {
  return <section className={`product-panel ${className}`}><header className="panel-header"><div><h2>{title}</h2>{note && <p>{note}</p>}</div>{action && <Link className="text-link" to={action.href}>{action.label}<ArrowRight size={15} aria-hidden /></Link>}</header>{children}</section>;
}
export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="product-empty"><strong>{title}</strong>{children && <p>{children}</p>}</div>;
}
export function StatusBadge({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "success" | "warning" | "danger" | "ai" | "info" }) {
  return <span className={`status-chip tone-${tone}`}><span aria-hidden />{children}</span>;
}
export function toneFor(value: string): "neutral" | "success" | "warning" | "danger" | "info" {
  if (/CRITICAL|HIGH|Просроч|Критич|Высок|Отмен/i.test(value)) return "danger";
  if (/MEDIUM|WARNING|Средн|Повыш|Риск/i.test(value)) return "warning";
  if (/LOW|NORMAL|DONE|ACTIVE|В работе|Заверш|Норма|Низк|Актив/i.test(value)) return "success";
  if (/REVIEW|Проверк/i.test(value)) return "info";
  return "neutral";
}
export const textOf = (root: ParentNode | undefined | null, selector: string) => root?.querySelector(selector)?.textContent?.trim() || "";
