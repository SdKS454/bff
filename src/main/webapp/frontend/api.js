'use strict';
(() => {
  const memory = new Map();
  const ctx = document.body.dataset.ctx || '';
  class ApiError extends Error {
    constructor(status, message) { super(message); this.status = status; }
  }
  async function response(path, method = 'GET', body) {
    let result;
    try {
      result = await fetch(ctx + path, {
        method, credentials: 'same-origin', cache: 'no-store',
        ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
      });
    } catch (_) { throw new ApiError(0, 'Не удалось связаться с BFF. Проверьте соединение и повторите запрос.'); }
    if (!result.ok) {
      const text = await result.text();
      let message = `Ошибка сервиса: HTTP ${result.status}`;
      try { const error = JSON.parse(text); if (typeof error.error === 'string') message = error.error; } catch (_) { /* Do not render upstream HTML. */ }
      throw new ApiError(result.status, message);
    }
    return result;
  }
  async function request(path, method = 'GET', body) {
    const result = await response(path, method, body);
    if (result.status === 204) return null;
    try { return await result.json(); }
    catch (_) { throw new ApiError(502, 'Сервис вернул некорректный JSON.'); }
  }
  async function downloadDocument(body) {
    const result = await response('/api/documents/generate', 'POST', body);
    if (!/^application\/pdf(?:;|$)/i.test(result.headers.get('Content-Type') || ''))
      throw new ApiError(502, 'Сервис не вернул PDF-документ.');
    const url = URL.createObjectURL(await result.blob());
    const link = document.createElement('a');
    link.href = url; link.download = 'document.pdf';
    document.body.append(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  async function redirectAdmin(auth) {
    if (auth.user?.role !== 'ADMIN') return false;
    const config = await request('/api/config');
    if (!config.moderationPanelUrl || !auth.accessToken) throw new Error('Переход в Moderation не настроен.');
    const target = new URL(config.moderationPanelUrl);
    if (!['http:', 'https:'].includes(target.protocol) || target.username || target.password || target.hash)
      throw new Error('Некорректный адрес панели Moderation.');
    target.hash = 'accessToken=' + encodeURIComponent(auth.accessToken);
    location.replace(target.href);
    return true;
  }
  const key = userId => 'scootx:favorites:v1:' + userId;
  function favorites(userId) {
    if (!userId) return [];
    if (memory.has(userId)) return memory.get(userId);
    try {
      const items = JSON.parse(localStorage.getItem(key(userId)) || '[]');
      return Array.isArray(items) ? [...new Set(items.filter(x => typeof x === 'string'))] : [];
    } catch (_) { return memory.get(userId) || []; }
  }
  function setFavorite(userId, plate, enabled) {
    if (!userId) throw new Error('Войдите в аккаунт.');
    const items = favorites(userId).filter(x => x !== plate);
    if (enabled) items.push(plate);
    memory.set(userId, items);
    try { localStorage.setItem(key(userId), JSON.stringify(items)); } catch (_) { /* Device storage can be disabled. */ }
    return items;
  }
  function validateRegistration(values) {
    if (!/^[A-Za-z0-9_.-]{3,64}$/.test(values.login || '')) throw new Error('Логин: 3–64 символа, латиница, цифры, _, . или -.');
    if ((values.password || '').length < 8 || values.password.includes('\0') || new TextEncoder().encode(values.password).length > 72)
      throw new Error('Пароль: минимум 8 символов, максимум 72 байта UTF-8.');
    if (!values.fullName?.trim() || values.fullName.trim().length > 200 || /[\u0000-\u001f\u007f-\u009f]/.test(values.fullName))
      throw new Error('Укажите ФИО до 200 символов без управляющих символов.');
    const date = new Date((values.birthDate || '') + 'T00:00:00Z');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(values.birthDate || '') || !Number.isFinite(date.getTime())
        || date.toISOString().slice(0, 10) !== values.birthDate || values.birthDate < '1900-01-01'
        || values.birthDate > new Date().toISOString().slice(0, 10)) throw new Error('Укажите корректную дату рождения.');
    if (values.password !== values.confirm) throw new Error('Пароли не совпадают.');
  }
  window.ScootxApi = { request, downloadDocument, redirectAdmin, favorites, setFavorite, validateRegistration, ApiError };
})();
