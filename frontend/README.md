# Интерфейс FlowStaff

React 19 + TypeScript + Vite. Тема Black × Lilac × Lime, локальные Suisse Intl и JetBrains Mono, Phosphor Icons; диалоги Radix, навигация React Router, загрузка TanStack Query.

## Запуск и сборка

Node.js 22.14+ и Java 21+ необходимы для сборки. Готовый JAR работает без Node.js.

Из корня проекта:

```powershell
mvn clean package
docker compose up --build
```

Приложение: http://localhost:8080/login. Maven сам устанавливает зависимости по package-lock, собирает TypeScript/Vite и помещает ресурсы в JAR. Повторная установка нужна только при изменении lockfile или основной версии Node. Docker использует отдельный Node-этап.

Разработка с уже запущенным backend:

```powershell
cd frontend
npm ci
npm run dev
```

Vite слушает 5173 и проксирует запросы к backend:8080. Для другого адреса задайте переменную FLOWSTAFF_BACKEND, например http://localhost:18080. Доступны npm run check и npm run build. В PowerShell Maven-параметры с точкой заключайте в кавычки: `'-Dfrontend.skip=true'`; пропуск допустим только когда frontend уже собран.

## Что сохранено на сервере

Авторизация, сессии, CSRF, права, фильтрация, валидация, версии записей, операции MVC и JSON API остались серверными. React загружает страницы с заголовком X-FlowStaff-View: 1 и разбирает декларативный контракт из ui-contracts в компоненты. Серверный HTML не вставляется через innerHTML; скрипты и inline-обработчики отбрасываются. Это совместимость с существующим MVC, а не полная миграция backend на JSON REST.

Формы сохраняют action, multipart, formaction, скрытые поля версии и CSRF, checkbox-маркеры Spring и ошибки. Kanban, создание задачи, подтверждение перегрузки, смена статусов и комментарий причины реализованы в React и используют существующий JSON API.

Новые самостоятельные экраны следует писать компонентами React и подключать к явным DTO. При последующей замене транспорта можно убрать адаптер без изменения нового AppShell и дизайн-токенов.

## Дизайн-система

Токены — `src/tokens.css`, оформление — `src/product-theme.css`, общие компоненты — `src/ui.tsx`. UI-кит доступен в Vite dev на `http://127.0.0.1:5173/dev/design-system` и отсутствует в production. Подробные правила — [docs/design-system.md](../docs/design-system.md).

Предоставленный Suisse Regular содержит только латиницу. По подтверждённому выбору пользователя русский текст отображается локальным Onest, латиница — Suisse. JetBrains Mono используется только для данных; OFL-лицензия сохранена рядом с файлами.

## Проверки

`mvn clean package` запускает Java-тесты и проверку TypeScript. `tools/browser-react-acceptance.js` проверяет страницы на изолированном стенде 18080 при 1440×900, 1280×720 и 390×844. Снимки и журнал сохраняются в output/react-ui. Старые исторические браузерные сценарии могут содержать прежние названия кнопок; актуальный UI проверяется новым сценарием.
