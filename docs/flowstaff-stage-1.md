# FlowStaff AI — этап 1

Источник: FlowStaff_AI_Техническое_задание_v2.1.docx, разделы 7, 12 и 15.

Статус: **этап 1 принят 16.09.2026** в пределах инфраструктурных требований.
Это не финальная эксплуатационная приёмка всего продукта.

## Результаты приёмки v2.1

| Проверка | Результат |
|---|---|
| Java-тесты | 33 passed: 9 Workload, 24 Enterprise |
| Python-тесты | 16 passed; предупреждение об устаревшем alias AnyIO в Starlette |
| Сборка и запуск | MySQL, Enterprise, Workload, ML — healthy |
| Подготовка БД | db-bootstrap и ml-migrations — exit 0 |
| Пустой том | Инициализация БД и Liquibase успешна, включая отдельный TLS-стенд |
| Повторный запуск существующего тома | Checksums DATABASECHANGELOG и количества сотрудников/проектов/задач совпали |
| Внутренние API | Без service token — 401; ML с верным токеном — 200, database=UP |
| Недоступность ML | Новый вход и dashboard/tasks/kanban/employees/projects/workload/admin — 200; ML восстановлен |
| HTTPS | Вход и страницы с проверкой CA; preview нагрузки через Java-to-Workload TLS — 200 |
| ML TLS | Readiness по HTTPS с проверкой CA и service token — 200 |
| Cookie | JSESSIONID: Secure, HttpOnly, SameSite=Lax в prod |
| Изоляция | Dev публикует localhost:8080/3307; TLS-стенд — только localhost:8443 |
| Неверные настройки | Compose отвергает пустые production-секреты; unit-тесты проверяют запрет HTTP service URL и demo-секретов |
| Сертификаты | Недоверенный CA отклонён; проверка сертификатов не отключалась |

Проверки выполнены на Docker Engine 28.0.4 / Compose 2.34.0.
Ранее описанный сбой Docker и нехватка диска больше не блокируют запуск.
Исправлена ошибка повторной установки MySQL-драйвера Liquibase: драйвер устанавливается
при сборке образа, а не при каждом старте контейнера.

Повторить локальную приёмку: `python tools/verify-stage1.py` после запуска Compose.
TLS-профиль и ограничения: [DEPLOYMENT.md](DEPLOYMENT.md).
Логи текущего прогона находятся в игнорируемом `output/flowstaff/acceptance-*.log`.
HTTP-проверки выполнены от администратора; полная ролевая матрица относится к следующим этапам.


## Инвентаризация базы

Проект использует Java 21, Spring Boot 3.5.3, Maven, Thymeleaf, MySQL 8.4 и Liquibase.
Java-модули: enterprise-app, workload-service, common-contracts.
Существующие БД: enterprise_core и enterprise_workload. Hibernate работает в режиме validate.
Core содержит организацию, пользователей/роли, сотрудников/графики/отсутствия, проекты/команды, задачи/статусы/приоритеты, аудит и outbox.
Основные страницы: /login, /dashboard, /tasks, /tasks/{id}, /kanban, /employees, /projects, /workload, /admin.
Внешний API создания/смены статуса задач: /api/v1/tasks. Внутренний Workload API защищён X-Service-Token.
Оба Java-сервиса уже имеют /actuator/health.

## Объём этапа

Добавлен каркас attrition-ml-service на Python 3.12/FastAPI, HTTP health checks и отдельная БД enterprise_ml.
Существующая публичная точка входа — enterprise-app:8080. Порты Workload и ML не публикуются на хост.
MySQL:3307 доступен только с localhost для локальных инструментов разработчика.

Порядок старта ML: mysql healthy → db-bootstrap → ml-migrations → attrition-ml-service.
Enterprise не зависит от состояния ML и продолжает работать при его остановке.

## Миграции

mysql-init/02-create-ml-database.sh создаёт только БД и выдаёт права существующему приложению.
На новой установке его выполняет MySQL entrypoint, на существующем томе — идемпотентный db-bootstrap.
Данные существующего тома не удаляются. При смене DB_USERNAME/DB_PASSWORD в .env для старого тома требуется отдельная процедура ротации учётных данных.

attrition-ml-service/db/changelog.xml выполняется отдельным Liquibase job. Образ собирается из attrition-ml-service/db/Dockerfile с однократной установкой MySQL-драйвера.
Changeset ml-001-baseline фиксирует начальную версию схемы в DATABASECHANGELOG.
Предметных ML-таблиц на этапе 1 нет: snapshots, импорт и модели относятся к этапам 7–11.
Применённые Java changesets не редактируются. Новые изменения добавляются отдельными changesets.
Не применять docker compose down -v для обновления существующей установки.

## Проверки здоровья

| Сервис | Endpoint | Проверка |
|---|---|---|
| Enterprise | /actuator/health | HTTP 200, включая datasource |
| Workload | /actuator/health | HTTP 200, включая datasource |
| ML liveness | /health | процесс обрабатывает HTTP |
| ML readiness | /internal/v1/health | X-Service-Token, соединение MySQL и baseline migration |

ML readiness отвечает 401 без валидного токена, 503 при недоступной/неподготовленной БД.
Ответ UP содержит model=NOT_TRAINED: работа процесса не означает готовность обученной модели.
Ошибки соединения не раскрывают реквизиты БД.

## Запуск и тестирование

Из корня проекта:

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose exec workload-service bash /app/healthcheck.sh 8081
mvn -B test
```

ML health проверяется автоматически Docker Compose с токеном из окружения контейнера.

```powershell
python -m venv output/flowstaff/venv
output/flowstaff/venv/Scripts/python -m pip install -r attrition-ml-service/requirements-dev.txt
$env:PYTHONPATH = 'attrition-ml-service'
output/flowstaff/venv/Scripts/python -m pytest attrition-ml-service/tests -q
```

Для проверки идемпотентности повторно выполнить docker compose up -d; ml-migrations должен завершиться успешно без повторного применения baseline.
Для проверки отказоустойчивости остановить attrition-ml-service, открыть /login и /dashboard, затем запустить ML обратно.

## За пределами этапа

Обучение, прогнозы, feature builder, ML UI, новая палитра и полный CRUD реализуются следующими этапами.
Зафиксировано расхождение ТЗ: FR-REC-006 задаёт WARNING 100–120% и CRITICAL >120%, приложение А описывает визуальные диапазоны 80/90%.
Текущий расчёт нагрузки на этапе 1 не меняется.
В ТЗ названия Kanban также расходятся: функциональные статусы PLANNED/IN_PROGRESS/REVIEW/DONE/CANCELLED и пять визуальных колонок приложения А. Это следует согласовать при этапе 4.
