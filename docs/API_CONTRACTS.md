# Кикшеринг: REST-контракты микросервисов (по BFF)

Исходный документ: 16 октября 2026. Сверка BFF/User и адресов локального стенда: 22 октября 2026.

Разделы BFF и User обновлены по реализации. Контракты остальных сервисов и пометки [предложение] сохраняются как требования/вопросы для их команд; наличие контракта не подтверждает запуск их реализаций.

## 1. Обзор и общие соглашения

Документ фиксирует REST-контракты, которые BFF уже реализует для клиента и ожидает от четырёх downstream-сервисов; он составлен по актуальному коду BFF. Что в коде не определено и предложено нами, помечено **\[предложение\]**; всё, что требует решения команд, собрано в разделе «Открытые вопросы».

| Сервис | Порт по умолчанию | Свойство в BFF | Вызывается из BFF |
| --- | --- | --- | --- |
| User Service | 8081 | `user.service.url` | да |
| Moderation Service | 8082 | нет в BFF; `MODERATION_BASE_URL` в готовом User; настройка Documents зависит от его реализации | **нет** (вызывается из User Service и Documents Service, см. раздел 5) |
| Scooters Service | 8083 | `scooter.service.url` | да |
| Ride Service | 8084 | `ride.service.url` | да |
| Documents Service | 8085 | `document.service.url` | да |

### 1.1 Адреса согласованного локального стенда

| Компонент | Базовый адрес / страница |
| --- | --- |
| BFF + ScootX, порт 8080, контекст `/` | `http://localhost:8080/` |
| Вход BFF для пользователей и модераторов | `http://localhost:8080/login.jsp` |
| Прямая форма входа ScootX | `http://localhost:8080/?auth=login` |
| Регистрация | `http://localhost:8080/register.jsp` |
| User Service | `http://localhost:8081` |
| Moderation Service | `http://localhost:8082` |
| Панель Moderation (адрес подтверждён её командой) | `http://localhost:8082/admin/users.html` |
| Scooters Service | `http://localhost:8083` |
| Ride Service | `http://localhost:8084` |
| Documents Service | `http://localhost:8085` |

Пути BFF ниже указаны относительно его базового адреса. При согласованном
корневом развёртывании полный адрес входа API —
`http://localhost:8080/api/auth/login`; downstream-адрес User —
`http://localhost:8081/api/users/login`. Это разные ручки двух приложений.

Порт не задаёт context path. Здесь согласован именно контекст `/`, поэтому
префикс `/scooter-bff` в адресах этого стенда не используется. Maven по-прежнему
создаёт `scooter-bff.war`: для корневого запуска в обычном Tomcat его размещают
как `webapps/ROOT.war` либо задают application context `/` в IDE. Если положить
непереименованный `scooter-bff.war`, стандартный Tomcat даст другой контекст
`/scooter-bff`; это альтернативный способ развёртывания, а не адрес этого стенда.
BFF берёт context path динамически; прикладной код менять для `/` не требуется.

`login.jsp` и `register.jsp` совместимы со старым адресом входа, но перенаправляют
в формы единого ScootX. Формы открываются без микросервисов; для входа нужен
User с PostgreSQL/JWT-ключами и работающим Moderation. BFF не должен занимать
8081 одновременно с User.

### Общие правила

- **Формат:** JSON, UTF-8, `Content-Type: application/json`. Исключение — PDF-чек (`application/pdf`).
- **Авторизация:** `Authorization: Bearer <JWT>`. BFF принимает JWT из этого заголовка либо из HTTP-сессии (кладётся туда после `/api/auth/login`) и **пробрасывает его без изменений в каждый downstream-вызов**. Каждый сервис сам валидирует токен и достаёт из него `userId`.
- **Проверка токена в BFF:** перед защищёнными `/api/*` BFF вызывает `GET {User}/api/users/validate`. Публичны только POST register/login, GET users/exists. POST auth/logout обрабатывает текущий/истёкший токен сам, без предварительного validate. Любой 2xx validate — успех; отказ — 401, недоступность/5xx — 503. Общего публичного исключения `/api/auth/*` нет.
- **Формат ошибки BFF:** `{"error": "текст"}`. Ответы downstream-сервисов (статус и тело) BFF пробрасывает клиенту как есть, поэтому **сервисам нужен единый формат ошибки** — предлагаем тот же `{"error": "..."}`.
- **Недоступность сервиса:** BFF отвечает 503. Таймауты BFF: подключение 3 с, ответ 10 с.
- **Именование полей:** camelCase. Координаты — `posX`, `posY` (в ТЗ они названы `posx`, `posy`; см. раздел «Открытые вопросы»).
- **Даты:** `birthDate` — `YYYY-MM-DD`; метки времени — ISO-8601 UTC **\[предложение\]**.

## 2. Публичный API BFF (клиент → BFF)

Основные API и маршруты BFF перечислены ниже; большинство — простое проксирование с сохранением статуса и тела ответа downstream-сервиса. Колонка «Auth» — нужен ли JWT.

| # | Метод и путь BFF | Auth | Дальше вызывает |
| --- | --- | --- | --- |
| 1 | `POST /api/auth/register` | нет | User: `POST /api/users/register` |
| 2 | `POST /api/auth/login` | нет | User: `POST /api/users/login` |
| 3 | `GET /api/users/exists?login=` | нет | User: `GET /api/users/exists` |
| 4 | `GET /api/users/me` | да | User: `GET /api/users/me` |
| 5 | `GET /api/scooters` | да | Scooters: `GET /api/scooters` |
| 6 | `POST /api/scooters/nearest` | да | Scooters: `POST /api/scooters/nearest` |
| 7 | `GET /api/scooters/{numberPlate}` | да | Scooters: `GET /api/scooters/{numberPlate}` |
| 8 | `POST /api/rides` | да | User: `me`, `blocked`; Ride: `POST /api/rides` |
| 9 | `PUT /api/rides/{id}/finish` | да | Ride: `PUT /api/rides/{id}/finish` |
| 10 | `POST /api/rides/{id}/pay` | да | Ride: `POST /api/rides/{id}/pay` |
| 11 | `GET /api/rides` | да | User: `me`; Ride: `GET /api/rides?userId=` |
| 12 | `POST /api/documents/generate` | да | Documents: `POST /api/documents/generate` |
| 13 | `POST /api/auth/logout` | текущий JWT из Bearer/сессии; повтор без JWT допустим | User: `POST /api/users/logout`, затем закрытие сессии BFF; ответ 204 |
| 14 | `POST /logout` | как выше | тот же выход для HTML-формы, затем 303 на `/login.jsp` |
| 15 | `GET /logout` | нет | страница подтверждения; ничего не отзывает |
| 16 | `GET /api/config` | да | локальная настройка `{moderationPanelUrl}` для перехода ADMIN |

BFF ничего не вызывает у Moderation Service — см. «Открытые вопросы».

### 2.1 Регистрация

`POST /api/auth/register` — проверяет поля и проксирует результат User Service.
Логин: 3–64 символа латиницы, цифр, `_`, `.` и `-`, сервер приводит к нижнему регистру
и убирает крайние пробелы. Пароль: минимум 8 символов (String.length как у User),
максимум 72 байта UTF-8, без NUL. fullName обязателен: до 200 символов после trim,
без управляющих символов. birthDate обязательна: YYYY-MM-DD, от 1900-01-01 до
сегодня UTC. avatarUrl опционален. Роль клиента не пересылается; User создаёт USER.
ScootX после успешной регистрации выполняет отдельный запрос login. Если вход
не удался, аккаунт уже создан: UI предлагает войти без повторной регистрации.

```json
// Запрос
{
  "login": "ivan",
  "password": "example-password",
  "fullName": "Иван Петров",
  "birthDate": "2000-05-17",
  "avatarUrl": "https://cdn.example.com/a/ivan.png"
}
// Ответ 201: профиль нового пользователя
{
  "id": "550e8400-e29b-41d4-a716-446655440000", "login": "ivan", "role": "USER", "fullName": "Иван Петров",
  "birthDate": "2000-05-17", "avatarUrl": "https://cdn.example.com/a/ivan.png",
  "deleted": false, "blocked": false
}
```

### 2.2 Логин

`POST /api/auth/login` — в теле запроса только `login` и `password`. При успехе BFF создаёт HTTP-сессию, кладёт в неё JWT и выставляет таймаут сессии = `expiresInSeconds`. Токен мы также возвращаем в теле ответа.

```json
// Запрос
{ "login": "ivan", "password": "example-password" }
// Ответ 200
{
  "accessToken": "eyJhbGciOi...",
  "expiresInSeconds": 3600,
  "user": { "id": "550e8400-e29b-41d4-a716-446655440000", "login": "ivan", "role": "USER", "fullName": "Иван Петров",
            "birthDate": "2000-05-17", "avatarUrl": null, "deleted": false, "blocked": false }
}
```

Если User Service ответил не 2xx, статус и тело пробрасываются клиенту (ожидаем 401 для неверных данных). Отсутствующий/пустой accessToken, неверное время жизни или неправильный UUID пользователя в успешном login-ответе дают 502.

#### Переход после входа

Фронтенд читает `user` и `accessToken` из JSON-ответа login.

- `user.role === "ADMIN"`: через `location.replace` переходит на
  `http://localhost:8082/admin/users.html#accessToken=<encodeURIComponent(accessToken)>`.
- `USER`: остаётся в ScootX и открывает личный кабинет; в Moderation не переходит.
- Браузер BFF не сохраняет JWT в localStorage/sessionStorage; для BFF используется
  HttpOnly-сессия. Токен для Moderation передаётся во фрагменте, не в query string.

При отсутствии токена панель Moderation направляет на
`http://localhost:8080/login.jsp`. После получения токена панель должна прочитать
фрагмент, при необходимости сохранить JWT в своём sessionStorage, очистить
фрагмент через history.replaceState и передавать `Authorization: Bearer <JWT>`
в запросах к API. Права ADMIN проверяются сервисами, а не только фронтендом.

Адрес панели в BFF по умолчанию совпадает с адресом команды. Его можно изменить
через `MODERATION_PANEL_URL` или `-Dmoderation.panel.url=...` (приоритет JVM).
Текущий фронт — `frontend/account.js` и `frontend/api.js`; старого `js/login.js`
после объединения ScootX больше нет. `login.jsp` остаётся совместимым входным URL.

### 2.3 Проверка логина

`GET /api/users/exists?login=ivan` — без авторизации. Пустой `login` → BFF сам отвечает `400 {"error":"Укажите логин"}`. Остальное — прокси. Готовый User возвращает `200 {"exists": true}` (либо false).

### 2.4 Профиль

`GET /api/users/me` — прокси `GET {User}/api/users/me`. Внутри BFF тот же вызов используется, чтобы узнать `userId`, поэтому ответ **обязан** содержать `id` в формате UUID (иначе BFF ответит 502).

```json
// Ответ 200
{ "id": "550e8400-e29b-41d4-a716-446655440000", "login": "ivan", "role": "USER", "fullName": "Иван Петров",
  "birthDate": "2000-05-17", "avatarUrl": null, "deleted": false, "blocked": false }
```

### 2.5 Список самокатов

`GET /api/scooters` — все самокаты. Фильтр «только свободные»: `?status=READY` или `?available=true` (оба ведут к `GET {Scooters}/api/scooters?status=READY`). Другие значения `status` игнорируются.

```json
// Ответ 200 [предложение]
[ { "numberPlate": "A123BC", "status": "READY", "charge": 87, "posX": 10, "posY": 25 } ]
```

### 2.6 Ближайшие самокаты

`POST /api/scooters/nearest` — тело с `posX` и `posY`, оба обязательны, иначе `400 {"error":"Нужны posX и posY"}`. `GET` на этот путь → 405. BFF передаёт значения «как есть» (сырым JSON-числом), без приведения к int.

```json
// Запрос
{ "posX": 10, "posY": 24 }
// Ответ 200 [предложение], отсортирован по distance возрастающей
[ { "numberPlate": "A123BC", "status": "READY", "charge": 87, "posX": 10, "posY": 25, "distance": 1.0 },
  { "numberPlate": "B777OP", "status": "READY", "charge": 64, "posX": 12, "posY": 30, "distance": 6.3 } ]
```

Фронтенд уже читает именно `numberPlate`, `status`, `charge`, `distance` (и пробует `number_plate` как запасной вариант) — эти имена фиксируем как контракт.

### 2.7 Самокат по номеру

`GET /api/scooters/{numberPlate}` — номер уже декодирован контейнером Servlet и кодируется ровно один раз при вызове Scooters. Ответ — один объект самоката той же формы, без `distance`; 404, если не найден.

### 2.8 Начало поездки

`POST /api/rides`. Порядок внутри BFF: (1) `GET {User}/api/users/me` → `userId`; (2) `GET {User}/api/users/{userId}/blocked`; если заблокирован — `403 {"error":"Пользователь заблокирован"}`; (3) `POST {Ride}/api/rides`. `userId` в Ride не передаётся — Ride берёт его из JWT.

```json
// Запрос
{ "numberPlate": "A123BC", "rateId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "posX": 10, "posY": 24 }
// Ответ 201 [предложение]
{ "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "userId": "550e8400-e29b-41d4-a716-446655440000", "numberPlate": "A123BC", "rateId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "status": "ACTIVE",
  "startedAt": "2026-10-16T07:40:00Z", "startPosX": 10, "startPosY": 24 }
```

`rateId` — необязателен на уровне BFF (может быть `null`; фронтенд его сейчас не шлёт), решение о тарифах — в «Открытых вопросах».

### 2.9 Завершение поездки

`PUT /api/rides/{id}/finish` — `{id}` должен быть UUID, иначе 404.

```json
// Запрос
{ "posX": 15, "posY": 30 }
// Ответ 200 [предложение]
{ "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "status": "FINISHED", "finishedAt": "2026-10-16T08:05:00Z",
  "durationSeconds": 1500, "amount": 250.00, "currency": "RUB", "paid": false }
```

### 2.10 Оплата поездки

`POST /api/rides/{id}/pay` — без тела (BFF шлёт `{}`).

```json
// Ответ 200 [предложение]
{ "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "status": "PAID", "amount": 250.00, "currency": "RUB", "paidAt": "2026-10-16T08:06:10Z" }
```

### 2.11 История поездок

`GET /api/rides` — поездки текущего пользователя; параметров нет. BFF сначала получает `userId` из User Service, потом вызывает `GET {Ride}/api/rides?userId={userId}`. Неизвестный хвост пути, например `GET /api/rides/любой-путь`, возвращает 404. Клиентский userId в query не заменяет текущего пользователя.

```json
// Ответ 200 [предложение]
[ { "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "numberPlate": "A123BC", "status": "PAID", "startedAt": "2026-10-16T07:40:00Z",
    "finishedAt": "2026-10-16T08:05:00Z", "amount": 250.00, "currency": "RUB" } ]
```

Фронтенд использует ACTIVE → FINISHED → PAID. Сумма и статусы берутся из Ride, локальной имитации оплаты/тарифа нет.

### 2.12 Генерация PDF

`POST /api/documents/generate` — успешный ответ BFF: `200`, `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="document.pdf"`, тело = байты PDF от Documents Service. Ошибки — JSON.

```json
// Чек оплаченной поездки
{ "docType": "RIDE_PAYMENT", "sourceId": "7c9e6679-7425-40de-944b-e07fc1f90ae7" }
// История поездок
{ "docType": "USER_HISTORY", "from": "2026-10-01", "to": "2026-10-31" }
// Уведомление о блокировке
{ "docType": "USER_BLOCKED" }
```

userId BFF берёт из `/api/users/me`, клиентское значение не использует.
Для RIDE_PAYMENT нужен UUID sourceId; для USER_HISTORY — корректный период.
RECEIPT принимается как старый alias RIDE_PAYMENT; status не пересылается.
Documents дополнительно проверяет владельца поездки и факт оплаты по JWT.
BFF проверяет Content-Type и сигнатуру PDF; успешный ответ не в формате PDF → 502.

### 2.13 Выход

POST `/api/auth/logout` вызывает User POST `/api/users/logout` с текущим JWT,
затем закрывает сессию BFF; успешный ответ — 204. POST `/logout` делает то же
для HTML-формы, затем отвечает 303 с Location `/login.jsp` при корневом контексте.
GET `/logout` показывает подтверждение и ничего не меняет.

Без JWT повторный выход допустим. 401 User при отзыве означает, что токен уже
непригоден. Если User недоступен/отзыв отклонён, сессия BFF закрывается, но BFF
возвращает ошибку, не заявляя успешный отзыв. Прочие сервисы должны учитывать
отзыв токена через User, если им нужен немедленный общий logout.

### Коды ответов BFF

| Код | Когда |
| --- | --- |
| 400 | некорректный JSON/поля; нет `login` или `posX`/`posY` |
| 401 | нет JWT или токен отвергнут; 5xx validate не маскируется как 401 |
| 403 | пользователь заблокирован (только при старте поездки) |
| 404 | неизвестный путь внутри `/api/*` |
| 405 | неподдерживаемый метод, в том числе GET `/api/scooters/nearest` |
| 415 | тело API-запроса отправлено не как application/json |
| 502 | некорректный ответ User/статуса блокировки или неправильный PDF |
| 503 | сервис недоступен или таймаут |
| любой другой | проброшен от downstream-сервиса без изменений |

## 3. Контракты для команд сервисов (BFF → сервис)

Каждая команда реализует эндпоинты, которые BFF уже вызывает; исключение — эндпоинты с пометкой **[предложение]** и вызовы между сервисами (User → Moderation, Documents → Ride/User/Moderation).Во всех вызовах (кроме `register`, `login`, `exists`) уходит `Authorization: Bearer <JWT>`. Все сервисы возвращают `Content-Type: application/json` и ошибки вида `{"error": "..."}`.

### 3.1 User Service (`:8081`)

| Эндпоинт | Тело / параметры запроса | Ответ, который ожидает BFF |
| --- | --- | --- |
| `POST /api/users/register` (без JWT) | `login`, `password`, `fullName`, `birthDate`, `avatarUrl`; role не пересылается | `201` + user (форма ниже); BFF прокидывает как есть. `409`, если логин занят |
| `POST /api/users/login` (без JWT) | `login`, `password` — только эти поля | `200` `{accessToken, expiresInSeconds, user}`. BFF **парсит** этот ответ: `accessToken` обязателен |
| `GET /api/users` | — | `200` массив пользователей для админки: `[{"id":"550e8400-e29b-41d4-a716-446655440000","login":"ivan","fullName":"Иван Петров"}]` **[предложение]** |
| `GET /api/users/me` | — | `200` user. Поле `id` (UUID) обязательно |
| `GET /api/users/{id}` | path `id` (UUID) | `200` user, если пользователь существует; `404`, если не найден. Используется для проверки существования перед блокировкой **[предложение]** |
| `GET /api/users/exists?login=` (без JWT) | query `login` (URL-encoded) | `200 {"exists": true\|false}` **\[предложение\]** |
| `GET /api/users/validate` | — | любой 2xx = токен валиден; `401` — нет. Тело BFF не читает. Вызывается на каждый запрос клиента, поэтому нужен дешёвый |
| `GET /api/users/{id}/blocked` | path `id` (UUID) | `200` тело `true`/`false` или `{"blocked": true}`; можно и то, и другое. **Явный blocked=true → 403 при старте; недоступность/5xx → 503, неверная структура/пустое тело → 502** |

User объект (BFF разбирает ровно эти поля; недостающие = `null`/`false`):

```json
{ "id": "550e8400-e29b-41d4-a716-446655440000", "login": "ivan", "role": "USER", "fullName": "Иван Петров",
  "birthDate": "2000-05-17", "avatarUrl": null, "deleted": false, "blocked": false }
```

В готовом User Service роли — USER и ADMIN; JWT — RS256, userId — UUID.
Приватный ключ хранится у User, публичный нужен сервисам с локальной проверкой;
BFF проверяет токен через validate. Реализован POST `/api/users/logout`, ответ 204,
после отзыва validate возвращает 401. User проверяет свою БД токенов. Полный
перечень готовых User endpoints и ограничения — также в USER_SERVICE_SYNC.md.

### 3.2 Moderation Service (не вызывается BFF)

Прямого REST-клиента Moderation в BFF нет; отдельно настроен URL страницы панели для browser-редиректа ADMIN. Согласно текущему коду «забанен ли пользователь» выясняется через User Service (`/api/users/{id}/blocked`), а тот должен сам спрашивать Moderation — это соответствует формулировке «BFF обращается через users». Moderation вызывается из User Service и из Documents Service (только `GET .../status`, см. раздел 3.5). Порт локального стенда — `8082`; готовый User читает адрес из MODERATION_BASE_URL; адрес для Documents настраивает его команда. У BFF есть только отдельная настройка URL страницы панели.

Для админки User Service также должен предоставить `GET /api/users` (список с `id` UUID, `login`, `fullName`) и `GET /api/users/{id}` (`404`, если пользователь не найден) — см. 3.1. Существование пользователя User Service проверяет сам до вызова Moderation; Moderation в User Service не обращается, чтобы не было циклической зависимости **[предложение]**.

Moderation хранит список заблокированных пользователей с причиной блокировки и сроком. История банов не ведётся. Предлагаемый контракт **[предложение]**:

| Эндпоинт | Тело / параметры | Ответ |
| --- | --- | --- |
| `GET /api/moderation/users/{userId}/status` | — | `200 {"userId": "550e8400-e29b-41d4-a716-446655440000", "blocked": true, "reasonType": "RIDE_DANGEROUS", "reason": "Агрессивное / опасное вождение", "blockedAt": "2026-10-10T12:00:00Z", "blockedUntil": "2026-10-17T12:00:00Z"}` |
| `POST /api/moderation/users/{userId}/ban` (ADMIN) | `{"reason": "RIDE_DANGEROUS"}` | `200` тот же объект статуса |
| `POST /api/moderation/users/{userId}/unban` (ADMIN) | — | `200` объект статуса |
| `GET /api/moderation/users?blocked=true` (ADMIN) | необязательные query `limit`, `offset` | `200` массив объектов статуса только действующих блокировок, новые первыми; пусто — `[]` |
| `GET /api/moderation/reasons` (ADMIN) | — | `200 [{"reasonType": "RIDE_DANGEROUS", "description": "Агрессивное / опасное вождение", "durationSeconds": 604800}]`; `durationSeconds: null` = навсегда |

Правила для Moderation:

- **Токен:** вызывающий сервис пробрасывает в Moderation JWT исходного запроса без изменений (как и остальные сервисы, см. «Общие правила»). Moderation сам валидирует токен и берёт из него роль и `userId` **[решить]** (альтернатива — сервисный токен, тогда роль передаётся отдельно).
- **Доступ к `status`:** ADMIN или сам пользователь (`userId` из JWT совпадает с `{userId}`), иначе `403`. Так Documents может вызывать `status` с JWT пользователя, который запрашивает документ.
- **Причины:** допустимые причины и их сроки заданы справочником Moderation (`GET /api/moderation/reasons`), свободный текст не принимается. В запросе `ban` поле `reason` — имя причины (`reasonType`), например `RIDE_DANGEROUS`; неизвестное или пустое значение даёт `400`. В ответе `reasonType` — имя причины, `reason` — её описание.
- **Срок блокировки:** определяется причиной и фиксируется при `ban`: `blockedUntil = blockedAt + длительность причины`. `blockedUntil: null` — бессрочно, снимается только через `unban`. По истечении срока `status` отвечает `blocked: false`, а запись пропадает из списка.
- **Пользователь без записи:** `status` отвечает `200` с `"blocked": false`; `reasonType`, `reason`, `blockedAt`, `blockedUntil` = `null`. Это значение по умолчанию, а не `404`.
- **`unban`:** пользователь убирается из списка заблокированных, его статус становится `"blocked": false`, а `reasonType`, `reason`, `blockedAt`, `blockedUntil` = `null`. Прошлые причины не сохраняются.
- **Повторные вызовы идемпотентны:** `ban` уже заблокированного → `200` с текущим объектом статуса (причина и сроки не перезаписываются); `unban` незаблокированного → `200` с `"blocked": false`.
- **Список и справочник причин** доступны только ADMIN.
- **Ошибки** (формат `{"error": "..."}`): `400` — `userId` не UUID, некорректное тело, неизвестная причина или параметры `limit`/`offset`, `401` — нет или невалидный токен, `403` — нет прав.
- **Источник правды для `blocked`:** значение хранит и отдаёт Moderation; поле `blocked` в объекте User Service берёт из ответа `status`, а не хранит у себя **[решить с User]**.
- **Недоступность Moderation:** User Service должен вернуть `503` из `/api/users/{id}/blocked`, а не «заблокирован»; это согласуется с вопросом №11 **[решить с User]**.

### 3.3 Scooters Service (`:8083`)

| Эндпоинт | Запрос | Ответ |
| --- | --- | --- |
| `GET /api/scooters` | необязательный query `status=READY` | `200` массив самокатов |
| `POST /api/scooters/nearest` | `{"posX": 10, "posY": 24}` | `200` массив самокатов **только со статусом READY**, по возрастанию `distance`; пусто — `[]` |
| `GET /api/scooters/{numberPlate}` | path (URL-encoded) | `200` объект самоката или `404` |

Объект самоката:

```json
{ "numberPlate": "A123BC", "status": "READY", "charge": 87, "posX": 10, "posY": 25, "distance": 1.0 }
```

- `distance` есть только в ответе `nearest`. Единицы — в одних условных единицах сетки `posX/posY` (фронтенд дописывает «м» — уточнить).
- `status`: `READY`, `IN_RIDE`, `CHARGING`, `REPAIR` **\[предложение\]** (в коде BFF жёстко зашит только `READY`).
- Расстояние — евклидово: `sqrt((x-posX)^2 + (y-posY)^2)`.

### 3.4 Ride Service (`:8084`)

| Эндпоинт | Тело / параметры | Ответ |
| --- | --- | --- |
| `POST /api/rides` | `{"numberPlate": "A123BC", "rateId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "posX": 10, "posY": 24}`; `rateId`, `posX`, `posY` могут быть `null` | `201` объект поездки со статусом `ACTIVE`. `409`, если самокат не `READY` или у пользователя уже есть активная поездка |
| `PUT /api/rides/{id}/finish` | `{"posX": 15, "posY": 30}` | `200` поездка со статусом `FINISHED` и рассчитанной `amount`. `404` — нет поездки; `403` — чужая; `409` — уже завершена |
| `POST /api/rides/{id}/pay` | `{}` | `200` поездка со статусом `PAID`. Идемпотентно: повтор на оплаченной → `200` с тем же объектом; `409` если поездка ещё не завершена |
| `GET /api/rides?userId=550e8400-e29b-41d4-a716-446655440000` | query `userId` | `200` массив поездок пользователя, новые первыми |

Правила для Ride:

- `userId` при старте берётся из JWT, в теле его нет.
- При `GET ?userId=` нужно сверять `userId` с JWT: BFF не проверяет владельца поездки при `finish`/`pay`, это должен делать Ride.

Объект поездки **\[предложение\]**:

```json
{ "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "userId": "550e8400-e29b-41d4-a716-446655440000", "numberPlate": "A123BC", "rateId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "status": "PAID",
  "startedAt": "2026-10-16T07:40:00Z", "finishedAt": "2026-10-16T08:05:00Z",
  "startPosX": 10, "startPosY": 24, "endPosX": 15, "endPosY": 30,
  "durationSeconds": 1500, "amount": 250.00, "currency": "RUB" }
```

Статусы: `ACTIVE` → `FINISHED` → `PAID`.

### 3.5 Documents Service (`:8085`)

Documents Service генерирует PDF по запросу от BFF. Поддерживаются три типа документов:

- `RIDE_PAYMENT` — подтверждение оплаты поездки.
- `USER_BLOCKED` — уведомление о блокировке пользователя.
- `USER_HISTORY` — история поездок за выбранный период.

| Эндпоинт | Тело / параметры запроса | Ответ |
|---|---|---|
| `POST /api/documents/generate` | `userId`, `docType`, `sourceId`, `from`, `to` | `200` + PDF (`application/pdf`) |
| `GET /api/documents` [предложение] | JWT | `200` + история генераций пользователя |
| `GET /api/documents/{id}` [предложение] | ID документа | `200` + метаданные документа |
| `DELETE /api/documents/{id}` [предложение] | ID документа | `204`, soft delete |

**RIDE_PAYMENT:**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "docType": "RIDE_PAYMENT",
  "sourceId": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
}
```

**USER_BLOCKED:**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "docType": "USER_BLOCKED"
}
```

Для генерации используем `GET /api/moderation/users/{userId}/status`. Если пользователь не заблокирован (`blocked: false`), возвращаем `409`.

**USER_HISTORY:**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "docType": "USER_HISTORY",
  "from": "2026-10-01",
  "to": "2026-10-31"
}
```

Все три варианта используют один POST endpoint. При успехе возвращаем `200`, `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="document.pdf"` и PDF в теле ответа. При ошибке — соответствующий HTTP-статус и `{"error":"..."}`.

`userId` сверяем с JWT. Для `RIDE_PAYMENT` проверяем, что поездка принадлежит пользователю и имеет статус `PAID`. Для `USER_BLOCKED` проверяем принадлежность блокировки пользователю. Для `USER_HISTORY` проверяем корректность периода.

BFF отправляет `docType = RIDE_PAYMENT`; RECEIPT принимает как совместимый alias и преобразует перед вызовом Documents.

**Запросы к другим сервисам:**

| Сервис | Метод | Endpoint | Назначение |
|---|---|---|---|
| Ride | GET | `/api/rides/{rideId}` | Данные конкретной поездки [предложение] |
| Ride | GET | `/api/rides?userId={userId}` | История поездок |
| Moderation | GET | `/api/moderation/users/{userId}/status` | Данные текущей блокировки |
| User | GET | `/api/users/{userId}` | Данные пользователя [предложение] |

Для `USER_HISTORY` Documents получает список поездок и фильтрует его по `from` и `to`.

**Генерация PDF:** три захардкоженных HTML-шаблона (`ride-payment.html`, `user-blocked.html`, `user-history.html`). Данные подставляются в HTML, затем HTML преобразуется в PDF. Файл создаётся только по запросу и не сохраняется.

**БД:** PostgreSQL, одна таблица `documents`. Храним `id`, `user_id`, `document_type`, `source_id`, `period_from`, `period_to`, `status`, `created_at`, `updated_at`, `generated_at`, `is_deleted`, `deleted_at`. Для `user_id` и `source_id` используем UUID. `source_id` заполняется только для `RIDE_PAYMENT`, для остальных типов документов — `NULL`. Поддерживаем статусы `PENDING`, `GENERATED`, `FAILED` и soft delete.

## 4. Сквозной сценарий аренды

&#91;embedded content: сквозной сценарий аренды · 6 запросов клиента, 8 вызовов сервисов\]

Бан-статус проверяется на шаге 6: если пользователь заблокирован, BFF отвечает 403 и до Ride Service не доходит. Шаги 8, 10 и 12 клиент вызывает строго по порядку: сначала завершение (расчёт суммы), затем оплата, затем чек **\[предложение: чек только для поездок в статусе `PAID`\]**.

## 5. Открытые вопросы и расхождения

> Это сохранённая таблица обсуждений из исходного документа. Исправления BFF
> из пунктов 9–11, статусы UI и роли уже отражены в разделах 1–3.1; таблица
> не является перечнем оставшихся багов текущего BFF. Тарифы, координаты,
> reserve/release и остальные межсервисные предложения требуют ответов команд.

Ниже собраны открытые вопросы и расхождения между контрактами микросервисов. Их необходимо согласовать между командами перед интеграцией.

| # | Вопрос / расхождение | Кто решает | Наше предложение |
| --- | --- | --- | --- |
| 1 | **Moderation не подключён.** В BFF нет URL и клиента; бан-статус берётся из `GET {User}/api/users/{id}/blocked` | User, Moderation, BFF | User Service сам спрашивает Moderation (раздел 3.2); BFF не меняется. Альтернатива — BFF ходит в Moderation напрямую (нужен `moderation.service.url`) |
| 2 | **Координаты:** в ТЗ `posx`/`posy` — целые числа; фронтенд шлёт `posX`/`posY` дробными из геолокации (`toFixed(5)`). BFF прокидывает число как есть | Scooters, Ride, клиент | Сервисы принимают `posX`/`posY` как `number` (int и float). Если строго int — клиент округляет до вызова |
| 3 | **Смена статуса самоката.** При старте поездки `READY` → `IN_RIDE`, при финише обратно. BFF этого не делает, эндпоинта смены статуса в Scooters нет | Ride, Scooters | Ride вызывает `PUT /api/scooters/{numberPlate}/status` `{"status": "IN_RIDE"}`; атомарность — отказ `409`, если статус не `READY`. Нужно разрешить связь Ride → Scooters, в топологии всё замыкается на BFF |
| 4 | **Данные для чека.** BFF шлёт в Documents только `sourceId`; данные поездки должны откуда-то прийти | Documents, Ride | Documents сам обращается к `GET {Ride}/api/rides/{id}` (такого эндпоинта сейчас нет — добавить). Альтернатива — BFF собирает данные и кладёт в тело |
| 5 | **Тарифы.** Поле `rateId` есть, эндпоинта списка тарифов нет, фронтенд его не шлёт | Ride, BFF | `GET {Ride}/api/rates` → `[{"id":"6ba7b810-9dad-11d1-80b4-00c04fd430c8","name":"Минутный","pricePerMinute":8.0,"unlockFee":50.0}]`; в BFF добавить `GET /api/rates`; если `rateId` = `null` — Ride берёт тариф по умолчанию |
| 6 | **Статусы поездки.** Фронт угадывает `ACTIVE`/`IN_PROGRESS`/`STARTED` | Ride, клиент | Один набор: `ACTIVE`, `FINISHED`, `PAID` (плюс `CANCELLED`, если нужен) |
| 7 | **Трекинг поездки.** В задаче есть «трекинг времени/координат», в BFF эндпоинта обновления позиции нет | Ride, BFF | `PUT /api/rides/{id}/position` `{"posX":..,"posY":..}` → `204`; в BFF прокси того же пути. Если трекинг не нужен — убрать из задачи |
| 8 | **Флоу оплаты:** в ТЗ завершение включает расчёт/проведение оплаты, в коде — два шага `finish` и `pay` | Ride, BFF | Оставить два шага (`finish` считает сумму, `pay` проводит); клиент вызывает их подряд |
| 9 | **Безопасность: `role` при регистрации** приходит от клиента и прокидывается в User Service | User, BFF | User игнорирует присланную роль и ставит `USER`; BFF перестаёт её пересылать |
| 10 | **Безопасность: `userId` в `documents/generate`** берётся из тела клиента — можно запросить чужой чек. То же для `finish`/`pay` (владелец не проверяется) | BFF, Documents, Ride | BFF подставляет `userId` из `/api/users/me`; Documents и Ride дополнительно сверяют владельца по JWT |
| 11 | **Отказ при проверке бана:** если `blocked` не ответил или вернул 5xx, BFF отдаёт клиенту «403 Заблокирован» | BFF | При 5xx/таймауте отдавать 503, при явном `blocked: true` — 403 |
| 12 | **Нагрузка на User Service:** `validate` на каждый запрос + повторная валидация в каждом сервисе | User, все | Подпись JWT (RS256, публичный ключ у всех) — сервисы проверяют локально; `validate` остаётся для проверок отзыва/бана |
| 13 | **Хранение документов.** Documents хранит метаданные генераций, но не сами PDF | Documents, BFF | Добавить `GET /api/documents` для истории генераций. Повторную выдачу PDF согласовать отдельно |
| 14 | **Moderation ↔ User: договорённости по вызову.** Не решено: какой токен User передаёт в Moderation (JWT исходного запроса или сервисный); кто источник правды для `blocked` (Moderation, а User не хранит поле у себя); что User отдаёт из `/api/users/{id}/blocked`, если Moderation недоступен | User, Moderation | JWT исходного запроса пробрасывается без изменений; `blocked` хранит и отдаёт Moderation; при недоступности Moderation User отвечает `503` (согласуется с №11). Подробности — раздел 3.2 |
| 15 | **Нет пути для бана из админки.** Moderation вызывается из User Service, но в User (раздел 3.1) нет ручек бана/разбана/списка заблокированных, а в BFF нет админских путей | User, BFF, Moderation | User добавляет `POST /api/users/{id}/ban` `{"reason": "RIDE_DANGEROUS"}`, `POST /api/users/{id}/unban`, `GET /api/users/blocked` и проксирует их в Moderation (раздел 3.2). В теле `ban` передаётся имя причины из справочника Moderation (`GET /api/moderation/reasons`), User и BFF пересылают его как есть. BFF добавляет прокси тех же путей. Существующие эндпоинты не меняются |
| 16 | **Роли.** Значения `role` не утверждены, а `ban`/`unban`/список заблокированных в Moderation доступны только ADMIN | User, Moderation, BFF | Утвердить `USER` и `ADMIN`; роль лежит в JWT (claim `role`); Moderation и User проверяют её сами. Связано с №9 (роль при регистрации всегда `USER`) |
| 17 | **Documents → Moderation для `USER_BLOCKED`.** Документ строится по данным блокировки, но id блокировки у Moderation нет (одна запись на пользователя, после `unban` она удаляется) | Documents, Moderation | Documents вызывает `GET /api/moderation/users/{userId}/status` с JWT пользователя; `sourceId` для `USER_BLOCKED` не нужен. В ответе есть `reason` (описание причины) и `blockedUntil` (`null` = бессрочно), их можно показать в документе. Если `blocked: false` — Documents отвечает `409` |
| 18 | **Снятие бессрочных блокировок.** Часть причин бессрочная (например, `PAYMENT_DEBT` — «до оплаты»), автоснятия у Moderation нет | Moderation, Ride, User | Снимается вручную через `unban`. Если нужно снятие при оплате долга, Ride или User вызывает `unban` после оплаты (потребуется сервисный токен, см. №14) |
| 19 | **Данные оплаты.** У Ride нет утверждённого GET конкретной поездки с полной информацией об оплате | Ride, Documents | Добавить `GET /api/rides/{rideId}` с полями `status`, `amount`, `currency`, `paidAt` |
| 20 | **HTML → PDF.** Не согласована библиотека для генерации PDF из HTML | Documents, преподаватель | Уточнить возможность использования OpenHTMLtoPDF вместо Apache POI |
| 21 | **История документов.** PDF не сохраняются, поэтому повторное скачивание по ID пока невозможно без повторной генерации | Documents, BFF | Согласовать, нужна ли отдельная GET-ручка для повторной генерации |

Актуализация BFF: пункты 9–11 уже реализованы — role не пересылается,
userId документа берётся из me, ошибки проверки блокировки различаются.
JsonText использует JSON-P, разбор JWT/ответов вынесен в общие классы.
Таблица открытых вопросов выше сохраняет историю предложений, а не означает,
что перечисленные там старые ошибки по-прежнему присутствуют.
Для текущего состояния BFF приоритет имеют обновлённые разделы 1–2 и описание
FRONTEND_INTEGRATION.md. Неподтверждённые межсервисные предложения не объявляются реализованными.
