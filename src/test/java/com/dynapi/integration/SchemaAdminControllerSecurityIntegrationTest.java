package com.dynapi.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dynapi.DynapiApplication;
import com.dynapi.controller.SchemaAdminController;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.domain.model.SchemaLifecycleStatus;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.exception.GlobalExceptionHandler;
import com.dynapi.repository.FieldDefinitionRepository;
import com.dynapi.repository.FieldGroupRepository;
import com.dynapi.service.SchemaLifecycleService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = {
      DynapiApplication.class,
      SchemaAdminControllerSecurityIntegrationTest.SchemaAdminControllerTestConfig.class
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchemaAdminControllerSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FieldDefinitionRepository fieldDefinitionRepository;

  @MockitoBean private FieldGroupRepository fieldGroupRepository;

  @MockitoBean private SchemaLifecycleService schemaLifecycleService;

  @Value("${security.jwt.secret}")
  private String jwtSecret;

  @BeforeEach
  void setUp() {
    when(fieldDefinitionRepository.save(any(FieldDefinition.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(fieldGroupRepository.save(any(FieldGroup.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    FieldDefinition existingField = new FieldDefinition();
    existingField.setId("field-id");
    existingField.setFieldName("age");
    when(fieldDefinitionRepository.findTopByFieldNameOrderByVersionDesc(anyString()))
        .thenReturn(Optional.of(existingField));
    when(fieldDefinitionRepository.deleteByFieldName(anyString())).thenReturn(1L);

    FieldGroup existingGroup = new FieldGroup();
    existingGroup.setId("group-id");
    existingGroup.setName("profile");
    when(fieldGroupRepository.findTopByNameOrderByVersionDesc(anyString()))
        .thenReturn(Optional.of(existingGroup));
    when(fieldGroupRepository.deleteByName(anyString())).thenReturn(1L);

    when(fieldDefinitionRepository.findAll()).thenReturn(List.of());
    when(fieldGroupRepository.findAll()).thenReturn(List.of());
    when(schemaLifecycleService.publish(anyString()))
        .thenReturn(schemaVersion("users", 1, SchemaLifecycleStatus.PUBLISHED));
    when(schemaLifecycleService.deprecate(anyString()))
        .thenReturn(schemaVersion("users", 1, SchemaLifecycleStatus.DEPRECATED));
    when(schemaLifecycleService.rollback(anyString(), anyInt()))
        .thenReturn(schemaVersion("users", 2, SchemaLifecycleStatus.PUBLISHED));
    when(schemaLifecycleService.listVersions(anyString()))
        .thenReturn(List.of(schemaVersion("users", 1, SchemaLifecycleStatus.PUBLISHED)));
  }

  @ParameterizedTest
  @MethodSource("adminSchemaRequests")
  void schemaEndpoints_forbidWithoutAdminRole(String method, String path, String body)
      throws Exception {
    performRequest(method, path, body, null).andExpect(status().isForbidden());

    performRequest(method, path, body, tokenWithRoles("USER")).andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @MethodSource("adminSchemaRequests")
  void schemaEndpoints_allowAdminRole(String method, String path, String body) throws Exception {
    performRequest(method, path, body, tokenWithRoles("ADMIN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  private ResultActions performRequest(String method, String path, String body, String token)
      throws Exception {
    MockHttpServletRequestBuilder builder =
        request(HttpMethod.valueOf(method), path)
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
        Arguments.of(
            "POST",
            "/api/admin/schema/field-definitions",
            """
            {
              "fieldName": "age",
              "type": "NUMBER",
              "required": true
            }
            """),
        Arguments.of(
            "PUT",
            "/api/admin/schema/field-definitions/age",
            """
            {
              "type": "NUMBER",
              "required": false
            }
            """),
        Arguments.of("DELETE", "/api/admin/schema/field-definitions/age", null),
        Arguments.of("GET", "/api/admin/schema/field-definitions", null),
        Arguments.of(
            "POST",
            "/api/admin/schema/field-groups",
            """
            {
              "name": "profile",
              "entity": "users",
              "fieldNames": ["age"]
            }
            """),
        Arguments.of(
            "PUT",
            "/api/admin/schema/field-groups/profile",
            """
            {
              "entity": "users",
              "fieldNames": ["age", "name"]
            }
            """),
        Arguments.of("DELETE", "/api/admin/schema/field-groups/profile", null),
        Arguments.of("GET", "/api/admin/schema/field-groups", null),
        Arguments.of("POST", "/api/admin/schema/field-groups/profile/publish", null),
        Arguments.of("POST", "/api/admin/schema/entities/users/deprecate", null),
        Arguments.of("POST", "/api/admin/schema/entities/users/rollback/1", null),
        Arguments.of("GET", "/api/admin/schema/entities/users/versions", null));
  }

  private static SchemaVersion schemaVersion(
      String entity, int version, SchemaLifecycleStatus status) {
    SchemaVersion schemaVersion = new SchemaVersion();
    schemaVersion.setEntityName(entity);
    schemaVersion.setVersion(version);
    schemaVersion.setStatus(status);
    schemaVersion.setCreatedAt(LocalDateTime.now());
    return schemaVersion;
  }

  @TestConfiguration
  static class SchemaAdminControllerTestConfig {
    @Bean
    SchemaAdminController schemaAdminController(
        FieldDefinitionRepository fieldDefinitionRepository,
        FieldGroupRepository fieldGroupRepository,
        SchemaLifecycleService schemaLifecycleService) {
      return new SchemaAdminController(
          fieldDefinitionRepository, fieldGroupRepository, schemaLifecycleService);
    }

    @Bean
    GlobalExceptionHandler globalExceptionHandler(MessageSource messageSource) {
      return new GlobalExceptionHandler(messageSource);
    }
  }
}
