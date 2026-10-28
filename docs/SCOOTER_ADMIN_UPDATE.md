# Админка самокатов: результат сравнения и обновление

Архив коллег scooter api with admin.zip содержит функции, которых не было в пакете teacher-final: создание самоката, зарядка до100% и удаление через страницу управления парком.

Архив нельзя целиком подставить вместо services/scooter: в нём другая/старая реализация API, целочисленная сетка вместо GPS, фиксированные параметры БД в Java-коде, нет проверок JWT/ADMIN, зарядка меняет данные через GET и способна освободить самокат в поездке, удаление не сохраняет is_deleted и не имеет servlet mapping. Эти классы/старый web.xml не переносились поверх согласованной интеграции. Перенесена функциональность с исправлением этих проблем, текущий каталог/резервации/Ride/Documents сохранены.

## Как обновить работающий стенд

Основа точечного архива — предыдущий полный пакет для преподавателя от28.10.2026 (ветка submission/teacher-final-2026-10-28, cdfbc73). Если у вас более старая/изменённая копия, используйте новый полный архив и свои сохранённые local-run; не переносите чужие ключи/пароли и не удаляйте свои БД. Сравните изменения своих исходников перед перезаписью.

1. Остановите запущенный стенд: `python3 scripts/stand.py stop`. При запуске из IDEA остановите отдельно BFF, Scooter и Moderation.
2. Из scooter-admin-update.zip скопируйте содержимое папки scooter-admin-update поверх корня проекта, сохраняя структуру каталогов. Это только изменённые/новые файлы: перечень ниже. Внутри уже есть три пересобранных WAR, обновлённый build-info и исходники.
3. **Повторное создание БД, миграции и смена переменных не нужны.** Свою local-run/ и настройки IDEA сохраните. Если stand.py сообщает об изменённых исходниках относительно dist, выполните `python3 scripts/stand.py build` (нужны Maven и доступ к зависимостям).
4. При скриптовом запуске: `python3 scripts/stand.py start --tomcat-home "/ваша/папка/apache-tomcat-10.1.61"`. На Windows замените python3 на py -3. Полный запуск с нуля — README_RU.md.
5. В IDEA сделайте Maven package для корневого BFF и services/scooter, services/moderation; заново разверните их WAR/exploded-артефакты с контекстом `/`. User/Ride/Documents пересобирать для этой правки не требуется. Если используете готовые dist/*.war, выберите именно обновлённые файлы.
6. В браузере обновите страницу с очисткой кэша (Ctrl+Shift+R). Войдите своим ADMIN через http://localhost:8080/login.jsp. В панели блокировок появится «Управление самокатами». Прямой адрес: **http://localhost:8080/admin/scooters**. USER получает403; без сессии переход на вход. Роль ADMIN задаётся как раньше — stand.py demo или User CLI PromoteAdmin; новый общий пароль не добавлен.
7. Проверьте добавление → отображение в каталоге → зарядку → поездку (в ней зарядка/удаление409) → завершение → удаление. История/оплаченный PDF остаются доступны. Восстановление ранее удалённого номера не предусмотрено, для нового самоката нужен новый уникальный номер.

Настройки по сервисам: ENVIRONMENT.md; контракты: API_CONTRACTS.md §14; полный ручной чек-лист: FULL_API_URL_CHECKLIST.md/CSV. Результаты реально выполненных тестов — FINAL_VERIFICATION.md. Прежние проверки сохранены и дополнены; теперь68 операций и350 ручных пунктов.

## Рабочие исходники, которые изменились

BFF (да, для этого обновления его тоже нужно обновить):

- src/main/java/bff/service/impl/ScooterServiceRestImpl.java
- src/main/java/bff/servlet/AdminScooterProxyServlet.java — новый
- src/main/webapp/WEB-INF/admin-scooters.jsp — новый
- src/main/webapp/frontend/admin-scooters.js — новый
- src/main/webapp/frontend/admin-scooters.css — новый

Scooter:

- services/scooter/src/main/java/ru/itis/filter/AuthFilter.java
- services/scooter/src/main/java/ru/itis/repository/Scooters.java
- services/scooter/src/main/java/ru/itis/servlet/AdminScooterServlet.java — новый

Moderation (только ссылка в интерфейсе):

- services/moderation/src/main/webapp/admin/users.html
- services/moderation/src/main/webapp/admin/js/users.js

Проверки: src/test/java/bff/BffIT.java и services/scooter/src/test/java/ru/itis/repository/ScooterDistanceTest.java.

Сборки: dist/bff.war, dist/scooter.war, dist/moderation.war, dist/build-info.json. Документация/справочники: README.md, README_RU.md, START_HERE.html, docs/API_CONTRACTS.md, docs/API_CONTRACTS_REVIEW.md, docs/API_URL_INVENTORY.json, docs/ALL_API.postman_collection.json, docs/FULL_API_URL_CHECKLIST.md, docs/FULL_API_URL_CHECKLIST.csv, docs/FINAL_VERIFICATION.md, docs/ENVIRONMENT.md, этот SCOOTER_ADMIN_UPDATE.md и дополненный docs/FINAL_CHANGES.md. MANIFEST_SHA256.txt содержит контрольные суммы актуального полного проекта.
