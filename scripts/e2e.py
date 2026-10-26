"""Vérifie les API réelles, la réplication, Redis, MongoDB et la reprise du dashboard."""
import json
import os
import subprocess
import time
import uuid
import urllib.request
import urllib.error
from datetime import datetime, timedelta, timezone
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]
CHECKS = []
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
local_env = {}
if (ROOT/'.env').exists():
    local_env = dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if line and not line.startswith('#'))
PORTS = {8081+i:int(os.environ.get(name+'_HOST_PORT',local_env.get(name+'_HOST_PORT',8081+i))) for i,name in enumerate(['RIDER','HORSE','BOOKING','PAYMENT','DASHBOARD'])}

def check(condition, label):
    if not condition:
        raise AssertionError(label)
    CHECKS.append(label)
    print('OK - '+label, flush=True)

def schema(folder, name, value):
    contract = json.loads((ROOT/'contracts'/folder/(name+'.json')).read_text(encoding='utf-8'))
    Draft202012Validator(contract, format_checker=FormatChecker()).validate(value)

def request(port, method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f'http://localhost:{PORTS.get(port,port)}{path}', data=data, method=method, headers={'Content-Type':'application/json'})
    try:
        with OPENER.open(req, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)

def envelope(kind, payload):
    value = {'commandId':str(uuid.uuid4()), 'commandType':kind, 'occurredAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'), 'payload':json.loads(json.dumps(payload))}
    schema('commands', kind, value)
    return value

def eventually(work, predicate=lambda x: x[0] in (200, 201), timeout=40):
    end = time.monotonic()+timeout
    last = None
    while time.monotonic() < end:
        try:
            last = work()
            if predicate(last): return last
        except (OSError, ValueError, subprocess.SubprocessError) as e:
            last = str(e)
        time.sleep(0.25)
    raise AssertionError(f'Délai dépassé : {last}')

def send(port, method, path, kind, payload, wait=False):
    body = envelope(kind, payload)
    work = lambda: request(port, method, path, body)
    status, value = eventually(work) if wait else work()
    check(status in (200,201), kind)
    return value

def docker(*args, input=None):
    result = subprocess.run(['docker','compose',*args], input=input, text=True, encoding='utf-8', capture_output=True, cwd=ROOT, check=True)
    return result.stdout.strip()

def dashboard(rider_id, **expected):
    _, value = eventually(lambda: request(8085,'GET','/dashboard/riders/'+rider_id),
        lambda x: x[0] == 200 and all(x[1].get(k)==v for k,v in expected.items()))
    schema('aggregates','rider-dashboard.aggregate',value)
    return value

def replica_status(horse_id):
    return docker('exec','-T','mongodb','mongosh','--quiet','--eval',f'db.getSiblingDB("equibook_bookings").horse_replicas.findOne({{"_id":"{horse_id}"}})?.status')

def main():
    for port in range(8081,8086):
        eventually(lambda: request(port,'GET','/actuator/health'), lambda x:x[0]==200 and x[1]['status']=='UP')
    check(True,'Les cinq services sont sains avec MongoDB et Redis')
    for path in (ROOT/'contracts').rglob('*.json'):
        Draft202012Validator.check_schema(json.loads(path.read_text(encoding='utf-8')))
    check(True,'Les 36 contrats sont des JSON Schema valides')
    r_payload = {'firstName':'Emma','lastName':'Martin','email':f'{uuid.uuid4()}@example.org','phone':'+33612345678','level':'BEGINNER'}
    command = envelope('CreateRider',r_payload)
    status, r = request(8081,'POST','/riders',command)
    check(status==201,'Création du cavalier')
    schema('aggregates','rider.aggregate',r)
    rid = r['riderId']
    status, replay = request(8081,'POST','/riders',command)
    check(status==201 and replay==r,'Rejouer une commande conserve le même agrégat')
    r_payload['firstName']='Léa'
    r = send(8081,'PUT','/riders/'+rid,'UpdateRider',r_payload)
    r = send(8081,'PATCH',f'/riders/{rid}/level','ChangeRiderLevel',{'level':'GALOP_3'})
    h_payload = {'name':'Éclair','breed':'Selle français','birthYear':2016,'minimumRiderLevel':'GALOP_4'}
    h = send(8082,'POST','/horses','CreateHorse',h_payload)
    schema('aggregates','horse.aggregate',h)
    hid = h['horseId']
    date = (datetime.now(timezone.utc)+timedelta(days=2)).isoformat().replace('+00:00','Z')
    b_payload = {'riderId':rid,'horseId':hid,'sessionDate':date,'durationMinutes':60,'price':45.50}
    status, value = eventually(lambda: request(8083,'POST','/bookings',envelope('CreateBooking',b_payload)), lambda x:x[0]==409 and 'Niveau' in x[1].get('error',''))
    check(status==409,'Refus d’une réservation si le niveau est insuffisant')
    h_payload['minimumRiderLevel']='GALOP_2'
    send(8082,'PUT','/horses/'+hid,'UpdateHorse',h_payload)
    send(8082,'PATCH',f'/horses/{hid}/availability','ChangeHorseAvailability',{'status':'RESTING'})
    eventually(lambda:replica_status(hid), lambda value:value=='RESTING')
    unavailable_status, unavailable = request(8083,'POST','/bookings',envelope('CreateBooking',b_payload))
    check(unavailable_status==409 and 'indisponible' in unavailable.get('error',''),'La disponibilité est répliquée dans Booking')
    send(8082,'PATCH',f'/horses/{hid}/availability','ChangeHorseAvailability',{'status':'AVAILABLE'})
    b = send(8083,'POST','/bookings','CreateBooking',b_payload,wait=True)
    schema('aggregates','booking.aggregate',b)
    bid = b['bookingId']
    check(b['rider']['fullName']=='Léa Martin' and b['rider']['level']=='GALOP_3' and b['horse']['minimumRiderLevel']=='GALOP_2','Création et mise à jour effectives des réplicas Rider et Horse')
    check(request(8083,'PATCH',f'/bookings/{bid}/confirm',envelope('ConfirmBooking',{}))[0]==409,'Confirmation refusée avant paiement')
    p = send(8084,'POST','/payments','CreatePayment',{'bookingId':bid},wait=True)
    schema('aggregates','payment.aggregate',p)
    pid = p['paymentId']
    p = send(8084,'PATCH',f'/payments/{pid}/confirm','ConfirmPayment',{})
    send(8083,'PATCH',f'/bookings/{bid}/confirm','ConfirmBooking',{},wait=True)
    eventually(lambda:request(8082,'GET','/horses/'+hid),lambda x:x[0]==200 and x[1]['status']=='RESERVED')
    check(True,'Le cheval source passe à RESERVED après BookingConfirmed')
    dashboard(rid,totalBookings=1,confirmedBookings=1,totalAmountPaid=45.50,riderName='Léa Martin')
    check(True,'Projection de la réservation confirmée et du paiement')
    send(8083,'PATCH',f'/bookings/{bid}/complete','CompleteBooking',{})
    eventually(lambda:request(8082,'GET','/horses/'+hid),lambda x:x[0]==200 and x[1]['status']=='AVAILABLE')
    dashboard(rid,completedBookings=1,confirmedBookings=0)
    check(True,'Fin de séance : cheval disponible et projection mise à jour')
    b = send(8083,'POST','/bookings','CreateBooking',b_payload,wait=True)
    bid2 = b['bookingId']
    p = send(8084,'POST','/payments','CreatePayment',{'bookingId':bid2},wait=True)
    pid2 = p['paymentId']
    send(8084,'PATCH',f'/payments/{pid2}/confirm','ConfirmPayment',{})
    send(8083,'PATCH',f'/bookings/{bid2}/confirm','ConfirmBooking',{},wait=True)
    eventually(lambda:request(8082,'GET','/horses/'+hid),lambda x:x[0]==200 and x[1]['status']=='RESERVED')
    dashboard(rid,totalBookings=2,confirmedBookings=1,totalAmountPaid=91.0)
    docker('stop','dashboard-service')
    try:
        send(8083,'PATCH',f'/bookings/{bid2}/cancel','CancelBooking',{})
        send(8084,'PATCH',f'/payments/{pid2}/refund','RefundPayment',{},wait=True)
        eventually(lambda:request(8082,'GET','/horses/'+hid),lambda x:x[0]==200 and x[1]['status']=='AVAILABLE')
        def queued():
            raw = docker('exec','-T','redis','redis-cli','--raw','LRANGE','events:dashboard','0','-1')
            return [json.loads(line) for line in raw.splitlines() if line]
        messages = eventually(queued, lambda rows:any(e['eventType']=='PaymentRefunded' and e['payload']['paymentId']==pid2 for e in rows))
        for event in messages:
            schema('events',event['eventType'],event)
        duplicate = next(e for e in messages if e['eventType']=='PaymentRefunded' and e['payload']['paymentId']==pid2)
        for _ in range(2):
            docker('exec','-T','redis','redis-cli','-x','LPUSH','events:dashboard',input=json.dumps(duplicate))
        check(True,'Événements conservés dans Redis pendant l’arrêt du consommateur, contrats vérifiés')
        docker('restart','redis')
        eventually(queued, lambda rows:any(e['eventId']==duplicate['eventId'] for e in rows))
        check(True,'Persistance AOF des événements après redémarrage de Redis')
        interrupted = docker('exec','-T','redis','redis-cli','--raw','RPOPLPUSH','events:dashboard','events:dashboard:processing')
        check(bool(interrupted),'Simulation d’une interruption après prise en charge, avant acquittement')
    finally:
        docker('start','dashboard-service')
    final = dashboard(rid,totalBookings=2,completedBookings=1,cancelledBookings=1,confirmedBookings=0,totalAmountPaid=45.50)
    check(True,'Reprise du dashboard et remboursement idempotent malgré trois livraisons')
    # Concurrence réelle : deux commandes différentes concourent pour le même cheval.
    with ThreadPoolExecutor(max_workers=2) as pool:
        commands = [envelope('CreateBooking',b_payload), envelope('CreateBooking',b_payload)]
        results = list(pool.map(lambda c:request(8083,'POST','/bookings',c),commands))
    check(sorted(status for status,_ in results)==[201,409],'Deux réservations concurrentes : une seule attribution du cheval')
    winning = next(value for status,value in results if status==201)
    send(8083,'PATCH',f'/bookings/{winning["bookingId"]}/cancel','CancelBooking',{})
    failed_booking = send(8083,'POST','/bookings','CreateBooking',b_payload,wait=True)
    failed_payment = send(8084,'POST','/payments','CreatePayment',{'bookingId':failed_booking['bookingId']},wait=True)
    failed_payment_id = failed_payment['paymentId']
    send(8084,'PATCH',f'/payments/{failed_payment_id}/fail','FailPayment',{})
    check(request(8084,'PATCH',f'/payments/{failed_payment_id}/confirm',envelope('ConfirmPayment',{}))[0]==409,'Un paiement FAILED ne peut pas être confirmé')
    send(8083,'PATCH',f'/bookings/{failed_booking["bookingId"]}/cancel','CancelBooking',{})
    dashboard(rid,totalBookings=4,completedBookings=1,cancelledBookings=3,confirmedBookings=0,totalAmountPaid=45.50)
    for database, collection in [('equibook_riders','riders'),('equibook_horses','horses'),('equibook_bookings','rider_replicas'),('equibook_bookings','horse_replicas'),('equibook_payments','booking_replicas'),('equibook_dashboard','rider_dashboards'),('equibook_dashboard','processed_events')]:
        count = docker('exec','-T','mongodb','mongosh','--quiet','--eval',f'db.getSiblingDB("{database}").getCollection("{collection}").countDocuments({{}})')
        check(int(count)>0,f'Persistance MongoDB : {database}.{collection}')
    resources = [(8081,'/riders/'+rid),(8082,'/horses/'+hid),(8083,'/bookings/'+bid),(8083,'/bookings/'+bid2),(8083,'/bookings/'+failed_booking['bookingId']),(8084,'/payments/'+pid),(8084,'/payments/'+pid2),(8084,'/payments/'+failed_payment_id),(8085,'/dashboard/riders/'+rid)]
    before = [request(port,'GET',path)[1] for port,path in resources]
    docker('stop')
    docker('up','-d','--wait','--wait-timeout','240')
    after = [eventually(lambda port=port,path=path:request(port,'GET',path))[1] for port,path in resources]
    check(before==after,'Agrégats, disponibilités, paiements et projection identiques après arrêt et redémarrage des sept conteneurs')
    replay_status, persisted_replay = request(8081,'POST','/riders',command)
    check(replay_status==201 and persisted_replay==replay,'Idempotence des commandes préservée après redémarrage')
    docker('stop','redis')
    try:
        send(8081,'PATCH',f'/riders/{rid}/level','ChangeRiderLevel',{'level':'GALOP_4'})
        pending = docker('exec','-T','mongodb','mongosh','--quiet','--eval',f'db.getSiblingDB("equibook_riders").outbox.countDocuments({{"event.aggregateId":"{rid}"}})')
        check(int(pending)==2,'Redis indisponible : transaction métier et deux messages outbox conservés dans MongoDB')
    finally:
        docker('start','redis')
    final_dashboard = dashboard(rid,riderLevel='GALOP_4',totalBookings=4,completedBookings=1,cancelledBookings=3,totalAmountPaid=45.50)
    check(True,'Republication automatique de l’outbox et convergence après reprise de Redis')
    report = {'checks':len(CHECKS),'results':CHECKS,'riderId':rid,'dashboardAfterRecovery':final,'dashboardFinal':final_dashboard,'verifiedAt':datetime.now(timezone.utc).isoformat()}
    output = ROOT/'artifacts'/'e2e-results.json'
    output.parent.mkdir(exist_ok=True)
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'{len(CHECKS)} vérifications réussies. Rapport : {output}',flush=True)

if __name__ == '__main__':
    main()
