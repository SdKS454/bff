'use strict';
const dialog = document.getElementById('details');
const dialogContent = document.getElementById('dialog-content');
const photoCrop = [145, 3990, 404, 500];
const viewState = {fleet: [], user: null, rides: [], favorites: [], showAll: false, catalogMessage: 'Загрузка…'};
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
}[c]));
const finite = value => typeof value === 'number' && Number.isFinite(value);
const coordinate = value => finite(value) ? value.toLocaleString('ru-RU', {maximumFractionDigits: 5}) : '—';
const money = (value, currency = 'RUB') => {
    if (!finite(value)) return 'Сумма ещё не рассчитана';
    try {
        return value.toLocaleString('ru-RU', {style: 'currency', currency, maximumFractionDigits: 2});
    } catch (_) {
        return value.toLocaleString('ru-RU') + ' ' + String(currency);
    }
};
const rideStatuses = {ACTIVE: 'В поездке', FINISHED: 'Ожидает оплаты', PAID: 'Оплачена'};
const scooterStatuses = {READY: 'Свободен', IN_RIDE: 'В поездке', CHARGING: 'На зарядке', REPAIR: 'На обслуживании'};
const dateLabel = value => Number.isFinite(Date.parse(value)) ? new Date(value).toLocaleString('ru-RU', {
    dateStyle: 'short',
    timeStyle: 'short'
}) : '—';
const chargeValue = scooter => finite(scooter?.charge) ? Math.min(100, Math.max(0, scooter.charge)) : null;
const chargeLabel = scooter => chargeValue(scooter) === null ? 'Нет данных' : chargeValue(scooter) + '%';
let toastTimer;
let reviewLoadVersion = 0;

function reviewHtml(review) {
    const stars = '★'.repeat(review.rideRating) + '☆'.repeat(5 - review.rideRating);
    return `<article class="review user-review"><div class="review-copy"><div class="stars" aria-label="Оценка поездки ${review.rideRating} из 5">${stars}</div><h3>Пользователь ScootX</h3><small>${escapeHtml(dateLabel(review.reviewedAt))}</small><div class="quote"><h4>Поездка: ${review.rideRating}/5</h4><p>${escapeHtml(review.rideComment || 'Без комментария')}</p><h4>Самокат: ${review.scooterRating}/5</h4><p>${escapeHtml(review.scooterComment || 'Без комментария')}</p></div></div></article>`;
}

async function reviewPage(limit, offset) {
    const items = await window.ScootxApi.request('/api/reviews?limit=' + limit + '&offset=' + offset);
    if (!Array.isArray(items) || items.length > limit || !items.every(item => item
        && [item.rideRating, item.scooterRating].every(n => Number.isInteger(n) && n >= 1 && n <= 5)
        && [item.rideComment, item.scooterComment].every(text => text === null || typeof text === 'string' && text.length <= 1000)
        && typeof item.reviewedAt === 'string' && Number.isFinite(Date.parse(item.reviewedAt))))
        throw new Error('Сервис вернул некорректные отзывы.');
    return items;
}

async function refreshReviews() {
    const version = ++reviewLoadVersion;
    const grid = document.getElementById('review-grid'), message = document.getElementById('reviews-message');
    message.textContent = 'Загружаем отзывы…';
    try {
        const items = await reviewPage(2, 0);
        if (version !== reviewLoadVersion) return;
        grid.innerHTML = items.map(reviewHtml).join('');
        message.textContent = items.length ? '' : 'Отзывов пока нет. Оставьте первый после завершения поездки.';
    } catch (error) {
        if (version !== reviewLoadVersion) return;
        grid.innerHTML = '';
        message.textContent = 'Не удалось загрузить отзывы. ' + error.message;
    }
    if (version === reviewLoadVersion)
        document.querySelectorAll('[data-review-direction]').forEach(button => {
            button.disabled = grid.children.length < 2;
        });
}

async function loadReviewDialog(list, reset = false) {
    if (!list.isConnected || !dialog.open || list.dataset.loading === 'true') return;
    list.dataset.loading = 'true';
    const container = list.parentElement, more = container.querySelector('[data-more-reviews]'),
        message = container.querySelector('[data-reviews-message]');
    more.disabled = true;
    message.textContent = 'Загружаем отзывы…';
    const offset = reset ? 0 : Number(list.dataset.offset || 0);
    try {
        const items = await reviewPage(20, offset);
        if (!list.isConnected || !dialog.open) return;
        if (reset) list.innerHTML = '';
        list.insertAdjacentHTML('beforeend', items.map(reviewHtml).join(''));
        list.dataset.offset = String(offset + items.length);
        more.hidden = items.length < 20;
        message.textContent = list.children.length ? '' : 'Отзывов пока нет. Оставьте первый после завершения поездки.';
    } catch (error) {
        if (list.isConnected) message.textContent = 'Не удалось загрузить отзывы. ' + error.message;
    } finally {
        delete list.dataset.loading;
        more.disabled = false;
    }
}

function plateLabel(value) {
    const alphabet = {A: 'А', B: 'В', E: 'Е', K: 'К', M: 'М', H: 'Н', O: 'О', P: 'Р', C: 'С', T: 'Т', Y: 'У', X: 'Х'};
    return String(value || '').replace(/[ABEKMHOPCTYX]/g, c => alphabet[c]);
}

function plateHtml(value) {
    return `<span class="license-plate">${escapeHtml(plateLabel(value))}<small>RUS</small></span>`;
}

function cropImages(root = document) {
    root.querySelectorAll('[data-crop]').forEach(element => {
        const [x, y, width, height] = element.dataset.crop.split(',').map(Number);
        element.style.backgroundSize = `${2100 / width * 100}% auto`;
        element.style.backgroundPosition = `${x / (2100 - width) * 100}% ${y / (8314 - height) * 100}%`;
        element.style.aspectRatio = `${width} / ${height}`;
    });
}

function notify(message) {
    const toast = document.getElementById('toast');
    clearTimeout(toastTimer);
    toast.textContent = message;
    toast.classList.add('visible');
    toastTimer = setTimeout(() => toast.classList.remove('visible'), 5000);
}

function openDialog(html, account = false) {
    dialog.classList.toggle('account-dialog', account);
    dialogContent.innerHTML = html;
    cropImages(dialogContent);
    if (!dialog.open) dialog.showModal();
    document.body.style.overflow = 'hidden';
}

function renderFleet() {
    const fleet = viewState.showAll ? viewState.fleet : viewState.fleet.slice(0, 4);
    document.getElementById('catalog-message').textContent = viewState.catalogMessage;
    document.getElementById('product-grid').innerHTML = fleet.map(s => `
    <article class="product">
      <div class="product-top"><span class="scooter-status ${s.status === 'READY' ? '' : 'unavailable'}">● ${escapeHtml(scooterStatuses[s.status] || 'Недоступен')}</span><button class="circle" data-favorite="${escapeHtml(s.numberPlate)}" aria-label="Сохранить самокат ${escapeHtml(plateLabel(s.numberPlate))}" aria-pressed="${viewState.favorites.includes(s.numberPlate)}" type="button"><svg><use href="#heart"/></svg></button></div>
      <div class="product-picture crop" data-crop="${photoCrop.join(',')}" role="img" aria-label="Иллюстрация самоката"></div>
      ${plateHtml(s.numberPlate)}
      <div class="charge-heading"><span>Заряд аккумулятора</span><strong>${chargeLabel(s)}</strong></div>
      <div class="charge-bar"><span style="width:${chargeValue(s) ?? 0}%"></span></div>
      <div class="product-location"><svg><use href="#pin"/></svg><div>Местоположение самоката<small>Долгота: ${coordinate(s.posX)}, широта: ${coordinate(s.posY)}</small></div></div>
      ${finite(s.distance) ? `<div class="range-row"><span>Расстояние</span><strong>${s.distance < 1000 ? Math.round(s.distance).toLocaleString('ru-RU') + ' м' : (s.distance / 1000).toLocaleString('ru-RU', {maximumFractionDigits: 1}) + ' км'}</strong></div>` : ''}
      <button class="product-action" data-scooter="${escapeHtml(s.numberPlate)}" type="button"><span>Подробнее о самокате</span><b>→</b></button>
    </article>`).join('');
    document.getElementById('fleet-count').textContent = viewState.user ? `Свободно ${viewState.fleet.filter(s => s.status === 'READY').length} из ${viewState.fleet.length}` : 'Самокаты доступны после входа';
    document.querySelector('#view-all span').textContent = viewState.showAll ? 'Показать меньше' : 'Показать все самокаты';
    document.getElementById('view-all').hidden = viewState.fleet.length <= 4;
    cropImages(document.getElementById('product-grid'));
}

function setPageData(data) {
    Object.assign(viewState, data);
    renderFleet();
}

async function openScooter(numberPlate) {
    try {
        const s = await window.ScootxApi.request('/api/scooters/' + encodeURIComponent(numberPlate));
        if (!s || typeof s.numberPlate !== 'string') throw new Error('Некорректный ответ сервиса самокатов.');
        openDialog(`<div class="dialog-product"><div class="product-picture crop" data-crop="${photoCrop.join(',')}"></div><section><h2>Самокат</h2>${plateHtml(s.numberPlate)}<div class="dialog-facts"><div><span>Состояние</span><b>${escapeHtml(scooterStatuses[s.status] || 'Недоступен')}</b></div><div><span>Заряд</span><b>${chargeLabel(s)}</b></div><div><span>Местоположение</span><b>Долгота: ${coordinate(s.posX)}, широта: ${coordinate(s.posY)}</b></div></div><p class="account-subtitle">Фотография иллюстративная.</p><button class="account-submit" data-start-ride="${escapeHtml(s.numberPlate)}" type="button" ${s.status === 'READY' ? '' : 'disabled'}><span>${s.status === 'READY' ? 'Начать поездку' : 'Сейчас недоступен'}</span><b>→</b></button></section></div>`);
    } catch (error) {
        window.ScootxAccount.handleError(error);
    }
}

document.addEventListener('click', async event => {
    const scooter = event.target.closest('[data-scooter]');
    if (scooter) {
        await openScooter(scooter.dataset.scooter);
        return;
    }
    const favorite = event.target.closest('[data-favorite]');
    if (favorite) {
        if (!viewState.user) {
            await window.ScootxAccount.open();
            return;
        }
        const plate = favorite.dataset.favorite;
        const favorites = window.ScootxApi.setFavorite(viewState.user.id, plate, !viewState.favorites.includes(plate));
        setPageData({favorites});
        window.ScootxAccount.updateFavorites(favorites);
        return;
    }
    if (event.target.closest('[data-scroll-catalog]')) {
        dialog.close();
        document.getElementById('scooters').scrollIntoView();
        return;
    }
    const direction = event.target.closest('[data-review-direction]');
    if (direction) {
        const grid = document.getElementById('review-grid');
        if (grid.children.length > 1) Number(direction.dataset.reviewDirection) > 0 ? grid.append(grid.firstElementChild) : grid.prepend(grid.lastElementChild);
        return;
    }
    const gallery = event.target.closest('.gallery-photo');
    if (gallery) openDialog(`<h2>Моменты в движении</h2><div class="dialog-gallery crop" data-crop="${escapeHtml(gallery.dataset.crop)}" role="img" aria-label="Фотография ScootX"></div>`);
});
document.getElementById('view-all').addEventListener('click', () => {
    viewState.showAll = !viewState.showAll;
    renderFleet();
});
document.getElementById('refresh-reviews').addEventListener('click', refreshReviews);
document.getElementById('all-reviews').addEventListener('click', () => {
    openDialog('<section><h2>Отзывы о поездках и самокатах</h2><p data-reviews-message role="status"></p><div class="dialog-reviews" id="public-review-list" data-offset="0"></div><button type="button" data-more-reviews>Показать ещё</button> <button type="button" data-reload-reviews>Обновить отзывы</button></section>');
    loadReviewDialog(document.getElementById('public-review-list'), true);
});
document.addEventListener('click', event => {
    const button = event.target.closest('[data-more-reviews],[data-reload-reviews]');
    if (button && !button.disabled) {
        const list = dialog.querySelector('#public-review-list');
        if (list) loadReviewDialog(list, button.hasAttribute('data-reload-reviews'));
    }
});
document.querySelector('.dialog-close').addEventListener('click', () => dialog.close());
dialog.addEventListener('close', () => {
    if (dialog.open) return;
    document.body.style.overflow = '';
    dialog.classList.remove('account-dialog');
});
dialog.addEventListener('click', event => {
    if (event.target === dialog) {
        const r = dialog.getBoundingClientRect();
        if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close();
    }
});
document.querySelectorAll('.nav a').forEach(link => link.addEventListener('click', () => document.querySelectorAll('.nav a').forEach(other => other.classList.toggle('active', other === link))));
cropImages();
renderFleet();
window.ScootxPage = {
    refreshReviews,
    setData: setPageData,
    openDialog,
    notify,
    plateLabel,
    plateHtml,
    photoCrop,
    escapeHtml,
    dateLabel,
    money,
    coordinate,
    chargeLabel,
    rideStatuses,
    scooterStatuses
};

refreshReviews();
