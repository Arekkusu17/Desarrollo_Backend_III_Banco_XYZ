package cl.duoc.backendiii.bff.mobile;

import cl.duoc.backendiii.bff.common.config.BffCommonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(BffCommonConfiguration.class)
public class BffMobileApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffMobileApplication.class, args);
    }
}
