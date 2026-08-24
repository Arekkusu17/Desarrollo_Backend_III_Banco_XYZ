package cl.duoc.backendiii.bankbatch.reader;

import cl.duoc.backendiii.bankbatch.domain.LegacyAnnualEntry;

import java.io.IOException;

// Reader for input/{week}/cuentas_anuales.csv.
// It mirrors the professor's reader approach to avoid ItemStream restart warnings with TaskExecutor.
public class LegacyAnnualEntryCsvReader extends AbstractLegacyCsvReader<LegacyAnnualEntry> {

    public LegacyAnnualEntryCsvReader(String resourcePath) throws IOException {
        super(resourcePath, 5);
    }

    @Override
    protected LegacyAnnualEntry map(String[] fields) {
        return new LegacyAnnualEntry(fields[0], fields[1], fields[2], fields[3], fields[4]);
    }
}
