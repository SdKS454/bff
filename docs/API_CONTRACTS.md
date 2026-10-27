# Кикшеринг: REST-контракты микросервисов (по BFF)

Исходный документ: 16 октября 2026, @heaven. Версия для сдачи, сверка всех пяти сервисов и BFF: 27 октября 2026.

Все разделы ниже сверены с каноническими исходниками: BFF в `src/`, пять
сервисов в `services/`. Полный стенд собран и проверен на настоящих приложениях,
PostgreSQL, Docker Compose и Chromium; HTTP-фикстуры в полной проверке не используются.
Публичные адреса localhost:8080–8085, контекст `/`. Инструкция запуска —
[local-stack/README.md](../local-stack/README.md).

## 1. Обзор и общие соглашения

Исходный MD сохранён побайтово в docs/archive. Все 21 тема обсуждения сохранены
в §5; исторические предложения не объявляются действующими endpoint.
Актуальными считаются версии `services/`, а не первоначальные архивы команд
и не старые временные handoff-сборки. «Реализовано» подтверждено кодом/запуском;
Исторические предложения в §5 отделены от принятых решений сдаваемой версии.

Оплата по подтверждению пользователя — **учебная без банковского списания**.
Тариф хранит Ride: 50 ₽ за старт + 8 ₽ за начатую минуту, минимум одна минута.
Снимок тарифа записывается в поездку и не меняется после редактирования rates.
Настоящий платёжный провайдер не подключён. Для сдаваемого учебного стенда
выбрана евклидова сетка: дробные X/Y от -1000000 до 1000000 включительно,
distance в единицах сетки. Это соответствует примерам самокатов и формуле nearest
исходного контракта. GPS/географические градусы не смешиваются с сеткой.

| Сервис | Порт по умолчанию | Свойство в BFF | Вызывается из BFF |
| --- | --- | --- | --- |
| User Service | 8081 | `user.service.url` | да |
| Moderation Service | 8082 | нет в BFF; `MODERATION_BASE_URL` в готовом User; MODERATION_SERVICE_URL в Documents | **нет прямых REST-вызовов BFF**; User и Documents вызывают status; BFF задаёт URL панели |
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
- **UUID:** id пользователя, поездки, тарифа и sourceId документа — строки UUID, не числа. numberPlate остаётся строкой номера.
- **Авторизация:** `Authorization: Bearer <JWT>`. BFF принимает JWT из этого заголовка либо из HTTP-сессии (кладётся туда после `/api/auth/login`) и **пробрасывает его без изменений в каждый защищённый downstream-вызов**. Каждый сервис сам валидирует токен и достаёт из него `userId`.
- **Проверка токена в BFF:** перед защищёнными `/api/*` BFF вызывает `GET {User}/api/users/validate`. Публичны только POST register/login, GET users/exists. POST auth/logout обрабатывает текущий/истёкший токен сам, без предварительного validate. Любой 2xx validate — успех; отказ — 401, недоступность/5xx — 503. Общего публичного исключения `/api/auth/*` нет.
- **Формат ошибки:** `{"error": "текст"}`. Обычные proxy-вызовы сохраняют статус и тело downstream. При разборе успешного login/me, проверке блокировки/PDF и при logout BFF может сформировать свою ошибку; поэтому «как есть» не означает все без исключения ответы.
- **Типы/размер:** JSON-тело BFF — до 64 KiB, объект со строгими типами; JSON-числа координат должны быть конечными и находиться в диапазоне [-1000000, 1000000]. Готовый User принимает JSON-тело до 16 KiB (413 при превышении). Несоответствующий Content-Type API-запроса с телом — 415.
- **Заголовок и сессия:** явный Authorization имеет приоритет. Некорректный явный заголовок не заменяется привилегированным JWT сессии. Сессия BFF — HttpOnly, только cookie, без session ID в URL.
- **Origin:** ScootX работает с BFF с одного origin. BFF отклоняет межсайтовые write-запросы. Для прямых запросов панели Moderation к User его CORS_ALLOWED_ORIGINS должен включать `http://localhost:8082`; API получает Bearer, не cookie BFF.
- **Недоступность сервиса:** BFF отвечает 503. Таймауты BFF: подключение 3 с, ответ 10 с.
- **Именование полей:** camelCase. Координаты — `posX`, `posY` (в ТЗ они названы `posx`, `posy`; см. решения в §5).
- **Даты:** `birthDate` — `YYYY-MM-DD`; метки времени — ISO-8601 с UTC/offset, не массив чисел.

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
| 17 | `GET /api/rates` | да | Ride: активные тарифы |
| 18 | `PUT /api/rides/{id}/position` | да | Ride: обновление позиции владельцем ACTIVE |
| 19 | `GET /api/documents?limit=50&offset=0` | да | Documents: история собственных генераций |
| 20 | `GET /api/documents/{id}` | да | Documents: собственные метаданные |
| 21 | `GET /api/documents/{id}/download` | да | Documents: повторный PDF из снимка |
| 22 | `DELETE /api/documents/{id}` | да | Documents: soft delete |

BFF не вызывает REST API Moderation напрямую. Браузер ADMIN переходит на страницу панели, а User получает блокировки у Moderation. Это разные виды взаимодействия.

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
// Ответ 200
[ { "numberPlate": "A123BC", "status": "READY", "charge": 87, "posX": 10, "posY": 25 } ]
```

### 2.6 Ближайшие самокаты

`POST /api/scooters/nearest` — тело с `posX` и `posY`, оба обязательны, иначе `400 {"error":"Нужны posX и posY"}`. `GET` на этот путь → 405. BFF проверяет конечные JSON-числа и передаёт их без округления до int; строки, null и объекты вместо обязательных координат дают 400.

```json
// Запрос
{ "posX": 10, "posY": 24 }
// Ответ 200, отсортирован по distance возрастающей
[ { "numberPlate": "A123BC", "status": "READY", "charge": 87, "posX": 10, "posY": 25, "distance": 1.0 },
  { "numberPlate": "B777OP", "status": "READY", "charge": 64, "posX": 12, "posY": 30, "distance": 6.3 } ]
```

Текущий ScootX читает `numberPlate`, `status`, `charge`, `posX`, `posY` и при наличии `distance`. Старый fallback `number_plate` больше не используется: актуальное имя — `numberPlate`. distance выражен в единицах учебной сетки; интерфейс показывает «ед. сетки».

### 2.7 Самокат по номеру

`GET /api/scooters/{numberPlate}` — номер уже декодирован контейнером Servlet и кодируется ровно один раз при вызове Scooters. Ответ — один объект самоката той же формы, без `distance`; 404, если не найден.

### 2.8 Начало поездки

`POST /api/rides`. Порядок внутри BFF: (1) `GET {User}/api/users/me` → `userId`; (2) `GET {User}/api/users/{userId}/blocked`; если заблокирован — `403 {"error":"Пользователь заблокирован"}`; (3) `POST {Ride}/api/rides`. `userId` в Ride не передаётся — Ride берёт его из JWT.

```json
// Запрос
{ "numberPlate": "A123BC", "rateId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "posX": 10, "posY": 24 }
// Ответ 201
{ "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "userId": "550e8400-e29b-41d4-a716-446655440000", "numberPlate": "A123BC", "rateId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "status": "ACTIVE",
  "startedAt": "2026-10-16T07:40:00Z", "startPosX": 10, "startPosY": 24 }
```

`rateId` — необязательный UUID (может быть null; ScootX его сейчас не шлёт). posX/posY при старте API тоже допускают null, но форма ScootX просит обе координаты. numberPlate обязателен. Тариф определяет Ride; null rateId выбирает активный default из GET /api/rates. При deleted=true BFF также запрещает старт (403). Недоступность проверки бана → 503, некорректный ответ → 502, до Ride запрос не доходит.

### Обновление позиции активной поездки

`PUT /api/rides/{id}/position`, тело `{"posX":12.125,"posY":16.75}` → 204.
Доступ только владельцу ACTIVE, неверные координаты → 400, чужая → 403,
отсутствующая → 404, завершённая → 409. Ride сохраняет lastPosX/lastPosY/
lastPositionAt и передаёт позицию Scooter со служебным ключом. Finish задаёт
окончательную позицию отдельно. Начало/тариф от position не меняются.

### 2.9 Завершение поездки

`PUT /api/rides/{id}/finish` — `{id}` должен быть UUID, иначе 404. Обе конечные числовые координаты обязательны; при ошибке — 400 до вызова Ride. Владельца и состояние поездки проверяет Ride.

```json
// Запрос
{ "posX": 15, "posY": 30 }
// Ответ 200
{ "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "status": "FINISHED", "finishedAt": "2026-10-16T08:05:00Z",
  "durationSeconds": 1500, "amount": 250.00, "currency": "RUB" }
```

### 2.10 Оплата поездки

`POST /api/rides/{id}/pay` — без тела либо с пустым объектом (BFF шлёт `{}`). id должен быть UUID, иначе 404. Владельца, состояние и идемпотентность проверяет Ride. Фронтенд не устанавливает PAID локально и не делает расчёт стоимости вместо Ride.

```json
// Ответ 200
{ "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "status": "PAID", "amount": 250.00, "currency": "RUB", "paidAt": "2026-10-16T08:06:10Z", "paymentMode": "EDUCATIONAL" }
```

### 2.11 История поездок

`GET /api/rides` — поездки текущего пользователя; параметров нет. BFF сначала получает `userId` из User Service, потом вызывает `GET {Ride}/api/rides?userId={userId}`. Неизвестный хвост пути, например `GET /api/rides/любой-путь`, возвращает 404. Клиентский userId в query не заменяет текущего пользователя.

```json
// Ответ 200
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

POST `/api/auth/logout` не требует предварительного validate и вызывает User POST `/api/users/logout` с текущим JWT,
затем закрывает сессию BFF; успешный ответ — 204. POST `/logout` делает то же
для HTML-формы, затем отвечает 303 с Location `/login.jsp` при корневом контексте.
GET `/logout` показывает подтверждение и ничего не меняет.

Без JWT повторный выход допустим. 401 User при отзыве означает, что токен уже
непригоден. Если User недоступен/отзыв отклонён, сессия BFF закрывается, но BFF
возвращает ошибку, не заявляя успешный отзыв. Исправленный Moderation также вызывает User validate на каждом защищённом
запросе и отвергает отозванный JWT. Прочие сервисы должны отдельно подтвердить
проверку отзыва, если им нужен немедленный общий logout.

Выход из исправленной панели Moderation вызывает непосредственно User
POST `/api/users/logout`, очищает sessionStorage и открывает BFF login.jsp.
Сессия BFF физически может ещё существовать, но её JWT уже отозван и BFF
возвращает 401. При сбое отзыва панель показывает ошибку и предлагает повторить выход.

### 2.14 Тарифы и настройки интерфейса

GET `/api/rates` — защищённый прокси BFF в Ride: активные тарифы с id, name,
unlockFee, pricePerMinute, currency, default, paymentMode. ScootX перед стартом
получает default-тариф и показывает цену старта/начатой минуты. Ранее пример
50/8 не был действующим тарифом; 26 октября он явно принят для учебного стенда
и теперь хранится в Ride DB. Не смешивать это с настоящим банковским списанием.



GET `/api/config` защищён JWT/сессией и возвращает:

```json
{"moderationPanelUrl":"http://localhost:8082/admin/users.html"}
```

Роли не выдаются этим endpoint: доступ к админским API проверяют сами сервисы.
Вне localhost настройте доверенный HTTPS URL панели.

Профиль ScootX только для чтения, write API нет. Избранное — номера самокатов
только на устройстве, отдельно по userId; серверного endpoint избранного нет.
Координаты учебной сетки вводятся вручную; GPS-кнопка удалена. Каталог требует входа.
Локальные пользователи, запас хода 30 км и имитация перемещения/разряда
из старого макета не используются. Учебные тариф/оплата теперь реализованы на
стороне Ride по подтверждению пользователя, с явной пометкой; это не demo-storage.js.
Время активной поездки может отображаться по startedAt, но сумму считает Ride.

### Коды ответов BFF

| Код | Когда |
| --- | --- |
| 400 | некорректный JSON/поля; нет `login` или `posX`/`posY` |
| 401 | нет JWT или токен отвергнут; 5xx validate не маскируется как 401 |
| 403 | явный бан/удалённый пользователь запрещает старт; также запрет Origin или отказ downstream в доступе |
| 404 | неизвестный путь внутри `/api/*` |
| 405 | неподдерживаемый метод, в том числе GET `/api/scooters/nearest` |
| 415 | тело API-запроса отправлено не как application/json |
| 502 | некорректный ответ User/статуса блокировки или неправильный PDF |
| 503 | сервис недоступен или таймаут |
| 413 | слишком большое тело на стороне User; BFF собственный лимит тела сейчас сообщает как 400 |
| любой другой | обычный proxy сохраняет ответ downstream; исключения разбора/проверок и logout описаны выше |

## 3. Контракты для команд сервисов (BFF → сервис)

Разделы 3.1–3.5 описывают фактические API пяти сервисов из `services/`.
В защищённых вызовах используется Authorization: Bearer JWT; ошибки JSON.
BFF непосредственно вызывает только часть User API. Исправленная админка Moderation
обращается к админским User endpoints напрямую с Bearer и разрешённым CORS.

### 3.1 User Service (`:8081`)

**Реализовано в присланном User.** Пути относительны к `http://localhost:8081`;
для этих URL User разворачивается в корне (например ROOT.war), либо его context
path явно добавляют в `user.service.url` BFF.

| Эндпоинт | Доступ / тело | Фактический ответ / использование |
| --- | --- | --- |
| `POST /api/users/register` | публичный; login, password, fullName, birthDate, avatarUrl; правила §2.1. BFF не пересылает role | 201 + user; роль нового пользователя всегда USER. 409 — занятый нормализованный логин |
| `POST /api/users/login` | публичный; только login и password | 200 accessToken, expiresInSeconds, user. 401 — неправильные данные/удалённый пользователь, 503 — недоступен Moderation/БД |
| `POST /api/users/logout` | текущий Bearer | 204 — токен отозван; прямой повтор с уже отозванным JWT → 401 (BFF считает это завершённым выходом) |
| `GET /api/users/exists?login=` | публичный, корректный логин | 200 `{"exists": true}` или false; невалидный/пустой логин → 400 |
| `GET /api/users/validate` | JWT; проверяет подпись/issuer/exp, запись токена, текущего пользователя и роль | 204 — валиден, 401 — недействителен/отозван/истёк. Не вызывает Moderation; БД недоступна → 503 |
| `GET /api/users/me` | JWT | 200 user; blocked запрашивается у Moderation, его недоступность → 503 |
| `GET /api/users` | ADMIN | 200 массив активных пользователей: `[{"id":"UUID","login":"ivan","fullName":"Иван Петров"}]`; с USER → 403 |
| `GET /api/users/{id}` | сам пользователь или ADMIN; UUID | 200 user; нет активного пользователя → 404, чужой для USER → 403. Также запрашивает статус Moderation |
| `GET /api/users/{id}/blocked` | сам пользователь или ADMIN; UUID | 200 `{"blocked": true}` или false. Нет пользователя → 404; ошибки Moderation обрабатываются ниже |
| `POST /api/users/{id}/ban` | ADMIN; `{"reason":"RIDE_DANGEROUS"}` | 200 статус Moderation; проверяет существование активного пользователя и пересылает reason. Имя причины проверяется также Moderation |
| `POST /api/users/{id}/unban` | ADMIN; тело не требуется | 200 статус Moderation; проверяет пользователя, затем проксирует unban |
| `GET /api/users/blocked` | ADMIN; query limit/offset | 200 массив из Moderation; limit по умолчанию 100, диапазон 1–500; offset по умолчанию 0, неотрицательный int. Некорректные параметры → 400 |
| `GET /api/users/reasons` | ADMIN | 200 массив причин из Moderation |

Нет JWT → 401, нет требуемых прав → 403. Во внешний API BFF вынесены me/exists
из GET users и register/login/logout из auth; me защищён JWT; **проксирование админских User
маршрутов в BFF не реализовано**: панель обращается к User напрямую. Решение и исходное предложение сохранены в п.15.
Изменения профиля и публичного API назначения ADMIN в полученном User нет.
ADMIN назначается оператором через CLI PromoteAdmin; старые токены пользователя
при повышении роли отзываются. Публичная регистрация не повышает роль.

User:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "login": "ivan", "role": "USER", "fullName": "Иван Петров",
  "birthDate": "2000-05-17", "avatarUrl": null, "deleted": false, "blocked": false
}
```

При login BFF требует непустой accessToken, целочисленный expiresInSeconds
от 1 до Integer.MAX_VALUE и user с UUID id; иначе 502. Остальные отсутствующие
поля профиля при разборе могут стать null/false, но сервисам следует выдавать
полный объект выше. Удалённый пользователь из login-ответа не принимается (403).
BFF совместим также со старым булевым ответом blocked `true`/`false`, однако
готовый User выдаёт объект. Пустой/неверный успешный blocked → 502; 5xx/сеть → 503;
только явный blocked=true становится запретом старта 403.

**JWT:** только User выдаёт RS256. Claim userId — UUID, role — USER/ADMIN;
также есть sub=userId, jti (UUID токена), iss, iat, exp. Issuer по умолчанию
`scooter-user-service`, TTL по умолчанию 3600 секунд. Приватный ключ остаётся
только у User; публичный ключ передаётся сервисам с локальной проверкой.
User дополнительно проверяет хеш/действительность токена в БД. BFF не подписывает
JWT, не хранит собственный JWT-секрет и проверяет токен через validate.
Для немедленного учёта отзыва другим сервисам одной проверки подписи недостаточно.

**User → Moderation (реализовано в User):** исходный Bearer передаётся без
изменений; status/ban/unban должны вернуть объект с тем же userId и boolean
blocked, списки/reasons — массивы. Таймаут/сеть, 429 и 5xx → 503; неверный JSON,
неверный userId/blocked, неожиданный redirect/404 → 502. 400/401/403/409
Moderation сохраняются как JSON-ошибка. blocked не хранится в User DB.
Новый пользователь при регистрации получает blocked=false без вызова Moderation
(новый UUID, JWT ещё нет). Login сначала сохраняет токен, затем вызывает status;
если Moderation не ответил корректно, новый токен отзывается, login не объявляется успешным.
me/byId/blocked вызывают Moderation; validate — нет. Нельзя делать цикл
Moderation status → полный User profile → Moderation status.

**Запуск User:** PostgreSQL, схема users/tokens из его архива, JWT-пара ключей.
Обязательные переменные: DB_URL, DB_USER, DB_PASSWORD, JWT_PRIVATE_KEY_PATH,
JWT_PUBLIC_KEY_PATH; значения секретов вводятся вне документа. Адрес Moderation —
MODERATION_BASE_URL, по умолчанию `http://localhost:8082` (без завершающего `/`).
Для прямых browser-вызовов панели User CORS_ALLOWED_ORIGINS по умолчанию включает
`http://localhost:8082`; при другом origin его нужно настроить. BFF своей БД не имеет.

### 3.2 Moderation Service (нет прямого REST-клиента BFF)

Прямого REST-клиента Moderation в BFF нет; отдельно настроен URL страницы панели для browser-редиректа ADMIN. Согласно текущему коду «забанен ли пользователь» выясняется через User Service (`/api/users/{id}/blocked`), а тот спрашивает Moderation — это соответствует формулировке «BFF обращается через users». Вызовы из User и исправленного Documents (GET status) реализованы и проверены с настоящим Moderation. Порт локального стенда — `8082`; готовый User читает адрес из MODERATION_BASE_URL; адрес для Documents настраивает его команда. У BFF есть только отдельная настройка URL страницы панели.

В готовом User уже есть GET `/api/users`, GET `/api/users/{id}`, ban/unban/blocked/reasons (§3.1). Совместимость ответов проверена с исправленным Moderation. Существование пользователя перед ban/unban проверяет User; не следует вызывать из status полный User profile, чтобы не создать цикл.

**Проверена актуальная версия services/moderation.** Для полного стенда собирается
этот исходник. Исторический патч исходного архива сохранён в integrations/moderation.
В исходном архиве сервер только декодировал JWT: можно было подделать ADMIN,
а выход не прекращал доступ к Moderation. Патч добавляет обязательный вызов
User validate и реальный отзыв JWT при выходе из панели.

Список содержит заблокированных пользователей с причиной и сроком, без истории прошлых банов. API и правила:

| Эндпоинт | Тело / параметры | Ответ |
| --- | --- | --- |
| `GET /api/moderation/users/{userId}/status` | — | `200 {"userId": "550e8400-e29b-41d4-a716-446655440000", "blocked": true, "reasonType": "RIDE_DANGEROUS", "reason": "Агрессивное / опасное вождение", "blockedAt": "2026-10-10T12:00:00Z", "blockedUntil": "2026-10-17T12:00:00Z"}` |
| `POST /api/moderation/users/{userId}/ban` (ADMIN) | `{"reason": "RIDE_DANGEROUS"}` | `200` тот же объект статуса |
| `POST /api/moderation/users/{userId}/unban` (ADMIN) | — | `200` объект статуса |
| `GET /api/moderation/users?blocked=true` (ADMIN) | необязательные query `limit` 1–500 (default 50), `offset` ≥ 0 (default 0) | `200` массив объектов статуса только действующих блокировок, новые первыми; пусто — `[]` |
| `GET /api/moderation/reasons` (ADMIN) | — | `200 [{"reasonType": "RIDE_DANGEROUS", "description": "Агрессивное / опасное вождение", "durationSeconds": 604800}]`; `durationSeconds: null` = навсегда |

Правила для Moderation:

- **Токен:** вызывающий сервис пробрасывает в Moderation JWT исходного запроса без изменений (как и остальные сервисы, см. «Общие правила»). В готовом User выбран исходный JWT; Moderation должен валидировать его и извлекать role/userId. Сервисный токен остаётся только альтернативой для будущих межсервисных действий, сейчас User так не вызывает.
- **Доступ к `status`:** ADMIN или сам пользователь (`userId` из JWT совпадает с `{userId}`), иначе `403`. Так Documents может вызывать `status` с JWT пользователя, который запрашивает документ.
- **Причины:** допустимые причины и их сроки заданы справочником Moderation (`GET /api/moderation/reasons`), свободный текст не принимается. В запросе `ban` поле `reason` — имя причины (`reasonType`), например `RIDE_DANGEROUS`; неизвестное или пустое значение даёт `400`. В ответе `reasonType` — имя причины, `reason` — её описание.
- **Срок блокировки:** определяется причиной и фиксируется при `ban`: `blockedUntil = blockedAt + длительность причины`. `blockedUntil: null` — бессрочно, снимается только через `unban`. По истечении срока `status` отвечает `blocked: false`, а запись пропадает из списка.
- **Пользователь без записи:** `status` отвечает `200` с `"blocked": false`; `reasonType`, `reason`, `blockedAt`, `blockedUntil` = `null`. Это значение по умолчанию, а не `404`.
- **`unban`:** пользователь убирается из списка заблокированных, его статус становится `"blocked": false`, а `reasonType`, `reason`, `blockedAt`, `blockedUntil` = `null`. Прошлые причины не сохраняются.
- **Повторные вызовы идемпотентны:** `ban` уже заблокированного → `200` с текущим объектом статуса (причина и сроки не перезаписываются); `unban` незаблокированного → `200` с `"blocked": false`.
- **Список и справочник причин** доступны только ADMIN.
- **Ошибки** (формат `{"error": "..."}`): `400` — `userId` не UUID, некорректное тело, неизвестная причина или параметры `limit`/`offset`, `401` — нет или невалидный токен, `403` — нет прав.
- **Источник правды для `blocked`:** значение хранит и отдаёт Moderation; поле `blocked` в объекте User Service берёт из ответа `status`, а не хранит у себя **[проверено с исправленным Moderation и настоящим User]**.
- **Недоступность Moderation:** User Service должен вернуть `503` из `/api/users/{id}/blocked`, а не «заблокирован»; это согласуется с вопросом №11 **[реализовано в User; ответ 503 при недоступном User также проверен в Moderation]**.

**Проверка JWT в исправленном Moderation:** каждый защищённый запрос вызывает
User `GET /api/users/validate` с исходным Bearer; только 204 допускает запрос.
401 → 401, таймаут/сеть/неожиданный ответ → 503. Redirect не выполняется.
Это обеспечивает проверку RS256, issuer/exp, текущей роли, существования
пользователя и отзыва токена силами User. Декодирование payload само по себе
не считается проверкой. User validate не вызывает Moderation, рекурсии нет.

**Панель:** получает пользователей через User GET `/api/users`, блокировки —
`/api/users/blocked?limit=200&offset=...`, причины — `/api/users/reasons`;
ban/unban вызывает через `/api/users/{id}/ban` и `/unban` (User проверяет
существование пользователя). Все запросы содержат Bearer. Прямого BFF-прокси
для админских действий нет. Fragment accessToken переносится в sessionStorage
и сразу удаляется из адреса. Выход описан в §2.13.

**Запуск Moderation:** Java 17+ / Tomcat 10.1+ с Servlet 6.0; своя PostgreSQL БД,
предварительно выполнить `src/main/resources/schema.sql` из его проекта.
DB_URL/DB_USER/DB_PASSWORD обязательны. USER_BASE_URL по умолчанию
`http://localhost:8081` — адрес User для серверной проверки. ADMIN_USER_BASE_URL
задаёт адрес User для браузера (default равен USER_BASE_URL), BFF_LOGIN_URL —
адрес входа (default `http://localhost:8080/login.jsp`). Настройки страницы
возвращает `/admin/js/config.js`; секретов в ответе нет. В локальном стенде все
три WAR разворачиваются в корне на разных портах. Никакие ключи JWT в Moderation
не копируются: выбран validate. У User должен быть разрешён CORS origin панели.

### 3.3 Scooters Service (`:8083`)

**Реализовано и проверено в services/scooter.** Внешние вызовы требуют JWT,
проверяемый через User validate; отозванный JWT даёт 401.

| Эндпоинт | Запрос | Ответ |
| --- | --- | --- |
| `GET /api/scooters` | необязательный status=READY/IN_RIDE/CHARGING/REPAIR | 200 JSON-массив неудалённых самокатов |
| `POST /api/scooters/nearest` | posX/posY — обязательные конечные JSON-числа | 200 массив только READY по возрастанию distance; при равенстве по numberPlate |
| `GET /api/scooters/{numberPlate}` | URL-encoded номер | 200 объект, 404 если нет/удалён |
| `POST /api/scooters/{numberPlate}/reserve` | служебный X-Service-Key; rideId/userId — UUID | атомарно READY → IN_RIDE, 200; занято/заряд 0 → 409 |
| `POST /api/scooters/{numberPlate}/position` | служебный X-Service-Key; rideId, posX/posY | 200 объект самоката; только текущая активная резервация, иначе 409 |
| `POST /api/scooters/{numberPlate}/release` | служебный X-Service-Key; rideId; optional posX/posY (обе или ни одной) | освобождение собственного rideId, 200; чужой номер операции → 409 |

Объект самоката:

```json
{ "numberPlate": "A111AA77", "charge": 95, "status": "READY", "posX": 10.0, "posY": 15.0, "distance": 1.5 }
```

Без nearest distance может быть null. Старые envelopes scooters/scooter и поля
numericPlate/positionX/positionY заменены согласованным wire API; в SQL имена
numeric_plate/position_x/position_y остаются внутренними. Статусы READY,
IN_RIDE, CHARGING, REPAIR. Примерные самокаты добавляются при первой инициализации.
Координаты double precision, nearest использует Math.hypot: исходное предложение
`sqrt((x-posX)^2 + (y-posY)^2)` для условной сетки. Для географических градусов
это не расстояние в метрах. BFF не округляет и не выбирает географическую метрику.
В сдаваемой версии выбрана учебная сетка с диапазоном ±1000000; разряд батареи по расстоянию
не вычисляется. Обе конечные координаты сохраняются при освобождении.

Служебные reserve/release/position доступны только Ride с общим SERVICE_API_KEY,
а не по обычному пользовательскому JWT. Ключ создаётся автоматически в Compose,
не попадает в браузер. Это исключение из правила передачи исходного JWT:
восстановление незавершённой операции должно работать и после отзыва JWT пользователя.
На rideId хранится постоянная запись операции: повтор reserve идемпотентен,
release завершённой операции не освобождает новый rideId. Release до задержанного
reserve фиксирует отмену, и поздний reserve получает 409. Изменение состояния
и записи операции выполняется транзакционно.

### 3.4 Ride Service (`:8084`)

**Реализовано и проверено в services/ride.** Защищённые вызовы валидируют JWT
через User. Бан дополнительно проверяется только при старте; finish/pay/history
не требуют доступного Moderation. Обычный пользователь читает только свои поездки.

| Эндпоинт | Тело / параметры | Ответ |
| --- | --- | --- |
| `POST /api/rides` | `{"numberPlate": "A123BC", "rateId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "posX": 10, "posY": 24}`; `rateId`, `posX`, `posY` могут быть `null` | `201` объект поездки со статусом `ACTIVE`. `409`, если самокат не `READY` или у пользователя уже есть активная поездка |
| `PUT /api/rides/{id}/position` | posX/posY, дробные числа ±1000000 | 204; чужая — 403, не ACTIVE — 409, неверные координаты — 400 |
| `PUT /api/rides/{id}/finish` | `{"posX": 15, "posY": 30}` | `200` поездка со статусом `FINISHED` и рассчитанной `amount`. `404` — нет поездки; `403` — чужая; `409` — другие координаты повторного finish/неподходящее состояние |
| `POST /api/rides/{id}/pay` | `{}` | `200` поездка со статусом `PAID`. Идемпотентно: повтор на оплаченной → `200` с тем же объектом; `409` если поездка ещё не завершена |
| `GET /api/rides?userId=550e8400-e29b-41d4-a716-446655440000` | query `userId` | `200` массив поездок пользователя, новые первыми |

**Реализован для Documents:**
`GET /api/rides/{id}` с Bearer и проверкой владельца. Это сервисный endpoint Ride,
в BFF GET одной поездки не опубликован. В Ride этот endpoint возвращает 200/403/404. Documents вызывает его;
для чека требует PAID, UUID id/userId, numberPlate, startedAt, finishedAt, amount,
currency, paidAt. amount ≥ 0, currency — 3 заглавные латинские буквы,
startedAt ≤ finishedAt ≤ paidAt. Чужой владелец → 403, неоплаченная поездка → 409,
неполный/подменённый успешный ответ Ride → 502. Валюта RUB не подставляется.
GET списка должен возвращать JSON-массив собственных поездок с id/userId,
numberPlate/status/startedAt; PAID должен включать перечисленные платёжные поля.
Эти требования проверены на настоящем Ride и настоящем Documents.

Правила для Ride:

- `userId` при старте берётся из JWT, в теле его нет.
- При `GET ?userId=` нужно сверять `userId` с JWT: BFF не проверяет владельца поездки при `finish`/`pay`, это должен делать Ride.

Пример объекта поездки (поля могут быть null до finish/pay):

```json
{ "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "userId": "550e8400-e29b-41d4-a716-446655440000", "numberPlate": "A123BC", "rateId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "status": "PAID",
  "startedAt": "2026-10-16T07:40:00Z", "finishedAt": "2026-10-16T08:05:00Z",
  "startPosX": 10, "startPosY": 24, "endPosX": 15, "endPosY": 30,
  "durationSeconds": 1500, "amount": 250.00, "currency": "RUB", "paidAt": "2026-10-16T08:06:10Z", "paymentMode": "EDUCATIONAL" }
```

Статусы для клиента: `ACTIVE` → `FINISHED` → `PAID`.
Finish с теми же координатами можно повторить: возвращается сохранённый результат;
с другими координатами — 409. Повтор/конкурентный pay возвращает один paidAt.
Поездки пользователя/самоката ограничены уникальными индексами БД.

`GET /api/rates` доступен в Ride и через BFF. Возвращает массив id/name/unlockFee/
pricePerMinute/currency/default/paymentMode. Если rateId null, Ride выбирает
активный тариф default. Сумма = unlockFee + pricePerMinute × max(1, ceil(seconds/60));
durationSeconds округляется вверх от точного времени поездки. Тариф записывается
снимком unlockFee/pricePerMinute/currency в поездку. По согласованию default 50/8 RUB.

Ride при старте сначала фиксирует STARTING, затем резервирует Scooter и включает
ACTIVE. При сбое сохраняется CANCELLING; фоновая обработка освобождает только
свой rideId. При finish сначала фиксируются время/координаты/сумма и FINISHING,
затем освобождается Scooter и сохраняется FINISHED. Состояния восстанавливаются
после перезапуска. STARTING/CANCELLING не входят в публичную историю; FINISHING
временно выдаётся клиенту как ACTIVE, до завершения восстановления. Повтор не
пересчитывает сумму за время недоступности сервиса.

При PAYMENT_MODE=educational pay сохраняет PAID **без банковского списания**.
Без этой настройки оплата отключена (503). JSON поездки содержит paymentMode
EDUCATIONAL/DISABLED. У поездки сохраняется режим, а при оплате он фиксируется
в БД как EDUCATIONAL: смена конфигурации не убирает пометку старого учебного чека.
В интерфейсе и PDF-чеке учебный режим явно обозначен.

### 3.5 Documents Service (`:8085`)

**Проверена актуальная версия services/documents на основе Documents_for_integration.zip.** Актуальные исходники
находятся в `services/documents`; первоначальный diff сохранён в integrations/documents.
README архива прочитан: публичный ключ User, локальный шрифт с кириллицей и
Ride GET реализованы и проверены в полном стенде. BFF уже
поддерживает три типа, историю, метаданные, повторное скачивание и удаление. Типы:

- `RIDE_PAYMENT` — подтверждение оплаты поездки.
- `USER_BLOCKED` — уведомление о блокировке пользователя.
- `USER_HISTORY` — история поездок за выбранный период.

| Эндпоинт | Тело / параметры запроса | Ответ |
|---|---|---|
| `POST /api/documents/generate` | `userId`, `docType`, `sourceId`, `from`, `to` | `200` + PDF (`application/pdf`) |
| `GET /api/documents` | JWT; limit=50 (1..100), offset=0 (≥0) | `200` массив собственных неудалённых записей, новые первыми |
| `GET /api/documents/{id}` | UUID документа | `200` метаданные; чужой — 403, удалённый/отсутствующий — 404 |
| `GET /api/documents/{id}/download` | UUID документа | `200` PDF из сохранённого снимка; не создаёт новую запись |
| `DELETE /api/documents/{id}` | UUID документа | `204`, soft delete; повтор владельцем — 204, чужой — 403 |

В исправленном Documents JWT проверяется локально: только RS256, issuer,
обязательный exp, UUID userId с тем же sub. Затем до записи метаданных вызывается
User GET `/api/users/validate`: только 204 разрешает продолжение, 401 запрещает,
сбой/неожиданный ответ → 503. Для всех внешних вызовов используется исходный JWT.
userId тела обязан совпадать с userId JWT; клиент BFF сам подставляет его из me.

Тело — JSON-объект до 64 KiB: повторные поля/хвосты/неверные типы/UUID/даты → 400,
большое тело → 413, Content-Type не application/json → 415. На /generate разрешён только POST (иначе 405).
UUID и даты — строки. Ответы PDF и ошибки не кешируются. Ошибки JSON {"error":"..."}.
Успешный неправильный JSON/Content-Type/отсутствующие поля внешнего API → 502;
401/403/404/409 сохраняются, 429/5xx/сеть → 503. Авторизационные данные и тела
внешних ошибок в сообщения не включаются.

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

Для генерации используется `GET /api/moderation/users/{userId}/status` с исходным
JWT (проверено с настоящим исправленным Moderation). userId ответа должен
совпадать, blocked — boolean. При true обязательны reasonType/reason/blockedAt,
blockedUntil может быть null (бессрочно). При blocked=false — `409`.

**USER_HISTORY:**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "docType": "USER_HISTORY",
  "from": "2026-10-01",
  "to": "2026-10-31"
}
```

Все три варианта используют один POST endpoint. При успехе возвращаем `200`,
`Content-Type: application/pdf` и PDF в теле ответа. Через BFF имя скачивания
`document.pdf`; прямой Documents использует ride_payment.pdf, user_blocked.pdf
или user_history.pdf. При ошибке — соответствующий HTTP-статус и `{"error":"..."}`.

`userId` сверяем с JWT. Для `RIDE_PAYMENT` проверяем, что поездка принадлежит пользователю и имеет статус `PAID`. Для `USER_BLOCKED` проверяем принадлежность блокировки пользователю. Для `USER_HISTORY` проверяем корректность периода.

BFF отправляет `docType = RIDE_PAYMENT`; RECEIPT принимает как совместимый alias и преобразует перед вызовом Documents.

**Запросы к другим сервисам:**

| Сервис | Метод | Endpoint | Назначение |
|---|---|---|---|
| Ride | GET | `/api/rides/{rideId}` | Исправленный Documents вызывает; реализовано в Ride и используется Documents |
| Ride | GET | `/api/rides?userId={userId}` | История поездок |
| Moderation | GET | `/api/moderation/users/{userId}/status` | Данные текущей блокировки |
| User | GET | `/api/users/validate` | Проверка отзыва/актуальности JWT до создания метаданных |
| User | GET | `/api/users/{userId}` | Данные пользователя; в User реализован для самого пользователя/ADMIN. Вызов исправленного Documents подтверждён с настоящим User |

Для `USER_HISTORY` Documents получает список поездок и фильтрует его по `from` и `to`.

**Генерация PDF:** три захардкоженных HTML-шаблона (`ride-payment.html`, `user-blocked.html`, `user-history.html`). Данные подставляются в HTML, затем HTML преобразуется в PDF. Файл создаётся только по запросу и не сохраняется.

**БД:** PostgreSQL, одна таблица `documents`. Храним `id`, `user_id`, `document_type`, `source_id`, `period_from`, `period_to`, `status`, `created_at`, `updated_at`, `generated_at`, `is_deleted`, `deleted_at`, `payload_json` (JSONB-снимок исходных данных). Для `user_id` и `source_id` используем UUID. `source_id` заполняется только для `RIDE_PAYMENT`, для остальных типов документов — `NULL`. Поддерживаем статусы `PENDING`, `GENERATED`, `FAILED` и soft delete.

**Фактическая генерация/запуск Documents:** Java 17+ / Servlet 6.0 / Tomcat
10.1, PostgreSQL с отдельной БД и схемой `src/main/resources/schema.sql`.
Обязательны DB_URL, **DB_USERNAME** (отличается от DB_USER в User/Moderation),
DB_PASSWORD, JWT_PUBLIC_KEY_PATH (публичный PEM SubjectPublicKeyInfo User;
альтернатива JWT_PUBLIC_KEY как PEM/base64 DER), PDF_FONT_PATH (локальный TTF
с кириллицей). Приватный ключ сюда не копируется; ключ кешируется, при его
замене перезапустить сервис. JWT_ISSUER default `scooter-user-service` должен
совпадать с User. USER_SERVICE_URL default `http://localhost:8081`,
MODERATION_SERVICE_URL `http://localhost:8082`, RIDE_SERVICE_URL
`http://localhost:8084`. Сервис Documents: 8085, контекст `/` через ROOT.war;
при контексте `/documents` его нужно добавить в document.service.url BFF.
Шрифт не включён в архив: отсутствие/неподходящий шрифт не считается успешной
генерацией. В PDF русский текст проверен извлечением из реального файла.
История фильтруется по startedAt в UTC, обе границы периода включительны;
неверная принадлежность поездки в списке → 502 вместо неполной успешной истории.
Метаданные PENDING → GENERATED/FAILED сохраняются, PDF на диске/в БД не хранится.
GET/DELETE опубликованы и проксируются BFF; история/скачивание/подтверждение удаления доступны
в кабинете ScootX. При GENERATED атомарно сохраняется JSON-снимок данных, а не PDF.
Download рендерит этот снимок: старое уведомление доступно после unban, история
не меняется после новых поездок. Для записей прежней версии без снимка возможна
однократная попытка восстановить данные из текущих источников; если они недоступны,
возвращается ошибка вместо выдуманного документа. PENDING старше двух минут при
чтении истории переводится в FAILED; повтор создаёт новую запись генерации.

### Метаданные истории Documents (также через BFF)

Пример одного элемента массива GET /api/documents и ответа GET по ID:

```json
{"id":"550e8400-e29b-41d4-a716-446655440001","userId":"550e8400-e29b-41d4-a716-446655440000","docType":"USER_HISTORY","sourceId":null,"from":"2026-10-01","to":"2026-10-27","status":"GENERATED","createdAt":"2026-10-27T10:00:00Z","generatedAt":"2026-10-27T10:00:01Z","snapshotAvailable":true}
```

Порядок — createdAt DESC, id DESC. limit/offset вне диапазона → 400. Поле
snapshotAvailable=false возможно у старых записей; payload_json клиенту не выдаётся.
Любой маршрут требует действующий JWT и владельца, включая download/delete.

## 4. Сквозной сценарий аренды

В исходном MD на месте диаграммы был только маркер встроенного содержимого
«6 запросов клиента, 8 вызовов сервисов», самой диаграммы/её вложения в файле нет.
Ниже восстановлено текстовое описание по контракту и коду; фиксированное число
внутренних вызовов не указывается, поскольку защищённые запросы включают validate.

1. При необходимости регистрация, затем отдельный login. User получает blocked
   у Moderation и возвращает JWT/user; BFF создаёт HttpOnly-сессию.
2. Просмотр каталога/nearest/карточки через BFF, данные приходят от Scooters.
3. Старт POST rides: BFF проверяет validate, получает me и blocked у User.
   При явном бане — 403 и Ride не вызывается; при ошибке проверки нет ложного
   blocked=false/успешного старта. Ride определяет владельца по JWT, проверяет
   готовность самоката/активную поездку. Состояние меняется через служебные reserve/release Scooter (п.3).
4. Завершение PUT finish: Ride проверяет владельца, рассчитывает amount и
   возвращает FINISHED. BFF не рассчитывает сумму самостоятельно.
5. Оплата POST pay: Ride проверяет владельца/состояние, возвращает PAID при успехе;
   повтор должен быть идемпотентным. При отказе UI не объявляет поездку оплаченной.
6. Чек POST documents/generate: BFF добавляет userId из me; исправленный Documents
   проверяет sourceId, владельца и PAID, получить данные Ride, вернуть PDF.
   GET конкретной поездки реализован и проверен в Ride.
7. Выход POST auth/logout: User отзывает JWT, BFF закрывает сессию. Исправленные
   Moderation и Documents проверяют отзыв через User validate. Scooter и Ride также немедленно учитывают отзыв через User validate.

Диаграмма успешного сценария USER (ошибки и ADMIN-переход описаны выше):

```mermaid
sequenceDiagram
    actor Client as Браузер
    participant BFF
    participant User
    participant Mod as Moderation
    participant Scooters
    participant Ride
    participant Docs as Documents
    Client->>BFF: POST /api/auth/login
    BFF->>User: POST /api/users/login
    User->>Mod: GET /api/moderation/users/{userId}/status (новый JWT)
    Mod->>User: GET /api/users/validate (исходный JWT)
    User-->>Mod: 204
    Mod-->>User: Статус блокировки
    User-->>BFF: accessToken, expiresInSeconds, user
    BFF-->>Client: JSON + HttpOnly session cookie
    Note over BFF,User: Перед каждым защищённым API BFF вызывает User validate
    Client->>BFF: POST /api/scooters/nearest
    BFF->>Scooters: POST nearest (исходный JWT)
    Scooters-->>BFF: Список самокатов
    BFF-->>Client: Список самокатов
    Client->>BFF: POST /api/rides
    BFF->>User: GET me, GET users/{id}/blocked
    User->>Mod: Запросы status
    Mod-->>User: Статусы блокировки
    User-->>BFF: me и blocked
    Note over BFF,Ride: Только если проверка успешна и blocked=false
    BFF->>Ride: POST rides (JWT)
    Ride-->>BFF: ACTIVE
    BFF-->>Client: ACTIVE
    Client->>BFF: PUT /api/rides/{id}/finish
    BFF->>Ride: PUT finish (JWT, координаты)
    Ride-->>BFF: FINISHED + amount
    BFF-->>Client: FINISHED + amount
    Client->>BFF: POST /api/rides/{id}/pay
    BFF->>Ride: POST pay (JWT)
    Ride-->>BFF: PAID
    BFF-->>Client: PAID
    Client->>BFF: POST /api/documents/generate
    BFF->>User: GET me
    User-->>BFF: me (текущий userId)
    BFF->>Docs: POST generate (JWT, userId, sourceId)
    Docs->>User: GET validate, GET users/{id} (исходный JWT)
    User-->>Docs: 204, профиль
    Docs->>Ride: GET rides/{id} (проверка владельца)
    Ride-->>Docs: Настоящая поездка PAID, владелец, платёжные поля
    Note over Docs,Ride: Documents проверяет владельца и оплату
    Docs-->>BFF: PDF
    BFF-->>Client: PDF
    Client->>BFF: POST /api/auth/logout
    BFF->>User: POST /api/users/logout
    User-->>BFF: 204, токен отозван
    BFF-->>Client: 204, сессия закрыта
```

## 5. Все 21 вопрос исходного документа: актуальные статусы

Номера и исходные предложения сохранены. Все 21 тема имеют решение для сдаваемой
версии; исходный вариант может быть заменён явно выбранной реализацией.
Дополнительных обязательных требований преподавателя нет (подтверждено пользователем).

| № | Тема и сохранённое предложение | Решение на 27 октября |
| --- | --- | --- |
| 1 | **Moderation через User или напрямую из BFF.** В исходнике предлагалось User → Moderation; альтернативой был прямой клиент BFF с moderation.service.url | Выбран и реализован User → Moderation; BFF не вызывает REST Moderation напрямую. Отдельный URL панели для ADMIN — не REST-клиент. Status API проверен с настоящим User и исправленным Moderation |
| 2 | **Координаты.** В ТЗ posx/posy — int, геолокация — дробные градусы. Предложение: number (int/float); если команды требуют int, согласовать преобразование | Закрыто для сдачи: дробная евклидова учебная сетка, X/Y в диапазоне ±1000000, distance в единицах сетки. GPS-кнопка удалена; int-округления нет |
| 3 | **Смена статуса самоката.** READY → IN_RIDE → READY. Предложено Ride → `PUT /api/scooters/{numberPlate}/status` с `{"status":"IN_RIDE"}` и атомарным отказом 409, если не READY | Реализована прямая связь Ride → Scooter через служебные reserve/release, уникальный rideId, транзакции, постоянные отмены и восстановление; предложенный PUT status не публикуется |
| 4 | **Данные для чека.** Documents должен получить поездку. Предлагался `GET /api/rides/{id}` в Ride; альтернатива — сбор данных в BFF и расширение тела | Закрыто: BFF отправляет идентификаторы, Documents получает настоящую поездку через Ride GET и проверяет владельца/оплату |
| 5 | **Тарифы.** Предложен `GET /api/rates` в Ride → `[{"id":"6ba7b810-9dad-11d1-80b4-00c04fd430c8","name":"Минутный","pricePerMinute":8.0,"unlockFee":50.0}]`, прокси BFF `GET /api/rates`; null rateId → тариф по умолчанию | Закрыто: rates реализованы в Ride и BFF, null rateId выбирает default. Пользователь подтвердил учебный тариф 50/8 и отсутствие настоящего списания |
| 6 | **Статусы поездки.** Вместо старого угадывания ACTIVE/IN_PROGRESS/STARTED предложены ACTIVE, FINISHED, PAID; CANCELLED — только если понадобится | В BFF/UI используется ACTIVE → FINISHED → PAID. Ride выдаёт эти статусы; внутренние восстановительные состояния не объявлены пользовательским CANCELLED |
| 7 | **Трекинг времени/координат.** Предложено `PUT /api/rides/{id}/position` с posX/posY → 204 и прокси BFF; альтернатива — исключить трекинг из задания | Реализовано в Ride и BFF: PUT position → 204, только владелец ACTIVE; последняя позиция/время сохраняются, Scooter обновляется служебным запросом. Сумму считает сервер, GPS не имитируется |
| 8 | **Finish и pay вместе или раздельно.** Предложено два шага: finish считает, pay проводит оплату | BFF/ScootX реализуют два отдельных шага и явное подтверждение оплаты. Ride соблюдает FINISHED → PAID и идемпотентный повтор учебного pay, что проверено конкурирующими запросами |
| 9 | **Роль при регистрации.** Убрать доверие role клиента; назначать USER на сервере | Закрыто в BFF и готовом User: роль не пересылается BFF, User принудительно создаёт USER |
| 10 | **Чужой userId/sourceId и поездки.** Предложено userId из me; проверять владельца в Documents/Ride | BFF подставляет userId из me. Закрыто: Documents и настоящий Ride проверяют владельца, оплату и платёжные поля; прямые попытки чужого доступа проверены |
| 11 | **Ложный 403 при сбое blocked.** Предлагалось разделить ошибку проверки и явный бан | Закрыто в BFF: недоступность/5xx → 503; неверная успешная структура → 502; только true → запрет старта 403. User при сбое Moderation также возвращает ошибку, а не выдуманный blocked |
| 12 | **Нагрузка validate и подпись JWT.** Предлагались RS256 с публичным ключом у сервисов и validate для отзыва | User выдаёт RS256; BFF, исправленные Moderation и Documents вызывают validate на защищённых API. Scooter и Ride также проверяют токены через User validate. Отзыв проверен во всех шести приложениях |
| 13 | **История генераций Documents, без хранения PDF.** Предложен GET `/api/documents`; повторную выдачу согласовать отдельно | Реализовано в Documents/BFF и кабинете: постраничный GET истории, метаданные, soft delete; PDF не сохраняются, исходные данные фиксируются JSON-снимком |
| 14 | **User ↔ Moderation: JWT, источник blocked, ошибки.** Предлагались исходный JWT, blocked только в Moderation, 503 при сбое | На стороне User реализовано: исходный JWT; blocked запрашивается, не хранится в User; сбой → 503, неверный ответ → 502. Совместимость userId/boolean blocked, бан/разбан и отзыв JWT проверены с исправленным Moderation на PostgreSQL |
| 15 | **Админские ban/unban/blocked и справочник причин.** Исходно предлагались User-прокси и аналогичные BFF-маршруты, reason — имя из справочника | Закрыто: User-прокси ban/unban/blocked/reasons, ADMIN; панель вызывает User с Bearer/CORS. Дублирующий BFF-прокси выбранной панели не требуется |
| 16 | **Роли USER/ADMIN и claim role** | В готовом User и BFF-входе используются USER/ADMIN, роль есть в JWT. Исправленный Moderation проверяет JWT через User и применяет ADMIN/self; Scooter/Ride/Documents также проверяют JWT и права; служебный ключ разрешён только для операций восстановления |
| 17 | **Документ USER_BLOCKED без ID блокировки.** Предложено GET Moderation `/api/moderation/users/{userId}/status`; sourceId не нужен, reason/blockedUntil идут в документ; false → 409 | Сохранено как требование Documents/Moderation. BFF поддерживает docType и отправляет userId из me, sourceId=null. Исправленный Documents проверен с настоящим Moderation: PDF о текущем бане, 409 без бана, UUID/sourceId и русский текст |
| 18 | **Бессрочные причины (например PAYMENT_DEBT) и снятие после оплаты.** По исходному правилу снимаются вручную unban; предложена автоматизация через Ride/User и сервисный токен | Закрыто по исходному правилу: бессрочный PAYMENT_DEBT снимает ADMIN вручную. Учебная оплата не отменяет произвольный бан; автоматизация не входит в выбранный контракт |
| 19 | **Данные оплаты для чека.** Предложен `GET /api/rides/{rideId}` в Ride с status, amount, currency, paidAt | Закрыто в Ride/Documents: GET одной поездки и платёжные поля реализованы и проверены; BFF отдельный GET одной поездки не публикует |
| 20 | **HTML → PDF.** Возможность OpenHTMLtoPDF вместо Apache POI | Присланный Documents использует Thymeleaf + OpenHTMLtoPDF; три настоящих PDF с кириллицей проверены. Используется реализация из присланного Documents; генерация и кириллица проверены в полном стенде |
| 21 | **Повторное скачивание PDF по ID.** Исходно PDF не сохраняется; возможна повторная генерация | Реализовано: GET /api/documents/{id}/download в Documents и BFF рендерит сохранённый JSON-снимок без новой записи. PDF не хранится; владелец/JWT/soft delete проверяются |

## 6. Что изменено относительно присланного файла

- Сохранены общие правила, BFF API, контракты всех пяти сервисов, поля JSON,
  требования к PDF/таблице documents и все 21 тема обсуждения.
- Устаревшее поведение заменено явно: общий публичный auth/*, role клиента,
  числовые/непроверенные типы входных полей, чужой userId документа, ложный бан, угадывание
  статусов/метры во фронте, локальный GET-logout больше не являются текущим API.
- Добавлены реальные User endpoints/права, обязательная дата и лимиты,
  RS256/отзыв, вход ADMIN, корневой BFF на 8080, ответы по ошибкам и UI-ограничения.
- 23 октября добавлены результаты проверки Moderation: обязательная проверка
  JWT/отзыва через User, выход панели с отзывом, работа панели через User API,
  единый limit до 500 и настройки запуска отдельного сервиса.
- Исходный alias RECEIPT сохранён как совместимость; старые status/userId из
  клиентского PDF-запроса описаны как неиспользуемые, не как текущие фильтры.
- Вместо недоступного встроенного виджета дан самостоятельный сценарий/диаграмма.
  Старая нумерация «шаг 6/8/10/12» не используется, поскольку самого виджета в MD нет.
- JSON-P вместо ручного парсинга, общий Tokens/BaseServlet — внутренние изменения
  BFF; они не требуют изменений wire API остальных сервисов.

Это самостоятельный файл для рассылки. Для аудита исходник целиком и без
изменений сохранён в репозитории в docs/archive/API_CONTRACTS_ORIGINAL_2026-10-16.md;
исторические утверждения исходника не следует применять вместо текущих разделов.
Готовность остальных микросервисов не выводится из документа: нужны их код/запуск
и интеграционная проверка. Подтверждены BFF, User, исправленные Moderation и Documents, включая совместный
запуск на пяти отдельных PostgreSQL БД, сценарий ADMIN и три скачивания PDF из ScootX.
Для запуска использовать согласованные исходники services/ и compose.yaml; исходные архивы команд содержали несовместимости.
Все пять сервисов получены, приведены к единому API и проверены вместе с BFF без HTTP-фикстур.


## 7. Полный локальный запуск

`docker compose up --build -d` из корня запускает все шесть приложений. Compose
создаёт пять БД одного PostgreSQL (user/moderation/scooter/ride/documents), ключи
и данные примеров. Каждый сервис — отдельный контейнер и ROOT-приложение.
Все настройки и публичные порты согласованы в compose.yaml. User хранит
приватный ключ, Documents получает только public.pem; служебный ключ Ride/Scooter
хранится отдельно. Ключи/БД не меняются при обычном down/up. Шрифт PDF уже есть
в образе. Для ручного Tomcat и переменных окружения см. local-stack/README.md.
Для проверки с намеренными сбоями используются scripts/check-full-stack.cjs и
scripts/check-submission.cjs и scripts/check-stack-recovery.cjs только в отдельном проекте kicksharing-full-check.

При каждом обновлении Compose запускает migrate: версии SQL в local-stack/migrations,
транзакции, advisory lock и SHA-256 в schema_migrations. Миграции не удаляют данные.
Изменять уже применённый файл нельзя: для следующего изменения добавляется новый.
Проверки сдаваемой версии: [SUBMISSION_REPORT.md](SUBMISSION_REPORT.md),
ручной прогон: [SUBMISSION_CHECKLIST.md](SUBMISSION_CHECKLIST.md).
