"""Consolide les rapports de tests et vérifie l'état final du projet Compose."""
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from datetime import datetime, timezone
from e2e import docker, eventually

ROOT = Path(__file__).resolve().parents[1]
services = ['rider','horse','booking','payment','dashboard']
unit = {}
for name in services:
    reports = list((ROOT/f'{name}-service/build/test-results/test').glob('TEST-*.xml'))
    assert reports, f'Rapport JUnit absent : {name}'
    totals = {'tests':0,'failures':0,'errors':0,'skipped':0}
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.get(key,'0'))
    assert totals['tests'] and not (totals['failures'] or totals['errors'] or totals['skipped'])
    unit[name+'-service'] = totals

queue_count = "local n=0; for _,k in ipairs(redis.call('keys','events:*')) do n=n+redis.call('llen',k); end; return n"
eventually(lambda:docker('exec','-T','redis','redis-cli','--raw','EVAL',queue_count,'0'),lambda n:n=='0')
databases = ['equibook_'+s for s in ['riders','horses','bookings','payments','dashboard']]
outbox_count = json.dumps(databases)+'.reduce((n,s)=>n+db.getSiblingDB(s).outbox.countDocuments({}),0)'
eventually(lambda:docker('exec','-T','mongodb','mongosh','--quiet','--eval',outbox_count),lambda n:n=='0')
health = dict(line.split() for line in docker('ps','--format','{{.Service}} {{.Health}}').splitlines())
assert set(health)==set([s+'-service' for s in services]+['mongodb','redis'])
assert all(v=='healthy' for v in health.values()), health
integration = json.loads((ROOT/'artifacts/e2e-results.json').read_text(encoding='utf-8'))
summary = {
    'verifiedAt':datetime.now(timezone.utc).isoformat(),
    'unit':unit,
    'unitTotal':sum(s['tests'] for s in unit.values()),
    'integrationChecks':integration['checks'],
    'health':health,
    'pendingRedisMessages':0,
    'pendingOutboxMessages':0,
    'riderId':integration['riderId']
}
(ROOT/'artifacts/verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False,indent=2))
