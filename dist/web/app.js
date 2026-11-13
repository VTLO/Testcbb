/* VTLOgenerat0r — PC web app. Vanilla JS, no build step, no dependencies.
   Mirrors the Android app: dork generation, auto-check (Google Custom Search
   JSON API with your own key), chaining, and a feedback-weighted generative
   engine. State persists in localStorage. For authorized bug-bounty / pentest
   recon only. Author: VTLO. */
(function () {
  "use strict";

  // ── Seed dorks (ported 1:1 from scripts/dork_runner.py) ───────────────────
  var CATEGORIES = {
    credentials: [
      'site:{target} ext:env', 'site:{target} ext:env "DB_PASSWORD"', 'site:{target} ext:env "API_KEY"',
      'site:{target} "api_key" OR "apikey"', 'site:{target} "secret_key" OR "SECRET_KEY"',
      'site:{target} "password" filetype:log', 'site:{target} "password" filetype:txt',
      'site:{target} ext:yaml "password:"', 'site:{target} ext:json "private_key"',
      'site:{target} inurl:".git/config"', 'site:{target} ext:pem "BEGIN RSA PRIVATE KEY"',
      'site:{target} "aws_secret_access_key"', 'site:{target} "AKIA" intext:AKIA',
      'site:{target} ext:json "type: service_account"'
    ],
    pii: [
      'site:{target} ext:csv intext:"email" intext:"phone"', 'site:{target} ext:xls intext:"ssn"',
      'site:{target} ext:xlsx intext:"date of birth"', 'site:{target} ext:csv "first name" "last name" "email"',
      'site:{target} filetype:csv "password" "username"', 'site:{target} intitle:"index of" "users.csv"',
      'site:{target} intitle:"index of" "customers.csv"', 'site:{target} ext:log intext:"email"',
      'site:{target} filetype:xls "employee" "salary"'
    ],
    admin: [
      'site:{target} inurl:admin', 'site:{target} inurl:/admin/login', 'site:{target} inurl:/phpmyadmin',
      'site:{target} inurl:/jenkins', 'site:{target} inurl:/grafana', 'site:{target} inurl:/kibana',
      'site:{target} inurl:/actuator', 'site:{target} inurl:/swagger-ui', 'site:{target} inurl:/api-docs',
      'site:{target} intitle:"admin panel"', 'site:{target} intitle:"control panel"', 'site:{target} inurl:"/wp-login.php"'
    ],
    errors: [
      'site:{target} "SQL syntax" OR "mysql_fetch"', 'site:{target} "Warning: mysql_"',
      'site:{target} "Fatal error:" filetype:php', 'site:{target} "Stack trace:" filetype:html',
      'site:{target} "Traceback (most recent call last)"', 'site:{target} "NullPointerException"',
      'site:{target} "DEBUG = True" filetype:py', 'site:{target} "APP_DEBUG=true" ext:env',
      'site:{target} inurl:phpinfo.php', 'site:{target} ext:log "error"', 'site:{target} intitle:"index of" "error.log"'
    ],
    cloud: [
      '"{target}" site:s3.amazonaws.com', '"{target}" site:blob.core.windows.net',
      '"{target}" site:storage.googleapis.com', '"{target}" site:firebaseio.com',
      '{target}.s3.amazonaws.com', 'intitle:"index of" site:{target}'
    ],
    subdomains: [
      'site:*.{target}', 'site:*.*.{target}', 'site:*.{target} inurl:login', 'site:*.{target} inurl:admin',
      'site:*.{target} inurl:api', 'site:*.{target} inurl:staging', 'site:*.{target} inurl:dev', 'site:*.{target} inurl:test'
    ],
    params: [
      'site:{target} inurl:url=http', 'site:{target} inurl:redirect=http', 'site:{target} inurl:next=http',
      'site:{target} inurl:?id=', 'site:{target} inurl:?user_id=', 'site:{target} inurl:search=',
      'site:{target} inurl:q=', 'site:{target} inurl:file=', 'site:{target} inurl:path=',
      'site:{target} inurl:include=', 'site:{target} inurl:page=', 'site:{target} inurl:debug='
    ],
    leaks: [
      'site:pastebin.com "{target}"', 'site:pastebin.com "{target}" "password"', 'site:github.com "{target}" "password"',
      'site:github.com "{target}" "api_key"', 'site:github.com "{target}" ".env"', 'site:gist.github.com "{target}"',
      'site:notion.so "{target}"', 'site:docs.google.com "{target}"', 'site:trello.com "{target}"'
    ],
    github: [
      'site:github.com "{target}" "password"', 'site:github.com "{target}" "api_key"', 'site:github.com "{target}" "secret"',
      'site:github.com "{target}" "token"', 'site:github.com "{target}" extension:env',
      'site:github.com "{target}" filename:config.yml', 'site:github.com "{target}" filename:.env',
      'site:github.com "{target}" "BEGIN RSA PRIVATE KEY"'
    ],
    juicy: [
      'site:{target} intitle:"index of" "backup"', 'site:{target} intitle:"index of" "sql"',
      'site:{target} intitle:"index of" "dump"', 'site:{target} ext:sql', 'site:{target} ext:bak',
      'site:{target} ext:old', 'site:{target} inurl:backup', 'site:{target} filetype:pdf "confidential"',
      'site:{target} filetype:pdf "internal use only"'
    ]
  };
  var CATEGORY_KEYS = Object.keys(CATEGORIES).concat(['all']);

  // ── Generator pools (feedback-weighted) ───────────────────────────────────
  var GEN_PATTERNS = {
    credentials: ['site:{target} ext:{ext} "{kw}"', 'site:{target} "{kw}" filetype:{ext}', 'site:{target} intext:"{kw}"', 'site:{target} inurl:{kw}'],
    pii: ['site:{target} ext:{ext} intext:"{kw}"', 'site:{target} filetype:{ext} "{kw}"', 'site:{target} intitle:"index of" "{kw}"'],
    admin: ['site:{target} inurl:{kw}', 'site:{target} intitle:"{kw}"', 'site:{target} inurl:/{kw}/login'],
    errors: ['site:{target} "{kw}" filetype:{ext}', 'site:{target} intext:"{kw}"'],
    cloud: ['"{target}" site:{host}', 'site:{target} inurl:{kw}'],
    subdomains: ['site:*.{target} inurl:{kw}', 'site:*.{target} intitle:"{kw}"'],
    params: ['site:{target} inurl:{kw}=', 'site:{target} inurl:"{kw}="'],
    leaks: ['site:{host} "{target}" "{kw}"', 'site:{host} "{target}"'],
    github: ['site:github.com "{target}" "{kw}"', 'site:github.com "{target}" filename:{kw}'],
    juicy: ['site:{target} intitle:"index of" "{kw}"', 'site:{target} ext:{ext}', 'site:{target} inurl:{kw}']
  };
  var GEN_KW = {
    credentials: ['client_secret', 'access_token', 'bearer', 'jdbc:mysql', 'DB_USER', 'SMTP_PASSWORD', 'REDIS_URL', 'connectionstring', 'private_token', 'id_rsa', 'htpasswd', 'credentials', 'vault_token', 'GITHUB_TOKEN'],
    pii: ['passport', 'national id', 'dob', 'home address', 'credit card', 'iban', 'medical record', 'patient', 'tax id'],
    admin: ['dashboard', 'console', 'manager', 'cpanel', 'webmin', 'portainer', 'rundeck', 'airflow', 'superset', 'adminer'],
    errors: ['undefined index', 'ORA-00933', 'psql: error', 'unhandled exception', 'whoops', 'symfony profiler', 'django.core.exceptions'],
    cloud: ['backup', 'dump', 'export', 'assets', 'uploads'],
    subdomains: ['vpn', 'jira', 'gitlab', 'mail', 'internal', 'uat', 'qa'],
    params: ['return', 'callback', 'dest', 'continue', 'data', 'doc', 'template'],
    leaks: ['password', 'api_key', 'token', 'secret', 'dump', 'leak'],
    github: ['config.json', 'credentials', '.npmrc', 'docker-compose.yml', 'secrets'],
    juicy: ['db', 'archive', 'staging', 'private', 'secret']
  };
  var GEN_EXT = ['env', 'yml', 'yaml', 'ini', 'conf', 'config', 'json', 'txt', 'log', 'bak', 'sql'];
  var GEN_HOST = ['s3.amazonaws.com', 'blob.core.windows.net', 'storage.googleapis.com', 'firebaseio.com', 'digitaloceanspaces.com', 'pastebin.com', 'gitlab.com', 'bitbucket.org', 'gist.github.com'];

  // ── Persistent state ──────────────────────────────────────────────────────
  var LS = 'vtlo_state_v1';
  var state = load();
  function load() {
    try { var s = JSON.parse(localStorage.getItem(LS)); if (s) return s; } catch (e) {}
    return { stats: {}, generated: [], seen: {}, onboarded: false, apiKey: '', cx: '' };
  }
  function save() { try { localStorage.setItem(LS, JSON.stringify(state)); } catch (e) {} }

  // ── Core helpers ──────────────────────────────────────────────────────────
  function googleUrl(d) { return 'https://www.google.com/search?q=' + encodeURIComponent(d) + '&num=50'; }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function pick(a) { return a[Math.floor(Math.random() * a.length)]; }

  var dorks = [];   // {category, dork, template}
  var results = {}; // index -> {count, topLink, error}
  var target = '';

  function templatesFor(cat) {
    if (cat === 'all') {
      var out = [];
      for (var k in CATEGORIES) out = out.concat(CATEGORIES[k].map(function (t) { return { c: k, t: t }; }));
      return out;
    }
    return (CATEGORIES[cat] || []).map(function (t) { return { c: cat, t: t }; });
  }

  // ── Actions ───────────────────────────────────────────────────────────────
  var app = {};

  app.generate = function () {
    var t = document.getElementById('domain').value.trim();
    if (!t) { status('Enter a target domain.'); return; }
    target = t;
    var cat = document.getElementById('category').value;
    var rows = templatesFor(cat).slice();
    // include learned/generated dorks for this category
    state.generated.forEach(function (g) {
      if (cat === 'all' || g.category === cat) rows.push({ c: g.category, t: g.template });
    });
    dorks = rows.map(function (r) { var d = r.t.replace(/\{target\}/g, t); return { category: r.c, dork: d, template: r.t }; });
    results = {};
    render();
    status(dorks.length + ' dorks generated for ' + t);
  };

  app.generateNew = function () {
    var created = [];
    var weights = categoryWeights();
    var attempts = 0;
    while (created.length < 12 && attempts < 480) {
      attempts++;
      var cat = weightedPick(Object.keys(GEN_PATTERNS), weights);
      var tpl = pick(GEN_PATTERNS[cat]).replace('{kw}', pick(GEN_KW[cat]) || 'config')
        .replace('{ext}', pick(GEN_EXT)).replace('{host}', pick(GEN_HOST));
      if (state.seen[tpl]) continue;
      state.seen[tpl] = 1;
      state.generated.push({ template: tpl, category: cat });
      created.push({ category: cat, template: tpl });
    }
    save();
    if (!created.length) { status('No new unique dorks this round — try again.'); return; }
    var t = target || 'example.com'; target = t;
    dorks = created.map(function (r) { var d = r.template.replace(/\{target\}/g, t); return { category: r.category, dork: d, template: r.template }; });
    results = {};
    render();
    status('+' + created.length + ' new dorks · DB now ' + (totalTemplates()) + ' templates (' + state.generated.length + ' generated).');
  };

  app.autoCheckAll = function () {
    if (!dorks.length) { status('Generate dorks first.'); return; }
    if (!state.apiKey || !state.cx) { app.openSettings(); return; }
    var cap = Math.min(dorks.length, 50), hits = 0, i = 0;
    status('Auto-checking ' + cap + ' dorks via Search API…');
    (function step() {
      if (i >= cap) { status('Auto-check done: ' + hits + '/' + cap + ' returned results.'); return; }
      checkOne(i).then(function (res) {
        if (res.count > 0) hits++;
        i++;
        render();
        setTimeout(step, 300);
      });
    })();
  };

  function checkOne(i) {
    var d = dorks[i];
    var url = 'https://www.googleapis.com/customsearch/v1?key=' + encodeURIComponent(state.apiKey) +
      '&cx=' + encodeURIComponent(state.cx) + '&num=1&q=' + encodeURIComponent(d.dork);
    return fetch(url).then(function (r) { return r.json().then(function (j) { return { ok: r.ok, j: j }; }); })
      .then(function (o) {
        var res;
        if (!o.ok) { res = { count: 0, topLink: '', error: 'API error: ' + ((o.j.error && o.j.error.message) || 'HTTP') }; }
        else {
          var count = parseInt((o.j.searchInformation && o.j.searchInformation.totalResults) || '0', 10) || 0;
          var top = (o.j.items && o.j.items[0] && o.j.items[0].link) || '';
          res = { count: count, topLink: top, error: null };
        }
        results[i] = res;
        recordRun(d.template || d.dork, res.count);
        return res;
      })
      .catch(function (e) { var res = { count: 0, topLink: '', error: 'network/CORS error' }; results[i] = res; return res; });
  }

  app.check = function (i) {
    if (!state.apiKey || !state.cx) { app.openSettings(); return; }
    checkOne(i).then(render);
  };

  app.chain = function (i) {
    var res = results[i];
    if (!res || !res.topLink) { status('Check this dork first to chain from its result.'); return; }
    var host = ''; try { host = new URL(res.topLink).host.toLowerCase(); } catch (e) {}
    var scope = (host && host.indexOf(target) >= 0) ? host : target;
    var follow = [];
    follow.push('site:' + scope + ' intitle:"index of"');
    follow.push('site:' + scope + ' ext:env');
    follow.push('site:' + scope + ' ext:sql OR ext:bak OR ext:old');
    follow.push('site:' + scope + ' inurl:admin OR inurl:login');
    ['s3.amazonaws.com', 'storage.googleapis.com', 'blob.core.windows.net'].forEach(function (b) {
      if (host && host.indexOf(b) >= 0) { follow.push('site:' + host + ' "' + target + '"'); }
    });
    if (res.topLink.indexOf('?') >= 0 && res.topLink.indexOf('=') >= 0) {
      follow.push('site:' + scope + ' inurl:redirect= OR inurl:url= OR inurl:next=');
      follow.push('site:' + scope + ' inurl:file= OR inurl:path= OR inurl:include=');
    }
    var uniq = follow.filter(function (v, k, a) { return a.indexOf(v) === k; });
    var newDorks = uniq.map(function (d) { return { category: 'chain', dork: d, template: d }; });
    // re-key the results map for the indices that shift, then insert after i
    var shifted = {};
    Object.keys(results).forEach(function (k) { k = +k; shifted[k >= i + 1 ? k + newDorks.length : k] = results[k]; });
    results = shifted;
    dorks.splice.apply(dorks, [i + 1, 0].concat(newDorks));
    render();
    status('Added ' + newDorks.length + ' chained dork(s).');
  };

  app.share = function () {
    if (!dorks.length) return;
    var txt = dorks.map(function (d) { return '# [' + d.category + ']\nDORK: ' + d.dork + '\nURL:  ' + googleUrl(d.dork) + '\n'; }).join('\n');
    var blob = new Blob([txt], { type: 'text/plain' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'dorks_' + (target || 'target').replace(/\./g, '_') + '.txt';
    a.click();
  };

  // ── Learning loop ─────────────────────────────────────────────────────────
  function recordRun(template, count) {
    var cat = catOfTemplate(template);
    if (!cat) return;
    var s = state.stats[cat] || { runs: 0, hits: 0 };
    s.runs++; if (count > 0) s.hits++;
    state.stats[cat] = s; save();
  }
  function catOfTemplate(tpl) {
    for (var k in CATEGORIES) if (CATEGORIES[k].indexOf(tpl) >= 0) return k;
    for (var g = 0; g < state.generated.length; g++) if (state.generated[g].template === tpl) return state.generated[g].category;
    // fallback: look up current dorks
    for (var i = 0; i < dorks.length; i++) if (dorks[i].template === tpl || dorks[i].dork === tpl) return dorks[i].category;
    return null;
  }
  function categoryWeights() {
    var w = {};
    for (var k in CATEGORIES) { var s = state.stats[k]; w[k] = s ? (s.hits + 1) / (s.runs + 2) : 0.5; }
    return w;
  }
  function weightedPick(keys, w) {
    var total = keys.reduce(function (a, k) { return a + (w[k] || 0.5); }, 0);
    var r = Math.random() * total;
    for (var i = 0; i < keys.length; i++) { r -= (w[keys[i]] || 0.5); if (r <= 0) return keys[i]; }
    return keys[keys.length - 1];
  }
  function totalTemplates() { var n = 0; for (var k in CATEGORIES) n += CATEGORIES[k].length; return n + state.generated.length; }

  // ── Settings ──────────────────────────────────────────────────────────────
  app.openSettings = function () {
    document.getElementById('apiKey').value = state.apiKey || '';
    document.getElementById('cx').value = state.cx || '';
    document.getElementById('settings').classList.add('on');
  };
  app.closeSettings = function () { document.getElementById('settings').classList.remove('on'); };
  app.saveSettings = function () {
    state.apiKey = document.getElementById('apiKey').value.trim();
    state.cx = document.getElementById('cx').value.trim();
    save(); app.closeSettings(); status('Settings saved.'); render();
  };

  // ── Report render ─────────────────────────────────────────────────────────
  function status(m) { document.getElementById('status').textContent = m; }
  function render() {
    var el = document.getElementById('report');
    if (!dorks.length) { el.innerHTML = ''; return; }
    var configured = !!(state.apiKey && state.cx);
    var h = '';
    h += '<div class="count">Target: <strong style="color:#C77E36">' + esc(target) + '</strong> · ' + dorks.length + ' dorks</div>';
    if (configured) h += '<div class="bar"><a onclick="app.autoCheckAll()">⚡ Auto-check all (official Search API)</a></div>';
    else h += '<div class="bar count">Tip: add a Google Custom Search API key in Settings to auto-check which dorks return results.</div>';
    var cur = '';
    dorks.forEach(function (item, i) {
      if (item.category !== cur) { cur = item.category; h += '<h2>📁 ' + esc(cur) + '</h2>'; }
      var res = results[i];
      var cls = !res ? 'dork' : (res.count > 0 ? 'dork hit' : 'dork miss');
      h += '<div class="' + cls + '">';
      h += '<a href="' + esc(googleUrl(item.dork)) + '" target="_blank" rel="noopener">🔗 ' + esc(item.dork) + '</a>';
      if (res) {
        if (res.error) h += '<span class="badge miss">' + esc(res.error) + '</span>';
        else if (res.count > 0) {
          h += '<span class="badge hit">' + res.count + ' hit(s)</span>';
          if (res.topLink) h += '<a class="top" href="' + esc(res.topLink) + '" target="_blank" rel="noopener">' + esc(res.topLink) + '</a>';
        } else h += '<span class="badge miss">0</span>';
      }
      h += '<div class="actions"><a onclick="app.check(' + i + ')">✓ check</a>';
      if (res && res.count > 0 && res.topLink) h += '<a onclick="app.chain(' + i + ')">⛓ chain</a>';
      h += '</div></div>';
    });
    el.innerHTML = h;
  }

  // ── Onboarding (French, animated) ─────────────────────────────────────────
  var SLIDES = [
    { art: svgIntro(), h: '<span class="ac">VTLO</span>generat0r', p: 'Reconnaissance par <strong>Google dorking</strong>. Trouvez ce qui est exposé sur une cible — avant les autres.', warn: '⚠ Cibles autorisées uniquement · bug bounty / pentest' },
    { art: svgList(), h: '1 · <span class="ac">Générer</span>', p: "Saisissez un domaine cible, choisissez une catégorie — identifiants, admin, fuites, cloud… — puis cliquez <strong>Generate</strong>. L'app crée des requêtes Google prêtes à cliquer." },
    { art: svgCheck(), h: '2 · <span class="ac">Auto-check</span>', p: 'Ajoutez votre clé <strong>Google Custom Search</strong> dans Settings. L\'app teste chaque dork et marque en vert ceux qui renvoient réellement des résultats.' },
    { art: svgChain(), h: '3 · <span class="ac">Chaînage</span>', p: 'Depuis un résultat trouvé, cliquez <strong>⛓ chain</strong> pour générer des dorks de suivi et creuser la piste — sous-domaine, bucket cloud, paramètres.' },
    { art: svgSpark(), h: '4 · <span class="ac">Nouveaux dorks</span>', p: 'Le moteur génère de nouveaux dorks originaux et <strong>apprend</strong> lesquels fonctionnent, puis les enregistre pour la prochaine fois.' },
    { art: svgReady(), h: 'Prêt ?', p: "Chassez de façon responsable. N'utilisez l'outil que sur des cibles que vous êtes autorisé à tester." }
  ];
  var onbIdx = 0, onbTimer = null;
  app.showGuide = function () { onbIdx = 0; buildOnb(); document.getElementById('onb').classList.add('on'); renderOnb(); };
  function buildOnb() {
    var dots = SLIDES.map(function (_, k) { return '<div class="dot' + (k === 0 ? ' on' : '') + '"></div>'; }).join('');
    var slides = SLIDES.map(function (s, k) {
      return '<section class="slide' + (k === 0 ? ' on' : '') + '"><div class="art">' + s.art + '</div><h1>' + s.h + '</h1><p>' + s.p + '</p>' + (s.warn ? '<p class="warn">' + s.warn + '</p>' : '') + '</section>';
    }).join('');
    document.getElementById('onb').innerHTML =
      '<header><div style="display:flex;align-items:center;gap:10px;font-weight:bold"><span class="logo">V</span>VTLOgenerat0r</div>' +
      '<button class="skip" onclick="app.finishGuide()">Passer ✕</button></header>' +
      '<div class="dots">' + dots + '</div>' +
      '<main>' + slides + '</main>' +
      '<footer><button class="next" id="onbNext" onclick="app.onbAdvance()">Suivant</button></footer>';
  }
  function renderOnb() {
    var onb = document.getElementById('onb');
    var slides = onb.querySelectorAll('.slide'), dots = onb.querySelectorAll('.dot');
    slides.forEach(function (s, k) { s.classList.toggle('on', k === onbIdx); });
    dots.forEach(function (d, k) { d.classList.toggle('on', k === onbIdx); });
    document.getElementById('onbNext').textContent = (onbIdx === SLIDES.length - 1) ? 'Commencer' : 'Suivant';
    clearTimeout(onbTimer); onbTimer = setTimeout(app.onbAdvance, 6000);
  }
  app.onbAdvance = function () { if (onbIdx < SLIDES.length - 1) { onbIdx++; renderOnb(); } else { app.finishGuide(); } };
  app.finishGuide = function () { clearTimeout(onbTimer); document.getElementById('onb').classList.remove('on'); state.onboarded = true; save(); };

  function svgIntro() { return '<svg width="120" height="120" viewBox="0 0 120 120" fill="none"><circle class="ring" cx="60" cy="60" r="50" stroke="#294863" stroke-width="2" stroke-dasharray="6 10"/><g class="pulse"><circle cx="54" cy="54" r="24" stroke="#C77E36" stroke-width="5" fill="none"/><line x1="72" y1="72" x2="92" y2="92" stroke="#C77E36" stroke-width="6" stroke-linecap="round"/></g></svg>'; }
  function svgList() { return '<svg width="150" height="120" viewBox="0 0 150 120" fill="none"><rect x="15" y="12" width="120" height="22" rx="5" stroke="#294863" stroke-width="2"/><rect class="float" x="15" y="46" width="120" height="16" rx="4" fill="#122A41" stroke="#C77E36" stroke-width="1.5"/><rect x="15" y="68" width="120" height="16" rx="4" fill="#122A41" stroke="#294863" stroke-width="1.5"/><rect x="15" y="90" width="120" height="16" rx="4" fill="#122A41" stroke="#294863" stroke-width="1.5"/></svg>'; }
  function svgCheck() { return '<svg width="140" height="120" viewBox="0 0 140 120" fill="none"><g transform="translate(20,18)"><circle class="pulse" cx="12" cy="12" r="11" stroke="#4FB477" stroke-width="2.5"/><path d="M6 12 l4 4 l7 -8" stroke="#4FB477" stroke-width="3" fill="none" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="45" r="11" stroke="#4FB477" stroke-width="2.5"/><path d="M6 45 l4 4 l7 -8" stroke="#4FB477" stroke-width="3" fill="none" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="78" r="11" stroke="#294863" stroke-width="2.5"/><rect x="34" y="4" width="78" height="16" rx="4" fill="#122A41"/><rect x="34" y="37" width="78" height="16" rx="4" fill="#122A41"/><rect x="34" y="70" width="60" height="16" rx="4" fill="#0f2235"/></g></svg>'; }
  function svgChain() { return '<svg width="150" height="120" viewBox="0 0 150 120" fill="none"><path d="M30 30 L75 60 L120 40 M75 60 L70 95" stroke="#C77E36" stroke-width="2.5" fill="none"/><circle class="float" cx="30" cy="30" r="10" fill="#122A41" stroke="#4FB477" stroke-width="2.5"/><circle cx="75" cy="60" r="9" fill="#122A41" stroke="#C77E36" stroke-width="2.5"/><circle cx="120" cy="40" r="8" fill="#122A41" stroke="#C77E36" stroke-width="2.5"/><circle cx="70" cy="95" r="8" fill="#122A41" stroke="#C77E36" stroke-width="2.5"/></svg>'; }
  function svgSpark() { return '<svg width="130" height="120" viewBox="0 0 130 120" fill="none"><g transform="translate(65,58)"><path class="spark" d="M0 -34 L7 -7 L34 0 L7 7 L0 34 L-7 7 L-34 0 L-7 -7 Z" fill="#C77E36"/><circle cx="0" cy="0" r="6" fill="#F4F6F9"/></g><circle class="spark" cx="26" cy="26" r="3" fill="#4FB477"/><circle class="spark" cx="104" cy="90" r="3" fill="#4FB477"/></svg>'; }
  function svgReady() { return '<svg width="120" height="120" viewBox="0 0 120 120" fill="none"><circle class="pulse" cx="60" cy="60" r="40" fill="#122A41" stroke="#4FB477" stroke-width="3"/><path d="M44 60 l11 11 l22 -24" stroke="#4FB477" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>'; }

  // ── Init ──────────────────────────────────────────────────────────────────
  window.app = app;
  document.addEventListener('DOMContentLoaded', function () {
    var sel = document.getElementById('category');
    CATEGORY_KEYS.forEach(function (k) { var o = document.createElement('option'); o.value = k; o.textContent = k; sel.appendChild(o); });
    sel.value = 'all';
    if (!state.onboarded) app.showGuide();
  });
})();
