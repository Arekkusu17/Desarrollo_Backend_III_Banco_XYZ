package cl.duoc.backendiii.bankbatch.reader;

import cl.duoc.backendiii.bankbatch.domain.LegacyAnnualEntry;

import java.io.IOException;

// Reader for input/{week}/cuentas_anuales.csv.
// It mirrors the professor's reader approach to avoid ItemStream restart warnings with TaskExecutor.
public class LegacyAnnualEntryCsvReader extends AbstractLegacyCsvReader<LegacyAnnualEntry> {

    public LegacyAnnualEntryCsvReader(String resourcePath) throws IOException {
        super(resourcePath, 5);
    }

    public LegacyAnnualEntryCsvReader(String resourcePath, int startIndex, Integer endIndexInclusive) throws IOException {
        super(resourcePath, 5, startIndex, endIndexInclusive);
    }

    @Override
    protected LegacyAnnualEntry map(String[] fields) {
        return new LegacyAnnualEntry(fields[0], fields[1], fields[2], fields[3], fields[4]);
    }

    @Override
    protected LegacyAnnualEntry map(String[] fields, boolean duplicate) {
        return new LegacyAnnualEntry(fields[0], fields[1], fields[2], fields[3], fields[4], duplicate);
    }

    @Override
    protected String deduplicationKey(String[] fields) {
        try {
            return cl.duoc.backendiii.bankbatch.processor.LegacyParsing.parseLong(fields[0], "id") + "|"
                    + cl.duoc.backendiii.bankbatch.processor.LegacyParsing.parseDate(fields[1]) + "|"
                    + cl.duoc.backendiii.bankbatch.processor.LegacyParsing.requireText(fields[2], "tipo").toLowerCase() + "|"
                    + cl.duoc.backendiii.bankbatch.processor.LegacyParsing.parseMoney(fields[3], "monto") + "|"
                    + cl.duoc.backendiii.bankbatch.processor.LegacyParsing.requireText(fields[4], "descripcion");
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
