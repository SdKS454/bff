<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="UTF-8"/>
  <title>Вход</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"/>
</head>
<body data-ctx="${pageContext.request.contextPath}">
  <h2>Вход</h2>
  <form id="f">
    <p>Логин: <input name="login" required></p>
    <p>Пароль: <input name="password" type="password" required></p>
    <button type="submit">Войти</button>
  </form>
  <p id="err"></p>
  <p><a href="register.jsp">Регистрация</a></p>
  <script src="${pageContext.request.contextPath}/js/api.js"></script>
  <script src="${pageContext.request.contextPath}/js/login.js"></script>
</body>
</html>
