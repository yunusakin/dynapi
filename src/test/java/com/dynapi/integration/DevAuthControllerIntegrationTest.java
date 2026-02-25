package com.dynapi.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dynapi.security.JwtTokenService;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void issueToken_withDefaults_returnsUsableAdminJwt() throws Exception {
        MvcResult result =
                mockMvc
                        .perform(
                                post("/api/dev/auth/token")
                                        .contextPath("/api")
                                        .contentType(APPLICATION_JSON)
                                        .content("{}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.message").value("Dev token issued"))
                        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                        .andExpect(jsonPath("$.data.subject").value("dev-admin"))
                        .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                        .andReturn();

        String token = readJsonString(result, "$.data.token");
        assertFalse(token.isBlank());

        Optional<Authentication> auth = jwtTokenService.parseAuthentication(token);
        assertTrue(auth.isPresent());
        assertTrue(
                auth.get().getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));

        mockMvc
                .perform(
                        get("/api/admin/schema/field-definitions")
                                .contextPath("/api")
                                .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void issueToken_withCustomPayload_preservesSubjectAndRoles() throws Exception {
        MvcResult result =
                mockMvc
                        .perform(
                                post("/api/dev/auth/token")
                                        .contextPath("/api")
                                        .contentType(APPLICATION_JSON)
                                        .content(
                                                """
                                                        {
                                                          "subject": "local-user",
                                                          "roles": ["ROLE_ADMIN", "USER"],
                                                          "ttlSeconds": 120
                                                        }
                                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.subject").value("local-user"))
                        .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                        .andExpect(jsonPath("$.data.roles[1]").value("USER"))
                        .andReturn();

        String token = readJsonString(result, "$.data.token");
        Optional<Authentication> auth = jwtTokenService.parseAuthentication(token);
        assertTrue(auth.isPresent());
        assertTrue(
                auth.get().getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority())));
    }

    @Test
    void issueToken_rejectsTtlAboveConfiguredMax() throws Exception {
        mockMvc
                .perform(
                        post("/api/dev/auth/token")
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "subject": "qa",
                                                  "roles": ["ADMIN"],
                                                  "ttlSeconds": 999999
                                                }
                                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request"));
    }

    private String readJsonString(MvcResult result, String jsonPath) throws Exception {
        return new JsonPathExpectationsHelper(jsonPath)
                .evaluateJsonPath(result.getResponse().getContentAsString(), String.class);
    }
}
