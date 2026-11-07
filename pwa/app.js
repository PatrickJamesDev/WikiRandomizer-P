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
const LANG_NAMES = new Map(LANGS);

const TOPICS = [
  { id: 'science', label: 'Science', terms: ['science', 'physics', 'chemistry', 'biology', 'scientific'] },
  { id: 'history', label: 'History', terms: ['history', 'historical', 'archaeology', '19th-century', '20th-century'] },
  { id: 'technology', label: 'Technology', terms: ['technology', 'computing', 'engineering', 'software', 'internet'] },
  { id: 'space', label: 'Space', terms: ['astronomy', 'spaceflight', 'outer space', 'planets', 'nasa'] },
  { id: 'music', label: 'Music', terms: ['music', 'albums', 'songs', 'musicians', 'bands'] },
  { id: 'film', label: 'Film & TV', terms: ['film', 'television', 'cinema', 'actors', 'series'] },
  { id: 'literature', label: 'Literature', terms: ['literature', 'novels', 'books', 'writers', 'poetry'] },
  { id: 'art', label: 'Art & design', terms: ['art', 'painting', 'sculpture', 'design', 'architecture'] },
  { id: 'sport', label: 'Sport', terms: ['sport', 'football', 'basketball', 'olympic', 'cricket'] },
  { id: 'geography', label: 'Geography', terms: ['geography', 'countries', 'cities', 'mountains', 'rivers'] },
  { id: 'nature', label: 'Nature', terms: ['nature', 'animals', 'plants', 'birds', 'ecology'] },
  { id: 'medicine', label: 'Medicine', terms: ['medicine', 'health', 'diseases', 'anatomy', 'medical'] },
  { id: 'mathematics', label: 'Mathematics', terms: ['mathematics', 'mathematical', 'geometry', 'algebra', 'numbers'] },
  { id: 'philosophy', label: 'Philosophy', terms: ['philosophy', 'ethics', 'logic', 'philosophers', 'metaphysics'] },
  { id: 'food', label: 'Food', terms: ['food', 'cuisine', 'drinks', 'cooking', 'restaurants'] },
  { id: 'games', label: 'Games', terms: ['video games', 'games', 'board games', 'game'] }
];

const STORAGE = {
  lang: 'wr.lang', theme: 'wr.theme', colorScheme: 'wr.colorScheme', blackTheme: 'wr.blackTheme',
  fontSize: 'wr.fontSize', fontStyle: 'wr.fontStyle', imageBackground: 'wr.imageBackground',
  reduceMotion: 'wr.reduceMotion', boldArticleText: 'wr.boldArticleText', menuSide: 'wr.menuSide',
  dataSaver: 'wr.dataSaver', feedEnabled: 'wr.feedEnabled', browsingHistory: 'wr.browsingHistory',
  searchHistory: 'wr.searchHistory', onlyImages: 'wr.onlyImages', expandedSections: 'wr.expandedSections',
  saveOffline: 'wr.saveOffline', hapticFeedback: 'wr.hapticFeedback', saved: 'wr.saved',
  history: 'wr.history', highlights: 'wr.highlights', tabs: 'wr.tabs', stats: 'wr.stats',
  searches: 'wr.searches', recentLangs: 'wr.recentLangs', topics: 'wr.topics',
  customTopics: 'wr.customTopics', installDismissed: 'wr.installDismissed'
};
const SHELL_CACHE_PREFIX = 'wikireader-shell-';
const ARTICLE_CACHE_PREFIX = 'wikireader-articles-';
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
const cleanLang = (lang) => (lang || '').trim().toLowerCase() || 'en';
const langName = (lang) => LANG_NAMES.get(cleanLang(lang)) || cleanLang(lang).toUpperCase();
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[char]));
const escapeAttr = escapeHtml;
const icon = (name, extra = '') => `<svg ${extra} aria-hidden="true"><use href="#i-${name}"></use></svg>`;
const readJson = (key, fallback) => {
  try { const value = localStorage.getItem(key); return value ? JSON.parse(value) : fallback; }
  catch (_) { return fallback; }
};
const writeJson = (key, value) => {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch (_) { /* storage full */ }
};
const readText = (key, fallback) => { try { return localStorage.getItem(key) ?? fallback; } catch (_) { return fallback; } };
const writeText = (key, value) => { try { localStorage.setItem(key, value); } catch (_) { /* private mode */ } };
const readBool = (key, fallback = false) => readText(key, fallback ? '1' : '0') === '1';
const writeBool = (key, value) => writeText(key, value ? '1' : '0');
const formatNumber = (value) => new Intl.NumberFormat().format(value || 0);
const compactNumber = (value) => {
  if (!Number.isFinite(Number(value))) return '';
  return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(Number(value));
};
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
  return new Date(timestamp).toLocaleDateString(undefined, { dateStyle: 'medium' });
};
const normalizeTheme = (value) => value === 'system' ? 'auto' : ['auto', 'light', 'dark'].includes(value) ? value : 'auto';
const normalizeCustomTopic = (value) => String(value ?? '').replace(/\s+/g, ' ').trim().slice(0, 80);
const normalizeCustomTopics = (value) => {
  if (!Array.isArray(value)) return [];
  const seen = new Set();
  return value.map(normalizeCustomTopic).filter((topic) => {
    const key = topic.toLowerCase();
    if (!topic || seen.has(key)) return false;
    seen.add(key);
    return true;
  }).slice(0, 30);
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
let selectionTimer = null;
let coreStatus = 'Starting core…';

const state = {
  lang: cleanLang(readText(STORAGE.lang, 'en')),
  theme: normalizeTheme(readText(STORAGE.theme, 'auto')),
  colorScheme: readText(STORAGE.colorScheme, 'green'),
  blackTheme: readBool(STORAGE.blackTheme),
  fontSize: clamp(Number(readText(STORAGE.fontSize, '16')) || 16, 10, 22),
  fontStyle: readText(STORAGE.fontStyle, 'sans'),
  imageBackground: readBool(STORAGE.imageBackground),
  reduceMotion: readBool(STORAGE.reduceMotion),
  boldArticleText: readBool(STORAGE.boldArticleText),
  menuSide: readText(STORAGE.menuSide, 'left') === 'right' ? 'right' : 'left',
  dataSaver: readBool(STORAGE.dataSaver),
  feedEnabled: readText(STORAGE.feedEnabled, '1') !== '0',
  browsingHistory: readText(STORAGE.browsingHistory, '1') !== '0',
  searchHistory: readText(STORAGE.searchHistory, '1') !== '0',
  onlyImages: readBool(STORAGE.onlyImages),
  expandedSections: readText(STORAGE.expandedSections, '1') !== '0',
  saveOffline: readBool(STORAGE.saveOffline),
  hapticFeedback: readText(STORAGE.hapticFeedback, '1') !== '0',
  tab: 'home',
  settingsCategory: 'root',
  settingsQuery: '',
  home: null,
  homeLoading: false,
  homeError: '',
  searchQuery: '',
  searchResults: null,
  searchLoading: false,
  searchError: '',
  recentSearches: readJson(STORAGE.searches, []),
  recentLangs: readJson(STORAGE.recentLangs, ['en']),
  topics: readJson(STORAGE.topics, []),
  customTopics: normalizeCustomTopics(readJson(STORAGE.customTopics, [])),
  article: null,
  articleToken: 0,
  saved: readJson(STORAGE.saved, []),
  history: readJson(STORAGE.history, []),
  highlights: readJson(STORAGE.highlights, []),
  tabs: readJson(STORAGE.tabs, []),
  savedFilter: 'all',
  stats: { articlesRead: 0, wordsRead: 0, minutesRead: 0, sessions: 0, since: Date.now(), lastReadAt: 0, ...readJson(STORAGE.stats, {}) },
  lastReadKey: '',
  lastReadAt: 0,
  installDismissed: readText(STORAGE.installDismissed, '0') === '1',
  selectionText: ''
};

function imageFor(summary) {
  const source = summary?.thumbnail?.source || summary?.originalimage?.source || summary?.image?.source || '';
  if (!source) return '';
  return source.startsWith('//') ? `https:${source}` : source;
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

function effectiveDark() {
  return state.theme === 'dark' ||
    (state.theme === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
}

function applyAppearance() {
  const root = document.documentElement;
  const dark = effectiveDark();
  root.dataset.theme = dark ? 'dark' : 'light';
  root.dataset.themePreference = state.theme;
  root.dataset.color = state.colorScheme;
  root.dataset.blackTheme = state.blackTheme ? 'true' : 'false';
  root.dataset.reduceMotion = state.reduceMotion ? 'true' : 'false';
  root.dataset.imageBackground = state.imageBackground ? 'true' : 'false';
  root.dataset.dataSaver = state.dataSaver ? 'true' : 'false';
  root.dataset.boldText = state.boldArticleText ? 'true' : 'false';
  root.dataset.menuSide = state.menuSide;
  root.dataset.fontStyle = state.fontStyle;
  const meta = $('meta[name="theme-color"]');
  if (meta) meta.content = state.blackTheme && dark ? '#000000' : dark ? '#12140e' : '#f9faef';
}

function applyFontSize() {
  document.documentElement.style.setProperty('--article-font-size', `${state.fontSize}px`);
  const label = $('#fontSizeLabel');
  if (label) label.textContent = `${state.fontSize} px`;
  const input = $('#fontSizeInput');
  if (input) input.value = String(state.fontSize);
}

function applyPreferences() {
  applyAppearance();
  applyFontSize();
}

function haptic() {
  if (state.hapticFeedback && navigator.vibrate) {
    try { navigator.vibrate(6); } catch (_) { /* unsupported */ }
  }
}

function updateOnlineBadge() {
  const badge = $('#offlineBadge');
  if (badge) badge.hidden = navigator.onLine;
}

function setCoreStatus(text) {
  coreStatus = text;
  ['#wasmStatus', '#railCoreStatus', '#drawerCoreStatus'].forEach((selector) => {
    const element = $(selector);
    if (element) element.textContent = text;
  });
}

function showToast(message, timeout = 3200) {
  const toast = $('#toast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), timeout);
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
  try {
    const module = await import('./wasm/WikiRandomizer-P-shared.mjs');
    if (typeof module.plainText !== 'function' || typeof module.randomSummaryUrl !== 'function') {
      throw new Error('WASM exports are incomplete');
    }
    core = module;
    setCoreStatus('WASM core loaded (Kotlin/WebAssembly)');
    document.documentElement.dataset.core = 'wasm';
  } catch (error) {
    core = fallbackCore;
    setCoreStatus('JavaScript fallback core (WASM unavailable)');
    document.documentElement.dataset.core = 'js';
    console.warn('[pwa] WASM core unavailable, using JavaScript fallback', error);
  }
}

function sparkline(values) {
  const points = values.filter((value) => Number.isFinite(Number(value))).map(Number);
  if (points.length < 2) return '';
  const min = Math.min(...points);
  const max = Math.max(...points);
  const span = max - min || 1;
  const coords = points.map((value, index) => {
    const x = (index / (points.length - 1)) * 92;
    const y = 24 - ((value - min) / span) * 18;
    return `${x.toFixed(1)} ${y.toFixed(1)}`;
  });
  return `<svg class="sparkline" viewBox="0 0 92 28" aria-hidden="true"><path d="M${coords.join(' L')}"/></svg>`;
}

function sectionHeading(title, detail = '') {
  return `<div class="section-heading"><div><h2>${escapeHtml(title)}</h2>${detail ? `<p>${escapeHtml(detail)}</p>` : ''}</div></div>`;
}

function emptyState(title, detail, action = '', iconName = 'book') {
  return `<div class="empty-state"><span class="empty-icon">${icon(iconName)}</span><strong>${escapeHtml(title)}</strong><p>${escapeHtml(detail)}</p>${action}</div>`;
}

function articleCard(summary) {
  if (!summary?.title) return '';
  const image = imageFor(summary);
  return `<a class="article-card" href="${articleHref(summary.title, summary.lang)}">
    ${image ? `<img src="${escapeAttr(image)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">` : '<span class="placeholder"></span>'}
    <span class="card-body"><h3>${escapeHtml(summary.title)}</h3><p>${escapeHtml(summary.description || summary.extract || '')}</p></span>
  </a>`;
}

function storyRow(summary, options = {}) {
  if (!summary?.title) return '';
  const image = imageFor(summary);
  const meta = options.meta || [cleanLang(summary.lang || state.lang).toUpperCase(), summary.description].filter(Boolean).join(' · ');
  return `<a class="story-row library-row" href="${articleHref(summary.title, summary.lang)}">
    <h3>${escapeHtml(summary.title)}</h3>
    ${summary.description ? `<p>${escapeHtml(summary.description)}</p>` : summary.extract ? `<p>${escapeHtml(core.snippet(summary.extract, 180))}</p>` : ''}
    <div class="row-meta">${escapeHtml(meta)}</div>
    ${image && options.thumbnail ? `<img class="feed-thumb" src="${escapeAttr(image)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : ''}
  </a>`;
}

function savedRow(summary, index) {
  if (!summary?.title) return '';
  return `<div class="saved-item">
    ${storyRow(summary)}
    <button class="icon-button saved-remove" data-action="remove-saved" data-index="${index}" aria-label="Delete ${escapeAttr(summary.title)}">${icon('close')}</button>
  </div>`;
}

function feedRow(item) {
  if (!item) return '';
  const summary = summaryFromFeed(item);
  if (!summary?.title) return '';
  const image = imageFor(item);
  const values = item.viewHistory?.map((entry) => entry.views).filter((value) => Number.isFinite(Number(value))) || [];
  const views = Number(item.views);
  const chart = sparkline(values);
  return `<a class="feed-row" href="${articleHref(summary.title)}">
    <span class="feed-row-copy">
      <h3>${escapeHtml(summary.title)}</h3>
      <p>${escapeHtml(summary.description || summary.extract || '')}</p>
      ${(chart || Number.isFinite(views)) ? `<span class="feed-views">${chart}${Number.isFinite(views) ? `<span>${escapeHtml(compactNumber(views))}</span>` : ''}</span>` : ''}
    </span>
    ${image ? `<img class="feed-thumb" src="${escapeAttr(image)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">` : '<span class="feed-thumb placeholder"></span>'}
  </a>`;
}

function carouselLinks(links = []) {
  return links.slice(0, 3).map((link) => {
    const title = link.titles?.normalized || link.titles?.canonical || '';
    return title ? `<a class="carousel-chip" href="${articleHref(title)}">${escapeHtml(title)}</a>` : '';
  }).join('');
}

function renderHome() {
  const container = $('#homeContent');
  if (!container) return;
  if (state.dataSaver) {
    container.innerHTML = emptyState('Data saver is on', 'The featured feed and images are paused. You can still open a random article or change this in Settings.', `<button class="pill-button primary" data-action="random">Random article</button>`, 'cloud');
    return;
  }
  if (!state.feedEnabled) {
    container.innerHTML = emptyState('The feed is off', 'Turn the feed back on in Settings, or jump straight into a random article.', `<button class="pill-button primary" data-action="random">Random article</button>`, 'sparkle');
    return;
  }
  if (state.homeLoading) {
    container.innerHTML = `<div class="home-hero skeleton" style="min-height:320px"></div>
      <div class="section-heading"><div><h2>Trending articles</h2></div></div>
      <div class="feed-list">${Array.from({ length: 5 }, () => '<div class="skeleton" style="min-height:112px;border-radius:0"></div>').join('')}</div>`;
    return;
  }
  if (state.homeError) {
    container.innerHTML = emptyState('Could not load the feed', state.homeError, '<button class="pill-button primary" data-action="refresh-home">Try again</button>', 'cloud');
    return;
  }
  const home = state.home;
  if (!home) {
    container.innerHTML = emptyState('Ready when you are', 'The daily Wikipedia feed will appear here.', '<button class="pill-button primary" data-action="refresh-home">Load the feed</button>', 'sparkle');
    return;
  }

  let html = '';
  if (home.feed?.tfa) {
    const tfa = summaryFromFeed(home.feed.tfa);
    const image = imageFor(home.feed.tfa);
    const today = new Date().toLocaleDateString(undefined, { dateStyle: 'long' });
    html += `<section class="feed-section" id="section-featured">
      ${sectionHeading('Featured article', today)}
      <a class="home-hero" href="${articleHref(tfa.title)}">
        ${image ? `<span class="home-hero-media"><img src="${escapeAttr(image)}" alt="" referrerpolicy="no-referrer"></span>` : ''}
        <span class="home-hero-copy">
          <h2>${escapeHtml(tfa.title)}</h2>
          ${tfa.description ? `<span class="hero-desc">${escapeHtml(tfa.description)}</span>` : ''}
          <span class="hero-extract">${escapeHtml(tfa.extract)}</span>
          <span class="hero-link">Read article ${icon('chevron')}</span>
        </span>
      </a>
    </section>`;
  } else if (home.randoms?.length) {
    html += `<section class="feed-section" id="section-featured">${sectionHeading('Discover')}<div class="article-card-grid">${home.randoms.map(articleCard).join('')}</div></section>`;
  }

  const mostRead = home.feed?.mostread?.articles?.slice(0, 8) || [];
  if (mostRead.length) {
    html += `<section class="feed-section" id="section-trending">
      ${sectionHeading('Trending articles', 'Top articles of the day')}
      <div class="feed-list">${mostRead.map(feedRow).join('')}</div>
    </section>`;
  }

  if (home.feed?.image) {
    const potd = home.feed.image;
    const source = potd.thumbnail?.source || potd.image?.source || '';
    const image = source.startsWith('//') ? `https:${source}` : source;
    const description = core.plainText(potd.description?.html || potd.description || '');
    const title = (potd.title || '').replace(/^File:/, '').replace(/_/g, ' ');
    const link = potd.file_page || potd.filePage || '';
    html += `<section class="feed-section" id="section-potd">
      ${sectionHeading('Picture of the day', title)}
      <a class="potd-card" href="${escapeAttr(link || image)}" target="_blank" rel="noopener noreferrer">
        ${image ? `<img src="${escapeAttr(image)}" alt="${escapeAttr(title)}" loading="lazy" referrerpolicy="no-referrer">` : ''}
        <span class="potd-copy"><h3>${escapeHtml(title)}</h3><p>${escapeHtml(core.snippet(description, 320))}</p></span>
      </a>
    </section>`;
  }

  const news = home.feed?.news || [];
  if (news.length) {
    html += `<section class="feed-section" id="section-news">
      ${sectionHeading('In the news')}
      <div class="carousel">${news.slice(0, 8).map((item) => {
        const imageLink = item.links?.find((link) => link.thumbnail?.source);
        const source = imageLink?.thumbnail?.source || '';
        const image = source.startsWith('//') ? `https:${source}` : source;
        return `<article class="carousel-card">
          ${image ? `<img src="${escapeAttr(image)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : ''}
          <div class="carousel-copy">
            <p>${escapeHtml(core.snippet(item.story?.replace(/<[^>]+>/g, ' ') || '', 340))}</p>
            <div class="carousel-chips">${carouselLinks(item.links)}</div>
          </div>
        </article>`;
      }).join('')}</div>
    </section>`;
  }

  const onThisDay = home.feed?.onthisday || [];
  if (onThisDay.length) {
    html += `<section class="feed-section" id="section-onthisday">
      ${sectionHeading('On this day', new Date().toLocaleDateString(undefined, { month: 'long', day: 'numeric' }))}
      <div class="carousel story-carousel">${onThisDay.slice(0, 8).map((event) => {
        const page = event.pages?.find((item) => item.thumbnail?.source);
        const source = page?.thumbnail?.source || '';
        const image = source.startsWith('//') ? `https:${source}` : source;
        const year = Number(event.year) > 1 ? event.year : `${Math.abs(Number(event.year) || 0)} BC`;
        return `<article class="carousel-card">
          ${image ? `<img src="${escapeAttr(image)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : ''}
          <div class="carousel-copy">
            <div class="carousel-year">${escapeHtml(year)}</div>
            <p>${escapeHtml(event.text || '')}</p>
            <div class="carousel-chips">${carouselLinks(event.pages)}</div>
          </div>
        </article>`;
      }).join('')}</div>
    </section>`;
  }

  if (home.randoms?.length) {
    html += `<section class="feed-section">${sectionHeading('More to explore')}<div class="article-card-grid">${home.randoms.map(articleCard).join('')}</div></section>`;
  }

  if (state.history[0]) {
    html += `<section class="feed-section">${sectionHeading('Continue reading', timeAgo(state.history[0].viewedAt))}<div class="library-list">${storyRow(state.history[0])}</div></section>`;
  }

  if (!html.trim()) {
    html = emptyState('No stories right now', 'The feed was empty. Try refreshing.', '<button class="pill-button primary" data-action="refresh-home">Refresh</button>', 'refresh');
  }
  container.innerHTML = html;
}

async function loadHome(force = false) {
  if (state.homeLoading) return;
  if (state.dataSaver || !state.feedEnabled) { renderHome(); return; }
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

function localMatches(query) {
  const needle = query.trim().toLowerCase();
  if (!needle) return [];
  const seen = new Set();
  return [...state.saved, ...state.history]
    .filter((item) => `${item.title} ${item.description || ''}`.toLowerCase().includes(needle))
    .filter((item) => {
      const key = `${cleanLang(item.lang)}:${core.normalizeTitle(item.title)}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .slice(0, 4);
}

function renderSearch() {
  const container = $('#searchContent');
  if (!container) return;
  if (state.searchLoading) {
    container.innerHTML = Array.from({ length: 5 }, () => '<div class="skeleton" style="min-height:74px"></div>').join('');
    return;
  }
  if (state.searchError) {
    container.innerHTML = emptyState('Search failed', state.searchError, '<button class="pill-button primary" data-action="retry-search">Try again</button>', 'search');
    return;
  }
  if (state.searchResults) {
    const local = localMatches(state.searchQuery);
    const localHtml = local.length
      ? `<div class="section-heading"><div><h2>From your library</h2></div></div>
         <div class="list-stack">${local.map((item) => `<a class="result-row" href="${articleHref(item.title, item.lang)}"><h3>${escapeHtml(item.title)}</h3><p>${escapeHtml(item.description || item.extract || langName(item.lang))}</p></a>`).join('')}</div>
         <div class="section-heading" style="margin-top:24px"><div><h2>Wikipedia articles</h2></div></div>`
      : '';
    if (!state.searchResults.length && !local.length) {
      container.innerHTML = emptyState('No results', `Nothing found for “${state.searchQuery}”. Try another spelling.`, '', 'search');
      return;
    }
    container.innerHTML = localHtml + `<div class="list-stack">${state.searchResults.map((item) => `<a class="result-row" href="${articleHref(item.title)}">
      <h3>${escapeHtml(item.title)}</h3>${item.description ? `<p>${escapeHtml(item.description)}</p>` : ''}
    </a>`).join('')}</div>`;
    return;
  }

  let html = '';
  if (state.recentSearches.length) {
    html += `<div class="section-heading"><div><h2>Recent searches</h2></div><button class="text-button" data-action="clear-search-history">Clear</button></div>`;
    html += `<div class="chip-row">${state.recentSearches.map((query) => `<button class="chip" data-action="recent-search" data-query="${escapeAttr(query)}">${escapeHtml(query)}</button>`).join('')}</div>`;
  }
  html += emptyState('Search the world’s knowledge', 'Results are cached so you can reopen them offline.', '', 'search');
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
    if (state.searchHistory) {
      state.recentSearches = [trimmed, ...state.recentSearches.filter((item) => item !== trimmed)].slice(0, 10);
      writeJson(STORAGE.searches, state.recentSearches);
    }
  } catch (error) {
    state.searchError = navigator.onLine ? 'Wikipedia search did not answer.' : 'You are offline. Only cached pages can be opened.';
    console.error(error);
  } finally {
    state.searchLoading = false;
    renderSearch();
  }
}

function renderSavedFilters() {
  const container = $('#savedFilters');
  if (!container) return;
  const counts = new Map();
  state.saved.forEach((item) => {
    const lang = cleanLang(item.lang);
    counts.set(lang, (counts.get(lang) || 0) + 1);
  });
  if (counts.size <= 1) { container.innerHTML = ''; return; }
  container.innerHTML = `<button class="chip ${state.savedFilter === 'all' ? 'active' : ''}" data-action="filter-saved" data-lang="all">All <span class="count">${state.saved.length}</span></button>` +
    [...counts.entries()].map(([lang, count]) => `<button class="chip ${state.savedFilter === lang ? 'active' : ''}" data-action="filter-saved" data-lang="${escapeAttr(lang)}">${escapeHtml(langName(lang))} <span class="count">${count}</span></button>`).join('');
}

function renderSaved() {
  const container = $('#savedContent');
  const clearButton = $('#clearSavedButton');
  const summary = $('#savedSummary');
  if (!container) return;
  const filtered = state.savedFilter === 'all' ? state.saved : state.saved.filter((item) => cleanLang(item.lang) === state.savedFilter);
  if (summary) summary.textContent = state.saved.length ? `${state.saved.length} article${state.saved.length === 1 ? '' : 's'} · ${new Set(state.saved.map((item) => cleanLang(item.lang))).size} language${new Set(state.saved.map((item) => cleanLang(item.lang))).size === 1 ? '' : 's'}` : '';
  if (clearButton) clearButton.hidden = state.saved.length === 0;
  renderSavedFilters();
  if (!state.saved.length) {
    container.innerHTML = emptyState('Nothing saved yet', 'Open an article and tap Save to keep it in your library.', '', 'bookmark');
    return;
  }
  if (!filtered.length) {
    container.innerHTML = emptyState('No articles in this language', 'Choose another language filter or save something new.', '', 'bookmark');
    return;
  }
  container.innerHTML = `<div class="library-list">${filtered.map((item) => savedRow(item, state.saved.indexOf(item))).join('')}</div>`;
}

function renderHistory() {
  const container = $('#historyContent');
  const clearButton = $('#clearHistoryButton');
  const summary = $('#historySummary');
  if (!container) return;
  if (summary) summary.textContent = state.history.length ? `${state.history.length} recent article${state.history.length === 1 ? '' : 's'}` : '';
  if (clearButton) clearButton.hidden = state.history.length === 0;
  if (!state.history.length) {
    container.innerHTML = emptyState('No reading history', 'Articles you open appear here automatically.', '', 'history');
    return;
  }
  container.innerHTML = `<div class="library-list">${state.history.map((item, index) => `<div class="saved-item">
    ${storyRow(item, { meta: [langName(item.lang), timeAgo(item.viewedAt)].filter(Boolean).join(' · ') })}
    <button class="icon-button saved-remove" data-action="remove-history" data-index="${index}" aria-label="Delete ${escapeAttr(item.title)}">${icon('close')}</button>
  </div>`).join('')}</div>`;
}

function renderHighlights() {
  const container = $('#highlightsContent');
  const clearButton = $('#clearHighlightsButton');
  const summary = $('#highlightsSummary');
  if (!container) return;
  if (summary) summary.textContent = state.highlights.length ? `${state.highlights.length} saved passage${state.highlights.length === 1 ? '' : 's'}` : '';
  if (clearButton) clearButton.hidden = state.highlights.length === 0;
  if (!state.highlights.length) {
    container.innerHTML = emptyState('No saved highlights', 'Select text in an article, then choose Highlight to keep the passage here.', '', 'highlight');
    return;
  }
  container.innerHTML = `<div class="library-list">${state.highlights.map((highlight, index) => `<div class="saved-item">
    <a class="story-row library-row" href="${articleHref(highlight.title, highlight.lang)}">
      <p class="highlight-text">“${escapeHtml(highlight.text)}”</p>
      <h3>${escapeHtml(highlight.title)}</h3>
      <div class="row-meta">${escapeHtml([langName(highlight.lang), new Date(highlight.createdAt).toLocaleDateString()].join(' · '))}</div>
    </a>
    <button class="icon-button saved-remove" data-action="remove-highlight" data-index="${index}" aria-label="Delete highlight">${icon('close')}</button>
  </div>`).join('')}</div>`;
}

function renderTabs() {
  const container = $('#tabsContent');
  const clearButton = $('#clearTabsButton');
  const summary = $('#tabsSummary');
  if (!container) return;
  if (summary) summary.textContent = state.tabs.length ? `${state.tabs.length} open tab${state.tabs.length === 1 ? '' : 's'}` : '';
  if (clearButton) clearButton.hidden = state.tabs.length === 0;
  if (!state.tabs.length) {
    container.innerHTML = emptyState('No open tabs', 'Articles you open are kept here while you browse.', '', 'tabs');
    return;
  }
  container.innerHTML = `<div class="library-list">${state.tabs.map((tab, index) => `<div class="saved-item">
    <a class="story-row library-row" href="${articleHref(tab.title, tab.lang)}">
      <h3>${escapeHtml(tab.title)}</h3>
      <div class="row-meta">${escapeHtml(langName(tab.lang))} · ${escapeHtml(timeAgo(tab.openedAt))}</div>
    </a>
    <button class="icon-button tab-close" data-action="remove-tab" data-index="${index}" aria-label="Close ${escapeAttr(tab.title)}">${icon('close')}</button>
  </div>`).join('')}</div>`;
}

function populateLanguages() {
  const select = $('#languageSelect');
  if (select) {
    select.innerHTML = LANGS.map(([code, name]) => `<option value="${escapeAttr(code)}">${escapeHtml(name)} (${escapeHtml(code)})</option>`).join('');
    select.value = state.lang;
  }
}

function renderLanguageList(filter = '') {
  const list = $('#languageList');
  if (!list) return;
  const needle = filter.trim().toLowerCase();
  const matches = LANGS.filter(([code, name]) => !needle || name.toLowerCase().includes(needle) || code.includes(needle));
  const current = matches.find(([code]) => code === state.lang);
  const recentCodes = state.recentLangs.filter((code) => code !== state.lang && matches.some(([candidate]) => candidate === code));
  const groups = [];
  if (current) groups.push(['Current language', [current]]);
  if (recentCodes.length) groups.push(['Recent languages', recentCodes.map((code) => [code, LANG_NAMES.get(code) || code])]);
  const others = matches.filter(([code]) => code !== state.lang && !recentCodes.includes(code));
  if (others.length) groups.push([current || recentCodes.length ? 'Other languages' : 'Languages', others]);
  list.innerHTML = groups.map(([label, items]) => `<div class="language-group-title">${escapeHtml(label)}</div>${items.map(([code, name]) => `<button type="button" class="language-option ${code === state.lang ? 'active' : ''}" data-action="set-language" data-lang="${escapeAttr(code)}">${escapeHtml(name)}<small>${escapeHtml(code)}.wikipedia.org</small></button>`).join('')}`).join('') || '<p class="setting-hint">No matching language.</p>';
}

function renderStats() {
  const container = $('#statsContent');
  if (!container) return;
  container.innerHTML = `
    <div class="stat-box"><strong>${formatNumber(state.stats.articlesRead)}</strong><span>Articles opened</span></div>
    <div class="stat-box"><strong>${formatNumber(state.stats.minutesRead)}</strong><span>Minutes reading</span></div>
    <div class="stat-box"><strong>${formatNumber(state.stats.wordsRead)}</strong><span>Words read</span></div>
    <div class="stat-box"><strong>${formatNumber(state.saved.length)}</strong><span>Saved articles</span></div>
    <div class="stat-box"><strong>${formatNumber(state.highlights.length)}</strong><span>Saved highlights</span></div>
    <div class="stat-box"><strong>${formatNumber(state.history.length)}</strong><span>Articles in history</span></div>`;
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

const SETTINGS_CATEGORIES = [
  { id: 'reading', icon: 'book', title: 'Reading & topics', desc: 'Choose topics, manage reading and offline articles', count: 13 },
  { id: 'appearance', icon: 'palette', title: 'Appearance', desc: 'Theme, colors, typography, images and motion', count: 10 },
  { id: 'behaviour', icon: 'translate', title: 'Language & behaviour', desc: 'Wikipedia and app language, navigation and data use', count: 4 },
  { id: 'data', icon: 'database', title: 'Data & privacy', desc: 'Vocabulary, statistics, backups and history', count: 6 },
  { id: 'about', icon: 'info', title: 'About', desc: 'Tutorial, default app, debugging and reset options', count: 5 }
];

function settingSwitch(key, title, support, iconName) {
  const checked = Boolean(state[key]);
  return `<div class="setting-item">
    <span class="setting-icon">${icon(iconName)}</span>
    <span class="setting-copy"><span class="setting-title">${escapeHtml(title)}</span><span class="setting-support">${escapeHtml(support)}</span></span>
    <span class="setting-control"><button class="switch" role="switch" aria-checked="${checked}" aria-label="${escapeAttr(title)}" data-action="set-setting" data-setting="${escapeAttr(key)}"></button></span>
  </div>`;
}

function settingSegment(key, title, support, choices, iconName) {
  return `<div class="setting-item">
    <span class="setting-icon">${icon(iconName)}</span>
    <span class="setting-copy"><span class="setting-title">${escapeHtml(title)}</span>${support ? `<span class="setting-support">${escapeHtml(support)}</span>` : ''}
      <span class="segmented" style="margin-top:8px">${choices.map(([value, label]) => `<button class="${state[key] === value ? 'active' : ''}" data-action="set-setting" data-setting="${escapeAttr(key)}" data-value="${escapeAttr(value)}">${escapeHtml(label)}</button>`).join('')}</span>
    </span>
  </div>`;
}

function settingGroup(title, rows) {
  return `<section class="settings-group">${title ? `<h2 class="settings-group-title">${escapeHtml(title)}</h2>` : ''}${rows.join('')}</section>`;
}

function settingsSearchBox() {
  return `<label class="settings-search">${icon('search')}<input id="settingsSearch" type="search" placeholder="Search settings…" autocomplete="off" value="${escapeAttr(state.settingsQuery)}" aria-label="Search settings"></label>`;
}

function renderSettingsRoot() {
  const query = state.settingsQuery.trim().toLowerCase();
  const categories = SETTINGS_CATEGORIES.filter((category) =>
    !query || `${category.title} ${category.desc}`.toLowerCase().includes(query));
  return `${settingsSearchBox()}
    <h1 class="settings-hub-title">Settings</h1>
    <p class="settings-hub-desc">Choose a category to change how WikiReader looks and works.</p>
    <div class="settings-cards">
      ${categories.map((category) => `<button class="category-card" data-action="open-settings-category" data-category="${category.id}">
        <span class="category-icon">${icon(category.icon)}</span>
        <span class="category-copy"><strong>${escapeHtml(category.title)}</strong><span>${escapeHtml(category.desc)}</span><span class="category-count">${category.count} settings</span></span>
        ${icon('chevron')}
      </button>`).join('') || '<p class="settings-empty">No settings category matches that search.</p>'}
    </div>`;
}

function renderSettingsCategory(categoryId) {
  const category = SETTINGS_CATEGORIES.find((item) => item.id === categoryId) || SETTINGS_CATEGORIES[0];
  let body = '';
  if (category.id === 'reading') {
    const topicCount = state.topics.length + state.customTopics.length;
    body = settingGroup('Topics', [
      `<button class="setting-item clickable" data-action="open-topics">
        <span class="setting-icon">${icon('book')}</span>
        <span class="setting-copy"><span class="setting-title">Random article topics</span><span class="setting-support">${topicCount ? `${topicCount} topic${topicCount === 1 ? '' : 's'} selected` : 'Choose topics or add your own'}</span></span>
        <span class="setting-control">${icon('chevron')}</span>
      </button>`
    ]) + settingGroup('Article reading', [
      settingSwitch('onlyImages', 'Only show articles with images', 'Skip random topic articles that do not have a lead image.', 'image'),
      settingSwitch('expandedSections', 'Open contents by default', 'Expand the table of contents when an article opens.', 'book'),
      settingSwitch('boldArticleText', 'Bold article text', 'Use a heavier weight for article paragraphs.', 'text'),
      settingSwitch('saveOffline', 'Save articles for offline reading', 'Keep a copy of the article text when you tap Save.', 'download')
    ]) + settingGroup('Offline reading', [
      `<div class="setting-item"><span class="setting-icon">${icon('database')}</span><span class="setting-copy"><span class="setting-title">Cached content</span><span class="setting-support">The app shell and every article you read stay available offline.</span></span></div>`,
      `<div class="setting-stack"><div id="cacheStats" class="stat-grid"></div><div class="button-row"><button class="pill-button primary" data-action="random">Try a random article</button><button class="pill-button" data-action="clear-cache">Clear cache</button></div></div>`
    ]);
  } else if (category.id === 'appearance') {
    body = settingGroup('Color', [
      `<div class="setting-stack">
        <span class="setting-title">Color scheme</span>
        <div class="color-swatches">
          ${[['green', 'Green'], ['lavender', 'Lavender'], ['ocean', 'Ocean'], ['rose', 'Rose']].map(([value, label]) => `<button class="swatch ${state.colorScheme === value ? 'active' : ''}" data-action="set-setting" data-setting="colorScheme" data-value="${value}" aria-label="${label}" title="${label}"></button>`).join('')}
          <span class="swatch-label">${escapeHtml({ green: 'Green', lavender: 'Lavender', ocean: 'Ocean', rose: 'Rose' }[state.colorScheme] || 'Green')}</span>
        </div>
      </div>`,
      settingSegment('theme', 'Theme', 'Follow the system, or force light or dark.', [['auto', 'System'], ['light', 'Light'], ['dark', 'Dark']], 'palette'),
      settingSwitch('blackTheme', 'Black theme', 'Use a pure black dark theme.', 'palette')
    ]) + settingGroup('Typography', [
      settingSegment('fontStyle', 'Font style', 'Applied to article text throughout the reader.', [['sans', 'Sans'], ['serif', 'Serif'], ['mono', 'Mono'], ['hyperlegible', 'Legible']], 'text'),
      `<div class="setting-stack"><div class="setting-item" style="border:0;padding:0;min-height:0"><span class="setting-icon">${icon('text')}</span><span class="setting-copy"><span class="setting-title">Font size</span><span class="setting-support">Current: <strong id="fontSizeLabel">${state.fontSize} px</strong></span></span></div>
        <input id="fontSizeInput" class="setting-range" type="range" min="10" max="22" step="1" value="${state.fontSize}" data-setting="fontSize" aria-label="Article font size">
      </div>`
    ]) + settingGroup('Images & motion', [
      settingSwitch('imageBackground', 'Image background', 'Add a white background behind transparent images in dark mode.', 'image'),
      settingSwitch('reduceMotion', 'Reduce motion', 'Use quieter, shorter animations throughout the app.', 'refresh')
    ]);
  } else if (category.id === 'behaviour') {
    body = settingGroup('Language', [
      `<label class="setting-item clickable"><span class="setting-icon">${icon('translate')}</span><span class="setting-copy"><span class="setting-title">Wikipedia language</span><span class="setting-support">Articles, search and the daily feed use this language.</span></span><span class="setting-control"><select id="languageSelect" class="setting-select" aria-label="Wikipedia language"></select></span></label>`,
      `<div class="setting-item"><span class="setting-icon">${icon('globe')}</span><span class="setting-copy"><span class="setting-title">App language</span><span class="setting-support">English interface. Article language is set separately above.</span></span></div>`
    ]) + settingGroup('Home & navigation', [
      settingSwitch('feedEnabled', 'Show the featured feed', 'Load Wikipedia’s featured article, trending list and news on Home.', 'home'),
      settingSwitch('dataSaver', 'Data saver', 'Pause the feed and hide images. Articles can still be opened.', 'cloud'),
      settingSwitch('searchHistory', 'Search history', 'Remember recent search queries on this device.', 'search'),
      settingSwitch('browsingHistory', 'Browsing history', 'Save articles you open to History.', 'history'),
      settingSegment('menuSide', 'Menu side', 'Physical side the navigation drawer opens from.', [['left', 'Left'], ['right', 'Right']], 'menu')
    ]) + settingGroup('Interaction', [
      settingSwitch('hapticFeedback', 'Haptic feedback', 'Vibrate briefly when changing settings or saving an article.', 'sparkle')
    ]);
  } else if (category.id === 'data') {
    body = settingGroup('Reading statistics', [
      `<div class="setting-stack"><div id="statsContent" class="stat-grid"></div><div class="button-row"><button class="pill-button" data-action="reset-stats">Reset statistics</button></div></div>`
    ]) + settingGroup('Your library', [
      `<div class="setting-item"><span class="setting-icon">${icon('bookmark')}</span><span class="setting-copy"><span class="setting-title">Saved articles</span><span class="setting-support">${state.saved.length} article${state.saved.length === 1 ? '' : 's'} in your library.</span></span><button class="text-button" data-action="tab" data-tab="saved">Open</button></div>`,
      `<div class="setting-item"><span class="setting-icon">${icon('highlight')}</span><span class="setting-copy"><span class="setting-title">Saved highlights</span><span class="setting-support">${state.highlights.length} passage${state.highlights.length === 1 ? '' : 's'} kept from articles.</span></span><button class="text-button" data-action="tab" data-tab="highlights">Open</button></div>`,
      `<div class="setting-stack"><span class="setting-title">Storage</span><div id="cacheStats" class="stat-grid"></div><div class="button-row"><button class="pill-button" data-action="export-data">Export backup</button><button class="pill-button" data-action="import-data">Import backup</button><button class="pill-button" data-action="clear-cache">Clear cache</button></div><input id="importFile" type="file" accept="application/json" hidden></div>`
    ]) + settingGroup('Privacy', [
      `<div class="setting-item"><span class="setting-icon">${icon('info')}</span><span class="setting-copy"><span class="setting-title">Everything stays on this device</span><span class="setting-support">Saved articles, highlights, history and statistics are stored in your browser. Nothing is sent to us.</span></span></div>`
    ]);
  } else {
    body = `<section class="card about-card">
      <span class="brand-mark large" aria-hidden="true">W</span>
      <h2>WikiReader</h2>
      <p>WikiRandomizer-P is an open-source Wikipedia reader. The Android app is native Compose, the iOS app is SwiftUI, and this PWA shares the same Kotlin core compiled to WebAssembly.</p>
      <p><span id="wasmStatus">${escapeHtml(coreStatus)}</span></p>
      <div class="button-row" style="justify-content:center"><a class="pill-button" href="https://github.com/PatrickJamesDev/WikiRandomizer-P" target="_blank" rel="noopener">Source &amp; releases</a><button class="pill-button primary" data-action="install" id="installButton">Install app</button></div>
      <p class="setting-hint" id="installHint"></p>
    </section>` + settingGroup('App', [
      `<div class="setting-item"><span class="setting-icon">${icon('sparkle')}</span><span class="setting-copy"><span class="setting-title">PWA build</span><span class="setting-support">Installable, offline-capable web build of WikiRandomizer-P.</span></span></div>`,
      `<div class="setting-item"><span class="setting-icon">${icon('refresh')}</span><span class="setting-copy"><span class="setting-title">Reset settings</span><span class="setting-support">Restore appearance and reading settings to their defaults. Your library is kept.</span></span><button class="text-button destructive" data-action="reset-settings">Reset</button></div>`
    ]);
  }
  return `<div class="settings-category-header"><button class="icon-button" data-action="settings-home" aria-label="Back to settings">${icon('back')}</button><div><h1>${escapeHtml(category.title)}</h1><p>${escapeHtml(category.desc)}</p></div></div>${body}`;
}

function renderTopicsSettings() {
  const builtInChips = TOPICS.map((topic) => `<button class="chip topic-chip ${state.topics.includes(topic.id) ? 'active' : ''}" data-action="set-setting" data-setting="topics" data-value="${topic.id}">${state.topics.includes(topic.id) ? icon('check') : ''}${escapeHtml(topic.label)}</button>`).join('');
  const customChips = state.customTopics.map((topic) => `<button class="chip topic-chip custom-topic-chip active" data-action="remove-custom-topic" data-topic="${escapeAttr(topic)}" aria-label="Remove ${escapeAttr(topic)}">${escapeHtml(topic)}${icon('close')}</button>`).join('');
  const count = state.topics.length + state.customTopics.length;
  const summary = count
    ? `${count} topic${count === 1 ? '' : 's'} selected`
    : 'No topics selected — random articles will come from all of Wikipedia';
  return `<div class="settings-category-header">
      <button class="icon-button" data-action="open-settings-category" data-category="reading" aria-label="Back to Reading & topics">${icon('back')}</button>
      <div><h1>Random article topics</h1><p>Choose what the shuffle button finds.</p></div>
    </div>
    <section class="topic-intro">
      <p>Select topics you’re interested in. The random article button will only show articles from your chosen topics. Leave all unselected for fully random articles.</p>
    </section>
    ${settingGroup('Topics', [
      `<div class="topic-grid">${builtInChips}</div>`
    ])}
    ${settingGroup('Custom topics', [
      `<div class="setting-stack">
        <span class="setting-title">Add your own topic</span>
        <p class="setting-hint" style="margin:0 0 10px">Any phrase works — for example “succulent plants”, “jazz drummers” or “medieval castles”. Custom topics find matching articles by Wikipedia search.</p>
        ${customChips ? `<div class="topic-grid custom-topic-list">${customChips}</div>` : ''}
        <form id="customTopicForm" class="custom-topic-form">
          <input id="customTopicInput" type="text" maxlength="80" autocomplete="off" placeholder="e.g. succulent plants" aria-label="Custom topic">
          <button class="pill-button primary" type="submit">${icon('plus')}Add</button>
        </form>
      </div>`
    ])}
    ${settingGroup('Selection', [
      `<div class="setting-stack">
        <p class="setting-hint" style="margin:0 0 12px">${escapeHtml(summary)}</p>
        <button class="pill-button primary" data-action="open-settings-category" data-category="reading">Save topics</button>
      </div>`
    ])}`;
}

function renderSettings() {
  const container = $('#settingsContent');
  if (!container) return;
  const active = document.activeElement;
  const wasSearching = active?.id === 'settingsSearch';
  const cursor = wasSearching ? active.selectionStart : null;
  const content = state.settingsCategory === 'topics'
    ? renderTopicsSettings()
    : state.settingsCategory === 'root'
      ? renderSettingsRoot()
      : renderSettingsCategory(state.settingsCategory);
  container.innerHTML = content;
  populateLanguages();
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
  applyFontSize();
  if (wasSearching) {
    const input = $('#settingsSearch');
    if (input) {
      input.focus();
      input.setSelectionRange(cursor, cursor);
    }
  }
}

function updateSaveButton() {
  const button = $('#saveArticleButton');
  if (!button) return;
  const saved = isSaved(state.article?.summary);
  button.classList.toggle('active', saved);
  button.setAttribute('aria-label', saved ? 'Remove saved article' : 'Save article');
  const label = $('span', button);
  if (label) label.textContent = saved ? 'Saved' : 'Save';
}

function updateArticleActions() {
  const bar = $('#articleReaderActions');
  if (!bar) return;
  bar.hidden = state.tab !== 'article' || !state.article;
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
    ? `<details class="toc"${state.expandedSections ? ' open' : ''}><summary>Contents</summary><ol>${headings.slice(0, 16).map((heading) => `<li class="toc-${heading.tagName.toLowerCase()}"><a href="#${escapeAttr(heading.id)}" data-anchor="#${escapeAttr(heading.id)}">${escapeHtml(heading.textContent.trim())}</a></li>`).join('')}</ol></details>`
    : '';
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
  if (!summary?.title || !state.browsingHistory) return;
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

function addTab(summary, lang) {
  if (!summary?.title) return;
  const key = `${cleanLang(lang)}:${core.normalizeTitle(summary.title)}`;
  state.tabs = [{
    title: summary.title,
    lang: cleanLang(lang),
    openedAt: Date.now()
  }, ...state.tabs.filter((item) => `${cleanLang(item.lang)}:${core.normalizeTitle(item.title)}` !== key)].slice(0, 12);
  writeJson(STORAGE.tabs, state.tabs);
}

function recordStats(summary, html) {
  const lang = cleanLang(summary?.lang || state.article?.lang || state.lang);
  const key = `${lang}:${core.normalizeTitle(summary?.title || '')}`;
  if (key === state.lastReadKey && Date.now() - state.lastReadAt < 60000) return;
  const now = Date.now();
  if (!state.lastReadAt || now - state.stats.lastReadAt > 30 * 60 * 1000) {
    state.stats.sessions = (state.stats.sessions || 0) + 1;
  }
  state.lastReadKey = key;
  state.lastReadAt = now;
  state.stats.lastReadAt = now;
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
  updateArticleActions();

  if (article.error) {
    container.innerHTML = emptyState('Article unavailable', article.error, '<button class="pill-button primary" data-action="retry-article">Try again</button>', 'cloud');
    return;
  }
  if (!article.summary) {
    container.innerHTML = '<div class="article-status"><div class="skeleton" style="height:48px;max-width:70%;margin:0 auto 18px"></div><div class="skeleton" style="height:220px"></div></div>';
    return;
  }

  const summary = article.summary;
  const image = imageFor(summary);
  const minutes = core.readingMinutes(article.html ? core.plainText(article.html) : summary.extract);
  let html = `<header class="article-header">
    <h1>${escapeHtml(summary.title)}</h1>
    ${summary.description ? `<p class="article-description">${escapeHtml(summary.description)}</p>` : ''}
    <div class="article-meta"><span class="pill">${escapeHtml(article.lang.toUpperCase())} Wikipedia</span>${minutes ? `<span>${minutes} min read</span>` : ''}<span>${isSaved(summary) ? 'Saved' : 'Not saved'}</span></div>
  </header>`;
  if (image) html += `<img class="article-hero" src="${escapeAttr(image)}" alt="" referrerpolicy="no-referrer">`;
  if (article.html) {
    html += article.toc || '';
    html += `<div class="mw-parser-output">${article.html}</div>`;
  } else {
    html += `<p class="article-summary">${escapeHtml(summary.extract || '')}</p>`;
    html += `<div class="card" style="margin-top:20px"><h2>Full article is not cached</h2><p class="setting-hint">Reconnect to download the complete article. The summary above is still readable offline.</p></div>`;
  }
  container.innerHTML = html;
  document.title = `${summary.title} · WikiReader`;
}

async function loadArticle(lang, title) {
  const token = ++state.articleToken;
  state.article = { lang: cleanLang(lang), title: title.replace(/_/g, ' '), summary: null, html: '', toc: '', error: '' };
  hideSelectionToolbar();
  renderArticle();
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
    addTab(summary, lang);
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
  } catch (error) {
    if (token !== state.articleToken) return;
    state.article.error = navigator.onLine
      ? 'Wikipedia did not answer. Try again in a moment.'
      : 'You are offline and this article has not been cached yet.';
    renderArticle();
    console.error(error);
  }
}

function showTab(tab, category = 'root') {
  const allowed = ['home', 'search', 'saved', 'history', 'highlights', 'tabs', 'settings'];
  state.tab = allowed.includes(tab) ? tab : 'home';
  state.settingsCategory = category || 'root';
  state.articleToken++;
  state.article = null;
  hideSelectionToolbar();
  document.body.dataset.view = state.tab;
  $$('.view').forEach((view) => { view.hidden = view.dataset.view !== state.tab; });
  $$('[data-action="tab"]').forEach((button) => {
    const settingsActive = button.dataset.tab === 'settings' && state.settingsCategory !== 'about';
    button.classList.toggle('active', button.dataset.tab === state.tab && (button.dataset.tab !== 'settings' || settingsActive));
  });
  $$('[data-action="open-about"]').forEach((button) => button.classList.toggle('active', state.tab === 'settings' && state.settingsCategory === 'about'));
  const titles = { home: 'WikiReader', search: 'Search · WikiReader', saved: 'Saved · WikiReader', history: 'History · WikiReader', highlights: 'Saved highlights · WikiReader', tabs: 'Tabs · WikiReader', settings: 'Settings · WikiReader' };
  document.title = state.tab === 'settings' && state.settingsCategory === 'topics' ? 'Random article topics · WikiReader' : titles[state.tab] || 'WikiReader';
  updateArticleActions();
  const progress = $('#readingProgress');
  if (progress) progress.style.width = '0%';
  window.scrollTo({ top: 0, behavior: 'instant' });
  closeDrawer();
  if (state.tab === 'home') { renderHome(); loadHome(); }
  if (state.tab === 'search') {
    renderSearch();
    setTimeout(() => $('#searchInput')?.focus(), 120);
  }
  if (state.tab === 'saved') renderSaved();
  if (state.tab === 'history') renderHistory();
  if (state.tab === 'highlights') renderHighlights();
  if (state.tab === 'tabs') renderTabs();
  if (state.tab === 'settings') renderSettings();
}

function parseHash() {
  const raw = decodeURI(location.hash.replace(/^#\/?/, ''));
  if (!raw) return { type: 'tab', tab: 'home' };
  const parts = raw.split('/');
  if (parts[0] === 'wiki' && parts.length >= 3) {
    return { type: 'article', lang: cleanLang(parts[1]), title: parts.slice(2).join('/').replace(/_/g, ' ') };
  }
  if (parts[0] === 'settings') {
    if (parts[1] === 'topics') return { type: 'tab', tab: 'settings', category: 'topics' };
    const category = parts[1] && SETTINGS_CATEGORIES.some((item) => item.id === parts[1]) ? parts[1] : 'root';
    return { type: 'tab', tab: 'settings', category };
  }
  if (parts[0] === 'about') return { type: 'tab', tab: 'settings', category: 'about' };
  if (['home', 'search', 'saved', 'history', 'highlights', 'tabs'].includes(parts[0])) return { type: 'tab', tab: parts[0] };
  return { type: 'tab', tab: 'home' };
}

function route() {
  const destination = parseHash();
  if (destination.type === 'article') {
    state.tab = 'article';
    state.articleToken++;
    document.body.dataset.view = 'article';
    $$('.view').forEach((view) => { view.hidden = view.dataset.view !== 'article'; });
    $$('[data-action="tab"]').forEach((button) => button.classList.remove('active'));
    updateArticleActions();
    closeDrawer();
    loadArticle(destination.lang, destination.title);
  } else {
    showTab(destination.tab, destination.category);
  }
}

function openDrawer() {
  document.body.classList.add('drawer-open');
  $('#navigationDrawer')?.setAttribute('aria-hidden', 'false');
  $('[data-action="open-drawer"]')?.setAttribute('aria-expanded', 'true');
}

function closeDrawer() {
  document.body.classList.remove('drawer-open');
  $('#navigationDrawer')?.setAttribute('aria-hidden', 'true');
  $('[data-action="open-drawer"]')?.setAttribute('aria-expanded', 'false');
}

function closeDialog() {
  const dialog = $('#languageDialog');
  if (dialog?.open) dialog.close();
}

function askConfirm(title, body, confirmLabel = 'Confirm') {
  const dialog = $('#confirmDialog');
  if (!dialog) return Promise.resolve(confirm(title + '\n\n' + body));
  $('#confirmTitle').textContent = title;
  $('#confirmBody').textContent = body;
  $('#confirmAccept').textContent = confirmLabel;
  return new Promise((resolve) => {
    const onClose = () => {
      dialog.removeEventListener('close', onClose);
      resolve(dialog.returnValue === 'confirm');
    };
    dialog.addEventListener('close', onClose);
    dialog.returnValue = '';
    dialog.showModal();
  });
}

function toggleTheme() {
  const dark = document.documentElement.dataset.theme === 'dark';
  state.theme = dark ? 'light' : 'dark';
  writeText(STORAGE.theme, state.theme);
  applyAppearance();
  renderSettings();
  showToast(`${state.theme === 'dark' ? 'Dark' : 'Light'} theme`);
}

function setLanguage(lang) {
  const code = cleanLang(lang);
  if (!LANG_NAMES.has(code)) return;
  state.lang = code;
  writeText(STORAGE.lang, state.lang);
  state.recentLangs = [code, ...state.recentLangs.filter((item) => item !== code)].slice(0, 6);
  writeJson(STORAGE.recentLangs, state.recentLangs);
  populateLanguages();
  renderLanguageList($('#languageFilter')?.value || '');
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
  showToast(`Wikipedia language: ${langName(state.lang)}`);
}

function saveTabForCurrentArticle() {
  const summary = state.article?.summary;
  if (!summary) { showToast('Article is still loading'); return; }
  addTab(summary, state.article.lang);
  showToast('Kept in tabs');
}

function toggleSave() {
  const summary = state.article?.summary;
  if (!summary) { showToast('Article is still loading'); return; }
  haptic();
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
    if (state.saveOffline && !state.article?.html) {
      fetchArticleHtml(state.article.lang, summary.title).catch(() => {});
    }
    showToast(state.saveOffline ? 'Saved for offline reading' : 'Saved');
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
  state.fontSize = clamp(state.fontSize + delta, 10, 22);
  writeText(STORAGE.fontSize, String(state.fontSize));
  applyFontSize();
  haptic();
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
  const confirmed = await askConfirm('Clear offline cache?', 'Cached articles and the app shell will be removed. You can download them again later.', 'Clear cache');
  if (!confirmed) return;
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

function addCustomTopic(value) {
  const topic = normalizeCustomTopic(value);
  if (!topic) return false;
  if (state.customTopics.some((item) => item.toLowerCase() === topic.toLowerCase())) {
    showToast('That topic is already in your list');
    return false;
  }
  state.customTopics = [...state.customTopics, topic];
  writeJson(STORAGE.customTopics, state.customTopics);
  haptic();
  showToast(`Added “${topic}”`);
  return true;
}

function removeCustomTopic(topic) {
  state.customTopics = state.customTopics.filter((item) => item !== topic);
  writeJson(STORAGE.customTopics, state.customTopics);
  haptic();
}

function randomItem(items) {
  return items.length ? items[Math.floor(Math.random() * items.length)] : null;
}

async function randomBuiltInTopicTitle() {
  if (!state.topics.length) return null;
  const selected = TOPICS.filter((topic) => state.topics.includes(topic.id));
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const url = `${apiOrigin()}/w/api.php?action=query&format=json&origin=*&generator=random&grnnamespace=0&grnlimit=40&prop=categories|pageimages&cllimit=max&piprop=thumbnail&pithumbsize=320`;
    const data = await fetchJson(url);
    const pages = Object.values(data?.query?.pages || {});
    const matches = pages.filter((page) => {
      if (!page?.title || page.missing !== undefined) return false;
      if (state.onlyImages && !page.thumbnail?.source) return false;
      if (/^(list of|outline of|index of)/i.test(page.title)) return false;
      const categories = (page.categories || []).map((category) => category.title.replace(/^Category:/, '').toLowerCase());
      return selected.some((topic) => topic.terms.some((term) => categories.some((category) => category.includes(term))));
    });
    if (matches.length) return matches[Math.floor(Math.random() * matches.length)].title;
  }
  return null;
}

async function randomCustomTopicTitle(topic) {
  const phrase = normalizeCustomTopic(topic);
  if (!phrase) return null;
  const url = `${apiOrigin()}/w/api.php?action=query&format=json&origin=*&generator=search&gsrsearch=${encodeURIComponent(phrase)}&gsrnamespace=0&gsrlimit=20&prop=pageimages&piprop=thumbnail&pithumbsize=320`;
  const data = await fetchJson(url);
  const pages = Object.values(data?.query?.pages || {});
  const matches = pages.filter((page) => {
    if (!page?.title || page.missing !== undefined) return false;
    if (state.onlyImages && !page.thumbnail?.source) return false;
    return !/^(list of|outline of|index of)/i.test(page.title);
  });
  return randomItem(matches)?.title || null;
}

async function randomTopicTitle() {
  const sources = [];
  if (state.topics.length) sources.push('built-in');
  if (state.customTopics.length) sources.push('custom');
  if (!sources.length) return null;
  const source = randomItem(sources);
  if (source === 'custom') {
    const phrases = [...state.customTopics].sort(() => Math.random() - 0.5);
    for (const phrase of phrases.slice(0, 3)) {
      const title = await randomCustomTopicTitle(phrase);
      if (title) return title;
    }
    return state.topics.length ? randomBuiltInTopicTitle() : null;
  }
  return randomBuiltInTopicTitle();
}

async function openRandom() {
  try {
    if (state.topics.length || state.customTopics.length) {
      try {
        const topical = await randomTopicTitle();
        if (topical) {
          location.hash = articleHref(topical, state.lang);
          return;
        }
        showToast('No topic match found. Opening a general random article.');
      } catch (error) {
        console.warn('[pwa] topic lookup failed, using a general random article', error);
      }
    }
    if (state.onlyImages) {
      for (let attempt = 0; attempt < 3; attempt += 1) {
        const candidate = await fetchJson(core.randomSummaryUrl(state.lang));
        if (candidate?.thumbnail?.source || candidate?.originalimage?.source) {
          location.hash = articleHref(candidate.normalizedtitle || candidate.title, state.lang);
          return;
        }
      }
    }
    const data = await fetchJson(core.randomSummaryUrl(state.lang));
    const title = data?.normalizedtitle || data?.title || data?.displaytitle;
    if (!title) throw new Error('Random summary did not include a title');
    location.hash = articleHref(title, state.lang);
  } catch (error) {
    showToast('Could not load a random article');
    console.error(error);
  }
}

function setSetting(key, value) {
  const booleanKeys = ['blackTheme', 'imageBackground', 'reduceMotion', 'boldArticleText', 'dataSaver', 'feedEnabled', 'browsingHistory', 'searchHistory', 'onlyImages', 'expandedSections', 'saveOffline', 'hapticFeedback'];
  if (booleanKeys.includes(key)) {
    state[key] = !state[key];
    writeBool(STORAGE[key], state[key]);
  } else if (key === 'topics') {
    const topic = String(value || '');
    if (!TOPICS.some((item) => item.id === topic)) return;
    state.topics = state.topics.includes(topic) ? state.topics.filter((item) => item !== topic) : [...state.topics, topic];
    writeJson(STORAGE.topics, state.topics);
  } else if (key === 'fontSize') {
    state.fontSize = clamp(Number(value) || 16, 10, 22);
    writeText(STORAGE.fontSize, String(state.fontSize));
  } else if (key === 'theme') {
    state.theme = normalizeTheme(String(value));
    writeText(STORAGE.theme, state.theme);
  } else if (key === 'colorScheme') {
    state.colorScheme = ['green', 'lavender', 'ocean', 'rose'].includes(String(value)) ? String(value) : 'green';
    writeText(STORAGE.colorScheme, state.colorScheme);
  } else if (key === 'fontStyle') {
    state.fontStyle = ['sans', 'serif', 'mono', 'hyperlegible'].includes(String(value)) ? String(value) : 'sans';
    writeText(STORAGE.fontStyle, state.fontStyle);
  } else if (key === 'menuSide') {
    state.menuSide = String(value) === 'right' ? 'right' : 'left';
    writeText(STORAGE.menuSide, state.menuSide);
  } else {
    return;
  }
  haptic();
  applyPreferences();
  if (['feedEnabled', 'dataSaver'].includes(key)) {
    state.home = null;
    if (state.tab === 'home') loadHome(true);
  }
  if (key === 'browsingHistory' && !state.browsingHistory) {
    state.history = [];
    writeJson(STORAGE.history, state.history);
  }
  if (key === 'searchHistory' && !state.searchHistory) {
    state.recentSearches = [];
    writeJson(STORAGE.searches, state.recentSearches);
  }
  renderSettings();
  if (state.tab === 'home') renderHome();
}

function selectionInArticle() {
  const selection = window.getSelection?.();
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return '';
  const text = selection.toString().trim();
  if (text.length < 2) return '';
  const range = selection.getRangeAt(0);
  const node = range.commonAncestorContainer;
  const element = node.nodeType === 1 ? node : node.parentElement;
  if (!element?.closest('#articleContent')) return '';
  return text;
}

function updateSelectionToolbar() {
  const toolbar = $('#selectionToolbar');
  if (!toolbar) return;
  if (state.tab !== 'article') { hideSelectionToolbar(); return; }
  const text = selectionInArticle();
  state.selectionText = text;
  toolbar.hidden = !text;
}

function hideSelectionToolbar() {
  state.selectionText = '';
  const toolbar = $('#selectionToolbar');
  if (toolbar) toolbar.hidden = true;
}

function saveSelectionHighlight() {
  const text = state.selectionText || selectionInArticle();
  const summary = state.article?.summary;
  if (!text || !summary) { showToast('Select a passage first'); return; }
  const key = `${state.article.lang}:${core.normalizeTitle(summary.title)}`;
  const exists = state.highlights.some((item) => `${cleanLang(item.lang)}:${core.normalizeTitle(item.title)}` === key && item.text === text);
  if (exists) { showToast('Highlight already saved'); return; }
  state.highlights = [{
    id: Date.now(),
    text,
    title: summary.title,
    lang: state.article.lang,
    createdAt: Date.now()
  }, ...state.highlights].slice(0, 500);
  writeJson(STORAGE.highlights, state.highlights);
  window.getSelection?.()?.removeAllRanges();
  hideSelectionToolbar();
  showToast('Highlight saved');
  haptic();
}

function openSelectionSearch() {
  const text = state.selectionText || selectionInArticle();
  if (!text) return;
  window.getSelection?.()?.removeAllRanges();
  hideSelectionToolbar();
  location.hash = '#/search';
  setTimeout(() => {
    const input = $('#searchInput');
    if (input) input.value = text;
    doSearch(text);
  }, 160);
}

function onAction(action, element) {
  switch (action) {
    case 'home':
      location.hash = '#/home';
      break;
    case 'tab':
      location.hash = `#/${element.dataset.tab}`;
      break;
    case 'open-drawer': openDrawer(); break;
    case 'close-drawer': closeDrawer(); break;
    case 'open-search':
      location.hash = '#/search';
      setTimeout(() => $('#searchInput')?.focus(), 120);
      break;
    case 'open-about': location.hash = '#/settings/about'; break;
    case 'random': openRandom(); break;
    case 'toggle-theme': toggleTheme(); break;
    case 'open-language': renderLanguageList(); $('#languageDialog')?.showModal(); break;
    case 'set-language': setLanguage(element.dataset.lang); break;
    case 'refresh-home': state.home = null; loadHome(true); break;
    case 'scroll-section': {
      const section = element.dataset.section;
      if (state.tab !== 'home') location.hash = '#/home';
      closeDrawer();
      setTimeout(() => document.getElementById(`section-${section}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 160);
      break;
    }
    case 'retry-search': doSearch(state.searchQuery); break;
    case 'recent-search': $('#searchInput').value = element.dataset.query; doSearch(element.dataset.query); break;
    case 'clear-search':
      $('#searchInput').value = '';
      state.searchQuery = '';
      state.searchResults = null;
      state.searchError = '';
      renderSearch();
      $('#searchInput')?.focus();
      break;
    case 'clear-search-history':
      state.recentSearches = [];
      writeJson(STORAGE.searches, state.recentSearches);
      renderSearch();
      break;
    case 'filter-saved':
      state.savedFilter = element.dataset.lang || 'all';
      renderSaved();
      break;
    case 'clear-saved':
      askConfirm('Delete all saved articles?', 'Every saved article will be removed from this device.', 'Delete all').then((confirmed) => {
        if (!confirmed) return;
        state.saved = [];
        state.savedFilter = 'all';
        writeJson(STORAGE.saved, state.saved);
        renderSaved();
        showToast('Saved articles deleted');
      });
      break;
    case 'clear-history':
      askConfirm('Clear reading history?', 'Articles you opened will no longer appear in History.', 'Clear history').then((confirmed) => {
        if (!confirmed) return;
        state.history = [];
        writeJson(STORAGE.history, state.history);
        renderHistory();
        showToast('History cleared');
      });
      break;
    case 'clear-highlights':
      askConfirm('Delete all highlights?', 'Every saved passage will be removed from this device.', 'Delete all').then((confirmed) => {
        if (!confirmed) return;
        state.highlights = [];
        writeJson(STORAGE.highlights, state.highlights);
        renderHighlights();
        showToast('Highlights deleted');
      });
      break;
    case 'clear-tabs':
      askConfirm('Close all tabs?', 'Open article tabs will be cleared. Your reading history is kept.', 'Close all').then((confirmed) => {
        if (!confirmed) return;
        state.tabs = [];
        writeJson(STORAGE.tabs, state.tabs);
        renderTabs();
        showToast('Tabs closed');
      });
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
    case 'remove-highlight':
      state.highlights.splice(Number(element.dataset.index), 1);
      writeJson(STORAGE.highlights, state.highlights);
      renderHighlights();
      break;
    case 'remove-tab':
      state.tabs.splice(Number(element.dataset.index), 1);
      writeJson(STORAGE.tabs, state.tabs);
      renderTabs();
      break;
    case 'open-settings-category':
      location.hash = `#/settings/${element.dataset.category}`;
      break;
    case 'open-topics':
      location.hash = '#/settings/topics';
      break;
    case 'remove-custom-topic':
      removeCustomTopic(element.dataset.topic);
      renderSettings();
      break;
    case 'settings-home':
      location.hash = '#/settings';
      break;
    case 'set-setting':
      setSetting(element.dataset.setting, element.dataset.value);
      break;
    case 'clear-cache': clearCaches(); break;
    case 'export-data': exportData(); break;
    case 'import-data': $('#importFile')?.click(); break;
    case 'reset-stats':
      askConfirm('Reset reading statistics?', 'Article counts, words and reading time will be reset. Your library is kept.', 'Reset').then((confirmed) => {
        if (!confirmed) return;
        state.stats = { articlesRead: 0, wordsRead: 0, minutesRead: 0, sessions: 0, since: Date.now(), lastReadAt: 0 };
        writeJson(STORAGE.stats, state.stats);
        renderStats();
        showToast('Statistics reset');
      });
      break;
    case 'reset-settings':
      askConfirm('Reset settings?', 'Appearance, language, feed and reading settings will return to their defaults. Your saved articles and highlights are kept.', 'Reset').then((confirmed) => {
        if (!confirmed) return;
        const keep = new Set([STORAGE.saved, STORAGE.history, STORAGE.highlights, STORAGE.tabs, STORAGE.stats, STORAGE.searches]);
        for (const key of Object.values(STORAGE)) if (!keep.has(key)) localStorage.removeItem(key);
        location.reload();
      });
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
    case 'add-tab': saveTabForCurrentArticle(); break;
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
    case 'highlight-selection': saveSelectionHighlight(); break;
    case 'search-selection': openSelectionSearch(); break;
    case 'clear-selection': window.getSelection?.()?.removeAllRanges(); hideSelectionToolbar(); break;
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

  document.addEventListener('pointerdown', (event) => {
    if (event.target.closest('#selectionToolbar')) event.preventDefault();
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      closeDrawer();
      hideSelectionToolbar();
      if ($('#languageDialog')?.open) closeDialog();
      return;
    }
    if (event.key === '/' && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName || '')) {
      event.preventDefault();
      location.hash = '#/search';
      setTimeout(() => $('#searchInput')?.focus(), 120);
      return;
    }
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const actionElement = event.target.closest?.('[data-action]');
    if (actionElement && actionElement.tagName !== 'BUTTON' && actionElement.tagName !== 'A' && actionElement.tagName !== 'SELECT') {
      event.preventDefault();
      onAction(actionElement.dataset.action, actionElement);
    }
  });

  $('#searchForm')?.addEventListener('submit', (event) => {
    event.preventDefault();
    doSearch($('#searchInput')?.value || '');
  });
  document.addEventListener('submit', (event) => {
    if (event.target.id !== 'customTopicForm') return;
    event.preventDefault();
    const input = $('#customTopicInput');
    if (addCustomTopic(input?.value)) {
      renderSettings();
      $('#customTopicInput')?.focus();
    }
  });
  document.addEventListener('input', (event) => {
    const target = event.target;
    if (target.id === 'searchInput') {
      if (!target.value) {
        state.searchResults = null;
        state.searchError = '';
        renderSearch();
      }
    } else if (target.id === 'settingsSearch') {
      state.settingsQuery = target.value;
      renderSettings();
    } else if (target.id === 'fontSizeInput') {
      state.fontSize = clamp(Number(target.value) || 16, 10, 22);
      writeText(STORAGE.fontSize, String(state.fontSize));
      applyFontSize();
    } else if (target.id === 'languageFilter') {
      renderLanguageList(target.value);
    }
  });
  document.addEventListener('change', (event) => {
    const target = event.target;
    if (target.id === 'languageSelect') setLanguage(target.value);
    else if (target.id === 'fontSizeInput') haptic();
    else if (target.id === 'importFile') {
      const file = target.files?.[0];
      if (file) importData(file);
      target.value = '';
    }
  });
  $('#languageDialog')?.addEventListener('click', (event) => {
    if (event.target === event.currentTarget) closeDialog();
  });
  $('#confirmDialog')?.addEventListener('click', (event) => {
    if (event.target === event.currentTarget) {
      event.currentTarget.returnValue = 'cancel';
      event.currentTarget.close();
    }
  });
  document.addEventListener('selectionchange', () => {
    clearTimeout(selectionTimer);
    selectionTimer = setTimeout(updateSelectionToolbar, 80);
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
    if (state.theme === 'auto') applyAppearance();
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
  applyPreferences();
  setCoreStatus(coreStatus);
  updateOnlineBadge();
  ensureProgressBar();
  bindEvents();
  renderLanguageList();
  await loadCore();

  const params = new URLSearchParams(location.search);
  if (params.get('action') === 'random') {
    if (location.hash) history.replaceState(null, '', location.pathname + location.search);
    await openRandom();
  } else if (params.get('action') === 'search') {
    history.replaceState(null, '', location.pathname + location.search + '#/search');
    route();
    setTimeout(() => $('#searchInput')?.focus(), 250);
  } else {
    if (!location.hash) history.replaceState(null, '', location.pathname + location.search + '#/home');
    route();
  }

  updateCacheStats();
  registerServiceWorker();
}

init().catch((error) => {
  console.error('[pwa] startup failed', error);
  const main = $('#main');
  if (main) main.innerHTML = emptyState('WikiReader could not start', 'Reload the page. If this keeps happening, clear the site data and try again.', '', 'cloud');
});