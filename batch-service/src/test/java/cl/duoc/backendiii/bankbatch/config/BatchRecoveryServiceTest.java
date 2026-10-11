package cl.duoc.backendiii.bankbatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class BatchRecoveryServiceTest {
    @Test
    void retriesFailedInstanceWithSameParametersAndDoesNotRerunCompletedJob() throws Exception {
        verifyRecovery(false);
    }

    @Test
    void boundsRetriesAcrossServiceRestarts() throws Exception {
        verifyRecovery(true);
    }

    private void verifyRecovery(boolean alwaysFails) throws Exception {
        var ds = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2)
                .addScript("org/springframework/batch/core/schema-h2.sql").build();
        var manager = new DataSourceTransactionManager(ds);
        var repositoryFactory = new JobRepositoryFactoryBean();
        repositoryFactory.setDataSource(ds);
        repositoryFactory.setTransactionManager(manager);
        repositoryFactory.afterPropertiesSet();
        var repository = repositoryFactory.getObject();
        var explorerFactory = new JobExplorerFactoryBean();
        explorerFactory.setDataSource(ds);
        explorerFactory.setTransactionManager(manager);
        explorerFactory.afterPropertiesSet();
        var explorer = explorerFactory.getObject();
        var launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(repository);
        launcher.afterPropertiesSet();
        var executions = new AtomicInteger();
        var step = new StepBuilder("recoverableStep", repository).tasklet((contribution, context) -> {
            if (executions.incrementAndGet() == 1 || alwaysFails) {
                throw new org.springframework.dao.TransientDataAccessResourceException("Temporary outage");
            }
            return RepeatStatus.FINISHED;
        }, manager).build();
        var job = new JobBuilder("recoverableJob", repository).start(step).build();
        var recovery = new BatchRecoveryService(List.of(job), launcher, repository, explorer, "eft", "test", 3, 0);
        recovery.runAll();
        var restarted = new BatchRecoveryService(List.of(job), launcher, repository, explorer, "eft", "test", 3, 0);
        restarted.runAll();
        assertThat(executions.get()).isEqualTo(alwaysFails ? 3 : 2);
        assertThat(explorer.getJobInstances("recoverableJob", 0, 10)).hasSize(1);
        assertThat(restarted.completed()).isEqualTo(!alwaysFails);
        ds.shutdown();
    }
}
