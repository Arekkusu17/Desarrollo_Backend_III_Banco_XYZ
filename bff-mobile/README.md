# BFF Mobile

Servicio para entregar un contrato liviano para app movil.

Consume `core-banking` y expone endpoints reducidos, pensados para velocidad y bajo consumo de datos.

## Endpoint

```bash
curl http://localhost:8082/mobile/cuentas/101/inicio \
  -H "X-Channel-Token: MOBILE-SECRET"
```

## Seguridad del canal

Este BFF exige el header `X-Channel-Token` con el token configurado en `channel.auth-token`. La validacion representa que la app movil tiene un canal autorizado propio y solo recibe un contrato liviano con los datos necesarios.
