/* WikiRandomizer-P service worker: installable app shell + offline article cache. */
const VERSION = 'v3';
const SHELL_CACHE = `wikireader-shell-${VERSION}`;
const RUNTIME_CACHE = `wikireader-runtime-${VERSION}`;
const ARTICLE_CACHE = `wikireader-articles-${VERSION}`;
const IMAGE_CACHE = `wikireader-images-${VERSION}`;
const CURRENT_CACHES = new Set([SHELL_CACHE, RUNTIME_CACHE, ARTICLE_CACHE, IMAGE_CACHE]);

const SHELL_ASSETS = [
  './',
  './index.html',
  './offline.html',
  './styles.css',
  './app.js',
  './manifest.webmanifest',
  './icons/favicon-32.png',
  './icons/apple-touch-icon.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png',
  './wasm/WikiRandomizer-P-shared.mjs',
  './wasm/WikiRandomizer-P-shared.uninstantiated.mjs',
  './wasm/WikiRandomizer-P-shared.wasm'
];

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL_CACHE);
    // Cache each file independently so one missing optional asset cannot break installation.
    await Promise.all(SHELL_ASSETS.map(async (path) => {
      try { await cache.add(new Request(path, { cache: 'reload' })); }
      catch (error) { console.warn('[sw] could not precache', path, error); }
    }));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter((name) => !CURRENT_CACHES.has(name)).map((name) => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting();
  if (event.data?.type === 'CLEAR_CACHES') {
    event.waitUntil((async () => {
      await Promise.all([...CURRENT_CACHES].map((name) => caches.delete(name)));
      await self.clients.matchAll({ type: 'window' }).then((clients) => {
        clients.forEach((client) => client.postMessage({ type: 'CACHES_CLEARED' }));
      });
    })());
  }
});

function isWikipedia(url) {
  return url.hostname === 'wikipedia.org' || url.hostname.endsWith('.wikipedia.org') ||
    url.hostname === 'wikimedia.org' || url.hostname.endsWith('.wikimedia.org');
}

function isArticleHtml(url) {
  return /\/api\/rest_v1\/page\/html\//.test(url.pathname);
}

function isApi(url) {
  return /\/api\/rest_v1\//.test(url.pathname) || /\/w\/api\.php$/.test(url.pathname);
}

function isImage(request, url) {
  return request.destination === 'image' || /\.(png|jpe?g|gif|webp|svg|avif|tiff?)$/i.test(url.pathname);
}

async function trimCache(name, maxEntries) {
  const cache = await caches.open(name);
  const keys = await cache.keys();
  if (keys.length <= maxEntries) return;
  await Promise.all(keys.slice(0, keys.length - maxEntries).map((key) => cache.delete(key)));
}

async function networkFirst(request, cacheName, fallback) {
  const cache = await caches.open(cacheName);
  try {
    const response = await fetch(request);
    if (response && (response.ok || response.type === 'opaque')) cache.put(request, response.clone());
    return response;
  } catch (error) {
    const cached = await cache.match(request, { ignoreVary: true });
    if (cached) return cached;
    if (fallback) return fallback();
    throw error;
  }
}

async function cacheFirst(request, cacheName) {
  const cache = await caches.open(cacheName);
  const cached = await cache.match(request, { ignoreVary: true });
  if (cached) return cached;
  const response = await fetch(request);
  if (response && (response.ok || response.type === 'opaque')) {
    cache.put(request, response.clone()).then(() => {
      if (cacheName === IMAGE_CACHE) trimCache(IMAGE_CACHE, 80);
    }).catch(() => {});
  }
  return response;
}

async function staleWhileRevalidate(request) {
  const cache = await caches.open(SHELL_CACHE);
  const cached = await cache.match(request, { ignoreVary: true });
  const network = fetch(request).then((response) => {
    if (response && response.ok) cache.put(request, response.clone());
    return response;
  }).catch(() => null);
  return cached || (await network) || Response.error();
}

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);

  if (url.origin === self.location.origin) {
    if (request.mode === 'navigate') {
      event.respondWith((async () => {
        try {
          return await fetch(request);
        } catch (error) {
          const cache = await caches.open(SHELL_CACHE);
          return (await cache.match('./index.html')) || (await cache.match('./offline.html')) ||
            new Response('Offline', { status: 503, headers: { 'Content-Type': 'text/plain' } });
        }
      })());
      return;
    }
    event.respondWith(staleWhileRevalidate(request));
    return;
  }

  if (!isWikipedia(url)) return;

  if (isArticleHtml(url)) {
    event.respondWith(networkFirst(request, ARTICLE_CACHE, () =>
      new Response('', { status: 503, statusText: 'Article not cached' })));
    return;
  }

  if (isApi(url)) {
    event.respondWith(networkFirst(request, RUNTIME_CACHE));
    return;
  }

  if (isImage(request, url)) {
    event.respondWith(cacheFirst(request, IMAGE_CACHE));
  }
});
