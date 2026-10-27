'use strict';
(() => {
  const page = window.ScootxPage, service = window.ScootxApi;
  const state = { user:null, rides:[], fleet:[], favorites:[], pendingStart:null, ridesError:'', documents:[], documentsLoaded:false, documentsError:'', documentsMore:false };
  const $ = selector => document.querySelector(selector);
  const esc = page.escapeHtml;
  let refreshVersion = 0;
  let documentsVersion = 0;
  function publish(catalogMessage = '') {
    page.setData({ user:state.user, rides:state.rides, favorites:state.favorites, fleet:state.fleet, catalogMessage });
    $('#account-label').textContent = state.user ? 'Личный кабинет' : 'Вход / Регистрация';
    $('#account-button').setAttribute('aria-label', state.user ? 'Открыть личный кабинет' : 'Вход и регистрация');
  }
  function clearSession(message = 'Войдите, чтобы увидеть доступные самокаты.') {
    Object.assign(state, { user:null, rides:[], fleet:[], favorites:[], ridesError:'', documents:[], documentsLoaded:false, documentsError:'', documentsMore:false });
    publish(message);
  }
  function handleError(error) {
    if (error.status === 401) { ++refreshVersion; clearSession(); auth(); }
    page.notify(error.message || 'Не удалось выполнить запрос.');
  }
  async function refresh() {
    const version = ++refreshVersion;
    let user;
    try { user = await service.request('/api/users/me'); }
    catch (error) {
      if (version !== refreshVersion) return;
      clearSession(error.status === 401 ? undefined : error.message);
      if (error.status !== 401) throw error;
      return;
    }
    if (version !== refreshVersion) return;
    if (!user || typeof user.id !== 'string') { clearSession('Некорректный профиль пользователя.'); throw new Error('Некорректный профиль пользователя.'); }
    const results = await Promise.allSettled([service.request('/api/scooters'), service.request('/api/rides')]);
    if (version !== refreshVersion) return;
    const unauthenticated = results.find(r => r.status === 'rejected' && r.reason.status === 401);
    if (unauthenticated) { clearSession(); throw unauthenticated.reason; }
    if(state.user?.id!==user.id){state.documents=[];state.documentsLoaded=false;state.documentsError='';state.documentsMore=false;}
    state.user = user; state.favorites = service.favorites(user.id);
    const [fleet, rides] = results;
    let message = '';
    const fleetValid = fleet.status === 'fulfilled' && Array.isArray(fleet.value) && fleet.value.every(s => s && typeof s.numberPlate === 'string');
    state.fleet = fleetValid ? fleet.value : [];
    if (fleet.status === 'rejected') message = fleet.reason.message;
    else if (!fleetValid) message = 'Некорректный список самокатов.';
    else if (!fleet.value.length) message = 'Самокаты пока не найдены.';
    const ridesValid = rides.status === 'fulfilled' && Array.isArray(rides.value) && rides.value.every(r => r && typeof r.id === 'string' && typeof r.status === 'string');
    state.rides = ridesValid ? rides.value : [];
    state.ridesError = rides.status === 'rejected' ? rides.reason.message : !ridesValid ? 'Некорректная история поездок.' : '';
    publish(message);
  }
  function aside(account = false) {
    const name = state.user?.fullName || state.user?.login || 'Пользователь';
    return `<aside class="account-aside"><a class="brand" href="#home"><svg><use href="#logo"/></svg><span>Scoot<span class="orange">X</span></span></a>${account ? `<div class="account-avatar">${esc(name.slice(0,1).toUpperCase())}</div><h3>${esc(name)}</h3><p class="account-login">@${esc(state.user.login)}</p><nav class="account-nav" aria-label="Разделы личного кабинета"><button data-account-tab="profile">Профиль <span>↗</span></button><button data-account-tab="rides">Мои поездки <span>${state.rides.length}</span></button><button data-account-tab="saved">Избранное <span>${state.favorites.length}</span></button><button data-account-tab="documents">Документы <span>↓</span></button></nav>` : '<h2>Твой город.<br>Твой маршрут.</h2><p>Выбирай самокат рядом.<br>Остальное — дело движения.</p><div class="account-decor"></div>'}</aside>`;
  }
  function shell(content, account = false) { page.openDialog(`<div class="account-shell">${aside(account)}<section class="account-content">${content}</section></div>`, true); }
  function auth(mode = 'login', login = '', error = '') {
    const register = mode === 'register';
    shell(`<div class="account-tabs" role="tablist" aria-label="Вход или регистрация"><button role="tab" aria-selected="${!register}" class="${register ? '' : 'selected'}" data-auth-mode="login">Вход</button><button role="tab" aria-selected="${register}" class="${register ? 'selected' : ''}" data-auth-mode="register">Регистрация</button></div><h2>${register ? 'Создать аккаунт' : 'С возвращением'}</h2><p class="account-subtitle">${register ? 'Профиль и история поездок — в одном месте.' : 'Войди, чтобы выбрать следующий маршрут.'}</p><form id="auth-form" class="account-form" data-mode="${register ? 'register' : 'login'}">${register ? `<label>ФИО<input name="fullName" autocomplete="name" maxlength="200" required></label><label>Дата рождения<input name="birthDate" type="date" min="1900-01-01" max="${new Date().toISOString().slice(0,10)}" autocomplete="bday" required></label>` : ''}<label>Логин<input name="login" autocomplete="username" ${register ? 'minlength="3" maxlength="64" pattern="[A-Za-z0-9_.\\-]{3,64}" title="Латиница, цифры, _, . или -"' : ''} value="${esc(login)}" required></label><label>Пароль<div class="password-field"><input name="password" type="password" autocomplete="${register ? 'new-password' : 'current-password'}" ${register ? 'minlength="8"' : ''} required><button type="button" data-password-toggle aria-label="Показать пароль">Показать</button></div></label>${register ? '<p class="account-subtitle">От 8 символов, не более 72 байт UTF-8. Кириллица и эмодзи занимают больше одного байта.</p><label>Повтори пароль<input name="confirm" type="password" autocomplete="new-password" minlength="8" required></label>' : ''}<p class="form-error" role="alert" ${error ? '' : 'hidden'}>${esc(error)}</p><button class="account-submit" type="submit"><span>${register ? 'Зарегистрироваться' : 'Войти'}</span><b>→</b></button></form><p class="auth-switch">${register ? 'Уже есть аккаунт?' : 'Ещё нет аккаунта?'} <button data-auth-mode="${register ? 'login' : 'register'}">${register ? 'Войти' : 'Создать аккаунт'}</button></p>`);
  }
  function emptyState(title, message) { return `<div class="account-empty"><h3>${esc(title)}</h3><p>${esc(message)}</p><button data-scroll-catalog type="button">Выбрать самокат →</button></div>`; }
  function elapsed(ride) {
    const seconds = ride.status === 'ACTIVE' && Number.isFinite(Date.parse(ride.startedAt))
      ? Math.max(0, Math.floor((Date.now() - Date.parse(ride.startedAt)) / 1000)) : ride.durationSeconds;
    return typeof seconds === 'number' && Number.isFinite(seconds) ? `${Math.floor(seconds / 60)} мин ${Math.floor(seconds % 60)} с` : '—';
  }
  function rideHtml(ride) {
    const active = ride.status === 'ACTIVE', s = state.fleet.find(s => s.numberPlate === ride.numberPlate);
    return `<article class="ride-item"><div class="ride-top">${page.plateHtml(ride.numberPlate)}<span class="ride-status ${active ? 'active' : ride.status === 'PAID' ? 'paid' : 'finished'}">${esc(page.rideStatuses[ride.status] || ride.status)}</span></div>${active && Number.isFinite(ride.lastPosX) && Number.isFinite(ride.lastPosY)?`<p class="ride-date">Последняя позиция: X=${esc(page.coordinate(ride.lastPosX))}, Y=${esc(page.coordinate(ride.lastPosY))} ед. сетки</p>`:''}<p class="ride-date">${esc(page.dateLabel(ride.startedAt))}${ride.finishedAt ? ' → ' + esc(page.dateLabel(ride.finishedAt)) : ''}</p><div class="ride-metrics"><div><small>Время</small><b ${active ? `data-elapsed="${esc(ride.startedAt)}"` : ''}>${elapsed(ride)}</b></div><div><small>Заряд самоката</small><b>${page.chargeLabel(s)}</b></div><div><small>${active ? 'Сумма от сервиса' : 'Итоговая сумма'}</small><b>${esc(page.money(ride.amount, ride.currency))}</b></div></div>${active ? `<button class="ride-button" data-position-ride="${esc(ride.id)}" type="button">Обновить позицию на сетке</button><button class="ride-button" data-finish-ride="${esc(ride.id)}" type="button">Завершить поездку →</button>` : ride.status === 'FINISHED' ? `<button class="ride-button" data-pay-ride="${esc(ride.id)}" type="button">Оплатить →</button>` : ride.status === 'PAID' ? `<span class="payment-complete">✓ ${ride.paymentMode==='EDUCATIONAL'?'Учебная оплата подтверждена':'Оплата подтверждена'}</span><button class="ride-button" data-receipt="${esc(ride.id)}" type="button">Скачать чек PDF ↓</button>` : ''}</article>`;
  }
  function account(tab = 'profile') {
    if (!state.user) { auth(); return; }
    if (!['profile','rides','saved','documents'].includes(tab)) tab = 'profile';
    const titles = {profile:'Мой профиль', rides:'Мои поездки', saved:'Избранное', documents:'Документы'};
    const heading = `<div class="account-topline"><h2>${titles[tab]}</h2><button class="account-logout" data-logout type="button">Выйти ↗</button></div>`;
    let content;
    if (tab === 'profile') {
      content = `<p class="account-subtitle">Ваши регистрационные данные. Профиль доступен для просмотра.</p><dl class="profile-data"><dt>ФИО</dt><dd>${esc(state.user.fullName)}</dd><dt>Логин</dt><dd>${esc(state.user.login)}</dd><dt>Дата рождения</dt><dd>${esc(state.user.birthDate)}</dd><dt>Статус</dt><dd>${state.user.blocked ? 'Пользователь заблокирован' : 'Блокировок нет'}</dd></dl>`;
    } else if (tab === 'rides') {
      content = `<p class="account-subtitle">Текущий маршрут и история поездок. Стоимость рассчитывается по тарифу поездки.</p><button data-account-tab="rides" type="button">Обновить историю</button>${state.ridesError ? `<p class="form-error" role="alert">${esc(state.ridesError)}</p>` : state.rides.length ? `<div class="ride-list">${state.rides.map(rideHtml).join('')}</div>` : emptyState('Первый маршрут — впереди', 'После старта поездка появится здесь.')}`;
    } else if (tab === 'saved') {
      const scooters = state.fleet.filter(s => state.favorites.includes(s.numberPlate));
      content = `<p class="account-subtitle">Избранное хранится только в этом браузере, отдельно для каждого пользователя.</p>${scooters.length ? `<div class="saved-list">${scooters.map(s => `<article class="saved-row"><div class="product-picture crop" data-crop="${page.photoCrop.join(',')}"></div><div>${page.plateHtml(s.numberPlate)}<p>Заряд: ${page.chargeLabel(s)}</p><button data-scooter="${esc(s.numberPlate)}" type="button">Открыть</button> <button data-unsave="${esc(s.numberPlate)}" type="button">Убрать</button></div></article>`).join('')}</div>` : emptyState('Сохранённых самокатов нет', 'Нажми на сердечко в карточке самоката.')}`;
    } else {
      content = `<p class="account-subtitle">Чеки оплаченных поездок доступны в разделе «Мои поездки». Период истории задаётся по датам UTC.</p><form class="account-form" id="history-document-form"><label>Период с<input type="date" name="from" required></label><label>По<input type="date" name="to" required></label><p class="form-error" role="alert" hidden></p><button class="account-submit" type="submit">Скачать историю поездок PDF</button></form>${state.user.blocked ? '<button class="ride-button" data-blocked-document type="button">Уведомление о блокировке PDF</button>' : ''}`;
    }
    if(tab==='documents') {
      const types={RIDE_PAYMENT:'Чек поездки',USER_BLOCKED:'Уведомление о блокировке',USER_HISTORY:'История поездок'};
      const statuses={GENERATED:'Сформирован',FAILED:'Ошибка генерации',PENDING:'Формируется'};
      content+=`<h3>История документов</h3><button data-documents-refresh type="button">Обновить список</button>${state.documentsError?`<p class="form-error" role="alert">${esc(state.documentsError)}</p>`:''}${!state.documentsLoaded?'<p>Загружаем документы…</p>':state.documents.length?state.documents.map(d=>`<article class="ride-item"><strong>${esc(types[d.docType]||d.docType)}</strong><p>${esc(page.dateLabel(d.createdAt))} · ${esc(statuses[d.status]||d.status)}</p>${d.status==='GENERATED'?`<button data-document-download="${d.id}" type="button">${d.snapshotAvailable?'Скачать повторно PDF':'Восстановить PDF'}</button>`:d.status==='FAILED'?`<button data-document-retry="${d.id}" type="button">Повторить генерацию</button>`:''} <button data-document-delete="${d.id}" type="button">Удалить из истории</button></article>`).join(''):'<p>Документов ещё нет.</p>'}${state.documentsMore?'<button data-documents-more type="button">Показать ещё</button>':''}`;
    }
    shell(heading + content, true);
    if(tab==='documents'&&!state.documentsLoaded)loadDocuments();
    document.querySelectorAll('[data-account-tab]').forEach(button => button.classList.toggle('selected', button.dataset.accountTab === tab));
  }
  async function loadDocuments(append=false) {
    const owner=state.user?.id;if(!owner)return;
    const version=++documentsVersion;
    state.documentsLoaded=true;
    try {
      const list=await service.request('/api/documents?limit=50&offset='+(append?state.documents.length:0));
      if(state.user?.id!==owner || version!==documentsVersion)return;
      if(!Array.isArray(list)||!list.every(d=>d&&typeof d.id==='string'&&/^[0-9a-fA-F-]{36}$/.test(d.id)&&typeof d.docType==='string'))throw new Error('Некорректная история документов.');
      state.documents=append?[...state.documents,...list]:list;state.documentsMore=list.length===50;state.documentsError='';
    }catch(error){if(state.user?.id!==owner || version!==documentsVersion)return;if(error.status===401){handleError(error);return;}state.documentsError=error.message;}
    const form=document.querySelector('#history-document-form');
    if(form && dialog.open){const range=Object.fromEntries(new FormData(form));account('documents');const next=document.querySelector('#history-document-form');if(next){next.elements.from.value=range.from||'';next.elements.to.value=range.to||'';}}

  }
  async function entry() { try { state.pendingStart = null; await refresh(); state.user ? account() : auth(); } catch (error) { handleError(error); } }
  function coordinatesFields() { return '<label>Координата X<input name="posX" type="number" step="any" min="-1000000" max="1000000" required></label><label>Координата Y<input name="posY" type="number" step="any" min="-1000000" max="1000000" required></label><p class="account-subtitle">Учебная координатная сетка. Введите X/Y в единицах сетки, например 10 и 15. Диапазон каждой координаты: от −1 000 000 до 1 000 000. Это не GPS и не метры.</p>'; }

  async function confirmStart(plate) {
    state.pendingStart = plate;
    if (!state.user) { auth(); return; }
    if (state.user.blocked) { account(); page.notify('Пользователь заблокирован. Начать поездку нельзя.'); return; }
    const scooter = state.fleet.find(s => s.numberPlate === plate);
    if (!scooter || scooter.status !== 'READY') { page.notify('Самокат сейчас недоступен. Обновите каталог.'); return; }
    if (state.ridesError) { account('rides'); return; }
    if (state.rides.some(r => r.status === 'ACTIVE')) { account('rides'); page.notify('Сначала завершите текущую поездку.'); return; }
    const origin=dialog.querySelector('[data-start-ride],#auth-form');
    let tariff='';
    try {
      const rates=await service.request('/api/rates');
      if(!Array.isArray(rates))throw new Error('Некорректный список тарифов.');
      const rate=rates.find(r=>r.default===true)||rates[0];
      if(!rate || !Number.isFinite(Number(rate.unlockFee)) || !Number.isFinite(Number(rate.pricePerMinute)))throw new Error('Тариф недоступен.');
      tariff=`${esc(page.money(rate.unlockFee,rate.currency))} за старт + ${esc(page.money(rate.pricePerMinute,rate.currency))} за каждую начатую минуту (минимум одна минута).`;
      if(rate.paymentMode==='EDUCATIONAL')tariff+=' Учебный режим: реальные деньги не списываются.';
    } catch(error) { page.notify(error.message); return; }
    if(!state.user || state.pendingStart!==plate || !dialog.open || (origin && !origin.isConnected))return;
    shell(`<button data-account-tab="rides" type="button">← Мои поездки</button><h2>Готов к движению?</h2>${page.plateHtml(plate)}<p class="booking-notice">${tariff} Запрос начнёт поездку.</p><form class="account-form" id="start-ride-form" data-plate="${esc(plate)}">${coordinatesFields()}<p class="form-error" role="alert" hidden></p><button class="account-submit" type="submit">Начать поездку</button></form>`, true);
  }
  function confirmFinish(id) {
    const ride = state.rides.find(r => r.id === id);
    if (!ride || ride.status !== 'ACTIVE') { account('rides'); return; }
    shell(`<button data-account-tab="rides" type="button">← Мои поездки</button><h2>Завершить поездку</h2>${page.plateHtml(ride.numberPlate)}<p class="account-subtitle">Укажи координаты завершения. После подтверждения будет рассчитана итоговая сумма.</p><form class="account-form" id="finish-ride-form" data-ride-id="${esc(id)}">${coordinatesFields()}<p class="form-error" role="alert" hidden></p><button class="account-submit" type="submit">Завершить маршрут</button></form>`, true);
  }
  function confirmPay(id) {
    const ride = state.rides.find(r => r.id === id);
    if (!ride || ride.status !== 'FINISHED') { account('rides'); return; }
    shell(`<button data-account-tab="rides" type="button">← Мои поездки</button><h2>Оплата поездки</h2>${page.plateHtml(ride.numberPlate)}<div class="payment-total"><small>Итоговая стоимость</small><strong>${esc(page.money(ride.amount, ride.currency))}</strong></div><p class="booking-notice">${ride.paymentMode === 'EDUCATIONAL' ? 'Учебная оплата: реальные деньги не списываются.' : 'Подтвердите оплату поездки. Статус обновится после успешной операции.'}</p><form class="account-form" id="pay-ride-form" data-ride-id="${esc(id)}"><p class="form-error" role="alert" hidden></p><button class="account-submit" type="submit">Оплатить поездку</button></form>`, true);
  }
  function nearestForm() {
    if (!state.user) { auth(); return; }
    shell(`<h2>Самокаты рядом</h2><form class="account-form" id="nearest-form">${coordinatesFields()}<p class="form-error" role="alert" hidden></p><button class="account-submit" type="submit">Найти самокаты</button></form>`, true);
  }
  function coords(values) {
    const x = values.posX, y = values.posY;
    if (typeof x !== 'string' || typeof y !== 'string' || !x.trim() || !y.trim() || !Number.isFinite(Number(x)) || !Number.isFinite(Number(y))) throw new Error('Укажите числовые координаты X и Y.');
    return {posX:Number(x), posY:Number(y)};
  }
  async function submit(form) {
    if (form.dataset.submitting === 'true' || !form.reportValidity()) return;
    form.dataset.submitting = 'true';
    const values = Object.fromEntries(new FormData(form));
    const error = form.querySelector('.form-error'), button = form.querySelector('[type="submit"]');
    error.hidden = true; button.disabled = true;
    let committed = '';
    try {
      if (form.id === 'auth-form') {
        const register = form.dataset.mode === 'register';
        if (register) {
          service.validateRegistration(values);
          await service.request('/api/auth/register', 'POST', {login:values.login, password:values.password, fullName:values.fullName, birthDate:values.birthDate, avatarUrl:null});
        }
        let authResult;
        try { authResult = await service.request('/api/auth/login', 'POST', {login:values.login, password:values.password}); }
        catch (e) { if (register) { auth('login', values.login, 'Аккаунт создан. Войдите в него: ' + e.message); return; } throw e; }
        if (await service.redirectAdmin(authResult)) return;
        await refresh();
        const entryUrl=new URL(location.href);entryUrl.searchParams.delete('auth');history.replaceState(null,'',entryUrl.pathname+entryUrl.search+entryUrl.hash);
        state.pendingStart ? confirmStart(state.pendingStart) : account();
        page.notify(register ? 'Аккаунт создан' : 'Ты вошёл в аккаунт');
      } else if (form.id === 'start-ride-form') {
        await service.request('/api/rides', 'POST', {numberPlate:form.dataset.plate, ...coords(values)});
        committed = 'Поездка начата'; state.pendingStart = null;
        await refresh(); if(dialog.open&&form.isConnected)account('rides'); page.notify(committed);
      } else if (form.id === 'position-ride-form') {
        await service.request('/api/rides/'+encodeURIComponent(form.dataset.rideId)+'/position','PUT',coords(values));
        committed='Позиция обновлена';await refresh();if(dialog.open&&form.isConnected)account('rides');page.notify(committed);
      } else if (form.id === 'finish-ride-form') {
        await service.request('/api/rides/' + encodeURIComponent(form.dataset.rideId) + '/finish', 'PUT', coords(values));
        committed = 'Поездка завершена'; await refresh(); if(dialog.open&&form.isConnected)account('rides'); page.notify(committed);
      } else if (form.id === 'pay-ride-form') {
        await service.request('/api/rides/' + encodeURIComponent(form.dataset.rideId) + '/pay', 'POST', {});
        committed = 'Оплата подтверждена'; await refresh(); if(dialog.open&&form.isConnected)account('rides'); page.notify(committed);
      } else if (form.id === 'nearest-form') {
        const scooters = await service.request('/api/scooters/nearest', 'POST', coords(values));
        if (!Array.isArray(scooters)) throw new Error('Некорректный список ближайших самокатов.');
        state.fleet = scooters;
        publish(scooters.length ? 'Результаты поиска по координатам. Расстояние — в единицах учебной сетки.' : 'Свободных самокатов рядом нет.');
        dialog.close(); document.getElementById('scooters').scrollIntoView();
      } else if (form.id === 'history-document-form') {
        if (values.from > values.to) throw new Error('Начало периода должно быть не позже конца.');
        await service.downloadDocument({docType:'USER_HISTORY', from:values.from, to:values.to});await loadDocuments();
      }
    } catch (e) {
      if (committed) { if(dialog.open&&form.isConnected)account('rides'); page.notify(committed + '. Не удалось обновить данные: ' + e.message); }
      else if (e.status === 401 && form.id !== 'auth-form') handleError(e);
      else if (form.isConnected) { error.textContent = e.message; error.hidden = false; }
      else page.notify(e.message);
    } finally { button.disabled = false; delete form.dataset.submitting; }
  }
  document.addEventListener('submit', event => {
    if (['auth-form','start-ride-form','finish-ride-form','position-ride-form','pay-ride-form','nearest-form','history-document-form'].includes(event.target.id)) { event.preventDefault(); submit(event.target); }
  });
  document.addEventListener('click', async event => {
    const action = event.target.closest('[data-auth-mode],[data-password-toggle],[data-account-tab],[data-start-ride],[data-finish-ride],[data-pay-ride],[data-unsave],[data-logout],[data-position-ride],[data-document-download],[data-document-delete],[data-document-retry],[data-documents-refresh],[data-documents-more],[data-receipt],[data-blocked-document]');
    if (!action || action.disabled) return;
    try {
      if (action.hasAttribute('data-auth-mode')) { auth(action.dataset.authMode); return; }
      if (action.hasAttribute('data-password-toggle')) {
        const input = action.parentElement.querySelector('input'); input.type = input.type === 'password' ? 'text' : 'password';
        action.textContent = input.type === 'password' ? 'Показать' : 'Скрыть'; return;
      }
      action.disabled = true;
      if (action.hasAttribute('data-account-tab')) { await refresh(); if(!dialog.open||!action.isConnected)return; if(action.dataset.accountTab==='documents')state.documentsLoaded=false;account(action.dataset.accountTab); }
      else if (action.hasAttribute('data-start-ride')) { await refresh(); if(dialog.open&&action.isConnected)confirmStart(action.dataset.startRide); }
      else if (action.hasAttribute('data-finish-ride')) { await refresh(); if(dialog.open&&action.isConnected)confirmFinish(action.dataset.finishRide); }
      else if (action.hasAttribute('data-pay-ride')) { await refresh(); if(dialog.open&&action.isConnected)confirmPay(action.dataset.payRide); }
      else if (action.hasAttribute('data-unsave')) { state.favorites = service.setFavorite(state.user?.id, action.dataset.unsave, false); publish(); account('saved'); }
      else if(action.hasAttribute('data-position-ride')) {
        shell(`<h2>Позиция активной поездки</h2><form class="account-form" id="position-ride-form" data-ride-id="${esc(action.dataset.positionRide)}">${coordinatesFields()}<p class="form-error" role="alert" hidden></p><button class="account-submit" type="submit">Сохранить позицию</button></form>`,true);
      }
      else if(action.hasAttribute('data-documents-refresh'))await loadDocuments();
      else if(action.hasAttribute('data-documents-more'))await loadDocuments(true);
      else if(action.hasAttribute('data-document-download'))await service.downloadDocument(undefined,action.dataset.documentDownload);
      else if(action.hasAttribute('data-document-delete')) {
        if(window.confirm('Удалить документ из вашей истории?')){await service.request('/api/documents/'+encodeURIComponent(action.dataset.documentDelete),'DELETE');await loadDocuments();}
      }
      else if(action.hasAttribute('data-document-retry')) {
        const item=state.documents.find(d=>d.id===action.dataset.documentRetry);
        if(item){await service.downloadDocument({docType:item.docType,sourceId:item.sourceId,from:item.from,to:item.to});await loadDocuments();page.notify('Новый документ добавлен в историю.');}
      }
      else if (action.hasAttribute('data-receipt')) await service.downloadDocument({docType:'RIDE_PAYMENT', sourceId:action.dataset.receipt});
      else if (action.hasAttribute('data-blocked-document')) {await service.downloadDocument({docType:'USER_BLOCKED'});await loadDocuments();
      } else if (action.hasAttribute('data-logout')) {
        let warning = '';
        try { await service.request('/api/auth/logout', 'POST'); } catch (e) { warning = e.message; }
        ++refreshVersion; state.pendingStart = null; clearSession(); auth();
        page.notify(warning || 'Ты вышел из аккаунта');
      }
    } catch (error) { handleError(error); }
    finally { action.disabled = false; }
  });
  dialog.addEventListener('close',()=>{if(!dialog.open)state.pendingStart=null;});
  $('#account-label').addEventListener('click', entry);
  $('#account-button').addEventListener('click', entry);
  $('#refresh-fleet').addEventListener('click', async () => { try { await refresh(); } catch (e) { handleError(e); } });
  $('#nearest-fleet').addEventListener('click', nearestForm);
  setInterval(() => document.querySelectorAll('[data-elapsed]').forEach(el => { el.textContent = elapsed({status:'ACTIVE', startedAt:el.dataset.elapsed}); }), 1000);
  window.ScootxAccount = {open:entry, refresh, handleError, updateFavorites:items => { state.favorites = items; }};
  refresh().then(() => {
    const query = new URLSearchParams(location.search);
    if (query.get('auth') === 'register') auth('register');
    else if (query.get('auth') === 'login') auth();
    else if (query.has('account')) account(query.get('account'));
  }).catch(handleError);
})();
