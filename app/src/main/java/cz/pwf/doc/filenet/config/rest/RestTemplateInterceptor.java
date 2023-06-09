package cz.pwf.filenet.config.rest;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.CollectionUtils;

/**
 * Třída pro zachycení HTTP request/response zpráv a jejich následné logování.
 */
@Slf4j
@NoArgsConstructor
public class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final ObjectMapper objectMapper;
    private String[] fieldsToExclude;

    public RestTemplateInterceptor(String[] fieldsToExclude) {
        this.fieldsToExclude = fieldsToExclude;
    }

    {
        objectMapper = new ObjectMapper();
        objectMapper.configure(Feature.AUTO_CLOSE_SOURCE, true);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        logRequest(request, body);
        ClientHttpResponse response = execution.execute(request, body);
        logResponse(response);
        return response;
    }

    private void excludeValuesOfFieldsFromPayload(Map<String, Object> payload) {
        if (Objects.isNull(this.fieldsToExclude) || CollectionUtils.isEmpty(payload)) {
            return;
        }

        for (String fieldToExclude : this.fieldsToExclude) {
            payload.computeIfPresent(fieldToExclude, (k, v) -> {
                if (v instanceof String) {
                    return "#";
                }

                return v;
            });
        }
    }

    @SneakyThrows
    private void logRequest(HttpRequest request, byte[] body) {
        log.info("URI: " + request.getURI());
        log.info("HTTP Method: " + request.getMethod());
        log.info("HTTP Headers: " + headersToString(request.getHeaders()));

        if (body.length > 0) {
            Map<String, Object> bodyMap = objectMapper.readValue(body, HashMap.class);
            if (!log.isDebugEnabled()) {
                excludeValuesOfFieldsFromPayload(bodyMap);
            }

            log.info("Request Body: " + objectMapper.writeValueAsString(bodyMap));
        } else {
            log.info("Request Body: No body");
        }
    }

    private void logResponse(ClientHttpResponse response) throws IOException {
        log.info("HTTP Status Code: " + response.getRawStatusCode());
        log.info("Status Text: " + response.getStatusText());
        log.info("HTTP Headers: " + headersToString(response.getHeaders()));

        InputStream responseBodyStream = response.getBody();
        if (responseBodyStream.available() > 0) {
            Map<String, Object> bodyMap = objectMapper.readValue(responseBodyStream, HashMap.class);
            if (!log.isDebugEnabled()) {
                excludeValuesOfFieldsFromPayload(bodyMap);
            }

            log.info("Response Body: " + objectMapper.writeValueAsString(bodyMap));
        }
    }

    private String headersToString(HttpHeaders headers) {
        StringBuilder builder = new StringBuilder();
        for(Entry<String, List<String>> entry : headers.entrySet()) {
            builder.append(entry.getKey()).append("=[");
            for(String value : entry.getValue()) {
                builder.append(value).append(",");
            }
            builder.setLength(builder.length() - 1);
            builder.append("],");
        }
        builder.setLength(builder.length() - 1);
        return builder.toString();
    }
}