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
      await apiRequest('/api/auth/login', jsonOptions('POST', values));
      location.assign((document.body.dataset.ctx || '') + '/main.jsp');
    } catch (error) { err.textContent = error.message || String(error); }
    finally { submit.disabled = false; }
  });
});
