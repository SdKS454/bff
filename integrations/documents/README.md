# Синхронизация Documents, BFF, User и Moderation

Источник: `Documents_for_integration.zip`, получен 24 октября 2026.
README прочитан целиком, исходная версия сохранена в README_UPSTREAM.md.
Исправленный проект находится в `handoff/documents`, переносимый diff —
`documents-sync.patch`. Сервис остаётся отдельным WAR с отдельной БД.

## Результат сверки

BFF уже отправляет три поддерживаемых docType, UUID sourceId для чека, даты для
истории и текущий userId из User. Он пересылает исходный JWT и проверяет Content-Type
и сигнатуру PDF. Дополнительный BFF API или новый UI не потребовались.

Подтверждён собственный GET `/api/users/{id}` с обычным JWT настоящего User.
Подтверждён GET статуса бана настоящего исправленного Moderation. Никакой цикл
запросов не появляется: авторизация использует User `/validate`, а не `/me`.

В исходном Documents подпись JWT проверялась, но issuer/обязательный exp и
строгое соответствие sub/userId — нет. Отзыв проверялся только косвенно при более
позднем получении профиля. Теперь RS256/issuer/exp проверяются локально, а User
validate вызывается **до создания метаданных**, с отказом 503 при недоступности
авторизации. Для локальной проверки нужен публичный ключ User; это требование
исходного README сохранено. Приватный ключ остаётся только у User.

## Исправления

- Единая Java 17 / Servlet 6.0 / Tomcat 10.1; обновлены Jackson 2.18.6,
  PostgreSQL driver 42.7.8 и JJWT 0.12.7.
- Явная загрузка JDBC-драйвера в WAR; недоступная БД сообщает 503.
- Проверка UUID/идентификаторов, обязательных полей профиля/статуса бана/поездки.
  Успешный неверный JSON, отсутствующие поля, подмена ID → 502.
- Оплаченная поездка требует amount/currency/finishedAt/paidAt; валюта RUB
  больше не подставляется. Чужая поездка → 403, неоплаченная → 409.
- В списке истории проверяется владелец каждой поездки; неверные данные не
  скрываются за успешной пустой историей. Период включителен, сравнение дат в UTC.
- 401/403/404/409 внешнего API сохраняются; 429/5xx/сеть → 503; остальные
  неожиданные статусы/redirect/неправильный Content-Type/JSON → 502.
  В ошибку не попадают тела запросов, JWT или подробности сетевой конфигурации.
- Строгий JSON: нет повторных полей/хвостов; UUID и даты — строки, не числа/массивы;
  64 KiB лимит тела, 413/415/405, no-store/nosniff и JSON-ошибки.
- Шрифт с кириллицей обязателен: отсутствующий/неподходящий PDF_FONT_PATH
  даёт ошибку, вместо внешне успешного PDF с нечитаемым русским текстом.
- При сбое обновления FAILED сохраняется первоначальная ошибка зависимости.

## Что настроить и что передать командам

Для коллег Documents: исправленные исходники `handoff/documents-synced.zip`
либо `handoff/documents/`, а также инструкция README внутри проекта.
Можно применить `documents-sync.patch` из каталога с исходным pom.xml:

```bash
git apply --check /path/to/bff/integrations/documents/documents-sync.patch
git apply /path/to/bff/integrations/documents/documents-sync.patch
mvn clean verify
```

Documents на 8085 в корне: сборка как ROOT.war. Обязательны отдельная БД/схема,
DB_URL, **DB_USERNAME**, DB_PASSWORD, JWT_PUBLIC_KEY_PATH, PDF_FONT_PATH.
USER_SERVICE_URL=8081, MODERATION_SERVICE_URL=8082, RIDE_SERVICE_URL=8084
(полные HTTP URL, default localhost); JWT_ISSUER совпадает с User.
PDF_FONT_PATH указывает на локальный TTF с кириллицей; ни ключ, ни шрифт не
включены в исходный архив. Список настроек и все примеры — в README проекта.

Для команды Ride остаётся обязательным подтверждение GET `/api/rides/{id}` и
GET `/api/rides?userId=...` с JWT и проверкой владельца. Для чека нужен PAID,
UUID id/userId, numberPlate, startedAt/finishedAt/paidAt, amount и currency.
Эти поля описаны в общем REST-документе. Реальный Ride не получен: его поддержку
не выдаём за проверенную. Автоматическая подстановка данных/валюты не добавлена.
Метаданные GET/DELETE документов не опубликованы, как и сказано в исходном README.

## Проверка

63 теста Documents: JWT/issuer/exp/RS256, строгий ввод, HTTP-клиенты, владельцы,
сбои метаданных и все три PDF с извлечением русского текста. 85 Java-тестов BFF
прошли без изменения production API. Сквозная проверка использует настоящие
BFF + User + исправленный Moderation + Documents, три отдельные PostgreSQL БД,
одноразовые RSA-ключи, Chromium и настоящий PDF renderer.

```bash
mvn clean verify dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt
mvn -f handoff/documents/pom.xml clean verify
npm ci
# User и исправленный Moderation предварительно собрать.
python3 scripts/check-moderation-integration.py \
  --user-project /path/to/user-service \
  --moderation-project /path/to/patched-moderation \
  --documents-project handoff/documents \
  --pdf-font /path/to/CyrillicFont.ttf
```

Нужны Java 17+, Maven, Docker с PostgreSQL 16, OpenSSL, Python 3.9+, Node 18+,
Chromium (Playwright либо CHROMIUM_EXECUTABLE), утилита pdftotext.
Остальная механика стенда описана в `../moderation/README.md`.
Стенд создаёт и удаляет собственный контейнер/БД; к существующим БД не подключается.
Проверены три скачивания через ScootX, бан/разбан, отказ чужому пользователю,
неоплаченная поездка, неполные/неправильные ответы Ride, отзыв JWT и сбой User.
Ride и Scooters в этом сценарии — HTTP-фикстуры, Documents уже настоящий.
