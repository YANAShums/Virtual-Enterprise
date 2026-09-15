(() => {
    const form = document.getElementById('taskCreateForm');
    if (!form) return;
    const alertBox = document.getElementById('taskFormAlert');
    const candidatesBox = document.getElementById('candidateResults');
    const criticalBox = document.getElementById('criticalConfirmation');
    let previewResponse = null;
    let selectedCandidate = null;
    let previewGeneration = 0;
    let previewTimer;
    const previewButton = document.getElementById('previewButton');
    const createButton = document.getElementById('createTaskButton');

    const iso = d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    const start = document.getElementById('taskStart');
    const end = document.getElementById('taskEnd');
    const today = new Date(); start.value = iso(today); const endDate = new Date(today); endDate.setDate(endDate.getDate()+5); end.value = iso(endDate);

    function showAlert(message, type='danger') { alertBox.innerHTML = `<div class="alert alert-${type}">${escapeHtml(message)}</div>`; }
    function clearAlert() { alertBox.innerHTML = ''; }
    function statusClass(status) { return status.toLowerCase(); }
    function statusLabel(status) {
        return ({SEVERE_UNDERLOAD:'Сильно ниже нормы',UNDERLOAD:'Ниже нормы',NORMAL:'Норма',WARNING:'Повышенная',CRITICAL:'Критическая',UNAVAILABLE:'Нет данных'})[status] || status;
    }

    async function calculate() {
        const generation = ++previewGeneration;
        end.setCustomValidity(end.value && start.value && end.value < start.value ? 'Дата завершения должна быть не раньше даты начала.' : '');
        if (!['taskProject','taskStart','taskEnd','taskHours'].every(id => document.getElementById(id).reportValidity())) return;
        previewResponse = null;
        previewButton.disabled = true;
        createButton.disabled = true;
        document.getElementById('overloadConfirmed').checked = false;
        clearAlert(); candidatesBox.innerHTML = '<div class="text-center py-3"><div class="spinner-border spinner-border-sm text-primary"></div> Выполняется расчёт...</div>';
        selectedCandidate = null; criticalBox.classList.add('d-none');
        const payload = {
            projectId: Number(document.getElementById('taskProject').value),
            plannedStartDate: start.value,
            plannedEndDate: end.value,
            plannedHours: Number(document.getElementById('taskHours').value),
            candidateIds: []
        };
        try {
            const response = await apiFetch('/api/v1/tasks/workload-preview',{method:'POST',body:JSON.stringify(payload)});
            if (generation !== previewGeneration) return;
            previewResponse = response;
            renderCandidates(previewResponse.candidates || []);
        } catch(e) { if(generation === previewGeneration) { candidatesBox.innerHTML=''; showAlert(e.message); } }
        finally { if(generation === previewGeneration) previewButton.disabled=false; }
    }

    function renderCandidates(candidates) {
        document.getElementById('candidateHint').textContent=candidates.length ? 'Выберите исполнителя. Справа показана загрузка после назначения.' : 'Измените период или трудоёмкость и повторите расчёт.';
        if (!candidates.length) { candidatesBox.innerHTML='<div class="alert alert-warning">Нет доступных кандидатов.</div>'; return; }
        candidatesBox.innerHTML = candidates.map(c => `
            <label class="candidate-option status-${statusClass(c.status)} ${!c.available ? 'disabled':''}" data-employee-id="${c.employeeId}" data-status="${c.status}">
                <input type="radio" name="assignee" value="${c.employeeId}" ${!c.available ? 'disabled':''} class="form-check-input mt-1">
                <div><strong>${escapeHtml(c.fullName)}</strong><div class="small text-secondary">${escapeHtml((c.warnings||[]).join(' · ') || 'Доступен для назначения')}</div>${c.skillMatchPercent == null ? '' : `<div class="small text-secondary">Соответствие навыков: ${Number(c.skillMatchPercent)}%</div>`}</div>
                <div class="candidate-load"><div class="small text-secondary">Сейчас → после</div><strong>${Number(c.currentLoadPercent)}% → ${Number(c.projectedLoadPercent)}%</strong><div class="workload-badge workload-${statusClass(c.status)}">${escapeHtml(statusLabel(c.status))}</div></div>
            </label>`).join('');
        candidatesBox.querySelectorAll('input[name="assignee"]').forEach(input => input.addEventListener('change', () => {
            candidatesBox.querySelectorAll('.candidate-option').forEach(el => el.classList.remove('selected'));
            input.closest('.candidate-option').classList.add('selected');
            selectedCandidate = candidates.find(c => c.employeeId === Number(input.value));
            document.getElementById('overloadConfirmed').checked = false;
            document.getElementById('criticalMessage').textContent = `Назначение увеличит загрузку до ${selectedCandidate.projectedLoadPercent}%`;
            criticalBox.classList.toggle('d-none', selectedCandidate.status !== 'CRITICAL');
            createButton.disabled = selectedCandidate.status === 'CRITICAL';
            createButton.title = selectedCandidate.status === 'CRITICAL' ? 'Подтвердите критическую перегрузку' : '';
        }));
    }

    document.getElementById('overloadConfirmed').addEventListener('change', event => {
        if (selectedCandidate?.status === 'CRITICAL') {
            createButton.disabled = !event.target.checked;
            createButton.title = event.target.checked ? '' : 'Подтвердите критическую перегрузку';
        }
    });

    document.getElementById('previewButton').addEventListener('click', calculate);
    ['taskProject','taskStart','taskEnd','taskHours'].forEach(id => document.getElementById(id).addEventListener('input', () => {
        ++previewGeneration; clearTimeout(previewTimer); previewResponse=null; selectedCandidate=null;
        previewButton.disabled=false; createButton.disabled=true; createButton.title='Сначала рассчитайте загрузку и выберите исполнителя'; end.setCustomValidity('');
        document.getElementById('overloadConfirmed').checked=false;
        candidatesBox.innerHTML='<p class="small text-secondary">Параметры изменены. Обновляем расчёт…</p>'; criticalBox.classList.add('d-none');
        previewTimer=setTimeout(calculate,600);
    }));

    form.addEventListener('submit', async event => {
        event.preventDefault(); clearAlert();
        if (!form.checkValidity()) { form.classList.add('was-validated'); form.reportValidity(); return; }
        if (!selectedCandidate || !previewResponse) { showAlert('Сначала выполните расчёт и выберите исполнителя.','warning'); return; }
        const isCritical = selectedCandidate.status === 'CRITICAL';
        if (isCritical && !document.getElementById('overloadConfirmed').checked) { showAlert('Подтвердите критическую перегрузку.','warning'); return; }
        const body = {
            projectId:Number(document.getElementById('taskProject').value),
            title:document.getElementById('taskTitle').value,
            description:document.getElementById('taskDescription').value,
            assigneeEmployeeId:selectedCandidate.employeeId,
            statusCode:'PLANNED', priorityCode:document.getElementById('taskPriority').value,
            plannedStartDate:start.value, plannedEndDate:end.value,
            plannedHours:Number(document.getElementById('taskHours').value),
            calculationRequestId:previewResponse.calculationRequestId,
            overloadConfirmed:isCritical && document.getElementById('overloadConfirmed').checked,
            overloadReason:isCritical ? document.getElementById('overloadReason').value : null
        };
        createButton.disabled=true; createButton.textContent='Создаём задачу…';
        try { await apiFetch('/api/v1/tasks',{method:'POST',body:JSON.stringify(body)}); showAlert('Задача создана. Открываем доску…','success'); setTimeout(()=>location.assign('/kanban?projectId='+body.projectId+'&created=1'),500); }
        catch(e) { const details=e.payload?.details; showAlert(`${e.message}${details?.projectedLoadPercent ? ` (${details.projectedLoadPercent}%)`:''}`); }
        finally { createButton.disabled=selectedCandidate?.status === 'CRITICAL' && !document.getElementById('overloadConfirmed').checked; createButton.textContent='Создать задачу'; }
    });


    function updateBoard() {
        const query=(document.getElementById('taskSearch')?.value || '').trim().toLocaleLowerCase('ru');
        const priority=document.getElementById('priorityFilter')?.value;
        const overdue=document.getElementById('overdueFilter')?.checked;
        document.querySelectorAll('.task-card').forEach(card => {
            card.hidden=!(card.dataset.search.toLocaleLowerCase('ru').includes(query) && (!priority || card.dataset.priority===priority) && (!overdue || card.dataset.overdue==='true'));
        });
        document.querySelectorAll('.kanban-column').forEach(column => {
            column.querySelector('.kanban-column-header .badge').textContent=column.querySelectorAll('.task-card:not([hidden])').length;
        });
    }
    ['taskSearch','priorityFilter','overdueFilter'].forEach(id=>document.getElementById(id)?.addEventListener('input',updateBoard));
    document.addEventListener('task-status-changed',updateBoard);
    if(new URLSearchParams(location.search).get('create')==='1' && document.querySelector('[data-bs-target="#taskOffcanvas"]:not([disabled])')) {
        bootstrap.Offcanvas.getOrCreateInstance(document.getElementById('taskOffcanvas')).show();
    }
    document.getElementById('taskOffcanvas').addEventListener('shown.bs.offcanvas',()=>document.getElementById('taskTitle').focus());
    document.getElementById('taskOffcanvas').addEventListener('hidden.bs.offcanvas',()=> {
        const url=new URL(location.href); url.searchParams.delete('create'); history.replaceState(null,'',url);
    });
    function escapeHtml(value) { const div=document.createElement('div'); div.textContent=value??''; return div.innerHTML; }
})();
