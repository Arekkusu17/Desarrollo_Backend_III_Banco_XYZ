package cl.duoc.backendiii.bankbatch.reader;

import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Base reader for the legacy CSV files used in the batch jobs.
// It follows the same approach used in the Week 2 reference project:
// load the CSV lines once, keep them in memory, and expose a synchronized read() method.
// Since it implements ItemReader only, not ItemStream, Spring Batch does not try to store
// restart offset metadata while the Step is running with a TaskExecutor.
public abstract class AbstractLegacyCsvReader<T> implements ItemReader<T> {

    private final List<String> lines;
    private final int expectedFields;
    private int currentIndex = 0;

    protected AbstractLegacyCsvReader(String resourcePath, int expectedFields) throws IOException {
        this.lines = loadLines(resourcePath);
        this.expectedFields = expectedFields;
    }

    @Override
    public synchronized T read() {
        if (currentIndex >= lines.size()) {
            return null;
        }

        String line = lines.get(currentIndex);
        int lineNumber = currentIndex + 2;
        currentIndex++;

        String[] fields = line.split(",", -1);
        if (fields.length != expectedFields) {
            throw new FlatFileParseException(
                    "Invalid CSV field count. Expected " + expectedFields + " but found " + fields.length,
                    line,
                    lineNumber);
        }

        return map(fields);
    }

    protected abstract T map(String[] fields);

    private List<String> loadLines(String resourcePath) throws IOException {
        List<String> loadedLines = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(resourcePath);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                loadedLines.add(line);
            }
        }

        return loadedLines;
    }
}
