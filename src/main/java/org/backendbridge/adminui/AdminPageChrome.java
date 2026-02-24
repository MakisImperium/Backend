package org.backendbridge.adminui;

import static org.backendbridge.adminui.AdminUiUtil.esc;

/**
 * Shared HTML chrome:
 * - inline CSS (no assets)
 * - inline JS (tables, copy-to-clipboard, role toggle saving)
 * - global timestamp formatting for DE/EN with official IANA timezones
 *
 * Important: timestamps are formatted client-side from elements with [data-iso] attributes.
 */
public final class AdminPageChrome {

    private AdminPageChrome() {}

    public static String pageStart(String title) {
        String html = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>__BB_TITLE__</title>
                <style>
                  :root{
                    --bgA:#4f46e5;
                    --bgB:#a855f7;
                    --text:#0f172a;
                    --shadow: 0 22px 70px rgba(2,6,23,.18);
                    --cardBg: rgba(255,255,255,.92);
                    --radius: 18px;
                    --ease: cubic-bezier(.22, 1, .36, 1);
                    --durFast: 140ms;
                    --dur: 260ms;

                    --btnShadow: 0 14px 34px rgba(2,6,23,.18);
                  }

                  *{box-sizing:border-box;}
                  html,body{height:100%;}
                  body{
                    margin:0;
                    font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",system-ui,Arial,sans-serif;
                    color: var(--text);
                    overflow-x:hidden;
                    background: linear-gradient(120deg, var(--bgA), var(--bgB));
                    -webkit-font-smoothing: antialiased;
                    -moz-osx-font-smoothing: grayscale;
                    text-rendering: optimizeLegibility;
                  }

                  /* Theme used on all non-login pages (appShellStart adds bb-statsTheme) */
                  body.bb-statsTheme{
                    background:
                      radial-gradient(1200px 600px at 10% 20%, rgba(34,197,94,.20), transparent 60%),
                      radial-gradient(1200px 600px at 90% 10%, rgba(59,130,246,.22), transparent 55%),
                      radial-gradient(1000px 520px at 70% 90%, rgba(168,85,247,.22), transparent 60%),
                      linear-gradient(120deg, #0b1020, #111a3a);
                    color: rgba(255,255,255,.92);
                  }
                  body.bb-statsTheme .hero,
                  body.bb-statsTheme .card{
                    background: rgba(255,255,255,.92);
                    color: rgba(15,23,42,.92);
                  }

                  a{color:inherit; text-decoration:none;}

                  svg{
                    width:16px;height:16px;flex:0 0 auto;
                    stroke: currentColor; fill:none; stroke-width:2;
                    stroke-linecap:round; stroke-linejoin:round; display:block;
                  }

                  .shell{max-width:1220px; margin:18px auto; padding:0 16px;}
                  .content{margin-top:16px;}

                  .bb-reveal{opacity:0; transform: translate3d(0,4px,0);
                    transition: opacity var(--dur) var(--ease), transform var(--dur) var(--ease); }
                  .bb-reveal.in{opacity:1; transform: translate3d(0,0,0); }

                  .top{display:flex; justify-content:space-between; align-items:center; gap:12px; padding:10px 0 14px; color: rgba(255,255,255,.92);}
                  .bb-serverBadge{display:inline-flex; align-items:center; gap:10px; padding:8px 12px; border-radius:999px;
                    background: rgba(255,255,255,.92); border: 1px solid rgba(255,255,255,.55); box-shadow: 0 14px 40px rgba(2,6,23,.14); color: rgba(15,23,42,.90);}
                  .bb-serverLogo{width:28px; height:28px; border-radius:10px; background: rgba(15,23,42,.04); border: 1px solid rgba(15,23,42,.08);
                    display:flex; align-items:center; justify-content:center; overflow:hidden;}
                  .bb-serverLogo svg{width:18px; height:18px;}
                  .bb-serverLogoImg{ width:100%; height:100%; object-fit:cover; display:block; }
                  .bb-serverName{font-weight:700; letter-spacing:.01em; font-size:13px;}

                  .menu{display:flex; gap:12px; flex-wrap:wrap; padding:10px 12px; border-radius:999px;
                    background: rgba(255,255,255,.14); border: 1px solid rgba(255,255,255,.22); backdrop-filter: blur(10px); color: rgba(255,255,255,.92);}
                  .tab{padding:10px 14px; border-radius:999px; display:inline-flex; gap:10px; align-items:center; border:1px solid transparent;}
                  .tab.active{background: rgba(255,255,255,.22); border-color: rgba(255,255,255,.30);}

                  .hero{
                    padding:22px 18px;
                    border-radius: var(--radius);
                    background: var(--cardBg);
                    border:1px solid rgba(15,23,42,.08);
                    box-shadow: var(--shadow);
                    text-align:center;
                  }
                  .hero h1{margin:0; font-size:28px;}
                  .bb-heroSub{opacity:.75; margin-top:6px; text-align:center;}

                  .card{margin-top:16px; border-radius: var(--radius); overflow:hidden; background: var(--cardBg); border:1px solid rgba(15,23,42,.08); box-shadow: var(--shadow);}
                  .cardHead{padding:14px 16px 10px; display:flex; justify-content:space-between; flex-wrap:wrap; gap:12px; align-items:center; border-bottom: 1px solid rgba(15,23,42,.06);}
                  .pad{padding:16px;}

                  .mono{font-family: ui-monospace, Consolas, monospace; font-size:12px; color: rgba(15,23,42,.75);}

                  .btn{
                    padding:10px 14px;
                    border-radius: 14px;
                    border:1px solid rgba(15,23,42,.14);
                    background: rgba(255,255,255,.86);
                    cursor:pointer;
                    display:inline-flex;
                    gap:10px;
                    align-items:center;
                    font-weight: 700;
                    transition: transform var(--durFast) var(--ease), box-shadow var(--durFast) var(--ease), filter var(--durFast) var(--ease);
                  }
                  .btn:hover{ transform: translateY(-1px); box-shadow: var(--btnShadow); }
                  .btn:active{ transform: translateY(0); filter: brightness(.98); }

                  .btn.primary{
                    border-color: rgba(59,130,246,.55);
                    background: linear-gradient(135deg, rgba(59,130,246,.96), rgba(99,102,241,.96));
                    color: #fff;
                  }
                  .btn.danger{
                    border-color: rgba(239,68,68,.45);
                    background: rgba(239,68,68,.14);
                    color: rgba(185,28,28,.95);
                  }
                  .btn.active{
                    border-color: rgba(34,197,94,.45);
                    background: rgba(34,197,94,.16);
                    color: rgba(21,128,61,.95);
                  }

                  .form{display:flex; flex-direction:column; gap:10px;}
                  .inp{padding:12px 12px; border-radius:12px; border:1px solid rgba(15,23,42,.10); background: rgba(255,255,255,.95); outline:none;}

                  .pill{padding:3px 10px; border-radius:999px; border:1px solid rgba(15,23,42,.12); font-size:12px; background: rgba(15,23,42,.04);}
                  .pill-ok{ border-color: rgba(34,197,94,.35); background: rgba(34,197,94,.12); color: rgba(21,128,61,.95); }
                  .pill-warn{ border-color: rgba(245,158,11,.35); background: rgba(245,158,11,.14); color: rgba(180,83,9,.95); }

                  .alert{
                    padding:12px 12px;
                    border-radius:14px;
                    border: 1px solid rgba(15,23,42,.12);
                    background: rgba(255,255,255,.85);
                    margin: 10px 0 12px;
                  }

                  .badge{
                    padding: 6px 10px;
                    border-radius: 999px;
                    border: 1px solid rgba(15,23,42,.10);
                    background: rgba(255,255,255,.70);
                  }

                  table{width:100%; border-collapse:separate; border-spacing:0;}
                  thead th{position:sticky; top:0; z-index:2; background: rgba(255,255,255,.92); border-bottom:1px solid rgba(15,23,42,.06);}
                  th,td{padding:12px 12px; border-bottom:1px solid rgba(15,23,42,.06); text-align:left; vertical-align:middle;}
                  th{font-size:12px; letter-spacing:.06em; text-transform:uppercase; color: rgba(15,23,42,.55); cursor:pointer;}
                  tbody tr:hover{ background: rgba(59,130,246,.06); }

                  .bb-tableTools{ display:flex; gap:10px; align-items:center; flex-wrap:wrap; justify-content:flex-end; }
                  .bb-mini{ padding:10px 12px; border-radius: 12px; border:1px solid rgba(15,23,42,.10); background: rgba(255,255,255,.95); font-size: 13px; outline: none; }

                  /* Toggle */
                  .bb-toggle{ position:relative; display:inline-flex; width:44px; height:24px; }
                  .bb-toggle input{ opacity:0; width:0; height:0; }
                  .bb-slider{ position:absolute; inset:0; border-radius:999px; background: rgba(15,23,42,.10); border:1px solid rgba(15,23,42,.12);
                    transition: background var(--durFast) var(--ease), border-color var(--durFast) var(--ease);
                  }
                  .bb-slider:before{
                    content:""; position:absolute; width:18px; height:18px; left:3px; top:50%;
                    transform: translateY(-50%); border-radius:999px; background:#fff; transition: transform var(--durFast) var(--ease);
                  }
                  .bb-toggle input:checked + .bb-slider{
                    background: rgba(34,197,94,.20);
                    border-color: rgba(34,197,94,.40);
                  }
                  .bb-toggle input:checked + .bb-slider:before{ transform: translate(20px, -50%); }

                  td.bb-permOn{
                    background: rgba(34,197,94,.10);
                    box-shadow: inset 0 0 0 1px rgba(34,197,94,.18);
                  }

                  /* ---------- STATS DASHBOARD LAYOUT (restored) ---------- */
                  .bb-dashGrid{
                    display:grid;
                    gap:12px;
                    grid-template-columns: repeat(12, minmax(0, 1fr));
                  }
                  .bb-gaugeCard{
                    grid-column: span 6;
                    border-radius: 16px;
                    border: 1px solid rgba(15,23,42,.08);
                    background: rgba(255,255,255,.92);
                    overflow:hidden;
                  }
                  .bb-kpiCard{
                    grid-column: span 3;
                    border-radius: 16px;
                    border: 1px solid rgba(15,23,42,.08);
                    background: rgba(255,255,255,.92);
                    padding: 14px 14px;
                  }
                  @media (max-width: 980px){
                    .bb-gaugeCard{ grid-column: span 12; }
                    .bb-kpiCard{ grid-column: span 6; }
                  }
                  @media (max-width: 640px){
                    .bb-kpiCard{ grid-column: span 12; }
                  }

                  .bb-gaugeTop{
                    display:flex;
                    align-items:center;
                    justify-content:space-between;
                    gap:10px;
                    padding: 12px 14px 8px;
                    border-bottom: 1px solid rgba(15,23,42,.06);
                  }
                  .bb-gaugeTitle{ font-weight: 800; letter-spacing:.01em; }
                  .bb-gaugeBadge{
                    font-family: ui-monospace, Consolas, monospace;
                    font-size: 12px;
                    padding: 4px 8px;
                    border-radius: 999px;
                    border: 1px solid rgba(15,23,42,.10);
                    background: rgba(255,255,255,.70);
                  }
                  .bb-gaugeRow{
                    display:flex;
                    align-items:center;
                    gap:14px;
                    padding: 12px 14px 14px;
                  }
                  .bb-gaugeValue{ display:flex; flex-direction:column; gap:4px; }
                  .bb-gaugeBig{ font-size: 26px; font-weight: 900; letter-spacing:.01em; }

                  .bb-kpiTitle{ font-weight: 800; opacity:.85; }
                  .bb-kpiBig{ font-size: 24px; font-weight: 900; margin-top: 6px; }

                  .bb-chartGrid{
                    margin-top: 12px;
                    display:grid;
                    gap:12px;
                    grid-template-columns: repeat(12, minmax(0,1fr));
                  }
                  .bb-chartCard{
                    grid-column: span 6;
                    border-radius: 16px;
                    border: 1px solid rgba(15,23,42,.08);
                    background: rgba(255,255,255,.92);
                    overflow:hidden;
                  }
                  @media (max-width: 980px){
                    .bb-chartCard{ grid-column: span 12; }
                  }
                  .bb-chartHead{
                    padding: 12px 14px 8px;
                    border-bottom: 1px solid rgba(15,23,42,.06);
                    display:flex;
                    align-items:center;
                    justify-content:space-between;
                    gap:10px;
                  }
                  .bb-chartCanvas{
                    width:100%;
                    height:auto;
                    display:block;
                  }

                  /* -------- LOGIN PAGE -------- */
                  .bb-loginWrap{
                    min-height: calc(100vh - 36px);
                    display:flex;
                    align-items:center;
                    justify-content:center;
                    padding: 18px;
                  }
                  .bb-loginCard{
                    width: min(980px, 100%);
                    display:grid;
                    grid-template-columns: 1.1fr 1fr;
                    border-radius: 24px;
                    overflow:hidden;
                    background: rgba(255,255,255,.92);
                    border:1px solid rgba(255,255,255,.55);
                    box-shadow: 0 26px 80px rgba(2,6,23,.22);
                  }
                  @media (max-width: 880px){
                    .bb-loginCard{ grid-template-columns: 1fr; }
                    .bb-loginLeft{ display:none; }
                  }
                  .bb-loginLeft{
                    padding: 28px;
                    background: linear-gradient(135deg, rgba(79,70,229,.92), rgba(168,85,247,.92));
                    color: rgba(255,255,255,.94);
                    display:flex;
                    align-items:center;
                    justify-content:center;
                  }
                  .bb-illus{ width: 100%; height: 100%; display:flex; align-items:center; justify-content:center; }
                  .bb-illusIcon{
                    width: 120px; height: 120px;
                    border-radius: 28px;
                    display:flex; align-items:center; justify-content:center;
                    background: rgba(255,255,255,.16);
                    border: 1px solid rgba(255,255,255,.24);
                    box-shadow: 0 18px 50px rgba(2,6,23,.22);
                  }
                  .bb-illusIcon svg{ width: 62px; height: 62px; stroke: rgba(255,255,255,.95); }

                  .bb-loginRight{ padding: 26px; display:flex; flex-direction:column; justify-content:center; }
                  .bb-loginTitle{ font-weight: 800; letter-spacing: .01em; font-size: 22px; margin-bottom: 6px; }

                  .bb-floatField{ position:relative; display:block; }
                  .bb-inputIcon{ position:absolute; left:12px; top:50%; transform: translateY(-50%); opacity:.70; }
                  .bb-floatField input{
                    width:100%;
                    padding: 14px 12px 14px 40px;
                    border-radius: 14px;
                    border:1px solid rgba(15,23,42,.12);
                    background: rgba(255,255,255,.96);
                    outline:none;
                  }
                  .bb-floatLabel{
                    position:absolute;
                    left: 40px;
                    top: 50%;
                    transform: translateY(-50%);
                    pointer-events:none;
                    opacity:.55;
                    transition: all var(--durFast) var(--ease);
                    font-size: 13px;
                  }
                  .bb-floatField input:focus + .bb-floatLabel,
                  .bb-floatField input:not(:placeholder-shown) + .bb-floatLabel{
                    top: 8px;
                    transform: none;
                    opacity:.70;
                    font-size: 12px;
                  }

                  .bb-loginBtn{
                    margin-top: 6px;
                    width: 100%;
                    padding: 12px 14px;
                    border-radius: 14px;
                    border: 1px solid rgba(59,130,246,.35);
                    background: rgba(59,130,246,.92);
                    color: #fff;
                    font-weight: 800;
                    letter-spacing: .08em;
                    cursor: pointer;
                  }

                  /* Toast */
                  .toast{
                    position:fixed; right:18px; bottom:18px;
                    padding:10px 12px; border-radius:12px;
                    border:1px solid rgba(15,23,42,.12);
                    background: rgba(255,255,255,.92);
                    opacity:0; transform: translateY(8px);
                    transition: opacity var(--durFast) var(--ease), transform var(--durFast) var(--ease);
                    pointer-events:none; z-index:9999;
                  }
                  .toast.on{opacity:1; transform: translateY(0);}
                </style>
              </head>
              <body>
                <div class="toast" id="bbToast"></div>
            """;
        return html.replace("__BB_TITLE__", esc(title));
    }

    public static String pageEndWithAppScript() {
        return """
              <script>
                (function(){
                  const toastEl = document.getElementById('bbToast');
                  function toast(msg){
                    if(!toastEl) return;
                    toastEl.textContent = msg;
                    toastEl.classList.add('on');
                    clearTimeout(toastEl._t);
                    toastEl._t = setTimeout(() => toastEl.classList.remove('on'), 1200);
                  }

                  // -------- Global time formatting for any element with [data-iso] --------
                  function fmtDateTime(iso){
                    if(!iso) return '';
                    const d = new Date(iso);
                    if(Number.isNaN(d.getTime())) return String(iso);

                    const lang = (document.documentElement.getAttribute('data-lang') || 'de').toLowerCase();
                    const tz = (lang === 'de') ? 'Europe/Berlin' : 'America/New_York';
                    const locale = (lang === 'de') ? 'de-DE' : 'en-US';

                    const dtf = new Intl.DateTimeFormat(locale, {
                      timeZone: tz,
                      year: 'numeric',
                      month: '2-digit',
                      day: '2-digit',
                      hour: '2-digit',
                      minute: '2-digit',
                      hour12: (lang !== 'de')
                    });

                    const parts = dtf.formatToParts(d);
                    const get = (type) => (parts.find(p => p.type === type)?.value || '');

                    const yyyy = get('year');
                    const MM = get('month');
                    const dd = get('day');
                    const hh = get('hour');
                    const mm = get('minute');
                    const ap = get('dayPeriod');

                    if(lang === 'de') return `${dd}.${MM}.${yyyy} ${hh}:${mm}`;
                    return `${yyyy}.${MM}.${dd} ${hh}:${mm} ${ap}`;
                  }

                  function formatAllIso(){
                    document.querySelectorAll('[data-iso]').forEach(el => {
                      try{
                        const iso = el.getAttribute('data-iso') || '';
                        if(!iso) return;
                        el.textContent = fmtDateTime(iso);
                      }catch(e){}
                    });
                  }

                  // Reveal
                  const revealEls = Array.from(document.querySelectorAll('.bb-reveal, .card, .hero, .bb-loginCard'));
                  if('IntersectionObserver' in window){
                    const io = new IntersectionObserver((entries) => {
                      for(const e of entries){
                        if(e.isIntersecting){
                          e.target.classList.add('in');
                          io.unobserve(e.target);
                        }
                      }
                    }, { threshold: 0.08 });
                    revealEls.forEach((el) => {
                      if(!el.classList.contains('bb-reveal')) el.classList.add('bb-reveal');
                      el.style.transitionDelay = '0ms';
                      io.observe(el);
                    });
                  }else{
                    revealEls.forEach(el => el.classList.add('in'));
                  }

                  // Apply time formatting
                  formatAllIso();

                  // Tables: sorting + filter + CSV
                  function detectType(sample){
                    const s = (sample ?? '').trim();
                    if(!s) return 'text';
                    if (/^-?\\d+(?:[.,]\\d+)?$/.test(s)) return 'num';
                    if (/^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}/.test(s)) return 'date';
                    return 'text';
                  }
                  function parseValue(type, td){
                    if(!td) return '';
                    const ds = td.getAttribute('data-sort');
                    if(ds != null && ds !== ''){
                      const n = Number(ds);
                      if(Number.isFinite(n)) return n;
                      const t = Date.parse(ds);
                      if(Number.isFinite(t)) return t;
                      return ds.toLowerCase();
                    }
                    const s = (td.textContent ?? '').trim();
                    if(type === 'num'){
                      const n = Number(s.replace(',', '.'));
                      return Number.isFinite(n) ? n : -Infinity;
                    }
                    if(type === 'date'){
                      const t = Date.parse(s);
                      return Number.isFinite(t) ? t : -Infinity;
                    }
                    return s.toLowerCase();
                  }
                  function toCsv(table){
                    const rows = Array.from(table.querySelectorAll('tr'))
                      .filter(tr => tr.style.display !== 'none');
                    return rows.map(tr => {
                      const cells = Array.from(tr.children).map(td => {
                        const txt = (td.textContent ?? '').trim().replace(/\\s+/g,' ');
                        const escaped = txt.replace(/"/g, '""');
                        return '"' + escaped + '"';
                      });
                      return cells.join(',');
                    }).join('\\n');
                  }
                  function initTable(table){
                    const tbody = table.querySelector('tbody');
                    const thead = table.querySelector('thead');
                    if(!tbody || !thead) return;

                    const headerCells = Array.from(thead.querySelectorAll('th'));
                    headerCells.forEach((th, idx) => {
                      th.addEventListener('click', () => {
                        const rows = Array.from(tbody.querySelectorAll('tr')).filter(tr => tr.style.display !== 'none');

                        let sample = '';
                        for(const tr of rows){
                          const td = tr.children[idx];
                          const t = (td?.textContent ?? '').trim();
                          if(t){ sample = t; break; }
                        }
                        const type = detectType(sample);

                        const currentlyAsc = th.classList.contains('sort-asc');
                        headerCells.forEach(h => { h.classList.remove('sort-asc'); h.classList.remove('sort-desc'); });

                        const asc = !currentlyAsc;
                        th.classList.add(asc ? 'sort-asc' : 'sort-desc');

                        rows.sort((a,b) => {
                          const av = parseValue(type, a.children[idx]);
                          const bv = parseValue(type, b.children[idx]);
                          if(av < bv) return asc ? -1 : 1;
                          if(av > bv) return asc ? 1 : -1;
                          return 0;
                        });

                        rows.forEach(tr => tbody.appendChild(tr));
                      });
                    });

                    const card = table.closest('.bb-tableCard') || table.closest('.card');
                    const tools = card ? card.querySelector('[data-bb-tabletools]') : null;

                    const search = document.createElement('input');
                    search.className = 'bb-mini';
                    search.placeholder = 'Search…';

                    const exportBtn = document.createElement('button');
                    exportBtn.className = 'btn';
                    exportBtn.type = 'button';
                    exportBtn.textContent = 'Download CSV';

                    const count = document.createElement('span');
                    count.className = 'mono';
                    count.style.opacity = '.75';

                    function updateCount(){
                      const all = Array.from(tbody.querySelectorAll('tr'));
                      const visible = all.filter(tr => tr.style.display !== 'none');
                      count.textContent = visible.length + ' / ' + all.length;
                    }
                    function applyFilter(){
                      const q = (search.value || '').trim().toLowerCase();
                      const rows = Array.from(tbody.querySelectorAll('tr'));
                      for(const tr of rows){
                        const text = (tr.textContent || '').toLowerCase();
                        tr.style.display = (!q || text.includes(q)) ? '' : 'none';
                      }
                      updateCount();
                    }
                    search.addEventListener('input', applyFilter);

                    exportBtn.addEventListener('click', () => {
                      const csv = toCsv(table);
                      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
                      const url = URL.createObjectURL(blob);
                      const a = document.createElement('a');
                      a.href = url;
                      a.download = (document.title || 'table') + '.csv';
                      document.body.appendChild(a);
                      a.click();
                      a.remove();
                      URL.revokeObjectURL(url);
                      toast('CSV exported');
                    });

                    if(tools){
                      tools.innerHTML = '';
                      tools.appendChild(search);
                      tools.appendChild(exportBtn);
                      tools.appendChild(count);
                    }
                    updateCount();
                  }
                  document.querySelectorAll('table').forEach(initTable);

                  // Copy-to-clipboard
                  document.addEventListener('click', async (ev) => {
                    const el = ev.target && ev.target.closest ? ev.target.closest('[data-copy]') : null;
                    if(!el) return;
                    const value = el.getAttribute('data-copy') || '';
                    try{
                      await navigator.clipboard.writeText(value);
                      toast('Copied');
                    }catch(e){
                      toast('Copy failed');
                    }
                  });

                  // Roles toggles (instant save) + green highlight for enabled cells
                  async function postForm(url, obj){
                    const body = new URLSearchParams();
                    for(const k in obj) body.set(k, String(obj[k]));
                    const r = await fetch(url, {
                      method: 'POST',
                      headers: { 'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8' },
                      body: body.toString()
                    });
                    if(!r.ok) throw new Error('HTTP ' + r.status);
                  }

                  function updatePermCellHighlight(cb){
                    const td = cb && cb.closest ? cb.closest('td') : null;
                    if(!td) return;
                    if(cb.checked) td.classList.add('bb-permOn');
                    else td.classList.remove('bb-permOn');
                  }

                  document.querySelectorAll('.bb-permTable input[type="checkbox"][data-role-id][data-perm-key]')
                    .forEach(cb => updatePermCellHighlight(cb));

                  document.addEventListener('change', async (ev) => {
                    const cb = ev.target;
                    if(!cb || cb.tagName !== 'INPUT' || cb.type !== 'checkbox') return;
                    const roleId = cb.getAttribute('data-role-id');
                    const permKey = cb.getAttribute('data-perm-key');
                    if(!roleId || !permKey) return;

                    updatePermCellHighlight(cb);

                    cb.disabled = true;
                    try{
                      await postForm('/admin/roles/perms/set', {
                        roleId: roleId,
                        permKey: permKey,
                        enabled: cb.checked ? '1' : '0'
                      });
                      toast('Saved');
                    }catch(e){
                      cb.checked = !cb.checked;
                      updatePermCellHighlight(cb);
                      toast('Save failed');
                    }finally{
                      cb.disabled = false;
                    }
                  });
                })();
              </script>
              </body>
            </html>
            """;
    }
}