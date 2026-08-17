# Banco XYZ Batch

Proyecto Spring Batch para modernizar tres procesos legacy del Banco XYZ:

- Reporte de transacciones diarias.
- Calculo de intereses mensuales.
- Generacion de estados de cuenta anuales.

## Base de datos 

Se utiliza **PostgreSQL con Docker Compose**. 

## Ejecutar

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

## Resultados

Los datos limpios se escriben en PostgreSQL:

- `daily_transaction_summary`
- `monthly_interest_results`
- `annual_statement_entries`
- `rejected_records`

Ademas, el job anual genera `output/annual_statement_report.csv`.

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
