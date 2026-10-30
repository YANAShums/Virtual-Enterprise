# Исправление загрузки CSV в ML

## Причина

При отправке CSV со страницы `/admin/ml/models` Java завершала запрос с HTTP 500: `NoClassDefFoundError: org/reactivestreams/Publisher`. `MultipartBodyBuilder` ссылался на Reactive Streams, отсутствующий в runtime classpath синхронного Spring MVC приложения. Файл не доходил до Python. Аналогичный код существовал и в старом пути импорта V1.

## Изменение

Общая фабрика `CsvMultipartBody` формирует обычный `MultiValueMap<String, Object>` с `HttpEntity<ByteArrayResource>` и MIME `text/csv`. Оба синхронных RestClient используют её. Имя файла, поле `file`, содержимое, адреса API, авторизация и ограничения размера сохраняются. Реактивный стек и дополнительные зависимости не добавлены.

## Проверка

- 9 целевых Java-тестов пройдены: сериализация настоящими message converters для V1/V2, клиент ML, сервис и контроллер workflow.
- Регрессионный тест проверяет POST, multipart boundary, поле `file`, имя файла, Content-Type и байты CSV.
- Docker-сборка приложения прошла. Существующие изменения пользователя в PDF/отчётах не включаются в коммит исправления.
- Через реальную React-форму на 127.0.0.1:5173 загружен `FlowStaffAI_training_SYNTHETIC_detailed.csv` (2 000 826 байт). POST завершился штатным redirect 302, интерфейс показал «Принято: 13539, ошибок: 0». Ошибок JavaScript нет.
- Загружены только учебные данные по намерению пользователя. Обучение и активация рабочей модели в ходе исправления не запускались.
- Локальные доказательства: `output/ml-training-dataset/browser-upload-results.json`, `upload-success.png`, `output/redesign/ml-upload-tests.log`.

Первоначальная проверка подготовленного CSV выполнялась напрямую Python-импортом и обучением в временной SQLite-базе. Она подтверждала корректность данных, но не проверяла Java multipart-путь — это и объясняет отличие от ошибки в браузере.
