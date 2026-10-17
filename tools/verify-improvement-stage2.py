"""Observed vs projected workload, immutable observations and Java/ML contracts."""
from pathlib import Path
from datetime import datetime,timedelta
from zoneinfo import ZoneInfo
from uuid import uuid4
import httpx,json,runpy,re

ROOT=Path(__file__).resolve().parents[1]
def main():
    env=dict(line.split('=',1) for line in (ROOT/'.env.example').read_text().splitlines() if '=' in line and not line.startswith('#'))
    client=httpx.Client(base_url='http://localhost:18081',headers={'X-Service-Token':env.get('WORKLOAD_SERVICE_TOKEN','change-me-internal-token')},timeout=40)
    today=datetime.now(ZoneInfo('Europe/Minsk')).date()
    employee=500000000+uuid4().int%100000000
    candidate={'employeeId':employee,'fullName':'Acceptance observation','departmentId':1,'employmentRate':1,
        'scheduleHoursByDay':{str(i):8 for i in range(1,8)},'calendarHourOverrides':{},'absences':[],
        'existingTasks':[],'projectMember':True,'skillMatchPercent':100,'activeTaskCount':0,'activeProjectCount':1}
    def features(day):
        response=client.get(f'/internal/v1/workload/features/{employee}',params={'snapshotDate':day.isoformat()})
        assert response.status_code==200,response.text
        return response.json()
    def evaluate(day,hours):
        value=dict(candidate)
        if hours:value['existingTasks']=[{'taskId':99,'startDate':day.isoformat(),'endDate':day.isoformat(),
            'plannedHours':hours,'actualHours':0,'statusCode':'PLANNED'}]
        response=client.post('/internal/v1/workload/evaluate-candidates',json={'enterpriseId':1,'projectId':1,
            'plannedStartDate':day.isoformat(),'plannedEndDate':day.isoformat(),'plannedHours':0,'candidates':[value]})
        assert response.status_code==200,response.text
    missing=features(today);assert missing['averageLoad30Days'] is None and missing['workloadDaysAvailable30d']==0
    evaluate(today+timedelta(days=1),0)
    assert features(today)['workloadDaysAvailable30d']==0
    evaluate(today,0)
    observed=features(today);assert observed['averageLoad30Days']==0 and observed['workloadDaysAvailable30d']==1
    evaluate(today,8)
    assert features(today)['averageLoad30Days']==0, 'Frozen observation was rewritten'
    evaluate(today-timedelta(days=1),8)
    assert features(today-timedelta(days=1))['workloadDaysAvailable30d']==0
    login=runpy.run_path(str(ROOT/'tools/verify-improvement-stage1.py'))['login']
    admin=login('admin','admin123')
    preview=admin.get('/api/v1/attrition/features/1');assert preview.status_code==200,preview.text
    assert 'workloadDaysAvailable30d' in preview.json()
    page=admin.get('/tasks').text
    token=re.search(r'name="csrf-token" content="([^"]+)"',page).group(1)
    header=re.search(r'name="csrf-header" content="([^"]+)"',page).group(1)
    rebuilt=admin.post('/api/v1/attrition/features/rebuild',headers={header:token})
    assert rebuilt.status_code==200 and rebuilt.json()['generated']>0,rebuilt.text
    recalc=admin.post('/api/v1/attrition/recalculate',headers={header:token})
    assert recalc.status_code==200 and recalc.json()['failed']==0,recalc.text
    (ROOT/'output/improvement-stage2-results.json').write_text(json.dumps({'missing':missing,'observed':observed,
        'future_and_past_ignored':True,'observation_immutable':True,'java_preview':preview.json(),
        'batch':recalc.json()},indent=2),encoding='utf-8')
    print('PASS stage 2: missing != zero, immutable actual-day observations, counters, Java snapshots and forecasts')
if __name__=='__main__':main()
