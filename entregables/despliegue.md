# Despliegue en entorno cloud AWS Banco XYZ

## Objetivo

Este documento describe una estrategia de despliegue cloud para Banco XYZ usando AWS. La arquitectura local con Docker Compose se puede llevar a un entorno administrado mediante imagenes Docker publicadas en Amazon ECR, servicios ejecutados en Amazon ECS Fargate, base de datos en Amazon RDS PostgreSQL, mensajeria en Amazon MSK o Amazon EventBridge, y balanceo mediante Application Load Balancer.

## Arquitectura propuesta en AWS

| Componente local | Servicio AWS recomendado |
| --- | --- |
| Contenedores Docker | Amazon ECS Fargate |
| Imagenes Docker | Amazon ECR |
| PostgreSQL | Amazon RDS for PostgreSQL |
| Kafka | Amazon MSK |
| API Gateway interno del proyecto | ECS Service detras de Application Load Balancer |
| Config Server | ECS Service privado |
| Eureka Discovery Server | ECS Service privado |
| OAuth2 Auth Server | ECS Service privado o publico controlado por ALB |
| Logs de aplicacion | Amazon CloudWatch Logs |
| Secretos | AWS Secrets Manager o SSM Parameter Store |
| Red | Amazon VPC con subnets publicas y privadas |

## 1. Preparar cuenta y herramientas

Instalar y configurar:

```bash
aws --version
docker --version
git --version
```

Configurar credenciales:

```bash
aws configure
```

Seleccionar una region, por ejemplo:

```text
us-east-1
```

## 2. Crear repositorios ECR

Crear un repositorio por servicio:

```bash
aws ecr create-repository --repository-name banco-xyz/config-server
aws ecr create-repository --repository-name banco-xyz/discovery-server
aws ecr create-repository --repository-name banco-xyz/auth-server
aws ecr create-repository --repository-name banco-xyz/api-gateway
aws ecr create-repository --repository-name banco-xyz/ms-account
aws ecr create-repository --repository-name banco-xyz/ms-payment
aws ecr create-repository --repository-name banco-xyz/ms-customer
aws ecr create-repository --repository-name banco-xyz/batch-service
aws ecr create-repository --repository-name banco-xyz/bff-web
aws ecr create-repository --repository-name banco-xyz/bff-mobile
aws ecr create-repository --repository-name banco-xyz/bff-atm
aws ecr create-repository --repository-name banco-xyz/risk-service
aws ecr create-repository --repository-name banco-xyz/notification-service
```

Autenticarse contra ECR:

```bash
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com
```

## 3. Construir imagenes Docker

Desde la raiz del proyecto:

```bash
docker compose build
```

Etiquetar imagenes:

```bash
docker tag desarrollo_backend_iii_banco_xyz-config-server:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/config-server:latest
docker tag desarrollo_backend_iii_banco_xyz-discovery-server:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/discovery-server:latest
docker tag desarrollo_backend_iii_banco_xyz-auth-server:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/auth-server:latest
docker tag desarrollo_backend_iii_banco_xyz-api-gateway:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/api-gateway:latest
docker tag desarrollo_backend_iii_banco_xyz-ms-account:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/ms-account:latest
docker tag desarrollo_backend_iii_banco_xyz-ms-payment:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/ms-payment:latest
docker tag desarrollo_backend_iii_banco_xyz-ms-customer:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/ms-customer:latest
docker tag desarrollo_backend_iii_banco_xyz-batch-service:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/batch-service:latest
docker tag desarrollo_backend_iii_banco_xyz-bff-web:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/bff-web:latest
docker tag desarrollo_backend_iii_banco_xyz-bff-mobile:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/bff-mobile:latest
docker tag desarrollo_backend_iii_banco_xyz-bff-atm:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/bff-atm:latest
docker tag desarrollo_backend_iii_banco_xyz-risk-service:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/risk-service:latest
docker tag desarrollo_backend_iii_banco_xyz-notification-service:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/notification-service:latest
```

Publicar imagenes:

```bash
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/config-server:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/discovery-server:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/auth-server:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/api-gateway:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/ms-account:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/ms-payment:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/ms-customer:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/batch-service:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/bff-web:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/bff-mobile:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/bff-atm:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/risk-service:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/banco-xyz/notification-service:latest
```

## 4. Crear infraestructura base

Crear una VPC con:

- Subnets publicas para Application Load Balancer.
- Subnets privadas para ECS, RDS y MSK.
- NAT Gateway para salida a internet desde servicios privados.
- Security Groups separados por capa.

Reglas minimas:

| Origen | Destino | Puerto |
| --- | --- | --- |
| Internet | ALB | `443` |
| ALB | `api-gateway` | `8084` |
| ECS interno | Config Server | `8888` |
| ECS interno | Eureka | `8761` |
| ECS interno | Auth Server | `9000` |
| ECS interno | RDS PostgreSQL | `5432` |
| ECS interno | MSK Kafka | `9092` |

## 5. Crear base de datos

Crear RDS PostgreSQL:

- Motor: PostgreSQL.
- Base de datos: `banco_xyz`.
- Usuario: `banco`.
- Password: administrar en Secrets Manager.
- Acceso: solo desde Security Group de ECS.

Variables requeridas por servicios con base de datos:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS_ENDPOINT>:5432/banco_xyz
SPRING_DATASOURCE_USERNAME=banco
SPRING_DATASOURCE_PASSWORD=<secret>
```

## 6. Crear mensajeria

Opcion recomendada:

- Amazon MSK para mantener compatibilidad Kafka.

Variables:

```text
KAFKA_BOOTSTRAP_SERVERS=<MSK_BOOTSTRAP_SERVERS>
```

Servicios que usan Kafka:

- `batch-service`
- `bff-atm`
- `risk-service`
- `notification-service`

## 7. Crear cluster ECS

```bash
aws ecs create-cluster --cluster-name banco-xyz-cluster
```

Crear una task definition por servicio. Cada task debe definir:

- Imagen ECR.
- Puerto del contenedor.
- Variables de entorno.
- Log group de CloudWatch.
- CPU y memoria.
- Rol de ejecucion ECS.

## 8. Variables por servicio

Variables comunes:

```text
CONFIG_SERVER_URL=http://config-server:8888
EUREKA_URL=http://discovery-server:8761/eureka/
OAUTH_ISSUER_URI=http://auth-server:9000
```

Variables para `auth-server`:

```text
OAUTH_ISSUER=http://auth-server:9000
OAUTH_DEMO_SECRET=<secret>
OAUTH_BFF_WEB_SECRET=<secret>
OAUTH_BFF_MOBILE_SECRET=<secret>
OAUTH_BFF_ATM_SECRET=<secret>
```

Variables para BFF:

```text
ACCOUNT_SERVICE_URL=http://ms-account
OAUTH_TOKEN_URL=http://auth-server:9000/oauth2/token
```

Variables para servicios con PostgreSQL:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS_ENDPOINT>:5432/banco_xyz
SPRING_DATASOURCE_USERNAME=banco
SPRING_DATASOURCE_PASSWORD=<secret>
```

## 9. Orden de despliegue recomendado

1. `config-server`
2. `discovery-server`
3. `auth-server`
4. `postgres` administrado en RDS
5. `kafka` administrado en MSK
6. `batch-service`
7. `ms-account`
8. `ms-payment`
9. `ms-customer`
10. `risk-service`
11. `notification-service`
12. `bff-web`
13. `bff-mobile`
14. `bff-atm`
15. `api-gateway`

## 10. Balanceo y escalabilidad horizontal

En ECS, aumentar instancias de un servicio:

```bash
aws ecs update-service \
  --cluster banco-xyz-cluster \
  --service ms-account \
  --desired-count 2
```

Aplicar el mismo enfoque para:

- `ms-payment`
- `ms-customer`
- `bff-web`
- `bff-mobile`
- `api-gateway`

Los servicios se registran en Eureka y el Gateway enruta mediante balanceo a las instancias disponibles.

## 11. Exposicion publica

Exponer solo el Application Load Balancer asociado a `api-gateway`.

Endpoints externos esperados:

```text
https://<dominio>/gateway/web/**
https://<dominio>/gateway/mobile/**
https://<dominio>/gateway/atm/**
https://<dominio>/gateway/payments
https://<dominio>/gateway/customers
```

Configurar HTTPS con AWS Certificate Manager.

## 12. Monitoreo

Configurar:

- CloudWatch Logs por servicio.
- Alarmas por CPU y memoria.
- Alarmas por errores HTTP 5xx en ALB.
- Alarmas por conexiones RDS.
- Alarmas por lag de consumidores Kafka en MSK.

## 13. Validacion posterior al despliegue

Validar health checks:

```bash
curl https://<dominio>/actuator/health
```

Obtener token OAuth2 contra el Auth Server expuesto de forma controlada o mediante ruta interna autorizada.

Ejecutar pruebas funcionales equivalentes a la coleccion Postman:

- Token OAuth2 valido.
- Rechazo sin token.
- Rechazo por scope incorrecto.
- Consulta de cuentas.
- Deposito y transferencia.
- Gestion de cliente.
- Flujo ATM asincrono.
- Compensacion por rechazo de riesgo.

## 14. Buenas practicas de seguridad

- No guardar secretos en archivos YAML ni en Git.
- Usar Secrets Manager o SSM Parameter Store.
- Exponer publicamente solo el ALB.
- Mantener RDS y MSK en subnets privadas.
- Usar HTTPS obligatorio.
- Separar roles IAM por servicio.
- Registrar logs y auditoria de operaciones criticas.

