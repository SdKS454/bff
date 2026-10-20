/* Same-origin API client. BFF uses an HttpOnly session; ADMIN login also hands JWT to Moderation via a URL fragment. */
async function apiRequest(path, options = {}) {
  const ctx = document.body.dataset.ctx || '';
  const response = await fetch(ctx + path, options);
  if (!response.ok) {
    const text = await response.text();
    let message = text || `HTTP ${response.status}`;
    try { message = JSON.parse(text).error || message; } catch (_) { /* Non-JSON upstream error. */ }
    if (response.status === 401 && !path.startsWith('/api/auth/')) {
      location.assign(ctx + '/login.jsp');
    }
    throw new Error(message);
  }
  return response;
}
function jsonOptions(method, body) {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}
async function downloadDocument(body) {
  const response = await apiRequest('/api/documents/generate', jsonOptions('POST', body));
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = 'document.pdf';
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
