/* ほうげん予測変換 service worker: offline-first. Bump VERSION on every release. */
const VERSION = "hogen-0.1.0";
const SHELL = ["./", "index.html", "dict.json", "manifest.webmanifest",
  "icons/icon-192.png", "icons/icon-512.png", "icons/maskable-512.png", "icons/apple-touch-icon.png"];
self.addEventListener("install", e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith("hogen-") && k !== VERSION).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", e => {
  const req = e.request;
  if (req.method !== "GET" || new URL(req.url).origin !== location.origin) return;
  // ページと辞書はネット優先（更新がすぐ届く）、オフライン時はキャッシュ
  const fresh = req.mode === "navigate" || req.url.endsWith("dict.json");
  if (fresh) {
    const key = req.mode === "navigate" ? "index.html" : req;
    e.respondWith(fetch(req).then(r => { if (r.ok) { const copy = r.clone(); caches.open(VERSION).then(c => c.put(key, copy)); } return r; })
      .catch(() => caches.match(key)));
    return;
  }
  e.respondWith(caches.match(req).then(hit => hit || fetch(req)));
});
