package cz.pwf.filenet.config;

import cz.pwf.filenet.service.FileNetService;
import cz.notix.document.plugin.configuration.DefaultOptionsRegistry;
import cz.notix.document.plugin.configuration.DmsConfigurer;
import cz.notix.document.plugin.configuration.DmsOperationsBuilder;
import cz.notix.document.plugin.connector.DmsOperations;
import cz.notix.document.plugin.domain.UserIdSupplier;
import cz.notix.document.plugin.rest.DocumentCacheController;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Konfigurační třída pro nastavení parametrů document pluginu.
 */
@Configuration
@Import(DocumentCacheController.class)
@RequiredArgsConstructor
public class DmsConfiguration implements DmsConfigurer {

    private final FileNetService fileNetService;

    @Value("${filenet.namespace}")
    private String namespace;

    @Override
    public DmsOperations configure(final DmsOperationsBuilder dmsOperationsBuilder, final DefaultOptionsRegistry registry) {
        DmsOperations defaultDmsOperation = dmsOperationsBuilder.configure(fileNetService).build();

        return dmsOperationsBuilder
                .defaultNamespace(namespace)
                .metadataNotSupported()
                .operatedBy(defaultDmsOperation)
                .configure(defaultDmsOperation)
                .build();
    }

    @Bean
    public UserIdSupplier userIdSupplier() {
        return () -> "test"; // replace by spring security in real impl
    }
}
