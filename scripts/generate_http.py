"""Parcours manuels, avec identifiants issus des réponses et dates calculées."""
import json
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--local', action='store_true', help='Utiliser les ports du fichier .env')
args = parser.parse_args()
ports = {}
if args.local and (ROOT/'.env').exists():
    values = dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if line and not line.startswith('#'))
    ports = {str(8081+i):values.get(name+'_HOST_PORT', str(8081+i)) for i,name in enumerate(['RIDER','HORSE','BOOKING','PAYMENT','DASHBOARD'])}
steps = [
    ('createRider','POST','8081','/riders','CreateRider',{'firstName':'Emma','lastName':'Martin','email':'emma.martin@example.org','phone':'+33612345678','level':'GALOP_3'},'riderId'),
    ('createHorse','POST','8082','/horses','CreateHorse',{'name':'Éclair','breed':'Selle français','birthYear':2016,'minimumRiderLevel':'GALOP_2'},'horseId'),
    ('createBooking','POST','8083','/bookings','CreateBooking',{'riderId':'{{riderId}}','horseId':'{{horseId}}','sessionDate':'{{futureDate}}','durationMinutes':60,'price':45.50},'bookingId'),
    ('createPayment','POST','8084','/payments','CreatePayment',{'bookingId':'{{bookingId}}'},'paymentId'),
    ('confirmPayment','PATCH','8084','/payments/{{paymentId}}/confirm','ConfirmPayment',{},None),
    ('confirmBooking','PATCH','8083','/bookings/{{bookingId}}/confirm','ConfirmBooking',{},None),
    ('getDashboard','GET','8085','/dashboard/riders/{{riderId}}',None,None,None),
    ('completeBooking','PATCH','8083','/bookings/{{bookingId}}/complete','CompleteBooking',{},None),
    ('getCompletedDashboard','GET','8085','/dashboard/riders/{{riderId}}',None,None,None),
]
header = '# Exécuter les requêtes une par une. Attendre la réplication entre les étapes.\n# Une réponse 404/409 transitoire peut être rejouée après convergence.\n\n'
vscode = header+'@futureDate = {{$datetime iso8601 2 d}}\n'
vscode += '\n'.join(f'@{key} = {{{{{name}.response.body.$.{key}}}}}' for name,_,_,_,_,_,key in steps if key)+'\n\n'
idea = header
for name, method, port, path, kind, payload, key in steps:
    port = ports.get(port, port)
    part = f'### {name}\n# @name {name}\n{method} http://localhost:{port}{path}\n'
    if kind:
        body = {'commandId':'{{$guid}}','commandType':kind,'occurredAt':'{{$datetime iso8601}}','payload':payload}
        vscode += part+'Content-Type: application/json\n\n'+json.dumps(body,ensure_ascii=False,indent=2)+'\n\n'
        body['commandId']='{{$uuid}}'
        body['occurredAt']='{{now}}'
        idea += f'### {name}\n# @name {name}\n'
        idea += '''< {%
request.variables.set("now", new Date().toISOString());
request.variables.set("futureDate", new Date(Date.now() + 2 * 86400000).toISOString());
%}
'''+f'{method} http://localhost:{port}{path}\nContent-Type: application/json\n\n'+json.dumps(body,ensure_ascii=False,indent=2)+'\n'
        idea += '> {%\nclient.test("Succès HTTP", function () { client.assert(response.status === 200 || response.status === 201); });\n'
        if key: idea += f'client.global.set("{key}", response.body.{key});\n'
        idea += '%}\n\n'
    else:
        vscode += part+'\n'
        idea += part+'\n'
(ROOT/'http').mkdir(exist_ok=True)
suffix = '-local' if args.local else ''
(ROOT/'http'/f'equibook-vscode{suffix}.http').write_text(vscode.rstrip()+'\n',encoding='utf-8')
(ROOT/'http'/f'equibook-intellij{suffix}.http').write_text(idea.rstrip()+'\n',encoding='utf-8')
