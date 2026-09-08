# BFF Web

Servicio para entregar un contrato completo para banca web.

Consume `core-banking` y expone endpoints optimizados para dashboards, historiales y vistas completas del cliente.

## Endpoint

```bash
curl http://localhost:8081/web/cuentas/101/dashboard \
  -H "X-Channel-Token: WEB-SECRET"
```

## Seguridad del canal

Este BFF exige el header `X-Channel-Token` con el token configurado en `channel.auth-token`. La validacion representa que la banca web tiene un canal autorizado propio y puede acceder a un contrato mas completo que Mobile o ATM.
