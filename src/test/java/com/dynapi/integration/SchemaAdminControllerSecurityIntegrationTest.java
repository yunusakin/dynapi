package com.dynapi.integration;

import com.dynapi.controller.SchemaAdminController;
import com.dynapi.DynapiApplication;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.exception.GlobalExceptionHandler;
import com.dynapi.repository.FieldDefinitionRepository;
import com.dynapi.repository.FieldGroupRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {
                DynapiApplication.class,
                SchemaAdminControllerSecurityIntegrationTest.SchemaAdminControllerTestConfig.class
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchemaAdminControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FieldDefinitionRepository fieldDefinitionRepository;

    @MockBean
    private FieldGroupRepository fieldGroupRepository;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        when(fieldDefinitionRepository.save(any(FieldDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldGroupRepository.save(any(FieldGroup.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldDefinitionRepository.findAll()).thenReturn(List.of());
        when(fieldGroupRepository.findAll()).thenReturn(List.of());
    }

    @ParameterizedTest
    @MethodSource("adminSchemaRequests")
    void schemaEndpoints_forbidWithoutAdminRole(String method, String path, String body) throws Exception {
        performRequest(method, path, body, null)
                .andExpect(status().isForbidden());

        performRequest(method, path, body, tokenWithRoles("USER"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("adminSchemaRequests")
    void schemaEndpoints_allowAdminRole(String method, String path, String body) throws Exception {
        performRequest(method, path, body, tokenWithRoles("ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private ResultActions performRequest(String method, String path, String body, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path)
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON);

        if (body != null) {
            builder.content(body);
        }

        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return mockMvc.perform(builder);
    }

    private String tokenWithRoles(String... roles) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        return Jwts.builder()
                .subject("integration-user")
                .claim("roles", List.of(roles))
                .signWith(key)
                .compact();
    }

    private static Stream<Arguments> adminSchemaRequests() {
        return Stream.of(
                Arguments.of("POST", "/api/admin/schema/field-definitions",
                        """
                                {
                                  "fieldName": "age",
                                  "type": "NUMBER",
                                  "required": true
                                }
                                """),
                Arguments.of("PUT", "/api/admin/schema/field-definitions/age",
                        """
                                {
                                  "type": "NUMBER",
                                  "required": false
                                }
                                """),
                Arguments.of("DELETE", "/api/admin/schema/field-definitions/age", null),
                Arguments.of("GET", "/api/admin/schema/field-definitions", null),
                Arguments.of("POST", "/api/admin/schema/field-groups",
                        """
                                {
                                  "name": "profile",
                                  "entity": "users",
                                  "fieldNames": ["age"]
                                }
                                """),
                Arguments.of("PUT", "/api/admin/schema/field-groups/profile",
                        """
                                {
                                  "entity": "users",
                                  "fieldNames": ["age", "name"]
                                }
                                """),
                Arguments.of("DELETE", "/api/admin/schema/field-groups/profile", null),
                Arguments.of("GET", "/api/admin/schema/field-groups", null)
        );
    }

    @TestConfiguration
    static class SchemaAdminControllerTestConfig {
        @Bean
        SchemaAdminController schemaAdminController(
                FieldDefinitionRepository fieldDefinitionRepository,
                FieldGroupRepository fieldGroupRepository) {
            return new SchemaAdminController(fieldDefinitionRepository, fieldGroupRepository);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler(MessageSource messageSource) {
            return new GlobalExceptionHandler(messageSource);
        }
    }
}
