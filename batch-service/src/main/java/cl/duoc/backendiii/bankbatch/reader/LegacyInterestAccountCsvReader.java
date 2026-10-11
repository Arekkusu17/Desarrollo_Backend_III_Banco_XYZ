package cl.duoc.backendiii.bankbatch.reader;

import cl.duoc.backendiii.bankbatch.domain.LegacyInterestAccount;

import java.io.IOException;

// Reader for input/{week}/intereses.csv.
// It keeps the reader simple and thread-safe for parallel chunk execution.
public class LegacyInterestAccountCsvReader extends AbstractLegacyCsvReader<LegacyInterestAccount> {

    public LegacyInterestAccountCsvReader(String resourcePath) throws IOException {
        super(resourcePath, 5);
    }

    public LegacyInterestAccountCsvReader(String resourcePath, int start, Integer end) throws IOException {
        super(resourcePath, 5, start, end);
    }

    @Override
    protected LegacyInterestAccount map(String[] fields) {
        return new LegacyInterestAccount(fields[0], fields[1], fields[2], fields[3], fields[4]);
    }

    @Override
    protected LegacyInterestAccount map(String[] fields, boolean duplicate) {
        return new LegacyInterestAccount(fields[0], fields[1], fields[2], fields[3], fields[4], duplicate);
    }

    @Override
    protected String deduplicationKey(String[] fields) {
        try {
            long id = cl.duoc.backendiii.bankbatch.processor.LegacyParsing.parseLong(fields[0], "id");
            cl.duoc.backendiii.bankbatch.processor.LegacyParsing.requireText(fields[1], "nombre");
            cl.duoc.backendiii.bankbatch.processor.LegacyParsing.parseMoney(fields[2], "saldo");
            cl.duoc.backendiii.bankbatch.processor.LegacyParsing.parseInt(fields[3], "edad");
            cl.duoc.backendiii.bankbatch.processor.LegacyParsing.requireText(fields[4], "tipo");
            return Long.toString(id);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
