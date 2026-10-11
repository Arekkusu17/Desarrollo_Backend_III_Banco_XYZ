# Codigo fuente Banco XYZ Semana 9

## Repositorio

Codigo fuente completo disponible en GitHub:

https://github.com/Arekkusu17/Desarrollo_Backend_III_Banco_XYZ

## Rama de entrega

La version integrada para la Evaluacion Final Transversal se encuentra en:

```text
develop
```

## Descripcion general

El proyecto implementa una arquitectura backend moderna para Banco XYZ basada en Spring Boot y Spring Cloud. La solucion migra procesos batch legacy, separa capacidades del core bancario en microservicios, incorpora BFF por canal, protege endpoints con OAuth2/JWT, utiliza Eureka para descubrimiento de servicios, Config Server para configuracion centralizada, Kafka para mensajeria asincrona y Docker Compose para orquestacion local.

## Componentes principales

| Componente | Proposito |
| --- | --- |
| `config-server` | Configuracion centralizada de los servicios. |
| `discovery-server` | Registro Eureka para descubrimiento dinamico. |
| `auth-server` | Servidor OAuth2 para emision de JWT. |
| `api-gateway` | Entrada unica a BFFs y microservicios protegidos. |
| `ms-account` | Gestion de consultas de cuentas, saldos, resumen y movimientos. |
| `ms-payment` | Procesamiento de pagos, depositos y transferencias. |
| `ms-customer` | Gestion de perfiles e informacion de clientes. |
| `batch-service` | Procesos batch migrados desde legacy y operaciones de reserva/liberacion de fondos. |
| `bff-web` | Backend for Frontend para canal web. |
| `bff-mobile` | Backend for Frontend para canal movil. |
| `bff-atm` | Backend for Frontend para cajeros automaticos. |
| `risk-service` | Evaluacion de riesgo y resiliencia con Resilience4j. |
| `notification-service` | Consumo de eventos y auditoria operacional. |
| `shared-events` | Contratos compartidos para eventos Kafka. |

## Entregables incluidos

| Archivo | Contenido |
| --- | --- |
| `entregables/readme.md` | Link al repositorio y resumen del codigo fuente. |
| `entregables/instrucciones.md` | Pasos para ejecutar y probar cada componente. |
| `entregables/despliegue.md` | Pasos para despliegue en entorno cloud AWS. |
| `postman/Banco_XYZ_Semana_8.postman_collection.json` | Coleccion Postman actualizada para Semana 9. |

## Requisitos tecnicos

- Java 21.
- Docker Desktop o Docker Engine con Docker Compose.
- Git.
- Postman para pruebas manuales.
- Conexion a internet para descargar dependencias Maven e imagenes Docker.

