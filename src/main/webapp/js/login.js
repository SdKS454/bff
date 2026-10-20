document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('f');
  const err = document.getElementById('err');
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const submit = form.querySelector('button');
    submit.disabled = true;
    err.textContent = '';
    try {
      const values = Object.fromEntries(new FormData(form));
      const auth = await (await apiRequest('/api/auth/login', jsonOptions('POST', values))).json();
      if (auth.user?.role === 'ADMIN') {
        const config = await (await apiRequest('/api/config')).json();
        if (!config.moderationPanelUrl) throw new Error('Адрес панели Moderation ещё не настроен. Обратитесь к администратору.');
        const target = new URL(config.moderationPanelUrl);
        if (!['http:', 'https:'].includes(target.protocol) || target.username || target.password || target.hash)
          throw new Error('Некорректный адрес панели Moderation');
        if (!auth.accessToken) throw new Error('User Service не вернул токен');
        target.hash = 'accessToken=' + encodeURIComponent(auth.accessToken);
        location.assign(target.href);
      } else {
        location.assign((document.body.dataset.ctx || '') + '/main.jsp');
      }
    } catch (error) { err.textContent = error.message || String(error); }
    finally { submit.disabled = false; }
  });
});
