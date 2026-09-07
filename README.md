# Banco XYZ BFF

Proyecto formativo de Semana 4 para implementar el patron Backend for Frontend sobre el backend bancario construido en Semana 3.

## Arquitectura

```text
                       +---------------------+
WEB ------------------> | BFF WEB :8081      | -----+
                       +---------------------+      |
                                                     |
                       +---------------------+      v
MOBILE --------------> | BFF MOBILE :8082   | ---> CORE BANKING :8080
                       +---------------------+      ^
                                                     |
                       +---------------------+      |
CAJERO AUTOMATICO --> | BFF ATM :8083      | -----+
                       +---------------------+
```

## Estructura

```text
Desarrollo_Backend_III_Banco_XYZ/
|-- core-banking/    Backend central con Spring Batch, PostgreSQL y API REST
|-- bff-web/         BFF para banca web
|-- bff-mobile/      BFF para app movil
|-- bff-atm/         BFF para cajeros automaticos
|-- postman/         Colecciones de prueba
|-- docker-compose.yml
|-- pom.xml
```

## Servicios

- `core-banking`: procesa los archivos CSV legacy, persiste resultados limpios y expone APIs REST internas para los BFF.
- `bff-web`: entrega respuestas completas para interfaces web.
- `bff-mobile`: entrega respuestas reducidas para mejorar velocidad y consumo de datos.
- `bff-atm`: entrega operaciones acotadas y seguras para cajeros automaticos.

## Ejecucion actual

Levantar PostgreSQL:

```bash
docker compose up -d postgres
```

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

Ejecutar pruebas:

```bash
./mvnw test
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

## Autenticacion y autorizacion por canal

Cada BFF valida el header `X-Channel-Token` antes de atender sus rutas. Se usa un token distinto por canal para representar que Web, Mobile y ATM son clientes diferentes y no comparten exactamente el mismo contrato de acceso.

Esta solucion es intencionalmente simple para el alcance academico del proyecto: permite evidenciar autenticacion y autorizacion especificas por canal sin agregar la complejidad completa de OAuth2 o JWT. En un escenario productivo, estos tokens deberian reemplazarse por un proveedor de identidad, expiracion de credenciales, scopes por canal y auditoria centralizada.

Los tokens locales por defecto son:

```text
WEB-SECRET
MOBILE-SECRET
ATM-SECRET
```

ATM ademas solicita `pin` en el retiro porque ese canal ejecuta operaciones criticas. Web y Mobile solo consultan vistas adaptadas por el BFF.

BFF Web:

```bash
curl http://localhost:8081/web/cuentas/101/dashboard \
  -H "X-Channel-Token: WEB-SECRET"
```

BFF Mobile:

```bash
curl http://localhost:8082/mobile/cuentas/101/inicio \
  -H "X-Channel-Token: MOBILE-SECRET"
```

BFF ATM:

```bash
curl http://localhost:8083/atm/cuentas/101/saldo \
  -H "X-Channel-Token: ATM-SECRET"

curl -X POST http://localhost:8083/atm/cuentas/101/retiros \
  -H "Content-Type: application/json" \
  -H "X-Channel-Token: ATM-SECRET" \
  -d '{"amount":1000,"pin":"1234"}'
```
