# Despliegue de Banco XYZ EFT: ZIP, S3 y EC2

## Alcance y estado

Procedimiento academico: subir codigo como ZIP a S3, descargarlo en una EC2 y ejecutar Docker Compose. No se requiere ECR, ECS, Kubernetes, balanceador AWS ni un pipeline de despliegue.

La version candidata es feature/eft-compliance hasta aprobar el PR a develop. La validacion local esta completada; las verificaciones AWS de esta guia siguen pendientes. El informe tecnico y el video no se generan en esta etapa.

## 1. Requisitos EC2

| Item | Recomendacion inicial |
| --- | --- |
| AMI | Amazon Linux 2023, arquitectura x86_64 |
| Instancia | t3.large: 2 vCPU y 8 GiB RAM; ampliar si hay falta de memoria |
| Disco | EBS gp3 de 30 GiB o mas |
| Red | Subred publica, IPv4 publica, Internet Gateway y salida a Internet |
| Acceso | Key pair SSH y Security Group restringido a la IP de pruebas |
| IAM | Rol de instancia con s3:GetObject para el ZIP; s3:ListBucket solo si se necesita listar |
| Software | Docker Engine, Docker Compose 2.24.4 o posterior, AWS CLI, unzip, curl, jq y Java 21/keytool |

El dimensionamiento es una estimacion para la demostracion, no una capacidad garantizada ni un resultado de carga. No usar una instancia micro para todo el stack. Durante el build, limitar concurrencia; durante el escalado observar memoria y OOM. Si es insuficiente, aumentar capacidad antes de repetir.

t3.large tiene 2 vCPU y 8 GiB segun [AWS T3](https://aws.amazon.com/ec2/instance-types/t3/). La recomendacion de usarla es propia del proyecto. Docker Compose debe soportar los tags de override usados por el repo; ver [merge de Compose](https://docs.docker.com/reference/compose-file/merge/).

Reglas de entrada TCP, siempre desde la IP publica de pruebas con mascara /32:

| Puerto | Uso |
| --- | --- |
| 22 | SSH |
| 8084 | Gateway HTTPS |
| 9001 | Auth HTTPS; 9000 si se elige el puerto predeterminado |
| 8091, 8092, 8093 | Opcional y temporal: coleccion Postman con acceso directo |
| 8090 | Opcional y temporal: simulacion de riesgo por Postman (HTTP) |

No abrir 5432, 9092, 29092, 8888, 8761, 8080 ni 8085 a Internet. BFFs se prueban por Gateway. Inspeccionar Eureka mediante SSH o un tunel, no mediante acceso publico. No usar 0.0.0.0/0 para las reglas anteriores.

El Compose base publica puertos en el host; el Security Group es la restriccion de acceso externa en esta demostracion. No equivale a una configuracion de produccion. Kafka no tiene volumen persistente explicito; PostgreSQL si. No prometer alta disponibilidad del host, de la base ni del broker.

## 2. Preparar ZIP local

Requisitos locales: Git, zip/unzip y AWS CLI configurado. Despues de guardar/commitear la version candidata y ejecutar ./mvnw verify, desde la raiz del repositorio:

```bash
git status --short
git switch feature/eft-compliance
git rev-parse HEAD
git archive --format=zip --output=../banco-xyz-semana-9.zip HEAD
zip -d ../banco-xyz-semana-9.zip '*.p12' '*.jks' '*.pem' '*.key'
unzip -l ../banco-xyz-semana-9.zip
```

git archive incluye solo archivos versionados del commit: no agrega target, output, .git, el verificador local ignorado ni archivos no commiteados. El paso zip -d retira los tres keystores de laboratorio ya versionados en los BFF y otros archivos de claves con esas extensiones. Revisar igualmente el contenido antes de subirlo. Registrar el hash mostrado para identificar la evidencia AWS. El ZIP no incluye historial Git; el override HTTPS usara certificados nuevos externos al classpath.

Subir a un bucket privado existente, reemplazando el nombre:

```bash
export BUCKET='reemplazar-por-bucket-propio'
aws s3 cp ../banco-xyz-semana-9.zip "s3://$BUCKET/banco-xyz-semana-9.zip"
```

Usar IAM Role en EC2; no copiar credenciales AWS personales ni la llave SSH al ZIP.

## 3. SSH e instalacion (Amazon Linux 2023)

Desde el equipo local:

```bash
chmod 400 llave.pem
ssh -i llave.pem ec2-user@IP_PUBLICA_EC2
```

Dentro de EC2:

```bash
sudo dnf install -y docker unzip jq awscli-2 java-21-amazon-corretto-devel
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Cerrar y volver a abrir SSH para aplicar el grupo docker. Amazon Linux suele incluir curl; si falta, instalar el paquete curl disponible en la AMI. Para Java/keytool ver [Corretto 21](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/amazon-linux-install.html); AWS documenta [AWS CLI en AL2023](https://docs.aws.amazon.com/linux/al2023/ug/awscli2.html).

Si Compose no esta instalado, instalar una version fijada compatible:

```bash
mkdir -p "$HOME/.docker/cli-plugins"
curl -fSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o "$HOME/.docker/cli-plugins/docker-compose"
chmod +x "$HOME/.docker/cli-plugins/docker-compose"
docker compose version
docker version
java -version
aws --version
```

2.29.7 es una version fijada para esta guia, no se afirma que sea la mas reciente. La instalacion manual no se actualiza automaticamente; procedimiento basado en [Docker Compose para Linux](https://docs.docker.com/compose/install/linux/). Para Ubuntu, usar el [repositorio oficial de Docker](https://docs.docker.com/engine/install/ubuntu/) e instalar Java 21 y AWS CLI segun la distribucion; no ejecutar comandos dnf en Ubuntu.

## 4. Descargar y descomprimir

Dentro de EC2:

```bash
export BUCKET='reemplazar-por-bucket-propio'
aws s3 cp "s3://$BUCKET/banco-xyz-semana-9.zip" "$HOME/banco-xyz-semana-9.zip"
mkdir -p "$HOME/banco-xyz-semana-9"
unzip "$HOME/banco-xyz-semana-9.zip" -d "$HOME/banco-xyz-semana-9"
cd "$HOME/banco-xyz-semana-9"
chmod +x mvnw
```

Usar una carpeta nueva por version para evitar mezclar archivos antiguos. AccessDenied al descargar indica revisar rol/permisos del objeto, no publicar el bucket.

## 5. Certificados y arranque HTTPS

Antes del primer arranque, reemplazar la IP y elegir una clave de laboratorio sin compartirla:

```bash
export TLS_PUBLIC_IP='IP_PUBLICA_EC2'
read -rsp 'Clave para el almacen TLS: ' TLS_STORE_PASSWORD
export TLS_STORE_PASSWORD
export AUTH_SERVER_PORT=9001
TLS_COPY_BFF=false bash scripts/generar-certificados.sh
export COMPOSE_FILE=docker-compose.yml:docker-compose.secure.yml
docker compose config --quiet
COMPOSE_PARALLEL_LIMIT=1 docker compose build
docker compose up -d --remove-orphans
docker compose ps
```

El unico script de certificados es scripts/generar-certificados.sh. Genera bank.p12, truststore.p12 y bank.crt bajo output/tls. El override los monta de solo lectura; TLS_COPY_BFF=false evita copiar claves al codigo fuente.

Repetir los exports COMPOSE_FILE, AUTH_SERVER_PORT y la misma clave al reconectar SSH antes de operar el stack. No guardar secretos en el repo. Si la IP publica cambia, generar certificados en una carpeta nueva y reemplazar el juego completo con el stack detenido; el script conserva certificados existentes y no modifica sus SAN.

Los servicios arrancan asincronamente; verificar salud y logs, no depender de una espera fija. Si falta memoria o aparecen contenedores Exited, diagnosticar antes de continuar:

```bash
docker compose logs --tail=100 config-server discovery-server auth-server api-gateway batch-service
docker stats --no-stream
free -h
df -h
curl --fail --cacert output/tls/bank.crt https://localhost:8084/actuator/health
curl --fail --cacert output/tls/bank.crt https://localhost:8091/actuator/health
curl --fail --cacert output/tls/bank.crt https://localhost:8092/actuator/health
curl --fail --cacert output/tls/bank.crt https://localhost:8093/actuator/health
```

Consultar Eureka desde EC2 con curl http://localhost:8761/eureka/apps -H 'Accept: application/json'. Esperar registros UP. Revisar rutas/configuracion ante 404; si el Gateway arranco sin configuracion, reiniciarlo una vez que Config Server este listo.

## 6. Pruebas desde Postman y EC2

Descargar solo el certificado publico al equipo local:

```bash
scp -i llave.pem ec2-user@IP_PUBLICA_EC2:~/banco-xyz-semana-9/output/tls/bank.crt ./bank-ec2.crt
```

Importarlo como CA en Postman. No descargar ni compartir bank.p12 o truststore.p12. Certificado autofirmado: confianza manual de laboratorio, no certificado de una CA publica.

Configurar la coleccion existente:

Importar tambien `postman/Banco_XYZ_EC2_Semana_9.postman_environment.json` y seleccionar el entorno EC2. Incluye la IP actual 34.229.100.153; la tabla siguiente permite ajustarla si cambia.

| Variable | Valor EC2 |
| --- | --- |
| authBaseUrl | https://IP_PUBLICA_EC2:9001 |
| gatewayBaseUrl | https://IP_PUBLICA_EC2:8084 |
| accountServiceBaseUrl | https://IP_PUBLICA_EC2:8091 |
| paymentServiceBaseUrl | https://IP_PUBLICA_EC2:8092 |
| customerServiceBaseUrl | https://IP_PUBLICA_EC2:8093 |
| riskBaseUrl | http://IP_PUBLICA_EC2:8090 |

Seguir [instrucciones de pruebas](instrucciones.md), incluyendo las verificaciones manuales no cubiertas por la coleccion. Completar regresion con una instancia por servicio antes de escalar. Los requests de riesgo son solo para la demostracion y deben desactivarse al terminar.

Dentro de EC2, obtener token para las siguientes lecturas:

```bash
TOKEN=$(curl --fail --silent --show-error --cacert output/tls/bank.crt \
  -u banco-xyz-demo:demo-secret \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'scope=web.read mobile.read atm.read atm.write risk.read account.read account.write payment.read payment.write customer.read customer.write' \
  https://localhost:9001/oauth2/token | jq -er '.access_token')
curl --fail --cacert output/tls/bank.crt \
  -H "Authorization: Bearer $TOKEN" https://localhost:8084/gateway/cuentas/119/saldo
```

Confirmar los tres jobs COMPLETED, sus particiones, rechazos y reporte anual con los comandos SQL/docker cp de las instrucciones. No ejecutar pruebas de carga ni atribuir estos resultados a COBOL.

## 7. Escalabilidad temporal de los tres microservicios

El criterio 6 de la pauta solicita Docker y escalabilidad horizontal de tres microservicios en la nube. No fija dos replicas permanentes. Esta demostracion usa dos temporalmente en una EC2; no demuestra alta disponibilidad entre hosts.

Aplicar el override, que elimina container_name y los puertos fijos de ms-account, ms-payment y ms-customer. Tambien anuncia la IP individual de cada replica en Eureka: un hostname compartido puede dirigir las conexiones HTTPS al mismo contenedor aunque Eureka tenga dos registros.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.secure.yml:docker-compose.scale.yml
docker compose config --quiet
docker compose up -d --remove-orphans \
  --scale ms-account=2 --scale ms-payment=2 --scale ms-customer=2
docker compose ps
docker stats --no-stream
```

Antes de probar el balanceo, incluir las IP actuales en el certificado. Conservar TLS_PUBLIC_IP (la IP publica de EC2), TLS_PUBLIC_DNS si se utilizo y TLS_STORE_PASSWORD con los mismos valores del despliegue. No publicar la clave ni los almacenes de certificados.

```bash
export TLS_EXTRA_IPS="$(docker inspect \
  $(docker compose ps -q ms-account ms-payment ms-customer) \
  --format '{{range .NetworkSettings.Networks}}{{println .IPAddress}}{{end}}' \
  | sort -u | sed '/^$/d' | paste -sd, -)"
test -n "$TLS_EXTRA_IPS"
printf 'IP de las replicas: %s\n' "$TLS_EXTRA_IPS"
```

Si la inspeccion falla o no devuelve las seis IP, detener el procedimiento. Revisar que existan dos replicas de cada servicio. No continuar con una lista vacia ni usar las IP de un despliegue anterior.

Durante esta actualizacion hay una interrupcion breve. Detener los servicios TLS sin eliminar sus contenedores conserva las IP inspeccionadas. El script existente genera los nuevos archivos antes de reemplazar los anteriores y conserva un respaldo en output/tls/backup.*. No renombrar el directorio output/tls porque esta montado en los contenedores.

```bash
docker compose stop api-gateway bff-web bff-mobile bff-atm \
  ms-payment ms-account ms-customer risk-service batch-service auth-server
TLS_REGENERATE=true TLS_COPY_BFF=false bash scripts/generar-certificados.sh
```

Solo si el script termina correctamente, arrancar los mismos contenedores para recargar identidad y confianza TLS:

```bash
docker compose start auth-server ms-account ms-payment ms-customer \
  risk-service batch-service bff-web bff-mobile bff-atm api-gateway
```

Reimportar output/tls/bank.crt como certificado de confianza en Postman y reemplazar el certificado anterior. Mantener SSL certificate verification activado. No es necesario reconstruir las imagenes Java para este cambio.

Las IP internas pueden cambiar al recrear contenedores o cambiar la cantidad de replicas. En ese caso repetir inspeccion, regeneracion y recarga antes de verificar el balanceo. Esta estrategia corresponde a la demostracion temporal; para operacion continua se requieren identidades y emision de certificados automatizadas. TLS_EXTRA_IPS acepta IPv4 separadas por comas; sin TLS_REGENERATE=true el script conserva los archivos existentes y rechaza IP adicionales no cubiertas por su certificado.

Realizar la regresion breve de Postman: 01 UP; 02 y 04 tokens nuevos; 03 y 05 rechazos 401/403; 06 y 24 datos reales sin fallback; 19 y 23 listados por Gateway. Luego ejecutar la carpeta 28 completa. No repetir operaciones de escritura ni todos los bloques funcionales por este cambio de infraestructura.

Esperar dos instancias UP de cada servicio en Eureka y propagacion al Gateway. Si el token expiro, obtener uno nuevo. Realizar lecturas de baja frecuencia para comprobar distribucion, no una prueba de carga:

```bash
for ruta in cuentas payments customers; do
  for intento in 1 2 3 4 5 6; do
    curl --fail --silent --show-error --cacert output/tls/bank.crt \
      -D - -o /dev/null -H "Authorization: Bearer $TOKEN" \
      "https://localhost:8084/gateway/$ruta" | grep -i '^X-Service-Instance:'
    sleep 1
  done
done
```

Resultado esperado: al menos dos valores distintos de X-Service-Instance para cada microservicio. Conservar tambien el listado Eureka y Compose. Mientras esta activo el override, 8091/8092/8093 no estan publicados: usar Gateway; no correr las requests directas de la coleccion en ese modo.

La carpeta Postman `28 - Escalabilidad por Gateway (opcional)` realiza esta comprobacion con seis muestras por servicio. Obtener primero el token 02, activar `enableScalingChecks=true`, dejar `expectedInstances=2` y ejecutar solo la carpeta 28 en Runner. Al volver a una replica, desactivar el flag o ajustar expectedInstances a 1. La carpeta no crea ni elimina replicas.

Volver a una replica manteniendo el override (si cambia alguna IP, renovar nuevamente su cobertura TLS):

```bash
docker compose up -d --remove-orphans \
  --scale ms-account=1 --scale ms-payment=1 --scale ms-customer=1
```

En ese modo siguen sin puertos directos. Para volver a la configuracion inicial con puertos directos:

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.secure.yml
docker compose up -d --remove-orphans
```

Esperar convergencia de Eureka. No ejecutar docker compose down -v durante las transiciones. Comprobar nuevamente saldo y listado de pagos/clientes.

## 8. Evidencia, limites y cierre

Registrar commit del ZIP, recursos EC2, fecha, servicios UP, resultados Postman, saldos/operaciones, tres jobs y sus particiones, rechazos/CSV y dos IDs de instancia por cada microservicio. No incluir secretos o tokens en evidencia.

Se estimara rendimiento con supuestos explicitos, sin pruebas de gran volumen ni una comparacion medida contra COBOL. La recuperacion abrupta y de PostgreSQL ya tiene pruebas locales aisladas; si se repiten en EC2 usar la base temporal de las instrucciones, nunca detener la base principal por accidente.

El despliegue quedara validado solo despues de ejecutar este procedimiento. No aprobar automaticamente el PR ni hacer merge a develop antes de revisar los resultados.

Para detener los contenedores conservando PostgreSQL:

```bash
docker compose down --remove-orphans
```

Detener EC2 cuando no se use y revisar recursos/costos restantes (EBS, IPv4, snapshots y S3). No eliminar volumenes o evidencia necesaria. La persistencia de Kafka al eliminar contenedores no esta garantizada.
