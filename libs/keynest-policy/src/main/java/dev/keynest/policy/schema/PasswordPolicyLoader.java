package dev.keynest.policy.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads and validates Keynest password-policy documents (Zod-like schema contract for Java).
 */
public final class PasswordPolicyLoader {

    public static final String DEFAULT_POLICY_RESOURCE = "/policies/keynest.default.v1.json";
    public static final String SCHEMA_RESOURCE = "/schemas/password-policy.schema.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PasswordPolicyLoader() {
    }

    public static PasswordPolicyDocument loadDefault() {
        return loadClasspath(DEFAULT_POLICY_RESOURCE);
    }

    public static PasswordPolicyDocument loadClasspath(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        try (InputStream in = PasswordPolicyLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new PolicySchemaException("policy resource not found: " + resourcePath);
            }
            return load(in);
        } catch (IOException e) {
            throw new PolicySchemaException("failed to read policy resource: " + resourcePath, e);
        }
    }

    public static PasswordPolicyDocument load(InputStream json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonNode node = MAPPER.readTree(json);
            validateAgainstSchema(node);
            PasswordPolicyDocument document = MAPPER.treeToValue(node, PasswordPolicyDocument.class);
            if (document.rules() == null) {
                throw new PolicySchemaException("policy document missing rules");
            }
            if (document.rules().maxLength < document.rules().minLength) {
                throw new PolicySchemaException("rules.maxLength must be >= rules.minLength");
            }
            // Touch conversion early so invalid builder constraints fail at load time
            document.toRuntimePolicy();
            return document;
        } catch (PolicySchemaException e) {
            throw e;
        } catch (Exception e) {
            throw new PolicySchemaException("invalid password policy document", e);
        }
    }

    public static void validateAgainstSchema(JsonNode node) {
        JsonSchema schema = schema();
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            String detail = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
            throw new PolicySchemaException("policy document failed JSON Schema validation: " + detail);
        }
    }

    private static JsonSchema schema() {
        try (InputStream in = PasswordPolicyLoader.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new PolicySchemaException("schema resource not found: " + SCHEMA_RESOURCE);
            }
            JsonNode schemaNode = MAPPER.readTree(in);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            return factory.getSchema(schemaNode);
        } catch (IOException e) {
            throw new PolicySchemaException("failed to load password-policy JSON Schema", e);
        }
    }
}
