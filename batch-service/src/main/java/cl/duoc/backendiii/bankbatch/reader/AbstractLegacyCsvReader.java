package cl.duoc.backendiii.bankbatch.reader;

import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Each partition owns a reader and commits its offset with the chunk transaction.
public abstract class AbstractLegacyCsvReader<T> implements ItemStreamReader<T> {

    private final List<String[]> lines;
    private final int expectedFields;
    private final int endIndexInclusive;
    private int currentIndex;
    private final int startIndex;
    private java.util.Map<String, Integer> firstRows;

    protected AbstractLegacyCsvReader(String resourcePath, int expectedFields) throws IOException {
        this(resourcePath, expectedFields, 0, null);
    }

    protected AbstractLegacyCsvReader(String resourcePath, int expectedFields, int startIndex, Integer endIndexInclusive) throws IOException {
        this.lines = loadLines(resourcePath);
        this.expectedFields = expectedFields;
        this.startIndex = Math.max(startIndex, 0);
        this.currentIndex = this.startIndex;
        int lastAvailableIndex = Math.max(lines.size() - 1, -1);
        this.endIndexInclusive = endIndexInclusive == null
                ? lastAvailableIndex
                : Math.min(endIndexInclusive, lastAvailableIndex);
    }

    @Override
    public synchronized void open(ExecutionContext context) {
        currentIndex = context.getInt("csv.nextIndex", startIndex);
        if (currentIndex < startIndex || currentIndex > endIndexInclusive + 1) {
            throw new org.springframework.batch.item.ItemStreamException("Checkpoint fuera de la particion");
        }
    }

    @Override
    public synchronized void update(ExecutionContext context) { context.putInt("csv.nextIndex", currentIndex); }

    @Override
    public void close() { }

    @Override
    public synchronized T read() {
        if (currentIndex > endIndexInclusive || currentIndex >= lines.size()) {
            return null;
        }

        String[] fields = lines.get(currentIndex);
        String line = String.join(",", fields);
        int lineNumber = currentIndex + 2;
        currentIndex++;

        if (fields.length != expectedFields) {
            throw new FlatFileParseException(
                    "Invalid CSV field count. Expected " + expectedFields + " but found " + fields.length,
                    line,
                    lineNumber);
        }

        if (firstRows == null) {
            firstRows = new java.util.HashMap<>();
            for (int i = 0; i < lines.size(); i++) {
                String[] candidate = lines.get(i);
                if (candidate.length == expectedFields) {
                    String key = deduplicationKey(candidate);
                    if (key != null) firstRows.putIfAbsent(key, i);
                }
            }
        }
        String key = deduplicationKey(fields);
        boolean duplicate = key != null && firstRows.get(key) != currentIndex - 1;
        return map(fields, duplicate);
    }

    protected abstract T map(String[] fields);

    protected T map(String[] fields, boolean duplicate) {
        return map(fields);
    }

    protected String deduplicationKey(String[] fields) {
        return null;
    }

    private List<String[]> loadLines(String resourcePath) throws IOException {
        List<String[]> loadedLines = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(resourcePath);

        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             var parser = org.apache.commons.csv.CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            for (var record : parser) {
                loadedLines.add(record.values());
            }
        }

        return loadedLines;
    }
}
