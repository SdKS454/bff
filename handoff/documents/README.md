# Documents Service — версия для интеграции с BFF

Сервис генерирует PDF и хранит только метаданные в PostgreSQL. PDF не сохраняется.
Сверен с BFF, присланным User Service и исправленным Moderation 24 октября 2026.
Изменения исходного архива и оставшиеся задачи: `integrations/documents/README.md`
в репозитории BFF. Исходный README сохранён там же как `README_UPSTREAM.md`.

## Запуск

1. Java **17+**, Maven **3.9+**, Tomcat **10.1+**, PostgreSQL. Код не требует Java 21;
   сборка приведена к общей Java 17 / Servlet 6.0, Java 21 также подходит.
2. Создать **отдельную** БД documents и выполнить `src/main/resources/schema.sql`.
3. Настроить переменные окружения (либо соответствующие application.properties):

| Переменная | Значение / назначение |
| --- | --- |
| DB_URL | JDBC URL собственной PostgreSQL БД |
| DB_USERNAME | пользователь БД; здесь именно DB_USERNAME, у User/Moderation — DB_USER |
| DB_PASSWORD | пароль БД, задаётся вне репозитория |
| USER_SERVICE_URL | default `http://localhost:8081` |
| MODERATION_SERVICE_URL | default `http://localhost:8082` |
| RIDE_SERVICE_URL | default `http://localhost:8084` |
| JWT_PUBLIC_KEY_PATH | путь к **публичному** RSA-ключу User в PEM SubjectPublicKeyInfo; приватный ключ сюда не копировать |
| JWT_PUBLIC_KEY | альтернативно публичный PEM либо base64 DER, если не задан путь |
| JWT_ISSUER | должен совпадать с User; default `scooter-user-service` |
| PDF_FONT_PATH | **обязательно** доступный TTF-шрифт с кириллицей, например LiberationSans-Regular.ttf / DejaVuSans.ttf; шрифт в архив не включён |

Публичный ключ должен соответствовать приватному ключу, которым текущий User
подписывает JWT. После замены ключа перезапустить Documents (ключ кешируется).
Переопределение для локальных тестов возможно JVM-свойствами `-Ddocuments.<ключ>=...`,
например `-Ddocuments.user.service.url=...`; приоритет: JVM → env → properties.

4. `mvn clean verify` → `target/documents.war`. Для PDF-тестов тоже нужен шрифт:
   задайте PDF_FONT_PATH; на Linux тесты пробуют LiberationSans-Regular.ttf.
5. В согласованном стенде Documents слушает **8085**, контекст **/**:
   развернуть сборку как `webapps/ROOT.war` отдельного Tomcat. При исходном имени
   WAR путь будет `/documents/api/documents/generate`, тогда base URL в BFF должен
   включать `/documents`. Согласованный URL: `http://localhost:8085/api/documents/generate`.

## Авторизация и API

`POST /api/documents/generate`, `Content-Type: application/json`,
`Authorization: Bearer <JWT>`; успех 200, application/pdf, no-store.
JWT локально проверяется по **RS256**, issuer, exp, согласованным userId/sub;
до записи метаданных User `/api/users/validate` проверяет отзыв/актуальность.
Исходный JWT передаётся во все внешние запросы. userId тела должен совпадать
с UUID пользователя в JWT. Клиентские status/amount/role не определяют документ.

- RIDE_PAYMENT (старый RECEIPT поддерживается): `{ "userId": "<UUID>", "docType": "RIDE_PAYMENT", "sourceId": "<ride UUID>" }`.
- USER_BLOCKED: `{ "userId": "<UUID>", "docType": "USER_BLOCKED" }`.
- USER_HISTORY: `{ "userId": "<UUID>", "docType": "USER_HISTORY", "from": "2026-10-01", "to": "2026-10-31" }`.

BFF сам добавляет текущий userId. sourceId нужен только для чека; границы истории
включительны, startedAt переводится в UTC перед сравнением дат. Невалидные
UUID/даты/типы JSON — 400; лимит тела 64 KiB — 413; неверный Content-Type — 415.
Ошибки JSON `{ "error": "..." }`; 401 — JWT, 403 — чужой пользователь/поездка,
404 — нет поездки, 409 — не оплачена / текущего бана нет, 502 — неверные данные
внешнего сервиса, 503 — недоступная зависимость/проверка токена/генерация PDF.

## Зависимости и задачи коллегам

- **User:** GET `/api/users/{userId}` (login/fullName/id) и GET `/api/users/validate`.
  Доступ обычного пользователя к собственному профилю с его JWT подтверждён
  на реальном User. Выход User отзывает токен; Documents после этого отвечает 401.
- **Moderation:** GET `/api/moderation/users/{userId}/status` с тем же JWT.
  Работает с исправленным Moderation: ADMIN/self, обязательные userId и boolean
  blocked; при true нужны reasonType/reason/blockedAt, blockedUntil может быть null.
  При false — 409, PDF о несуществующем бане не выдаётся.
- **Ride: осталось подтвердить на настоящем сервисе.** Нужны GET `/api/rides/{rideId}`
  и GET `/api/rides?userId=<UUID>`, с передачей исходного Bearer и проверкой владельца.
  Объект поездки: UUID id/userId, numberPlate, status, startedAt (ISO offset/Z).
  Для PAID обязательны amount ≥ 0, currency (3 заглавные латинские буквы),
  finishedAt и paidAt; порядок startedAt ≤ finishedAt ≤ paidAt.
  Без этих данных чек не генерируется (502), подставной валюты RUB нет.
  Список — JSON-массив собственных поездок. Чужая поездка в списке — ошибка ответа,
  а не успешная неполная история. Внешний 404 не превращается в 503.

Настоящий Ride ещё не предоставлен; чек/история проверены с HTTP-фикстурами его
контракта. User/Moderation/Documents/BFF и PostgreSQL в сквозном сценарии настоящие.

## Ограничения и шаблоны

Дополнительные GET `/api/documents`, GET `/api/documents/{id}` и DELETE
`/api/documents/{id}` не опубликованы. Методы repository сами по себе не являются
REST API. Кнопки скачивания уже есть в ScootX/BFF; отдельный UI Documents не нужен.

Три HTML-шаблона рендерятся Thymeleaf → OpenHTMLtoPDF; проверено извлечение
русского текста из полученных PDF. Наличие библиотеки в проекте не заменяет
согласование допустимого PDF-стека с преподавателем, если оно требуется по заданию.
`doc`/`inlineCss` передаются динамически; предупреждения IDEA Cannot resolve
в шаблонах не равны ошибкам компиляции Java. Сборка и реальные проверки обязательны.

Метаданные проходят PENDING → GENERATED/FAILED. Если при обработке исходной
ошибки отдельно ломается отметка FAILED, она не затирает исходный HTTP-статус.
