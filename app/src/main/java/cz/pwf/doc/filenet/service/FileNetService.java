package cz.pwf.filenet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.pwf.filenet.pwf_ecm_filenet_api_client.handler.EcmApi;
import cz.pwf.filenet.pwf_ecm_filenet_api_client.model.DocumentMetadataResponse;
import cz.pwf.filenet.pwf_ecm_filenet_api_client.model.FileNetIdentificator;
import cz.pwf.filenet.pwf_ecm_filenet_api_client.model.GetDocumentResponse;
import cz.pwf.filenet.config.Constants;
import cz.pwf.filenet.model.mapper.DocumentMapper;
import cz.notix.document.plugin.connector.DmsOperations;
import cz.notix.document.plugin.connector.dto.DMSDocument;
import cz.notix.document.plugin.connector.dto.DMSDocumentData;
import cz.notix.document.plugin.connector.dto.DMSDocumentId;
import cz.notix.document.plugin.connector.dto.DMSDocumentInfo;
import cz.notix.document.plugin.connector.dto.DMSDocumentNew;
import cz.notix.document.plugin.connector.dto.DMSDocumentStorno;
import cz.notix.document.plugin.connector.dto.DMSDocumentUpdate;
import cz.notix.document.plugin.connector.dto.DmsAttribute;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servisní třída obsahující business logiku pro manipulaci s dokumenty FileNetu.
 */
@Slf4j
@Service
@Qualifier("primaryDmsOperations")
@RequiredArgsConstructor
public final class FileNetService implements DmsOperations {

    private static final String KPJM_ZEEBE_VARIABLE_KEY = "uid";
    private static final String REAUTHORIZE_KPJM_FIELD = "reauthorize";
    private static final String PWF_TECH_USER_UID = "pwfadmin";

    private final EcmApi ecmApi;
    private final DocumentMapper documentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${filenet.namespace}")
    private String namespace;

    /**
     * Metoda loguje čas strávený zpracováním HTTP požadavku.
     *
     * @param start        začátek zpracování požadavku
     * @param endpointName název endpointu, u kterého se sleduje doba zpracování požadavku
     */
    private void logEndpointCallElapsedTime(Instant start, String endpointName) {
        if (log.isDebugEnabled()) {
            Instant finish = Instant.now();
            log.debug("Call {} endpoint - elapsed time: {} ms", endpointName, Duration.between(start, finish).toMillis());
        }
    }

    /**
     * Metoda slouží k získání KPJM ze Zeebe header.
     *
     * @param zeebeHeaders objekt obsahující Zeebe headers
     * @param defaultKpjm    výchozí hodnota, která bude vrácena v případě, že nebyl nalezen v Zeebe headers příslušná hlavička s hodnotou
     * @return Vrací KPJM, pokud je obsaženo v Zeebe header, jinak {@code defaultKpjm}.
     */
    private String obtainKpjmFromZeebeHeaderOrGetDefault(Map<String, Object> zeebeHeaders, String defaultKpjm) {

        return Optional.ofNullable(zeebeHeaders)
                .map(headers -> headers.get(KPJM_ZEEBE_VARIABLE_KEY).toString())
                .orElse(defaultKpjm);
    }

    /**
     * Metoda slouží k získání KPJM z metadat dokumentu.
     *
     * @param endpointName název endpointu ze kterého je metoda volána (pro logovací účely)
     * @param metadata     kolekce metadat odkud je získána příslužná hodnota atributu {@link FileNetService#REAUTHORIZE_KPJM_FIELD}
     * @param defaultKpjm  výchozí hodnota která bude vrácena v případě, že nebyl nalezen v metadatech příslušný atribut s hodnotou
     * @return Vrací KPJM, pokud je obsaženo v metadatech, jinak {@code defaultKpjm}.
     */
    private String obtainKpjmFromMetadataOrGetDefault(String endpointName, List<DmsAttribute> metadata, String defaultKpjm) {
        log.debug("{}: metadata{}", endpointName, metadata.stream()
                .map(m -> m.getName() + " -> " + m.getValue())
                .collect(Collectors.toList()));

        return metadata.stream()
                .filter(m -> m.getName().equalsIgnoreCase(REAUTHORIZE_KPJM_FIELD))
                .map(DmsAttribute::getValue)
                .peek(m -> log.debug("{}: reauthorize KPJM - {}", endpointName, m))
                .findFirst()
                .orElseGet(() -> {
                    log.debug("{}: The 'reauthorize' attribute not found. " +
                            "Return a default KPJM: {}", endpointName, defaultKpjm);
                    return defaultKpjm;
                });
    }

    @SneakyThrows
    private Map<String, Object> castToHeadersMap(String zeebeVariables) {
        Map<String, Map<String, Object>> map = objectMapper.readValue(zeebeVariables, new TypeReference<>() {});

        return Optional.ofNullable(map.get("headers")).orElse(Collections.emptyMap());
    }

    /**
     * Metoda se pokusí reautorizovat KPJM na základě metadat dokumentu za podmínky že v těchto metadatech je
     * přítomen atribut reauthorize a v Zeebe header bylo zasláno KPJM technického uživatele.
     * Pokud není splněno, vrací se KPJM ze Zeebe header.
     *
     * @param endpointName   název endpointu ze kterého je metoda volána (pro logovací účely)
     * @param zeebeVariables objekt obsahující Zeebe headers
     * @param metadata       kolekce metadat odkud je získána příslužná hodnota atributu {@link FileNetService#REAUTHORIZE_KPJM_FIELD}
     * @return Vrací KPJM k reautorizaci, případně standardně zaslané ze Zeebe header na základě podmínky v popisu.
     */
    private String reauthorizeKpjmIfNeeded(String endpointName, Map<String, Object> zeebeVariables, List<DmsAttribute> metadata) {
        String kpjm = obtainKpjmFromZeebeHeaderOrGetDefault(zeebeVariables, null);

        if (Objects.isNull(kpjm) || PWF_TECH_USER_UID.equalsIgnoreCase(kpjm)) {
            log.debug("{}: KPJM from Zeebe header is null or equals PWF tech user: {}. Try to obtain KPJM from " +
                    "the reauthorize metadata attribute...", endpointName, PWF_TECH_USER_UID);
            kpjm = obtainKpjmFromMetadataOrGetDefault(endpointName, metadata, kpjm);
        }

        return kpjm;
    }

    @Override
    public boolean isZeebeVariablesAware() {
        return true;
    }

    /**
     * Metoda pro uložení nového dokumentu do FileNetu.
     *
     * @param doc            dokument, který má být uložen
     * @param zeebeVariables objekt obsahující Zeebe headers
     * @return Vrací informace o nově uloženém dokumentu (ID, URL, ...).
     */
    @Override
    public DMSDocumentInfo create(DMSDocumentNew doc, String zeebeVariables) {
        final String endpointName = "CreateDocument";
        Instant start = Instant.now();

        final String kpjm = reauthorizeKpjmIfNeeded(endpointName, castToHeadersMap(zeebeVariables), doc.metadata);

        ResponseEntity<FileNetIdentificator> response = ecmApi.eCMCreateDocumentWithHttpInfo(kpjm,
                UUID.randomUUID().toString(), String.valueOf(System.currentTimeMillis()), Constants.SOURCE_SYSTEM,
                documentMapper.toCreateDocumentBodyRequest(doc), null, null, null, null);
        logEndpointCallElapsedTime(start, endpointName);

        return Optional.ofNullable(response.getBody())
                .map(r -> documentMapper.toDMSDocumentInfo(r, doc))
                .orElse(null);
    }

    /**
     * Metoda vrací informace o již uloženém dokumentu ve FileNetu.
     *
     * @param id             identifikátor dokumentu
     * @param zeebeVariables objekt obsahující Zeebe headers
     * @return Vrací informace o uloženém dokumentu (ID, URL, ...).
     */
    @Override
    public DMSDocumentInfo getInfo(DMSDocumentId id, String zeebeVariables) {
        final String endpointName = "GetDocumentMetadata";
        Instant start = Instant.now();

        final String kpjm = reauthorizeKpjmIfNeeded(endpointName, castToHeadersMap(zeebeVariables), id.getDmsSpecificAttributes());

        ResponseEntity<DocumentMetadataResponse> response = ecmApi.eCMGetDocumentMetadataWithHttpInfo(kpjm, id.getId(),
                UUID.randomUUID().toString(), String.valueOf(System.currentTimeMillis()), Constants.SOURCE_SYSTEM,
                namespace, null, null, null, null, id.getVersion());
        logEndpointCallElapsedTime(start, endpointName);

        return Optional.ofNullable(response.getBody())
                .map(documentMapper::toDMSDocumentInfo)
                .orElse(null);
    }

    /**
     * Metoda poskytuje podle ID binární obsah uloženého dokumentu ve FileNetu.
     *
     * @param id             identifikátor dokumentu
     * @param zeebeVariables objekt obsahující Zeebe headers
     * @return Vrací binární obsah uloženého dokumentu.
     */
    @Override
    public DMSDocumentData getData(DMSDocumentId id, String zeebeVariables) {
        final String endpointName = "GetDocument";
        Instant start = Instant.now();

        log.info("zeebeVariables: {}", zeebeVariables);
        final String kpjm = reauthorizeKpjmIfNeeded(endpointName, castToHeadersMap(zeebeVariables), id.getDmsSpecificAttributes());

        ResponseEntity<GetDocumentResponse> data = ecmApi.eCMGetDocumentWithHttpInfo(kpjm, id.getId(),
                UUID.randomUUID().toString(), String.valueOf(System.currentTimeMillis()), Constants.SOURCE_SYSTEM,
                namespace, null, null, null, null, id.getVersion());
        logEndpointCallElapsedTime(start, endpointName);

        if (data.hasBody()) {
            return new DMSDocumentData(documentMapper.toDMSDocumentInfo(data.getBody()),
                    Optional.of(data).map(HttpEntity::getBody)
                            .map(GetDocumentResponse::getContent).map(c -> Base64.getDecoder().decode(c)).orElse(null));
        } else {
            return null;
        }
    }

    /**
     * Metoda aktualizuje dokument ve FileNetu.
     *
     * @param id             identifikátor dokumentu
     * @param doc            nová verze dokumentu, který se má aktualizovat
     * @param zeebeVariables objekt obsahující Zeebe headers
     * @return Vrací informace o aktualizovaném dokumentu (ID, URL, ...).
     */
    @Override
    public DMSDocumentInfo update(DMSDocumentId id, DMSDocumentUpdate doc, String zeebeVariables) {
        final String endpointName = "UpdateDocument";
        Instant start = Instant.now();

        final String kpjm = reauthorizeKpjmIfNeeded(endpointName, castToHeadersMap(zeebeVariables), id.getDmsSpecificAttributes());

        ResponseEntity<FileNetIdentificator> response = ecmApi.eCMUpdateDocumentWithHttpInfo(kpjm, id.getId(),
                UUID.randomUUID().toString(), String.valueOf(System.currentTimeMillis()),
                Constants.SOURCE_SYSTEM, documentMapper.toUpdateDocumentBodyRequest(namespace, doc), null,
                null, null, null);
        logEndpointCallElapsedTime(start, endpointName);

        return Optional.ofNullable(response.getBody())
                .map(r -> documentMapper.toDMSDocumentInfo(r, doc))
                .orElse(null);
    }

    /**
     * Metoda na základě ID smaže dokument ve FileNetu.
     *
     * @param id             identifikátor dokumentu
     * @param zeebeVariables objekt obsahující Zeebe headers
     * @return Vrací ID smazaného dokumentu.
     */
    @Override
    public DMSDocumentId delete(DMSDocumentId id, String zeebeVariables) {
        final String endpointName = "DeleteDocument";
        Instant start = Instant.now();

        final String kpjm = reauthorizeKpjmIfNeeded(endpointName, castToHeadersMap(zeebeVariables), id.getDmsSpecificAttributes());

        ResponseEntity<FileNetIdentificator> response = ecmApi.eCMDeleteDocumentWithHttpInfo(kpjm, id.getId(),
                UUID.randomUUID().toString(), String.valueOf(System.currentTimeMillis()), Constants.SOURCE_SYSTEM,
                namespace, null, null, null, null, null);
        logEndpointCallElapsedTime(start, endpointName);

        return Optional.ofNullable(response.getBody())
                .map(documentMapper::toDMSDocumentId)
                .orElse(null);
    }

    @Override
    public DMSDocumentInfo create(DMSDocumentNew dmsDocumentNew) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public DMSDocument get(DMSDocumentId dmsDocumentId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public DMSDocumentInfo getInfo(DMSDocumentId dmsDocumentId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public DMSDocumentData getData(DMSDocumentId dmsDocumentId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public DMSDocumentInfo update(DMSDocumentId dmsDocumentId, DMSDocumentUpdate dmsDocumentUpdate) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public DMSDocumentInfo updateMetadata(DMSDocumentId dmsDocumentId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public DMSDocumentId delete(DMSDocumentId dmsDocumentId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public DMSDocumentId storno(DMSDocumentId dmsDocumentId, DMSDocumentStorno dmsDocumentStorno) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
