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
    <p>Логин: <input name="login" required minlength="3" maxlength="64"
      pattern="[A-Za-z0-9_.\-]{3,64}" autocomplete="username"
      title="3–64 символа: латиница, цифры, _, . или -"></p>
    <p>ФИО: <input name="fullName" required maxlength="200" autocomplete="name"></p>
    <p>Дата рождения: <input name="birthDate" type="date" required min="1900-01-01"></p>
    <p>Пароль: <input name="password" type="password" required minlength="8"
      autocomplete="new-password" aria-describedby="password-rules"></p>
    <p id="password-rules">Пароль: минимум 8 символов, максимум 72 байта UTF-8.
      Кириллица и эмодзи занимают больше одного байта.</p>
    <button type="submit">Зарегистрироваться</button>
  </form>
  <p id="err"></p>
  <p><a href="login.jsp">Вход</a></p>
  <script src="${pageContext.request.contextPath}/js/api.js"></script>
  <script src="${pageContext.request.contextPath}/js/register.js"></script>
</body>
</html>
