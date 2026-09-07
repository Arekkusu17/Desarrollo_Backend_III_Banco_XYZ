# BFF ATM

Servicio para cajeros automaticos.

Consume `core-banking` y expone operaciones acotadas como consulta de saldo y retiros.

## Endpoints

```bash
curl http://localhost:8083/atm/cuentas/101/saldo
curl -X POST http://localhost:8083/atm/cuentas/101/retiros \
  -H "Content-Type: application/json" \
  -d '{"amount":1000,"pin":"1234"}'
```
