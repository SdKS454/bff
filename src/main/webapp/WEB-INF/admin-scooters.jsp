<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>ScootX — Управление самокатами</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/frontend/fonts.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/frontend/admin-scooters.css">
</head>
<body data-ctx="${pageContext.request.contextPath}">
<main>
    <header><h1>Управление самокатами</h1><a href="${pageContext.request.contextPath}/">На сайт</a>
        <a href="${pageContext.request.contextPath}/login.jsp">Админка блокировок</a></header>
    <p id="message" role="status" aria-live="polite"></p>
    <section><h2>Добавить самокат</h2>
        <form id="add-scooter">
            <label>Номерной знак<input name="numberPlate" maxlength="64" pattern="[A-Za-z0-9_.\-]{1,64}"
                                       required></label>
            <label>Долгота (posX)<input name="posX" type="number" step="any" min="-180" max="180" required></label>
            <label>Широта (posY)<input name="posY" type="number" step="any" min="-90" max="90" required></label>
            <button type="submit">Добавить</button>
        </form>
        <p>Новый самокат получает заряд 100% и статус «Свободен».</p></section>
    <section><h2>Парк самокатов</h2>
        <button id="refresh" type="button">Обновить</button>
        <div class="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>Номер</th>
                    <th>Заряд</th>
                    <th>Статус</th>
                    <th>Долгота</th>
                    <th>Широта</th>
                    <th>Действия</th>
                </tr>
                </thead>
                <tbody id="scooters"></tbody>
            </table>
        </div>
    </section>
</main>
<script src="${pageContext.request.contextPath}/frontend/admin-scooters.js"></script>
</body>
</html>
