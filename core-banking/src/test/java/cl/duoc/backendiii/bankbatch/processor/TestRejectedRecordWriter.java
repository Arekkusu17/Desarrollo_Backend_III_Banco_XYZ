package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;

import java.util.ArrayList;
import java.util.List;

class TestRejectedRecordWriter extends RejectedRecordWriter {

    private final List<RejectedRecord> rejectedRecords = new ArrayList<>();

    TestRejectedRecordWriter() {
        super(null, 120, 120, 500);
    }

    @Override
    public void reject(RejectedRecord rejectedRecord) {
        rejectedRecords.add(rejectedRecord);
    }

    List<RejectedRecord> rejectedRecords() {
        return rejectedRecords;
    }
}
