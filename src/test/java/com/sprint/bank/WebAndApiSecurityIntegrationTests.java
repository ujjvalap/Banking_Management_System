package com.sprint.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.bank.auth.dto.LoginRequest;
import com.sprint.bank.auth.dto.RegisterRequest;
import com.sprint.bank.customer.dto.CustomerProfileRequest;
import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.enums.Role;
import com.sprint.bank.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebAndApiSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @Test
    void customerProfileUpdateAndGlobalExceptionErrorContract() throws Exception {
        RegisterRequest register = new RegisterRequest(
                "Profile Tester",
                "profile.tester@example.com",
                "StrongPass#2026!",
                "+15551112233",
                "10 Oak Avenue",
                "PRFTY1234K"
        );

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regJson = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String customerToken = regJson.at("/data/accessToken").asText();
        assertFalse(customerToken.isBlank());
        assertEquals("", regJson.at("/data/user/password").asText());

        // Fetch profile
        mockMvc.perform(get("/api/customers/me")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Profile Tester"))
                .andExpect(jsonPath("$.data.pan").value("PRFTY1234K"));

        // Update profile
        CustomerProfileRequest updateReq = new CustomerProfileRequest(
                "Profile Updated",
                "+15559998877",
                "42 Elm Boulevard"
        );
        mockMvc.perform(put("/api/customers/me")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Profile Updated"))
                .andExpect(jsonPath("$.data.phone").value("+15559998877"))
                .andExpect(jsonPath("$.data.address").value("42 Elm Boulevard"));

        // Validation error payload contract
        CustomerProfileRequest invalidReq = new CustomerProfileRequest("", "bad", "");
        mockMvc.perform(put("/api/customers/me")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/customers/me"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.fullName").isNotEmpty());

        // Role boundary JSON ErrorResponse
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/admin/customers"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void thymeleafPagesSwaggerOpenApiAndRoleRoutesWorkEndToEnd() throws Exception {
        // Public Thymeleaf pages
        String[] publicPages = {"/", "/login", "/register", "/forgot-password", "/reset-password", "/verify-email"};
        for (String page : publicPages) {
            mockMvc.perform(get(page).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }

        // Unauthenticated protected HTML page redirects to login
        mockMvc.perform(get("/customer/dashboard").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));

        // OpenAPI JSON & Swagger UI
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/transfers")))
                .andExpect(content().string(containsString("bearerAuth")))
                .andExpect(content().string(not(containsString("passwordHash"))));

        mockMvc.perform(get("/swagger-ui/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());

        // Register customer and verify cookie-authenticated portal pages
        RegisterRequest register = new RegisterRequest(
                "Portal Customer",
                "portal.customer@example.com",
                "StrongPass#2026!",
                "+15552223344",
                "90 Pine Lane",
                "PRTCL5678L"
        );
        MvcResult custReg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie customerCookie = custReg.getResponse().getCookie("access_token");
        assertNotNull(customerCookie);

        String[] customerPages = {
                "/customer/dashboard",
                "/customer/accounts",
                "/customer/open-account",
                "/customer/deposit",
                "/customer/withdraw",
                "/customer/transfer",
                "/customer/statements",
                "/customer/profile"
        };
        for (String page : customerPages) {
            mockMvc.perform(get(page).cookie(customerCookie).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }

        // Customer blocked from admin portal UI
        mockMvc.perform(get("/admin/dashboard").cookie(customerCookie).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?forbidden=true"));

        // Seed admin and verify admin portal pages
        User admin = userRepository.findByEmail("webadmin@northstarbank.com")
                .orElseGet(() -> userRepository.save(new User(
                        "webadmin@northstarbank.com",
                        passwordEncoder.encode("AdminPass#2026!"),
                        Role.ADMIN,
                        true
                )));
        assertNotNull(admin.getId());

        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("webadmin@northstarbank.com", "AdminPass#2026!"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie adminCookie = adminLogin.getResponse().getCookie("access_token");
        assertNotNull(adminCookie);

        String[] adminPages = {"/admin/dashboard", "/admin/customers", "/admin/accounts", "/admin/transactions"};
        for (String page : adminPages) {
            mockMvc.perform(get(page).cookie(adminCookie).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }
    }
}
