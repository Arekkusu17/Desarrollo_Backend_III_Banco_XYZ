# BFF ATM

Servicio para cajeros automaticos.

Consume `core-banking` y expone operaciones acotadas como consulta de saldo y retiros.

## Endpoints

```bash
curl -k -u atmuser:atm123 https://localhost:8083/atm/cuentas/101/saldo

curl -k -u atmuser:atm123 -X POST https://localhost:8083/atm/cuentas/101/retiros \
  -H "Content-Type: application/json" \
  -d '{"amount":1000,"pin":"1234"}'
```

## Seguridad del canal

Este BFF expone sus endpoints por HTTPS y exige autenticacion HTTP Basic. Solo usuarios con rol `ATM` pueden acceder a `/atm/**`. Ademas, el retiro solicita `pin` porque ATM es el canal de mayor restriccion: entrega respuestas minimas y valida operaciones criticas antes de aprobarlas.
