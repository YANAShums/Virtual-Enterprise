"""Real employee -> transactional outbox -> ML delivery and correction on isolated stack."""
import os,runpy,re,time,json,subprocess
from pathlib import Path
from datetime import date
ROOT=Path(__file__).resolve().parents[1]
os.environ['STAGE2_TEST_URL']='http://localhost:18080'
Forms=runpy.run_path(str(ROOT/'tools/verify-stage2.py'))['Forms']
login=runpy.run_path(str(ROOT/'tools/verify-improvement-stage1.py'))['login']
COMPOSE=['docker','compose','--env-file','.env.example','-p','flowstaff-ml-check','-f','docker-compose.yml','-f','tools/compose.ml-acceptance.yml']

def main():
    admin=login('admin','admin123')
    def form(path):
        page=admin.get(path);assert page.status_code==200,page.text[:200]
        return next(f for f in Forms(page.text).forms if f['action'].startswith('/employees'))
    def save(path,values):
        f=form(path);fields={k:v[0] for k,v in f['fields'].items()};fields.update(values)
        result=admin.post(f['action'],data=fields,follow_redirects=True)
        assert result.status_code==200,result.text[:300]
        return result
    marker='EVENT'+str(int(time.time()))
    result=save('/employees/new',dict(personnelNumber=marker,lastName=marker,firstName='Acceptance',
        hireDate='2024-01-01',departmentId='1',positionId='1',workScheduleId='1',employmentRate='1'))
    employee=int(re.search(r'/employees/(\d+)',str(result.url)).group(1))
    def sql(query):
        command=COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot -e "$1"','sh',query]
        return subprocess.check_output(command,text=True).strip()
    def daily():
        page=admin.get('/admin/ml/models')
        f=next(f for f in Forms(page.text).forms if f['action'].endswith('/daily'))
        return admin.post(f['action'],data={k:v[0] for k,v in f['fields'].items()})
    subprocess.run(COMPOSE+['stop','attrition-ml-service'],check=True,capture_output=True)
    try:
        save(f'/employees/{employee}/edit',dict(status='DISMISSED',terminationDate=date.today().isoformat(),
            terminationReason='Acceptance termination',terminationCategory='VOLUNTARY'))
        assert sql(f'SELECT COUNT(*) FROM enterprise_core.ml_event_outbox WHERE employee_id={employee} AND delivered_at IS NULL')=='1'
        daily()
        assert sql(f'SELECT COUNT(*) FROM enterprise_core.ml_event_outbox WHERE employee_id={employee} AND delivered_at IS NULL')=='1'
    finally:
        subprocess.run(COMPOSE+['start','attrition-ml-service'],check=True,capture_output=True)
    for _ in range(20):
        try:
            import httpx
            if httpx.get('http://localhost:18082/health',timeout=2).status_code==200:break
        except httpx.HTTPError:pass
        time.sleep(2)
    daily()
    assert sql(f"SELECT reason_category FROM enterprise_ml.ml_v2_terminations WHERE employee_id='{employee}'")=='VOLUNTARY'
    save(f'/employees/{employee}/edit',dict(terminationCategory='INVOLUNTARY'))
    daily()
    assert sql(f"SELECT reason_category FROM enterprise_ml.ml_v2_terminations WHERE employee_id='{employee}'")=='INVOLUNTARY'
    save(f'/employees/{employee}/edit',dict(status='ACTIVE',changeReason='Acceptance correction'))
    daily()
    assert sql(f"SELECT cancelled FROM enterprise_ml.ml_v2_terminations WHERE employee_id='{employee}'")=='1'
    assert sql(f'SELECT COUNT(*) FROM enterprise_core.ml_event_outbox WHERE employee_id={employee} AND delivered_at IS NULL')=='0'
    print('PASS stage 3: employee save during ML outage, persisted outbox, retry, correction, cancellation')
if __name__=='__main__':main()
