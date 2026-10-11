package cl.duoc.backendiii.bankbatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BancoXyzBatchApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(BancoXyzBatchApplication.class, args);
        if (context.getEnvironment().getProperty("batch.exit-after-run", Boolean.class, false)) {
            boolean completed = context.getBean(cl.duoc.backendiii.bankbatch.config.BatchRecoveryService.class).completed();
            System.exit(SpringApplication.exit(context, () -> completed ? 0 : 1));
        }
    }
}
