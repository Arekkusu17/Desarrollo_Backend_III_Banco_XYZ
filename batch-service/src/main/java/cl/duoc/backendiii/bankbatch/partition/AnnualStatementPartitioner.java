package cl.duoc.backendiii.bankbatch.partition;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnnualStatementPartitioner extends CsvRangePartitioner {
    public AnnualStatementPartitioner(@Value("${legacy.data.week}") String dataWeek) {
        super(dataWeek, "cuentas_anuales.csv", "annualPartition");
    }
}
