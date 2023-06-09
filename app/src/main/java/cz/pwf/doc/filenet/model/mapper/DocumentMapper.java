package cz.pwf.filenet.model.mapper;

import cz.pwf.filenet.pwf_ecm_filenet_api_client.model.CreateDocumentBodyRequest;
import cz.pwf.filenet.pwf_ecm_filenet_api_client.model.DocumentMetadataResponse;
import cz.pwf.filenet.pwf_ecm_filenet_api_client.model.FileNetAttributes;
import cz.pwf.filenet.pwf_ecm_filenet_api_client.model.FileNetIdentificator;
import cz.pwf.filenet.pwf_ecm_filenet_api_client.model.GetDocumentResponse;
import cz.pwf.filenet.pwf_ecm_filenet_api_client.model.UpdateDocumentBodyRequest;
import cz.notix.document.plugin.connector.dto.AttributeType;
import cz.notix.document.plugin.connector.dto.DMSDocumentId;
import cz.notix.document.plugin.connector.dto.DMSDocumentInfo;
import cz.notix.document.plugin.connector.dto.DMSDocumentNew;
import cz.notix.document.plugin.connector.dto.DMSDocumentUpdate;
import cz.notix.document.plugin.connector.dto.DmsAttribute;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Třída umožňující vzájemné mapování modelových tříd, vztahujících se k dokumentu.
 */
@Mapper(componentModel = "spring")
public abstract class DocumentMapper {

    @Value("${filenet.namespace}")
    private String namespace;

    @Mappings({
            @Mapping(target = "attributes", source = "attributes", qualifiedByName = "toDmsAttributeList"),
            @Mapping(target = "id", source = "source", qualifiedByName = "fromDocumentVersionToDMSDocumentId"),
            @Mapping(target = "sizeInBytes", source = "sizeInBytes", qualifiedByName = "mapStringToLong"),
    })
    public abstract DMSDocumentInfo toDMSDocumentInfo(DocumentMetadataResponse source);

    @Mappings({
            @Mapping(target = "id", source = "source", qualifiedByName = "fromFileNetIdentificatorToDMSDocumentId"),
            @Mapping(target = "filename", source = "doc.filename"),
            @Mapping(target = "mimetype", source = "doc.mimeType"),
            @Mapping(target = "attributes", source = "doc.metadata"),
    })
    public abstract DMSDocumentInfo toDMSDocumentInfo(FileNetIdentificator source, DMSDocumentNew doc);

    @Mappings({
            @Mapping(target = "id", source = "source", qualifiedByName = "fromFileNetIdentificatorToDMSDocumentId"),
            @Mapping(target = "filename", source = "doc.filename"),
            @Mapping(target = "mimetype", source = "doc.mimetype"),
            @Mapping(target = "attributes", source = "doc.attributes"),
    })
    public abstract DMSDocumentInfo toDMSDocumentInfo(FileNetIdentificator source, DMSDocumentUpdate doc);

    public abstract DMSDocumentId toDMSDocumentId(FileNetIdentificator source);

    @Mappings({
            @Mapping(target = "data", source = "source.data", qualifiedByName = "toBase64"),
            @Mapping(target = "attributes", source = "source.attributes", qualifiedByName = "toFileNetAttributesList"),
    })
    public abstract UpdateDocumentBodyRequest toUpdateDocumentBodyRequest(String namespace, DMSDocumentUpdate source);

    @Mappings({
            @Mapping(target = "data", source = "source.bytes", qualifiedByName = "toBase64"),
            @Mapping(target = "filename", source = "filename"),
            @Mapping(target = "title", source = "filename", qualifiedByName = "extractTitleFromFilename"),
            @Mapping(target = "mimetype", source = "source.mimeType"),
            @Mapping(target = "attributes", source = "source.metadata", qualifiedByName = "toFileNetAttributesList"),
    })
    public abstract CreateDocumentBodyRequest toCreateDocumentBodyRequest(DMSDocumentNew source);

    @Mappings({
            @Mapping(target = "id", source = "source.id", qualifiedByName = "fromFileNetIdentificatorToDMSDocumentId"),
            @Mapping(target = "filename", source = "source.fileName"),
            @Mapping(target = "mimetype", source = "source.mimeType"),
            @Mapping(target = "sizeInBytes", source = "source.content", qualifiedByName = "getFileSizeInBytesFromBase64String"),
            @Mapping(target = "attributes", expression = "java(java.util.Collections.emptyList())"),
    })
    public abstract DMSDocumentInfo toDMSDocumentInfo(GetDocumentResponse source);

    @Named("toBase64")
    protected String toBase64(byte[] data) {
        if (data.length == 0) {
            return null;
        }

        return Base64.getEncoder().encodeToString(data);
    }

    @Named("extractTitleFromFilename")
    protected String extractTitleFromFilename(String filename) {
        Objects.requireNonNull(filename, "Attribute 'filename' can not be null.");
        int indexOfFileExtension = filename.lastIndexOf(".");

        if (indexOfFileExtension > 0) {
            return filename.substring(0, indexOfFileExtension);
        }

        return filename;
    }

    @Named("getFileSizeInBytesFromBase64String")
    protected Long getFileSizeInBytesFromBase64String(String base64Data) {
        if (!StringUtils.hasText(base64Data)) {
            return null;
        }

        return (long) Base64.getDecoder().decode(base64Data).length;
    }

    @Named("toDmsAttributeList")
    protected List<DmsAttribute> toDmsAttributeList(List<FileNetAttributes> attributes) {
        if (!CollectionUtils.isEmpty(attributes)) {
            return attributes.stream()
                    .map(attribute -> new DmsAttribute(attribute.getName(), attribute.getValue(), AttributeType.valueOf(attribute.getType())))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Named("toFileNetAttributesList")
    protected List<FileNetAttributes> toFileNetAttributesList(List<DmsAttribute> attributes) {
        if (!CollectionUtils.isEmpty(attributes)) {
            return attributes.stream()
                    .map(attribute -> new FileNetAttributes()
                            .type(attribute.getType().name())
                            .name(attribute.getName())
                            .value(attribute.getValue())
                    )
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Named("fromFileNetIdentificatorToDMSDocumentId")
    protected DMSDocumentId fromFileNetIdentificatorToDMSDocumentId(FileNetIdentificator fileNetIdentificator) {
        if (Objects.nonNull(fileNetIdentificator) && StringUtils.hasText(fileNetIdentificator.getId())) {
            return new DMSDocumentId(namespace, fileNetIdentificator.getId(), fileNetIdentificator.getVersion());
        }

        return null;
    }

    @Named("fromDocumentVersionToDMSDocumentId")
    protected DMSDocumentId fromDocumentVersionToDMSDocumentId(DocumentMetadataResponse documentMetadataResponse) {
        if (Objects.nonNull(documentMetadataResponse) && Objects.nonNull(documentMetadataResponse.getId()) && StringUtils.hasText(documentMetadataResponse.getId().getId())) {
            return new DMSDocumentId(documentMetadataResponse.getNamespace(), documentMetadataResponse.getId().getId(), documentMetadataResponse.getId().getVersion(),
                    toDmsAttributeList(documentMetadataResponse.getAttributes()), documentMetadataResponse.getFilename(), documentMetadataResponse.getMimetype(),
                    Optional.ofNullable(documentMetadataResponse.getSizeInBytes()).map(Double::parseDouble).map(Double::longValue).orElse(null));
        }

        return null;
    }

    @Named("mapStringToLong")
    protected Long mapStringToLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return Double.valueOf(value).longValue();
    }

}