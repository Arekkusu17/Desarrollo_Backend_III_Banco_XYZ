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
- `bff-web`: entregara respuestas completas para interfaces web.
- `bff-mobile`: entregara respuestas reducidas para mejorar velocidad y consumo de datos.
- `bff-atm`: entregara operaciones acotadas y seguras para cajeros automaticos.

## Ejecucion actual

Levantar PostgreSQL:

```bash
docker compose up -d postgres
```

Ejecutar el core bancario:

```bash
./mvnw -pl core-banking spring-boot:run
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

