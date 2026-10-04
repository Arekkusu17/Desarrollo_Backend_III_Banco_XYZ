# Banco XYZ Spring Cloud Seguro y Eventos

Proyecto formativo de Semana 7 para evolucionar el sistema Banco XYZ hacia una arquitectura de microservicios con **Spring Cloud Config Server**, **Eureka Service Discovery**, **Spring Cloud Gateway**, seguridad con **Spring Security**, tolerancia a fallos con **Resilience4j** y mensajeria asincrona con **Kafka**.

El sistema mantiene el backend central con los datos procesados desde archivos legacy y los tres BFF independientes por canal construidos en semanas anteriores. En esta version, el retiro del canal ATM se procesa mediante una saga coreografiada basada en eventos.

## Objetivo

Implementar una arquitectura distribuida para Banco XYZ usando Spring Cloud, manteniendo la separacion por canales BFF e incorporando configuracion centralizada, descubrimiento de servicios, entrada por Gateway, autenticacion/autorizacion, resiliencia ante fallas y procesamiento asincrono de transacciones mediante Kafka.

## Evolucion Semana 6

La actividad pide:

- Configurar un servidor centralizado de configuracion consumido por al menos un microservicio.
- Habilitar Service Discovery y registrar al menos un microservicio.
- Implementar un microservicio con tolerancia a fallos y autenticacion.

Este proyecto cumple esos puntos asi:

| Requisito | Implementacion |
| --- | --- |
| Config Server | Modulo `config-server` en puerto `8888`, con repositorio nativo en `config-repo`. |
| Service Discovery | Modulo `discovery-server` con Eureka en puerto `8761`. |
| Microservicio registrado | `core-banking`, `bff-web`, `bff-mobile`, `bff-atm` y `api-gateway`. |
| Tolerancia a fallos | Circuit Breaker en `bff-web` para el dashboard cuando `core-banking` no responde. |
| Seguridad | Spring Security con HTTP Basic y roles por canal, conservando respuestas `401` y `403`. |
| Gateway | Modulo `api-gateway` en puerto `8084`, enruta hacia los BFF mediante Eureka. |

## Evolucion Semana 7

La actividad pide configurar tolerancia a fallos y arquitectura de eventos con microservicios en la nube. Este proyecto implementa esa evolucion asi:

| Requisito | Implementacion |
| --- | --- |
| Arquitectura de eventos | Saga coreografiada basada en eventos de dominio. |
| Mensajeria | Apache Kafka en modo KRaft, configurado en `docker-compose.yml`. |
| Transaccion asincrona | Retiro ATM iniciado por `bff-atm` y procesado por `core-banking`, `risk-service` y `notification-service`. |
| Tolerancia a fallos | Circuit Breaker `riskService` en `risk-service`, con fallback que rechaza la transaccion y dispara compensacion. |
| Idempotencia | Cada consumidor guarda `eventId` procesados en memoria para ignorar duplicados. |
| Escalabilidad | Topicos con tres particiones y `notification-service-group` para demostrar consumer groups. |

La decision tecnica es usar **Kafka + Saga coreografiada**. Cada servicio reacciona a eventos publicados por otros servicios sin invocaciones sincronas entre todos los participantes de la transaccion. La compensacion se realiza con `funds.release-requested` cuando el riesgo rechaza una operacion despues de reservar fondos.

### Topicos Semana 7

| Topico | Productor | Consumidor | Proposito |
| --- | --- | --- | --- |
| `withdrawal.requested` | `bff-atm` | `core-banking` | Solicitar retiro asincrono. |
| `funds.reserved` | `core-banking` | `risk-service` | Indicar que los fondos fueron reservados. |
| `funds.rejected` | `core-banking` | `bff-atm` | Rechazar por cuenta inexistente, monto invalido o saldo insuficiente. |
| `risk.approved` | `risk-service` | `bff-atm` | Aprobar la operacion por riesgo. |
| `risk.rejected` | `risk-service` | `bff-atm` | Rechazar por riesgo o fallback de Resilience4j. |
| `withdrawal.confirmed` | `bff-atm` | `notification-service` | Notificar retiro confirmado. |
| `withdrawal.cancelled` | `bff-atm` | `notification-service` | Notificar retiro cancelado. |
| `funds.release-requested` | `bff-atm` | `core-banking` | Pedir compensacion de fondos reservados. |
| `funds.released` | `core-banking` | `notification-service` | Registrar liberacion compensatoria. |

El diagrama de la solucion esta en [docs/architecture/semana-7-event-driven-banking.md](docs/architecture/semana-7-event-driven-banking.md).

## Estrategia BFF

La estrategia seleccionada es **un BFF por canal**. Esta decision permite separar contratos, respuestas y validaciones segun el tipo de cliente.

| Canal | BFF | Proposito |
| --- | --- | --- |
| Web | `bff-web` | Entregar una vista completa para navegadores, con saldo, datos de cuenta, movimientos y alertas operativas. |
| Mobile | `bff-mobile` | Entregar una respuesta liviana con datos esenciales, optimizada para menor consumo y carga rapida. |
| Cajero automatico | `bff-atm` | Entregar operaciones acotadas y seguras para consulta de saldo y retiro simulado. |

## Arquitectura

![Arquitectura BFF Banco XYZ](evidencias/00-arquitectura-bff.png)

```text
Cliente / Postman
       |
       v
API Gateway :8084
       |
       +--> BFF WEB :8081 ----\
       +--> BFF MOBILE :8082 --+--> CORE BANKING :8080 --> PostgreSQL
       +--> BFF ATM :8083 ----/

Config Server :8888 --> configuracion centralizada
Eureka :8761        --> registro y descubrimiento de servicios
```

El flujo de cada BFF mantiene la separacion:

```text
Controller -> Service -> Client -> Core Banking
```

## Estructura del proyecto

```text
Desarrollo_Backend_III_Banco_XYZ/
|-- core-banking/    Backend central con Spring Batch, PostgreSQL y API REST
|-- bff-common/      RestClient compartido y seguridad comun de los BFF
|-- bff-web/         BFF para banca web
|-- bff-mobile/      BFF para app movil
|-- bff-atm/         BFF para cajeros automaticos
|-- shared-events/   Contratos de eventos Kafka compartidos
|-- risk-service/    Servicio de evaluacion de riesgo con Resilience4j
|-- notification-service/ Consumidor de eventos finales y auditoria
|-- config-server/   Configuracion centralizada Spring Cloud
|-- discovery-server/ Registro Eureka para descubrimiento de servicios
|-- api-gateway/     Entrada unica hacia los BFF con Spring Cloud Gateway
|-- postman/         Coleccion de prueba de APIs
|-- evidencias/      Capturas de ejecucion para la entrega
|-- scripts/         Script para certificado HTTPS de laboratorio
|-- docker-compose.yml
|-- pom.xml
```

Cada modulo BFF mantiene su logica propia en paquetes `controller`, `model`, `service` y `client`. El modulo `bff-common` concentra la configuracion tecnica reutilizable: `RestClient`, usuarios de laboratorio, autenticacion HTTP Basic, autorizacion por rol y respuestas `401/403`.

## Requisitos

- Java 21.
- Docker y Docker Compose.
- Maven Wrapper incluido en el proyecto.
- `keytool` disponible en el JDK.
- Postman para ejecutar la coleccion de validacion.

## Certificado HTTPS

Los BFF usan HTTPS con un certificado autofirmado de laboratorio. Para generarlo:

```bash
./scripts/generar-certificados.sh
```

El script copia `keystore.p12` a:

```text
bff-web/src/main/resources/
bff-mobile/src/main/resources/
bff-atm/src/main/resources/
```

La clave local del keystore es `changeit`. Es una credencial didactica y no debe usarse en produccion.

## Ejecucion

Desde la raiz del proyecto, levantar todos los servicios con Docker Compose:

```bash
docker compose up -d --build
```

El comando construye las imagenes cuando existen cambios y deja ejecutando PostgreSQL, Kafka, Core Banking, los tres BFF, Risk Service, Notification Service, Config Server, Eureka y Gateway.

Para revisar el estado de los contenedores:

```bash
docker compose ps
```

Para detener el ambiente:

```bash
docker compose down
```

Si se requiere eliminar tambien los datos locales de PostgreSQL:

```bash
docker compose down -v
```

Cada Dockerfile usa una etapa Maven con Java 21 para compilar el jar dentro de la imagen y una etapa final `eclipse-temurin:21-jre` para ejecutar el servicio. Por eso no es necesario empaquetar los modulos manualmente antes de usar Compose.

Puertos utilizados:

| Servicio | URL |
| --- | --- |
| Kafka | `kafka:9092` dentro de Docker, `localhost:29092` para clientes locales |
| Core Banking | `http://localhost:8080` |
| BFF Web | `https://localhost:8081` en ejecucion local directa, `http://localhost:8081` con Docker Compose |
| BFF Mobile | `https://localhost:8082` en ejecucion local directa, `http://localhost:8082` con Docker Compose |
| BFF ATM | `https://localhost:8083` en ejecucion local directa, `http://localhost:8083` con Docker Compose |
| API Gateway | `http://localhost:8084` |
| Notification Service | `http://localhost:8085` |
| Risk Service | `http://localhost:8090` |
| Config Server | `http://localhost:8888` |
| Eureka | `http://localhost:8761` |

En Docker Compose los BFF se ejecutan con `BFF_SSL_ENABLED=false` para que el Gateway pueda enrutar internamente por HTTP sin problemas de certificados autofirmados. La seguridad por usuario y rol se mantiene.

## Usuarios de laboratorio

| Usuario | Password | Rol | Canal |
| --- | --- | --- | --- |
| `webuser` | `web123` | `WEB` | Web |
| `mobileuser` | `mobile123` | `MOBILE` | Mobile |
| `atmuser` | `atm123` | `ATM` | Cajero automatico |

Las claves se pueden reemplazar con variables de entorno: `WEB_PASSWORD`, `MOBILE_PASSWORD` y `ATM_PASSWORD`.

## Seguridad

Cada BFF publica sus endpoints por HTTPS y valida credenciales con Spring Security. La autenticacion usa HTTP Basic y la autorizacion se define por rol:

| BFF | Ruta protegida | Rol requerido |
| --- | --- | --- |
| `bff-web` | `/web/**` | `WEB` |
| `bff-mobile` | `/mobile/**` | `MOBILE` |
| `bff-atm` | `/atm/**` | `ATM` |

Sin credenciales o con credenciales invalidas, el BFF responde `401 Unauthorized`. Con un usuario valido pero de otro canal, responde `403 Forbidden`.

## Pruebas

Ejecutar la suite completa:

```bash
./mvnw test
```

La coleccion Postman esta disponible en:

```text
postman/Banco_XYZ_Semana_5.postman_collection.json
postman/Banco_XYZ_Semana_6.postman_collection.json
```

## APIs Spring Cloud Semana 6

### Config Server

```bash
curl http://localhost:8888/bff-web/default
curl http://localhost:8888/core-banking/default
curl http://localhost:8888/api-gateway/default
```

### Eureka

Abrir en navegador:

```text
http://localhost:8761
```

Deberian aparecer, entre otros:

- `CORE-BANKING`
- `BFF-WEB`
- `BFF-MOBILE`
- `BFF-ATM`
- `API-GATEWAY`

### Gateway

Con Docker Compose:

```bash
curl -u webuser:web123 http://localhost:8084/gateway/web/cuentas/101/dashboard
curl -u mobileuser:mobile123 http://localhost:8084/gateway/mobile/cuentas/101/inicio
curl -u atmuser:atm123 http://localhost:8084/gateway/atm/cuentas/101/saldo
```

### Retiro asincrono Semana 7

Iniciar un retiro ATM por Gateway:

```bash
curl -i -u atmuser:atm123 -X POST http://localhost:8084/gateway/atm/cuentas/101/retiros \
  -H "Content-Type: application/json" \
  -d '{"amount":10000,"pin":"1234"}'
```

Resultado esperado: `202 Accepted`, con `transactionId` y estado `PENDING`.

Consultar el estado reemplazando `UUID` por el `transactionId` recibido:

```bash
curl -u atmuser:atm123 http://localhost:8084/gateway/atm/transacciones/UUID
```

Resultado esperado para un flujo exitoso: estado `CONFIRMED` y `authorizationCode`.

Para demostrar idempotencia, republicar deliberadamente el mismo `WithdrawalRequested`:

```bash
curl -u atmuser:atm123 -X POST http://localhost:8084/gateway/atm/transacciones/UUID/replay-requested
```

En los logs de `core-banking` debe verse un mensaje similar a `WithdrawalRequested duplicado ignorado`.

### Rechazo y compensacion

Activar falla del servicio de riesgo:

```bash
curl -X POST "http://localhost:8090/api/risk/admin/failure?enabled=true"
```

Crear un retiro nuevo:

```bash
curl -u atmuser:atm123 -X POST http://localhost:8084/gateway/atm/cuentas/101/retiros \
  -H "Content-Type: application/json" \
  -d '{"amount":15000,"pin":"1234"}'
```

Resultado esperado: la transaccion pasa a `RISK_REJECTED`, `bff-atm` publica `funds.release-requested`, `core-banking` publica `funds.released` y `notification-service` registra la cancelacion.

Revisar el Circuit Breaker:

```bash
curl http://localhost:8090/actuator/circuitbreakers
```

Desactivar la falla:

```bash
curl -X POST "http://localhost:8090/api/risk/admin/failure?enabled=false"
```

### Consumer groups

Con el primer `notification-service` corriendo en Docker, se puede iniciar otra instancia local:

```bash
INSTANCE_ID=notification-2 KAFKA_BOOTSTRAP_SERVERS=localhost:29092 ./mvnw -pl notification-service spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8086"
```

Al crear varias transacciones, los mensajes de `withdrawal.confirmed` y `withdrawal.cancelled` se distribuyen dentro del grupo `notification-service-group`.

### Resiliencia

El endpoint web tiene Circuit Breaker:

```bash
curl -u webuser:web123 http://localhost:8084/gateway/web/cuentas/101/dashboard
```

Si `core-banking` no esta disponible, `bff-web` responde de forma degradada con:

```json
{
  "channel": "WEB",
  "backendStatus": "CORE_BANKING_NO_DISPONIBLE_CIRCUIT_BREAKER"
}
```

## API del core bancario

```bash
curl http://localhost:8080/api/estado
curl http://localhost:8080/api/cuentas
curl http://localhost:8080/api/cuentas/101
curl http://localhost:8080/api/cuentas/101/resumen
curl http://localhost:8080/api/cuentas/101/saldo
curl "http://localhost:8080/api/cuentas/101/movimientos?limit=5"
curl "http://localhost:8080/api/transacciones?onlyAnomalies=true&limit=5"
curl "http://localhost:8080/api/rechazos?limit=5"
```

## APIs BFF

### BFF Web

```bash
curl -k -u webuser:web123 https://localhost:8081/web/cuentas/101/dashboard
```

Con Docker Compose, usar HTTP porque el Gateway enruta por HTTP interno:

```bash
curl -u webuser:web123 http://localhost:8081/web/cuentas/101/dashboard
```

Respuesta esperada: dashboard con datos de cuenta, saldo disponible, ultimos movimientos, alertas y secciones visibles para web.

### BFF Mobile

```bash
curl -k -u mobileuser:mobile123 https://localhost:8082/mobile/cuentas/101/inicio
```

```bash
curl -u mobileuser:mobile123 http://localhost:8082/mobile/cuentas/101/inicio
```

Respuesta esperada: resumen liviano con saldo, datos principales de cuenta, ultimos movimientos y acciones rapidas.

### BFF ATM

```bash
curl -k -u atmuser:atm123 https://localhost:8083/atm/cuentas/101/saldo
```

```bash
curl -u atmuser:atm123 http://localhost:8083/atm/cuentas/101/saldo
```

```bash
curl -k -u atmuser:atm123 -X POST https://localhost:8083/atm/cuentas/101/retiros \
  -H "Content-Type: application/json" \
  -d '{"amount":1000,"pin":"1234"}'
```

Respuesta esperada: consulta de saldo o retiro simulado aprobado cuando el monto y el PIN cumplen las reglas del canal.

## Validaciones sugeridas

| Validacion | Resultado esperado |
| --- | --- |
| `./mvnw test` | `BUILD SUCCESS` |
| `GET /api/estado` | `200 OK` |
| Web sin credenciales | `401 Unauthorized` |
| Web con `webuser:web123` | `200 OK` |
| Web con `mobileuser:mobile123` | `403 Forbidden` |
| Mobile sin credenciales | `401 Unauthorized` |
| Mobile con `mobileuser:mobile123` | `200 OK` |
| Mobile con `atmuser:atm123` | `403 Forbidden` |
| ATM sin credenciales | `401 Unauthorized` |
| ATM con `atmuser:atm123` | `200 OK` |
| ATM con `webuser:web123` | `403 Forbidden` |
| Retiro ATM con usuario, monto y PIN valido | `200 OK` con retiro aprobado |

## Evidencia de ejecucion

Las capturas de `evidencias/` documentan la ejecucion del sistema y la validacion de los endpoints solicitados.

### Servicios levantados

![Servicios levantados](evidencias/01-servicios-levantados.png)

### Pruebas automatizadas

![Pruebas Maven exitosas](evidencias/02-mvn-test-success.png)

### Core bancario

![Core bancario respondiendo](evidencias/03-core-bancario.png)

### BFF Web

![BFF Web autorizado](evidencias/04-bff-web-autorizado.png)

### BFF Mobile

![BFF Mobile autorizado](evidencias/05-bff-mobile-autorizado.png)

### BFF ATM

![BFF ATM saldo autorizado](evidencias/06-bff-atm-saldo-autorizado.png)

![BFF ATM retiro autorizado](evidencias/07-bff-atm-retiro-autorizado.png)

### Seguridad por canal

![Rechazo sin credenciales](evidencias/08-rechazo-sin-credenciales.png)

![Rechazo por rol incorrecto](evidencias/09-rechazo-rol-incorrecto.png)

### Coleccion Postman

![Coleccion Postman ejecutada](evidencias/10-postman-collection-run.png)
