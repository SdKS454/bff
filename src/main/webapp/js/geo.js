function getUserCoords() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('Геолокация не поддерживается'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({
        posX: pos.coords.longitude,
        posY: pos.coords.latitude
      }),
      (err) => reject(err),
      { enableHighAccuracy: true, timeout: 10000 }
    );
  });
}
