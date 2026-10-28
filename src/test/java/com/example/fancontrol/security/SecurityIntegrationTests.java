package com.example.fancontrol.security;

import com.example.fancontrol.model.OperatorUser;
import com.example.fancontrol.model.Role;
import com.example.fancontrol.repository.DeviceRepository;
import com.example.fancontrol.repository.OperatorUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    private static final String USER_PASSWORD = "NormalUserPass123!";
    private static final String ADMIN_PASSWORD = "TestAdminPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperatorUserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<Long> usersToClean = new ArrayList<>();
    private final List<Integer> devicesToClean = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        devicesToClean.forEach(deviceRepository::deleteById);
        usersToClean.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
    }

    @Test
    void deviceApiRequiresJwtAndRegisteredUserCanOnlyRead() throws Exception {
        String name = uniqueName("read-only");
        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isUnauthorized());

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", name, "password", USER_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        long registeredUserId = objectMapper.readTree(registration.getResponse().getContentAsString())
                .get("id").asLong();
        usersToClean.add(registeredUserId);

        String token = login(name, USER_PASSWORD);
        mockMvc.perform(get("/api/devices").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/devices")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"unauthorized-device\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanManageDevicesAndUsersWithoutReceivingCredentials() throws Exception {
        String adminName = uniqueName("admin-test");
        OperatorUser admin = createUser(adminName, ADMIN_PASSWORD, Role.ADMIN);
        String adminToken = login(adminName, ADMIN_PASSWORD);

        String userName = uniqueName("user-test");
        OperatorUser user = createUser(userName, USER_PASSWORD, Role.USER);
        String userToken = login(userName, USER_PASSWORD);

        MvcResult usersResult = mockMvc.perform(get("/api/users")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        String usersJson = usersResult.getResponse().getContentAsString();
        assertTrue(usersJson.contains(userName));
        assertFalse(usersJson.contains("passwordHash"));
        assertFalse(usersJson.contains(USER_PASSWORD));

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + uniqueName("auth-device") + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdDevice = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int deviceId = createdDevice.get("id").asInt();
        devicesToClean.add(deviceId);

        mockMvc.perform(put("/api/devices/{id}", deviceId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + uniqueName("renamed-device") + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/{id}", user.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/devices").header("Authorization", bearer(userToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsAnIncorrectPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", "unknown-user", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid name or password."));
    }

    private OperatorUser createUser(String name, String password, Role role) {
        OperatorUser user = new OperatorUser();
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        OperatorUser saved = userRepository.saveAndFlush(user);
        usersToClean.add(saved.getId());
        return saved;
    }

    private String login(String name, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", name, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(String nameKey, String name, String passwordKey, String password) {
        return "{\"" + nameKey + "\":\"" + name + "\",\"" + passwordKey + "\":\"" + password + "\"}";
    }
}
