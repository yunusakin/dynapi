package com.dynapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.ArrayList;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String API_VERSION_PARAM = "api-version";
    private static final String VERSION_1 = "1";
    private static final String EXAMPLE_VALIDATION_ERROR = "ValidationError";
    private static final String EXAMPLE_UNSUPPORTED_VERSION = "UnsupportedApiVersion";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Dynapi API")
                                .version("1.0")
                                .description(
                                        """
                                                Dynamic API backend with runtime schema, validation, and flexible querying.

                                                API versioning:
                                                - Query parameter based: `api-version`
                                                - Supported version: `1`
                                                - Default fallback when omitted: `1`
                                                - Unsupported versions return HTTP 400.

                                                Validation behavior:
                                                - Request DTO constraint failures return RFC 9457 ProblemDetail payloads
                                                - Field-level validation errors are exposed under `errors`.
                                                """))
                .components(
                        new Components()
                                .addExamples(
                                        EXAMPLE_VALIDATION_ERROR,
                                        new Example()
                                                .value(
                                                        Map.of(
                                                                "type",
                                                                "about:blank",
                                                                "title",
                                                                "Validation Error",
                                                                "status",
                                                                400,
                                                                "detail",
                                                                "Validation failed",
                                                                "errors",
                                                                Map.of("field", "must not be blank"))))
                                .addExamples(
                                        EXAMPLE_UNSUPPORTED_VERSION,
                                        new Example()
                                                .value(
                                                        Map.of(
                                                                "type",
                                                                "about:blank",
                                                                "title",
                                                                "Bad Request",
                                                                "status",
                                                                400,
                                                                "detail",
                                                                "Invalid API version '2'."))));
    }

    @Bean
    public OperationCustomizer apiVersionQueryParameterCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() == null) {
                operation.setParameters(new ArrayList<>());
            }

            boolean hasVersionParam =
                    operation.getParameters().stream()
                            .anyMatch(parameter -> API_VERSION_PARAM.equals(parameter.getName()));
            if (!hasVersionParam) {
                operation.addParametersItem(apiVersionParameter());
            }
            return operation;
        };
    }

    @Bean
    public OpenApiCustomizer badRequestResponseCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi
                    .getPaths()
                    .forEach(
                            (path, pathItem) ->
                                    pathItem
                                            .readOperations()
                                            .forEach(
                                                    operation -> {
                                                        ApiResponses responses = operation.getResponses();
                                                        if (responses != null && responses.containsKey("400")) {
                                                            return;
                                                        }

                                                        if (responses == null) {
                                                            responses = new ApiResponses();
                                                            operation.setResponses(responses);
                                                        }
                                                        responses.addApiResponse("400", badRequestResponse());
                                                    }));
        };
    }

    private Parameter apiVersionParameter() {
        StringSchema versionSchema = new StringSchema()._default(VERSION_1);
        versionSchema.addEnumItemObject(VERSION_1);
        return new Parameter()
                .name(API_VERSION_PARAM)
                .in("query")
                .required(false)
                .description("API version. Supported value: `1`.")
                .schema(versionSchema);
    }

    private io.swagger.v3.oas.models.responses.ApiResponse badRequestResponse() {
        return new io.swagger.v3.oas.models.responses.ApiResponse()
                .description("Validation, guardrail, or API version error.")
                .content(
                        new Content()
                                .addMediaType(
                                        "application/problem+json",
                                        new MediaType()
                                                .addExamples(
                                                        "validationError",
                                                        new Example().$ref("#/components/examples/" + EXAMPLE_VALIDATION_ERROR))
                                                .addExamples(
                                                        "unsupportedApiVersion",
                                                        new Example()
                                                                .$ref("#/components/examples/" + EXAMPLE_UNSUPPORTED_VERSION))));
    }
}
