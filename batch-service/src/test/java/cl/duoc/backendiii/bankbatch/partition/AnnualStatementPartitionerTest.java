package cl.duoc.backendiii.bankbatch.partition;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualStatementPartitionerTest {

    @Test
    void partitionsSemana3AnnualFileIntoContinuousRanges() {
        AnnualStatementPartitioner partitioner = new AnnualStatementPartitioner("semana_3");

        Map<String, ExecutionContext> partitions = partitioner.partition(3);
        List<ExecutionContext> contexts = List.copyOf(partitions.values());

        assertThat(partitions).hasSize(3);
        assertThat(contexts.get(0).getInt("start")).isZero();
        assertThat(contexts.get(0).getInt("end")).isEqualTo(333);
        assertThat(contexts.get(1).getInt("start")).isEqualTo(334);
        assertThat(contexts.get(1).getInt("end")).isEqualTo(667);
        assertThat(contexts.get(2).getInt("start")).isEqualTo(668);
        assertThat(contexts.get(2).getInt("end")).isEqualTo(999);
    }
}
