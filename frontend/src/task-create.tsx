import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowRight, Check } from "@phosphor-icons/react";
import { api } from "./api";
import type { Page } from "./api";
import { loadLabels } from "./renderer";

interface Candidate {
  employeeId: number;
  fullName: string;
  score: number;
  currentLoadPercent: number;
  projectedLoadPercent: number;
  status: string;
  available: boolean;
  explanations: string[];
  warnings: string[];
}
interface Preview {
  calculationRequestId: string;
  candidates: Candidate[];
}
const options = (page: Page, selector: string) =>
  [...page.document.querySelectorAll<HTMLOptionElement>(selector)].map(
    (option) => ({ value: option.value, label: option.textContent || "" }),
  );
function localDate(offset = 0) {
  const day = new Date();
  day.setDate(day.getDate() + offset);
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
}

export function TaskCreate({
  page,
  close,
  refresh,
}: {
  page: Page;
  close: () => void;
  refresh: () => unknown;
}) {
  const navigate = useNavigate();
  const form = useRef<HTMLFormElement>(null);
  const generation = useRef(0);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [selected, setSelected] = useState<Candidate | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const projects = options(page, "#taskProject option");
  const priorities = options(page, "#taskPriority option");
  const skills = [
    ...page.document.querySelectorAll<HTMLInputElement>(".task-skill"),
  ].map((input) => ({
    id: input.value,
    name: input.parentElement?.textContent || "",
  }));
  const selectedProject =
    page.document.querySelector<HTMLSelectElement>("#taskProject")?.value ||
    projects[0]?.value;
  const invalidate = () => {
    generation.current++;
    setPreview(null);
    setSelected(null);
    setConfirmed(false);
    setLoading(false);
    setError("");
  };
  const parameters = () => {
    const data = new FormData(form.current!);
    return {
      projectId: Number(data.get("projectId")),
      plannedStartDate: String(data.get("plannedStartDate")),
      plannedEndDate: String(data.get("plannedEndDate")),
      plannedHours: Number(data.get("plannedHours")),
      requiredSkillIds: data.getAll("requiredSkillIds").map(Number),
    };
  };
  async function calculate() {
    const data = parameters();
    if (
      !data.projectId ||
      !data.plannedStartDate ||
      !data.plannedEndDate ||
      !data.plannedHours ||
      data.plannedHours < 0.25
    ) {
      setError("Выберите проект, период и трудоёмкость.");
      return;
    }
    if (data.plannedEndDate < data.plannedStartDate) {
      setError("Окончание должно быть не раньше начала.");
      return;
    }
    const request = ++generation.current;
    setLoading(true);
    setError("");
    setPreview(null);
    setSelected(null);
    setConfirmed(false);
    try {
      const result = await api<Preview>("/api/v1/tasks/workload-preview", {
        method: "POST",
        body: JSON.stringify({ ...data, candidateIds: [] }),
      });
      if (request === generation.current) setPreview(result);
    } catch (error) {
      if (request === generation.current) setError((error as Error).message);
    } finally {
      if (request === generation.current) setLoading(false);
    }
  }
  async function create(event: React.FormEvent) {
    event.preventDefault();
    if (
      saving ||
      !form.current?.reportValidity() ||
      !selected ||
      !preview ||
      (selected.status === "CRITICAL" && !confirmed)
    )
      return;
    setSaving(true);
    setError("");
    const data = new FormData(form.current);
    try {
      const created = await api<{ id: number }>("/api/v1/tasks", {
        method: "POST",
        body: JSON.stringify({
          ...parameters(),
          title: data.get("title"),
          description: data.get("description"),
          priorityCode: data.get("priorityCode"),
          assigneeEmployeeId: selected.employeeId,
          statusCode: "PLANNED",
          calculationRequestId: preview.calculationRequestId,
          overloadConfirmed: selected.status === "CRITICAL" && confirmed,
          overloadReason:
            selected.status === "CRITICAL" ? data.get("overloadReason") : null,
        }),
      });
      await refresh();
      close();
      navigate(`/tasks/${created.id}`);
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setSaving(false);
    }
  }
  return (
    <form
      ref={form}
      className="task-create-form"
      data-react-form
      onSubmit={create}
    >
      {error && (
        <div className="alert alert-danger" role="alert">
          {error}
        </div>
      )}
      <label htmlFor="createTitle">Название</label>
      <input
        id="createTitle"
        name="title"
        placeholder="Что нужно сделать?"
        minLength={3}
        maxLength={255}
        required
        autoFocus
      />
      <label htmlFor="createDescription">
        Описание <span className="optional">необязательно</span>
      </label>
      <textarea
        id="createDescription"
        name="description"
        rows={3}
        placeholder="Контекст и ожидаемый результат"
      />
      <label htmlFor="createProject">Проект</label>
      <select
        id="createProject"
        name="projectId"
        defaultValue={selectedProject}
        onChange={invalidate}
        required
      >
        {projects.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <div className="form-grid">
        <div>
          <label htmlFor="createStart">Начало</label>
          <input
            id="createStart"
            name="plannedStartDate"
            type="date"
            defaultValue={localDate()}
            onChange={invalidate}
            required
          />
        </div>
        <div>
          <label htmlFor="createEnd">Завершение</label>
          <input
            id="createEnd"
            name="plannedEndDate"
            type="date"
            defaultValue={localDate(5)}
            onChange={invalidate}
            required
          />
        </div>
        <div>
          <label htmlFor="createHours">Трудоёмкость, ч</label>
          <input
            id="createHours"
            name="plannedHours"
            type="number"
            min={0.25}
            step={0.25}
            defaultValue={8}
            onChange={invalidate}
            required
          />
        </div>
        <div>
          <label htmlFor="createPriority">Приоритет</label>
          <select id="createPriority" name="priorityCode" defaultValue="MEDIUM">
            {priorities.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>
      {!!skills.length && (
        <details className="skills-details">
          <summary>
            Требуемые навыки <span className="optional">необязательно</span>
          </summary>
          <div className="skill-choice-list">
            {skills.map((skill) => (
              <label className="skill-choice" key={skill.id}>
                <input
                  type="checkbox"
                  name="requiredSkillIds"
                  value={skill.id}
                  onChange={invalidate}
                />
                {skill.name}
              </label>
            ))}
          </div>
        </details>
      )}
      <div className="assignee-heading">
        <h3>Исполнитель</h3>
        <button
          className="btn btn-secondary"
          type="button"
          disabled={loading || saving}
          onClick={calculate}
        >
          {loading
            ? "Считаем…"
            : preview
              ? "Пересчитать"
              : "Рассчитать загрузку"}
        </button>
      </div>
      <div className="candidate-results" aria-live="polite">
        {!preview && !loading && (
          <p className="muted">
            Рассчитайте загрузку, чтобы выбрать доступного сотрудника.
          </p>
        )}
        {preview?.candidates.length === 0 && (
          <p className="muted">
            Доступных кандидатов нет. Измените период или трудоёмкость.
          </p>
        )}
        {preview?.candidates.map((candidate) => (
          <label
            className={`candidate ${selected?.employeeId === candidate.employeeId ? "selected" : ""} ${!candidate.available ? "unavailable" : ""}`}
            key={candidate.employeeId}
          >
            <input
              type="radio"
              name="assignee"
              value={candidate.employeeId}
              disabled={!candidate.available || saving}
              checked={selected?.employeeId === candidate.employeeId}
              onChange={() => {
                setSelected(candidate);
                setConfirmed(false);
              }}
            />
            <span>
              <strong>{candidate.fullName}</strong>
              <small>
                {candidate.currentLoadPercent}% →{" "}
                {candidate.projectedLoadPercent}% ·{" "}
                {loadLabels[candidate.status] || candidate.status}
              </small>
              <details>
                <summary>Почему этот сотрудник</summary>
                <ul>
                  {candidate.explanations?.map((reason, index) => (
                    <li key={index}>{reason}</li>
                  ))}
                </ul>
                {!!candidate.warnings?.length && (
                  <p className="text-danger">
                    {candidate.warnings.join(" · ")}
                  </p>
                )}
                <small>Рейтинг: {candidate.score}</small>
              </details>
            </span>
            {selected?.employeeId === candidate.employeeId && (
              <Check size={19} />
            )}
          </label>
        ))}
      </div>
      {selected?.status === "CRITICAL" && (
        <div className="alert alert-warning">
          <p>
            Загрузка после назначения — {selected.projectedLoadPercent}%. Нужны
            подтверждение и причина.
          </p>
          <label className="filter-check">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(event) => setConfirmed(event.target.checked)}
            />
            Подтверждаю критическую перегрузку
          </label>
          <label htmlFor="overloadReason">Причина</label>
          <select id="overloadReason" name="overloadReason">
            {options(page, "#overloadReason option").map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      )}
      <div className="drawer-footer">
        <button type="button" className="btn" onClick={close} disabled={saving}>
          Отмена
        </button>
        <button
          className="btn btn-primary"
          disabled={
            saving ||
            loading ||
            !selected ||
            (selected.status === "CRITICAL" && !confirmed)
          }
        >
          {saving ? "Создаём…" : "Создать задачу"}
          <ArrowRight size={18} />
        </button>
      </div>
    </form>
  );
}
