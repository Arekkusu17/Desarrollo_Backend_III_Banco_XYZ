package cl.duoc.backendiii.bankbatch.config;

import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.List;

@Service
@EnableScheduling
public class BatchRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(BatchRecoveryService.class);
    private final List<Job> jobs;
    private final JobLauncher launcher;
    private final JobRepository repository;
    private final JobExplorer explorer;
    private final JobParameters parameters;
    private final int maxAttempts;
    private final long delay;
    private final BatchExecutionLock locks;

    public BatchRecoveryService(List<Job> jobs, JobLauncher launcher, JobRepository repository,
            JobExplorer explorer, @Value("${legacy.data.week}") String dataWeek,
            @Value("${batch.run-key:}") String runKey,
            @Value("${batch.recovery.max-attempts:3}") int maxAttempts,
            @Value("${batch.recovery.delay-ms:1000}") long delay, BatchExecutionLock locks) {
        this.jobs = jobs;
        this.launcher = launcher;
        this.repository = repository;
        this.explorer = explorer;
        this.locks = locks;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.delay = Math.max(0, Math.min(delay, 30000));
        parameters = new JobParametersBuilder().addString("data.week", dataWeek)
                .addString("run.key", runKey.isBlank() ? LocalDate.now().toString() : runKey)
                .toJobParameters();
    }

    @Scheduled(fixedDelayString = "${batch.recovery.poll-ms:60000}",
            initialDelayString = "${batch.recovery.poll-ms:60000}")
    public synchronized void runAll() {
        for (Job job : jobs) {
            try {
                run(job);
            } catch (Exception ex) {
                log.error("Job {} pendiente de recuperacion: {}", job.getName(), ex.getMessage());
            }
        }
    }

    private void run(Job job) throws Exception {
        try (var lease = locks.acquire(job.getName(), parameters)) {
            if (lease == null) return;
            runOwned(job);
        }
    }

    private void runOwned(Job job) throws Exception {
        JobExecution previous = repository.getLastJobExecution(job.getName(), parameters);
        if (previous != null && previous.getStatus() == BatchStatus.COMPLETED) return;
        if (previous != null) recoverInterrupted(previous);
        int attempts = previous == null ? 0 : explorer.getJobExecutions(previous.getJobInstance()).size();
        while (attempts < maxAttempts) {
            JobExecution execution = launcher.run(job, parameters);
            attempts++;
            if (execution.getStatus() == BatchStatus.COMPLETED) return;
            if (execution.getStatus() != BatchStatus.FAILED && execution.getStatus() != BatchStatus.STOPPED) return;
            if (attempts < maxAttempts) {
                log.warn("Reejecucion automatica {} intento {}/{} con los mismos parametros",
                        job.getName(), attempts + 1, maxAttempts);
                Thread.sleep(delay);
            }
        }
        log.error("Job {} agoto {} intentos; requiere revision", job.getName(), maxAttempts);
    }

    private void recoverInterrupted(JobExecution previous) {
        JobExecution execution = explorer.getJobExecution(previous.getId());
        for (StepExecution step : execution.getStepExecutions()) {
            if (step.getStatus().isRunning() || step.getStatus() == BatchStatus.UNKNOWN) {
                step.setStatus(BatchStatus.FAILED);
                step.setExitStatus(ExitStatus.FAILED.addExitDescription("Ejecucion interrumpida; checkpoint conservado"));
                step.setEndTime(java.time.LocalDateTime.now());
                repository.update(step);
            }
        }
        if (execution.isRunning() || execution.getStatus() == BatchStatus.UNKNOWN) {
            execution.setStatus(BatchStatus.FAILED);
            execution.setExitStatus(ExitStatus.FAILED.addExitDescription("Recuperacion de ejecucion sin propietario activo"));
            execution.setEndTime(java.time.LocalDateTime.now());
            repository.update(execution);
            log.warn("Recuperada ejecucion interrumpida {} id={}", execution.getJobInstance().getJobName(), execution.getId());
        }
    }

    public boolean completed() {
        return jobs.stream().allMatch(job -> {
            JobExecution execution = repository.getLastJobExecution(job.getName(), parameters);
            return execution != null && execution.getStatus() == BatchStatus.COMPLETED;
        });
    }
}
