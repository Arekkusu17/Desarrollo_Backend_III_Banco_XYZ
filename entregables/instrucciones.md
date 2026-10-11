# Instrucciones de ejecucion y pruebas - Banco XYZ EFT

## 1. Version y requisitos

Trabajar con `feature/eft-compliance` mientras el PR no este aprobado. Requisitos: Java 21, Docker, Compose 2.24.4 o posterior, curl, jq y Postman. No usar un volumen con datos importantes para pruebas destructivas.

```bash
git clone --branch feature/eft-compliance https://github.com/Arekkusu17/Desarrollo_Backend_III_Banco_XYZ.git
cd Desarrollo_Backend_III_Banco_XYZ
./mvnw verify
```

Si ya existe el checkout, no volver a clonarlo. Revisar primero los cambios locales antes de cambiar de branch. La guia [S3 y EC2](despliegue.md) permite probar la misma version desde un ZIP, sin instalar Git en la instancia.

## 2. Arranque local y HTTPS

Para desarrollo local HTTP:

```bash
export AUTH_SERVER_PORT=9001
docker compose up -d --build --remove-orphans
docker compose ps
```

9001 es opcional y solo cambia el puerto publicado del Auth Server.

Para HTTPS de laboratorio, con una instancia por microservicio:

```bash
export TLS_STORE_PASSWORD='reemplazar-por-clave-de-laboratorio'
TLS_COPY_BFF=false bash scripts/generar-certificados.sh
export COMPOSE_FILE=docker-compose.yml:docker-compose.secure.yml
docker compose up -d --build --remove-orphans
curl --cacert output/tls/bank.crt https://localhost:8084/actuator/health
```

Los certificados son autofirmados y sirven para la demostracion, no para produccion. Se conservan si ya existen; no cambiar la clave o los SAN sin regenerarlos en una carpeta nueva. Nunca empaquetar ni publicar la clave privada.

El Gateway, Auth Server, BFFs y los tres microservicios usan HTTPS con el override seguro. Risk, Batch y Notification siguen con HTTP en sus puertos; Eureka y Config Server tambien. JWT se valida contra el emisor interno `https://auth-server:9000`, aunque el cliente obtenga el token mediante localhost o la IP EC2.

## 3. Salud y diagnostico

```bash
docker compose ps
docker compose logs --tail=100 config-server discovery-server auth-server api-gateway
curl --cacert output/tls/bank.crt https://localhost:8091/actuator/health
curl --cacert output/tls/bank.crt https://localhost:8092/actuator/health
curl --cacert output/tls/bank.crt https://localhost:8093/actuator/health
```

Esperar salud UP y registro en Eureka; estar Running no implica estar listo. El tiempo varia segun recursos, descargas y propagacion. Un 404 inicial amerita revisar rutas, logs y registro, no asumir inmediatamente un error de endpoint. Si el Gateway arranco con configuracion incompleta, reiniciarlo despues de que Config Server este disponible.

## 4. Token y seguridad

Ejemplo HTTPS con jq:

```bash
TOKEN=$(curl --fail --silent --show-error --cacert output/tls/bank.crt \
  -u banco-xyz-demo:demo-secret \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'scope=web.read mobile.read atm.read atm.write risk.read account.read account.write payment.read payment.write customer.read customer.write' \
  https://localhost:9001/oauth2/token | jq -er '.access_token')
curl --fail --cacert output/tls/bank.crt \
  -H "Authorization: Bearer $TOKEN" \
  https://localhost:8084/gateway/cuentas/101/saldo
```

Si no se configuro 9001, usar 9000. Para HTTP local quitar `--cacert` y cambiar el esquema.

Validar 401 sin token, 403 con un token valido sin el scope de la ruta y acceso permitido con el scope correspondiente. `invalid_scope` implica que se solicito un scope no autorizado para ese cliente. La coleccion solicita solo scopes registrados. `account.execute` es exclusivo del cliente interno `ms-payment`, no del cliente demo.

Los secretos incluidos son de laboratorio. Este procedimiento no convierte la configuracion en una solucion productiva de gestion de secretos.

## 5. Postman y regresion funcional

Importar `postman/Banco_XYZ_Semana_8.postman_collection.json`. Se conserva el nombre del archivo; el titulo de la coleccion es Banco XYZ - Semana 9 - Microservicios EFT.

En HTTPS ajustar `authBaseUrl`, `gatewayBaseUrl`, `accountServiceBaseUrl`, `paymentServiceBaseUrl`, `customerServiceBaseUrl` a https y los puertos de la instancia. `riskBaseUrl` sigue en http:8090. Importar `bank.crt` como CA en Postman; deshabilitar validacion SSL solo como alternativa temporal y declarada de laboratorio.

Ejecutar la coleccion con un intervalo de 1000 ms y repetir las consultas asincronas hasta el estado terminal:

1. Health, token, 401, 403 y dashboard Web.
2. ATM pendiente y confirmado; falla de riesgo y rechazo compensado.
3. Desactivar siempre la falla de riesgo (request 12), incluso si alguna prueba falla.
4. Cuentas: consulta, saldo y movimientos.
5. Deposito, transferencia y listado por Gateway.
6. Crear/consultar cliente y listado por Gateway.

La coleccion no cubre por si sola todo el CRUD de cuentas, idempotencia, BFF Mobile ni recuperacion batch. Completar manualmente:

| Caso | Verificacion |
| --- | --- |
| Cuentas | POST /gateway/cuentas, PUT /gateway/cuentas/{id}, GET /gateway/cuentas/{id}/gestion y POST /gateway/cuentas/{id}/cierre; usar IDs nuevos, cierre con saldo cero y account.write |
| Mobile | GET /gateway/mobile/cuentas/101/inicio; respuesta adaptada al canal |
| Pagos | El deposito aumenta saldo, la transferencia debita origen y acredita destino |
| Idempotencia | Repetir un POST con el mismo Idempotency-Key y el mismo cuerpo: misma operacion y saldo modificado una sola vez |
| Fondos insuficientes | Rechazo sin saldo negativo ni abono parcial en destino |
| ATM | Confirmacion debita una vez; rechazo termina compensado y libera la reserva |
| Persistencia | Reiniciar el servicio despues de completar una operacion y comprobar que su estado permanece |
| Seguridad | Lecturas y escrituras requieren sus scopes; el cliente demo no ejecuta operaciones internas |

Para nuevas cuentas el cuerpo contiene `accountId`, `customerName`, `accountType`, `age`, `initialBalance`. Para transferencias: `sourceAccountId`, `targetAccountId`, `amount`, `description`. La repeticion sin Idempotency-Key puede crear una operacion distinta. Repetir Collection Runner modifica saldos; registrar los saldos iniciales antes de comparar.

## 6. Batch y resultados

Los tres jobs se lanzan al iniciar batch-service. La clave predeterminada es la fecha del arranque; una instancia ya COMPLETED no se reprocesa con los mismos parametros.

```bash
docker compose logs --tail=150 batch-service
docker compose exec -T postgres psql -U banco -d banco_xyz -c \
  "SELECT i.job_name,e.job_execution_id,e.status,e.exit_code FROM batch_job_execution e JOIN batch_job_instance i USING(job_instance_id) ORDER BY e.job_execution_id;"
docker compose exec -T postgres psql -U banco -d banco_xyz -c \
  "SELECT 'daily' AS proceso,COUNT(*) FROM daily_transaction_summary UNION ALL SELECT 'interest',COUNT(*) FROM monthly_interest_results UNION ALL SELECT 'annual',COUNT(*) FROM annual_statement_entries;"
docker compose exec -T postgres psql -U banco -d banco_xyz -c \
  "SELECT process_name,reason,COUNT(*) FROM rejected_records GROUP BY process_name,reason ORDER BY process_name,reason;"
docker compose cp batch-service:/app/output/annual_statement_report.csv ./annual_statement_report.csv
```

Comprobar tres jobs COMPLETED, workers por particion, registros aceptados/rechazados y reporte anual generado. Los 1000 registros por CSV no implican 1000 resultados validos: aplicar reglas de rechazo y duplicados.

Comparar con valores esperados calculados independientemente a partir del CSV y las reglas del codigo: tipos y limite de anomalia para transacciones, tasas/edad para intereses, formato/tipos/duplicados para estados anuales. Comparar importes con precision decimal, totales y rechazos. No existe un COBOL ejecutable para comparar; una corrida Java sin fallos solo es referencia para verificar recuperacion, no prueba de correccion frente al legacy.

## 7. Pruebas automatizadas de fallos

La ultima validacion local paso 56 tests unitarios y 4 de integracion. Para repetir las cuatro pruebas de integracion, usar exclusivamente este PostgreSQL temporal, sin volumen del stack:

```bash
docker run -d --name banco-eft-recovery-postgres \
  -p 127.0.0.1:25432:5432 \
  -e POSTGRES_DB=banco_recovery_tests -e POSTGRES_USER=banco -e POSTGRES_PASSWORD=banco \
  postgres:16-alpine
docker exec banco-eft-recovery-postgres pg_isready -U banco -d banco_recovery_tests
BATCH_RECOVERY_PG_URL=jdbc:postgresql://localhost:25432/banco_recovery_tests \
BATCH_RECOVERY_PG_CONTAINER=banco-eft-recovery-postgres \
  ./mvnw -pl batch-service -am verify
docker rm -f banco-eft-recovery-postgres
```

Esperar que pg_isready confirme disponibilidad antes de ejecutar Maven. Los tests matan el proceso Java de cada job despues de un checkpoint y detienen/arrancan solo ese PostgreSQL temporal. Verifican reanudacion, ausencia de reproceso de chunks confirmados y equivalencia de tablas, rechazos y CSV con la corrida sin fallos. Otro test impide apropiarse de un job con propietario activo.

Los reportes quedan en `batch-service/target/failsafe-reports/` y los logs en `batch-service/target/recovery-validation/`. Sin las variables explicitas estos tests se omiten; BUILD SUCCESS por si solo no demuestra que se ejecutaron. No detener el PostgreSQL del stack para repetir estas pruebas.

## 8. Alcance de rendimiento y evidencias

No se haran pruebas de carga o gran volumen. Las estimaciones deben declarar cantidad de registros, hardware supuesto, paralelismo y limites de CPU, memoria, base de datos e I/O. No afirmar tiempos medidos en EC2 ni aceleracion frente a COBOL sin evidencia.

Conservar evidencia local y, por separado, evidencia AWS: version/commit, Docker Compose ps, salud, resultados Postman, saldo antes/despues, jobs/particiones/rechazos, CSV anual, dos IDs de instancia por microservicio durante el escalado temporal y consumo observado. No incluir JWT, claves privadas ni secretos en capturas.

## 9. Detener sin perder datos

```bash
docker compose down --remove-orphans
```

No agregar `-v`: elimina el volumen PostgreSQL. La persistencia de Kafka no esta configurada con un volumen explicito; no prometer conservar su historial despues de eliminar contenedores.
