document.addEventListener('DOMContentLoaded', () => {
  const listEl = document.getElementById('list');
  const ridesEl = document.getElementById('rides');
  const coordsEl = document.getElementById('coords');
  const messageEl = document.getElementById('message');
  let nearestVersion = 0;
  let ridesVersion = 0;

  function button(label, action) {
    const element = document.createElement('button');
    element.type = 'button';
    element.textContent = label;
    element.addEventListener('click', async () => {
      element.disabled = true;
      messageEl.textContent = '';
      try { await action(); }
      catch (error) { messageEl.textContent = error.message || String(error); }
      finally { element.disabled = false; }
    });
    return element;
  }

  async function loadNearest() {
    const version = ++nearestVersion;
    listEl.textContent = 'Определяем координаты...';
    try {
      const { posX, posY } = await getUserCoords();
      const response = await apiRequest('/api/scooters/nearest', jsonOptions('POST', { posX, posY }));
      const scooters = await response.json();
      if (version !== nearestVersion) return;
      if (!Array.isArray(scooters)) throw new Error('Некорректный список самокатов');
      coordsEl.textContent = `Ваша позиция: posX=${posX.toFixed(5)}, posY=${posY.toFixed(5)}`;
      listEl.replaceChildren();
      if (!scooters.length) listEl.textContent = 'Рядом нет самокатов со статусом READY';
      scooters.forEach(scooter => {
        const row = document.createElement('div');
        row.className = 'scooter-item';
        const description = document.createElement('span');
        // Units depend on the Scooters contract; do not label grid distances as metres.
        description.textContent = `${scooter.numberPlate} · ${scooter.status}`
          + (scooter.charge != null ? ` · заряд ${scooter.charge}%` : '')
          + (scooter.distance != null ? ` · расстояние ${scooter.distance}` : '');
        row.append(description, button('Заказать', () => startRide(scooter.numberPlate)));
        listEl.append(row);
      });
    } catch (error) {
      if (version === nearestVersion) listEl.textContent = error.message || String(error);
    }
  }

  async function loadRides() {
    const version = ++ridesVersion;
    try {
      const rides = await (await apiRequest('/api/rides')).json();
      if (version !== ridesVersion) return;
      if (!Array.isArray(rides)) throw new Error('Некорректная история поездок');
      ridesEl.replaceChildren();
      if (!rides.length) ridesEl.textContent = 'Поездок пока нет';
      rides.forEach(ride => {
        const row = document.createElement('div');
        const description = document.createElement('p');
        description.textContent = `Поездка ${ride.id} · ${ride.numberPlate || ''} · ${ride.status}`
          + (ride.amount != null ? ` · ${ride.amount} ${ride.currency || ''}` : '');
        row.append(description);
        if (ride.status === 'ACTIVE') row.append(button('Завершить и оплатить', () => finishRide(ride.id)));
        if (ride.status === 'FINISHED') row.append(button('Оплатить', () => payRide(ride.id)));
        if (ride.status === 'PAID') row.append(button('Скачать чек', () => downloadDocument({ docType: 'RIDE_PAYMENT', sourceId: ride.id })));
        ridesEl.append(row);
      });
    } catch (error) {
      if (version === ridesVersion) ridesEl.textContent = error.message || String(error);
    }
  }

  async function refresh() { await Promise.all([loadNearest(), loadRides()]); }
  async function startRide(numberPlate) {
    const coords = await getUserCoords();
    await apiRequest('/api/rides', jsonOptions('POST', { numberPlate, ...coords }));
    messageEl.textContent = 'Поездка начата';
    await refresh();
  }
  async function payRide(rideId) {
    await apiRequest(`/api/rides/${encodeURIComponent(rideId)}/pay`, jsonOptions('POST', {}));
    messageEl.textContent = 'Поездка оплачена. Чек доступен в истории.';
    await loadRides();
  }
  async function finishRide(rideId) {
    const coords = await getUserCoords();
    await apiRequest(`/api/rides/${encodeURIComponent(rideId)}/finish`, jsonOptions('PUT', coords));
    try { await payRide(rideId); }
    finally { await refresh(); } // A failed payment remains retryable as FINISHED.
  }

  document.getElementById('btn-refresh').addEventListener('click', refresh);
  document.getElementById('documents').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = form.querySelector('button');
    submit.disabled = true;
    try {
      const values = new FormData(form);
      await downloadDocument({ docType: 'USER_HISTORY', from: values.get('from'), to: values.get('to') });
    } catch (error) { messageEl.textContent = error.message || String(error); }
    finally { submit.disabled = false; }
  });
  document.getElementById('blocked-document').append(button('Уведомление о блокировке', () => downloadDocument({ docType: 'USER_BLOCKED' })));
  refresh();
});
