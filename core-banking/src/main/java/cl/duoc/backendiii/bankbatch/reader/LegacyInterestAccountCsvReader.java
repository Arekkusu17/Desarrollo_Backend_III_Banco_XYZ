package cl.duoc.backendiii.bankbatch.reader;

import cl.duoc.backendiii.bankbatch.domain.LegacyInterestAccount;

import java.io.IOException;

// Reader for input/{week}/intereses.csv.
// It keeps the reader simple and thread-safe for parallel chunk execution.
public class LegacyInterestAccountCsvReader extends AbstractLegacyCsvReader<LegacyInterestAccount> {

    public LegacyInterestAccountCsvReader(String resourcePath) throws IOException {
        super(resourcePath, 5);
    }

    @Override
    protected LegacyInterestAccount map(String[] fields) {
        return new LegacyInterestAccount(fields[0], fields[1], fields[2], fields[3], fields[4]);
    }
}
