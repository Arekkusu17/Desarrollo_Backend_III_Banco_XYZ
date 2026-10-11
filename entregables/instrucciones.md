# Instrucciones de ejecucion y pruebas Banco XYZ

## 1. Preparar el proyecto

Clonar el repositorio y entrar al proyecto:

```bash
git clone https://github.com/Arekkusu17/Desarrollo_Backend_III_Banco_XYZ.git
cd Desarrollo_Backend_III_Banco_XYZ
git checkout develop
```

Si ya se esta trabajando desde la carpeta de entrega de Semana 9:

```bash
cd "SEMANA_9/Desarrollo_Backend_III_Banco_XYZ"
git checkout develop
git pull origin develop
```

## 2. Ejecutar pruebas automatizadas

Ejecutar todos los tests del proyecto:

```bash
./mvnw test
```

Resultado esperado:

```text
BUILD SUCCESS
```

## 3. Levantar el ambiente completo

Ejecutar:

```bash
docker compose up -d --build --remove-orphans
```

Si el puerto `9000` esta ocupado en el equipo local, usar:

```bash
AUTH_SERVER_PORT=9001 docker compose up -d --build --remove-orphans
```

En ese caso, Postman debe usar:

```text
authBaseUrl = http://localhost:9001
```

Importante: dentro de Docker los servicios siguen usando `http://auth-server:9000`; el cambio a `9001` aplica solo desde el equipo local.

## 4. Verificar contenedores

```bash
docker compose ps
```

Servicios esperados:

| Servicio | Puerto local |
| --- | --- |
| `config-server` | `8888` |
| `discovery-server` | `8761` |
| `auth-server` | `9000` o `9001` |
| `api-gateway` | `8084` |
| `ms-account` | `8091` |
| `ms-payment` | `8092` |
| `ms-customer` | `8093` |
| `batch-service` | `8080` |
| `bff-web` | `8081` |
| `bff-mobile` | `8082` |
| `bff-atm` | `8083` |
| `risk-service` | `8090` |
| `notification-service` | `8085` |
| `postgres` | `5432` |
| `kafka` | `29092` |

Si el Gateway parte antes de que Config Server este completamente disponible, reiniciar:

```bash
docker compose restart api-gateway
```

## 5. Validar health checks

```bash
curl http://localhost:8084/actuator/health
curl http://localhost:8091/actuator/health
curl http://localhost:8092/actuator/health
curl http://localhost:8093/actuator/health
```

Resultado esperado:

```json
{"status":"UP"}
```

Para Auth Server usar el puerto configurado:

```bash
curl http://localhost:9000/actuator/health
```

o:

```bash
curl http://localhost:9001/actuator/health
```

## 6. Obtener token OAuth2

Con puerto `9000`:

```bash
curl -u banco-xyz-demo:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=web.read mobile.read atm.read atm.write risk.read account.read payment.read payment.write customer.read customer.write" \
  http://localhost:9000/oauth2/token
```

Con puerto `9001`:

```bash
curl -u banco-xyz-demo:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=web.read mobile.read atm.read atm.write risk.read account.read payment.read payment.write customer.read customer.write" \
  http://localhost:9001/oauth2/token
```

Guardar el valor de `access_token` para las pruebas por curl o usar la coleccion Postman.

## 7. Probar con Postman

Importar:

```text
postman/Banco_XYZ_Semana_8.postman_collection.json
```

La coleccion aparece como:

```text
Banco XYZ - Semana 9 - Microservicios EFT
```

Orden recomendado:

1. `01 - Health Gateway`
2. `02 - Obtener token OAuth2 completo`
3. `13 - Health ms-account`
4. `14 - ms-account listar cuentas`
5. `15 - ms-account saldo cuenta`
6. `16 - Health ms-payment`
7. `17 - ms-payment crear deposito`
8. `18 - ms-payment crear transferencia`
9. `19 - Gateway ms-payment listar operaciones`
10. `20 - Health ms-customer`
11. `21 - ms-customer crear perfil`
12. `22 - ms-customer consultar perfil`
13. `23 - Gateway ms-customer listar perfiles`

Luego validar el flujo asincrono ATM:

1. `07 - Crear retiro ATM pendiente`
2. `08 - Consultar retiro hasta confirmar`
3. `09 - Activar falla de riesgo`
4. `10 - Crear retiro para rechazo por resiliencia`
5. `11 - Consultar retiro hasta rechazo compensado`
6. `12 - Desactivar falla de riesgo`

## 8. Probar endpoints principales por curl

Reemplazar `$TOKEN` por el token obtenido.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8091/api/cuentas
```

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8091/api/cuentas/101/saldo
```

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8084/gateway/payments
```

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8084/gateway/customers
```

Crear deposito:

```bash
curl -X POST http://localhost:8092/api/payments/deposits \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"targetAccountId":101,"amount":5000,"description":"Deposito de prueba"}'
```

Crear cliente:

```bash
curl -X POST http://localhost:8093/api/customers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":101,"fullName":"Alex Fernandez","email":"alex.fernandez@example.com","phone":"+56912345678","segment":"premium","status":"active"}'
```

## 9. Revisar logs

```bash
docker compose logs -f api-gateway
docker compose logs -f ms-account
docker compose logs -f ms-payment
docker compose logs -f ms-customer
docker compose logs -f batch-service
docker compose logs -f risk-service
docker compose logs -f notification-service
```

## 10. Detener el ambiente

Detener contenedores sin borrar datos:

```bash
docker compose down --remove-orphans
```

Detener y borrar volumen de PostgreSQL:

```bash
docker compose down -v --remove-orphans
```

