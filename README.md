# Banco XYZ BFF Seguro

Proyecto formativo de Semana 5 para implementar el patron **Backend for Frontend (BFF)** sobre el backend bancario construido en la continuidad del proyecto Banco XYZ.

El sistema expone un backend central con los datos procesados desde archivos legacy y tres BFF independientes, cada uno adaptado a las necesidades de un canal: web, movil y cajero automatico. La implementacion agrega HTTPS, autenticacion y autorizacion por rol para demostrar acceso seguro por canal.

## Objetivo

Implementar una arquitectura BFF que optimice la comunicacion entre distintos clientes del Banco XYZ y el backend central. Cada canal recibe solo la informacion y las operaciones que necesita, reduciendo acoplamiento, payloads innecesarios y reglas duplicadas en los frontends.

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
WEB ----HTTPS----> BFF WEB :8081 ----\
                                      \
MOBILE -HTTPS----> BFF MOBILE :8082 ---- HTTP interno ----> CORE BANKING :8080
                                      /
ATM ----HTTPS----> BFF ATM :8083 ----/
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
|-- postman/         Coleccion de prueba de APIs
|-- evidencias/      Capturas de ejecucion para la entrega
|-- scripts/         Script para certificado HTTPS de laboratorio
|-- docker-compose.yml
|-- pom.xml
```

Cada modulo BFF mantiene su logica propia en paquetes `controller`, `model`, `service` y `client`. El modulo `bff-common` concentra la configuracion tecnica reutilizable: `RestClient`, usuarios de laboratorio, autenticacion HTTP Basic, autorizacion por rol y respuestas `401/403`.

## Requisitos

- Java 17 o superior.
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

Levantar PostgreSQL:

```bash
docker compose up -d postgres
```

Construir las imagenes Docker de los servicios:

```bash
docker compose build core-banking bff-web bff-mobile bff-atm
```

Cada Dockerfile usa una etapa Maven para compilar el jar dentro de la imagen y una etapa final `eclipse-temurin:17-jre` para ejecutar el servicio.

Ejecutar el core bancario:

```bash
./mvnw -pl core-banking spring-boot:run
```

Ejecutar cada BFF en una terminal distinta:

```bash
./mvnw -pl bff-web spring-boot:run
./mvnw -pl bff-mobile spring-boot:run
./mvnw -pl bff-atm spring-boot:run
```

Puertos utilizados:

| Servicio | URL |
| --- | --- |
| Core Banking | `http://localhost:8080` |
| BFF Web | `https://localhost:8081` |
| BFF Mobile | `https://localhost:8082` |
| BFF ATM | `https://localhost:8083` |

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

Respuesta esperada: dashboard con datos de cuenta, saldo disponible, ultimos movimientos, alertas y secciones visibles para web.

### BFF Mobile

```bash
curl -k -u mobileuser:mobile123 https://localhost:8082/mobile/cuentas/101/inicio
```

Respuesta esperada: resumen liviano con saldo, datos principales de cuenta, ultimos movimientos y acciones rapidas.

### BFF ATM

```bash
curl -k -u atmuser:atm123 https://localhost:8083/atm/cuentas/101/saldo
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

Reemplazar o actualizar las capturas con los siguientes nombres:

| Archivo | Evidencia esperada |
| --- | --- |
| `evidencias/01-servicios-levantados.png` | Salida de `docker compose ps` con PostgreSQL, core y los tres BFF levantados. |
| `evidencias/02-mvn-test-success.png` | Salida de `./mvnw test` mostrando `BUILD SUCCESS`. |
| `evidencias/03-core-bancario.png` | Respuesta de `curl http://localhost:8080/api/estado`. |
| `evidencias/04-bff-web-autorizado.png` | Respuesta de `curl -k -u webuser:web123 https://localhost:8081/web/cuentas/101/dashboard`. |
| `evidencias/05-bff-mobile-autorizado.png` | Respuesta de `curl -k -u mobileuser:mobile123 https://localhost:8082/mobile/cuentas/101/inicio`. |
| `evidencias/06-bff-atm-saldo-autorizado.png` | Respuesta de `curl -k -u atmuser:atm123 https://localhost:8083/atm/cuentas/101/saldo`. |
| `evidencias/07-bff-atm-retiro-autorizado.png` | Respuesta de retiro ATM autorizado con `atmuser:atm123`, monto valido y PIN `1234`. |
| `evidencias/08-rechazo-sin-credenciales.png` | Respuesta `HTTP/1.1 401` al consumir un BFF sin credenciales. |
| `evidencias/09-rechazo-rol-incorrecto.png` | Respuesta `HTTP/1.1 403` al usar un usuario valido en el BFF de otro canal. |
| `evidencias/10-postman-collection-run.png` | Ejecucion exitosa de `postman/Banco_XYZ_Semana_5.postman_collection.json`. |

## Entrega

El proyecto se debe subir a GitHub junto con este README, la propuesta tecnica y evidencias de ejecucion. Para AVA, todos los componentes deben quedar en una misma carpeta comprimida con la nomenclatura:

```text
Exp2_S5_Nombre_Apellido_Apellido
```
