"""Backup acceptance data and restore only into a disposable, isolated MySQL container."""
from pathlib import Path
import subprocess,os,json
ROOT=Path(__file__).resolve().parents[1]
COMPOSE=['docker','compose','--env-file','.env.example','-p','flowstaff-ml-check','-f','docker-compose.yml','-f','tools/compose.ml-acceptance.yml']
RESTORE=['docker','compose','-p','flowstaff-restore-check','-f','ops/backup/docker-compose.restore-drill.yml']
TABLES={'enterprise_core':['employees','tasks','ml_event_outbox','attrition_predictions'],
        'enterprise_workload':['workload_daily_observations'],
        'enterprise_ml':['ml_model_versions','ml_v2_model_versions','ml_v2_feature_snapshots','ml_v2_predictions']}

def run(command,**kwargs):return subprocess.run(command,cwd=ROOT,check=True,**kwargs)
def counts(compose,env=None):
    result={}
    for db,tables in TABLES.items():
        for table in tables:
            query=f'SELECT COUNT(*) FROM {db}.{table}'
            command=compose+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot -e "$1"','sh',query]
            result[f'{db}.{table}']=subprocess.check_output(command,cwd=ROOT,env=env,text=True).strip()
    return result

def main():
    path=ROOT/'output/ml-acceptance/backups';path.mkdir(parents=True,exist_ok=True)
    env={**os.environ,'BACKUP_DIRECTORY':str(path),'BACKUP_ID':'latest'}
    # Pause writers only on the explicit acceptance project to get a coordinated three-DB copy.
    try:
        run(COMPOSE+['stop','enterprise-app','workload-service','attrition-ml-service'])
        before=counts(COMPOSE)
        run(COMPOSE+['build','backup'])
        run(COMPOSE+['run','--rm','--no-deps','-e','BACKUP_ONCE=true','backup'])
    finally:
        run(COMPOSE+['start','workload-service','attrition-ml-service','enterprise-app'])
    try:
        run(RESTORE+['up','-d','--wait','mysql'],env=env)
        run(RESTORE+['build','restore'],env=env)
        restored=run(RESTORE+['run','--rm','restore'],env=env,capture_output=True,text=True)
        assert 'Restore completed and verified' in restored.stdout,restored.stdout+restored.stderr
        after=counts(RESTORE,env)
        assert before==after,{'before':before,'after':after}
        (ROOT/'output/improvement-stage6-restore.json').write_text(json.dumps({'before':before,'after':after,
            'artifact_hashes_checked':True,'separate_mysql_tmpfs':True},indent=2))
        print('PASS restore: all three DBs, row counts, nested artifact paths and V2 SHA-256 verified')
    finally:
        run(RESTORE+['down'],env=env)
if __name__=='__main__':main()
