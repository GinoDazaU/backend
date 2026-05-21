package com.workpool.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpool.office.*;
import com.workpool.payment.PaymentRepository;
import com.workpool.reservation.reschedule.RescheduleRequestRepository;
import com.workpool.reservation.reschedule.RescheduleStatus;
import com.workpool.reservation.reschedule.RescheduleStatusRepository;
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

import java.math.BigDecimal;
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
class ReservationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private OfficeKindRepository officeKindRepository;
    @Autowired private OfficeRepository officeRepository;
    @Autowired private OfficePlanRepository officePlanRepository;
    @Autowired private OfficeBlockRepository officeBlockRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationStatusRepository reservationStatusRepository;
    @Autowired private RescheduleRequestRepository rescheduleRequestRepository;
    @Autowired private RescheduleStatusRepository rescheduleStatusRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private GuestRepository guestRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private JavaMailSender mailSender;

    private String clienteToken;
    private String cliente2Token;
    private String adminToken;
    private UUID officeId;
    private UUID planId;
    private UUID reservationId;
    private UUID confirmedReservationId;

    @BeforeAll
    void setup() throws Exception {
        rescheduleRequestRepository.deleteAll();
        officeBlockRepository.deleteAll();
        reservationRepository.deleteAll();
        paymentRepository.deleteAll();
        guestRepository.deleteAll();
        vehicleRepository.deleteAll();
        officePlanRepository.deleteAll();
        officeRepository.deleteAll();
        officeKindRepository.deleteAll();
        userRepository.deleteAll();

        // Permisos
        Permission verEspacios = savePerm("VER_ESPACIOS");
        Permission realizarReservas = savePerm("REALIZAR_RESERVAS");
        Permission cancelarPropias = savePerm("CANCELAR_RESERVAS_PROPIAS");
        Permission verTodasReservas = savePerm("VER_TODAS_RESERVAS");
        Permission gestionarReservas = savePerm("GESTIONAR_RESERVAS");
        Permission gestionarEspacios = savePerm("GESTIONAR_ESPACIOS");

        // Roles
        Role cliente = saveRole("CLIENTE", Set.of(verEspacios, realizarReservas, cancelarPropias));
        Role admin = saveRole("ADMINISTRADOR", Set.of(verEspacios, realizarReservas, cancelarPropias,
                verTodasReservas, gestionarReservas, gestionarEspacios));
        Role superAdmin = saveRole("ADMINISTRADOR_PRINCIPAL", Set.of(verEspacios, realizarReservas,
                cancelarPropias, verTodasReservas, gestionarReservas, gestionarEspacios));

        // Status de reserva
        saveStatus("PENDIENTE");
        saveStatus("CONFIRMADA");
        saveStatus("EXPIRADA");
        saveStatus("CANCELADA");
        saveStatus("REPROGRAMADA");

        // Status de reprogramación
        saveRescheduleStatus("PENDIENTE");
        saveRescheduleStatus("APROBADA");
        saveRescheduleStatus("RECHAZADA");

        // Usuarios
        createUser("cliente@test.com", cliente);
        createUser("cliente2@test.com", cliente);
        createUser("admin@test.com", admin);

        // Oficina y plan
        OfficeKind kind = new OfficeKind();
        kind.setKindName("SALA_REUNIONES");
        kind = officeKindRepository.save(kind);

        Office office = new Office();
        office.setName("Sala Alpha");
        office.setDescription("Sala de reuniones");
        office.setCapacity(10);
        office.setOfficeKind(kind);
        office.setConditions("Condiciones básicas");
        office = officeRepository.save(office);
        officeId = office.getId();

        OfficePlan plan = new OfficePlan();
        plan.setOfficeKind(kind);
        plan.setPricePerHour(new BigDecimal("25.00"));
        plan.setPlanDurationHours(4);
        plan = officePlanRepository.save(plan);
        planId = plan.getId();

        // Tokens
        clienteToken = loginAndGetToken("cliente@test.com");
        cliente2Token = loginAndGetToken("cliente2@test.com");
        adminToken = loginAndGetToken("admin@test.com");
    }

    @AfterAll
    void cleanup() {
        rescheduleRequestRepository.deleteAll();
        officeBlockRepository.deleteAll();
        reservationRepository.deleteAll();
        paymentRepository.deleteAll();
        guestRepository.deleteAll();
        vehicleRepository.deleteAll();
        officePlanRepository.deleteAll();
        officeRepository.deleteAll();
        officeKindRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ===== COTIZACIÓN =====

    @Test @Order(1)
    void quote_validParams_returnsQuote() throws Exception {
        String begin = futureDate(1, 9, 0);
        String end = futureDate(1, 13, 0);

        mockMvc.perform(get("/api/reservations/quote")
                        .param("planId", planId.toString())
                        .param("beginDate", begin)
                        .param("endDate", end)
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalHours").value(4))
                .andExpect(jsonPath("$.data.pricePerHour").value(25.00))
                .andExpect(jsonPath("$.data.totalPriceUsd").value(100.00))
                .andExpect(jsonPath("$.data.exchangeRate").value(3.75))
                .andExpect(jsonPath("$.data.totalPricePen").value(375.00));
    }

    @Test @Order(2)
    void quote_extraHours_calculatesCorrectly() throws Exception {
        String begin = futureDate(1, 9, 0);
        String end = futureDate(1, 15, 0); // 6 horas, plan es de 4

        mockMvc.perform(get("/api/reservations/quote")
                        .param("planId", planId.toString())
                        .param("beginDate", begin)
                        .param("endDate", end)
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalHours").value(6))
                .andExpect(jsonPath("$.data.extraHours").value(2))
                .andExpect(jsonPath("$.data.totalPriceUsd").value(150.00));
    }

    @Test @Order(3)
    void quote_beginAfterEnd_returns400() throws Exception {
        String begin = futureDate(1, 14, 0);
        String end = futureDate(1, 10, 0);

        mockMvc.perform(get("/api/reservations/quote")
                        .param("planId", planId.toString())
                        .param("beginDate", begin)
                        .param("endDate", end)
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isBadRequest());
    }

    // ===== CREAR PRE-RESERVA =====

    @Test @Order(10)
    void createPreReservation_happyPath_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 2, 9, 13, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.totalPriceUsd").value(100.00))
                .andExpect(jsonPath("$.data.totalPricePen").value(375.00))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        reservationId = UUID.fromString(json.at("/data/id").asText());
    }

    @Test @Order(11)
    void createPreReservation_withGuests_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationWithGuestsJson(officeId, planId, 3, 9, 13, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.guests", hasSize(2)))
                .andExpect(jsonPath("$.data.vehiclePlates", hasSize(1)))
                .andReturn();

        // cancelar para liberar horario
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID tempId = UUID.fromString(json.at("/data/id").asText());
        mockMvc.perform(post("/api/reservations/" + tempId + "/cancel")
                .header("Authorization", "Bearer " + cliente2Token));
    }

    @Test @Order(12)
    void createPreReservation_overlapping_returns409() throws Exception {
        // mismo horario que test 10
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 2, 9, 13, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El horario seleccionado no está disponible"));
    }

    @Test @Order(13)
    void createPreReservation_exceedsCapacity_returns400() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 4, 14, 18, 99)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("supera la capacidad")));
    }

    @Test @Order(14)
    void createPreReservation_pastDate_returns400() throws Exception {
        String body = String.format("""
        {
            "officeId": "%s", "officePlanId": "%s",
            "beginDate": "%s", "endDate": "%s",
            "personAmount": 3, "usesParking": false,
            "representativeName": "Juan", "representativeLastName": "Pérez",
            "representativeDni": "12345678"
        }
        """, officeId, planId,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString(),
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).plusHours(2).toString());

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(15)
    void createPreReservation_tooManyVehicles_returns400() throws Exception {
        String body = String.format("""
        {
            "officeId": "%s", "officePlanId": "%s",
            "beginDate": "%s", "endDate": "%s",
            "personAmount": 3, "usesParking": true,
            "representativeName": "Juan", "representativeLastName": "Pérez",
            "representativeDni": "12345678",
            "vehiclePlates": ["ABC-123", "DEF-456", "GHI-789"]
        }
        """, officeId, planId, futureDate(5, 9, 0), futureDate(5, 13, 0));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Máximo 2 vehículos por reserva"));
    }

    @Test @Order(16)
    void createPreReservation_vehiclesWithoutParking_returns400() throws Exception {
        String body = String.format("""
        {
            "officeId": "%s", "officePlanId": "%s",
            "beginDate": "%s", "endDate": "%s",
            "personAmount": 3, "usesParking": false,
            "representativeName": "Juan", "representativeLastName": "Pérez",
            "representativeDni": "12345678",
            "vehiclePlates": ["ABC-123"]
        }
        """, officeId, planId, futureDate(6, 9, 0), futureDate(6, 13, 0));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe indicar que usa estacionamiento para registrar vehículos"));
    }

    @Test @Order(17)
    void createPreReservation_blockedSlot_returns409() throws Exception {
        // crear bloqueo
        OfficeBlock block = new OfficeBlock();
        block.setOffice(officeRepository.findById(officeId).orElseThrow());
        block.setBlockedBy(userRepository.findByEmail("admin@test.com").orElseThrow());
        block.setBeginDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withHour(9).withMinute(0));
        block.setEndDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withHour(12).withMinute(0));
        block.setReason("Mantenimiento");
        officeBlockRepository.save(block);

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 7, 10, 11, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El horario seleccionado está bloqueado por mantenimiento"));
    }

    @Test @Order(18)
    void createPreReservation_wrongPlanForOffice_returns400() throws Exception {
        // crear otro kind y plan
        OfficeKind otherKind = new OfficeKind();
        otherKind.setKindName("COWORKING_TEMP");
        otherKind = officeKindRepository.save(otherKind);

        OfficePlan otherPlan = new OfficePlan();
        otherPlan.setOfficeKind(otherKind);
        otherPlan.setPricePerHour(new BigDecimal("10.00"));
        otherPlan.setPlanDurationHours(2);
        otherPlan = officePlanRepository.save(otherPlan);

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, otherPlan.getId(), 8, 9, 11, 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El plan seleccionado no corresponde al tipo de espacio"));
    }

    // ===== CONFIRMAR PAGO =====

    @Test @Order(20)
    void confirmPayment_happyPath_returns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/reservations/confirm-payment")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"reservationId": "%s"}
                        """, reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMADA"))
                .andReturn();

        confirmedReservationId = reservationId;
    }

    @Test @Order(21)
    void confirmPayment_alreadyConfirmed_returns400() throws Exception {
        mockMvc.perform(post("/api/reservations/confirm-payment")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"reservationId": "%s"}
                        """, confirmedReservationId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La reserva no está en estado pendiente"));
    }

    @Test @Order(22)
    void confirmPayment_otherUser_returns403() throws Exception {
        // crear pre-reserva con cliente2
        MvcResult r = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 9, 9, 13, 3)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        UUID otherResId = UUID.fromString(json.at("/data/id").asText());

        // cliente1 intenta confirmar reserva de cliente2
        mockMvc.perform(post("/api/reservations/confirm-payment")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"reservationId": "%s"}
                        """, otherResId)))
                .andExpect(status().isForbidden());

        // cleanup
        mockMvc.perform(post("/api/reservations/" + otherResId + "/cancel")
                .header("Authorization", "Bearer " + cliente2Token));
    }

    @Test @Order(23)
    void confirmPayment_expired_returns400() throws Exception {
        // crear y expirar manualmente
        MvcResult r = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 10, 9, 13, 3)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        UUID expResId = UUID.fromString(json.at("/data/id").asText());

        // forzar expiración
        Reservation res = reservationRepository.findById(expResId).orElseThrow();
        res.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        reservationRepository.save(res);

        mockMvc.perform(post("/api/reservations/confirm-payment")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"reservationId": "%s"}
                        """, expResId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("expirado")));
    }

    // ===== CANCELAR RESERVA =====

    @Test @Order(30)
    void cancelReservation_own_returns200() throws Exception {
        // crear y cancelar
        MvcResult r = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 11, 9, 13, 3)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        UUID cancelId = UUID.fromString(json.at("/data/id").asText());

        mockMvc.perform(post("/api/reservations/" + cancelId + "/cancel")
                        .header("Authorization", "Bearer " + cliente2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELADA"));
    }

    @Test @Order(31)
    void cancelReservation_otherUser_returns403() throws Exception {
        mockMvc.perform(post("/api/reservations/" + confirmedReservationId + "/cancel")
                        .header("Authorization", "Bearer " + cliente2Token))
                .andExpect(status().isForbidden());
    }

    @Test @Order(32)
    void cancelReservation_admin_returns200() throws Exception {
        // admin puede cancelar cualquier reserva
        mockMvc.perform(post("/api/reservations/admin/" + confirmedReservationId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELADA"));
    }

    // ===== PANEL DE USUARIO =====

    @Test @Order(40)
    void myReservations_returnsHistory() throws Exception {
        mockMvc.perform(get("/api/reservations/my")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test @Order(41)
    void myReservations_otherUserSeesOwnOnly() throws Exception {
        MvcResult r1 = mockMvc.perform(get("/api/reservations/my")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult r2 = mockMvc.perform(get("/api/reservations/my")
                        .header("Authorization", "Bearer " + cliente2Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data1 = objectMapper.readTree(r1.getResponse().getContentAsString()).at("/data");
        JsonNode data2 = objectMapper.readTree(r2.getResponse().getContentAsString()).at("/data");

        // cada usuario ve solo sus propias reservas
        Assertions.assertNotEquals(data1.size(), data2.size());
    }

    // ===== ADMIN: RESERVA MANUAL =====

    @Test @Order(50)
    void adminCreate_happyPath_returns201() throws Exception {
        mockMvc.perform(post("/api/reservations/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminReservationJson(officeId, 12, 9, 13)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.data.createdByAdmin").value(true))
                .andExpect(jsonPath("$.data.totalPriceUsd").value(0));
    }

    @Test @Order(51)
    void adminCreate_asCliente_returns403() throws Exception {
        mockMvc.perform(post("/api/reservations/admin")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminReservationJson(officeId, 13, 9, 13)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(52)
    void adminCreate_overlapping_returns409() throws Exception {
        // mismo horario que test 50
        mockMvc.perform(post("/api/reservations/admin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminReservationJson(officeId, 12, 10, 12)))
                .andExpect(status().isConflict());
    }

    // ===== ADMIN: VER TODAS =====

    @Test @Order(55)
    void adminGetAll_returns200() throws Exception {
        mockMvc.perform(get("/api/reservations/admin/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test @Order(56)
    void adminGetAll_asCliente_returns403() throws Exception {
        mockMvc.perform(get("/api/reservations/admin/all")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isForbidden());
    }

    // ===== REPROGRAMACIÓN =====

    @Test @Order(60)
    void reschedule_request_happyPath() throws Exception {
        // crear y confirmar una reserva para reprogramar
        MvcResult r = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 14, 9, 13, 3)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        UUID resId = UUID.fromString(json.at("/data/id").asText());

        // confirmar pago
        mockMvc.perform(post("/api/reservations/confirm-payment")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"reservationId\": \"%s\"}", resId)))
                .andExpect(status().isOk());

        // solicitar reprogramación
        mockMvc.perform(post("/api/reservations/reschedule")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"reservationId\": \"%s\"}", resId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDIENTE"));
    }

    @Test @Order(61)
    void reschedule_pendingReservation_returns400() throws Exception {
        // crear sin confirmar
        MvcResult r = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 15, 9, 13, 3)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        UUID pendingId = UUID.fromString(json.at("/data/id").asText());

        mockMvc.perform(post("/api/reservations/reschedule")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"reservationId\": \"%s\"}", pendingId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Solo se puede solicitar reprogramación de reservas confirmadas"));

        // cleanup
        mockMvc.perform(post("/api/reservations/" + pendingId + "/cancel")
                .header("Authorization", "Bearer " + cliente2Token));
    }

    @Test @Order(62)
    void reschedule_duplicateRequest_returns409() throws Exception {
        // buscar una reserva confirmada del cliente que ya tiene solicitud de reprogramación
        UUID confirmedWithReschedule = rescheduleRequestRepository.findAll().stream()
                .map(rr -> rr.getReservation().getId())
                .findFirst().orElse(null);

        if (confirmedWithReschedule == null) return;

        mockMvc.perform(post("/api/reservations/reschedule")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"reservationId\": \"%s\"}", confirmedWithReschedule)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Ya existe una solicitud de reprogramación para esta reserva"));
    }

    @Test @Order(63)
    void reschedule_otherUsersReservation_returns403() throws Exception {
        Reservation anyConfirmed = reservationRepository.findAll().stream()
                .filter(Reservation::isConfirmed)
                .findFirst().orElse(null);

        if (anyConfirmed == null) return;

        mockMvc.perform(post("/api/reservations/reschedule")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"reservationId\": \"%s\"}",
                                anyConfirmed.getId())))
                .andExpect(status().isForbidden());
    }

    @Test @Order(70)
    void reschedule_adminApprove_returns200() throws Exception {
        com.workpool.reservation.reschedule.RescheduleRequest req =
                rescheduleRequestRepository.findAll().stream()
                        .filter(r -> r.getRescheduleStatus().getRescheduleStatusName().equals("PENDIENTE"))
                        .findFirst().orElse(null);

        if (req == null) return;

        String newBegin = futureDate(20, 9, 0);
        String newEnd = futureDate(20, 13, 0);

        mockMvc.perform(post("/api/reservations/reschedule/admin/process")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {
                            "rescheduleRequestId": "%s",
                            "approved": true,
                            "newBeginDate": "%s",
                            "newEndDate": "%s"
                        }
                        """, req.getId(), newBegin, newEnd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APROBADA"));
    }

    @Test @Order(71)
    void reschedule_adminReject_returns200() throws Exception {
        // crear reserva, confirmar, solicitar reprogramación y rechazar
        MvcResult r = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 21, 9, 13, 3)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        UUID resId = UUID.fromString(json.at("/data/id").asText());

        mockMvc.perform(post("/api/reservations/confirm-payment")
                .header("Authorization", "Bearer " + cliente2Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"reservationId\": \"%s\"}", resId)));

        mockMvc.perform(post("/api/reservations/reschedule")
                .header("Authorization", "Bearer " + cliente2Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"reservationId\": \"%s\"}", resId)));

        com.workpool.reservation.reschedule.RescheduleRequest req =
                rescheduleRequestRepository.findAll().stream()
                        .filter(rr -> rr.getReservation().getId().equals(resId))
                        .findFirst().orElseThrow();

        mockMvc.perform(post("/api/reservations/reschedule/admin/process")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                        {"rescheduleRequestId": "%s", "approved": false}
                        """, req.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECHAZADA"));

        // verificar que la reserva sigue CONFIRMADA
        Reservation unchanged = reservationRepository.findById(resId).orElseThrow();
        Assertions.assertEquals("CONFIRMADA", unchanged.getReservationStatus().getStatusName());
    }

    @Test @Order(72)
    void reschedule_adminProcess_asCliente_returns403() throws Exception {
        mockMvc.perform(post("/api/reservations/reschedule/admin/process")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"rescheduleRequestId": "a0000000-0000-0000-0000-000000000001", "approved": false}
                        """))
                .andExpect(status().isForbidden());
    }

    // ===== SCHEDULER =====

    @Test @Order(80)
    void scheduler_expiresOldPreReservations() throws Exception {
        // crear reserva y forzar expiración
        MvcResult r = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + cliente2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId, 22, 9, 13, 3)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        UUID expId = UUID.fromString(json.at("/data/id").asText());

        Reservation res = reservationRepository.findById(expId).orElseThrow();
        res.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        reservationRepository.save(res);

        // ejecutar scheduler manualmente
        ReservationService service = new ReservationService(
                reservationRepository, reservationStatusRepository, guestRepository,
                vehicleRepository, officeRepository, officePlanRepository,
                officeBlockRepository, userRepository, null, null);

        // usamos el service inyectado directamente
        int expired = reservationService.expireOldPreReservations();
        Assertions.assertTrue(expired >= 1);

        Reservation after = reservationRepository.findById(expId).orElseThrow();
        Assertions.assertEquals("EXPIRADA", after.getReservationStatus().getStatusName());
    }

    @Autowired private ReservationService reservationService;

    // ===== HELPERS =====

    private Permission savePerm(String name) {
        return permissionRepository.findAll().stream()
                .filter(p -> p.getPermissionName().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    Permission p = new Permission();
                    p.setPermissionName(name);
                    p.setEnabled(true);
                    return permissionRepository.save(p);
                });
    }

    private Role saveRole(String name, Set<Permission> perms) {
        return roleRepository.findByRoleName(name)
                .map(existing -> {
                    existing.setPermissions(perms);
                    return roleRepository.save(existing);
                })
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setRoleName(name);
                    r.setEnabled(true);
                    r.setPermissions(perms);
                    return roleRepository.save(r);
                });
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

    private void saveStatus(String name) {
        if (reservationStatusRepository.findByStatusName(name).isEmpty()) {
            ReservationStatus s = new ReservationStatus();
            s.setStatusName(name);
            reservationStatusRepository.save(s);
        }
    }

    private void saveRescheduleStatus(String name) {
        if (rescheduleStatusRepository.findByRescheduleStatusName(name).isEmpty()) {
            RescheduleStatus s = new RescheduleStatus();
            s.setRescheduleStatusName(name);
            rescheduleStatusRepository.save(s);
        }
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

    private String futureDate(int daysAhead, int hour, int minute) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(daysAhead)
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0).toString();
    }

    private String reservationJson(UUID officeId, UUID planId, int daysAhead,
                                   int startHour, int endHour, int persons) {
        return String.format("""
        {
            "officeId": "%s",
            "officePlanId": "%s",
            "beginDate": "%s",
            "endDate": "%s",
            "personAmount": %d,
            "usesParking": false,
            "representativeName": "Juan",
            "representativeLastName": "Pérez",
            "representativeDni": "12345678"
        }
        """, officeId, planId, futureDate(daysAhead, startHour, 0), futureDate(daysAhead, endHour, 0), persons);
    }

    private String reservationWithGuestsJson(UUID officeId, UUID planId, int daysAhead,
                                             int startHour, int endHour, int persons) {
        return String.format("""
        {
            "officeId": "%s",
            "officePlanId": "%s",
            "beginDate": "%s",
            "endDate": "%s",
            "personAmount": %d,
            "usesParking": true,
            "representativeName": "Juan",
            "representativeLastName": "Pérez",
            "representativeDni": "12345678",
            "guests": [
                {"name": "Carlos", "lastName": "López", "dni": "87654321"},
                {"name": "María", "lastName": "García", "dni": "11223344"}
            ],
            "vehiclePlates": ["ABC-123"]
        }
        """, officeId, planId, futureDate(daysAhead, startHour, 0), futureDate(daysAhead, endHour, 0), persons);
    }

    private String adminReservationJson(UUID officeId, int daysAhead, int startHour, int endHour) {
        return String.format("""
        {
            "officeId": "%s",
            "beginDate": "%s",
            "endDate": "%s",
            "personAmount": 5,
            "representativeName": "Admin",
            "representativeLastName": "Test",
            "representativeDni": "99887766"
        }
        """, officeId, futureDate(daysAhead, startHour, 0), futureDate(daysAhead, endHour, 0));
    }
}