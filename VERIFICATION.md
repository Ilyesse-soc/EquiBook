# Bilan de vérification EquiBook

Vérifications locales effectuées le **26 octobre 2026**, dans `D:\equibook`.

## Livrables réalisés

Les cinq services Spring Boot Java 21, leurs configurations et leurs API REST sont implémentés. MongoDB stocke les agrégats, réplicas, résultats des commandes, marqueurs d’idempotence et boîtes d’envoi. Redis transporte les événements JSON entre services. Le dashboard est construit uniquement depuis ses instantanés locaux alimentés par les événements.

Le dépôt contient 36 contrats JSON Schema : 14 commandes, 14 événements, 5 agrégats et 3 réplicas. Il contient également le wrapper Gradle, le Dockerfile multi-étapes, Docker Compose, deux parcours HTTP pour VS Code et IntelliJ, les scripts de tests et de génération, et une CI GitHub. Le README français contient les 22 sections demandées, le diagramme et la structure du projet. La section nominative contient uniquement Ilyesse EL ADAOUI.

## Builds et tests JUnit

Commande exécutée avec Java 21 dans Docker :

```powershell
docker run --rm --mount type=bind,source=D:\equibook,target=/workspace --mount type=volume,source=equibook-gradle-cache,target=/home/gradle/.gradle -w /workspace gradle:8.14.3-jdk21 ./gradlew --no-daemon clean build
```

Résultat : **BUILD SUCCESSFUL**, 22 tests, 0 échec, 0 erreur, 0 test ignoré.

| Service | Tests réussis | Couverture principale |
|---|---:|---|
| rider-service | 3 | Création, niveau, validation et rejeu des commandes |
| horse-service | 2 | Création, disponibilité, réservation, libération et anciennes versions |
| booking-service | 12 | Réplicas, validations, paiement requis, confirmation, annulation, fin de séance, doublons et verrou du cheval |
| payment-service | 3 | Réplica Booking, création, confirmation, échec, remboursement et réservation annulée |
| dashboard-service | 2 | Compteurs, montants, remboursement, doublons, versions et ordre d’arrivée |

Les rapports XML et HTML sont présents dans `build/test-results/test` et `build/reports/tests/test` de chaque service. Les cinq images Docker finales ont également été construites avec succès ; leur étape de build exécute ces tests.

## Intégration avec MongoDB et Redis réels

```shell
python scripts/e2e.py
```

Résultat : **51 vérifications réussies**. Le rapport détaillé de cette exécution est conservé dans [docs/verification/e2e-results.json](docs/verification/e2e-results.json). Les nouvelles exécutions écrivent leur rapport dans `artifacts/e2e-results.json`.

Les vérifications couvrent :

- La santé des cinq API et les 36 JSON Schema.
- La création et la modification du cavalier, du niveau, du cheval et de sa disponibilité.
- La création et la mise à jour effectives des réplicas Rider et Horse dans Booking.
- Le refus des réservations en cas de niveau insuffisant ou de cheval indisponible.
- Le refus de confirmer une réservation avant réception du paiement confirmé.
- La création du réplica Booking dans Payment, puis la création et la confirmation du paiement.
- La confirmation de la réservation et le passage du cheval source à RESERVED.
- La fin de séance, la libération du cheval et la mise à jour de la projection.
- L’annulation d’une réservation confirmée et son remboursement.
- L’échec d’un paiement et le refus de le confirmer ensuite.
- Deux commandes de réservation concurrentes : exactement un succès 201 et un refus 409.
- La lecture directe des collections MongoDB pour confirmer la persistance des agrégats, réplicas et marqueurs traités.

## Interruptions, reprises et persistance

Pendant l’arrêt de Dashboard, les événements d’annulation et de remboursement sont restés dans Redis. Un même événement réel de remboursement a été présent trois fois ; après reprise, le montant n’a été réduit qu’une fois.

Redis a été redémarré avec des messages en attente : les messages sont restés disponibles grâce à AOF. Un message a ensuite été déplacé vers `events:dashboard:processing` avant redémarrage du consommateur ; celui-ci l’a repris et acquitté correctement.

Les sept conteneurs EquiBook ont été arrêtés puis redémarrés. Les réponses REST ont été comparées avant et après pour le cavalier, le cheval, les réservations, les paiements PAID, REFUNDED et FAILED, et le dashboard : **les données étaient identiques**. Une commande de création antérieure a été rejouée après redémarrage et a renvoyé le même résultat initial.

Redis a également été arrêté pendant une modification de niveau. La commande a été enregistrée avec ses deux messages sortants dans MongoDB. Après reprise de Redis, les événements ont été republiés automatiquement et le dashboard a convergé vers GALOP_4.

## Contrôle final

```shell
python scripts/verify_results.py
```

Résultat : **7 conteneurs healthy**, **0 message restant dans les files Redis ou dans processing**, **0 événement restant dans les outbox MongoDB**. Le résumé machine de cette exécution est conservé dans [docs/verification/verification.json](docs/verification/verification.json). Les nouvelles exécutions écrivent `artifacts/verification.json`.

La projection réelle utilisée pour la vérification est consultable sur [l’API Dashboard locale](http://localhost:18085/dashboard/riders/26ba94d4-6128-4e2c-93c6-1a10290e113e). Après le scénario final, elle contient 4 réservations, dont 1 terminée et 3 annulées, 0 confirmation active, un total net payé de 45,50 et le niveau GALOP_4.

Les fichiers sources et livrables inspectés ne contiennent aucun fichier vide. Les contrats sont valides. Git est initialisé sur `main`, les fichiers du projet sont préparés dans l’index, le wrapper est marqué exécutable sous Linux et `.env`, les données de build et les rapports locaux sont exclus de Git.

## Lancement et ports locaux

```shell
docker compose up --build -d --wait --wait-timeout 300
docker compose ps
```

Les ports demandés restent les valeurs par défaut du dépôt. Comme ils étaient déjà occupés sur cette machine, le `.env` local utilise :

| Composant | Port local | Port interne |
|---|---:|---:|
| Rider | 18081 | 8081 |
| Horse | 18082 | 8082 |
| Booking | 18083 | 8083 |
| Payment | 18084 | 8084 |
| Dashboard | 18085 | 8085 |
| MongoDB | 37017 | 27017 |
| Redis | 16379 | 6379 |

Les commandes d’arrêt, de redémarrage et de recréation n’ont ciblé que le projet Compose `equibook`. Aucun autre projet Docker n’a été arrêté ni reconfiguré.

## Limites et état de livraison

Aucun problème bloquant n’a été détecté dans les parcours vérifiés. Les services sont démarrés et leurs données de test sont conservées.

La cohérence entre services reste éventuelle. Le modèle prévoit une seule réservation active par cheval et un seul paiement par réservation. Les consommateurs Redis fonctionnent avec une seule instance par service. Les événements invalides restent dans `processing` et demandent une correction manuelle ; aucun message n’est dans cet état à la fin des tests. Les paiements représentent des états métier ; aucun débit bancaire externe n’est effectué. Ces limites sont détaillées dans le README.

La CI est fournie mais son exécution distante n’a pas été observée ; les commandes correspondantes ont été exécutées localement. Les fichiers HTTP ont été générés pour les deux éditeurs ; les parcours REST ont été exécutés par le script d’intégration. Aucune action humaine n’est nécessaire pour relancer et tester le projet localement avec Docker et les dépendances Python indiquées.
