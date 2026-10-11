package cl.duoc.backendiii.bankbatch.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobLauncherRunner {
    @Bean
    CommandLineRunner runBankJobs(BatchRecoveryService recovery) {
        return args -> recovery.runAll();
    }
}
