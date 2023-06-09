package cz.pwf.filenet.config.rest;

import lombok.SneakyThrows;
import org.apache.http.impl.client.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Třída definující REST teplate.
 */
@Configuration
public class RestTemplateConfig {

    @SneakyThrows
    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(HttpClients.createDefault());
        return new RestTemplate(new BufferingClientHttpRequestFactory(requestFactory));
    }
}
