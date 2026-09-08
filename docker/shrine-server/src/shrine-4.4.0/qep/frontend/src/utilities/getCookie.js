const SUPPORTED_COOKIES = ["isSsoMode"];

export const getCookie = (name) => {
  let cookieMap = new Map();
  document.cookie.split(';').forEach(function(el) {
    let [k,v] = el.split('=');
    cookieMap.set(k.trim(), v);
  });

  return SUPPORTED_COOKIES.includes(name) ? cookieMap.get(name) : null;
}
