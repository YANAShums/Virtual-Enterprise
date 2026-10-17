"""Task/Kanban actions and workload outage against the isolated stack only."""
from datetime import date,timedelta
from pathlib import Path
import runpy,re,time,subprocess,json
ROOT=Path(__file__).resolve().parents[1]
COMPOSE=['docker','compose','--env-file','.env.example','-p','flowstaff-ml-check','-f','docker-compose.yml','-f','tools/compose.ml-acceptance.yml']

def main():
    admin=runpy.run_path(str(ROOT/'tools/verify-improvement-stage1.py'))['login']('admin','admin123')
    page=admin.get('/tasks').text
    token=re.search(r'name="csrf-token" content="([^"]+)"',page).group(1)
    header=re.search(r'name="csrf-header" content="([^"]+)"',page).group(1)
    headers={header:token}
    day=date.today()+timedelta(days=2)
    while day.weekday()>4:day+=timedelta(days=1)
    preview={'projectId':1,'plannedStartDate':day.isoformat(),'plannedEndDate':day.isoformat(),'plannedHours':0.25,'candidateIds':[]}
    result=admin.post('/api/v1/tasks/workload-preview',json=preview,headers=headers)
    assert result.status_code==200,result.text
    estimate=result.json()
    candidate=next(c for c in estimate['candidates'] if c['available'])
    payload={**preview,'title':'Acceptance task '+str(int(time.time())),'description':'Isolated regression',
        'assigneeEmployeeId':candidate['employeeId'],'statusCode':'PLANNED','priorityCode':'MEDIUM',
        'requiredSkillIds':[],'calculationRequestId':estimate['calculationRequestId'],'overloadConfirmed':True,'overloadReason':'Acceptance'}
    bad=admin.post('/api/v1/tasks',json={**payload,'plannedHours':-1},headers=headers)
    assert bad.status_code==400,bad.text
    no_csrf=admin.post('/api/v1/tasks',json=payload);assert no_csrf.status_code==403
    created=admin.post('/api/v1/tasks',json=payload,headers=headers)
    assert created.status_code==201,created.text
    task=created.json();initial=task['version'];id=task['id']
    def change(target,version):
        return admin.patch(f'/api/v1/tasks/{id}/status',json={'targetStatusCode':target,'version':version,'comment':'Acceptance'},headers=headers)
    assert change('DONE',initial).status_code==409
    for status in ['IN_PROGRESS','REVIEW','DONE']:
        result=change(status,task['version']);assert result.status_code==200,result.text
        task=result.json();assert task['statusCode']==status
    assert change('IN_PROGRESS',initial).status_code==409
    assert payload['title'] in admin.get('/kanban?projectId=1').text
    try:
        subprocess.run(COMPOSE+['stop','workload-service'],check=True,capture_output=True)
        unavailable=admin.post('/api/v1/tasks/workload-preview',json=preview,headers=headers)
        assert unavailable.status_code==503 and unavailable.json()['code']=='WORKLOAD_SERVICE_UNAVAILABLE',unavailable.text
        for route in ['/dashboard','/tasks','/kanban?projectId=1']:
            assert admin.get(route).status_code==200,route
        assert admin.post('/api/v1/tasks',json=payload,headers=headers).status_code==503
    finally:
        subprocess.run(COMPOSE+['start','workload-service'],check=True,capture_output=True)
    import httpx
    for _ in range(20):
        try:
            if httpx.get('http://localhost:18081/actuator/health',timeout=2).status_code==200:break
        except httpx.HTTPError:pass
        time.sleep(2)
    assert admin.post('/api/v1/tasks/workload-preview',json=preview,headers=headers).status_code==200
    (ROOT/'output/improvement-stage6-task.json').write_text(json.dumps({'task_id':id,'final_status':task['statusCode'],
        'invalid_transition_rejected':True,'stale_version_rejected':True,'workload_outage':True,'recovered':True},indent=2))
    print('PASS stage 6: task creation, validation, Kanban transitions, stale version, workload outage and recovery')
if __name__=='__main__':main()
