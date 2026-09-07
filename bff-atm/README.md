# BFF ATM

Servicio para cajeros automaticos.

Consume `core-banking` y expone operaciones acotadas como consulta de saldo y retiros.

## Endpoints

```bash
curl http://localhost:8083/atm/cuentas/101/saldo \
  -H "X-Channel-Token: ATM-SECRET"

curl -X POST http://localhost:8083/atm/cuentas/101/retiros \
  -H "Content-Type: application/json" \
  -H "X-Channel-Token: ATM-SECRET" \
  -d '{"amount":1000,"pin":"1234"}'
```

## Seguridad del canal

Este BFF exige el header `X-Channel-Token` con el token configurado en `channel.auth-token`. Ademas, el retiro solicita `pin` porque ATM es el canal de mayor restriccion: entrega respuestas minimas y valida operaciones criticas antes de aprobarlas.
