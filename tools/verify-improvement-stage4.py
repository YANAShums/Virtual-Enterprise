"""Check persisted quality report and unchanged prediction API on isolated services."""
from pathlib import Path
import runpy,re,httpx,json
ROOT=Path(__file__).resolve().parents[1]

def main():
    admin=runpy.run_path(str(ROOT/'tools/verify-improvement-stage1.py'))['login']('admin','admin123')
    page=admin.get('/admin/ml/models')
    token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page.text).group(1)
    trained=admin.post('/admin/ml/models/train',data={'_csrf':token})
    assert trained.status_code==200 and 'Создана версия' in trained.text,trained.text[:300]
    assert 'не подтверждено на реальной кадровой истории' in trained.text
    env=dict(line.split('=',1) for line in (ROOT/'.env.example').read_text().splitlines() if '=' in line and not line.startswith('#'))
    models=httpx.get('http://localhost:18082/internal/v2/models',headers={'X-Service-Token':env['ML_SERVICE_TOKEN']}).json()
    quality=models[0]['metrics']['quality_report']
    assert quality['business_validation']=='NOT_VALIDATED'
    assert quality['temporal_backtest']['status']=='INSUFFICIENT_DATA'
    assert quality['calibration']['method']=='sigmoid_on_independent_employees'
    assert 'fit_employee_ids' not in quality['calibration']
    assert admin.get('/api/v1/attrition/features/1').status_code==200
    (ROOT/'output/improvement-stage4-quality.json').write_text(json.dumps(quality,indent=2),encoding='utf-8')
    print('PASS stage 4: calibrated artifact, persisted quality report, truthful insufficient history, no employee IDs in metrics')
if __name__=='__main__':main()
