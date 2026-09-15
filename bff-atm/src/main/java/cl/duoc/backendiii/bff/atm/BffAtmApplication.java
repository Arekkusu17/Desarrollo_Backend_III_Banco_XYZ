package cl.duoc.backendiii.bff.atm;

import cl.duoc.backendiii.bff.common.config.BffCommonConfiguration;
import cl.duoc.backendiii.bff.common.config.GlobalBffExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({BffCommonConfiguration.class, GlobalBffExceptionHandler.class})
public class BffAtmApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffAtmApplication.class, args);
    }
}
