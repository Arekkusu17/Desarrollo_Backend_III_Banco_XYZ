# BFF Mobile

Servicio para entregar un contrato liviano para app movil.

Consume `core-banking` y expone endpoints reducidos, pensados para velocidad y bajo consumo de datos.

## Endpoint

```bash
curl -k -u mobileuser:mobile123 https://localhost:8082/mobile/cuentas/101/inicio
```

## Seguridad del canal

Este BFF expone sus endpoints por HTTPS y exige autenticacion HTTP Basic. Solo usuarios con rol `MOBILE` pueden acceder a `/mobile/**`; usuarios validos de otros canales reciben `403 Forbidden`.
