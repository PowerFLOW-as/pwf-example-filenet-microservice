package cz.pwf.filenet.config;

import cz.notix.zeebe.secure.ZeebeJwtTokenConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfigurační třída pro Zeebe beany.
 */
@Slf4j
@Configuration
public class ZeebeBeansConfiguration {

    @Bean
    public ZeebeJwtTokenConsumer zeebeJwtTokenConsumer() {
        return token -> log.warn("JWT token consumer not implemented");
    }

}
