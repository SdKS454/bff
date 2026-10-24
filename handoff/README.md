# Готовые файлы для передачи и запуска

Временная ветка `temp/documents-integration-2026-10-24` содержит исходники BFF
в корне репозитория и этот комплект. Бинарные сборки добавлены только в эту ветку.

| Файл / каталог | Что с ним делать |
| --- | --- |
| `moderation-synced.zip` | Передать коллегам Moderation: исправленные исходники, тесты и инструкция интеграции |
| `moderation/` | Те же исправленные исходники в распакованном виде; сборка из этого каталога: `mvn clean verify` |
| `moderation.war` | Готовая сборка исправленного Moderation; отдельный Tomcat на порту 8082 |
| `scooter-bff.war` | Готовая сборка BFF + ScootX; Tomcat на порту 8080 |
| `documents-synced.zip` | Передать коллегам Documents: исправленные исходники, тесты и инструкция |
| `documents/` | Распакованный исправленный проект Documents |
| `documents.war` | Готовый Documents, отдельный Tomcat на 8085 в корне |
| `API_CONTRACTS.md` | Обновлённый общий REST-документ |
| `SHA256SUMS` | Контрольные суммы ZIP, WAR и документа |

Для согласованных адресов сервисы разворачиваются в контексте `/`:
в отдельных Tomcat разместить соответствующий WAR как `webapps/ROOT.war`.
Вход BFF: http://localhost:8080/login.jsp.
Панель Moderation: http://localhost:8082/admin/users.html.
Если развернуть WAR под его исходным именем, Tomcat добавит имя в путь;
такое развёртывание потребует изменения настроек адресов.

Moderation требует собственную PostgreSQL БД, выполнение `moderation/src/main/resources/schema.sql`
и переменные DB_URL/DB_USER/DB_PASSWORD. User Service должен работать на 8081,
с настроенными БД и RSA-ключами. Исходники/сборка User в этот комплект не включены:
используется ранее присланная версия коллег. Documents требует отдельную БД (DB_URL/DB_USERNAME/DB_PASSWORD), публичный ключ
User (JWT_PUBLIC_KEY_PATH), TTF-шрифт с кириллицей (PDF_FONT_PATH) и настройки
адресов User/Moderation/Ride. Подробно: [Documents README](documents/README.md).
Для полного пользовательского сценария также нужны Scooters и Ride на 8083/8084;
Ride должен поддерживать GET конкретной поездки с платёжными полями для чека.

Подробные настройки сервисов, CORS, проверок JWT и инструкция запуска:
[интеграция Moderation](../integrations/moderation/README.md).
В панель входят пользователи с ролью ADMIN, назначенной через User PromoteAdmin.
Редирект с JWT после входа BFF уже поддерживает.

Проверки 24 октября: 63 теста Documents и 85 Java-тестов BFF; сквозной сценарий
настоящих BFF/User/исправленных Moderation и Documents с тремя PostgreSQL БД.
Все три PDF скачаны через ScootX, русский текст читается; бан/разбан, владельцы,
отзыв JWT и сбои зависимостей проверены. Scooters/Ride — HTTP-фикстуры.
Ранее для связки BFF/Moderation также прошли 233 теста.
