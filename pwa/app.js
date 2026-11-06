const LANGS = [
  ['en', 'English'], ['simple', 'Simple English'], ['de', 'Deutsch'], ['es', 'Español'],
  ['fr', 'Français'], ['it', 'Italiano'], ['pt', 'Português'], ['nl', 'Nederlands'],
  ['pl', 'Polski'], ['ru', 'Русский'], ['uk', 'Українська'], ['cs', 'Čeština'],
  ['sk', 'Slovenčina'], ['hu', 'Magyar'], ['ro', 'Română'], ['bg', 'Български'],
  ['el', 'Ελληνικά'], ['sv', 'Svenska'], ['no', 'Norsk'], ['da', 'Dansk'],
  ['fi', 'Suomi'], ['is', 'Íslenska'], ['et', 'Eesti'], ['lv', 'Latviešu'],
  ['lt', 'Lietuvių'], ['hr', 'Hrvatski'], ['sr', 'Српски'], ['sl', 'Slovenščina'],
  ['bs', 'Bosanski'], ['mk', 'Македонски'], ['sq', 'Shqip'], ['ca', 'Català'],
  ['gl', 'Galego'], ['eu', 'Euskara'], ['cy', 'Cymraeg'], ['ga', 'Gaeilge'],
  ['tr', 'Türkçe'], ['ar', 'العربية'], ['fa', 'فارسی'], ['he', 'עברית'],
  ['hi', 'हिन्दी'], ['bn', 'বাংলা'], ['ta', 'தமிழ்'], ['te', 'తెలుగు'],
  ['ml', 'മലയാളം'], ['kn', 'ಕನ್ನಡ'], ['gu', 'ગુજરાતી'], ['mr', 'मराठी'],
  ['ur', 'اردو'], ['ne', 'नेपाली'], ['si', 'සිංහල'], ['th', 'ไทย'],
  ['vi', 'Tiếng Việt'], ['id', 'Bahasa Indonesia'], ['ms', 'Bahasa Melayu'],
  ['tl', 'Tagalog'], ['zh', '中文'], ['ja', '日本語'], ['ko', '한국어'],
  ['kk', 'Қазақша'], ['uz', 'Oʻzbekcha'], ['az', 'Azərbaycanca'], ['ka', 'ქართული'],
  ['hy', 'Հայերեն'], ['sw', 'Kiswahili'], ['af', 'Afrikaans'], ['am', 'አማርኛ'],
  ['ha', 'Hausa'], ['yo', 'Yorùbá'], ['ig', 'Igbo'], ['zu', 'isiZulu'],
  ['km', 'ខ្មែរ'], ['lo', 'ລາວ'], ['my', 'မြန်မာ']
];

const STORAGE = {
  lang: 'wr.lang', theme: 'wr.theme', fontSize: 'wr.fontSize', saved: 'wr.saved',
  history: 'wr.history', stats: 'wr.stats', searches: 'wr.searches',
  installDismissed: 'wr.installDismissed'
};
const SHELL_CACHE_PREFIX = 'wikireader-shell-';
const ARTICLE_CACHE_PREFIX = 'wikireader-articles-';
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
const cleanLang = (lang) => (lang || '').trim().toLowerCase() || 'en';
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[char]));
const escapeAttr = escapeHtml;
const readJson = (key, fallback) => {
  try { const value = localStorage.getItem(key); return value ? JSON.parse(value) : fallback; }
  catch (_) { return fallback; }
};
const writeJson = (key, value) => {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch (_) { /* storage full */ }
};
const readText = (key, fallback) => { try { return localStorage.getItem(key) || fallback; } catch (_) { return fallback; } };
const writeText = (key, value) => { try { localStorage.setItem(key, value); } catch (_) { /* private mode */ } };
const formatNumber = (value) => new Intl.NumberFormat().format(value || 0);
const formatBytes = (bytes) => {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / (1024 ** index)).toFixed(index ? 1 : 0)} ${units[index]}`;
};
const timeAgo = (timestamp) => {
  if (!timestamp) return '';
  const seconds = Math.max(1, Math.round((Date.now() - timestamp) / 1000));
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return new Date(timestamp).toLocaleDateString();
};

function encodeWikiTitle(title) {
  const normalized = (title || '').trim().replace(/ /g, '_').replace(/_+/g, '_').replace(/^_+|_+$/g, '');
  return [...new TextEncoder().encode(normalized)].map((byte) => {
    const char = String.fromCharCode(byte);
    if (/[A-Za-z0-9\-_.~]/.test(char)) return char;
    return `%${byte.toString(16).toUpperCase().padStart(2, '0')}`;
  }).join('');
}

const fallbackCore = {
  articleUrl: (lang, title) => `https://${cleanLang(lang)}.wikipedia.org/wiki/${encodeWikiTitle(title)}`,
  mobileArticleUrl: (lang, title) => `https://${cleanLang(lang)}.m.wikipedia.org/wiki/${encodeWikiTitle(title)}`,
  summaryUrl: (lang, title) => `https://${cleanLang(lang)}.wikipedia.org/api/rest_v1/page/summary/${encodeWikiTitle(title)}`,
  randomSummaryUrl: (lang) => `https://${cleanLang(lang)}.wikipedia.org/api/rest_v1/page/random/summary`,
  searchUrl: (lang, query) => `https://${cleanLang(lang)}.wikipedia.org/w/index.php?search=${encodeURIComponent(query.trim())}`,
  filePathUrl: (lang, fileName, width) => `https://${cleanLang(lang)}.wikipedia.org/wiki/Special:FilePath/${encodeWikiTitle(String(fileName).replace(/^File:/i, ''))}${width > 0 ? `?width=${width}` : ''}`,
  normalizeTitle: (title) => (title || '').trim().replace(/ /g, '_').replace(/_+/g, '_').replace(/^_+|_+$/g, ''),
  encodeTitle: encodeWikiTitle,
  plainText: (html) => {
    if (!html) return '';
    const doc = new DOMParser().parseFromString(String(html), 'text/html');
    return (doc.body?.textContent || '').replace(/\s+/g, ' ').trim();
  },
  snippet: (html, maxChars = 280) => {
    const text = fallbackCore.plainText(html);
    if (text.length <= maxChars) return text;
    const cut = text.slice(0, maxChars);
    const sentenceEnd = Math.max(cut.lastIndexOf('.'), cut.lastIndexOf('!'), cut.lastIndexOf('?'));
    return `${cut.slice(0, sentenceEnd >= maxChars / 2 ? sentenceEnd + 1 : maxChars).trimEnd(' ', ',', ';', ':')}…`;
  },
  readingMinutes: (text) => {
    const words = fallbackCore.plainText(text).split(' ').filter((word) => /[\p{L}\p{N}]/u.test(word)).length;
    return words ? Math.ceil(words / 200) : 0;
  }
};

let core = fallbackCore;
let deferredInstallPrompt = null;
let toastTimer = null;
let scrollScheduled = false;

const state = {
  lang: cleanLang(readText(STORAGE.lang, 'en')),
  theme: readText(STORAGE.theme, 'system'),
  fontSize: clamp(Number(readText(STORAGE.fontSize, '18')) || 18, 15, 26),
  tab: 'home',
  home: null,
  homeLoading: false,
  homeError: '',
  searchQuery: '',
  searchResults: null,
  searchLoading: false,
  searchError: '',
  recentSearches: readJson(STORAGE.searches, []),
  article: null,
  articleToken: 0,
  saved: readJson(STORAGE.saved, []),
  history: readJson(STORAGE.history, []),
  stats: readJson(STORAGE.stats, { articlesRead: 0, wordsRead: 0, minutesRead: 0, since: Date.now() }),
  lastReadKey: '',
  lastReadAt: 0,
  installDismissed: readText(STORAGE.installDismissed, '0') === '1'
};

function imageFor(summary) {
  const source = summary?.thumbnail?.source || summary?.originalimage?.source || summary?.image?.source || '';
  if (!source) return '';
  if (source.startsWith('//')) return `https:${source}`;
  return source;
}

function articleHref(title, lang = state.lang) {
  return `#/wiki/${encodeURIComponent(cleanLang(lang))}/${encodeURIComponent(core.normalizeTitle(title))}`;
}

function summaryFromFeed(item, lang = state.lang) {
  if (!item) return null;
  const title = item.normalizedtitle || (item.title || '').replace(/_/g, ' ');
  return {
    title,
    description: item.description || '',
    extract: core.plainText(item.extract_html || item.extract || ''),
    thumbnail: item.thumbnail || null,
    originalimage: item.originalimage || null,
    pageid: item.pageid,
    lang
  };
}

function summaryFromRest(item, lang = state.lang) {
  return {
    title: item.normalizedtitle || item.title || '',
    description: item.description || '',
    extract: core.plainText(item.extract || item.extract_html || ''),
    thumbnail: item.thumbnail || null,
    originalimage: item.originalimage || null,
    pageid: item.pageid,
    lang
  };
}

function currentArticleKey() {
  return state.article ? `${state.article.lang}:${core.normalizeTitle(state.article.title)}` : '';
}

function isSaved(summary) {
  if (!summary) return false;
  const key = `${cleanLang(summary.lang || state.lang)}:${core.normalizeTitle(summary.title)}`;
  return state.saved.some((item) => `${cleanLang(item.lang)}:${core.normalizeTitle(item.title)}` === key);
}

function applyTheme() {
  const dark = state.theme === 'dark' ||
    (state.theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.documentElement.dataset.themePreference = state.theme;
  const meta = $('meta[name="theme-color"]');
  if (meta) meta.content = dark ? '#0b1220' : '#0f766e';
  const select = $('#themeSelect');
  if (select) select.value = state.theme;
}

function applyFontSize() {
  document.documentElement.style.setProperty('--article-font-size', `${state.fontSize}px`);
  const label = $('#fontSizeLabel');
  if (label) label.textContent = `${state.fontSize} px`;
  const input = $('#fontSizeInput');
  if (input) input.value = String(state.fontSize);
}

function setBodyBusy(busy) {
  document.body.classList.toggle('busy', busy);
}

function showToast(message, timeout = 3200) {
  const toast = $('#toast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), timeout);
}

function updateOnlineBadge() {
  const badge = $('#offlineBadge');
  if (badge) badge.hidden = navigator.onLine;
}

async function fetchJson(url, timeout = 15000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const response = await fetch(url, { signal: controller.signal, headers: { Accept: 'application/json' } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

async function fetchText(url, timeout = 20000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const response = await fetch(url, { signal: controller.signal, headers: { Accept: 'text/html' } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.text();
  } finally {
    clearTimeout(timer);
  }
}

function apiOrigin() {
  return `https://${cleanLang(state.lang)}.wikipedia.org`;
}

function featuredUrl() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${apiOrigin()}/api/rest_v1/feed/featured/${now.getFullYear()}/${month}/${day}`;
}

async function loadCore() {
  const status = $('#wasmStatus');
  try {
    const module = await import('./wasm/WikiRandomizer-P-shared.mjs');
    if (typeof module.plainText !== 'function' || typeof module.randomSummaryUrl !== 'function') {
      throw new Error('WASM exports are incomplete');
    }
    core = module;
    if (status) status.textContent = 'WASM core loaded (Kotlin/WebAssembly)';
    document.documentElement.dataset.core = 'wasm';
  } catch (error) {
    core = fallbackCore;
    if (status) status.textContent = 'JavaScript fallback core (WASM unavailable)';
    document.documentElement.dataset.core = 'js';
    console.warn('[pwa] WASM core unavailable, using JavaScript fallback', error);
  }
}

function articleCard(summary) {
  if (!summary?.title) return '';
  const image = imageFor(summary);
  return `<a class="article-card" href="${articleHref(summary.title, summary.lang)}">
    ${image ? `<img src="${escapeAttr(image)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">` : '<span class="placeholder"></span>'}
    <span class="card-body"><h3>${escapeHtml(summary.title)}</h3><p>${escapeHtml(summary.description || summary.extract || '')}</p></span>
  </a>`;
}

function storyRow(summary) {
  if (!summary?.title) return '';
  const image = imageFor(summary);
  return `<a class="story-row" href="${articleHref(summary.title, summary.lang)}">
    ${image ? `<img src="${escapeAttr(image)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">` : '<span class="placeholder"></span>'}
    <span><h3>${escapeHtml(summary.title)}</h3><p>${escapeHtml(summary.description || summary.extract || '')}</p></span>
  </a>`;
}

function savedRow(summary, index) {
  if (!summary?.title) return '';
  const image = imageFor(summary);
  return `<div class="saved-item">
    <a class="story-row" href="${articleHref(summary.title, summary.lang)}">
      ${image ? `<img src="${escapeAttr(image)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">` : '<span class="placeholder"></span>'}
      <span><h3>${escapeHtml(summary.title)}</h3><p>${escapeHtml(summary.description || summary.extract || '')}</p></span>
    </a>
    <button class="icon-button saved-remove" data-action="remove-saved" data-index="${index}" aria-label="Remove ${escapeAttr(summary.title)}">✕</button>
  </div>`;
}

function sectionHeading(title, detail = '') {
  return `<div class="section-heading"><h2>${escapeHtml(title)}</h2>${detail ? `<span>${escapeHtml(detail)}</span>` : ''}</div>`;
}

function emptyState(title, detail, action = '') {
  return `<div class="empty-state"><span class="brand-mark" aria-hidden="true">W</span><strong>${escapeHtml(title)}</strong><p>${escapeHtml(detail)}</p>${action}</div>`;
}

function renderHome() {
  const container = $('#homeContent');
  if (!container) return;
  if (state.homeLoading) {
    container.innerHTML = `<div class="home-hero skeleton" style="min-height:250px"></div>
      <div class="card-grid">${Array.from({ length: 6 }, () => '<div class="skeleton"></div>').join('')}</div>`;
    return;
  }
  if (state.homeError) {
    container.innerHTML = emptyState('Could not load the feed', state.homeError, '<button class="pill-button" data-action="refresh-home">Try again</button>');
    return;
  }
  const home = state.home;
  if (!home) {
    container.innerHTML = emptyState('Ready when you are', 'Tap refresh to load Wikipedia’s featured article and picture of the day.');
    return;
  }

  let html = '';
  if (home.feed?.tfa) {
    const tfa = summaryFromFeed(home.feed.tfa);
    const image = imageFor(tfa);
    html += `<a class="home-hero" href="${articleHref(tfa.title)}">
      <span class="home-hero-copy"><span class="eyebrow" style="color:#a7f3d0">Featured article</span>
        <h2>${escapeHtml(tfa.title)}</h2><p>${escapeHtml(tfa.extract)}</p>
        <span class="pill-button">Read article</span></span>
      ${image ? `<img src="${escapeAttr(image)}" alt="" referrerpolicy="no-referrer">` : ''}
    </a>`;
  } else if (home.randoms?.length) {
    html += articleCard(home.randoms[0]);
  }

  const continueEntry = state.history[0];
  if (continueEntry) {
    html += sectionHeading('Continue reading', timeAgo(continueEntry.viewedAt));
    html += `<div class="story-list">${storyRow(continueEntry)}</div>`;
  }

  if (home.feed?.image) {
    const potd = home.feed.image;
    const image = potd.thumbnail?.source || potd.image?.source || '';
    const description = core.plainText(potd.description?.html || potd.description || '');
    html += sectionHeading('Picture of the day', potd.title?.replace(/^File:/, '').replace(/_/g, ' '));
    html += `<div class="potd">${image ? `<img src="${escapeAttr(image.startsWith('//') ? `https:${image}` : image)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : ''}
      <div class="potd-copy"><h3>${escapeHtml((potd.title || '').replace(/^File:/, '').replace(/_/g, ' '))}</h3><p>${escapeHtml(core.snippet(description, 260))}</p></div></div>`;
  }

  const mostRead = home.feed?.mostread?.articles?.slice(0, 6) || [];
  if (mostRead.length) {
    html += sectionHeading('Trending articles');
    html += `<div class="story-list">${mostRead.map((item) => storyRow(summaryFromFeed(item))).join('')}</div>`;
  }

  const onThisDay = home.feed?.onthisday?.slice(0, 4) || [];
  if (onThisDay.length) {
    html += sectionHeading('On this day', new Date().toLocaleDateString(undefined, { month: 'long', day: 'numeric' }));
    html += `<div class="story-list">${onThisDay.map((event) => `<div class="result-row"><h3>${escapeHtml(event.year)}</h3><p>${escapeHtml(core.snippet(event.text, 240))}</p></div>`).join('')}</div>`;
  }

  if (home.randoms?.length) {
    html += sectionHeading('More to explore');
    html += `<div class="card-grid">${home.randoms.map(articleCard).join('')}</div>`;
  }

  if (!html) {
    html = emptyState('No stories right now', 'The feed was empty. Try refreshing.', '<button class="pill-button" data-action="refresh-home">Refresh</button>');
  }
  container.innerHTML = html;
}

async function loadHome(force = false) {
  if (state.homeLoading) return;
  if (state.home && !force) { renderHome(); return; }
  state.homeLoading = true;
  state.homeError = '';
  renderHome();
  try {
    const feed = await fetchJson(featuredUrl());
    state.home = { feed, loadedAt: Date.now() };
  } catch (error) {
    console.warn('[pwa] featured feed failed, trying random articles', error);
    try {
      const results = await Promise.allSettled(Array.from({ length: 6 }, () => fetchJson(core.randomSummaryUrl(state.lang))));
      const randoms = results.filter((result) => result.status === 'fulfilled').map((result) => summaryFromRest(result.value));
      if (!randoms.length) throw error;
      state.home = { randoms, loadedAt: Date.now() };
    } catch (randomError) {
      state.homeError = navigator.onLine ? 'Wikipedia did not answer. Check your connection and try again.' : 'You are offline. Reopen an article you have already visited, or reconnect.';
      console.error(randomError);
    }
  } finally {
    state.homeLoading = false;
    renderHome();
  }
}

function renderSearch() {
  const container = $('#searchContent');
  if (!container) return;
  if (state.searchLoading) {
    container.innerHTML = Array.from({ length: 5 }, () => '<div class="skeleton" style="min-height:72px"></div>').join('');
    return;
  }
  if (state.searchError) {
    container.innerHTML = emptyState('Search failed', state.searchError, '<button class="pill-button" data-action="retry-search">Try again</button>');
    return;
  }
  if (state.searchResults) {
    if (!state.searchResults.length) {
      container.innerHTML = emptyState('No results', `Nothing found for “${state.searchQuery}”. Try another spelling.`);
      return;
    }
    container.innerHTML = state.searchResults.map((item) => `<a class="result-row" href="${articleHref(item.title)}">
      <h3>${escapeHtml(item.title)}</h3>${item.description ? `<p>${escapeHtml(item.description)}</p>` : ''}
    </a>`).join('');
    return;
  }

  let html = '';
  if (state.recentSearches.length) {
    html += sectionHeading('Recent searches');
    html += `<div class="chip-row">${state.recentSearches.map((query) => `<button class="pill-button" data-action="recent-search" data-query="${escapeAttr(query)}">${escapeHtml(query)}</button>`).join('')}</div>`;
  }
  html += emptyState('Search the world’s knowledge', 'Results are cached so you can reopen them offline.');
  container.innerHTML = html;
}

async function doSearch(query) {
  const trimmed = (query || '').trim();
  if (!trimmed) return;
  state.searchQuery = trimmed;
  state.searchLoading = true;
  state.searchResults = null;
  state.searchError = '';
  renderSearch();
  try {
    const url = `${apiOrigin()}/w/api.php?action=opensearch&format=json&origin=*&search=${encodeURIComponent(trimmed)}&limit=12&namespace=0`;
    const data = await fetchJson(url);
    state.searchResults = (data[1] || []).map((title, index) => ({
      title,
      description: data[2]?.[index] || '',
      url: data[3]?.[index] || ''
    }));
    state.recentSearches = [trimmed, ...state.recentSearches.filter((item) => item !== trimmed)].slice(0, 10);
    writeJson(STORAGE.searches, state.recentSearches);
  } catch (error) {
    state.searchError = navigator.onLine ? 'Wikipedia search did not answer.' : 'You are offline. Only cached pages can be opened.';
    console.error(error);
  } finally {
    state.searchLoading = false;
    renderSearch();
  }
}

function renderSaved() {
  const container = $('#savedContent');
  const clearButton = $('#clearSavedButton');
  if (!container) return;
  if (clearButton) clearButton.hidden = state.saved.length === 0;
  if (!state.saved.length) {
    container.innerHTML = emptyState('Nothing saved yet', 'Open an article and tap the bookmark to keep it here for later.');
    return;
  }
  container.innerHTML = `<div class="list-stack">${state.saved.map(savedRow).join('')}</div>`;
}

function renderHistory() {
  const container = $('#historyContent');
  const clearButton = $('#clearHistoryButton');
  if (!container) return;
  if (clearButton) clearButton.hidden = state.history.length === 0;
  if (!state.history.length) {
    container.innerHTML = emptyState('No reading history', 'Articles you open appear here automatically.');
    return;
  }
  container.innerHTML = `<div class="list-stack">${state.history.map((item, index) => `<div class="saved-item">
    ${storyRow(item)}
    <button class="icon-button saved-remove" data-action="remove-history" data-index="${index}" aria-label="Remove ${escapeAttr(item.title)}">✕</button>
  </div>`).join('')}</div>`;
}

function populateLanguages() {
  const select = $('#languageSelect');
  if (select) {
    select.innerHTML = LANGS.map(([code, name]) => `<option value="${escapeAttr(code)}">${escapeHtml(name)} (${escapeHtml(code)})</option>`).join('');
    select.value = state.lang;
  }
  const chip = $('#currentLang');
  if (chip) chip.textContent = state.lang.toUpperCase();
}

function renderLanguageList(filter = '') {
  const list = $('#languageList');
  if (!list) return;
  const needle = filter.trim().toLowerCase();
  const items = LANGS.filter(([code, name]) => !needle || name.toLowerCase().includes(needle) || code.includes(needle));
  list.innerHTML = items.map(([code, name]) => `<button type="button" class="language-option ${code === state.lang ? 'active' : ''}" data-action="set-language" data-lang="${escapeAttr(code)}">${escapeHtml(name)}<small>${escapeHtml(code)}.wikipedia.org</small></button>`).join('') || '<p class="setting-hint">No matching language.</p>';
}

function renderStats() {
  const container = $('#statsContent');
  if (!container) return;
  container.innerHTML = `
    <div class="stat-box"><strong>${formatNumber(state.stats.articlesRead)}</strong><span>Articles read</span></div>
    <div class="stat-box"><strong>${formatNumber(state.stats.minutesRead)}</strong><span>Minutes reading</span></div>
    <div class="stat-box"><strong>${formatNumber(state.stats.wordsRead)}</strong><span>Words read</span></div>
    <div class="stat-box"><strong>${formatNumber(state.saved.length)}</strong><span>Saved articles</span></div>`;
}

async function updateCacheStats() {
  const container = $('#cacheStats');
  if (!container) return;
  let articles = 0;
  let storage = '';
  try {
    const names = await caches.keys();
    const articleCaches = names.filter((name) => name.startsWith(ARTICLE_CACHE_PREFIX));
    for (const name of articleCaches) {
      const cache = await caches.open(name);
      articles += (await cache.keys()).length;
    }
    if (navigator.storage?.estimate) {
      const estimate = await navigator.storage.estimate();
      storage = `${formatBytes(estimate.usage || 0)} used`;
    }
  } catch (error) {
    console.warn('[pwa] could not read cache stats', error);
  }
  container.innerHTML = `
    <div class="stat-box"><strong>${formatNumber(articles)}</strong><span>Cached articles</span></div>
    <div class="stat-box"><strong>${escapeHtml(storage || '—')}</strong><span>Storage used</span></div>`;
}

function renderSettings() {
  const themeSelect = $('#themeSelect');
  if (themeSelect) themeSelect.value = state.theme;
  const languageSelect = $('#languageSelect');
  if (languageSelect) languageSelect.value = state.lang;
  applyFontSize();
  renderStats();
  updateCacheStats();
  const installHint = $('#installHint');
  const installButton = $('#installButton');
  const standalone = matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  if (installHint) {
    installHint.textContent = standalone
      ? 'WikiReader is installed and running as a standalone app.'
      : isIos()
        ? 'On iPhone or iPad: tap Share → Add to Home Screen to install the offline reader.'
        : 'Install WikiReader on your home screen for a full-screen, offline-capable app.';
  }
  if (installButton) installButton.textContent = standalone ? 'Installed' : 'Install app';
}

function updateSaveButton() {
  const button = $('#saveArticleButton');
  if (!button) return;
  button.classList.toggle('active', isSaved(state.article?.summary));
}

function sanitiseArticleHtml(html, lang, articleTitle) {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const body = doc.body;
  if (!body) return { html: '', toc: '' };
  const origin = `https://${cleanLang(lang)}.wikipedia.org`;
  const base = `${origin}/wiki/`;

  body.querySelectorAll('script, style, link, meta, base, iframe, object, embed, form, input, button, textarea, select, audio, video, source, track, canvas, svg, .mw-editsection, .navbox, .vertical-navbox, .navbox-styles, .metadata, .ambox, .tmbox, .hatnote, .shortdescription, .mw-empty-elt, .noprint, .mw-hidden-catlinks, .mw-indicators, .printfooter, .catlinks, .sidebar').forEach((node) => node.remove());

  body.querySelectorAll('*').forEach((element) => {
    for (const attribute of [...element.attributes]) {
      const name = attribute.name.toLowerCase();
      if (name.startsWith('on') || name === 'srcdoc') element.removeAttribute(attribute.name);
      if ((name === 'href' || name === 'src' || name === 'xlink:href') && /^\s*javascript:/i.test(attribute.value)) {
        element.removeAttribute(attribute.name);
      }
    }
    if (element.tagName === 'A') {
      let href = element.getAttribute('href') || '';
      if (!href) return;
      if (href.startsWith('#')) {
        element.setAttribute('data-anchor', href);
        return;
      }
      if (href.startsWith('//')) href = `https:${href}`;
      else if (href.startsWith('./')) href = base + href.slice(2);
      else if (href.startsWith('/wiki/')) href = origin + href;
      else if (href.startsWith('/')) href = origin + href;
      else if (!/^https?:\/\//i.test(href)) href = base + href;
      element.setAttribute('href', href);
      if (!href.includes('.wikipedia.org/wiki/')) {
        element.setAttribute('target', '_blank');
        element.setAttribute('rel', 'noopener noreferrer');
      }
    }
    if (element.tagName === 'IMG') {
      let src = element.getAttribute('src') || '';
      if (src.startsWith('//')) src = `https:${src}`;
      if (!src) element.remove();
      else {
        element.setAttribute('src', src);
        element.removeAttribute('srcset');
        element.removeAttribute('sizes');
        element.setAttribute('loading', 'lazy');
        element.setAttribute('decoding', 'async');
        element.setAttribute('referrerpolicy', 'no-referrer');
      }
    }
  });

  const headings = [...body.querySelectorAll('h2, h3')].filter((heading) => heading.textContent.trim());
  headings.forEach((heading, index) => {
    if (!heading.id) heading.id = `section-${index + 1}`;
  });
  const toc = headings.length >= 2
    ? `<details class="toc"><summary>Contents</summary><ol>${headings.slice(0, 16).map((heading) => `<li class="toc-${heading.tagName.toLowerCase()}"><a href="#${escapeAttr(heading.id)}" data-anchor="#${escapeAttr(heading.id)}">${escapeHtml(heading.textContent.trim())}</a></li>`).join('')}</ol></details>`
    : '';
  if (doc.querySelector('base')) doc.querySelector('base').remove();
  return { html: body.innerHTML, toc };
}

async function fetchArticleHtml(lang, title) {
  const url = `https://${cleanLang(lang)}.wikipedia.org/api/rest_v1/page/html/${encodeWikiTitle(title)}`;
  try {
    const text = await fetchText(url);
    return sanitiseArticleHtml(text, lang, title);
  } catch (error) {
    try {
      const cached = await caches.match(url, { ignoreVary: true });
      if (cached) return sanitiseArticleHtml(await cached.text(), lang, title);
    } catch (cacheError) {
      console.warn('[pwa] article cache lookup failed', cacheError);
    }
    console.warn('[pwa] full article unavailable', error);
    return null;
  }
}

function recordHistory(summary) {
  if (!summary?.title) return;
  const entry = {
    title: summary.title,
    description: summary.description || '',
    extract: core.snippet(summary.extract || '', 280),
    thumbnail: summary.thumbnail || null,
    pageid: summary.pageid,
    lang: cleanLang(summary.lang || state.lang),
    viewedAt: Date.now()
  };
  const key = `${entry.lang}:${core.normalizeTitle(entry.title)}`;
  state.history = [entry, ...state.history.filter((item) => `${cleanLang(item.lang)}:${core.normalizeTitle(item.title)}` !== key)].slice(0, 100);
  writeJson(STORAGE.history, state.history);
}

function recordStats(summary, html) {
  const key = `${state.lang}:${core.normalizeTitle(summary?.title || '')}`;
  if (key === state.lastReadKey && Date.now() - state.lastReadAt < 60000) return;
  state.lastReadKey = key;
  state.lastReadAt = Date.now();
  const text = html ? core.plainText(html) : (summary?.extract || '');
  const words = text.split(/\s+/).filter(Boolean).length;
  state.stats.articlesRead = (state.stats.articlesRead || 0) + 1;
  state.stats.wordsRead = (state.stats.wordsRead || 0) + words;
  state.stats.minutesRead = (state.stats.minutesRead || 0) + Math.max(1, Math.round(words / 200));
  writeJson(STORAGE.stats, state.stats);
}

function renderArticle() {
  const container = $('#articleContent');
  const toolbarTitle = $('#articleToolbarTitle');
  if (!container) return;
  const article = state.article;
  if (!article) return;
  if (toolbarTitle) toolbarTitle.textContent = article.title || '';
  updateSaveButton();

  if (article.error) {
    container.innerHTML = emptyState('Article unavailable', article.error, '<button class="pill-button" data-action="retry-article">Try again</button>');
    return;
  }
  if (!article.summary) {
    container.innerHTML = '<div class="article-status"><div class="skeleton" style="height:44px;max-width:70%;margin:0 auto 18px"></div><div class="skeleton" style="height:180px"></div></div>';
    return;
  }

  const summary = article.summary;
  const image = imageFor(summary);
  const minutes = core.readingMinutes(article.html ? core.plainText(article.html) : summary.extract);
  let html = `<header class="article-header">
    <h1>${escapeHtml(summary.title)}</h1>
    ${summary.description ? `<p class="article-description">${escapeHtml(summary.description)}</p>` : ''}
    <div class="article-meta"><span class="pill">${escapeHtml(article.lang.toUpperCase())} Wikipedia</span>${minutes ? `<span>${minutes} min read</span>` : ''}<span>${state.saved.some((item) => `${cleanLang(item.lang)}:${core.normalizeTitle(item.title)}` === `${article.lang}:${core.normalizeTitle(summary.title)}`) ? 'Saved' : 'Not saved'}</span></div>
  </header>`;
  if (image) html += `<img class="article-hero" src="${escapeAttr(image)}" alt="" referrerpolicy="no-referrer">`;
  if (article.html) {
    html += article.toc || '';
    html += `<div class="mw-parser-output">${article.html}</div>`;
  } else {
    html += `<p class="article-summary">${escapeHtml(summary.extract || '')}</p>`;
    html += `<div class="result-row" style="margin-top:20px"><h3>Full article is not cached</h3><p>Reconnect to download the complete article. The summary above is still readable offline.</p></div>`;
  }
  container.innerHTML = html;
  document.title = `${summary.title} · WikiReader`;
}

async function loadArticle(lang, title) {
  const token = ++state.articleToken;
  state.article = { lang: cleanLang(lang), title: title.replace(/_/g, ' '), summary: null, html: '', toc: '', error: '' };
  renderArticle();
  setBodyBusy(true);
  try {
    const summaryUrl = core.summaryUrl(lang, title);
    const htmlPromise = fetchArticleHtml(lang, title);
    let summary = null;
    try {
      const data = await fetchJson(summaryUrl);
      summary = summaryFromRest(data, lang);
    } catch (error) {
      summary = state.saved.find((item) => cleanLang(item.lang) === cleanLang(lang) && core.normalizeTitle(item.title) === core.normalizeTitle(title)) ||
        state.history.find((item) => cleanLang(item.lang) === cleanLang(lang) && core.normalizeTitle(item.title) === core.normalizeTitle(title)) || null;
      if (!summary) throw error;
      summary = { ...summary, extract: summary.extract || '' };
    }
    if (token !== state.articleToken) return;
    state.article.summary = summary;
    state.article.title = summary.title || state.article.title;
    recordHistory(summary);
    renderArticle();
    const full = await htmlPromise;
    if (token !== state.articleToken) return;
    if (full) {
      state.article.html = full.html;
      state.article.toc = full.toc;
      recordStats(summary, full.html);
    } else {
      recordStats(summary, summary.extract);
    }
    renderArticle();
    setBodyBusy(false);
  } catch (error) {
    if (token !== state.articleToken) return;
    state.article.error = navigator.onLine
      ? 'Wikipedia did not answer. Try again in a moment.'
      : 'You are offline and this article has not been cached yet.';
    renderArticle();
    setBodyBusy(false);
    console.error(error);
  }
}

function showTab(tab) {
  const allowed = ['home', 'search', 'saved', 'history', 'settings'];
  state.tab = allowed.includes(tab) ? tab : 'home';
  state.articleToken++;
  state.article = null;
  $$('.view').forEach((view) => { view.hidden = view.dataset.view !== state.tab; });
  $$('[data-action="tab"]').forEach((button) => button.classList.toggle('active', button.dataset.tab === state.tab));
  const titles = { home: 'WikiReader', search: 'Search · WikiReader', saved: 'Saved · WikiReader', history: 'History · WikiReader', settings: 'Settings · WikiReader' };
  document.title = titles[state.tab] || 'WikiReader';
  window.scrollTo({ top: 0, behavior: 'instant' });
  if (state.tab === 'home') { renderHome(); loadHome(); }
  if (state.tab === 'search') { renderSearch(); }
  if (state.tab === 'saved') renderSaved();
  if (state.tab === 'history') renderHistory();
  if (state.tab === 'settings') renderSettings();
}

function parseHash() {
  const raw = decodeURI(location.hash.replace(/^#\/?/, ''));
  if (!raw) return { type: 'tab', tab: 'home' };
  const parts = raw.split('/');
  if (parts[0] === 'wiki' && parts.length >= 3) {
    return { type: 'article', lang: cleanLang(parts[1]), title: parts.slice(2).join('/').replace(/_/g, ' ') };
  }
  if (['home', 'search', 'saved', 'history', 'settings'].includes(parts[0])) return { type: 'tab', tab: parts[0] };
  return { type: 'tab', tab: 'home' };
}

function route() {
  const destination = parseHash();
  if (destination.type === 'article') {
    state.tab = 'article';
    state.articleToken++;
    $$('.view').forEach((view) => { view.hidden = view.dataset.view !== 'article'; });
    $$('[data-action="tab"]').forEach((button) => button.classList.remove('active'));
    loadArticle(destination.lang, destination.title);
  } else {
    showTab(destination.tab);
  }
}

function closeDialog() {
  const dialog = $('#languageDialog');
  if (dialog?.open) dialog.close();
}

function toggleTheme() {
  const dark = document.documentElement.dataset.theme === 'dark';
  state.theme = dark ? 'light' : 'dark';
  writeText(STORAGE.theme, state.theme);
  applyTheme();
  renderSettings();
  showToast(`${state.theme === 'dark' ? 'Dark' : 'Light'} theme`);
}

function setLanguage(lang) {
  state.lang = cleanLang(lang);
  writeText(STORAGE.lang, state.lang);
  populateLanguages();
  closeDialog();
  state.home = null;
  state.homeError = '';
  state.searchResults = null;
  if (state.tab === 'home') loadHome(true);
  else if (state.tab === 'settings') renderSettings();
  if (state.tab === 'article') {
    const articleTitle = state.article?.title;
    if (articleTitle) location.hash = articleHref(articleTitle, state.lang);
    else route();
  }
  showToast(`Wikipedia language: ${LANGS.find(([code]) => code === state.lang)?.[1] || state.lang}`);
}

function toggleSave() {
  const summary = state.article?.summary;
  if (!summary) { showToast('Article is still loading'); return; }
  if (isSaved(summary)) {
    state.saved = state.saved.filter((item) => !(cleanLang(item.lang) === cleanLang(summary.lang) && core.normalizeTitle(item.title) === core.normalizeTitle(summary.title)));
    showToast('Removed from saved');
  } else {
    state.saved = [{
      title: summary.title,
      description: summary.description || '',
      extract: core.snippet(summary.extract || '', 280),
      thumbnail: summary.thumbnail || null,
      lang: cleanLang(summary.lang || state.lang),
      pageid: summary.pageid,
      savedAt: Date.now()
    }, ...state.saved].slice(0, 200);
    showToast('Saved for offline reading');
  }
  writeJson(STORAGE.saved, state.saved);
  updateSaveButton();
  renderArticle();
  renderSaved();
}

async function shareArticle() {
  const summary = state.article?.summary;
  if (!summary) return;
  const url = core.articleUrl(state.article.lang, summary.title);
  try {
    if (navigator.share) {
      await navigator.share({ title: summary.title, text: summary.description || summary.extract || '', url });
    } else if (navigator.clipboard) {
      await navigator.clipboard.writeText(url);
      showToast('Link copied');
    } else {
      window.prompt('Copy this link', url);
    }
  } catch (error) {
    if (error.name !== 'AbortError') showToast('Could not share the link');
  }
}

function changeFont(delta) {
  state.fontSize = clamp(state.fontSize + delta, 15, 26);
  writeText(STORAGE.fontSize, String(state.fontSize));
  applyFontSize();
}

function isIos() {
  return /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

async function promptInstall() {
  if (deferredInstallPrompt) {
    deferredInstallPrompt.prompt();
    const choice = await deferredInstallPrompt.userChoice;
    if (choice.outcome === 'accepted') showToast('Installing WikiReader…');
    deferredInstallPrompt = null;
    $('#installBanner')?.setAttribute('hidden', '');
    return;
  }
  if (matchMedia('(display-mode: standalone)').matches || navigator.standalone === true) {
    showToast('WikiReader is already installed');
    return;
  }
  if (isIos()) {
    showToast('Tap Share, then “Add to Home Screen” to install.', 6000);
  } else {
    showToast('Open your browser menu and choose “Install app” or “Add to Home screen”.', 6000);
  }
}

async function clearCaches() {
  if (!confirm('Clear cached articles and the offline app shell? You can re-download them later.')) return;
  try {
    if (navigator.serviceWorker?.controller) navigator.serviceWorker.controller.postMessage({ type: 'CLEAR_CACHES' });
    for (const name of await caches.keys()) await caches.delete(name);
    updateCacheStats();
    showToast('Offline cache cleared');
  } catch (error) {
    console.warn(error);
    showToast('Could not clear the cache');
  }
}

function exportData() {
  const data = {};
  for (const key of Object.values(STORAGE)) {
    const value = localStorage.getItem(key);
    if (value !== null) data[key] = value;
  }
  const blob = new Blob([JSON.stringify({ app: 'WikiRandomizer-P', exportedAt: new Date().toISOString(), data }, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `wikireader-backup-${new Date().toISOString().slice(0, 10)}.json`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
  showToast('Backup exported');
}

function importData(file) {
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const parsed = JSON.parse(String(reader.result));
      const data = parsed.data || parsed;
      if (!data || typeof data !== 'object') throw new Error('Invalid backup');
      for (const key of Object.values(STORAGE)) {
        if (typeof data[key] === 'string') localStorage.setItem(key, data[key]);
      }
      showToast('Backup imported. Reloading…');
      setTimeout(() => location.reload(), 700);
    } catch (error) {
      console.warn(error);
      showToast('That file is not a WikiReader backup');
    }
  };
  reader.readAsText(file);
}

async function openRandom() {
  try {
    const data = await fetchJson(core.randomSummaryUrl(state.lang));
    const title = data?.normalizedtitle || data?.title || data?.displaytitle;
    if (!title) throw new Error('Random summary did not include a title');
    location.hash = articleHref(title, state.lang);
  } catch (error) {
    showToast('Could not load a random article');
    console.error(error);
  }
}

function onAction(action, element) {
  switch (action) {
    case 'home':
      location.hash = '#/home';
      break;
    case 'tab':
      location.hash = `#/${element.dataset.tab}`;
      break;
    case 'random': openRandom(); break;
    case 'toggle-theme': toggleTheme(); break;
    case 'open-language': renderLanguageList(); $('#languageDialog')?.showModal(); break;
    case 'set-language': setLanguage(element.dataset.lang); break;
    case 'refresh-home': state.home = null; loadHome(true); break;
    case 'retry-search': doSearch(state.searchQuery); break;
    case 'recent-search': $('#searchInput').value = element.dataset.query; doSearch(element.dataset.query); break;
    case 'clear-saved':
      if (confirm('Remove every saved article?')) { state.saved = []; writeJson(STORAGE.saved, state.saved); renderSaved(); showToast('Saved articles cleared'); }
      break;
    case 'clear-history':
      if (confirm('Clear your reading history?')) { state.history = []; writeJson(STORAGE.history, state.history); renderHistory(); showToast('History cleared'); }
      break;
    case 'remove-saved':
      state.saved.splice(Number(element.dataset.index), 1);
      writeJson(STORAGE.saved, state.saved);
      renderSaved();
      renderArticle();
      break;
    case 'remove-history':
      state.history.splice(Number(element.dataset.index), 1);
      writeJson(STORAGE.history, state.history);
      renderHistory();
      break;
    case 'clear-cache': clearCaches(); break;
    case 'export-data': exportData(); break;
    case 'import-data': $('#importFile')?.click(); break;
    case 'reset-stats':
      if (confirm('Reset reading statistics?')) { state.stats = { articlesRead: 0, wordsRead: 0, minutesRead: 0, since: Date.now() }; writeJson(STORAGE.stats, state.stats); renderStats(); showToast('Statistics reset'); }
      break;
    case 'install': promptInstall(); break;
    case 'dismiss-install':
      state.installDismissed = true;
      writeText(STORAGE.installDismissed, '1');
      $('#installBanner').hidden = true;
      break;
    case 'back':
      if (history.length > 1) history.back(); else location.hash = '#/home';
      break;
    case 'font-smaller': changeFont(-1); break;
    case 'font-larger': changeFont(1); break;
    case 'toggle-save': toggleSave(); break;
    case 'share': shareArticle(); break;
    case 'external': {
      const summary = state.article?.summary;
      if (summary) window.open(core.articleUrl(state.article.lang, summary.title), '_blank', 'noopener');
      break;
    }
    case 'retry-article': route(); break;
    default: break;
  }
}

function handleArticleClick(link) {
  const href = link.getAttribute('data-anchor') || link.getAttribute('href') || '';
  if (href.startsWith('#')) {
    const target = document.getElementById(href.slice(1));
    if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    return true;
  }
  if (/^https?:\/\/[^/]*\.wikipedia\.org\/wiki\//i.test(href)) {
    const url = new URL(href);
    const lang = url.hostname.split('.')[0];
    const title = decodeURIComponent(url.pathname.slice('/wiki/'.length)).replace(/_/g, ' ');
    location.hash = articleHref(title, lang);
    return true;
  }
  return false;
}

function bindEvents() {
  document.addEventListener('click', (event) => {
    const actionElement = event.target.closest('[data-action]');
    if (actionElement) {
      event.preventDefault();
      onAction(actionElement.dataset.action, actionElement);
      return;
    }
    const link = event.target.closest('.article-content a');
    if (link && handleArticleClick(link)) event.preventDefault();
  });

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const actionElement = event.target.closest?.('[data-action]');
    if (actionElement && actionElement.tagName !== 'BUTTON' && actionElement.tagName !== 'A') {
      event.preventDefault();
      onAction(actionElement.dataset.action, actionElement);
    }
  });

  $('#searchForm')?.addEventListener('submit', (event) => {
    event.preventDefault();
    doSearch($('#searchInput')?.value || '');
  });
  $('#searchInput')?.addEventListener('input', (event) => {
    if (!event.target.value) { state.searchResults = null; state.searchError = ''; renderSearch(); }
  });
  $('#themeSelect')?.addEventListener('change', (event) => {
    state.theme = event.target.value;
    writeText(STORAGE.theme, state.theme);
    applyTheme();
  });
  $('#languageSelect')?.addEventListener('change', (event) => setLanguage(event.target.value));
  $('#fontSizeInput')?.addEventListener('input', (event) => {
    state.fontSize = clamp(Number(event.target.value) || 18, 15, 26);
    writeText(STORAGE.fontSize, String(state.fontSize));
    applyFontSize();
  });
  $('#languageFilter')?.addEventListener('input', (event) => renderLanguageList(event.target.value));
  $('#importFile')?.addEventListener('change', (event) => {
    const file = event.target.files?.[0];
    if (file) importData(file);
    event.target.value = '';
  });
  $('#languageDialog')?.addEventListener('click', (event) => {
    if (event.target === event.currentTarget) closeDialog();
  });

  window.addEventListener('hashchange', route);
  window.addEventListener('online', () => { updateOnlineBadge(); showToast('Back online'); });
  window.addEventListener('offline', () => { updateOnlineBadge(); showToast('You are offline. Cached articles still work.'); });
  window.addEventListener('scroll', () => {
    if (scrollScheduled) return;
    scrollScheduled = true;
    requestAnimationFrame(() => {
      scrollScheduled = false;
      const bar = $('#readingProgress');
      if (!bar || state.tab !== 'article') return;
      const height = document.documentElement.scrollHeight - innerHeight;
      bar.style.width = `${height > 0 ? clamp((scrollY / height) * 100, 0, 100) : 0}%`;
    });
  }, { passive: true });

  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    deferredInstallPrompt = event;
    if (!state.installDismissed) $('#installBanner')?.removeAttribute('hidden');
    renderSettings();
  });
  window.addEventListener('appinstalled', () => {
    deferredInstallPrompt = null;
    $('#installBanner')?.setAttribute('hidden', '');
    showToast('WikiReader installed');
  });

  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', () => {
    if (state.theme === 'system') applyTheme();
  });
}

function ensureProgressBar() {
  if ($('#readingProgress')) return;
  const bar = document.createElement('div');
  bar.id = 'readingProgress';
  bar.className = 'reading-progress';
  document.body.appendChild(bar);
}

async function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) return;
  try {
    const registration = await navigator.serviceWorker.register('./sw.js', { scope: './' });
    registration.addEventListener('updatefound', () => {
      const worker = registration.installing;
      worker?.addEventListener('statechange', () => {
        if (worker.state === 'installed' && navigator.serviceWorker.controller) {
          showToast('A new version is ready. Reload to update.', 5000);
        }
      });
    });
    navigator.serviceWorker.addEventListener('message', (event) => {
      if (event.data?.type === 'CACHES_CLEARED') updateCacheStats();
    });
  } catch (error) {
    console.warn('[pwa] service worker registration failed', error);
  }
}

async function init() {
  applyTheme();
  applyFontSize();
  populateLanguages();
  renderLanguageList();
  updateOnlineBadge();
  ensureProgressBar();
  bindEvents();
  renderStats();
  await loadCore();
  renderSettings();

  const params = new URLSearchParams(location.search);
  if (params.get('action') === 'random') {
    if (location.hash) history.replaceState(null, '', location.pathname + location.search);
    await openRandom();
  } else {
    if (params.get('action') === 'search') {
      if (!location.hash) location.hash = '#/search';
      setTimeout(() => $('#searchInput')?.focus(), 250);
    }
    if (!location.hash) location.hash = '#/home';
    route();
  }

  updateCacheStats();
  registerServiceWorker();
}

init().catch((error) => {
  console.error('[pwa] startup failed', error);
  const main = $('#main');
  if (main) main.innerHTML = emptyState('WikiReader could not start', 'Reload the page. If this keeps happening, clear the site data and try again.');
});
