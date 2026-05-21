package com.workpool.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpool.user.Role;
import com.workpool.user.RoleRepository;
import com.workpool.user.User;
import com.workpool.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean
    private JavaMailSender mailSender;

    private Role clienteRole;

    @BeforeAll
    void setup() {
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role cliente = new Role();
        cliente.setRoleName("CLIENTE");
        cliente.setEnabled(true);
        clienteRole = roleRepository.save(cliente);

        Role admin = new Role();
        admin.setRoleName("ADMINISTRADOR");
        admin.setEnabled(true);
        roleRepository.save(admin);

        Role superAdmin = new Role();
        superAdmin.setRoleName("ADMINISTRADOR_PRINCIPAL");
        superAdmin.setEnabled(true);
        roleRepository.save(superAdmin);
    }

    @AfterAll
    void cleanup() {
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    // ===== REGISTRO =====

    @Test
    @Order(1)
    void register_happyPath_returns201() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("test@workpool.com", "Test1234", "Test1234", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(2)
    void register_duplicateEmail_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("test@workpool.com", "Test1234", "Test1234", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El correo ya está registrado"));
    }

    @Test
    @Order(3)
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("correo-malo", "Test1234", "Test1234", true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void register_weakPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("weak@workpool.com", "abc", "abc", true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void register_passwordMismatch_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("mis@workpool.com", "Test1234", "Test9999", true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void register_termsNotAccepted_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("terms@workpool.com", "Test1234", "Test1234", false)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void register_invalidDni_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "firstName": "Juan",
                            "lastName": "Pérez",
                            "email": "dni@workpool.com",
                            "phoneNumber": "987654321",
                            "dni": "ABC12345",
                            "birthDate": "1995-06-15",
                            "password": "Test1234",
                            "confirmPassword": "Test1234",
                            "termsAccepted": true
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void register_invalidPhone_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "firstName": "Juan",
                            "lastName": "Pérez",
                            "email": "phone@workpool.com",
                            "phoneNumber": "12345",
                            "dni": "12345678",
                            "birthDate": "1995-06-15",
                            "password": "Test1234",
                            "confirmPassword": "Test1234",
                            "termsAccepted": true
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    // ===== VERIFICACIÓN EMAIL =====

    @Test
    @Order(10)
    void verifyEmail_validToken_returns200() throws Exception {
        User user = userRepository.findByEmail("test@workpool.com").orElseThrow();
        VerificationToken vt = verificationTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();

        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", vt.getToken().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User verified = userRepository.findByEmail("test@workpool.com").orElseThrow();
        Assertions.assertTrue(verified.isEnabled());
    }

    @Test
    @Order(11)
    void verifyEmail_invalidToken_returns400() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    void verifyEmail_expiredToken_returns400() throws Exception {
        User user = createVerifiedUser("expired@workpool.com");
        user.setEnabled(false);
        userRepository.save(user);

        VerificationToken vt = new VerificationToken();
        vt.setUser(user);
        vt.setToken(UUID.randomUUID());
        vt.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        verificationTokenRepository.save(vt);

        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", vt.getToken().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El token ha expirado"));
    }

    // ===== LOGIN =====

    @Test
    @Order(20)
    void login_happyPath_returnsToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email": "test@workpool.com", "password": "Test1234"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.email").value("test@workpool.com"))
                .andExpect(jsonPath("$.data.role").value("CLIENTE"))
                .andReturn();
    }

    @Test
    @Order(21)
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email": "test@workpool.com", "password": "WrongPass1"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
    }

    @Test
    @Order(22)
    void login_nonExistentEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email": "nadie@workpool.com", "password": "Test1234"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(23)
    void login_unverifiedUser_returnsError() throws Exception {
        // registrar sin verificar
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("noverify@workpool.com", "Test1234", "Test1234", true)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email": "noverify@workpool.com", "password": "Test1234"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    // ===== BLOQUEO POR INTENTOS =====

    @Test
    @Order(30)
    void login_5FailedAttempts_locksAccount() throws Exception {
        createVerifiedUser("lockme@workpool.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                    {"email": "lockme@workpool.com", "password": "WrongPass1"}
                    """));
        }

        // intento 6 con password correcta: debe estar bloqueado
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email": "lockme@workpool.com", "password": "Test1234"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Cuenta bloqueada temporalmente. Intenta en unos minutos."));
    }

    // ===== FORGOT PASSWORD =====

    @Test
    @Order(40)
    void forgotPassword_existingEmail_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email": "test@workpool.com"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(41)
    void forgotPassword_unknownEmail_returns200SameMessage() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email": "fantasma@workpool.com"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ===== RESET PASSWORD =====

    @Test
    @Order(50)
    void resetPassword_validToken_returns200() throws Exception {
        User user = userRepository.findByEmail("test@workpool.com").orElseThrow();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken(UUID.randomUUID());
        prt.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        passwordResetTokenRepository.save(prt);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"token": "%s", "password": "NewPass1234", "confirmPassword": "NewPass1234"}
                        """, prt.getToken().toString())))
                .andExpect(status().isOk());

        // login con nueva contraseña
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email": "test@workpool.com", "password": "NewPass1234"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @Order(51)
    void resetPassword_usedToken_returns400() throws Exception {
        User user = userRepository.findByEmail("test@workpool.com").orElseThrow();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken(UUID.randomUUID());
        prt.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        prt.setUsed(true);
        passwordResetTokenRepository.save(prt);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"token": "%s", "password": "Another1234", "confirmPassword": "Another1234"}
                        """, prt.getToken().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El token ya fue utilizado"));
    }

    @Test
    @Order(52)
    void resetPassword_expiredToken_returns400() throws Exception {
        User user = userRepository.findByEmail("test@workpool.com").orElseThrow();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken(UUID.randomUUID());
        prt.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        passwordResetTokenRepository.save(prt);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"token": "%s", "password": "Another1234", "confirmPassword": "Another1234"}
                        """, prt.getToken().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El token ha expirado"));
    }

    @Test
    @Order(53)
    void resetPassword_invalidToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"token": "%s", "password": "Another1234", "confirmPassword": "Another1234"}
                        """, UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    // ===== RUTAS PROTEGIDAS =====

    @Test
    @Order(60)
    void protectedRoute_withToken_returns200orNot404() throws Exception {
        String token = loginAndGetToken("test@workpool.com", "NewPass1234");

        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()); // llega al endpoint, no 401
    }

    @Test
    @Order(61)
    void protectedRoute_withoutToken_returns401or403() throws Exception {
        mockMvc.perform(get("/api/any-protected-route"))
                .andExpect(status().isForbidden());
    }

    // ===== HELPERS =====

    private String registerJson(String email, String pass, String confirm, boolean terms) {
        return String.format("""
        {
            "firstName": "Test",
            "lastName": "User",
            "email": "%s",
            "phoneNumber": "987654321",
            "dni": "12345678",
            "birthDate": "1995-06-15",
            "password": "%s",
            "confirmPassword": "%s",
            "termsAccepted": %s
        }
        """, email, pass, confirm, terms);
    }

    private User createVerifiedUser(String email) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPhoneNumber("987654321");
        user.setPasswordHash(passwordEncoder.encode("Test1234"));
        user.setRole(clienteRole);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"email": "%s", "password": "%s"}
                        """, email, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.at("/data/token").asText();
    }
}