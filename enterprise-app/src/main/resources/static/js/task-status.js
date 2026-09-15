(() => {
    const records=[...document.querySelectorAll('[data-task-record]')];
    if(!records.length) return;
    const transitions={PLANNED:['IN_PROGRESS','CANCELLED'],IN_PROGRESS:['PLANNED','REVIEW','CANCELLED'],REVIEW:['IN_PROGRESS','DONE'],DONE:['IN_PROGRESS'],CANCELLED:['PLANNED']};
    function syncOptions(record) {
        const current=record.dataset.status;
        record.querySelectorAll('.task-status-select option').forEach(option=> {
            option.disabled=option.value!==current && !(transitions[current] || []).includes(option.value);
        });
    }
    function announce(message,type) {
        const alert=document.getElementById('boardAlert');
        if(alert) { alert.className='alert alert-'+type; alert.textContent=message; }
    }
    function requestComment() {
        return new Promise(resolve => {
            const element=document.createElement('div');
            element.className='modal fade'; element.tabIndex=-1; element.setAttribute('aria-labelledby','statusCommentTitle');
            element.innerHTML='<div class="modal-dialog modal-dialog-centered"><form class="modal-content"><div class="modal-header"><h2 class="modal-title h5" id="statusCommentTitle">Изменение статуса</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Закрыть"></button></div><div class="modal-body"><label class="form-label" for="statusComment">Причина изменения *</label><textarea id="statusComment" class="form-control" required rows="3" maxlength="2000"></textarea><p class="form-text mb-0">Комментарий сохранится в истории задачи.</p></div><div class="modal-footer"><button class="btn btn-outline-secondary" type="button" data-bs-dismiss="modal">Отмена</button><button class="btn btn-primary">Изменить статус</button></div></form></div>';
            document.body.append(element); const modal=new bootstrap.Modal(element); let answer=null;
            element.querySelector('form').addEventListener('submit',event=> {
                event.preventDefault(); const input=element.querySelector('textarea');
                input.setCustomValidity(input.value.trim() ? '' : 'Укажите причину.');
                if(input.reportValidity()) { answer=input.value.trim(); modal.hide(); }
            });
            element.querySelector('textarea').addEventListener('input',event=>event.target.setCustomValidity(''));
            element.addEventListener('shown.bs.modal',()=>element.querySelector('textarea').focus());
            element.addEventListener('hidden.bs.modal',()=>{modal.dispose();element.remove();resolve(answer);},{once:true});
            modal.show();
        });
    }
    async function changeStatus(record,target) {
        const old=record.dataset.status, select=record.querySelector('.task-status-select');
        if(target===old || record.dataset.pending || select?.disabled) return;
        if(!(transitions[old] || []).includes(target)) {
            if(select)select.value=old;
            announce('Этот переход недоступен. Выберите следующий этап в меню статуса.','warning'); return;
        }
        record.dataset.pending='true';
        if(select)select.value=old;
        let comment=null;
        if(target==='CANCELLED' || (old==='IN_PROGRESS' && target==='PLANNED') || (old==='REVIEW' && target==='IN_PROGRESS')) {
            comment=await requestComment();
            if(!comment) { if(select)select.value=old; delete record.dataset.pending; return; }
        }
        if(select)select.disabled=true;
        try {
            const response=await apiFetch('/api/v1/tasks/'+record.dataset.taskId+'/status',{method:'PATCH',body:JSON.stringify({targetStatusCode:target,version:Number(record.dataset.version),comment,kanbanOrder:Date.now()})});
            record.dataset.status=target; record.dataset.version=response.version;
            document.querySelectorAll('.kanban-dropzone').forEach(zone=>{if(zone.dataset.status===target)zone.prepend(record);});
            if(select)select.value=target;
            if(target==='DONE') { record.dataset.overdue='false'; record.querySelector('.task-card-date')?.classList.remove('text-danger'); record.querySelectorAll('.task-overdue-notice').forEach(el=>el.hidden=true); }
            const badge=document.getElementById('detailStatusBadge');
            if(badge) { badge.dataset.status=target; badge.textContent=select.selectedOptions[0].textContent; }
            syncOptions(record); announce('Статус задачи обновлён.','success');
            document.dispatchEvent(new Event('task-status-changed'));
        } catch(error) {
            if(select)select.value=old;
            announce(error.message+(error.status===409?' Обновите страницу, чтобы получить актуальное состояние задачи.':''),'danger');
        } finally { delete record.dataset.pending; if(select)select.disabled=false; }
    }
    let dragged=null;
    records.forEach(record=> {
        syncOptions(record);
        const select=record.querySelector('.task-status-select');
        select?.addEventListener('change',()=>changeStatus(record,select.value));
        if(select?.disabled) record.draggable=false;
        record.addEventListener('dragstart',event=>{
            if(!record.draggable || event.target.closest('select,button')) { event.preventDefault(); return; }
            dragged=record; record.classList.add('dragging'); event.dataTransfer.effectAllowed='move'; event.dataTransfer.setData('text/plain',record.dataset.taskId);
        });
        record.addEventListener('dragend',()=>{record.classList.remove('dragging');dragged=null;document.querySelectorAll('.drag-over').forEach(zone=>zone.classList.remove('drag-over'));});
    });
    document.querySelectorAll('.kanban-dropzone').forEach(zone=>{
        zone.addEventListener('dragover',event=>{if(dragged){event.preventDefault();zone.classList.add('drag-over');}});
        zone.addEventListener('dragleave',event=>{if(!zone.contains(event.relatedTarget))zone.classList.remove('drag-over');});
        zone.addEventListener('drop',event=>{event.preventDefault();zone.classList.remove('drag-over');if(dragged)changeStatus(dragged,zone.dataset.status);});
    });
})();
