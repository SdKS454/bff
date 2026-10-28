# Точечное обновление: регистрация, отзывы и PDF

База обновления: ветка `temp/gps-update`, коммит `cfe0bf6` (Ubuntu-скрипт уже добавлен).
Ветка с этим обновлением: `temp/reviews-validation`.

## Что изменено

- Регистрация доступна с 16 лет включительно; дата проверяется по UTC в браузере, BFF и User Service. Уже существующие аккаунты и даты рождения не переписываются.
- ФИО: 2–4 слова, от 2 букв в каждом, с заглавной буквы; один алфавит — кириллица или латиница. Допустимы дефис и апостроф внутри составного имени, максимум 200 символов. Например: `Иван Петров`, `Анна-Мария Петрова`, `John O’Connor`. Это проверка формата, а не подлинности имени: выдуманное имя правильного формата определить автоматически нельзя.
- Отдельная кнопка «Найти по координатам» убрана. Определение ближайших самокатов при загрузке/обновлении каталога осталось.
- Убраны уведомление и пояснение о хранении избранного в браузере. Способ хранения не менялся.
- После завершения поездки в истории доступен отзыв: две оценки (поездка и самокат) от 1 до 5 и два необязательных комментария до 1000 символов. Один отзыв на поездку, его можно изменить. Доступен только владельцу FINISHED/PAID-поездки; хранится в Ride DB и не пропадает после перезапуска. Отзывы пока видны владельцу в истории, а не публикуются в общей витрине сайта.
- Documents различает отсутствие настройки JWT, недоступный файл и некорректный RSA-ключ. Поддерживаются UTF-8 BOM и PEM с буквальными `\n` в переменной. Проверка подписи JWT сохраняется. Настройка пути на конкретном ПК по-прежнему необходима.

## Как заменить файлы

1. Остановите BFF, User, Ride и Documents.
2. Скопируйте файлы из ZIP в корень своего проекта с объединением каталогов и заменой одноимённых файлов. **Не удаляйте целиком `src`, `services` или свой проект.** Список обязательных рабочих файлов ниже. Остальные файлы в ZIP — обновлённые тесты, проверочные скрипты и инструкции.
3. Примените миграции тем же скриптом подготовки, с теми же портом и префиксом БД, с которыми создавали стенд. На CachyOS с портом 5432 и установленным DejaVu:

   ```bash
   bash scripts/linux/prepare-local.sh --pg-port 5432 --font-path /usr/share/fonts/TTF/DejaVuSans.ttf
   ```

   Запускайте из корня своего существующего проекта, обычным пользователем, сохранив его папку `local-run`. Скрипт применит `local-stack/migrations/ride/002_reviews.sql` и сохранит существующие базы, ключи и пароль роли. Для нестандартного порта/префикса используйте прежние параметры. Без этой миграции новый Ride не сможет читать историю поездок. Не редактируйте уже применённые старые миграции. Windows: повторите прежнюю команду `scripts/windows/prepare-local.ps1` с прежними параметрами.
4. Пересоберите и повторно разверните **BFF, User, Ride и Documents** в своих четырёх конфигурациях Tomcat. Рекомендуемые версии: JDK 17, Tomcat 10.1, контекст `/`. Scooter и Moderation этим обновлением не меняются.
5. Полностью перезапустите эти четыре сервиса. У Documents переменная `JWT_PUBLIC_KEY_PATH` должна содержать абсолютный путь к **существующему `public.pem` User Service**. Пример: `/home/timerlan/Загрузки/bfff/bff-temp-gps-update/local-run/secrets/public.pem` — начальный `/` обязателен. Не копируйте чужие ключи и не создавайте новый комплект только для Documents.
6. Обновите страницу с очисткой кэша (`Ctrl+Shift+R`). `pom.xml`, пароли и настройки остальных сервисов менять не нужно.

Папка `local-run`, базы данных и индивидуальные настройки IDEA в ZIP не включены.

## API отзывов

BFF: `PUT http://localhost:8080/api/rides/{rideId}/review` с HTTP-сессией BFF либо Bearer-токеном.
Ride: `PUT http://localhost:8084/api/rides/{rideId}/review` с Bearer-токеном.

```json
{
  "rideRating": 5,
  "scooterRating": 4,
  "rideComment": "Хорошая поездка",
  "scooterComment": "Удобный самокат"
}
```

Обе оценки обязательны и должны быть целыми числами 1–5; комментарии можно не передавать. Сервер отвечает `200` с поездкой и полем `review` (`rideRating`, `scooterRating`, `rideComment`, `scooterComment`, `reviewedAt`). История поездок также содержит `review`, до первого отзыва — `null`.

Ошибки: `400` — неверные поля, `401` — нет действующей авторизации, `403` — чужая поездка, `404` — поездка не найдена, `409` — поездка ещё не завершена. Повторный PUT изменяет один существующий отзыв, новые отзывы-дубликаты не создаются. Комментарии отображаются как текст, HTML не исполняется.

Это описание новых изменений; полный общий REST-документ будет окончательно сверен после проверки вашей локальной версии проекта.

## Быстрая проверка вручную

- Зарегистрировать пользователя, которому ровно 16 лет сегодня: успешно. Дата на один день позже границы: отказ. Повторить запрос напрямую в User — тот же отказ.
- Проверить нормальное ФИО, составное имя, цифры/HTML и `huIBSOIAHUD PAISUDB AOSIDHBN`: неверный формат отклоняется.
- В каталоге нет старой кнопки; после разрешения геолокации показаны ближайшие. Избранное переключается без сообщения о браузерном хранении.
- Начать поездку: кнопки отзыва нет. Завершить: отзыв доступен до и после оплаты.
- Сохранить обе оценки и комментарии, обновить страницу и перезапустить Ride: отзыв остаётся. Изменить отзыв: обновился тот же отзыв.
- Проверить чужую поездку, активную поездку, оценки 0/6/1.5/строкой и комментарий длиннее 1000: сервер отклоняет.
- Ввести HTML в комментарий: отображается текст без выполнения HTML.
- Скачать чек и историю PDF, проверить кириллицу. Проверить некорректный путь JWT: понятная ошибка, подпись JWT никогда не отключается.

## Рабочие файлы для замены/добавления (21)

- `local-stack/migrations/ride/002_reviews.sql`
- `services/documents/src/main/java/ru/kicksharing/documents/util/JwtPublicKeyLoader.java`
- `services/documents/src/main/java/ru/kicksharing/documents/util/JwtUtil.java`
- `services/ride/src/main/java/ru/itis/dto/ReviewRequest.java`
- `services/ride/src/main/java/ru/itis/model/RideEntity.java`
- `services/ride/src/main/java/ru/itis/model/RideReview.java`
- `services/ride/src/main/java/ru/itis/repository/RideRepository.java`
- `services/ride/src/main/java/ru/itis/repository/impl/RideRepositoryJdbcImpl.java`
- `services/ride/src/main/java/ru/itis/service/RideService.java`
- `services/ride/src/main/java/ru/itis/service/impl/RideServiceImpl.java`
- `services/ride/src/main/java/ru/itis/servlet/RideServlet.java`
- `services/user/src/main/java/ru/scooter/user/util/Validation.java`
- `src/main/java/bff/service/RideService.java`
- `src/main/java/bff/service/impl/RegistrationRules.java`
- `src/main/java/bff/service/impl/RideServiceRestImpl.java`
- `src/main/java/bff/servlet/RideProxyServlet.java`
- `src/main/webapp/frontend/account.css`
- `src/main/webapp/frontend/account.js`
- `src/main/webapp/frontend/api.js`
- `src/main/webapp/frontend/script.js`
- `src/main/webapp/index.jsp`

## Дополнительные изменённые файлы

- `README.md`
- `scripts/check-moderation-integration.py`
- `scripts/check-native-stack.cjs`
- `scripts/check-stack-recovery.cjs`
- `scripts/check-submission.cjs`
- `scripts/check-user-integration.py`
- `scripts/prepare-demo.cjs`
- `services/documents/src/test/java/ru/kicksharing/documents/util/JwtPublicKeyLoaderTest.java`
- `services/ride/src/test/java/ru/itis/service/RideReviewTest.java`
- `services/user/src/test/java/ru/scooter/user/ValidationTest.java`
- `src/test/browser/scootx.test.cjs`
- `src/test/java/bff/BrowserTestServer.java`
- `src/test/java/bff/RegistrationRulesTest.java`
- `src/test/js/api.test.cjs`

## Проверено перед публикацией

Локальный стенд: Java 17, Tomcat 10.1, PostgreSQL 17; шесть сервисов запущены без Docker.

- Maven: BFF 96 тестов (включая 47 HTTP), User 22 (включая 13 HTTP с настоящей PostgreSQL), Ride 17, Documents 71 — без ошибок и пропусков.
- JavaScript API: 8 тестов, включая границу 16 лет и формат ФИО.
- Браузер Chromium: 22 сценария, включая создание/изменение отзыва, вывод HTML-комментария как текста, геолокацию, регистрацию, поездку, оплату и скачивание.
- API на шести настоящих сервисах: запреты на отзыв активной/чужой поездки, неверные оценки/комментарии, создание и изменение отзыва в истории; регистрация 16+, поездка/позиция/оплата, настоящие PDF, владение документами, удаление, выход/отзыв JWT.
- Отдельно: перезапуск Ride сохраняет отзыв, изменение отзыва PAID-поездки не меняет оплату. Миграция применена поверх существующей базы с поездками и документами.

Эти проверки не заменяют проверку путей, портов и прав доступа на вашем ПК. Удалённый GitHub Actions для этой временной ветки не проверялся.
