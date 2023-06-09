package cz.pwf.filenet.config.rest;

import cz.pwf.filenet.pwf_ecm_filenet_api_client.handler.ApiClient;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

/**
 * Třída pro konfigurace REST API klienta, komunikujícího s FileNet REST API.
 */
@Configuration
@ComponentScan("cz.pwf.filenet.pwf_ecm_filenet_api_client")
public class EcmDocumentApiClient {
    @Value("${rest.client.pwf_ecm_filenet.url:https://restapidv.pwfdata.corp}")
    private String apiBaseUrl;

    @Value("${rest.client.pwf_ecm_filenet.debugging:false}")
    private boolean apiDebuggingEnabled;

    @Value("${rest.client.pwf_ecm_filenet.username}")
    private String username;

    @Value("${rest.client.pwf_ecm_filenet.password}")
    private String password;

    @Bean
    @Primary
    public ApiClient pwfEcmFileNetDocumentApiClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(apiBaseUrl);
        apiClient.setDebugging(apiDebuggingEnabled);
        apiClient.setUsername(username);
        apiClient.setPassword(password);

        return apiClient;
    }
}