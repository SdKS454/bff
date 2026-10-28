<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description"
          content="ScootX — аренда городских электросамокатов. Самокаты рядом, заряд аккумулятора и история поездок.">
    <title>ScootX — аренда самокатов</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/frontend/fonts.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/frontend/style.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/frontend/account.css">
    <script src="${pageContext.request.contextPath}/frontend/api.js" defer></script>
    <script src="${pageContext.request.contextPath}/frontend/script.js" defer></script>
    <script src="${pageContext.request.contextPath}/frontend/account.js" defer></script>
</head>
<body data-ctx="${pageContext.request.contextPath}">
<svg class="svg-defs" aria-hidden="true" xmlns="http://www.w3.org/2000/svg">
    <defs>
        <symbol id="logo" viewBox="0 0 40 40">
            <path fill="currentColor"
                  d="M2 1h12l6 7 7-7h11v12l-9 8H17l-3-3-5 5H2V12l7-5zM2 28l9-9h12l3 3 5-5h7v12l-9 9H17l-4-5-5 5H2z"/>
        </symbol>
        <symbol id="person" viewBox="0 0 24 24">
            <circle cx="12" cy="8" r="4" fill="none" stroke="currentColor" stroke-width="1.6"/>
            <path d="M4 22v-2a8 8 0 0 1 16 0v2" fill="none" stroke="currentColor" stroke-width="1.6"/>
        </symbol>
        <symbol id="pin" viewBox="0 0 24 24">
            <path d="M19 10c0 6-7 11-7 11S5 16 5 10a7 7 0 0 1 14 0Z" fill="none" stroke="currentColor"
                  stroke-width="1.6"/>
            <circle cx="12" cy="10" r="2.5" fill="none" stroke="currentColor" stroke-width="1.6"/>
        </symbol>
        <symbol id="heart" viewBox="0 0 24 24">
            <path fill="currentColor" d="M12 21s-9-5.7-9-12a5 5 0 0 1 9-3 5 5 0 0 1 9 3c0 6.3-9 12-9 12Z"/>
        </symbol>
        <symbol id="battery" viewBox="0 0 24 24">
            <rect x="2" y="6" width="18" height="12" rx="3" fill="none" stroke="currentColor" stroke-width="1.5"/>
            <path d="M22 10v4M6 9v6m4-6v6m4-6v6" stroke="currentColor" stroke-width="2"/>
        </symbol>
    </defs>
</svg>

<main class="page">
    <section class="hero" id="home" aria-labelledby="hero-title">
        <header class="header">
            <a class="brand" href="#home" aria-label="ScootX — на главную">
                <svg>
                    <use href="#logo"/>
                </svg>
                <span>Scoot<span class="orange">X</span></span></a>
            <nav class="nav" aria-label="Основная навигация">
                <a class="active" href="#home">Главная</a>
                <a href="#scooters">Самокаты</a>
                <a href="#performance">О самокатах</a>
                <a href="#support">Отзывы</a>
            </nav>
            <div class="account-entry">
                <button class="account-label" id="account-label" type="button">Вход / Регистрация</button>
                <button class="square orange-bg" id="account-button" type="button" aria-label="Вход и регистрация">
                    <svg>
                        <use href="#person"/>
                    </svg>
                </button>
            </div>
        </header>
        <div class="hero-copy">
            <h1 id="hero-title">Город ближе.<span>Движение</span><span>свободнее.</span></h1>
            <p>Твой маршрут — твои правила. Выбирай самокат рядом, проверяй заряд и отправляйся навстречу городу вместе
                со ScootX.</p>
        </div>
        <a href="#scooters" class="scroll-cue" aria-label="Перейти к самокатам"><span>САМОКАТЫ РЯДОМ</span><b>↓</b></a>
    </section>

    <section class="section collection" id="scooters" aria-labelledby="collection-title">
        <div class="section-heading row">
            <h2 id="collection-title">Один самокат.<span>Много маршрутов.</span></h2>
            <div class="collection-intro"><p>Одинаково удобные самокаты — в разных точках города.<br>Выбери по
                расположению, заряду и статусу.</p></div>
        </div>
        <div class="fleet-caption"><span><i class="availability-dot"></i><b
                id="fleet-count">Самокаты рядом</b></span><span>Одна модель · всё необходимое для поездки</span></div>
        <div class="catalog-tools">
            <button id="refresh-fleet" type="button">Обновить каталог</button>
            <button id="retry-location" type="button" hidden>Повторить геолокацию</button>
        </div>
        <p id="catalog-message" role="status"></p>
        <div class="product-grid" id="product-grid"></div>
        <div class="collection-bottom">
            <button class="split dark" id="view-all" type="button"><span>Показать все самокаты</span><b>→</b></button>
        </div>
    </section>

    <section class="section tyre" id="performance" aria-labelledby="tyre-title">
        <div class="tyre-copy">
            <h2 id="tyre-title">Надёжные шины.<br>Уверенное движение.</h2>
            <p>Комфорт и устойчивость для повседневных<br>городских маршрутов.</p>
            <div class="tyre-features">
                <article><b class="feature-icon">✣</b>
                    <h3>Защита от проколов</h3>
                    <p>Прочные шины для неровностей городских дорог.</p></article>
                <article><b class="feature-icon">−</b>
                    <h3>Износостойкость</h3>
                    <p>Конструкция для регулярных поездок по городу.</p></article>
                <article><b class="feature-icon">◉</b>
                    <h3>Надёжное сцепление</h3>
                    <p>Рисунок протектора помогает сохранять устойчивость.</p></article>
                <article><b class="feature-icon">✤</b>
                    <h3>Мягкий ход</h3>
                    <p>Амортизация смягчает небольшие неровности на пути.</p></article>
            </div>
        </div>
    </section>

    <section class="section reviews" id="support" aria-labelledby="reviews-title">
        <div class="section-heading centered"><h2 id="reviews-title">Для тех, кто в
            движении.<span>С любовью к городу.</span></h2>
            <p>По делам, на встречу или просто навстречу новому дню.<br>Вот что рассказывают о своих поездках наши
                пользователи.</p></div>
        <p id="reviews-message" role="status">Загружаем отзывы…</p>
        <div class="review-grid" id="review-grid"></div>
        <div class="carousel-controls">
            <button class="square previous" data-review-direction="-1" type="button" aria-label="Предыдущий отзыв">←
            </button>
            <button class="square dark" data-review-direction="1" type="button" aria-label="Следующий отзыв">→</button>
            <div class="progress"><span></span></div>
            <button type="button" id="refresh-reviews">Обновить отзывы</button>
            <button class="split dark push-right" id="all-reviews" type="button"><span>Все отзывы</span><b>→</b>
            </button>
        </div>
    </section>

    <section class="section social" aria-label="Галерея поездок ScootX">
        <div class="social-gallery">
            <button class="gallery-photo crop" data-crop="261,7486,232,382" type="button"
                    aria-label="Открыть фотографию поездки"></button>
            <button class="gallery-photo crop" data-crop="532,7565,232,226" type="button"
                    aria-label="Открыть фотографию самоката"></button>
            <button class="gallery-photo crop" data-crop="1342,7565,232,226" type="button"
                    aria-label="Открыть фотографию самоката в движении"></button>
            <button class="gallery-photo crop" data-crop="1072,7486,232,382" type="button"
                    aria-label="Открыть фотографию городского самоката"></button>
            <button class="gallery-photo crop" data-crop="532,7565,232,226" type="button"
                    aria-label="Открыть фотографию складного самоката"></button>
            <button class="gallery-photo crop" data-crop="1613,7486,232,382" type="button"
                    aria-label="Открыть фотографию пользователя на самокате"></button>
        </div>
    </section>

    <footer>
        <div><a class="brand" href="#home" aria-label="ScootX — на главную">
            <svg>
                <use href="#logo"/>
            </svg>
            <span>Scoot<span class="orange">X</span></span></a>
            <p>Свобода движения.<br>В ритме твоего города.</p></div>
        <div class="footer-right">
            <nav aria-label="Навигация в подвале"><a href="#home">Главная</a><a href="#scooters">Самокаты</a><a
                    href="#performance">О самокатах</a><a href="#support">Отзывы</a></nav>
        </div>
    </footer>
</main>
<dialog id="details" aria-label="Информация ScootX">
    <button class="dialog-close" type="button" aria-label="Закрыть окно">×</button>
    <div id="dialog-content"></div>
</dialog>
<div id="toast" role="status" aria-live="polite"></div>
</body>
</html>
