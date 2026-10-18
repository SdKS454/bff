<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="UTF-8"/>
  <title>Регистрация</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"/>
</head>
<body data-ctx="${pageContext.request.contextPath}">
  <h2>Регистрация</h2>
  <form id="f">
    <p>Логин: <input name="login" required></p>
    <p>ФИО: <input name="fullName" required></p>
    <p>Дата рождения: <input name="birthDate" type="date"></p>
    <p>Пароль: <input name="password" type="password" required></p>
    <button type="submit">Зарегистрироваться</button>
  </form>
  <p id="err"></p>
  <p><a href="login.jsp">Вход</a></p>
  <script src="${pageContext.request.contextPath}/js/api.js"></script>
  <script src="${pageContext.request.contextPath}/js/register.js"></script>
</body>
</html>
