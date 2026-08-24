# Banco XYZ Batch

Proyecto Spring Batch para modernizar tres procesos legacy del Banco XYZ:

- Reporte de transacciones diarias.
- Calculo de intereses mensuales.
- Generacion de estados de cuenta anuales.

## Base de datos 

Se utiliza **PostgreSQL con Docker Compose**. 

## Ejecutar

Requisitos:

- Java 17.
- Docker Desktop o Docker Compose.
- Puerto local `5432` disponible para PostgreSQL.

```bash
docker compose up -d
./mvnw spring-boot:run
```

Si no tienes Maven Wrapper generado, usa:

```bash
mvn spring-boot:run
```

Por defecto se procesan los CSV de `src/main/resources/input/semana_3`. Para cambiar semana:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--legacy.data.week=semana_1"
```

Configuracion minima de base de datos:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/banco_xyz
spring.datasource.username=banco
spring.datasource.password=banco
```

El archivo `src/main/resources/schema.sql` crea automaticamente las tablas de salida cuando inicia la aplicacion.

## Resultados

Los datos limpios se escriben en PostgreSQL:

- `daily_transaction_summary`
- `monthly_interest_results`
- `annual_statement_entries`
- `rejected_records`

Ademas, el job anual genera `output/annual_statement_report.csv`.

## Verificacion rapida

Despues de ejecutar la aplicacion, se puede validar la persistencia con:

```bash
docker exec -it banco-xyz-postgres psql -U banco -d banco_xyz
```

```sql
SELECT COUNT(*) FROM daily_transaction_summary;
SELECT COUNT(*) FROM monthly_interest_results;
SELECT COUNT(*) FROM annual_statement_entries;
SELECT process_name, record_key, reason
FROM rejected_records
ORDER BY id;
```

La consola tambien imprime un resumen por cada Job con registros leidos, escritos, filtrados, commits, tabla de salida y rechazos de esa ejecucion. Ese resumen permite trazar rapidamente que se leyo, que se transformo y que quedo persistido.

## Evidencias de ejecucion

### Consola de ejecucion

La siguiente captura muestra la ejecucion de la aplicacion Spring Batch y la finalizacion de los Jobs.

![Salida de consola](screenshots/console_output.png)

### Resultados en PostgreSQL

La siguiente captura muestra la conexion al contenedor PostgreSQL, las tablas creadas, muestras de datos procesados y los conteos finales.

![Resultados en PostgreSQL](screenshots/db_results.png)

### Reporte anual generado

La siguiente captura muestra el archivo `output/annual_statement_report.csv`, generado por `annualStatementsJob`.

![Reporte anual CSV](screenshots/output_csv.png)

## Flujo Batch

La aplicacion ejecuta tres Jobs independientes. Cada Job tiene un Step principal que sigue la estructura clasica de Spring Batch:

```text
CSV legacy -> ItemReader -> ItemProcessor -> ItemWriter -> salida final
```

### Trazabilidad general

| Job | Entrada CSV | Reader | Processor | Writer | Salida persistida |
| --- | --- | --- | --- | --- | --- |
| `dailyTransactionsJob` | `input/{semana}/transacciones.csv` | `transactionReader` | `DailyTransactionProcessor` | `transactionWriter` | `daily_transaction_summary` |
| `monthlyInterestJob` | `input/{semana}/intereses.csv` | `interestReader` | `MonthlyInterestProcessor` | `interestWriter` | `monthly_interest_results` |
| `annualStatementsJob` | `input/{semana}/cuentas_anuales.csv` | `annualReader` | `AnnualStatementProcessor` | `AnnualStatementReportWriter` | `annual_statement_entries` y `output/annual_statement_report.csv` |

### Job 1: Reporte de Transacciones Diarias

```text
src/main/resources/input/{semana}/transacciones.csv
    |
    v
transactionReader
    |
    v
DailyTransactionProcessor
    |-- valida fecha
    |-- valida monto mayor que cero
    |-- valida tipo debito/credito
    |-- omite duplicados
    |-- marca anomalias por monto alto
    |-- registra rechazos en rejected_records
    |
    v
transactionWriter
    |
    v
daily_transaction_summary
```

### Job 2: Calculo de Intereses Mensuales

```text
src/main/resources/input/{semana}/intereses.csv
    |
    v
interestReader
    |
    v
MonthlyInterestProcessor
    |-- valida cuenta
    |-- valida saldo mayor que cero
    |-- valida edad en rango
    |-- valida tipo ahorro/prestamo
    |-- omite cuentas duplicadas
    |-- calcula interes y saldo final
    |-- registra rechazos en rejected_records
    |
    v
interestWriter
    |
    v
monthly_interest_results
```

### Job 3: Generacion de Estados de Cuenta Anuales

```text
src/main/resources/input/{semana}/cuentas_anuales.csv
    |
    v
annualReader
    |
    v
AnnualStatementProcessor
    |-- valida fecha
    |-- valida descripcion obligatoria
    |-- valida monto distinto de cero
    |-- valida tipo de movimiento
    |-- omite movimientos duplicados
    |-- marca egresos para revision
    |-- registra rechazos en rejected_records
    |
    v
AnnualStatementReportWriter
    |
    +--> annual_statement_entries
    |
    +--> output/annual_statement_report.csv
```

### Orquestacion

`JobLauncherRunner` ejecuta los Jobs en este orden:

```text
dailyTransactionsJob
    |
    v
monthlyInterestJob
    |
    v
annualStatementsJob
```

## Reglas de consistencia

- Fechas: se aceptan solo formatos con año primero:
  - `yyyy-MM-dd`, ejemplo `2024-03-04`.
  - `yyyy/MM/dd`, ejemplo `2024/03/04`.
- Fechas en formato `dd-MM-yyyy` o `dd/MM/yyyy` se rechazan.
- Duplicados: omite registros repetidos por clave natural.
- Montos: rechaza montos nulos, cero o negativos cuando el proceso requiere movimientos positivos.
- Transacciones: acepta tipos esperados por proceso.
- Intereses: acepta solo cuentas `ahorro` y `prestamo`; rechaza edades fuera de rango y saldos invalidos.

Los rechazos se guardan en `rejected_records` con proceso, clave, motivo y payload original para auditoria.

## Ejemplos de validacion y manejo de errores

### Transacciones diarias

Registro valido:

```csv
1,2024-01-01,1000,debito
```

Transformacion esperada:

```text
transaction_id=1, transaction_date=2024-01-01, amount=1000.00, transaction_type=debito, anomaly=false
```

Registro invalido:

```csv
3,2024-01-03,-200,debito
```

Resultado esperado:

```text
rejected_records.process_name=dailyTransactionsJob
rejected_records.record_key=3
rejected_records.reason=monto debe ser mayor que cero
```

Registro valido con alerta:

```csv
9,2024-01-07,3000,debito
```

Transformacion esperada:

```text
anomaly=true, anomaly_reason=monto sobre limite diario
```

### Intereses mensuales

Registro valido:

```csv
101,John Doe,5000,30,ahorro
```

Transformacion esperada:

```text
monthly_rate=0.0050, interest_amount=25.00, final_balance=5025.00
```

Registro invalido:

```csv
105,Charlie Green,7000,35,hipoteca
```

Resultado esperado:

```text
rejected_records.process_name=monthlyInterestJob
rejected_records.record_key=105
rejected_records.reason=tipo de cuenta no soportado: hipoteca
```

### Estados de cuenta anuales

Registro valido:

```csv
101,2024-03-15,retiro,-500,Retiro parcial
```

Transformacion esperada:

```text
audit_flag=REVISION_EGRESO
```

Registro invalido:

```csv
107,2024-12-25,deposito,0,Ingreso navideno
```

Resultado esperado:

```text
rejected_records.process_name=annualStatementsJob
rejected_records.record_key=107
rejected_records.reason=monto no puede ser cero
```

## Pruebas automatizadas

Los procesadores principales tienen pruebas unitarias para reglas de negocio y rechazos:

```bash
./mvnw test
```
