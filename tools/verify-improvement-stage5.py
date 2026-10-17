"""Shared MySQL operation lock, legacy idempotency, manual activation and rollback."""
from pathlib import Path
from uuid import uuid4
import httpx,pymysql,json
ROOT=Path(__file__).resolve().parents[1]

def main():
    env=dict(line.split('=',1) for line in (ROOT/'.env.example').read_text().splitlines() if '=' in line and not line.startswith('#'))
    client=httpx.Client(base_url='http://localhost:18082',timeout=210,headers={'X-Service-Token':env['ML_SERVICE_TOKEN']})
    def models():return client.get('/internal/v2/models').json()
    approved=[m for m in models() if m['gate_passed']]
    assert len(approved)>=2,'Seed acceptance models first'
    original=client.get('/internal/v1/model/info').json()['version']
    db=pymysql.connect(host='127.0.0.1',port=13307,user=env['ML_DB_USERNAME'],password=env['ML_DB_PASSWORD'],database='enterprise_ml')
    try:
        with db.cursor() as cur:
            cur.execute("SELECT GET_LOCK('flowstaff-ml-lifecycle',0)");assert cur.fetchone()[0]==1
        assert client.post('/internal/v2/training/run').status_code==409
        assert client.post('/internal/v2/models/'+approved[0]['version']+'/activate').status_code==409
    finally:
        with db.cursor() as cur:cur.execute("SELECT RELEASE_LOCK('flowstaff-ml-lifecycle')")
        db.close()
    key=str(uuid4())
    first=client.post('/internal/v1/train',json={'idempotencyKey':key})
    assert first.status_code==200,first.text
    value=first.json();assert value['status'] in ('CANDIDATE','REJECTED') and value['activationRequired']
    second=client.post('/internal/v1/train',json={'idempotencyKey':key})
    assert second.status_code==200 and second.json()['version']==value['version'],second.text
    assert client.get('/internal/v1/model/info').json()['version']==original
    rollback=next(m['version'] for m in approved if m['version']!=original)
    try:
        assert client.post('/internal/v2/models/'+rollback+'/activate').status_code==200
        assert client.get('/internal/v1/model/info').json()['version']==rollback
    finally:
        restored=client.post('/internal/v2/models/'+original+'/activate')
        assert restored.status_code==200,restored.text
    assert sum(m['status']=='ACTIVE' for m in models())==1
    assert client.get('/internal/v1/model/info').json()['version']==original
    (ROOT/'output/improvement-stage5-results.json').write_text(json.dumps({'legacy_candidate':value['version'],
        'active_unchanged':original,'rollback_checked':rollback,'shared_mysql_lock':True,'idempotent':True},indent=2))
    print('PASS stage 5: shared MySQL lock, legacy candidate-only training, repeat request, manual rollback, consistent registries')
if __name__=='__main__':main()
