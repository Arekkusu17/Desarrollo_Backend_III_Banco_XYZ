# Propuesta tecnica Semana 6 - Banco XYZ

## Objetivo

Evolucionar Banco XYZ desde una arquitectura BFF por canal hacia una arquitectura de microservicios con Spring Cloud, manteniendo el dominio bancario construido con datos legacy y agregando configuracion centralizada, descubrimiento de servicios, Gateway, seguridad y tolerancia a fallos.

## Arquitectura propuesta

```text
Cliente / Postman
       |
       v
API Gateway
       |
       +--> BFF Web
       +--> BFF Mobile
       +--> BFF ATM
              |
              v
        Core Banking
              |
              v
          PostgreSQL

Config Server entrega configuracion a los servicios.
Eureka permite descubrir servicios por nombre logico.
```

## Componentes

| Componente | Responsabilidad |
| --- | --- |
| `config-server` | Centraliza configuracion de Core Banking, BFF y Gateway. |
| `discovery-server` | Registra y expone servicios disponibles mediante Eureka. |
| `api-gateway` | Punto de entrada unico para los canales Web, Mobile y ATM. |
| `core-banking` | Expone APIs bancarias construidas sobre la migracion de datos legacy. |
| `bff-web` | Agrega datos para banca web y demuestra resiliencia con Circuit Breaker. |
| `bff-mobile` | Entrega una respuesta liviana para app movil. |
| `bff-atm` | Entrega operaciones acotadas para cajero automatico. |

## Configuracion centralizada

El modulo `config-server` usa perfil `native` y lee configuraciones desde `config-server/src/main/resources/config-repo`.

Archivos principales:

- `core-banking.yml`
- `bff-web.yml`
- `bff-mobile.yml`
- `bff-atm.yml`
- `api-gateway.yml`

Cada microservicio define `spring.config.import=optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}` para poder ejecutarse con o sin Config Server durante desarrollo.

## Descubrimiento de servicios

Eureka se ejecuta en el puerto `8761`. Los servicios se registran usando `spring.application.name`, lo que permite consumir dependencias por nombre logico.

Ejemplo:

```text
http://core-banking
```

en lugar de una IP o puerto fijo.

## Gateway

Spring Cloud Gateway escucha en el puerto `8084` y publica rutas de entrada:

| Ruta externa | Servicio destino |
| --- | --- |
| `/gateway/web/**` | `lb://bff-web` |
| `/gateway/mobile/**` | `lb://bff-mobile` |
| `/gateway/atm/**` | `lb://bff-atm` |

El prefijo `/gateway` se reescribe antes de llegar a cada BFF.

## Seguridad

La autenticacion se mantiene con HTTP Basic para laboratorio. La autorizacion usa roles por canal:

| Usuario | Rol | Canal |
| --- | --- | --- |
| `webuser` | `WEB` | Web |
| `mobileuser` | `MOBILE` | Mobile |
| `atmuser` | `ATM` | ATM |

Casos esperados:

- Sin credenciales: `401 Unauthorized`.
- Usuario valido sin rol del canal: `403 Forbidden`.
- Usuario valido con rol correcto: `200 OK`.

## Tolerancia a fallos

`bff-web` aplica Circuit Breaker sobre la llamada al resumen de cuenta de `core-banking`.

Cuando el core esta disponible, el dashboard responde con datos completos. Cuando el core falla, el BFF responde una vista degradada con `backendStatus=CORE_BANKING_NO_DISPONIBLE_CIRCUIT_BREAKER`, listas vacias y secciones reducidas.

La degradacion se aplica a una lectura de dashboard. Operaciones criticas no deberian simular exito si no se puede confirmar el resultado.

## Evidencias sugeridas

- Config Server respondiendo `http://localhost:8888/bff-web/default`.
- Eureka con servicios registrados.
- Docker Compose con servicios arriba.
- Gateway respondiendo para Web, Mobile y ATM.
- Seguridad `401`, `403` y `200`.
- Respuesta degradada de `bff-web` con Core Banking detenido.
- `./mvnw test` exitoso.

