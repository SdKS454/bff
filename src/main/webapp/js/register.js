document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('f');
  const err = document.getElementById('err');
  const password = form.elements.namedItem('password');
  const fullName = form.elements.namedItem('fullName');
  const birthDate = form.elements.namedItem('birthDate');
  birthDate.max = new Date().toISOString().slice(0, 10); // User Service uses UTC.

  function validatePassword() {
    const value = password.value;
    const invalid = value.length < 8 || value.includes('\0') || new TextEncoder().encode(value).length > 72;
    password.setCustomValidity(invalid ? 'Пароль: минимум 8 символов, максимум 72 байта UTF-8' : '');
  }
  function validateName() {
    const value = fullName.value;
    const invalid = !value.trim() || value.trim().length > 200 || /[\u0000-\u001f\u007f-\u009f]/.test(value);
    fullName.setCustomValidity(invalid ? 'Укажите полное имя длиной до 200 символов без управляющих символов' : '');
  }
  password.addEventListener('input', validatePassword);
  fullName.addEventListener('input', validateName);
  form.addEventListener('submit', async event => {
    event.preventDefault();
    err.textContent = '';
    validatePassword();
    validateName();
    if (!form.reportValidity()) return;
    const submit = form.querySelector('button');
    submit.disabled = true;
    try {
      const values = Object.fromEntries(new FormData(form));
      await apiRequest('/api/auth/register', jsonOptions('POST', values));
      location.assign((document.body.dataset.ctx || '') + '/login.jsp');
    } catch (error) { err.textContent = error.message || String(error); }
    finally { submit.disabled = false; }
  });
});
