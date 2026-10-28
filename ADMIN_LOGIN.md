# Как назначить ADMIN и войти в панель

Готового общего логина и пароля администратора нет. Нужен зарегистрированный пользователь, которому локально назначена роль ADMIN. Пароль остаётся тем, который выбрал при регистрации.

## 1. Зарегистрируй тестовый аккаунт

Открой `http://localhost:8080/register.jsp` и создай, например:

- Логин: `admin.check`
- Пароль: `Admin-check-2026!`
- ФИО: `Иван Петров`
- Дата рождения: `2000-05-17`

Это предлагаемые тестовые данные, не встроенный аккаунт. Если логин уже занят, используй свой существующий аккаунт с известным паролем либо выбери новый логин.

## 2. Назначь роль — один из двух вариантов

### Вариант A: pgAdmin

Подключись к **базе User** (обычно `kicksharing_user`, с твоим прежним префиксом), под её владельцем/пользователем с правом UPDATE. Открой Query Tool и выполни:

```sql
BEGIN;

UPDATE public.users
SET role = 'ADMIN'
WHERE login = 'admin.check' AND NOT is_deleted;

UPDATE public.tokens
SET is_valid = FALSE
WHERE user_id IN (
    SELECT id FROM public.users
    WHERE login = 'admin.check' AND NOT is_deleted
);

COMMIT;

SELECT login, role
FROM public.users
WHERE login = 'admin.check' AND NOT is_deleted;
```

В последнем результате должен быть `admin.check | ADMIN`. Если строки нет — аккаунт ещё не зарегистрирован в этой базе либо открыта другая база. Если выбрал иной логин, замени его во всех местах SQL. Пароль SQL не меняет, старые токены отзываются.

### Вариант B: штатная CLI-команда

Из корня своего проекта сначала собери User:

```bash
mvn -f services/user/pom.xml -DskipTests package
```

Затем (работает и из Fish, поскольку явно вызывается Bash):

```bash
bash -c 'source local-run/user.env.sh; "$JAVA_HOME/bin/java" -cp "services/user/target/user-service/WEB-INF/classes:services/user/target/user-service/WEB-INF/lib/*" ru.scooter.user.cli.PromoteAdmin admin.check'
```

Ожидается `ADMIN granted to admin.check; sign in again`. Команда использует именно твою User DB из local-run, повышает существующий аккаунт и отзывает его старые токены. Вместо admin.check можно передать свой зарегистрированный логин.

## 3. Войди заново

Выйди из прежнего аккаунта BFF. Открой `http://localhost:8080/login.jsp` и введи логин/пароль выбранного аккаунта. Для примера выше:

```text
Логин: admin.check
Пароль: Admin-check-2026!
```

Ожидается переход на `http://localhost:8082/admin/users.html#accessToken=...`. После приёма токена панель убирает fragment из адреса. Публичный /api/config должен содержать правильный moderationPanelUrl; в конфигурации Moderation должны быть ADMIN_USER_BASE_URL=http://localhost:8081 и BFF_LOGIN_URL=http://localhost:8080/login.jsp.

Если ты уже вошёл другим пользователем, login.jsp сохраняет его действующую сессию и откроет его кабинет. Сначала нажми «Выйти» в кабинете либо используй отдельный профиль/инкогнито для ADMIN. Одновременно обычного пользователя и администратора удобнее проверять в разных профилях браузера.

Только GET login.jsp роль не меняет. Назначение через регистрацию role=ADMIN не работает — публичная регистрация создаёт USER.
