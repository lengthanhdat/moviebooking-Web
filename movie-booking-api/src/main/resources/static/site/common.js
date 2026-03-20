const API = {
  moviesList: "/api/admin/movies/list?status=NOW_SHOWING",
  moviesAll: "/api/admin/movies/list?status=ALL",
  showtimes: (movieId) => `/api/showtimes?movieId=${movieId}`,
  seats: (showtimeId) => `/api/seats?showtimeId=${showtimeId}`,
  seatingsHold: "/api/seatings/hold",
  seatingsRelease: "/api/seatings/release",
  quote: `/api/bookings/quote`,
  booking: `/api/bookings`,
  login: `/api/auth/login`,
  register: `/api/auth/register`,
  changePassword: `/api/auth/change-password`,
  profile: `/api/profile`,
  myBookings: `/api/bookings/my`,
  trailer: (internalId) => `/api/admin/movies/${internalId}/trailer`,
  trailerFallback: (internalId) => `/api/admin/movies/${internalId}/trailers/fallback`,
  credits: (internalId) => `/api/admin/movies/${internalId}/credits`,
  movieDetail: (internalId) => `/api/admin/movies/${internalId}`
};

;(function(){ try{ const p=new URLSearchParams(location.search); const b=p.get('apiBase'); if(b){ localStorage.setItem('mb_api_base', b); } }catch{} })();
const API_BASE = (window.API_BASE || localStorage.getItem('mb_api_base') || '').trim();
function buildUrl(u){ try{ if(!API_BASE) return u; if(/^https?:\/\//.test(u)) return u; const base = API_BASE.replace(/\/+$/,''); const path = u.startsWith('/') ? u : '/' + u; return base + path; }catch{ return u; } }

API.movieCast = (internalId) => `/api/movies/${internalId}/cast`;
API.imageBaseUrl = `/api/movies/config/imageBaseUrl`;
  API.publicCast = (tmdbId) => `/api/movies/public/cast?movieId=${tmdbId}`;
  API.publicImageBase = `/api/movies/public/image-base`;
  API.publicDetail = (tmdbId) => `/api/movies/public/detail?movieId=${tmdbId}`;
  API.tmdbNowPlaying = (page=1, language='vi-VN', region='') => `/api/movies/public/tmdb/now-playing?page=${page}&language=${encodeURIComponent(language)}${region ? `&region=${encodeURIComponent(region)}` : ''}`;
  API.tmdbUpcoming = (page=1, language='vi-VN', region='') => `/api/movies/public/tmdb/upcoming?page=${page}&language=${encodeURIComponent(language)}${region ? `&region=${encodeURIComponent(region)}` : ''}`;
  API.publicNowPlaying = `/api/movies/public/now-playing`;
  API.publicUpcoming = `/api/movies/public/upcoming`;
  API.resolve = (tmdbId) => `/api/movies/public/resolve?tmdbId=${tmdbId}`;
  API.publicShowtimes = (internalId) => `/api/movies/public/showtimes?movieId=${internalId}`;
  API.publicVideos = (tmdbId) => `/api/movies/public/videos?movieId=${tmdbId}`;

function parseJwt(t){ try{ const p=t.split('.')[1]; const s=atob(p.replace(/-/g,'+').replace(/_/g,'/')); return JSON.parse(s); }catch(e){ return {}; } }
function getToken(){ return localStorage.getItem('mb_token'); }
function setToken(t){ if(t){ localStorage.setItem('mb_token', t); } else { localStorage.removeItem('mb_token'); } }
function getEmail(){ const t=getToken(); if(!t) return null; const p=parseJwt(t); return p.sub || p.email || null; }
async function fetchJson(url, opts={}){
  if(url == null || url === '') throw new Error('Invalid API endpoint');
  const headers = { "Content-Type": "application/json", "Accept": "application/json" };
  const t = getToken();
  if(t) headers["Authorization"] = `Bearer ${t}`;
  const res = await fetch(buildUrl(url), { headers, ...opts });
  const ct = res.headers.get("content-type") || "";
  if(!res.ok){
    let msg = "";
    if(ct.includes("application/json")){
      try{ const err = await res.json(); msg = err.message || err.error || err.detail || err.title || ""; }catch{}
    }
    if(!msg){
      try{ const text = await res.text(); msg = String(text||"").replace(/<[^>]*>/g,' ').replace(/\s+/g,' ').trim(); }catch{}
    }
    if(!msg){ msg = res.status === 401 ? 'Tài khoản hoặc mật khẩu chưa chính xác' : (res.statusText || 'Đã xảy ra lỗi'); }
    throw new Error(msg);
  }
  if(ct.includes("application/json")) return res.json();
  return res.text();
}
function toast(msg){ let el=document.getElementById('toast'); if(!el){ el=document.createElement('div'); el.id='toast'; el.className='toast'; document.body.appendChild(el); } el.textContent=msg; el.style.display='block'; setTimeout(()=>{ el.style.display='none'; }, 2500); }
function setFlash(msg){ try{ localStorage.setItem('mb_flash', String(msg||'')); }catch{} }
function consumeFlash(){ try{ const k='mb_flash'; const m=localStorage.getItem(k); if(m){ localStorage.removeItem(k); return m; } return null; }catch{ return null; } }
;(function(){ const m=consumeFlash(); if(m) toast(m); })();
function parseLocalDateTime(str){ try{ if(!str) return null; const s=String(str); if(/Z|[\+\-]\d{2}:?\d{2}$/.test(s)) return new Date(s); const m=s.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):?(\d{2})?$/); if(!m) return new Date(s); const yyyy=+m[1], MM=+m[2]-1, dd=+m[3], hh=+m[4], mm=+m[5], ss=m[6]?+m[6]:0; const d=new Date(); d.setFullYear(yyyy); d.setMonth(MM); d.setDate(dd); d.setHours(hh); d.setMinutes(mm); d.setSeconds(ss); d.setMilliseconds(0); return d; }catch{ return null; } }
function formatDisplay(iso){ const dt=parseLocalDateTime(iso)||new Date(iso); const hh=String(dt.getHours()).padStart(2,'0'); const mm=String(dt.getMinutes()).padStart(2,'0'); const dd=String(dt.getDate()).padStart(2,'0'); const MM=String(dt.getMonth()+1).padStart(2,'0'); const yyyy=dt.getFullYear(); return `${hh}:${mm} ${dd}/${MM}/${yyyy}`; }
function qs(){ const p=new URLSearchParams(location.search); const o={}; for(const [k,v] of p.entries()) o[k]=v; return o; }
function requireAuth(nextUrl){ if(!getToken()){ const next = String(nextUrl||location.href); location.href = `/site/auth.html?next=${encodeURIComponent(next)}`; return false; } return true; }

function injectBottomNav(){
}

function renderUser(){
  const area = document.getElementById('userArea'); if(!area) return;
  const t = getToken();
  if(!t){ area.innerHTML = `<button class="primary" onclick="location.href='/site/auth.html?next='+encodeURIComponent(location.href)">Đăng nhập</button>`; return; }
  fetchJson(API.profile).then(p=>{
    const email = p.email || getEmail() || '';
    const display = p.fullName || p.name || email;
    const wrap = document.createElement('div'); wrap.className='user-chip-wrap';
    const inline = document.createElement('div'); inline.className='user-inline';
    const av = document.createElement('div'); av.className='avatar small'; av.textContent='👤';
    const mail = document.createElement('div'); mail.className='user-email'; mail.textContent = display;
    inline.appendChild(av); inline.appendChild(mail);
    const menu = document.createElement('div'); menu.className='user-menu';
    const mk = (icon, text, handler, active=false)=>{ const it=document.createElement('div'); it.className='item' + (active?' active':''); const ic=document.createElement('div'); ic.className='icon'; ic.textContent=icon; const tx=document.createElement('div'); tx.textContent=text; it.appendChild(ic); it.appendChild(tx); it.onclick=handler; return it; };
    menu.appendChild(mk('🔒','Tài Khoản', ()=>{ location.href='/site/account.html'; }, true));
    menu.appendChild(mk('↩','Đăng Xuất', ()=>{ setToken(null); renderUser(); toast('Đăng xuất thành công, hẹn gặp lại <3'); }));
    wrap.appendChild(inline); wrap.appendChild(menu); area.innerHTML=''; area.appendChild(wrap);
    inline.onclick = (e)=>{ e.stopPropagation(); menu.classList.toggle('show'); };
    document.addEventListener('click', ()=>{ menu.classList.remove('show'); }, { once:true });
  }).catch(()=>{ area.innerHTML = `<button class="primary" onclick="location.href='/site/auth.html?next='+encodeURIComponent(location.href)">Đăng nhập</button>`; });
}

function wireHeaderSearch(){
  const input = document.getElementById('search');
  if(!input) return;
  const btn = document.getElementById('btnSearch') || document.querySelector('.searchbar .icon');
  const handler = ()=>{ const q=(input.value||'').trim(); location.href = '/site/categories.html?q=' + encodeURIComponent(q); };
  if(btn) btn.onclick = handler;
  input.addEventListener('keyup', e=>{ if(e.key==='Enter') handler(); });
}

function wireGoHome(){
  document.addEventListener('click', (e)=>{
    const el = e.target.closest('[data-go-home], header .brand, footer .brand');
    if(el){ e.preventDefault(); location.href='/site/home.html'; }
  });
}

;(function(){ const run=()=>{ try{ wireHeaderSearch(); renderUser(); wireGoHome(); }catch{} }; if(document.readyState==='loading'){ document.addEventListener('DOMContentLoaded', run); } else { run(); } })();
