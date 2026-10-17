"""Stage 1 acceptance against the isolated 18080 stack only."""
import httpx
import re

BASE='http://localhost:18080'
def login(name, password):
    client=httpx.Client(base_url=BASE,follow_redirects=True,timeout=210)
    page=client.get('/login')
    token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page.text).group(1)
    result=client.post('/login',data={'username':name,'password':password,'_csrf':token})
    assert '/login' not in str(result.url)
    return client

def main():
    admin=login('admin','admin123')
    page=admin.get('/admin/ml/models')
    assert page.status_code==200 and 'Кандидаты, проверка качества' in page.text
    csrf=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page.text).group(1)
    assert admin.post('/admin/ml/models/train').status_code==403
    trained=admin.post('/admin/ml/models/train',data={'_csrf':csrf})
    assert trained.status_code==200 and 'Создана версия' in trained.text, trained.text[:300]
    assert 'ROC-AUC' in trained.text and 'Моделей пока нет' not in trained.text
    candidate=re.search(r'action="/admin/ml/models/([^/\"]+)/activate"',trained.text)
    assert candidate, 'No approved model available for activation'
    published=admin.post('/admin/ml/models/'+candidate.group(1)+'/activate',data={'_csrf':csrf})
    assert published.status_code==200 and 'Активирована модель' in published.text
    assert admin.get('/api/v1/ml/model').json()['version']==candidate.group(1)
    # Tampered version must not be activated; the endpoint delegates validation to ML.
    assert admin.post('/admin/ml/models/not-existing/activate',data={'_csrf':csrf}).status_code==404
    for name,password in [('employee','employee123'),('manager','manager123')]:
        client=login(name,password)
        assert client.get('/admin/ml/models').status_code==403
    template=admin.get('/admin/ml/models/template.csv')
    assert template.status_code==200 and 'workload_days_available_30d' in template.text
    print('PASS stage 1: real model page, training candidate, metrics, CSRF, roles, missing version and CSV template')

if __name__=='__main__': main()
