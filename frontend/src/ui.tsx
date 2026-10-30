import type { ReactNode, ButtonHTMLAttributes, ComponentType, InputHTMLAttributes } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Tray, Sparkle, Info, CheckCircle, WarningCircle, XCircle } from "@phosphor-icons/react";

export type Tone = "neutral" | "success" | "warning" | "danger" | "ai" | "info";

export function Button({ variant = "primary", className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "ai" | "danger" | "ghost" }) {
  return <button className={`btn btn-${variant} ${className}`} {...props} />;
}
export function IconButton({ label, className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return <button type="button" aria-label={label} className={`icon-button ${className}`} {...props} />;
}
export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`form-control ${className}`} {...props} />;
}
export function Panel({ title, note, action, children, className = "" }: { title: string; note?: string; action?: { href: string; label: string }; children: ReactNode; className?: string }) {
  return <section className={`product-panel ${className}`}><header className="panel-header"><div><h2>{title}</h2>{note && <p>{note}</p>}</div>{action && <Link className="text-link" to={action.href}>{action.label}<ArrowRight size={15} aria-hidden /></Link>}</header>{children}</section>;
}
export function Empty({ title, children, action, tone = "info" }: { title: string; children?: ReactNode; action?: { href: string; label: string }; tone?: Tone }) {
  return <div className={`product-empty empty-tone-${tone}`}><span className="empty-symbol"><Tray size={24} aria-hidden /></span><strong>{title}</strong>{children && <p>{children}</p>}{action && <Link className="btn btn-secondary" to={action.href}>{action.label}<ArrowRight size={16} aria-hidden /></Link>}</div>;
}
export function StatusBadge({ children, tone = "neutral" }: { children: ReactNode; tone?: Tone }) {
  return <span className={`status-chip tone-${tone}`}><span aria-hidden />{children}</span>;
}
export function Alert({ children, tone = "info" }: { children: ReactNode; tone?: Tone }) {
  const Icon = { neutral: Info, info: Info, success: CheckCircle, warning: WarningCircle, danger: XCircle, ai: Sparkle }[tone];
  return <div className={`alert ui-alert alert-${tone}`} role={tone === "danger" ? "alert" : "status"}><Icon size={20} aria-hidden /><div>{children}</div></div>;
}
export function Skeleton({ label = "Загружаем данные…", compact = false }: { label?: string; compact?: boolean }) {
  return <div className={`skeleton-state ${compact ? "is-compact" : ""}`} role="status" aria-busy="true"><span className="sr-only">{label}</span><div className="skeleton-line skeleton-title" aria-hidden /><div className="skeleton-metrics" aria-hidden>{[0, 1, 2, 3].map(n => <div className="skeleton-block" key={n} />)}</div><div className="skeleton-block skeleton-body" aria-hidden /></div>;
}
export function KPI({ label, value, href, note, Icon, accent = "info" }: { label: string; value: string; href: string; note: string; Icon: ComponentType<{ size?: number; "aria-hidden"?: boolean }>; accent?: "people" | "info" | "neutral" | "current" }) {
  return <Link className={`executive-metric metric-${accent}`} to={href}><span className="metric-symbol"><Icon size={23} aria-hidden /></span><div><span className="metric-label">{label}<ArrowRight size={12} aria-hidden /></span><strong className="data">{value || "—"}</strong><small>{note}</small></div></Link>;
}
export function AIInsight({ title, children, meta, intelligence = true }: { title: string; children: ReactNode; meta?: string; intelligence?: boolean }) {
  const Icon = intelligence ? Sparkle : Info;
  return <section className={`intelligence-panel ${intelligence ? "" : "information-panel"}`}><div className="intelligence-label"><Icon size={21} aria-hidden /><h2>{title}</h2>{meta && <span>{meta}</span>}</div>{children}</section>;
}
export function toneFor(value: string): "neutral" | "success" | "warning" | "danger" | "info" {
  if (/CRITICAL|HIGH|Просроч|Критич|Высок|Отмен/i.test(value)) return "danger";
  if (/MEDIUM|WARNING|Средн|Повыш|Риск/i.test(value)) return "warning";
  if (/LOW|NORMAL|DONE|ACTIVE|В работе|Заверш|Норма|Низк|Актив/i.test(value)) return "success";
  if (/REVIEW|PLANNED|Проверк|Запланир/i.test(value)) return "info";
  return "neutral";
}
export const textOf = (root: ParentNode | undefined | null, selector: string) => root?.querySelector(selector)?.textContent?.trim() || "";
