# BFF Web

Servicio para entregar un contrato completo para banca web.

Consume `core-banking` y expone endpoints optimizados para dashboards, historiales y vistas completas del cliente.

## Endpoint

```bash
curl -k -u webuser:web123 https://localhost:8081/web/cuentas/101/dashboard
```

## Seguridad del canal

Este BFF expone sus endpoints por HTTPS y exige autenticacion HTTP Basic. Solo usuarios con rol `WEB` pueden acceder a `/web/**`; usuarios validos de otros canales reciben `403 Forbidden`.
