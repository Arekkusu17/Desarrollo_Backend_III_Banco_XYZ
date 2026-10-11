# Banco XYZ - Semana 9 (EFT)

Proyecto de Desarrollo Backend III con Spring Boot 3, Java 21, Spring Cloud, OAuth2/JWT, Resilience4j, Kafka y Docker Compose.

## Arquitectura y responsabilidades

- `ms-account`: apertura, mantenimiento, cierre, saldo, movimientos y reserva/compensacion de fondos.
- `ms-payment`: depositos, pagos y transferencias contra el saldo operativo; idempotencia, reintentos y outbox Kafka.
- `ms-customer`: gestion persistente de perfiles de clientes.
- `batch-service`: tres jobs Spring Batch para transacciones diarias, intereses mensuales y estados anuales. No es el core transaccional.
- `bff-web`, `bff-mobile`, `bff-atm`: respuestas por canal; el ATM mantiene una saga persistente de retiro.
- `api-gateway`, `auth-server`, `config-server`, `discovery-server`: entrada, seguridad OAuth2, configuracion y descubrimiento Eureka.
- `risk-service`, `notification-service`, `shared-events`: riesgo, notificaciones y contratos/inbox/outbox de eventos.
- PostgreSQL conserva datos operativos y metadatos batch; Kafka transporta eventos asincronos.

```text
Postman -> API Gateway -> BFF Web / Mobile / ATM -> ms-account
                       -> ms-account / ms-payment / ms-customer
ms-payment -> ms-account (operaciones internas con account.execute)
ATM -> Kafka -> ms-account -> risk-service -> confirmacion o compensacion
batch-service -> PostgreSQL + reporte anual CSV
OAuth2/JWT + Eureka + Config Server
```

El uso de una base PostgreSQL compartida simplifica esta demostracion academica. No equivale a un despliegue productivo con bases aisladas, alta disponibilidad o escalabilidad entre multiples hosts.

## Requisitos y ejecucion

Java 21 para Maven y `keytool`; Docker Engine y Compose compatible con `!reset`/`!override` (2.24.4 o posterior); Postman y curl.

```bash
./mvnw verify
docker compose up -d --build --remove-orphans
docker compose ps
```

El comando Compose anterior usa HTTP y solo se recomienda para desarrollo local. En EC2 seguir el procedimiento HTTPS de [despliegue](entregables/despliegue.md). Si el puerto 9000 esta ocupado, exportar `AUTH_SERVER_PORT=9001` antes de ejecutar Compose y ajustar Postman; el puerto interno sigue siendo 9000.

## Documentacion de entrega

- [Instrucciones y pruebas](entregables/instrucciones.md): OAuth2, Postman, operaciones, batch y recuperacion.
- [Despliegue S3 y EC2](entregables/despliegue.md): requisitos, instalacion, HTTPS y demostracion temporal de escalabilidad.

Este README es el unico README general de entrega. El informe tecnico y el video estan pendientes; no se incluyen en esta etapa. Los documentos de semanas anteriores y las imagenes existentes son antecedentes, no evidencia de una ejecucion EFT en AWS.

## Puertos de laboratorio

| Servicio | Puerto del host |
| --- | --- |
| Auth Server | 9000 (configurable con AUTH_SERVER_PORT) |
| Gateway | 8084 |
| BFF Web / Mobile / ATM | 8081 / 8082 / 8083 |
| ms-account / ms-payment / ms-customer | 8091 / 8092 / 8093 |
| Batch / Risk / Notification | 8080 / 8090 / 8085 |
| Config / Eureka | 8888 / 8761 |
| PostgreSQL / Kafka externo | 5432 / 29092 |

Los tres microservicios dejan de publicar puertos directos al aplicar `docker-compose.scale.yml`. En ese modo se prueban a traves del Gateway. No abrir PostgreSQL, Kafka ni las consolas administrativas a Internet.

## Validacion y limites

La ultima corrida local completo 56 tests unitarios y 4 pruebas de integracion: terminacion abrupta de cada job y caida temporal de PostgreSQL. La recuperacion conserva checkpoints por particion, impide tomar un job con propietario activo y produce resultados equivalentes a una ejecucion sin fallos.

Las pruebas de integracion requieren una base aislada y variables explicitas; sin ellas Maven las omite. Ver [instrucciones](entregables/instrucciones.md).

Se utilizan los tres CSV de `batch-service/src/main/resources/input/eft/`, con 1000 registros cada uno, procedentes de [fin_legacy_data](https://github.com/KariVillagran/fin_legacy_data). No se dispone de un programa COBOL ejecutable: no se afirma equivalencia comprobada contra COBOL ni una mejora de rendimiento medida frente al legacy.

No se realizaran pruebas de carga o gran volumen. Las proyecciones de rendimiento se presentaran como estimaciones, indicando supuestos; no como resultados experimentales.

El despliegue y la escalabilidad en EC2 estan pendientes de validacion. El criterio 6 pide Docker y escalabilidad horizontal para los tres microservicios en la nube, sin fijar replicas permanentes. Se mantendra una instancia por servicio y se aumentaran temporalmente las replicas para obtener evidencia.

## Flujo Git

La version candidata esta en `feature/eft-compliance`. Las subetapas se integran mediante branches feature. El PR a `develop` queda para revision y pruebas en EC2, sin merge automatico.
