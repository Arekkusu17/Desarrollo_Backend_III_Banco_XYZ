package cl.duoc.backendiii.bankbatch.partition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AnnualStatementPartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(AnnualStatementPartitioner.class);

    private final String dataWeek;

    public AnnualStatementPartitioner(@Value("${legacy.data.week}") String dataWeek) {
        this.dataWeek = dataWeek;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int totalRecords = countAnnualRecords();
        int effectiveGridSize = Math.max(gridSize, 1);
        int partitionSize = (int) Math.ceil((double) totalRecords / effectiveGridSize);
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        int start = 0;
        for (int i = 0; i < effectiveGridSize && start < totalRecords; i++) {
            int end = Math.min(start + partitionSize - 1, totalRecords - 1);
            ExecutionContext context = new ExecutionContext();
            context.putInt("start", start);
            context.putInt("end", end);
            context.putString("partitionName", "annualPartition" + i);

            partitions.put("annualPartition" + i, context);
            log.info("Creada annualPartition{} -> start={}, end={}, totalRecords={}", i, start, end, totalRecords);

            start = end + 1;
        }

        return partitions;
    }

    private int countAnnualRecords() {
        ClassPathResource resource = new ClassPathResource("input/" + dataWeek + "/cuentas_anuales.csv");
        int count = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                count++;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo contar el archivo anual para particionamiento", ex);
        }

        return count;
    }
}
