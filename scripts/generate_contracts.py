"""Contrats JSON Schema 2020-12, sans identifiants de démonstration."""
import json
from pathlib import Path
from copy import deepcopy

ROOT = Path(__file__).resolve().parents[1] / 'contracts'
STRING = {'type': 'string', 'minLength': 1}
UUID = {'type': 'string', 'format': 'uuid'}
DATE = {'type': 'string', 'format': 'date-time'}
LEVEL = {'type': 'string', 'enum': ['BEGINNER'] + [f'GALOP_{i}' for i in range(1, 8)]}
MONEY = {'type': 'number', 'exclusiveMinimum': 0, 'maximum': 1000000, 'multipleOf': 0.01}
def obj(**props):
    return {'type': 'object', 'additionalProperties': False, 'required': list(props), 'properties': props}
def enum(*values): return {'type':'string', 'enum':list(values)}
def write(folder, name, schema):
    path = ROOT / folder / (name+'.json')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({'$schema':'https://json-schema.org/draft/2020-12/schema', 'title':name, **schema}, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

rider_input = obj(firstName=STRING, lastName=STRING, email={'type':'string', 'format':'email'}, phone=STRING, level=LEVEL)
horse_input = obj(name=STRING, breed=STRING, birthYear={'type':'integer','minimum':1950}, minimumRiderLevel=LEVEL)
rider_replica = obj(riderId=UUID, fullName=STRING, level=LEVEL)
horse_replica = obj(horseId=UUID, name=STRING, minimumRiderLevel=LEVEL, status=enum('AVAILABLE','RESERVED','RESTING','UNAVAILABLE'))
booking_input = obj(riderId=UUID, horseId=UUID, sessionDate=DATE, durationMinutes={'type':'integer','minimum':1}, price=MONEY)
commands = {}
for name, schema in [('CreateRider',rider_input),('UpdateRider',rider_input),('ChangeRiderLevel',obj(level=LEVEL)),('CreateHorse',horse_input),('UpdateHorse',horse_input),('ChangeHorseAvailability',obj(status=horse_replica['properties']['status'])),('CreateBooking',booking_input),('CreatePayment',obj(bookingId=UUID))]:
    commands[name] = schema
for name in ['ConfirmBooking','CancelBooking','CompleteBooking','ConfirmPayment','FailPayment','RefundPayment']:
    commands[name] = obj()
for name, payload in commands.items():
    write('commands', name, obj(commandId=UUID, commandType={'const':name}, occurredAt=DATE, payload=payload))

aggregates = {
    'rider': obj(riderId=UUID, **rider_input['properties'], createdAt=DATE, updatedAt=DATE),
    'horse': obj(horseId=UUID, **horse_input['properties'], status=horse_replica['properties']['status'], createdAt=DATE, updatedAt=DATE),
    'booking': obj(bookingId=UUID, rider=rider_replica, horse=horse_replica, sessionDate=DATE, durationMinutes={'type':'integer','minimum':1}, price=MONEY, status=enum('CREATED','PENDING_PAYMENT','CONFIRMED','CANCELLED','COMPLETED'), createdAt=DATE, updatedAt=DATE),
    'payment': obj(paymentId=UUID, bookingId=UUID, riderId=UUID, amount=MONEY, status=enum('PENDING','PAID','FAILED','REFUNDED'), createdAt=DATE, updatedAt=DATE),
    'rider-dashboard': obj(riderId=UUID, riderName=STRING, riderLevel=LEVEL, **{name:{'type':'integer','minimum':0} for name in ['totalBookings','confirmedBookings','completedBookings','cancelledBookings']}, totalAmountPaid={'type':'number','minimum':0,'multipleOf':0.01}, lastBookingDate={'anyOf':[DATE, {'type':'null'}]})
}
for name, schema in aggregates.items(): write('aggregates', name+'.aggregate', schema)

events = {
    'rider': ['RiderCreated','RiderUpdated','RiderLevelChanged'],
    'horse': ['HorseCreated','HorseUpdated','HorseAvailabilityChanged'],
    'booking': ['BookingCreated','BookingConfirmed','BookingCancelled','BookingCompleted'],
    'payment': ['PaymentCreated','PaymentConfirmed','PaymentFailed','PaymentRefunded']
}
for source, names in events.items():
    for name in names:
        if source == 'rider': receivers = ['booking-service','dashboard-service']
        elif source == 'horse': receivers = ['booking-service']
        elif source == 'booking': receivers = ['payment-service','dashboard-service'] if name == 'BookingCreated' else ['horse-service','payment-service','dashboard-service']
        else: receivers = ['dashboard-service'] if name in ['PaymentCreated','PaymentFailed'] else ['booking-service','dashboard-service']
        write('events', name, obj(eventId=UUID, eventType={'const':name}, occurredAt=DATE, emitterService={'const':source+'-service'}, receivers={'const':receivers}, aggregateId=UUID, aggregateVersion={'type':'integer','minimum':1}, payload=deepcopy(aggregates[source])))

write('replicas', 'rider.replica', rider_replica)
write('replicas', 'horse.replica', horse_replica)
write('replicas', 'booking.replica', obj(bookingId=UUID, riderId=UUID, price=MONEY, bookingStatus=aggregates['booking']['properties']['status']))
print(f'{len(commands)} commandes, {sum(map(len,events.values()))} événements, {len(aggregates)} agrégats et 3 réplicas générés.')
