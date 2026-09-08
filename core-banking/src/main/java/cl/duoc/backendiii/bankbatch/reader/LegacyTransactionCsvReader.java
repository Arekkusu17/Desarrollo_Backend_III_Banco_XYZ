package cl.duoc.backendiii.bankbatch.reader;

import cl.duoc.backendiii.bankbatch.domain.LegacyTransaction;

import java.io.IOException;

// Reader for input/{week}/transacciones.csv.
// The synchronized read() behavior is inherited from AbstractLegacyCsvReader.
public class LegacyTransactionCsvReader extends AbstractLegacyCsvReader<LegacyTransaction> {

    public LegacyTransactionCsvReader(String resourcePath) throws IOException {
        super(resourcePath, 4);
    }

    @Override
    protected LegacyTransaction map(String[] fields) {
        return new LegacyTransaction(fields[0], fields[1], fields[2], fields[3]);
    }
}
