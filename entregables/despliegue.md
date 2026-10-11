# Despliegue en AWS con S3 y EC2 Banco XYZ

## Objetivo

Este documento describe un despliegue simple del proyecto Banco XYZ en AWS. El enfoque consiste en comprimir el codigo fuente, subir el archivo ZIP a Amazon S3, copiarlo desde una instancia EC2 y ejecutar la solucion con Docker Compose.

Este procedimiento permite validar la aplicacion en un entorno cloud basico, manteniendo la misma arquitectura usada localmente:

- Microservicios Spring Boot.
- Docker Compose.
- PostgreSQL en contenedor.
- Kafka en contenedor.
- Config Server.
- Eureka Discovery Server.
- Auth Server OAuth2.
- API Gateway.
- BFF Web, Mobile y ATM.
- `ms-account`, `ms-payment`, `ms-customer`.
- `batch-service`, `risk-service` y `notification-service`.

## 1. Requisitos

En el equipo local:

- Git.
- AWS CLI configurado.
- Acceso a una cuenta AWS.
- Llave SSH para conectarse a EC2.

En AWS:

- Bucket S3 para almacenar el ZIP.
- Instancia EC2 con Amazon Linux 2023 o Ubuntu.
- Security Group con puertos necesarios abiertos.

## 2. Preparar el proyecto local

Desde la raiz del repositorio:

```bash
git checkout develop
git pull origin develop
```

Ejecutar pruebas antes de empaquetar:

```bash
./mvnw test
```

Resultado esperado:

```text
BUILD SUCCESS
```

## 3. Crear ZIP del proyecto

Crear el archivo ZIP sin incluir artefactos pesados:

```bash
zip -r banco-xyz-semana-9.zip . \
  -x "*.git*" \
  -x "*/target/*" \
  -x "*/.DS_Store" \
  -x "banco-xyz-semana-9.zip"
```

Validar que el archivo existe:

```bash
ls -lh banco-xyz-semana-9.zip
```

## 4. Subir ZIP a S3

Crear un bucket si aun no existe:

```bash
aws s3 mb s3://banco-xyz-semana-9-<identificador>
```

Subir el ZIP:

```bash
aws s3 cp banco-xyz-semana-9.zip s3://banco-xyz-semana-9-<identificador>/banco-xyz-semana-9.zip
```

Verificar:

```bash
aws s3 ls s3://banco-xyz-semana-9-<identificador>/
```

## 5. Crear instancia EC2

Requisitos recomendados para ejecutar la solucion completa:

| Item | Valor recomendado |
| --- | --- |
| AMI | Amazon Linux 2023 o Ubuntu 22.04 LTS |
| Arquitectura | `x86_64`, compatible con el instalador de Compose de esta guia |
| Tipo | `t3.large`: 2 vCPU y 8 GiB de RAM; aumentar capacidad si falta memoria |
| Disco | EBS de 30 GiB gp3 como punto de partida para codigo, imagenes y datos |
| Red | Subred publica con ruta a Internet Gateway, IPv4 publica y salida a Internet para descargar paquetes e imagenes |
| Acceso SSH | Key pair cuya llave privada este disponible en el equipo local |
| IAM Role | Rol asociado a EC2 con `s3:GetObject` sobre el ZIP del bucket |
| Software | Docker Engine, Docker Compose v2, AWS CLI, unzip, git y curl |

El dimensionamiento es una recomendacion inicial para la demostracion, no un minimo validado mediante pruebas de carga. Compose ejecuta varios procesos Spring Boot junto con Kafka y PostgreSQL; comprobar el consumo con `docker stats` y el espacio disponible con `df -h` durante la compilacion y el arranque.

Las especificaciones de `t3.large` se pueden consultar en [Amazon EC2 T3](https://aws.amazon.com/ec2/instance-types/t3/). El volumen gp3 incluye una base de 3000 IOPS y 125 MiB/s, descrita en [Amazon EBS gp3](https://docs.aws.amazon.com/ebs/latest/userguide/general-purpose.html).

El rol permite descargar el archivo desde EC2 sin configurar claves AWS personales en la instancia. Si se desea listar el contenido del bucket con `aws s3 ls`, agregar tambien `s3:ListBucket` sobre el bucket.

Configurar las siguientes reglas de entrada TCP en el Security Group:

| Puerto | Uso | Origen |
| --- | --- | --- |
| `22` | Conexion SSH | IP publica del equipo local, con mascara `/32` |
| `8084` | API Gateway para pruebas desde Postman | IP publica del equipo que realiza las pruebas, con mascara `/32` |
| `9000` | Obtener token OAuth2 | Misma IP de pruebas; usar `9001` si se define `AUTH_SERVER_PORT=9001` |
| `8761`, `8888` | Inspeccion opcional de Eureka y Config Server | IP de pruebas, solo cuando se necesite |

Para pruebas academicas, abrir tambien los puertos de microservicios si se desea validar directamente:

```text
8080, 8081, 8082, 8083, 8085, 8090, 8091, 8092, 8093
```

Restringir tambien estos puertos opcionales a la IP de pruebas. PostgreSQL (`5432`) y Kafka (`9092`, `29092`) se comunican con los servicios mediante la red interna de Docker y no necesitan reglas de entrada desde Internet para este procedimiento.

## 6. Conectarse a EC2

Ejemplo para Amazon Linux:

```bash
ssh -i llave.pem ec2-user@<EC2_PUBLIC_IP>
```

Ejemplo para Ubuntu:

```bash
ssh -i llave.pem ubuntu@<EC2_PUBLIC_IP>
```

## 7. Instalar dependencias en EC2

### Amazon Linux 2023

```bash
sudo dnf update -y
sudo dnf install -y docker unzip git awscli
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker $USER
```

Cerrar y volver a abrir la sesion SSH para aplicar el grupo `docker`.

Validar Docker Compose:

```bash
docker compose version
```

Si Docker Compose no esta disponible:

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

### Ubuntu

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin unzip git awscli
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker $USER
```

Cerrar y volver a abrir la sesion SSH para aplicar el grupo `docker`.

## 8. Copiar ZIP desde S3 a EC2

En la instancia EC2:

```bash
aws s3 cp s3://banco-xyz-semana-9-<identificador>/banco-xyz-semana-9.zip .
```

Descomprimir:

```bash
mkdir banco-xyz-semana-9
unzip banco-xyz-semana-9.zip -d banco-xyz-semana-9
cd banco-xyz-semana-9
```

## 9. Levantar la solucion

Ejecutar:

```bash
docker compose up -d --build --remove-orphans
```

Verificar contenedores:

```bash
docker compose ps
```

Esperar 60 a 90 segundos para que Spring Boot, Config Server y Eureka completen el arranque.

Si el Gateway no carga rutas en el primer intento:

```bash
docker compose restart api-gateway
```

## 10. Validar servicios desde EC2

Health Gateway:

```bash
curl http://localhost:8084/actuator/health
```

Health microservicios:

```bash
curl http://localhost:8091/actuator/health
curl http://localhost:8092/actuator/health
curl http://localhost:8093/actuator/health
```

Obtener token:

```bash
curl -u banco-xyz-demo:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=web.read mobile.read atm.read atm.write risk.read account.read payment.read payment.write customer.read customer.write" \
  http://localhost:9000/oauth2/token
```

Probar Gateway con token:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8084/gateway/payments
```

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8084/gateway/customers
```

## 11. Validar desde Postman

Usar la coleccion:

```text
postman/Banco_XYZ_Semana_8.postman_collection.json
```

Configurar variables:

| Variable | Valor |
| --- | --- |
| `authBaseUrl` | `http://<EC2_PUBLIC_IP>:9000` |
| `gatewayBaseUrl` | `http://<EC2_PUBLIC_IP>:8084` |
| `accountServiceBaseUrl` | `http://<EC2_PUBLIC_IP>:8091` |
| `paymentServiceBaseUrl` | `http://<EC2_PUBLIC_IP>:8092` |
| `customerServiceBaseUrl` | `http://<EC2_PUBLIC_IP>:8093` |

Orden recomendado:

1. Obtener token OAuth2 completo.
2. Probar health de Gateway y microservicios.
3. Probar cuentas.
4. Probar pagos.
5. Probar clientes.
6. Probar flujo ATM asincrono.
7. Probar resiliencia de riesgo y compensacion.

## 12. Escalabilidad basica en EC2

Para demostrar escalabilidad horizontal en Docker Compose se puede levantar mas de una instancia de un servicio que no publique puerto fijo directo al host.

Ejemplo conceptual:

```bash
docker compose up -d --scale ms-account=2 --scale ms-payment=2 --scale ms-customer=2
```

Nota: si un servicio expone un puerto fijo al host, Docker Compose puede impedir escalarlo directamente. Para una demostracion simple, se recomienda acceder por `api-gateway`, Eureka y nombres internos de servicio.

## 13. Detener ambiente

Detener contenedores:

```bash
docker compose down --remove-orphans
```

Detener y eliminar volumenes locales:

```bash
docker compose down -v --remove-orphans
```

## 14. Consideraciones de seguridad

- No subir llaves SSH ni secretos al ZIP.
- Usar IAM Role en EC2 para leer desde S3.
- Abrir solo los puertos necesarios en el Security Group.
- Para un ambiente real, exponer solo Gateway y protegerlo con HTTPS.
- Mantener PostgreSQL y Kafka sin exposicion publica si se separan del host.
- Eliminar el ZIP del bucket cuando ya no sea necesario.
