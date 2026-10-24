# Documents Service — Jakarta Servlet / JDBC / WAR

Генерация PDF по запросу BFF: `POST /api/documents/generate` (Authorization: Bearer JWT).
Поддерживаемые `docType`: `RIDE_PAYMENT` (также `RECEIPT` от BFF), `USER_BLOCKED`, `USER_HISTORY`.
PDF создаётся по запросу, не сохраняется. Метаданные сохраняются в PostgreSQL.

## Запуск

1. Java **21**, Maven **3.9+**, Tomcat **10.1+**, PostgreSQL.
2. Создать БД `documents` и выполнить `src/main/resources/schema.sql`.
3. Настроить `src/main/resources/application.properties` или переменные `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `USER_SERVICE_URL`, `RIDE_SERVICE_URL`, `MODERATION_SERVICE_URL`.
4. **Обязательно:** положить RSA public key User Service (PEM SubjectPublicKeyInfo) и задать `JWT_PUBLIC_KEY_PATH=/absolute/path/public.pem`, или `JWT_PUBLIC_KEY` как PEM/base64 DER.
5. Для русского текста PDF: установить локальный TTF-шрифт с кириллицей и задать `PDF_FONT_PATH=/absolute/path/font.ttf`. Файл шрифта в проект не включён.
6. `mvn clean package` → `target/documents.war` → развернуть в Tomcat. Контекст `/documents`, если WAR назван `documents.war`, тогда фактический путь `/documents/api/documents/generate`. Для корня сервера назовите WAR `ROOT.war`.

## Контракты

* `RIDE_PAYMENT`: JSON `{ "userId": "<UUID>", "docType": "RIDE_PAYMENT", "sourceId": "<ride UUID>" }`. Ride должен реализовать `GET /api/rides/{rideId}` и вернуть `status=PAID`, `userId`, `amount`, `paidAt`.
* `USER_BLOCKED`: `{ "userId": "<UUID>", "docType": "USER_BLOCKED" }`. Moderation `GET /api/moderation/users/{userId}/status`; только текущий бан, иначе 409.
* `USER_HISTORY`: `{ "userId": "<UUID>", "docType": "USER_HISTORY", "from": "2026-10-01", "to": "2026-10-31" }`. Ride `GET /api/rides?userId=`.
* User: `GET /api/users/{userId}` с `fullName`, `login`; доступ с переданным JWT нужно подтвердить.

Внешние запросы отправляются с исходным JWT. `userId` тела должен совпадать с JWT. JWT проверяется локально по RS256.

## Ограничения

* Дополнительные `GET /api/documents`, `GET /api/documents/{id}`, `DELETE /api/documents/{id}` пока **не опубликованы** (их внедрение согласовать с BFF).
* Страница/кнопка скачивания — зона BFF.
* Фактическая сборка и end-to-end проверки требуют Maven и запущенных зависимых сервисов. Не считать интеграцию завершённой без них.

## Проверка HTML-шаблонов в IntelliJ IDEA

Поля `doc` и `inlineCss` передаются в Thymeleaf динамически из `DocumentService` / `PdfService`.
IDEA может показывать `Cannot resolve` даже при корректном шаблоне. Это не Java compile errors.
`doc` имеет разный тип в трёх шаблонах, а `inlineCss` загружается из `static/css/document.css`.

При исходящем вызове REST-клиенты добавляют заголовок `Bearer` к чистому JWT.
`DocumentService` отделяет его от исходного заголовка Authorization перед вызовом клиентов.
