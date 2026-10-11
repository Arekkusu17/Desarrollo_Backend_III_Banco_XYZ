package cl.duoc.backendiii.bankbatch.reader;

import cl.duoc.backendiii.bankbatch.domain.LegacyTransaction;

import java.io.IOException;

// Reader for input/{week}/transacciones.csv.
// The synchronized read() behavior is inherited from AbstractLegacyCsvReader.
public class LegacyTransactionCsvReader extends AbstractLegacyCsvReader<LegacyTransaction> {

    public LegacyTransactionCsvReader(String resourcePath) throws IOException {
        super(resourcePath, 4);
    }

    public LegacyTransactionCsvReader(String resourcePath, int start, Integer end) throws IOException {
        super(resourcePath, 4, start, end);
    }

    @Override
    protected LegacyTransaction map(String[] fields, boolean duplicate) {
        return new LegacyTransaction(fields[0], fields[1], fields[2], fields[3], duplicate);
    }

    @Override
    protected String deduplicationKey(String[] fields) {
        try {
            long id = cl.duoc.backendiii.bankbatch.processor.LegacyParsing.parseLong(fields[0], "id");
            var date = cl.duoc.backendiii.bankbatch.processor.LegacyParsing.parseDate(fields[1]);
            var amount = cl.duoc.backendiii.bankbatch.processor.LegacyParsing.parseMoney(fields[2], "monto");
            var type = cl.duoc.backendiii.bankbatch.processor.LegacyParsing.requireText(fields[3], "tipo").toLowerCase();
            return id + "|" + date + "|" + amount + "|" + type;
        } catch (RuntimeException ex) { return null; }
    }

    @Override
    protected LegacyTransaction map(String[] fields) {
        return new LegacyTransaction(fields[0], fields[1], fields[2], fields[3]);
    }
}
