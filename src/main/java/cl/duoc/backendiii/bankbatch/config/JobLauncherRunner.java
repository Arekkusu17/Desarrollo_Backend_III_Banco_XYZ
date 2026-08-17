package cl.duoc.backendiii.bankbatch.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// This class is responsible for launching the batch jobs when the application starts. 
// It implements CommandLineRunner to execute the jobs with unique run IDs.
public class JobLauncherRunner {

    @Bean
    CommandLineRunner runBankJobs(JobLauncher jobLauncher,
                                  Job dailyTransactionsJob,
                                  Job monthlyInterestJob,
                                  Job annualStatementsJob) {
        return args -> {
            long runId = System.currentTimeMillis(); // Generate a unique run ID based on the current timestamp

            // Launch the daily transactions job with the unique run ID
            jobLauncher.run(dailyTransactionsJob, new JobParametersBuilder()
                    .addLong("run.id", runId)
                    .toJobParameters());
            // Launch the monthly interest job with the unique run ID        
            jobLauncher.run(monthlyInterestJob, new JobParametersBuilder()
                    .addLong("run.id", runId)
                    .toJobParameters());
            // Launch the annual statements job with the unique run ID
            jobLauncher.run(annualStatementsJob, new JobParametersBuilder()
                    .addLong("run.id", runId)
                    .toJobParameters());
        };
    }
}
