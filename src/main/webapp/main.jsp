<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="UTF-8"/>
  <title>Самокаты</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"/>
</head>
<body data-ctx="${pageContext.request.contextPath}">
  <h2>Ближайшие самокаты</h2>
  <form method="post" action="${pageContext.request.contextPath}/logout">
    <button type="submit">Выйти</button>
  </form>
  <p>
    <button type="button" id="btn-refresh">Обновить</button>
  </p>
  <p id="coords" class="muted"></p>
  <div id="list">Загрузка...</div>
  <p id="message" role="status"></p>
  <h3>Мои поездки</h3>
  <div id="rides"></div>
  <h3>Документы</h3>
  <form id="documents">
    <label>С <input type="date" name="from" required></label>
    <label>По <input type="date" name="to" required></label>
    <button type="submit">Скачать историю поездок</button>
  </form>
  <div id="blocked-document"></div>

  <script src="${pageContext.request.contextPath}/js/geo.js"></script>
  <script src="${pageContext.request.contextPath}/js/api.js"></script>
  <script src="${pageContext.request.contextPath}/js/main.js"></script>
</body>
</html>
