package cl.duoc.backendiii.bankbatch.partition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class CsvRangePartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(CsvRangePartitioner.class);

    private final String resourcePath;
    private final String prefix;

    public CsvRangePartitioner(String dataWeek, String file, String prefix) {
        resourcePath = "input/" + dataWeek + "/" + file;
        this.prefix = prefix;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int totalRecords = countRecords();
        int effectiveGridSize = Math.max(gridSize, 1);
        int partitionSize = (int) Math.ceil((double) totalRecords / effectiveGridSize);
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        int start = 0;
        for (int i = 0; i < effectiveGridSize && start < totalRecords; i++) {
            int end = Math.min(start + partitionSize - 1, totalRecords - 1);
            ExecutionContext context = new ExecutionContext();
            context.putInt("start", start);
            context.putInt("end", end);
            context.putString("partitionName", prefix + i);

            partitions.put(prefix + i, context);
            log.info("Creada {}{} -> start={}, end={}, totalRecords={}", prefix, i, start, end, totalRecords);

            start = end + 1;
        }

        return partitions;
    }

    private int countRecords() {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        int count = 0;

        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             var parser = org.apache.commons.csv.CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            for (var record : parser) {
                count++;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo contar el archivo CSV para particionamiento", ex);
        }

        return count;
    }
}
