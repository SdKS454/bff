# Полная проверка API, URL и админки

Обновлено для интеграции админки самокатов от28.10.2026 (включая 16+, отзывы и работу без геолокации). Реестр: **68 операций API**, **350 пунктов проверки**. Это план ручной проверки, а не заявление, что все пункты уже проверены на вашем ПК. В CSV поля результата пустые: заполняйте PASS / FAIL / BLOCKED, фактический код и комментарий. Проверки через BFF и напрямую в микросервисах нужны обе.

## Подготовка

1. Поднимите все шесть приложений с контекстом `/`: BFF **8080**, User **8081**, Moderation **8082**, Scooter **8083**, Ride **8084**, Documents **8085**. PostgreSQL — ваш реальный порт (5432/7689), к HTTP-портам это не относится. JDK 17, проверенный Tomcat 10.1; миграции должны быть применены.
2. Подготовьте два разных тестовых USER (A и B) и отдельный ADMIN. Не используйте реальные аккаунты для блокировок и разрушительных негативных проверок. `loginA`/`loginB`/`loginAdmin` должны быть разными свободными логинами. Пароль примеров: `Check-password-2026!`. ФИО правильного формата, дата рождения 2000-05-17.
3. После входа сохраните `tokenA`, `tokenB`, `tokenAdmin` и UUID `userA`, `userB`, `userAdmin`. Для каждого запроса используйте роль из таблицы; один общий Cookie Jar может смешать роли, поэтому для сравнения используйте явный Bearer либо раздельные профили браузера.
4. Возьмите `plate` из реального READY-каталога; `rideId` — из успешного старта. Для чеков сначала завершите и оплатите свою поездку. `documentId` — из GET истории документов, это не rideId. `reason` — реальный reasonType из справочника, например `PARKING_FORBIDDEN_ZONE`, если он присутствует.
5. Для внутренних reserve/position/release выделите отдельный `internalPlate` и UUID `internalRideId`, не связанный с активной поездкой Ride. `SERVICE_KEY` — ваш общий SERVICE_API_KEY Scooter/Ride из локальных настроек. Он не должен попадать во фронтенд. После проверки выполните release. Эти ручки пользовательский браузер не вызывает.
6. `from`/`to` — реальные календарные даты YYYY-MM-DD по UTC, включающие ваши тестовые поездки. Используйте свои даты, не даты примеров из старого документа.

`{{...}}` в таблицах/коллекции — переменные, которые нужно заменить реальными значениями. Все строки API проверяйте через Postman/curl/fetch с `Accept: application/json`, а не вводом URL в адресную строку. Для JSON-тела ставьте также `Content-Type: application/json`. Навигация браузера на служебные API теперь возвращает 403 HTML; это отдельная проверка, не ошибка авторизации API-клиента. `A`, `B`, `ADMIN` означают `Authorization: Bearer <соответствующий JWT>`. BFF также принимает собственную HTTP-сессию. Cookie BFF не заменяет Bearer при прямом вызове других сервисов. Перед тестом «без авторизации» очищайте и Authorization, и Cookie.

## Как подготовить ADMIN без Docker

Зарегистрируйте отдельный `admin.check` обычным способом. В корне **своего собранного проекта** выполните под обычным пользователем:

```bash
bash -c 'source local-run/user.env.sh; "$JAVA_HOME/bin/java" -cp "services/user/target/user-service/WEB-INF/classes:services/user/target/user-service/WEB-INF/lib/*" ru.scooter.user.cli.PromoteAdmin admin.check'
```

Нужен собранный User (`mvn package` в `services/user`) и его настройки из вашего local-run. Если каталог сборки отличается, подставьте фактический WEB-INF собранного User. CLI повышает существующий логин и отзывает его старые токены. После этого **войдите заново** через `http://localhost:8080/login.jsp`. Общедоступной ручки повышения роли нет.

## Порядок проверки состояния

Сначала регистрация/вход A, B, ADMIN → чтение → каталог → старт A → проверки ACTIVE и чужого доступа → position → finish → отзыв → pay и повтор pay → чек/история PDF → документ по id/download → снимки/удаление. Затем отдельная поездка для бана во время ACTIVE, проверки административных запретов и снятия бана. Logout/отзыв токенов выполняйте в конце либо сразу получайте новые токены. Для повторения старта нужна новая/законченная поездка; нельзя прогонять все строки подряд с одним постоянно меняющимся rideId без выполнения предусловий.

Коллекция Postman — справочник запросов для ручного запуска; **не обещает успешный запуск всех запросов одним Runner**. Выставьте переменные и соблюдайте предусловия таблиц; особенно для ADMIN, USER_BLOCKED, удаления документов и внутренних резерваций.

## Навигация и статические URL

Смотрите группы `URL BFF`, `URL Admin`, `Статические URL`, `Служебные URL`, `UI без отдельного URL` ниже. Якоря `#home/#scooters/#performance/#support` не новые серверные страницы. Кабинет/формы/карточки/галерея — диалоги одного BFF-приложения. На портах микросервисов нет отдельных login.jsp/main.jsp. При открытии корней 8081/8082/8084 в браузере показывается служебная HTML-страница 403; у 8083/8085 может быть HTML-404. В API-клиенте корень Ride без JWT даёт 401; это не проверка его бизнес-ручек.

Админка: `http://localhost:8082/admin/users.html`. Она получает token через `#accessToken=...`, сохраняет в sessionStorage, убирает fragment из адреса и вызывает **User API 8081**. Далее User вызывает Moderation API 8082. Поэтому проверяются оба слоя. Публичность HTML/JS админки допустима: данные и операции защищены API.

## Реестр всех операций API

Указан ожидаемый успешный ответ при выполненных предусловиях. Ошибки валидации и доступа — в следующих разделах. Нельзя требовать 200 от POST, который открыли адресной строкой как GET. GET одной поездки есть **только у Ride 8084**, у BFF 8080 — только список. CRUD редактирования профиля, создания/удаления самокатов, банковского эквайринга и загрузки фото парковки в текущем проекте нет.

Базовый тариф учебный: 50 ₽ старт + 8 ₽ за начатую минуту, минимум 58 ₽. Его отсутствие в текстах UI не превращает оплату в реальный банковский платёж. Отзывы пользователя хранятся в Ride и отображаются в его истории и публичной ленте на главной. Публичная лента не раскрывает идентификаторы или личные данные автора.

### BFF — 8080

| ID | Метод и URL | Доступ | Тело JSON | Успех | Предусловие / содержание |
|---|---|---|---|---|---|
| E001 | POST http://localhost:8080/api/auth/register | Нет | {"login": "{{loginA}}", "password": "Check-password-2026!", "fullName": "Иван Петров Иванович", "birthDate": "2000-05-17"} | 201 | Свободный loginA; Создан USER, пароль/hash не возвращаются; не автоматический вход |
| E002 | POST http://localhost:8080/api/auth/login | Нет | {"login": "{{loginA}}", "password": "Check-password-2026!"} | 200 | Уже зарегистрирован loginA; accessToken, expiresInSeconds, user; сохранить tokenA и userA; BFF также создаёт HttpOnly JSESSIONID |
| E003 | POST http://localhost:8080/api/auth/logout | A / сессия / без токена | — | 204 | Выполнять в конце сценария, дальше нужен новый вход; Текущий JWT отозван; BFF-сессия удалена, если вызван BFF |
| E007 | GET http://localhost:8080/api/users/exists?login={{loginA}} | Нет | — | 200 | {"exists":true}; новый незанятый логин → false |
| E008 | GET http://localhost:8080/api/users/me | A | — | 200 | Профиль A, роль, дата рождения и актуальный blocked |
| E011 | GET http://localhost:8080/api/config | A | — | 200 | moderationPanelUrl=http://localhost:8082/admin/users.html |
| E012 | GET http://localhost:8080/api/rates | A | — | 200 | Активные тарифы, default, валюта, paymentMode; базовый 50 + 8/начатая минута |
| E014 | GET http://localhost:8080/api/scooters | A | — | 200 | Настоящий каталог, все статусы; удалённых нет |
| E015 | POST http://localhost:8080/api/scooters/nearest | A | {"posX": 37.618423, "posY": 55.751244} | 200 | Только READY; расстояния в метрах, по возрастанию; X=долгота, Y=широта |
| E016 | GET http://localhost:8080/api/scooters/{{plate}} | A | — | 200 | plate взять из реального каталога; Карточка существующего самоката |
| E020 | GET http://localhost:8080/api/rides | A | — | 200 | Только история A; статусы ACTIVE/FINISHED/PAID, review=null либо объект |
| E021 | POST http://localhost:8080/api/rides | A | {"numberPlate": "{{plate}}", "posX": 37.618423, "posY": 55.751244} | 201 | READY plate; нет другой активной поездки у A/самоката; A не заблокирован; Начата одна ACTIVE-поездка, самокат IN_RIDE; сохранить rideId |
| E022 | PUT http://localhost:8080/api/rides/{{rideId}}/position | A | {"posX": 37.618423, "posY": 55.751244} | 204 | Своя ACTIVE-поездка; Сохранена последняя позиция и обновлена позиция занятого самоката |
| E023 | PUT http://localhost:8080/api/rides/{{rideId}}/finish | A | {"posX": 37.618423, "posY": 55.751244} | 200 | Своя ACTIVE-поездка; повтор допустим с теми же координатами; FINISHED, конечные координаты, длительность и сумма; самокат освобождён |
| E024 | PUT http://localhost:8080/api/rides/{{rideId}}/review | A | {"rideRating": 5, "scooterRating": 4, "rideComment": "Хорошая поездка", "scooterComment": "Удобный самокат"} | 200 | Своя FINISHED или PAID-поездка; Отзыв сохранён в Ride DB и возвращается в истории; повтор изменяет его |
| E025 | POST http://localhost:8080/api/rides/{{rideId}}/pay | A | {} | 200 | Своя FINISHED-поездка, PAYMENT_MODE=educational; PAID и paidAt, paymentMode=EDUCATIONAL; сумма не меняется; повтор идемпотентен |
| E032 | POST http://localhost:8080/api/documents/generate | A | {"docType": "RIDE_PAYMENT", "sourceId": "{{rideId}}"} | 200 | Для чека: своя PAID-поездка; другие типы и тела см. ниже; Настоящий PDF, application/pdf, %PDF-, корректная кириллица; метаданные GENERATED |
| E033 | GET http://localhost:8080/api/documents?limit=50&offset=0 | A | — | 200 | Только свои неудалённые метаданные, максимум limit; сохранить documentId |
| E034 | GET http://localhost:8080/api/documents/{{documentId}} | A | — | 200 | Метаданные существующего своего документа |
| E035 | GET http://localhost:8080/api/documents/{{documentId}}/download | A | — | 200 | Свой GENERATED-документ; PDF заново построен из сохранённого снимка данных, не JSON и не HTML |
| E036 | DELETE http://localhost:8080/api/documents/{{documentId}} | A | — | 204 | Выполнять после скачивания и проверки снимка; Мягкое удаление, повтор DELETE тоже 204; GET/download затем 404 |
| E059 | GET http://localhost:8080/api/reviews?limit=20&offset=0 | Нет | — | 200 | Нужна миграция отзывов 002; пустой список допустим; Публичный массив оценок, комментариев и reviewedAt; нет userId, rideId, логинов, сумм и координат |

### User — 8081

| ID | Метод и URL | Доступ | Тело JSON | Успех | Предусловие / содержание |
|---|---|---|---|---|---|
| E004 | POST http://localhost:8081/api/users/register | Нет | {"login": "{{loginA}}", "password": "Check-password-2026!", "fullName": "Иван Петров Иванович", "birthDate": "2000-05-17"} | 201 | Свободный loginA; Создан USER, пароль/hash не возвращаются; не автоматический вход |
| E005 | POST http://localhost:8081/api/users/login | Нет | {"login": "{{loginA}}", "password": "Check-password-2026!"} | 200 | Уже зарегистрирован loginA; accessToken, expiresInSeconds, user; сохранить tokenA и userA; BFF также создаёт HttpOnly JSESSIONID |
| E006 | POST http://localhost:8081/api/users/logout | A | — | 204 | Выполнять в конце сценария, дальше нужен новый вход; Текущий JWT отозван; BFF-сессия удалена, если вызван BFF |
| E009 | GET http://localhost:8081/api/users/exists?login={{loginA}} | Нет | — | 200 | {"exists":true}; новый незанятый логин → false |
| E010 | GET http://localhost:8081/api/users/me | A | — | 200 | Профиль A, роль, дата рождения и актуальный blocked |
| E042 | GET http://localhost:8081/api/users/validate | A | — | 204 | 204, без тела, токен действителен; не обращается к Moderation |
| E043 | GET http://localhost:8081/api/users | ADMIN | — | 200 | Список активных пользователей: id, login, fullName |
| E044 | GET http://localhost:8081/api/users/{{userA}} | A / ADMIN | — | 200 | Полный профиль A; USER B не имеет доступа |
| E045 | GET http://localhost:8081/api/users/{{userA}}/blocked | A / ADMIN | — | 200 | {"blocked":false/true} |
| E046 | GET http://localhost:8081/api/users/blocked?limit=50&offset=0 | ADMIN | — | 200 | Список блокировок из Moderation |
| E047 | GET http://localhost:8081/api/users/reasons | ADMIN | — | 200 | Справочник причин: reasonType, description, durationSeconds |
| E048 | POST http://localhost:8081/api/users/{{userA}}/ban | ADMIN | {"reason": "{{reason}}"} | 200 | Существующий тестовый userA; reason взять из справочника; Актуальный BlockStatus; повтор не создаёт вторую блокировку/не продлевает старую |
| E049 | POST http://localhost:8081/api/users/{{userA}}/unban | ADMIN | — | 200 | Существующий тестовый userA; reason взять из справочника; blocked=false; повтор допустим |

### Moderation — 8082

| ID | Метод и URL | Доступ | Тело JSON | Успех | Предусловие / содержание |
|---|---|---|---|---|---|
| E050 | GET http://localhost:8082/api/moderation/users/{{userA}}/status | A / ADMIN | — | 200 | BlockStatus: userId, blocked, reasonType, reason, blockedAt, blockedUntil |
| E051 | GET http://localhost:8082/api/moderation/reasons | ADMIN | — | 200 | Справочник причин |
| E052 | GET http://localhost:8082/api/moderation/users?blocked=true&limit=50&offset=0 | ADMIN | — | 200 | Список блокировок |
| E053 | POST http://localhost:8082/api/moderation/users/{{userA}}/ban | ADMIN | {"reason": "{{reason}}"} | 200 | Использовать реальный тестовый userA. Прямой Moderation не проверяет наличие UUID в User; BlockStatus; blocked=true |
| E054 | POST http://localhost:8082/api/moderation/users/{{userA}}/unban | ADMIN | — | 200 | Использовать реальный тестовый userA. Прямой Moderation не проверяет наличие UUID в User; BlockStatus; blocked=false |

### Scooter — 8083

| ID | Метод и URL | Доступ | Тело JSON | Успех | Предусловие / содержание |
|---|---|---|---|---|---|
| E017 | GET http://localhost:8083/api/scooters | A | — | 200 | Настоящий каталог, все статусы; удалённых нет |
| E018 | POST http://localhost:8083/api/scooters/nearest | A | {"posX": 37.618423, "posY": 55.751244} | 200 | Только READY; расстояния в метрах, по возрастанию; X=долгота, Y=широта |
| E019 | GET http://localhost:8083/api/scooters/{{plate}} | A | — | 200 | plate взять из реального каталога; Карточка существующего самоката |
| E056 | POST http://localhost:8083/api/scooters/{{internalPlate}}/reserve | SERVICE_KEY | {"rideId": "{{internalRideId}}", "userId": "{{userA}}"} | 200 | Только отдельный тестовый самокат и отдельный UUID, не связанный с настоящей Ride-поездкой; порядок reserve → position → release; Резервирование |
| E057 | POST http://localhost:8083/api/scooters/{{internalPlate}}/position | SERVICE_KEY | {"rideId": "{{internalRideId}}", "posX": 37.618423, "posY": 55.751244} | 200 | Только отдельный тестовый самокат и отдельный UUID, не связанный с настоящей Ride-поездкой; порядок reserve → position → release; Позиция обновлена |
| E058 | POST http://localhost:8083/api/scooters/{{internalPlate}}/release | SERVICE_KEY | {"rideId": "{{internalRideId}}", "posX": 37.618423, "posY": 55.751244} | 200 | Только отдельный тестовый самокат и отдельный UUID, не связанный с настоящей Ride-поездкой; порядок reserve → position → release; Резервация закрыта, самокат освобождён |

### Ride — 8084

| ID | Метод и URL | Доступ | Тело JSON | Успех | Предусловие / содержание |
|---|---|---|---|---|---|
| E013 | GET http://localhost:8084/api/rates | A | — | 200 | Активные тарифы, default, валюта, paymentMode; базовый 50 + 8/начатая минута |
| E026 | GET http://localhost:8084/api/rides | A | — | 200 | Только история A; статусы ACTIVE/FINISHED/PAID, review=null либо объект |
| E027 | POST http://localhost:8084/api/rides | A | {"numberPlate": "{{plate}}", "posX": 37.618423, "posY": 55.751244} | 201 | READY plate; нет другой активной поездки у A/самоката; A не заблокирован; Начата одна ACTIVE-поездка, самокат IN_RIDE; сохранить rideId |
| E028 | PUT http://localhost:8084/api/rides/{{rideId}}/position | A | {"posX": 37.618423, "posY": 55.751244} | 204 | Своя ACTIVE-поездка; Сохранена последняя позиция и обновлена позиция занятого самоката |
| E029 | PUT http://localhost:8084/api/rides/{{rideId}}/finish | A | {"posX": 37.618423, "posY": 55.751244} | 200 | Своя ACTIVE-поездка; повтор допустим с теми же координатами; FINISHED, конечные координаты, длительность и сумма; самокат освобождён |
| E030 | PUT http://localhost:8084/api/rides/{{rideId}}/review | A | {"rideRating": 5, "scooterRating": 4, "rideComment": "Хорошая поездка", "scooterComment": "Удобный самокат"} | 200 | Своя FINISHED или PAID-поездка; Отзыв сохранён в Ride DB и возвращается в истории; повтор изменяет его |
| E031 | POST http://localhost:8084/api/rides/{{rideId}}/pay | A | {} | 200 | Своя FINISHED-поездка, PAYMENT_MODE=educational; PAID и paidAt, paymentMode=EDUCATIONAL; сумма не меняется; повтор идемпотентен |
| E055 | GET http://localhost:8084/api/rides/{{rideId}} | A | — | 200 | Одна своя поездка; B → 403 |
| E060 | GET http://localhost:8084/api/reviews?limit=20&offset=0 | Нет | — | 200 | Нужна миграция отзывов 002; пустой список допустим; Публичный массив оценок, комментариев и reviewedAt; нет userId, rideId, логинов, сумм и координат |

### Documents — 8085

| ID | Метод и URL | Доступ | Тело JSON | Успех | Предусловие / содержание |
|---|---|---|---|---|---|
| E037 | POST http://localhost:8085/api/documents/generate | A | {"userId": "{{userA}}", "docType": "RIDE_PAYMENT", "sourceId": "{{rideId}}"} | 200 | Для чека: своя PAID-поездка; другие типы и тела см. ниже; Настоящий PDF, application/pdf, %PDF-, корректная кириллица; метаданные GENERATED |
| E038 | GET http://localhost:8085/api/documents?limit=50&offset=0 | A | — | 200 | Только свои неудалённые метаданные, максимум limit; сохранить documentId |
| E039 | GET http://localhost:8085/api/documents/{{documentId}} | A | — | 200 | Метаданные существующего своего документа |
| E040 | GET http://localhost:8085/api/documents/{{documentId}}/download | A | — | 200 | Свой GENERATED-документ; PDF заново построен из сохранённого снимка данных, не JSON и не HTML |
| E041 | DELETE http://localhost:8085/api/documents/{{documentId}} | A | — | 204 | Выполнять после скачивания и проверки снимка; Мягкое удаление, повтор DELETE тоже 204; GET/download затем 404 |

## Другие тела генерации PDF

В BFF (8080) не нужно задавать userId: он берётся из авторизации. В прямом Documents (8085) **обязательно** добавить свой `userId`.

```json
{"docType":"USER_HISTORY","from":"YYYY-MM-DD","to":"YYYY-MM-DD"}
```

```json
{"docType":"USER_BLOCKED"}
```

Для чека нужны RIDE_PAYMENT и sourceId оплаченной поездки. Только `RECEIPT` поддерживается как прежний алиас RIDE_PAYMENT. PDF надо открывать, а не только проверять код 200. В случае ошибки API возвращает JSON — его нельзя считать успешно скачанным PDF.

## Все пункты проверки

В CSV те же ID и строки, дополнительно колонки result/actual/comment. API-пункты с E-ID ссылаются на реестр выше; сначала выполните их предусловия. Ошибки формы/сервиса надо фиксировать как FAIL/BLOCKED с фактическим ответом, а не считать успехом только потому, что страница открылась.

### API BFF

- [ ] **C001 — E001 POST http://localhost:8080/api/auth/register**. Свободный loginA. Ожидается: 201; Создан USER, пароль/hash не возвращаются; не автоматический вход. URL: `POST http://localhost:8080/api/auth/register`.
- [ ] **C002 — E002 POST http://localhost:8080/api/auth/login**. Уже зарегистрирован loginA. Ожидается: 200; accessToken, expiresInSeconds, user; сохранить tokenA и userA; BFF также создаёт HttpOnly JSESSIONID. URL: `POST http://localhost:8080/api/auth/login`.
- [ ] **C003 — E003 POST http://localhost:8080/api/auth/logout**. Выполнять в конце сценария, дальше нужен новый вход. Ожидается: 204; Текущий JWT отозван; BFF-сессия удалена, если вызван BFF. URL: `POST http://localhost:8080/api/auth/logout`.
- [ ] **C007 — E007 GET http://localhost:8080/api/users/exists?login={{loginA}}**. Выполнить запрос с указанной авторизацией. Ожидается: 200; {"exists":true}; новый незанятый логин → false. URL: `GET http://localhost:8080/api/users/exists?login={{loginA}}`.
- [ ] **C008 — E008 GET http://localhost:8080/api/users/me**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Профиль A, роль, дата рождения и актуальный blocked. URL: `GET http://localhost:8080/api/users/me`.
- [ ] **C011 — E011 GET http://localhost:8080/api/config**. Выполнить запрос с указанной авторизацией. Ожидается: 200; moderationPanelUrl=http://localhost:8082/admin/users.html. URL: `GET http://localhost:8080/api/config`.
- [ ] **C012 — E012 GET http://localhost:8080/api/rates**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Активные тарифы, default, валюта, paymentMode; базовый 50 + 8/начатая минута. URL: `GET http://localhost:8080/api/rates`.
- [ ] **C014 — E014 GET http://localhost:8080/api/scooters**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Настоящий каталог, все статусы; удалённых нет. URL: `GET http://localhost:8080/api/scooters`.
- [ ] **C015 — E015 POST http://localhost:8080/api/scooters/nearest**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Только READY; расстояния в метрах, по возрастанию; X=долгота, Y=широта. URL: `POST http://localhost:8080/api/scooters/nearest`.
- [ ] **C016 — E016 GET http://localhost:8080/api/scooters/{{plate}}**. plate взять из реального каталога. Ожидается: 200; Карточка существующего самоката. URL: `GET http://localhost:8080/api/scooters/{{plate}}`.
- [ ] **C020 — E020 GET http://localhost:8080/api/rides**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Только история A; статусы ACTIVE/FINISHED/PAID, review=null либо объект. URL: `GET http://localhost:8080/api/rides`.
- [ ] **C021 — E021 POST http://localhost:8080/api/rides**. READY plate; нет другой активной поездки у A/самоката; A не заблокирован. Ожидается: 201; Начата одна ACTIVE-поездка, самокат IN_RIDE; сохранить rideId. URL: `POST http://localhost:8080/api/rides`.
- [ ] **C022 — E022 PUT http://localhost:8080/api/rides/{{rideId}}/position**. Своя ACTIVE-поездка. Ожидается: 204; Сохранена последняя позиция и обновлена позиция занятого самоката. URL: `PUT http://localhost:8080/api/rides/{{rideId}}/position`.
- [ ] **C023 — E023 PUT http://localhost:8080/api/rides/{{rideId}}/finish**. Своя ACTIVE-поездка; повтор допустим с теми же координатами. Ожидается: 200; FINISHED, конечные координаты, длительность и сумма; самокат освобождён. URL: `PUT http://localhost:8080/api/rides/{{rideId}}/finish`.
- [ ] **C024 — E024 PUT http://localhost:8080/api/rides/{{rideId}}/review**. Своя FINISHED или PAID-поездка. Ожидается: 200; Отзыв сохранён в Ride DB и возвращается в истории; повтор изменяет его. URL: `PUT http://localhost:8080/api/rides/{{rideId}}/review`.
- [ ] **C025 — E025 POST http://localhost:8080/api/rides/{{rideId}}/pay**. Своя FINISHED-поездка, PAYMENT_MODE=educational. Ожидается: 200; PAID и paidAt, paymentMode=EDUCATIONAL; сумма не меняется; повтор идемпотентен. URL: `POST http://localhost:8080/api/rides/{{rideId}}/pay`.
- [ ] **C032 — E032 POST http://localhost:8080/api/documents/generate**. Для чека: своя PAID-поездка; другие типы и тела см. ниже. Ожидается: 200; Настоящий PDF, application/pdf, %PDF-, корректная кириллица; метаданные GENERATED. URL: `POST http://localhost:8080/api/documents/generate`.
- [ ] **C033 — E033 GET http://localhost:8080/api/documents?limit=50&offset=0**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Только свои неудалённые метаданные, максимум limit; сохранить documentId. URL: `GET http://localhost:8080/api/documents?limit=50&offset=0`.
- [ ] **C034 — E034 GET http://localhost:8080/api/documents/{{documentId}}**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Метаданные существующего своего документа. URL: `GET http://localhost:8080/api/documents/{{documentId}}`.
- [ ] **C035 — E035 GET http://localhost:8080/api/documents/{{documentId}}/download**. Свой GENERATED-документ. Ожидается: 200; PDF заново построен из сохранённого снимка данных, не JSON и не HTML. URL: `GET http://localhost:8080/api/documents/{{documentId}}/download`.
- [ ] **C036 — E036 DELETE http://localhost:8080/api/documents/{{documentId}}**. Выполнять после скачивания и проверки снимка. Ожидается: 204; Мягкое удаление, повтор DELETE тоже 204; GET/download затем 404. URL: `DELETE http://localhost:8080/api/documents/{{documentId}}`.
- [ ] **C059 — E059 GET http://localhost:8080/api/reviews?limit=20&offset=0**. Нужна миграция отзывов 002; пустой список допустим. Ожидается: 200; Публичный массив оценок, комментариев и reviewedAt; нет userId, rideId, логинов, сумм и координат. URL: `GET http://localhost:8080/api/reviews?limit=20&offset=0`.

### API User

- [ ] **C004 — E004 POST http://localhost:8081/api/users/register**. Свободный loginA. Ожидается: 201; Создан USER, пароль/hash не возвращаются; не автоматический вход. URL: `POST http://localhost:8081/api/users/register`.
- [ ] **C005 — E005 POST http://localhost:8081/api/users/login**. Уже зарегистрирован loginA. Ожидается: 200; accessToken, expiresInSeconds, user; сохранить tokenA и userA; BFF также создаёт HttpOnly JSESSIONID. URL: `POST http://localhost:8081/api/users/login`.
- [ ] **C006 — E006 POST http://localhost:8081/api/users/logout**. Выполнять в конце сценария, дальше нужен новый вход. Ожидается: 204; Текущий JWT отозван; BFF-сессия удалена, если вызван BFF. URL: `POST http://localhost:8081/api/users/logout`.
- [ ] **C009 — E009 GET http://localhost:8081/api/users/exists?login={{loginA}}**. Выполнить запрос с указанной авторизацией. Ожидается: 200; {"exists":true}; новый незанятый логин → false. URL: `GET http://localhost:8081/api/users/exists?login={{loginA}}`.
- [ ] **C010 — E010 GET http://localhost:8081/api/users/me**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Профиль A, роль, дата рождения и актуальный blocked. URL: `GET http://localhost:8081/api/users/me`.
- [ ] **C042 — E042 GET http://localhost:8081/api/users/validate**. Выполнить запрос с указанной авторизацией. Ожидается: 204; 204, без тела, токен действителен; не обращается к Moderation. URL: `GET http://localhost:8081/api/users/validate`.
- [ ] **C043 — E043 GET http://localhost:8081/api/users**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Список активных пользователей: id, login, fullName. URL: `GET http://localhost:8081/api/users`.
- [ ] **C044 — E044 GET http://localhost:8081/api/users/{{userA}}**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Полный профиль A; USER B не имеет доступа. URL: `GET http://localhost:8081/api/users/{{userA}}`.
- [ ] **C045 — E045 GET http://localhost:8081/api/users/{{userA}}/blocked**. Выполнить запрос с указанной авторизацией. Ожидается: 200; {"blocked":false/true}. URL: `GET http://localhost:8081/api/users/{{userA}}/blocked`.
- [ ] **C046 — E046 GET http://localhost:8081/api/users/blocked?limit=50&offset=0**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Список блокировок из Moderation. URL: `GET http://localhost:8081/api/users/blocked?limit=50&offset=0`.
- [ ] **C047 — E047 GET http://localhost:8081/api/users/reasons**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Справочник причин: reasonType, description, durationSeconds. URL: `GET http://localhost:8081/api/users/reasons`.
- [ ] **C048 — E048 POST http://localhost:8081/api/users/{{userA}}/ban**. Существующий тестовый userA; reason взять из справочника. Ожидается: 200; Актуальный BlockStatus; повтор не создаёт вторую блокировку/не продлевает старую. URL: `POST http://localhost:8081/api/users/{{userA}}/ban`.
- [ ] **C049 — E049 POST http://localhost:8081/api/users/{{userA}}/unban**. Существующий тестовый userA; reason взять из справочника. Ожидается: 200; blocked=false; повтор допустим. URL: `POST http://localhost:8081/api/users/{{userA}}/unban`.

### API Ride

- [ ] **C013 — E013 GET http://localhost:8084/api/rates**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Активные тарифы, default, валюта, paymentMode; базовый 50 + 8/начатая минута. URL: `GET http://localhost:8084/api/rates`.
- [ ] **C026 — E026 GET http://localhost:8084/api/rides**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Только история A; статусы ACTIVE/FINISHED/PAID, review=null либо объект. URL: `GET http://localhost:8084/api/rides`.
- [ ] **C027 — E027 POST http://localhost:8084/api/rides**. READY plate; нет другой активной поездки у A/самоката; A не заблокирован. Ожидается: 201; Начата одна ACTIVE-поездка, самокат IN_RIDE; сохранить rideId. URL: `POST http://localhost:8084/api/rides`.
- [ ] **C028 — E028 PUT http://localhost:8084/api/rides/{{rideId}}/position**. Своя ACTIVE-поездка. Ожидается: 204; Сохранена последняя позиция и обновлена позиция занятого самоката. URL: `PUT http://localhost:8084/api/rides/{{rideId}}/position`.
- [ ] **C029 — E029 PUT http://localhost:8084/api/rides/{{rideId}}/finish**. Своя ACTIVE-поездка; повтор допустим с теми же координатами. Ожидается: 200; FINISHED, конечные координаты, длительность и сумма; самокат освобождён. URL: `PUT http://localhost:8084/api/rides/{{rideId}}/finish`.
- [ ] **C030 — E030 PUT http://localhost:8084/api/rides/{{rideId}}/review**. Своя FINISHED или PAID-поездка. Ожидается: 200; Отзыв сохранён в Ride DB и возвращается в истории; повтор изменяет его. URL: `PUT http://localhost:8084/api/rides/{{rideId}}/review`.
- [ ] **C031 — E031 POST http://localhost:8084/api/rides/{{rideId}}/pay**. Своя FINISHED-поездка, PAYMENT_MODE=educational. Ожидается: 200; PAID и paidAt, paymentMode=EDUCATIONAL; сумма не меняется; повтор идемпотентен. URL: `POST http://localhost:8084/api/rides/{{rideId}}/pay`.
- [ ] **C055 — E055 GET http://localhost:8084/api/rides/{{rideId}}**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Одна своя поездка; B → 403. URL: `GET http://localhost:8084/api/rides/{{rideId}}`.
- [ ] **C060 — E060 GET http://localhost:8084/api/reviews?limit=20&offset=0**. Нужна миграция отзывов 002; пустой список допустим. Ожидается: 200; Публичный массив оценок, комментариев и reviewedAt; нет userId, rideId, логинов, сумм и координат. URL: `GET http://localhost:8084/api/reviews?limit=20&offset=0`.

### API Scooter

- [ ] **C017 — E017 GET http://localhost:8083/api/scooters**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Настоящий каталог, все статусы; удалённых нет. URL: `GET http://localhost:8083/api/scooters`.
- [ ] **C018 — E018 POST http://localhost:8083/api/scooters/nearest**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Только READY; расстояния в метрах, по возрастанию; X=долгота, Y=широта. URL: `POST http://localhost:8083/api/scooters/nearest`.
- [ ] **C019 — E019 GET http://localhost:8083/api/scooters/{{plate}}**. plate взять из реального каталога. Ожидается: 200; Карточка существующего самоката. URL: `GET http://localhost:8083/api/scooters/{{plate}}`.
- [ ] **C056 — E056 POST http://localhost:8083/api/scooters/{{internalPlate}}/reserve**. Только отдельный тестовый самокат и отдельный UUID, не связанный с настоящей Ride-поездкой; порядок reserve → position → release. Ожидается: 200; Резервирование. URL: `POST http://localhost:8083/api/scooters/{{internalPlate}}/reserve`.
- [ ] **C057 — E057 POST http://localhost:8083/api/scooters/{{internalPlate}}/position**. Только отдельный тестовый самокат и отдельный UUID, не связанный с настоящей Ride-поездкой; порядок reserve → position → release. Ожидается: 200; Позиция обновлена. URL: `POST http://localhost:8083/api/scooters/{{internalPlate}}/position`.
- [ ] **C058 — E058 POST http://localhost:8083/api/scooters/{{internalPlate}}/release**. Только отдельный тестовый самокат и отдельный UUID, не связанный с настоящей Ride-поездкой; порядок reserve → position → release. Ожидается: 200; Резервация закрыта, самокат освобождён. URL: `POST http://localhost:8083/api/scooters/{{internalPlate}}/release`.

### API Documents

- [ ] **C037 — E037 POST http://localhost:8085/api/documents/generate**. Для чека: своя PAID-поездка; другие типы и тела см. ниже. Ожидается: 200; Настоящий PDF, application/pdf, %PDF-, корректная кириллица; метаданные GENERATED. URL: `POST http://localhost:8085/api/documents/generate`.
- [ ] **C038 — E038 GET http://localhost:8085/api/documents?limit=50&offset=0**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Только свои неудалённые метаданные, максимум limit; сохранить documentId. URL: `GET http://localhost:8085/api/documents?limit=50&offset=0`.
- [ ] **C039 — E039 GET http://localhost:8085/api/documents/{{documentId}}**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Метаданные существующего своего документа. URL: `GET http://localhost:8085/api/documents/{{documentId}}`.
- [ ] **C040 — E040 GET http://localhost:8085/api/documents/{{documentId}}/download**. Свой GENERATED-документ. Ожидается: 200; PDF заново построен из сохранённого снимка данных, не JSON и не HTML. URL: `GET http://localhost:8085/api/documents/{{documentId}}/download`.
- [ ] **C041 — E041 DELETE http://localhost:8085/api/documents/{{documentId}}**. Выполнять после скачивания и проверки снимка. Ожидается: 204; Мягкое удаление, повтор DELETE тоже 204; GET/download затем 404. URL: `DELETE http://localhost:8085/api/documents/{{documentId}}`.

### API Moderation

- [ ] **C050 — E050 GET http://localhost:8082/api/moderation/users/{{userA}}/status**. Выполнить запрос с указанной авторизацией. Ожидается: 200; BlockStatus: userId, blocked, reasonType, reason, blockedAt, blockedUntil. URL: `GET http://localhost:8082/api/moderation/users/{{userA}}/status`.
- [ ] **C051 — E051 GET http://localhost:8082/api/moderation/reasons**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Справочник причин. URL: `GET http://localhost:8082/api/moderation/reasons`.
- [ ] **C052 — E052 GET http://localhost:8082/api/moderation/users?blocked=true&limit=50&offset=0**. Выполнить запрос с указанной авторизацией. Ожидается: 200; Список блокировок. URL: `GET http://localhost:8082/api/moderation/users?blocked=true&limit=50&offset=0`.
- [ ] **C053 — E053 POST http://localhost:8082/api/moderation/users/{{userA}}/ban**. Использовать реальный тестовый userA. Прямой Moderation не проверяет наличие UUID в User. Ожидается: 200; BlockStatus; blocked=true. URL: `POST http://localhost:8082/api/moderation/users/{{userA}}/ban`.
- [ ] **C054 — E054 POST http://localhost:8082/api/moderation/users/{{userA}}/unban**. Использовать реальный тестовый userA. Прямой Moderation не проверяет наличие UUID в User. Ожидается: 200; BlockStatus; blocked=false. URL: `POST http://localhost:8082/api/moderation/users/{{userA}}/unban`.

### URL BFF

- [ ] **C061 — /**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: главная; без входа каталог не выдумывается. URL: `GET http://localhost:8080/`.
- [ ] **C062 — /index.jsp**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: та же главная. URL: `GET http://localhost:8080/index.jsp`.
- [ ] **C063 — /login.jsp**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 302 → /auth/entry; без сессии вход, USER кабинет, ADMIN панель с текущим JWT. URL: `GET http://localhost:8080/login.jsp`.
- [ ] **C064 — /register.jsp**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 302 → /?auth=register; открыта регистрация. URL: `GET http://localhost:8080/register.jsp`.
- [ ] **C065 — /main.jsp**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 302 → /?account=profile; профиль после входа, иначе форма входа. URL: `GET http://localhost:8080/main.jsp`.
- [ ] **C066 — /?auth=login**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: вход без сессии; при действующей USER-сессии кабинет, ADMIN перенаправляется в панель. URL: `GET http://localhost:8080/?auth=login`.
- [ ] **C067 — /?auth=register**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: регистрация. URL: `GET http://localhost:8080/?auth=register`.
- [ ] **C068 — /?account=profile**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: профиль после входа. URL: `GET http://localhost:8080/?account=profile`.
- [ ] **C069 — /?account=rides**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: история и активная поездка. URL: `GET http://localhost:8080/?account=rides`.
- [ ] **C070 — /?account=saved**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: избранное. URL: `GET http://localhost:8080/?account=saved`.
- [ ] **C071 — /?account=documents**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: документы. URL: `GET http://localhost:8080/?account=documents`.
- [ ] **C072 — /?account=unknown**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: безопасно открывается профиль, нет сбоя JS. URL: `GET http://localhost:8080/?account=unknown`.
- [ ] **C073 — /#home**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: главная, прокрутка к началу. URL: `GET http://localhost:8080/#home`.
- [ ] **C074 — /#scooters**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: секция каталога. URL: `GET http://localhost:8080/#scooters`.
- [ ] **C075 — /#performance**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: секция о самокатах. URL: `GET http://localhost:8080/#performance`.
- [ ] **C076 — /#support**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: реальные публичные отзывы из Ride через BFF. URL: `GET http://localhost:8080/#support`.
- [ ] **C077 — /logout**. Открыть в браузере; проверить вход/выход, обновление и Назад/Вперёд. Ожидается: 200: подтверждение выхода; GET не завершает сессию. URL: `GET http://localhost:8080/logout`.
- [ ] **C078 — Вход по действующей сессии**. Открыть /auth/entry без входа, после входа USER, после входа ADMIN. Ожидается: 302 на форму входа, кабинет или панель соответственно; JWT не отзывается. URL: `GET http://localhost:8080/auth/entry`.
- [ ] **C079 — Подтвердить /logout**. Отправить форму выхода после GET /logout. Ожидается: 303 → /login.jsp, сессия закрыта, текущий JWT отозван. URL: `POST http://localhost:8080/logout`.

### URL Admin

- [ ] **C080 — Панель без токена**. Очистить sessionStorage только в отдельном тестовом профиле браузера и открыть панель. Ожидается: Переход через BFF login.jsp/auth/entry: без сессии форма входа, USER кабинет, действующий ADMIN автоматически возвращается в панель. URL: `GET http://localhost:8082/admin/users.html`.
- [ ] **C081 — Вход ADMIN**. Войти ролью ADMIN через BFF login.jsp. Ожидается: Переход в /admin/users.html#accessToken=<JWT>; затем fragment удалён; пользователи загружаются. URL: `Браузер http://localhost:8080/login.jsp`.
- [ ] **C082 — Панель с ADMIN токеном**. Подставить актуальный JWT тестового ADMIN в fragment. Ожидается: Панель работает; JWT не остаётся в URL/query; refresh работает в той же вкладке. URL: `GET http://localhost:8082/admin/users.html#accessToken={{tokenAdmin}}`.
- [ ] **C083 — Панель с USER токеном**. Подставить tokenA в fragment. Ожидается: Недостаточно прав; нет списка пользователей и действий администратора. URL: `GET http://localhost:8082/admin/users.html#accessToken={{tokenA}}`.
- [ ] **C084 — Панель с просроченным/поддельным JWT**. Проверить истёкший JWT, изменённую подпись и не-JWT. Ожидается: Нет данных администратора; истёкший/некорректный токен ведёт на вход, 401 API также ведёт на вход. URL: `Браузер http://localhost:8082/admin/users.html`.

### Статические URL

- [ ] **C085 — BFF account.css**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/account.css`.
- [ ] **C086 — BFF account.js**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/account.js`.
- [ ] **C087 — BFF api.js**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/api.js`.
- [ ] **C088 — BFF JTUHjIg1_i6t8kCHKm4532VJOt5-QNFgpCtZ6Ew-.ttf**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/JTUHjIg1_i6t8kCHKm4532VJOt5-QNFgpCtZ6Ew-.ttf`.
- [ ] **C089 — BFF JTUHjIg1_i6t8kCHKm4532VJOt5-QNFgpCtr6Ew-.ttf**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/JTUHjIg1_i6t8kCHKm4532VJOt5-QNFgpCtr6Ew-.ttf`.
- [ ] **C090 — BFF JTUHjIg1_i6t8kCHKm4532VJOt5-QNFgpCu170w-.ttf**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/JTUHjIg1_i6t8kCHKm4532VJOt5-QNFgpCu170w-.ttf`.
- [ ] **C091 — BFF V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj42Vksj.ttf**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj42Vksj.ttf`.
- [ ] **C092 — BFF V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj4PVksj.ttf**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj4PVksj.ttf`.
- [ ] **C093 — BFF V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj7aUUsj.ttf**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj7aUUsj.ttf`.
- [ ] **C094 — BFF V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj7oUUsj.ttf**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/V8mQoQDjQSkFtoMM3T6r8E7mF71Q-gOoraIAEj7oUUsj.ttf`.
- [ ] **C095 — BFF hero-background.png**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/hero-background.png`.
- [ ] **C096 — BFF reference-desktop.jpg**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/reference-desktop.jpg`.
- [ ] **C097 — BFF reference-hero.jpg**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/reference-hero.jpg`.
- [ ] **C098 — BFF tyre-background.png**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/assets/tyre-background.png`.
- [ ] **C099 — BFF fonts.css**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/fonts.css`.
- [ ] **C100 — BFF script.js**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/script.js`.
- [ ] **C101 — BFF style.css**. Открыть URL и вкладку Network; проверить что тело соответствует JS/CSS/изображению/шрифту. Ожидается: 200, нет HTML-страницы ошибки. URL: `GET http://localhost:8080/frontend/style.css`.
- [ ] **C102 — Moderation /admin/js/config.js**. Открыть URL; config.js содержит USER_URL=8081 и LOGIN_URL=8080/login.jsp, без секретов. Ожидается: 200, правильное содержимое. URL: `GET http://localhost:8082/admin/js/config.js`.
- [ ] **C103 — Moderation /admin/js/users.js**. Открыть URL; config.js содержит USER_URL=8081 и LOGIN_URL=8080/login.jsp, без секретов. Ожидается: 200, правильное содержимое. URL: `GET http://localhost:8082/admin/js/users.js`.
- [ ] **C104 — Moderation /admin/css/style.css**. Открыть URL; config.js содержит USER_URL=8081 и LOGIN_URL=8080/login.jsp, без секретов. Ожидается: 200, правильное содержимое. URL: `GET http://localhost:8082/admin/css/style.css`.

### Служебные URL

- [ ] **C105 — Moderation /error**. Открыть напрямую. Ожидается: В браузере 403 HTML; в API-клиенте 404 JSON, это обработчик ошибок. URL: `GET http://localhost:8082/error`.
- [ ] **C106 — User root**. Открыть корень порта. Ожидается: 403 HTML: это служебный адрес, перейти в BFF. URL: `GET http://localhost:8081/`.
- [ ] **C107 — Moderation root**. Открыть корень порта. Ожидается: 403 HTML: это служебный адрес, перейти в BFF. URL: `GET http://localhost:8082/`.
- [ ] **C108 — Scooter root**. Открыть корень порта. Ожидается: 404 HTML допустим: у микросервиса нет главной страницы. URL: `GET http://localhost:8083/`.
- [ ] **C109 — Documents root**. Открыть корень порта. Ожидается: 404 HTML допустим: у микросервиса нет главной страницы. URL: `GET http://localhost:8085/`.
- [ ] **C110 — Ride root**. Открыть корень 8084 без токена; повторить с валидным Bearer. Ожидается: В адресной строке 403 HTML; из API-клиента без JWT 401, с JWT обычно 404; HTML-главной нет. URL: `GET http://localhost:8084/`.

### Маршрутизация

- [ ] **C111 — BFF неизвестная ручка**. С валидным tokenA (для Moderation tokenAdmin) запросить несуществующий вложенный путь. Ожидается: 404; нет данных и записи в БД. URL: `GET http://localhost:8080/api/no-such/route`.
- [ ] **C112 — User неизвестная ручка**. С валидным tokenA (для Moderation tokenAdmin) запросить несуществующий вложенный путь. Ожидается: 404; нет данных и записи в БД. URL: `GET http://localhost:8081/api/no-such/route`.
- [ ] **C113 — Moderation неизвестная ручка**. С валидным tokenA (для Moderation tokenAdmin) запросить несуществующий вложенный путь. Ожидается: 404; нет данных и записи в БД. URL: `GET http://localhost:8082/api/no-such/route`.
- [ ] **C114 — Scooter неизвестная ручка**. С валидным tokenA (для Moderation tokenAdmin) запросить несуществующий вложенный путь. Ожидается: 404; нет данных и записи в БД. URL: `GET http://localhost:8083/api/no-such/route`.
- [ ] **C115 — Ride неизвестная ручка**. С валидным tokenA (для Moderation tokenAdmin) запросить несуществующий вложенный путь. Ожидается: 404; нет данных и записи в БД. URL: `GET http://localhost:8084/api/no-such/route`.
- [ ] **C116 — Documents неизвестная ручка**. С валидным tokenA (для Moderation tokenAdmin) запросить несуществующий вложенный путь. Ожидается: 404; нет данных и записи в БД. URL: `GET http://localhost:8085/api/no-such/route`.
- [ ] **C325 — Неверные UUID**. Повторить запросы с not-a-uuid в userId/rideId/documentId. Ожидается: Не 2xx: User обычно 400, маршруты Ride/Documents обычно 404; BFF review может дать 400. Нет 500/изменения чужих сущностей.

### Авторизация: без доступа

- [ ] **C117 — E006 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8081/api/users/logout`.
- [ ] **C118 — E008 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8080/api/users/me`.
- [ ] **C119 — E010 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8081/api/users/me`.
- [ ] **C120 — E011 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8080/api/config`.
- [ ] **C121 — E012 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8080/api/rates`.
- [ ] **C122 — E013 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8084/api/rates`.
- [ ] **C123 — E014 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8080/api/scooters`.
- [ ] **C124 — E015 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8080/api/scooters/nearest`.
- [ ] **C125 — E016 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8080/api/scooters/{{plate}}`.
- [ ] **C126 — E017 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8083/api/scooters`.
- [ ] **C127 — E018 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8083/api/scooters/nearest`.
- [ ] **C128 — E019 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8083/api/scooters/{{plate}}`.
- [ ] **C129 — E020 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8080/api/rides`.
- [ ] **C130 — E021 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8080/api/rides`.
- [ ] **C131 — E022 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `PUT http://localhost:8080/api/rides/{{rideId}}/position`.
- [ ] **C132 — E023 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `PUT http://localhost:8080/api/rides/{{rideId}}/finish`.
- [ ] **C133 — E024 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `PUT http://localhost:8080/api/rides/{{rideId}}/review`.
- [ ] **C134 — E025 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8080/api/rides/{{rideId}}/pay`.
- [ ] **C135 — E026 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8084/api/rides`.
- [ ] **C136 — E027 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8084/api/rides`.
- [ ] **C137 — E028 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `PUT http://localhost:8084/api/rides/{{rideId}}/position`.
- [ ] **C138 — E029 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `PUT http://localhost:8084/api/rides/{{rideId}}/finish`.
- [ ] **C139 — E030 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `PUT http://localhost:8084/api/rides/{{rideId}}/review`.
- [ ] **C140 — E031 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8084/api/rides/{{rideId}}/pay`.
- [ ] **C141 — E032 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8080/api/documents/generate`.
- [ ] **C142 — E033 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8080/api/documents?limit=50&offset=0`.
- [ ] **C143 — E034 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8080/api/documents/{{documentId}}`.
- [ ] **C144 — E035 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8080/api/documents/{{documentId}}/download`.
- [ ] **C145 — E036 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `DELETE http://localhost:8080/api/documents/{{documentId}}`.
- [ ] **C146 — E037 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8085/api/documents/generate`.
- [ ] **C147 — E038 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8085/api/documents?limit=50&offset=0`.
- [ ] **C148 — E039 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8085/api/documents/{{documentId}}`.
- [ ] **C149 — E040 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8085/api/documents/{{documentId}}/download`.
- [ ] **C150 — E041 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `DELETE http://localhost:8085/api/documents/{{documentId}}`.
- [ ] **C151 — E042 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8081/api/users/validate`.
- [ ] **C152 — E043 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8081/api/users`.
- [ ] **C154 — E044 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8081/api/users/{{userA}}`.
- [ ] **C155 — E045 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8081/api/users/{{userA}}/blocked`.
- [ ] **C156 — E046 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8081/api/users/blocked?limit=50&offset=0`.
- [ ] **C158 — E047 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8081/api/users/reasons`.
- [ ] **C160 — E048 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8081/api/users/{{userA}}/ban`.
- [ ] **C162 — E049 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8081/api/users/{{userA}}/unban`.
- [ ] **C164 — E050 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8082/api/moderation/users/{{userA}}/status`.
- [ ] **C165 — E051 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8082/api/moderation/reasons`.
- [ ] **C167 — E052 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8082/api/moderation/users?blocked=true&limit=50&offset=0`.
- [ ] **C169 — E053 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8082/api/moderation/users/{{userA}}/ban`.
- [ ] **C171 — E054 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `POST http://localhost:8082/api/moderation/users/{{userA}}/unban`.
- [ ] **C173 — E055 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 401; нет данных/изменений. URL: `GET http://localhost:8084/api/rides/{{rideId}}`.
- [ ] **C174 — E056 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 403: нет прав, резерв/позиция не изменились. URL: `POST http://localhost:8083/api/scooters/{{internalPlate}}/reserve`.
- [ ] **C175 — E057 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 403: нет прав, резерв/позиция не изменились. URL: `POST http://localhost:8083/api/scooters/{{internalPlate}}/position`.
- [ ] **C176 — E058 без авторизации**. Отправить в чистом клиенте без Cookie и Authorization; для внутренней ручки без X-Service-Key. Ожидается: 403: нет прав, резерв/позиция не изменились. URL: `POST http://localhost:8083/api/scooters/{{internalPlate}}/release`.

### Авторизация: USER вместо ADMIN

- [ ] **C153 — E043 обычный пользователь**. Вызвать действующим tokenA вместо ADMIN. Ожидается: 403; данные/состояние не меняются. URL: `GET http://localhost:8081/api/users`.
- [ ] **C157 — E046 обычный пользователь**. Вызвать действующим tokenA вместо ADMIN. Ожидается: 403; данные/состояние не меняются. URL: `GET http://localhost:8081/api/users/blocked?limit=50&offset=0`.
- [ ] **C159 — E047 обычный пользователь**. Вызвать действующим tokenA вместо ADMIN. Ожидается: 403; данные/состояние не меняются. URL: `GET http://localhost:8081/api/users/reasons`.
- [ ] **C161 — E048 обычный пользователь**. Вызвать действующим tokenA вместо ADMIN. Ожидается: 403; данные/состояние не меняются. URL: `POST http://localhost:8081/api/users/{{userA}}/ban`.
- [ ] **C163 — E049 обычный пользователь**. Вызвать действующим tokenA вместо ADMIN. Ожидается: 403; данные/состояние не меняются. URL: `POST http://localhost:8081/api/users/{{userA}}/unban`.
- [ ] **C166 — E051 обычный пользователь**. Вызвать действующим tokenA вместо ADMIN. Ожидается: 403; данные/состояние не меняются. URL: `GET http://localhost:8082/api/moderation/reasons`.
- [ ] **C168 — E052 обычный пользователь**. Вызвать действующим tokenA вместо ADMIN. Ожидается: 403; данные/состояние не меняются. URL: `GET http://localhost:8082/api/moderation/users?blocked=true&limit=50&offset=0`.
- [ ] **C170 — E053 обычный пользователь**. Вызвать действующим tokenA вместо ADMIN. Ожидается: 403; данные/состояние не меняются. URL: `POST http://localhost:8082/api/moderation/users/{{userA}}/ban`.
- [ ] **C172 — E054 обычный пользователь**. Вызвать действующим tokenA вместо ADMIN. Ожидается: 403; данные/состояние не меняются. URL: `POST http://localhost:8082/api/moderation/users/{{userA}}/unban`.

### Публичные отзывы

- [ ] **C177 — Без входа**. GET /api/reviews на BFF и Ride без Cookie/Authorization. Ожидается: 200; только отзывы FINISHED/PAID, пустой список допустим.
- [ ] **C178 — Публикация и редактирование**. Оставить отзыв в своей завершённой поездке, изменить его и открыть главную в инкогнито. Ожидается: Виден реальный сохранённый отзыв, обе оценки и комментарии; изменение обновляет ту же запись.
- [ ] **C179 — Публичные поля**. Проверить каждый объект JSON и карточки. Ожидается: Только rideRating, scooterRating, rideComment, scooterComment, reviewedAt; автор отображается анонимно, личных данных поездки нет.
- [ ] **C180 — Пагинация и сортировка**. limit=1 offset=0/1, Все отзывы → Показать ещё. Ожидается: Новые отзывы первыми, страницы работают; limit=0/101 и offset=-1 → 400.
- [ ] **C181 — Пустота/ошибка/XSS**. Проверить отсутствие отзывов, остановку Ride, HTML в комментарии, повтор загрузки. Ожидается: Нет статических фальшивых отзывов, явные пустое состояние/ошибка, HTML отображается текстом; повтор работает.

### Регистрация и вход

- [ ] **C182 — 16 лет ровно**. birthDate=сегодня UTC минус 16 лет; проверить BFF и напрямую User. Ожидается: 201; отдельно использовать разные свободные логины.
- [ ] **C183 — Младше 16**. Дата на 1 день позже границы, сегодняшняя дата. Ожидается: 400 на BFF и User; пользователь не создан.
- [ ] **C184 — Невалидные даты**. Пустая/null дата, будущая, 2000-02-30, 1899-12-31, не YYYY-MM-DD. Ожидается: 400; в браузере тоже ошибка.
- [ ] **C185 — ФИО**. Иван Петров; Иван Петров Иванович; Анна-Мария Петрова; John O’Connor. Ожидается: 201 для нового логина; имя читаемо в профиле.
- [ ] **C186 — Неверное ФИО**. huIBSOIAHUD PAISUDB AOSIDHBN; Иван123 Петров; Иван Pетров; одно слово; HTML; >200 символов. Ожидается: 400; проверяется формат, подлинность имени по словарю не определяется.
- [ ] **C187 — Логин**. 2/65 символов, кириллица, пробелы, /; нормальный 3/64 и IVAN vs ivan. Ожидается: Невалидный → 400; регистр нормализуется; повтор уже занятого → 409.
- [ ] **C188 — Пароль**. 7 символов; 73 ASCII байта; 37 букв я; NUL; затем 36 букв я. Ожидается: 400 для неверного; 72 байта UTF-8 допустимы; UI подтверждает пароль.
- [ ] **C189 — Несовпадение паролей**. Ввести разные пароль/подтверждение в UI. Ожидается: Запрос регистрации не отправлен, показана ошибка.
- [ ] **C190 — Неверный вход**. Неизвестный логин и неверный пароль существующего. Ожидается: 401; форма не закрывается, история недоступна.
- [ ] **C191 — Подмена роли**. При регистрации добавить role=ADMIN; отдельно id чужого пользователя. Ожидается: role не повышает права: создан USER; прямой User может вернуть 400 для неизвестного поля id, BFF его не пересылает.
- [ ] **C192 — exists неверный**. Без login; login=ab; валидный новый логин. Ожидается: 400, 400, затем 200 exists=false.
- [ ] **C193 — Ошибка последующего login**. Регистрация успешна, login временно недоступен. Ожидается: UI сообщает аккаунт создан, предлагает войти; повторная регистрация не запускается.
- [ ] **C194 — Cookie и обновление**. Войти через BFF; проверить HttpOnly JSESSIONID; обновить страницу. Ожидается: Сессия сохраняется; пароля/JWT в localStorage BFF нет.
- [ ] **C195 — Выход повторно**. POST BFF /api/auth/logout без токена/повторно; User logout тем же отозванным Bearer. Ожидается: BFF 204; User 401. GET /logout только подтверждает выход.

### Каталог и геолокация

- [ ] **C196 — READY фильтр**. BFF GET /api/scooters?status=READY и ?available=true; Scooter ?status=READY. Ожидается: 200, только READY. BFF остальные status не проксирует как фильтр; остальные статусы проверять напрямую Scooter.
- [ ] **C197 — Статусы Scooter**. Прямой Scooter ?status=IN_RIDE, CHARGING, REPAIR; затем INVALID. Ожидается: 200 для четырёх допустимых статусов, 400 для INVALID.
- [ ] **C198 — Порядок nearest**. POST nearest с реальными lon/lat; сравнить несколько точек. Ожидается: READY, distance в метрах по возрастанию; в UI м/км.
- [ ] **C199 — Неверные GPS**. Для nearest X=181, Y=91, число строкой, отсутствующая координата. Ожидается: 400; координаты пользователя не подставляются фиктивно.
- [ ] **C200 — Неизвестный номер**. GET карточки заведомо несуществующего номера на BFF и Scooter. Ожидается: 404.
- [ ] **C201 — Геолокация разрешена**. Разрешить, загрузить каталог. Ожидается: POST nearest; X=longitude, Y=latitude; поля ручного ввода отсутствуют.
- [ ] **C202 — Геолокация запрещена**. Запретить и войти. Ожидается: Реальный общий каталог без расстояний/сортировки; профиль/история доступны.
- [ ] **C203 — Геолокация недоступна**. Отключить службу определения местоположения/смоделировать timeout. Ожидается: Общий каталог и понятное объяснение; кнопки не остаются заблокированы навсегда.
- [ ] **C204 — Повтор геолокации**. Разрешить в настройках и нажать Повторить геолокацию. Ожидается: Появились ближайшие и расстояния, кнопка повторной попытки скрылась.
- [ ] **C205 — Старт без GPS**. Запретить геолокацию, выбрать READY и начать. Ожидается: 201; начальная позиция из свежей карточки самоката, не выдуманный GPS пользователя.
- [ ] **C206 — Завершение без GPS**. На ACTIVE запретить GPS, нажать завершение; отменить/подтвердить сохранённую позицию. Ожидается: Отмена не делает finish; подтверждение завершает по сохранённой позиции.
- [ ] **C207 — Неизвестная сохранённая позиция**. Старая поездка без валидных start/last координат и без GPS. Ожидается: Запасная кнопка завершения не появляется; требуется доступ к геолокации.
- [ ] **C208 — Закрытие во время GPS**. Закрыть форму, пока браузер ищет позицию. Ожидается: Поздний callback не начинает и не завершает поездку.
- [ ] **C209 — Избранное**. Добавить/убрать, обновить, войти другим USER. Ожидается: Сохраняется локально отдельно по userId; лишних сообщений о браузере нет; не считается серверной синхронизацией.
- [ ] **C210 — Нет самокатов**. Все READY заняты либо ответ []. Ожидается: Понятное пустое состояние, нет демо-самокатов.

### Поездки и оплата

- [ ] **C211 — Чтение по userId**. Прямой Ride GET /api/rides?userId={{userA}} своим A; затем чужим B. Ожидается: A 200; B 403. BFF GET /api/rides всегда использует текущего пользователя.
- [ ] **C212 — Чужая поездка**. B вызывает direct Ride GET id, PUT position/finish/review, POST pay поездки A; также операции BFF. Ожидается: 403; поездка A не меняется, самокат не освобождён.
- [ ] **C213 — Неизвестный rideId**. Корректный случайный UUID в get/finish/position/pay/review. Ожидается: 404; для GET одной поездки используйте прямой Ride, у BFF такой GET не реализован.
- [ ] **C214 — Второй старт USER**. У A уже ACTIVE; попытаться взять другой READY. Ожидается: 409; не появляются две ACTIVE у одного пользователя.
- [ ] **C215 — Два пользователя один самокат**. Одновременно A и B начинают на одном READY. Ожидается: Только один 201, другой 409; нет двойной резервации.
- [ ] **C216 — Занятый самокат**. Начать на IN_RIDE/REPAIR/CHARGING. Ожидается: 409, ACTIVE не создана.
- [ ] **C217 — Неверные старт/позиция/финиш**. Пустой номер; отсутствующие обязательные posX/posY в position/finish; строка вместо числа. Ожидается: 400; не меняется текущая поездка.
- [ ] **C218 — Неактивная position**. PUT position для FINISHED/PAID. Ожидается: 409.
- [ ] **C219 — Идемпотентный finish**. Завершить; повторить с теми же координатами; затем с другими. Ожидается: 200 с теми же итогами; другие координаты → 409.
- [ ] **C220 — Учебный тариф**. Завершить почти сразу; отдельная поездка после >60 секунд. Ожидается: Минимум 58 RUB; после 60 секунд следующая начатая минута 66 RUB при неизменённом тарифе.
- [ ] **C221 — Идемпотентный pay**. POST pay несколько раз. Ожидается: 200 PAID, та же сумма и исходный paidAt; повторного списания нет.
- [ ] **C222 — Оплата ACTIVE**. POST pay активной поездки. Ожидается: 409.
- [ ] **C223 — Провайдер выключен**. На отдельном тестовом запуске PAYMENT_MODE не educational; попытка pay FINISHED. Ожидается: 503, поездка остаётся FINISHED; после возврата настройки можно оплатить.
- [ ] **C224 — Не менять сумму**. Изменить тело pay/поля amount/rateId на клиенте. Ожидается: Итог считается сервером, подмена суммы не принимается в расчёт.
- [ ] **C225 — История и перезапуск**. Оплатить и перезапустить Ride/остальные сервисы без удаления БД. Ожидается: История, PAID, сумма и отзывы сохранены.

### Отзывы

- [ ] **C226 — До завершения**. PUT review для ACTIVE. Ожидается: 409; кнопка отзыва в UI отсутствует.
- [ ] **C227 — Две оценки**. Сохранить оценки 1 и 5 и комментарии на FINISHED, затем PAID. Ожидается: 200, review в ответе/истории; доступны до и после оплаты.
- [ ] **C228 — Ошибочные оценки**. 0, 6, 1.5, строка 5, null, отсутствие одной оценки. Ожидается: 400, старый отзыв не повреждён.
- [ ] **C229 — Комментарии**. Пустые/отсутствуют; 1000 символов; 1001; число вместо строки; NUL. Ожидается: Допустимые → 200; неверные → 400.
- [ ] **C230 — Редактирование**. Повторный PUT для того же rideId. Ожидается: Один отзыв изменён, дубликатов нет.
- [ ] **C231 — XSS отзыв**. Комментарий <img src=x onerror=alert(1)>. Ожидается: Сохранён/показан текстом; HTML/JS не исполняется.
- [ ] **C232 — Сохранность**. Перезапустить Ride и открыть историю. Ожидается: review остался; данные оплаты не изменились.

### Документы

- [ ] **C233 — Чек BFF**. POST generate с RIDE_PAYMENT и sourceId своей PAID. Ожидается: 200 PDF; BFF сам подставляет текущий userId.
- [ ] **C234 — Чек Documents**. POST generate напрямую с userId=A, RIDE_PAYMENT, sourceId. Ожидается: 200 PDF; без userId → 400, чужой userId → 403.
- [ ] **C235 — Совместимость RECEIPT**. Использовать docType=RECEIPT вместо RIDE_PAYMENT. Ожидается: 200 PDF при прочих верных полях, тип нормализован.
- [ ] **C236 — Неоплаченная поездка**. Чек своей ACTIVE/FINISHED. Ожидается: 409; нет фиктивного чека оплаты.
- [ ] **C237 — Чужой чек**. sourceId поездки B с токеном A. Ожидается: 403.
- [ ] **C238 — Пустая/реальная история**. USER_HISTORY с from/to UTC: один день, период с поездкой, без поездок. Ожидается: 200 PDF; даты и количество строк соответствуют периоду.
- [ ] **C239 — Плохой период**. from>to, несуществующая дата, отсутствующий from/to. Ожидается: 400.
- [ ] **C240 — Уведомление о блокировке**. USER_BLOCKED: сначала без блокировки, потом после ADMIN ban. Ожидается: Без блокировки 409; во время блокировки 200 PDF.
- [ ] **C241 — Снимок блокировки**. Сохранить documentId уведомления, снять блокировку, скачать старый PDF. Ожидается: Историческое уведомление доступно из снимка и не становится новым текущим баном.
- [ ] **C242 — Снимок истории**. Создать историю, совершить ещё одну поездку, скачать старый документ. Ожидается: Старый документ не изменил историческое содержание.
- [ ] **C243 — Пагинация**. GET docs limit=1 offset=0/1; limit=0/101, offset=-1/строка. Ожидается: 200 с корректными страницами; неверные параметры 400.
- [ ] **C244 — Чужие документы**. B читает meta/download/DELETE документа A. Ожидается: 403; документ A не удалён.
- [ ] **C245 — Случайный документ**. Корректный неизвестный UUID в meta/download/DELETE. Ожидается: 404.
- [ ] **C246 — Удаление**. DELETE свой дважды; затем GET/meta/download и список. Ожидается: 204 оба DELETE; GET/download 404; из списка исчез.
- [ ] **C247 — FAILED/PENDING**. Повторить download FAILED/PENDING; в UI повторить генерацию; просроченный PENDING. Ожидается: download 409; повтор создаёт новую запись; PENDING старше 2 мин при чтении истории становится FAILED.
- [ ] **C248 — Неверный тип/поля**. Неизвестный docType; отсутствующий sourceId у чека; прямой Documents sourceId у USER_HISTORY. Ожидается: 400; BFF sourceId у неплатёжного типа не пересылает.
- [ ] **C249 — Файл PDF**. Открыть скачанный файл, кириллица, сумма, номер самоката, период, владелец. Ожидается: Настоящий читаемый PDF, не страница ошибки .pdf; учебный характер оплаты в PDF допустим.
- [ ] **C250 — Ключ и шрифт**. На отдельной конфигурации неверный JWT_PUBLIC_KEY_PATH и PDF_FONT_PATH, затем исправить. Ожидается: Нет ложного 200 PDF; ошибка конфигурации; после восстановления и рестарта PDF работает.

### Админка и блокировки

- [ ] **C251 — ADMIN через BFF**. Зарегистрировать отдельный admin.check, повысить через штатный CLI, войти заново. Ожидается: ADMIN уходит на 8082/admin/users.html, USER остаётся в BFF; старый токен после повышения недействителен.
- [ ] **C252 — Таблица**. Проверить логин/ФИО/статус/причину/даты/действия; фильтры Все/Активные/Заблокированные. Ожидается: Данные совпадают с User и Moderation, фильтры и кнопка обновления работают.
- [ ] **C253 — Все блокировки**. Больше страницы (или limit=1/offset) блокировок. Ожидается: Пагинация не теряет записи; админка читает страницы User /blocked.
- [ ] **C254 — Справочник**. Открыть ban; выбрать временную и постоянную причины из /reasons. Ожидается: Показаны описание и срок, без причины подтвердить нельзя; durationSeconds=null значит навсегда.
- [ ] **C255 — Отмена ban/unban**. Открыть окна и отменить кнопкой, Escape, кликом снаружи. Ожидается: Состояние не меняется; запрос не отправлен.
- [ ] **C256 — Блокировка через панель**. ADMIN блокирует тестового A, затем обновляет панель/профиль A. Ожидается: 200; причина/blockedAt/blockedUntil совпадают, blocked=true.
- [ ] **C257 — Повторная блокировка**. Повторить ban с той же и другой причиной, пока активен первый бан. Ожидается: 200 с существующим баном; причина и срок первого не переписываются.
- [ ] **C258 — Временная блокировка**. Проверить срок относительно blockedAt; на отдельной тестовой записи дождаться/моделировать истечение. Ожидается: После истечения status возвращает blocked=false; существующий активный бан не снимается раньше.
- [ ] **C259 — Права USER**. A свой status/blocked; A чужой status, /reasons, список, ban/unban. Ожидается: Свой 200; чужой/администраторские операции 403.
- [ ] **C260 — Неверная причина**. Пустая причина, UNKNOWN_REASON, свободный текст. Ожидается: 400 через User и Moderation.
- [ ] **C261 — Неверная пагинация**. User /blocked и Moderation /users: limit=0/501, offset=-1; direct blocked=false. Ожидается: 400; предел limit=500, blocked допускается только true либо отсутствует.
- [ ] **C262 — UUID без пользователя**. User GET/ban/unban для корректного несуществующего UUID с ADMIN. Ожидается: 404 в User. Прямой Moderation сам существование User не проверяет; его status может вернуть blocked=false.
- [ ] **C263 — Заблокированный USER**. Вход заблокированного A; чтение истории; новый старт. Ожидается: Вход/история доступны, старт 403.
- [ ] **C264 — Бан во время поездки**. A начинает; ADMIN банит; A завершает, оплачивает, скачивает чек/уведомление. Ожидается: Завершение и оплата не блокируются; новый старт запрещён.
- [ ] **C265 — Разблокировка**. ADMIN снимает бан; повторяет unban; A начинает новую поездку. Ожидается: 200 blocked=false оба раза; новый старт разрешён, если нет ACTIVE.
- [ ] **C266 — Выход ADMIN**. Нажать Выход в панели; вернуться/обновить; старый токен вызвать на 8081/8082. Ожидается: Токен отозван; панель больше не отдаёт данные; 401 на API. BFF-сессия с этим же JWT также непригодна.
- [ ] **C267 — Отказ выхода ADMIN**. Выключить User и нажать Выход в панели. Ожидается: Показана ошибка отзыва токена, нет ложного успешного выхода; после запуска повторить.
- [ ] **C268 — Отказ причин/списка**. Выключить Moderation/User, обновить панель. Ожидается: Показано сообщение, нет выдуманного пустого успешного списка/ложного успешного ban.
- [ ] **C269 — CORS панели**. Из origin 8082 запросить 8081 с Authorization; проверить OPTIONS и запрос. Ожидается: OPTIONS 204 с разрешённым Origin/Authorization/Content-Type; запрос проходит.

### Внутренний Scooter

- [ ] **C270 — Неверный service key**. Каждая reserve/position/release с отсутствующим/неверным X-Service-Key, даже с обычным JWT. Ожидается: 403; состояние не изменено.
- [ ] **C271 — Повтор reserve**. На выделенном самокате повторить тот же internalRideId/userId. Ожидается: 200; одна резервация; другой пользователь/самокат с тем же id → 409.
- [ ] **C272 — Повтор release**. Освободить выделенный самокат и повторить release с тем же id. Ожидается: 200; повтор не освобождает более новую резервацию.
- [ ] **C273 — Освобождение раньше reserve**. На выделенном id вызвать release, затем запоздалый reserve. Ожидается: Запоздалый reserve 409, закрытая резервация не оживает.
- [ ] **C274 — Старая позиция**. Попытаться position/release с чужим старым internalRideId при новой резервации. Ожидается: 409; новая резервация/координаты не повреждены.
- [ ] **C275 — Неполные координаты**. release только posX, position без posY, неверный UUID. Ожидается: 400; потом обязательно освободить тестовую резервацию.

### HTTP и безопасность

- [ ] **C276 — Неверный метод**. С действующей нужной ролью: GET /api/scooters/nearest, GET /api/documents/generate; POST на read-only route. Ожидается: 405 на известных операциях; на неподдерживаемом пути может быть 404; без авторизации сначала возможен 401.
- [ ] **C277 — JSON Content-Type**. POST регистрации, nearest, position/review, generate с текстовым телом и без application/json. Ожидается: 415 на ручках с JSON-телом; auth должен быть валиден, кроме публичной регистрации.
- [ ] **C278 — Сломанный JSON**. Обрезанный JSON, массив вместо объекта, число строкой на JSON-ручках. Ожидается: 400; нет 500 и побочных записей.
- [ ] **C279 — Дубли JSON-полей**. Повторить ключи login/role/rideRating в теле на BFF и напрямую. Ожидается: User/Scooter/Ride/Documents строгие парсеры отклоняют дубли (400). BFF JSON-P и прямой Moderation не гарантируют отбрасывание дублей; фиксировать фактический ответ, не считать это одинаковой политикой.
- [ ] **C280 — Большое тело**. Только ручки, читающие JSON: User >16384 байт; BFF/Scooter/Ride/Documents >65536. Ожидается: User/Scooter/Ride/Documents → 413; BFF при своём лимите → 400 (ниже лимита BFF может передать 413 User). У прямого Moderation прикладного ограничения тела сейчас нет; не ожидайте там гарантированного 413.
- [ ] **C281 — CSRF BFF**. POST/PUT/DELETE с чужим Origin или Sec-Fetch-Site=cross-site. Ожидается: 403; изменение не выполнено.
- [ ] **C282 — Не-JSON DELETE**. DELETE документа без тела; затем с текстовым непустым телом. Ожидается: Пустое тело допускается; непустое не-JSON тело 415 на BFF.
- [ ] **C283 — Недействительные JWT**. Без токена; мусор; истёкший; изменённый payload/подпись; чужой issuer; отозванный. Ожидается: Защищённые ручки не дают данные/изменения; для нормально сформированного запроса 401.
- [ ] **C284 — Отзыв во всех сервисах**. Сохранить tokenA, выполнить logout, повторить BFF me, User validate, Scooter list, Ride history, Documents history, Moderation own status. Ожидается: 401 на всех шести API, новый вход даёт рабочий новый токен.
- [ ] **C285 — Приоритет токена BFF**. Сессия A, передать Authorization: Bearer B; затем неверный явный Bearer при сессии A. Ожидается: Явный Bearer имеет приоритет; запрос от B либо 401, не скрытый доступ как A.
- [ ] **C286 — Запрет CORS User**. OPTIONS с неразрешённым Origin/методом/заголовком. Ожидается: 403; не открывать CORS для произвольных origins.
- [ ] **C287 — Закрытые ресурсы**. Открыть /WEB-INF/web.xml и /WEB-INF/classes в браузере на каждом сервисе. Ожидается: Не отдаются (обычно 404; Ride без токена сначала 401).
- [ ] **C288 — Утечки**. Ответы/ошибки/DevTools/localStorage/URL query. Ожидается: Нет паролей, passwordHash, приватного ключа, DB_PASSWORD, SERVICE_API_KEY; BFF JWT в localStorage отсутствует; панель использует sessionStorage.
- [ ] **C289 — Незавершённые изменения после ухода**. Закрыть форму/выйти во время загрузки GPS, каталога, документов. Ожидается: Поздние ответы не открывают закрытые окна, не создают поездку после отмены.

### Отказы и сдача

- [ ] **C290 — User выключен**. Остановить только User, обновить защищённые данные, попробовать действие. Ожидается: 5xx/понятная ошибка зависимости, нет ложной авторизации; после запуска повтор работает.
- [ ] **C291 — Moderation выключен**. Остановить Moderation; проверить профиль/старт/админку; отдельно прямой User /validate. Ожидается: Зависимые операции сообщают ошибку; validate остаётся 204 для действующего JWT, рекурсивного вызова нет.
- [ ] **C292 — Scooter выключен**. Остановить Scooter; каталог/старт; затем включить. Ожидается: Понятная ошибка, без демо-данных; после запуска доступность восстанавливается.
- [ ] **C293 — Ride выключен**. Остановить Ride; история/старт/оплата/отзыв. Ожидается: Понятная ошибка, нет ложного успеха; остальные разделы не падают.
- [ ] **C294 — Documents выключен**. Остановить Documents; PDF и история документов; затем включить. Ожидается: Понятная ошибка, HTML не скачан как PDF; поездки продолжают работать.
- [ ] **C295 — БД недоступна**. На отдельном тестовом запуске остановить PostgreSQL. Ожидается: Ошибки хранилища/запуска не маскируются успешными действиями; после восстановления данные сохранены.
- [ ] **C296 — Потерянный ответ finish**. Выключить Scooter во время finish, вернуть и повторить/дождаться recovery. Ожидается: Сумма/время первого finish не растут при повторе, самокат освобождён, нет второй поездки.
- [ ] **C297 — Повтор подготовки**. Повторить prepare-local с теми же параметрами, не удаляя local-run. Ожидается: Миграции повторно не ломаются; прежние ключи, пользователи, поездки, отзывы, документы сохранены.
- [ ] **C298 — Чистый браузер**. Инкогнито/новый профиль, затем разрешить/запретить геолокацию; refresh и Назад. Ожидается: Нет зависимости от чужой старой сессии/кэша; понятное поведение обеих ветвей.
- [ ] **C299 — Консоль и сеть**. Пройти UI, DevTools Console/Network, перезапустить сервисы. Ожидается: Нет uncaught JS, отсутствующих собственных CSS/JS/шрифтов, смешанных портов/context path.
- [ ] **C300 — Завершение проверки**. Снять тестовые блокировки и освободить internal-резервации; оплатить/закончить активные тестовые поездки. Ожидается: Стенд готов к демонстрации; причины/тарифы/основные данные не удалены.

### UI без отдельного URL

- [ ] **C301 — Карточки и каталог**. Открыть карточку, Показать все/меньше, обновить. Ожидается: Данные текущего самоката; неактивные нельзя арендовать; список и счётчик соответствуют API.
- [ ] **C302 — Галерея и витрина отзывов**. Открыть каждую фотографию, пролистать отзывы, Все отзывы, закрыть. Ожидается: Диалоги закрываются крестиком/вне окна; прокрутка страницы восстанавливается.
- [ ] **C303 — Модальные разделы**. Вход/Регистрация, Профиль, Мои поездки, Избранное, Документы, старт/финиш/оплата/отзыв. Ожидается: Все кнопки доступны по состоянию и роли; после закрытия окно не открывается поздним ответом.
- [ ] **C304 — Показать пароль**. Переключить видимость в формах входа/регистрации. Ожидается: Тип поля меняется, значение не теряется и не выводится в URL.
- [ ] **C305 — Неожиданные строки**. HTML в данных самоката/имени/отзыва при тестовой подмене ответа. Ожидается: Отображается текстом, без исполнения и сломанной разметки.

### HTTP дополнительные методы

- [ ] **C306 — BFF HEAD**. Отправить HEAD на существующую GET-ручку с валидным tokenA. Ожидается: 200 без тела. URL: `HEAD http://localhost:8080/api/users/me`.
- [ ] **C307 — BFF OPTIONS**. Отправить OPTIONS с нужным токеном, отдельно User CORS preflight из 8082. Ожидается: 200 и Allow; без изменения данных. URL: `OPTIONS http://localhost:8080/api/users/me`.
- [ ] **C308 — User HEAD**. Отправить HEAD на существующую GET-ручку с валидным tokenA. Ожидается: 405: этот маршрутизатор HEAD не поддерживает. URL: `HEAD http://localhost:8081/api/users/me`.
- [ ] **C309 — User OPTIONS**. Отправить OPTIONS с нужным токеном, отдельно User CORS preflight из 8082. Ожидается: 204; без изменения данных. URL: `OPTIONS http://localhost:8081/api/users/me`.
- [ ] **C310 — Moderation HEAD**. Отправить HEAD на существующую GET-ручку с валидным tokenA. Ожидается: 405: этот маршрутизатор HEAD не поддерживает. URL: `HEAD http://localhost:8082/api/moderation/users/{{userA}}/status`.
- [ ] **C311 — Moderation OPTIONS**. Отправить OPTIONS с нужным токеном, отдельно User CORS preflight из 8082. Ожидается: 405; без изменения данных. URL: `OPTIONS http://localhost:8082/api/moderation/users/{{userA}}/status`.
- [ ] **C312 — Scooter HEAD**. Отправить HEAD на существующую GET-ручку с валидным tokenA. Ожидается: 404 на корневом списке Scooter для HEAD. URL: `HEAD http://localhost:8083/api/scooters`.
- [ ] **C313 — Scooter OPTIONS**. Отправить OPTIONS с нужным токеном, отдельно User CORS preflight из 8082. Ожидается: 404 для OPTIONS корневого списка Scooter; без изменения данных. URL: `OPTIONS http://localhost:8083/api/scooters`.
- [ ] **C314 — Ride HEAD**. Отправить HEAD на существующую GET-ручку с валидным tokenA. Ожидается: 405: этот маршрутизатор HEAD не поддерживает. URL: `HEAD http://localhost:8084/api/rides`.
- [ ] **C315 — Ride OPTIONS**. Отправить OPTIONS с нужным токеном, отдельно User CORS preflight из 8082. Ожидается: 405; без изменения данных. URL: `OPTIONS http://localhost:8084/api/rides`.
- [ ] **C316 — Documents HEAD**. Отправить HEAD на существующую GET-ручку с валидным tokenA. Ожидается: 405: этот маршрутизатор HEAD не поддерживает. URL: `HEAD http://localhost:8085/api/documents`.
- [ ] **C317 — Documents OPTIONS**. Отправить OPTIONS с нужным токеном, отдельно User CORS preflight из 8082. Ожидается: 405; без изменения данных. URL: `OPTIONS http://localhost:8085/api/documents`.

### URL алиасы

- [ ] **C318 — BFF /api/scooters/**. GET с завершающим слешем. Ожидается: 200, тот же список что без завершающего слеша. URL: `GET http://localhost:8080/api/scooters/`.
- [ ] **C319 — BFF /api/rides/**. GET с завершающим слешем. Ожидается: 200, тот же список что без завершающего слеша. URL: `GET http://localhost:8080/api/rides/`.
- [ ] **C320 — BFF /api/documents/**. GET с завершающим слешем. Ожидается: 200, тот же список что без завершающего слеша. URL: `GET http://localhost:8080/api/documents/`.
- [ ] **C321 — User /api/users/**. GET с завершающим слешем. Ожидается: 200, тот же список что без завершающего слеша. URL: `GET http://localhost:8081/api/users/`.
- [ ] **C322 — Scooter /api/scooters/**. GET с завершающим слешем. Ожидается: 200, тот же список что без завершающего слеша. URL: `GET http://localhost:8083/api/scooters/`.
- [ ] **C323 — Ride /api/rides/**. GET с завершающим слешем. Ожидается: 200, тот же список что без завершающего слеша. URL: `GET http://localhost:8084/api/rides/`.
- [ ] **C324 — Documents /api/documents/**. GET с завершающим слешем. Ожидается: 200, тот же список что без завершающего слеша. URL: `GET http://localhost:8085/api/documents/`.

### Навигация браузера

- [ ] **C326 — BFF служебная страница**. Ввести адрес в адресную строку или отправить Accept: text/html. Ожидается: 403 HTML с понятной ссылкой на BFF, не JSON. При Accept: application/json API продолжает проверять права токена. URL: `GET http://localhost:8080/api/users/me`.
- [ ] **C327 — User служебная страница**. Ввести адрес в адресную строку или отправить Accept: text/html. Ожидается: 403 HTML с понятной ссылкой на BFF, не JSON. При Accept: application/json API продолжает проверять права токена. URL: `GET http://localhost:8081/api/users/me`.
- [ ] **C328 — Moderation служебная страница**. Ввести адрес в адресную строку или отправить Accept: text/html. Ожидается: 403 HTML с понятной ссылкой на BFF, не JSON. При Accept: application/json API продолжает проверять права токена. URL: `GET http://localhost:8082/api/moderation/reasons`.
- [ ] **C329 — Scooter служебная страница**. Ввести адрес в адресную строку или отправить Accept: text/html. Ожидается: 403 HTML с понятной ссылкой на BFF, не JSON. При Accept: application/json API продолжает проверять права токена. URL: `GET http://localhost:8083/api/scooters`.
- [ ] **C330 — Ride служебная страница**. Ввести адрес в адресную строку или отправить Accept: text/html. Ожидается: 403 HTML с понятной ссылкой на BFF, не JSON. При Accept: application/json API продолжает проверять права токена. URL: `GET http://localhost:8084/api/rides`.
- [ ] **C331 — Documents служебная страница**. Ввести адрес в адресную строку или отправить Accept: text/html. Ожидается: 403 HTML с понятной ссылкой на BFF, не JSON. При Accept: application/json API продолжает проверять права токена. URL: `GET http://localhost:8085/api/documents`.

## Как трактовать ошибки и ограничения

- 400 — данные запроса неверны; 401 — нет действующей авторизации; 403 — недостаточно прав/недопустимый Origin; 404 — нет ручки/сущности; 405 — неверный метод; 409 — конфликт состояния; 413/415 — размер/тип тела; 5xx — ошибка сервиса/зависимости. В зависимости от порядка фильтров запрос без авторизации может получить 401 прежде, чем проверяется путь/тело/метод.
- BFF поддерживает READY/available фильтр каталога; произвольный status через BFF не проверяется/не передаётся. Все четыре статуса проверяйте напрямую Scooter. GET /api/config BFF тоже требует авторизацию.
- User проверяет наличие пользователя при ban/unban. Прямой Moderation хранит блокировки UUID без самостоятельной проверки User; не ожидайте от него такого же 404 для неизвестного пользователя. Не создавайте там фиктивные UUID, кроме отдельного теста с последующей очисткой.
- BFF ограничивает читаемое JSON-тело 64 КиБ с кодом 400; User — 16 КиБ с 413; Scooter/Ride/Documents — 64 КиБ с 413. Прямой Moderation сейчас читает тело без собственного ограничения и не требует Content-Type для ban; его нельзя описывать как такой же строгий JSON-вход. Проверки дублей полей BFF/Moderation также отличаются от строгих парсеров остальных сервисов.
- Точные GPS-границы проверяются nearest. Общие position/finish в Ride исторически допускают более широкий числовой диапазон; клиент передаёт валидный GPS. Полноценного геозонирования/проверки физического положения/маршрута нет.
- При отказе геолокации старт использует позицию самоката, финиш — последнюю сохранённую позицию только после явного подтверждения. Нельзя демонстрировать это как проверку фактической парковки по GPS.
- Временный бан учитывается при чтении статуса; список может кратковременно показывать запись до проверки истечения её статуса. Самостоятельного UI удаления аккаунтов/изменения ФИО нет. Избранное не серверное.
- Общий REST-документ пока не заменён этим чек-листом. Текущий реестр получен из исполняемого кода; расхождения с прежним MD должны быть согласованы отдельно после вашей проверки.

## Что отправить после проверки

Заполненный CSV, версия/коммит проекта и для каждого FAIL: ID пункта, метод/URL, роль, тело без секретов, ожидаемый/фактический статус, error из ответа и соответствующий Caused by из лога. Не присылайте токены, пароли, local-run, приватные ключи. Если меняли код локально — архив исходников без target/node_modules/local-run/секретов.

## Дополнение: управление самокатами

Маршруты и правила — API_CONTRACTS.md §14. Изменения и запуск — SCOOTER_ADMIN_UPDATE.md.

| ID | Проверка | Действие | Ожидается |
| --- | --- | --- | --- |
| C332 | E061 GET http://localhost:8080/api/admin/scooters | Войти ADMIN | 200; Неудалённые самокаты |
| C333 | E062 POST http://localhost:8080/api/admin/scooters | Новый уникальный adminPlate; используйте разные номера для BFF и Scooter | 201; READY, заряд100, дробные GPS-координаты |
| C334 | E063 POST http://localhost:8080/api/admin/scooters/{{adminPlate}}/charge | Существующий adminPlate, не IN_RIDE | 200; Заряд100; REPAIR сохраняется, CHARGING→READY |
| C335 | E064 DELETE http://localhost:8080/api/admin/scooters/{{adminPlate}} | После проверки зарядки, не IN_RIDE | 204; Мягкое удаление, повтор204; каталоги и новые резервации не показывают самокат |
| C336 | E065 GET http://localhost:8083/api/admin/scooters | Войти ADMIN | 200; Неудалённые самокаты |
| C337 | E066 POST http://localhost:8083/api/admin/scooters | Новый уникальный adminPlate; используйте разные номера для BFF и Scooter | 201; READY, заряд100, дробные GPS-координаты |
| C338 | E067 POST http://localhost:8083/api/admin/scooters/{{adminPlate}}/charge | Существующий adminPlate, не IN_RIDE | 200; Заряд100; REPAIR сохраняется, CHARGING→READY |
| C339 | E068 DELETE http://localhost:8083/api/admin/scooters/{{adminPlate}} | После проверки зарядки, не IN_RIDE | 204; Мягкое удаление, повтор204; каталоги и новые резервации не показывают самокат |
| C340 | Страница парка ADMIN | Открыть http://localhost:8080/admin/scooters после ADMIN-входа | 200, форма и текущие самокаты; ссылка из панели блокировок работает |
| C341 | USER не управляет парком | Вызвать все4операции через BFF и напрямую Scooter с USER JWT | 403; данные не меняются |
| C342 | БезJWT и внутренний ключ | Вызвать все4операции безJWT, затем только с X-Service-Key | 401; внутренний ключ не заменяет ADMIN JWT |
| C343 | Дробные GPS и невалидные поля | Создать новый номер с37.618423,55.751244; проверить181/91/null/строки/скрипт/длинныйномер | Корректный201; ошибки400; координаты не округляются |
| C344 | Номер занят и удалён | Повторить создание до/после мягкого удаления | 409, исторический номер нельзя переиспользовать |
| C345 | Активная поездка | Начать поездку USER и попробовать зарядить/удалить ADMIN | 409 обеих операций; поездка продолжается и завершается штатно |
| C346 | Ремонт и зарядка | Зарядить тестовые REPAIR и CHARGING | 100%; REPAIR сохраняется, CHARGING становится READY |
| C347 | Удаление и повтор | Удалить свободный тестовый самокат и повторить DELETE | 204 оба раза; get404, нет в каталоге/ближайших |
| C348 | GET ничего не меняет | GET на /api/admin/scooters/{plate}/charge и /{plate} | 405 API; в браузере403HTML; данные прежние |
| C349 | Конкурентное резервирование | Одновременно reserve и delete, затем reserve и charge на отдельном самокате | Операции сериализуются; IN_RIDE невозможно удалить/снять с поездки |
| C350 | Форма управления парком | Через браузер добавить, зарядить, отменить удаление, подтвердить удаление | Список обновляется, ошибки видны, повторные клики на время запроса заблокированы |
