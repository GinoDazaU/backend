package com.workpool.office;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpool.user.*;
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
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfficeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private OfficeKindRepository officeKindRepository;
    @Autowired private OfficeRepository officeRepository;
    @Autowired private OfficePlanRepository officePlanRepository;
    @Autowired private OfficeBlockRepository officeBlockRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    private String clienteToken;
    private String adminToken;
    private String superAdminToken;
    private UUID officeKindId;
    private UUID officeId;
    private UUID planId;
    private UUID blockId;

    @BeforeAll
    void setup() throws Exception {
        officeBlockRepository.deleteAll();
        officePlanRepository.deleteAll();
        officeRepository.deleteAll();
        officeKindRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();

        // Permisos
        Permission verEspacios = savePerm("VER_ESPACIOS");
        Permission realizarReservas = savePerm("REALIZAR_RESERVAS");
        Permission cancelarPropias = savePerm("CANCELAR_RESERVAS_PROPIAS");
        Permission verTodasReservas = savePerm("VER_TODAS_RESERVAS");
        Permission gestionarReservas = savePerm("GESTIONAR_RESERVAS");
        Permission verUsuarios = savePerm("VER_USUARIOS");
        Permission desactivarUsuarios = savePerm("DESACTIVAR_USUARIOS");
        Permission cambiarRol = savePerm("CAMBIAR_ROL_USUARIO");
        Permission invitarAdmins = savePerm("INVITAR_ADMINISTRADORES");
        Permission gestionarEspacios = savePerm("GESTIONAR_ESPACIOS");
        Permission editarPermisos = savePerm("EDITAR_PERMISOS_ROLES");
        Permission verReportes = savePerm("VER_REPORTES");

        // Roles
        Role cliente = saveRole("CLIENTE", Set.of(verEspacios, realizarReservas, cancelarPropias));
        Role admin = saveRole("ADMINISTRADOR", Set.of(
                verEspacios, realizarReservas, cancelarPropias, verTodasReservas,
                gestionarReservas, verUsuarios, desactivarUsuarios, invitarAdmins,
                gestionarEspacios, verReportes));
        Role superAdmin = saveRole("ADMINISTRADOR_PRINCIPAL", Set.of(
                verEspacios, realizarReservas, cancelarPropias, verTodasReservas,
                gestionarReservas, verUsuarios, desactivarUsuarios, cambiarRol,
                invitarAdmins, gestionarEspacios, editarPermisos, verReportes));

        // Usuarios
        createUser("cliente@test.com", cliente);
        createUser("admin@test.com", admin);
        createUser("super@test.com", superAdmin);

        // Tokens
        clienteToken = loginAndGetToken("cliente@test.com");
        adminToken = loginAndGetToken("admin@test.com");
        superAdminToken = loginAndGetToken("super@test.com");
    }

    @AfterAll
    void cleanup() {
        officeBlockRepository.deleteAll();
        officePlanRepository.deleteAll();
        officeRepository.deleteAll();
        officeKindRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    // ===== OFFICE KINDS =====

    @Test @Order(1)
    void createKind_asAdmin_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/offices/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"kindName": "SALA_REUNIONES"}
                        """))
                .andReturn();

        // primero creamos el kind por separado
        result = mockMvc.perform(post("/api/offices/kinds")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"kindName": "SALA_REUNIONES"}
                        """))
                .andReturn();

        // kinds no tiene endpoint POST público, lo crearemos directo
        OfficeKind kind = new OfficeKind();
        kind.setKindName("SALA_REUNIONES");
        kind = officeKindRepository.save(kind);
        officeKindId = kind.getId();

        OfficeKind kind2 = new OfficeKind();
        kind2.setKindName("OFICINA_PRIVADA");
        officeKindRepository.save(kind2);

        Assertions.assertNotNull(officeKindId);
    }

    @Test @Order(2)
    void getKinds_asAnyUser_returnsAll() throws Exception {
        mockMvc.perform(get("/api/offices/kinds")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));
    }

    // ===== OFFICES - ADMIN CRUD =====

    @Test @Order(10)
    void createOffice_asAdmin_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/offices/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Sala Alpha", officeKindId, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Sala Alpha"))
                .andExpect(jsonPath("$.data.capacity").value(10))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        officeId = UUID.fromString(json.at("/data/id").asText());
    }

    @Test @Order(11)
    void createOffice_asCliente_returns403() throws Exception {
        mockMvc.perform(post("/api/offices/admin")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Sala Prohibida", officeKindId, 5)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(12)
    void createOffice_withoutToken_returns403() throws Exception {
        mockMvc.perform(post("/api/offices/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Sala Anon", officeKindId, 5)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(13)
    void createOffice_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/offices/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {
                            "name": "",
                            "description": "Desc",
                            "capacity": 5,
                            "officeKindId": "%s",
                            "conditions": "Condiciones"
                        }
                        """, officeKindId)))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(14)
    void createOffice_invalidKind_returns404() throws Exception {
        mockMvc.perform(post("/api/offices/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Sala Fantasma", UUID.randomUUID(), 5)))
                .andExpect(status().isNotFound());
    }

    @Test @Order(15)
    void createOffice_zeroCapacity_returns400() throws Exception {
        mockMvc.perform(post("/api/offices/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {
                            "name": "Sala Zero",
                            "description": "Desc",
                            "capacity": 0,
                            "officeKindId": "%s",
                            "conditions": "Condiciones"
                        }
                        """, officeKindId)))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(20)
    void updateOffice_asAdmin_returns200() throws Exception {
        mockMvc.perform(put("/api/offices/admin/" + officeId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Sala Alpha Renovada", officeKindId, 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Sala Alpha Renovada"))
                .andExpect(jsonPath("$.data.capacity").value(12));
    }

    @Test @Order(21)
    void updateOffice_asCliente_returns403() throws Exception {
        mockMvc.perform(put("/api/offices/admin/" + officeId)
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Hack", officeKindId, 5)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(22)
    void updateOffice_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/offices/admin/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Fantasma", officeKindId, 5)))
                .andExpect(status().isNotFound());
    }

    @Test @Order(30)
    void getOfficeById_asCliente_returns200() throws Exception {
        mockMvc.perform(get("/api/offices/" + officeId)
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(officeId.toString()));
    }

    @Test @Order(31)
    void getOfficeById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/offices/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isNotFound());
    }

    // ===== OFFICES - CATÁLOGO PÚBLICO =====

    @Test @Order(32)
    void getAvailable_withFilters_returns200() throws Exception {
        mockMvc.perform(get("/api/offices/available")
                        .param("kindId", officeKindId.toString())
                        .param("minCapacity", "5")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test @Order(33)
    void getAvailable_highCapacity_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/offices/available")
                        .param("kindId", officeKindId.toString())
                        .param("minCapacity", "999")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test @Order(34)
    void getAllOffices_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/offices/admin/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test @Order(35)
    void getAllOffices_asCliente_returns403() throws Exception {
        mockMvc.perform(get("/api/offices/admin/all")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isForbidden());
    }

    // ===== OFFICE PLANS =====

    @Test @Order(40)
    void createPlan_asAdmin_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/offices/admin/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(officeKindId, "25.00", 4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.pricePerHour").value(25.00))
                .andExpect(jsonPath("$.data.planDurationHours").value(4))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        planId = UUID.fromString(json.at("/data/id").asText());
    }

    @Test @Order(41)
    void createPlan_asCliente_returns403() throws Exception {
        mockMvc.perform(post("/api/offices/admin/plans")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(officeKindId, "10.00", 2)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(42)
    void createPlan_invalidKind_returns404() throws Exception {
        mockMvc.perform(post("/api/offices/admin/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(UUID.randomUUID(), "10.00", 2)))
                .andExpect(status().isNotFound());
    }

    @Test @Order(43)
    void createPlan_zeroPrice_returns400() throws Exception {
        mockMvc.perform(post("/api/offices/admin/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(officeKindId, "0.00", 2)))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(44)
    void createPlan_zeroDuration_returns400() throws Exception {
        mockMvc.perform(post("/api/offices/admin/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"officeKindId": "%s", "pricePerHour": 10.00, "planDurationHours": 0}
                        """, officeKindId)))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(45)
    void getPlansByKind_asCliente_returns200() throws Exception {
        mockMvc.perform(get("/api/offices/plans")
                        .param("kindId", officeKindId.toString())
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test @Order(46)
    void getPlansByKind_unknownKind_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/offices/plans")
                        .param("kindId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test @Order(47)
    void updatePlan_asAdmin_returns200() throws Exception {
        mockMvc.perform(put("/api/offices/admin/plans/" + planId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(officeKindId, "30.00", 6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricePerHour").value(30.00))
                .andExpect(jsonPath("$.data.planDurationHours").value(6));
    }

    @Test @Order(48)
    void getAllPlans_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/offices/admin/plans")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test @Order(49)
    void disablePlan_asAdmin_returns200() throws Exception {
        // crear un plan para desactivar
        MvcResult result = mockMvc.perform(post("/api/offices/admin/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(officeKindId, "5.00", 1)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID tempPlanId = UUID.fromString(json.at("/data/id").asText());

        mockMvc.perform(patch("/api/offices/admin/plans/" + tempPlanId + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // verificar que no aparece en planes activos
        mockMvc.perform(get("/api/offices/plans")
                        .param("kindId", officeKindId.toString())
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + tempPlanId + "')]").doesNotExist());
    }

    // ===== OFFICE BLOCKS =====

    @Test @Order(50)
    void createBlock_asAdmin_returns201() throws Exception {
        String begin = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withHour(9).withMinute(0).toString();
        String end = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withHour(12).withMinute(0).toString();

        MvcResult result = mockMvc.perform(post("/api/offices/admin/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(officeId, begin, end, "Mantenimiento programado")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reason").value("Mantenimiento programado"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        blockId = UUID.fromString(json.at("/data/id").asText());
    }

    @Test @Order(51)
    void createBlock_asCliente_returns403() throws Exception {
        String begin = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withHour(9).withMinute(0).toString();
        String end = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withHour(12).withMinute(0).toString();

        mockMvc.perform(post("/api/offices/admin/blocks")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(officeId, begin, end, "Hack")))
                .andExpect(status().isForbidden());
    }

    @Test @Order(52)
    void createBlock_beginAfterEnd_returns400() throws Exception {
        String begin = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).withHour(14).withMinute(0).toString();
        String end = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).withHour(10).withMinute(0).toString();

        mockMvc.perform(post("/api/offices/admin/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(officeId, begin, end, "Rango invertido")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La hora de inicio debe ser anterior a la hora de fin"));
    }

    @Test @Order(53)
    void createBlock_sameBeginEnd_returns400() throws Exception {
        String same = OffsetDateTime.now(ZoneOffset.UTC).plusDays(4).withHour(10).withMinute(0).toString();

        mockMvc.perform(post("/api/offices/admin/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(officeId, same, same, "Mismo rango")))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(54)
    void createBlock_pastDate_returns400() throws Exception {
        String begin = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).withHour(9).withMinute(0).toString();
        String end = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).withHour(12).withMinute(0).toString();

        mockMvc.perform(post("/api/offices/admin/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(officeId, begin, end, "Pasado")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No se pueden crear bloqueos en fechas pasadas"));
    }

    @Test @Order(55)
    void createBlock_overlapping_returns409() throws Exception {
        // mismo rango que el bloqueo del test 50
        String begin = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withHour(10).withMinute(0).toString();
        String end = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withHour(11).withMinute(0).toString();

        mockMvc.perform(post("/api/offices/admin/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(officeId, begin, end, "Solapado")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un bloqueo en el rango horario seleccionado"));
    }

    @Test @Order(56)
    void createBlock_unknownOffice_returns404() throws Exception {
        String begin = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5).withHour(9).withMinute(0).toString();
        String end = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5).withHour(12).withMinute(0).toString();

        mockMvc.perform(post("/api/offices/admin/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(UUID.randomUUID(), begin, end, "Fantasma")))
                .andExpect(status().isNotFound());
    }

    @Test @Order(57)
    void getBlocks_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/offices/admin/blocks/" + officeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test @Order(58)
    void cancelBlock_asAdmin_returns200() throws Exception {
        mockMvc.perform(patch("/api/offices/admin/blocks/" + blockId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test @Order(59)
    void cancelBlock_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/offices/admin/blocks/" + UUID.randomUUID() + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ===== DISABLE / DELETE OFFICES =====

    @Test @Order(60)
    void disableOffice_asAdmin_returns200() throws Exception {
        // crear otra sala para desactivar
        MvcResult result = mockMvc.perform(post("/api/offices/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Sala Temporal", officeKindId, 4)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID tempId = UUID.fromString(json.at("/data/id").asText());

        mockMvc.perform(patch("/api/offices/admin/" + tempId + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test @Order(61)
    void disableOffice_asCliente_returns403() throws Exception {
        mockMvc.perform(patch("/api/offices/admin/" + officeId + "/disable")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isForbidden());
    }

    @Test @Order(62)
    void deleteOffice_asAdmin_returns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/offices/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Sala Para Borrar", officeKindId, 3)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID deleteId = UUID.fromString(json.at("/data/id").asText());

        mockMvc.perform(delete("/api/offices/admin/" + deleteId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // verificar que ya no existe
        mockMvc.perform(get("/api/offices/" + deleteId)
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isNotFound());
    }

    @Test @Order(63)
    void deleteOffice_asCliente_returns403() throws Exception {
        mockMvc.perform(delete("/api/offices/admin/" + officeId)
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isForbidden());
    }

    // ===== PERMISOS DINÁMICOS =====

    @Test @Order(70)
    void getRoles_asSuperAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/roles")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test @Order(71)
    void getRoles_asAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test @Order(72)
    void getRoles_asCliente_returns403() throws Exception {
        mockMvc.perform(get("/api/roles")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isForbidden());
    }

    @Test @Order(73)
    void updatePermissions_asSuperAdmin_returns200() throws Exception {
        Role admin = roleRepository.findByRoleName("ADMINISTRADOR").orElseThrow();
        Permission verEspacios = permissionRepository.findAll().stream()
                .filter(p -> p.getPermissionName().equals("VER_ESPACIOS"))
                .findFirst().orElseThrow();

        // reducir admin a solo VER_ESPACIOS
        mockMvc.perform(put("/api/roles/" + admin.getId() + "/permissions")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"permissionIds": ["%s"]}
                        """, verEspacios.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", hasSize(1)));
    }

    @Test @Order(74)
    void adminLosesAccess_afterPermissionRemoved() throws Exception {
        // re-login admin para obtener token con nuevos permisos
        adminToken = loginAndGetToken("admin@test.com");

        // admin ya no tiene GESTIONAR_ESPACIOS
        mockMvc.perform(post("/api/offices/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeJson("Sala Denegada", officeKindId, 5)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(75)
    void updatePermissions_onSuperAdmin_returns403() throws Exception {
        Role superAdminRole = roleRepository.findByRoleName("ADMINISTRADOR_PRINCIPAL").orElseThrow();
        Permission verEspacios = permissionRepository.findAll().stream()
                .filter(p -> p.getPermissionName().equals("VER_ESPACIOS"))
                .findFirst().orElseThrow();

        mockMvc.perform(put("/api/roles/" + superAdminRole.getId() + "/permissions")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"permissionIds": ["%s"]}
                        """, verEspacios.getId())))
                .andExpect(status().isForbidden());
    }

    @Test @Order(76)
    void updatePermissions_restoreAdmin() throws Exception {
        Role admin = roleRepository.findByRoleName("ADMINISTRADOR").orElseThrow();
        // restaurar todos los permisos originales del admin
        Set<String> adminPerms = Set.of("VER_ESPACIOS", "REALIZAR_RESERVAS",
                "CANCELAR_RESERVAS_PROPIAS", "VER_TODAS_RESERVAS", "GESTIONAR_RESERVAS",
                "VER_USUARIOS", "DESACTIVAR_USUARIOS", "INVITAR_ADMINISTRADORES",
                "GESTIONAR_ESPACIOS", "VER_REPORTES");

        Set<UUID> permIds = new HashSet<>();
        permissionRepository.findAll().stream()
                .filter(p -> adminPerms.contains(p.getPermissionName()))
                .forEach(p -> permIds.add(p.getId()));

        String idsJson = permIds.stream()
                .map(id -> "\"" + id + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        mockMvc.perform(put("/api/roles/" + admin.getId() + "/permissions")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\": [" + idsJson + "]}"))
                .andExpect(status().isOk());

        adminToken = loginAndGetToken("admin@test.com");
    }

    @Test @Order(77)
    void updatePermissions_invalidPermissionId_returns400() throws Exception {
        Role admin = roleRepository.findByRoleName("ADMINISTRADOR").orElseThrow();

        mockMvc.perform(put("/api/roles/" + admin.getId() + "/permissions")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"permissionIds": ["%s"]}
                        """, UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Uno o más permisos no existen"));
    }

    // ===== HELPERS =====

    private Permission savePerm(String name) {
        Permission p = new Permission();
        p.setPermissionName(name);
        p.setEnabled(true);
        return permissionRepository.save(p);
    }

    private Role saveRole(String name, Set<Permission> perms) {
        Role r = new Role();
        r.setRoleName(name);
        r.setEnabled(true);
        r.setPermissions(perms);
        return roleRepository.save(r);
    }

    private void createUser(String email, Role role) {
        User u = new User();
        u.setFirstName("Test");
        u.setLastName("User");
        u.setEmail(email);
        u.setPhoneNumber("987654321");
        u.setPasswordHash(passwordEncoder.encode("Test1234"));
        u.setRole(role);
        u.setEnabled(true);
        userRepository.save(u);
    }

    private String loginAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"email": "%s", "password": "Test1234"}
                        """, email)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.at("/data/token").asText();
    }

    private String officeJson(String name, UUID kindId, int capacity) {
        return String.format("""
        {
            "name": "%s",
            "description": "Descripción de prueba",
            "capacity": %d,
            "officeKindId": "%s",
            "conditions": "Condiciones básicas de uso"
        }
        """, name, capacity, kindId);
    }

    private String planJson(UUID kindId, String price, int hours) {
        return String.format("""
        {"officeKindId": "%s", "pricePerHour": %s, "planDurationHours": %d}
        """, kindId, price, hours);
    }

    private String blockJson(UUID officeId, String begin, String end, String reason) {
        return String.format("""
        {"officeId": "%s", "beginDate": "%s", "endDate": "%s", "reason": "%s"}
        """, officeId, begin, end, reason);
    }
}