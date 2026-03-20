(() => {
  const links = [
    { href: '/admin/index.html', text: 'Doanh thu', icon: 'fa-chart-line' },
    { href: '/admin/showtimes.html', text: 'Quản lý suất chiếu', icon: 'fa-clock' },
    { href: '/admin/seats.html', text: 'Quản lý ghế', icon: 'fa-couch' },
    { href: '/admin/movies.html', text: 'Quản lý phim', icon: 'fa-film' },
    { href: '/admin/cinemas.html', text: 'Quản lý rạp', icon: 'fa-building' },
    { href: '/admin/bookings.html', text: 'Quản lý vé & đặt vé', icon: 'fa-ticket-alt' },
    { href: '/admin/counter.html', text: 'Đặt vé tại quầy', icon: 'fa-desktop' },
    { href: '/admin/users.html', text: 'Quản lý người dùng', icon: 'fa-users' }
  ];

  const style = `
  @import url('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css');
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
  
  #adminSidebar {
    position: fixed;
    top: 0;
    left: 0;
    height: 100vh;
    width: 240px;
    background: #0f172a;
    border-right: 1px solid #334155;
    padding: 24px 16px;
    z-index: 9999;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    font-family: 'Inter', sans-serif;
    transition: transform 0.3s ease;
  }
  
  #adminSidebar h2 {
    font-size: 20px;
    font-weight: 700;
    margin: 0 0 32px 12px;
    background: linear-gradient(45deg, #6366f1, #ec4899);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    letter-spacing: -0.5px;
  }
  
  #adminSidebar .nav {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4px;
    overflow-y: auto;
    overflow-x: hidden;
  }

  /* Custom Scrollbar */
  #adminSidebar .nav::-webkit-scrollbar {
    width: 4px;
  }
  #adminSidebar .nav::-webkit-scrollbar-track {
    background: transparent;
  }
  #adminSidebar .nav::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 4px;
  }
  #adminSidebar .nav::-webkit-scrollbar-thumb:hover {
    background: rgba(255, 255, 255, 0.2);
  }
  
  #adminSidebar .nav a {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    border-radius: 12px;
    color: #94a3b8;
    text-decoration: none;
    font-size: 14px;
    font-weight: 500;
    transition: all 0.2s;
  }
  
  #adminSidebar .nav a:hover {
    background: rgba(255, 255, 255, 0.03);
    color: #f8fafc;
    transform: translateX(4px);
  }
  
  #adminSidebar .nav a.active {
    background: linear-gradient(to right, rgba(99, 102, 241, 0.1), rgba(99, 102, 241, 0.05));
    color: #6366f1;
    border: 1px solid rgba(99, 102, 241, 0.1);
  }
  
  #adminSidebar .nav a i {
    width: 20px;
    text-align: center;
    font-size: 16px;
  }
  
  #adminSidebar .logout {
    margin-top: 24px;
    padding-top: 24px;
    border-top: 1px solid #334155;
  }
  
  #adminSidebar .logout button {
    width: 100%;
    background: rgba(239, 68, 68, 0.1);
    border: 1px solid rgba(239, 68, 68, 0.2);
    border-radius: 12px;
    color: #ef4444;
    padding: 12px;
    font-family: inherit;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
  }
  
  #adminSidebar .logout button:hover {
    background: #ef4444;
    color: white;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
  }

  @media (max-width: 1024px) {
    #adminSidebar {
        transform: translateX(-100%);
    }
    #adminSidebar.open {
        transform: translateX(0);
    }
    /* Add a toggle button style if needed, but for now we rely on the body margin logic in pages */
  }

  #adminConfirmOverlay{
    position: fixed;
    inset: 0;
    background: rgba(2, 6, 23, 0.72);
    display: none;
    align-items: center;
    justify-content: center;
    z-index: 10001;
    padding: 20px;
    opacity: 0;
    transition: opacity 0.18s ease;
  }
  #adminConfirmOverlay.show{ opacity: 1; }
  #adminConfirmOverlay .ac-dialog{
    width: min(520px, 100%);
    background: #0b1220;
    border: 1px solid rgba(255,255,255,0.10);
    border-radius: 16px;
    box-shadow: 0 18px 60px rgba(0,0,0,0.55);
    overflow: hidden;
    transform: translateY(8px) scale(0.98);
    transition: transform 0.18s ease;
  }
  #adminConfirmOverlay.show .ac-dialog{ transform: translateY(0) scale(1); }
  #adminConfirmOverlay .ac-head{
    padding: 14px 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    border-bottom: 1px solid rgba(255,255,255,0.08);
    background: rgba(255,255,255,0.02);
  }
  #adminConfirmOverlay .ac-title{
    margin: 0;
    font-size: 15px;
    font-weight: 700;
    color: #f8fafc;
    letter-spacing: -0.2px;
  }
  #adminConfirmOverlay .ac-body{
    padding: 14px 16px;
    color: rgba(226,232,240,0.92);
    font-size: 13px;
    line-height: 1.5;
  }
  #adminConfirmOverlay .ac-actions{
    padding: 12px 16px 16px;
    display: flex;
    justify-content: flex-end;
    gap: 10px;
  }
  #adminConfirmOverlay .ac-btn{
    border: 1px solid rgba(255,255,255,0.12);
    background: rgba(255,255,255,0.04);
    color: #e5e7eb;
    padding: 10px 14px;
    border-radius: 10px;
    font-weight: 700;
    cursor: pointer;
    font-family: inherit;
    transition: transform 0.15s ease, background 0.15s ease, border-color 0.15s ease;
  }
  #adminConfirmOverlay .ac-btn:hover{
    background: rgba(255,255,255,0.07);
    transform: translateY(-1px);
  }
  #adminConfirmOverlay .ac-btn.primary{
    background: linear-gradient(135deg, #6366f1, #3b82f6);
    border-color: rgba(99,102,241,0.55);
    color: #fff;
  }
  #adminConfirmOverlay .ac-btn.primary:hover{
    background: linear-gradient(135deg, #4f46e5, #2563eb);
  }
  #adminConfirmOverlay .ac-btn.primary.danger{
    background: linear-gradient(135deg, #ef4444, #f97316);
    border-color: rgba(239,68,68,0.55);
  }
  #adminConfirmOverlay .ac-btn.primary.danger:hover{
    background: linear-gradient(135deg, #dc2626, #ea580c);
  }
  `;

  function ensureAdminConfirm() {
    if (window.adminConfirm) return;
    const overlay = document.createElement('div');
    overlay.id = 'adminConfirmOverlay';
    overlay.setAttribute('aria-hidden', 'true');
    overlay.innerHTML = `
      <div class="ac-dialog" role="dialog" aria-modal="true">
        <div class="ac-head">
          <div class="ac-title" id="adminConfirmTitle">Xác nhận</div>
        </div>
        <div class="ac-body" id="adminConfirmMessage"></div>
        <div class="ac-actions">
          <button type="button" class="ac-btn" id="adminConfirmCancel">Hủy</button>
          <button type="button" class="ac-btn primary" id="adminConfirmOk">Xác nhận</button>
        </div>
      </div>
    `;
    document.body.appendChild(overlay);

    const titleEl = overlay.querySelector('#adminConfirmTitle');
    const messageEl = overlay.querySelector('#adminConfirmMessage');
    const btnCancel = overlay.querySelector('#adminConfirmCancel');
    const btnOk = overlay.querySelector('#adminConfirmOk');

    let resolveFn = null;
    let lastActive = null;
    let closeTimer = null;

    const close = (ok) => {
      if (!resolveFn) return;
      if (closeTimer) clearTimeout(closeTimer);
      overlay.classList.remove('show');
      overlay.setAttribute('aria-hidden', 'true');
      closeTimer = setTimeout(() => {
        overlay.style.display = 'none';
      }, 180);
      const r = resolveFn;
      resolveFn = null;
      if (lastActive && lastActive.focus) lastActive.focus();
      lastActive = null;
      r(!!ok);
    };

    overlay.addEventListener('click', (e) => { if (e.target === overlay) close(false); });
    btnCancel.addEventListener('click', () => close(false));
    btnOk.addEventListener('click', () => close(true));
    document.addEventListener('keydown', (e) => { if (resolveFn && e.key === 'Escape') close(false); });

    window.adminConfirm = (opts) => {
      const o = typeof opts === 'string' ? { message: opts } : (opts || {});
      if (closeTimer) clearTimeout(closeTimer);
      titleEl.textContent = (o.title || 'Xác nhận').toString();
      messageEl.textContent = (o.message || 'Bạn có chắc chắn muốn thực hiện thao tác này?').toString();
      btnOk.textContent = (o.confirmText || 'Xác nhận').toString();
      btnCancel.textContent = (o.cancelText || 'Hủy').toString();
      btnOk.classList.toggle('danger', !!o.danger);

      lastActive = document.activeElement;
      overlay.style.display = 'flex';
      overlay.setAttribute('aria-hidden', 'false');
      requestAnimationFrame(() => overlay.classList.add('show'));
      setTimeout(() => btnOk.focus(), 0);

      return new Promise((resolve) => { resolveFn = resolve; });
    };
  }

  function inject() {
    try { if (document.body && document.body.hasAttribute('data-no-admin-sidebar')) return; } catch(e) {}
    if (document.querySelector('.sidebar')) return;
    if (document.getElementById('adminSidebar')) return;
    
    const s = document.createElement('style');
    s.textContent = style;
    document.head.appendChild(s);

    ensureAdminConfirm();
    
    const el = document.createElement('aside');
    el.id = 'adminSidebar';
    
    const h = document.createElement('h2');
    h.innerHTML = '<i class="fas fa-film" style="-webkit-text-fill-color: initial; color: #6366f1; margin-right: 8px"></i> Admin';
    el.appendChild(h);
    
    const nav = document.createElement('nav');
    nav.className = 'nav';
    el.appendChild(nav);
    
    const path = location.pathname.replace(/\\/g, '/');
    links.forEach(l => {
      const a = document.createElement('a');
      a.href = l.href;
      a.innerHTML = `<i class="fas ${l.icon || 'fa-circle'}"></i> ${l.text}`;
      if (path.endsWith(l.href)) a.classList.add('active');
      nav.appendChild(a);
    });
    
    const logoutWrap = document.createElement('div');
    logoutWrap.className = 'logout';
    
    const btn = document.createElement('button');
    btn.innerHTML = '<i class="fas fa-sign-out-alt"></i> Đăng xuất';
    
    const token = localStorage.getItem('admin_token');
    if (!token) btn.style.display = 'none';
    
    btn.addEventListener('click', () => { 
        localStorage.removeItem('admin_token'); 
        location.href = '/admin/login.html?next=' + encodeURIComponent('/admin/index.html'); 
    });
    
    logoutWrap.appendChild(btn);
    el.appendChild(logoutWrap);
    
    document.body.appendChild(el);
    
    // Add toggle button for mobile if not exists
    if (window.innerWidth <= 1024) {
        const toggleBtn = document.createElement('button');
        toggleBtn.innerHTML = '<i class="fas fa-bars"></i>';
        toggleBtn.style.cssText = 'position: fixed; top: 20px; left: 20px; z-index: 10000; padding: 10px; border-radius: 8px; background: #1e293b; border: 1px solid #334155; color: white; cursor: pointer; display: none;';
        
        // Only show if we are in a mobile view context
        const checkMobile = () => {
            if(window.innerWidth <= 1024) toggleBtn.style.display = 'block';
            else toggleBtn.style.display = 'none';
        };
        checkMobile();
        window.addEventListener('resize', checkMobile);
        
        toggleBtn.addEventListener('click', () => {
            el.classList.toggle('open');
        });
        
        document.body.appendChild(toggleBtn);
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', inject); else inject();
})();
