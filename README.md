# Виртуальное предприятие

Рабочий MVP веб-приложения для управления подразделениями, сотрудниками, проектами, Kanban-задачами и плановой загруженностью работников.

## Что реализовано

- авторизация через Spring Security и роли;
- демонстрационные пользователи и данные;
- список сотрудников с фильтром по подразделению;
- список проектов;
- Kanban-доска по проектам;
- создание задачи через Bootstrap Offcanvas;
- предварительный расчёт загруженности кандидатов;
- цветовые статусы `UNDERLOAD`, `NORMAL`, `WARNING`, `CRITICAL`, `UNAVAILABLE`;
- обязательное подтверждение критической перегрузки;
- повторная серверная проверка перед созданием задачи;
- drag-and-drop переходы задач между статусами;
- optimistic locking по полю `version`;
- история смены статусов, аудит и outbox-события;
- раздел загруженности с дневной детализацией;
- две логические базы MySQL и Liquibase-миграции;
- Docker Compose для запуска всего решения.

## Архитектура

- `enterprise-app` — Spring Boot, Thymeleaf, Bootstrap, Spring Security, JPA;
- `workload-service` — отдельный Spring Boot REST-сервис расчёта;
- `common-contracts` — DTO контрактов между сервисами;
- MySQL 8.4:
  - `enterprise_core`;
  - `enterprise_workload`.

## Быстрый запуск через Docker

### Требования

- Docker Desktop или Docker Engine;
- Docker Compose v2;
- свободный порт `8080`;
- порт `3307` нужен только для подключения к MySQL с хост-машины.

### Windows PowerShell

```powershell
Expand-Archive .\virtual-enterprise.zip
cd .\virtual-enterprise
Copy-Item .env.example .env
notepad .env
docker compose up --build
```

Либо:

```powershell
.\start.ps1
```

### Linux/macOS

```bash
unzip virtual-enterprise.zip
cd virtual-enterprise
cp .env.example .env
./start.sh
```

После запуска откройте:

```text
http://localhost:8080
```

## Демо-пользователи

| Роль | Логин | Пароль |
|---|---|---|
| Администратор | `admin` | `admin123` |
| Руководитель проекта | `manager` | `manager123` |
| Сотрудник | `employee` | `employee123` |

Пароли предназначены только для локальной демонстрации. Перед размещением приложения измените начальные данные и секреты.

## Настройка `.env`

```dotenv
MYSQL_ROOT_PASSWORD=root_password_change_me
DB_USERNAME=virtual_app
DB_PASSWORD=virtual_app_password
WORKLOAD_SERVICE_TOKEN=change-me-internal-token
SESSION_TIMEOUT_MINUTES=30
MAX_FILE_SIZE_MB=20
```

После первого запуска MySQL создаёт пользователя и базы только при инициализации пустого volume. Если вы изменили логин или пароль БД после первого запуска, пересоздайте volume:

```bash
docker compose down -v
docker compose up --build
```

Команда удалит локальные данные приложения.

## Подключение к MySQL

Из MySQL Workbench, DataGrip или IntelliJ IDEA:

```text
Host: localhost
Port: 3307
User: virtual_app
Password: virtual_app_password
Databases: enterprise_core, enterprise_workload
```

Значения должны совпадать с `.env`.

## Запуск без Docker для Java-приложений

1. Установите Java 21 и Maven 3.9+.
2. Запустите MySQL из Docker:

```bash
docker compose up mysql
```

3. В первом терминале:

```bash
mvn -pl workload-service -am spring-boot:run
```

4. Во втором терминале:

```bash
mvn -pl enterprise-app -am spring-boot:run
```

При локальном запуске приложения используют MySQL на `localhost:3307`.

## Полезные команды

```bash
# Запуск
 docker compose up --build

# Запуск в фоне
 docker compose up --build -d

# Логи основного приложения
 docker compose logs -f enterprise-app

# Логи сервиса расчёта
 docker compose logs -f workload-service

# Остановка
 docker compose down

# Полный сброс БД и файлов
 docker compose down -v
```

## Основные адреса

| Адрес | Назначение |
|---|---|
| `/dashboard` | дашборд |
| `/employees` | сотрудники |
| `/projects` | проекты |
| `/kanban` | Kanban-доска |
| `/workload` | загрузка сотрудников |
| `/actuator/health` | состояние основного приложения |

`workload-service` не публикуется наружу и доступен только во внутренней Docker-сети.

## API задач

```text
POST  /api/v1/tasks/workload-preview
POST  /api/v1/tasks
PATCH /api/v1/tasks/{taskId}/status
```

Браузерные запросы защищены CSRF. Клиентский код получает токен из cookie `XSRF-TOKEN` и передаёт заголовок `X-XSRF-TOKEN`.

## Структура репозитория

```text
virtual-enterprise/
├── common-contracts/
├── enterprise-app/
├── workload-service/
├── mysql-init/
├── docs/
├── docker-compose.yml
├── .env.example
├── start.ps1
├── start.sh
└── README.md
```

## Ограничения текущей сборки

Архив содержит рабочее ядро MVP. В техническом задании также предусмотрены расширенные CRUD-формы администрирования, комментарии, вложения, компетенции, согласование отсутствий, полноценные PDF/XLSX-отчёты и внешний email-канал. Структура БД и архитектура допускают их дальнейшее добавление, но эти функции не включены в текущую демонстрационную сборку.

## Документация

Документация интерфейса:

- [Дизайн-система](docs/design-system.md)
- [Wireframes](docs/wireframes.md)
- [Пользовательские пути](docs/user-flow.md)
- [Проверка редизайна и границы реализации](docs/frontend-verification.md)

Карточки сотрудников и проектов доступны по ссылкам из списков. Раздел `/admin` доступен только администратору. UI Kit `/dev/ui-kit` зарегистрирован только при активном профиле `dev` и также требует ADMIN.

Полная постановка задачи находится в каталоге:

```text
docs/Техническое_задание.docx
```


## FlowStaff AI — инфраструктура ML

Добавлен каркас третьего сервиса attrition-ml-service. Команда запуска прежняя:
```powershell
docker compose up -d --build
```
Скопируйте новые параметры из .env.example в локальный .env при настройке окружения.
ML_SERVICE_TOKEN используется для внутренних запросов. Порт ML не публикуется.
База enterprise_ml создаётся автоматически также при обновлении существующего тома;
структура схемы отслеживается Liquibase. Полные команды и границы этапа:
[docs/flowstaff-stage-1.md](docs/flowstaff-stage-1.md).

Текущий статус: код инфраструктуры подготовлен, полный запуск требует завершения
проверки после устранения нехватки места на диске Docker (см. IMPLEMENTATION_STATUS.md).
