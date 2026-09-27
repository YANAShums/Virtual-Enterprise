# Этап 8. Импорт обучающего датасета

Этап реализует загрузку исторических CSV-данных для последующего обучения модели текучести.

## Реализовано

- административный экран `/admin/ml` в действующей дизайн-системе FlowStaff AI;
- скачивание пустого CSV-шаблона с утверждёнными заголовками;
- внешний endpoint `POST /api/v1/ml/import`, доступный только роли `ADMIN`;
- внутренний защищённый endpoint ML-сервиса `POST /internal/v1/datasets/import`;
- проверка UTF-8, расширения, размера файла, состава столбцов и типов значений;
- отдельные ошибки для каждой некорректной строки;
- проверка `attrition_within_90d` на значения 0 или 1, запрет будущих дат и дубликатов `employee_snapshot_id`;
- сохранение корректных строк с источником `IMPORT` и версией схемы `attrition-features-v1`;
- хранение итогов загрузки в `ml_import_batches`;
- запись факта импорта и итоговых счётчиков в аудит без содержимого CSV.

Пустые числовые признаки сохраняются как `NULL`. Их заполнение медианой выполняется в конвейере обучения этапа 9. Обучение модели на экране обозначено как будущая возможность, без вымышленных метрик.

## Формат CSV

Обязательные заголовки:

```text
employee_snapshot_id,employee_id,snapshot_date,tenure_months,employment_rate,department_code,position_code,active_projects_count,active_tasks_count,overdue_tasks_30d,completed_tasks_90d,avg_task_delay_days_90d,avg_workload_30d,avg_workload_90d,overload_days_30d,overload_days_90d,absence_days_90d,task_reassignments_90d,project_changes_180d,attrition_within_90d
```

Файл может использовать запятую или точку с запятой. Технические идентификаторы не требуют хранения персональных данных сотрудника.

## Проверка

- `MlDatasetImportControllerTest`: доступ ADMIN, обработка multipart и запрет для EMPLOYEE;
- `tests/test_import.py`: схема, построчные ошибки, дубликаты, будущие даты и допустимые пропуски числовых признаков.
