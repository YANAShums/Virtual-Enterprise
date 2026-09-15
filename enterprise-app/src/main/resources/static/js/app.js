window.getCookie = function(name) {
    const item = document.cookie.split('; ').find(row => row.startsWith(name + '='));
    return item ? decodeURIComponent(item.split('=')[1]) : null;
};
window.apiFetch = async function(url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (!headers.has('Content-Type') && options.body) headers.set('Content-Type','application/json');
    const token = document.querySelector('meta[name="csrf-token"]')?.content || getCookie('XSRF-TOKEN');
    const tokenHeader = document.querySelector('meta[name="csrf-header"]')?.content || 'X-XSRF-TOKEN';
    if (token) headers.set(tokenHeader, token);
    const response = await fetch(url, {...options, headers});
    if(response.redirected && new URL(response.url).pathname==='/login') {
        throw new Error('Сессия завершилась. Войдите в аккаунт и повторите действие.');
    }
    if (!response.ok) {
        let error;
        try { error = await response.json(); } catch { error = {message:'Ошибка запроса'}; }
        const e = new Error(error.message || (response.status===403 ? 'Недостаточно прав или сессия истекла. Обновите страницу.' : 'Ошибка запроса')); e.payload = error; e.status = response.status; throw e;
    }
    if (response.status === 204) return null;
    return response.json();
};

(() => {
    const workloadLabels = {
        SEVERE_UNDERLOAD:'Сильно ниже нормы', UNDERLOAD:'Ниже нормы', NORMAL:'Норма',
        WARNING:'Повышенная', CRITICAL:'Критическая', UNAVAILABLE:'Нет данных'
    };
    const main = document.querySelector('main');
    if (main) { main.id = 'mainContent'; main.tabIndex = -1; }
    const links = [...document.querySelectorAll('.app-nav a')];
    const current = links.filter(a => location.pathname === a.pathname || location.pathname.startsWith(a.pathname + '/'))
        .sort((a,b) => b.pathname.length - a.pathname.length)[0];
    if (current) { current.classList.add('active'); current.setAttribute('aria-current','page'); }
    const crumb = document.getElementById('currentBreadcrumb');
    if (crumb) crumb.textContent = document.title.split(' — ')[0];
    const sidebarToggle=document.getElementById('sidebarToggle');
    function updateSidebar(expanded) {
        document.body.classList.toggle('sidebar-expanded',expanded);
        sidebarToggle?.setAttribute('aria-expanded',String(expanded));
        const label=innerWidth<992 ? 'Закрыть меню' : expanded?'Свернуть меню':'Развернуть меню';
        sidebarToggle?.setAttribute('aria-label',label);
        if(sidebarToggle)sidebarToggle.title=label;
    }
    try { updateSidebar(localStorage.getItem('enterprise.sidebar')==='expanded'); } catch {}
    sidebarToggle?.addEventListener('click',()=>{
        if(innerWidth<992) { bootstrap.Offcanvas.getOrCreateInstance(document.getElementById('appSidebar')).hide(); return; }
        const expanded=!document.body.classList.contains('sidebar-expanded'); updateSidebar(expanded);
        try { localStorage.setItem('enterprise.sidebar',expanded?'expanded':'compact'); } catch {}
    });
    document.querySelectorAll('[data-tabs]').forEach(group=>{
        const buttons=[...group.querySelectorAll('[data-tab-target]')];
        const panels=buttons.map(button=>document.getElementById(button.dataset.tabTarget));
        function activate(button) {
            buttons.forEach((item,index)=>{
                const active=item===button; item.classList.toggle('active',active);
                item.setAttribute('aria-selected',String(active)); item.tabIndex=active?0:-1; panels[index].hidden=!active;
            });
        }
        buttons.forEach((button,index)=>{
            button.addEventListener('click',()=>activate(button));
            button.addEventListener('keydown',event=>{
                let next;
                if(event.key==='ArrowRight')next=buttons[(index+1)%buttons.length];
                if(event.key==='ArrowLeft')next=buttons[(index+buttons.length-1)%buttons.length];
                if(event.key==='Home')next=buttons[0]; if(event.key==='End')next=buttons.at(-1);
                if(next) { event.preventDefault(); activate(next); next.focus(); }
            });
        });
        if(buttons.length)activate(buttons[0]);
    });
    document.querySelectorAll('.scroll-hint').forEach(hint=>{
        const scroller=hint.parentElement.querySelector('.kanban-board,.table-responsive');
        if(scroller)new ResizeObserver(()=>hint.classList.toggle('is-visible',scroller.scrollWidth>scroller.clientWidth+1)).observe(scroller);
    });
    document.querySelectorAll('[data-filter-target]').forEach(input => {
        input.addEventListener('input', () => {
            const query = input.value.trim().toLocaleLowerCase('ru');
            const rows = [...document.querySelectorAll(input.dataset.filterTarget)];
            rows.forEach(row => { row.hidden = !row.textContent.toLocaleLowerCase('ru').includes(query); });
            const empty = document.querySelector('[data-filter-empty]');
            if (empty) empty.hidden = !rows.length || rows.some(row => !row.hidden);
        });
    });
    document.querySelectorAll('.day-cell').forEach(cell => cell.addEventListener('click', () => {
        const data = cell.dataset;
        const fields = {dayEmployee:data.employee, dayDate:data.date, dayPercent:data.percent+'%', dayAvailable:data.available+' ч', dayAssigned:data.assigned+' ч', dayStatus:workloadLabels[data.status] || data.status};
        Object.entries(fields).forEach(([id,value]) => { const el = document.getElementById(id); if(el) el.textContent=value; });
        const employeeLink=document.getElementById('dayEmployeeLink');
        if(employeeLink && data.employeeId) employeeLink.href='/employees/'+encodeURIComponent(data.employeeId);
    }));
    document.querySelectorAll('form').forEach(form => form.addEventListener('submit', event => {
        const start=form.querySelector('[name="start"],[name="plannedStartDate"]'), end=form.querySelector('[name="end"],[name="plannedEndDate"]');
        if(start && end) { end.setCustomValidity(end.value && start.value && end.value<start.value ? 'Окончание периода должно быть не раньше начала.' : ''); if(!form.reportValidity()) event.preventDefault(); }
    }));
    document.querySelectorAll('input[type="date"]').forEach(input => input.addEventListener('input',()=>input.setCustomValidity('')));
})();
