# Arquitectura de eventos Semana 7 Banco XYZ

La solucion usa una saga coreografiada con Kafka. El BFF ATM inicia el retiro, los demas servicios reaccionan a eventos y el flujo se compensa si la evaluacion de riesgo falla despues de reservar fondos.

```mermaid
flowchart LR
    Client[Cliente ATM o Postman] --> Gateway[API Gateway 8084]
    Gateway --> ATM[bff-atm 8083]

    ATM -- withdrawal.requested --> K1[(Kafka)]
    K1 --> Core[core-banking 8080]
    Core -- funds.reserved --> K2[(Kafka)]
    Core -- funds.rejected --> K3[(Kafka)]

    K2 --> Risk[risk-service 8090]
    Risk -- risk.approved --> K4[(Kafka)]
    Risk -- risk.rejected --> K5[(Kafka)]

    K4 --> ATM
    K5 --> ATM
    K3 --> ATM

    ATM -- withdrawal.confirmed --> K6[(Kafka)]
    ATM -- withdrawal.cancelled --> K7[(Kafka)]
    ATM -- funds.release-requested --> K8[(Kafka)]

    K8 --> Core
    Core -- funds.released --> K9[(Kafka)]

    K6 --> Notification[notification-service 8085]
    K7 --> Notification
    K9 --> Notification

    Risk -. Circuit Breaker riskService .-> Fallback[Fallback Risk service unavailable]
    Fallback -. risk.rejected .-> K5
```

## Flujo exitoso

1. `bff-atm` valida formato del monto y PIN, crea `transactionId` y publica `withdrawal.requested`.
2. `core-banking` valida cuenta y saldo disponible, reserva fondos en memoria y publica `funds.reserved`.
3. `risk-service` evalua la operacion con Circuit Breaker y publica `risk.approved`.
4. `bff-atm` marca la transaccion como `CONFIRMED` y publica `withdrawal.confirmed`.
5. `notification-service` consume el evento final y registra la notificacion.

## Flujo rechazado por fondos

1. `core-banking` recibe `withdrawal.requested`.
2. Si la cuenta no existe, el monto es invalido o el saldo no alcanza, publica `funds.rejected`.
3. `bff-atm` marca la transaccion como rechazada y publica `withdrawal.cancelled`.
4. `notification-service` registra la cancelacion.

## Flujo con compensacion

1. `core-banking` reserva fondos y publica `funds.reserved`.
2. `risk-service` rechaza o ejecuta fallback de Resilience4j y publica `risk.rejected`.
3. `bff-atm` publica `funds.release-requested` y `withdrawal.cancelled`.
4. `core-banking` libera la reserva y publica `funds.released`.
5. `notification-service` registra la cancelacion y la liberacion de fondos.
