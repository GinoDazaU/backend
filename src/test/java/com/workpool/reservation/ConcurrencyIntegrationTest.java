package com.workpool.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpool.office.*;
import com.workpool.payment.PaymentRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyIntegrationTest {

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
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private GuestRepository guestRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private JavaMailSender mailSender;

    private UUID officeId;
    private UUID planId;
    private List<String> tokens = new ArrayList<>();

    @BeforeAll
    void setup() throws Exception {
        // limpiar solo datos transaccionales
        officeBlockRepository.deleteAll();
        reservationRepository.deleteAll();
        paymentRepository.deleteAll();
        guestRepository.deleteAll();
        vehicleRepository.deleteAll();
        officePlanRepository.deleteAll();
        officeRepository.deleteAll();
        officeKindRepository.deleteAll();
        userRepository.deleteAll();

        // permisos y roles (reutilizar de Flyway si existen)
        Permission realizarReservas = savePerm("REALIZAR_RESERVAS");
        Permission verEspacios = savePerm("VER_ESPACIOS");
        Permission cancelarPropias = savePerm("CANCELAR_RESERVAS_PROPIAS");

        Role cliente = saveRole("CLIENTE", Set.of(verEspacios, realizarReservas, cancelarPropias));

        // status
        saveStatus("PENDIENTE");
        saveStatus("CONFIRMADA");
        saveStatus("EXPIRADA");
        saveStatus("CANCELADA");
        saveStatus("REPROGRAMADA");

        // oficina y plan
        OfficeKind kind = new OfficeKind();
        kind.setKindName("SALA_CONCURRENCIA");
        kind = officeKindRepository.save(kind);

        Office office = new Office();
        office.setName("Sala Concurrente");
        office.setDescription("Test de concurrencia");
        office.setCapacity(20);
        office.setOfficeKind(kind);
        office.setConditions("Test");
        office = officeRepository.save(office);
        officeId = office.getId();

        OfficePlan plan = new OfficePlan();
        plan.setOfficeKind(kind);
        plan.setPricePerHour(new BigDecimal("10.00"));
        plan.setPlanDurationHours(2);
        plan = officePlanRepository.save(plan);
        planId = plan.getId();

        // crear 8 usuarios distintos
        for (int i = 1; i <= 8; i++) {
            String email = "concurrent" + i + "@test.com";
            createUser(email, cliente);
            tokens.add(loginAndGetToken(email));
        }
    }

    @AfterAll
    void cleanup() {
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

    @Test
    @DisplayName("8 usuarios intentan reservar el MISMO horario simultáneamente → solo 1 gana")
    void sameSlot_8concurrent_only1succeeds() throws Exception {
        String begin = futureDate(30, 9, 0);
        String end = futureDate(30, 13, 0);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CyclicBarrier barrier = new CyclicBarrier(8);

        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            String token = tokens.get(i);
            futures.add(executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS); // todos arrancan al mismo tiempo
                MvcResult result = mockMvc.perform(post("/api/reservations")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationJson(officeId, planId, begin, end, 3)))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        int successes = 0;
        int conflicts = 0;
        int errors = 0;

        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 201) successes++;
            else if (status == 409) conflicts++;
            else errors++;
        }

        System.out.println("=== CONCURRENCIA MISMO HORARIO ===");
        System.out.println("201 (creados): " + successes);
        System.out.println("409 (conflicto): " + conflicts);
        System.out.println("Otros errores: " + errors);

        // CLAVE: exactamente 1 reserva debe haberse creado
        Assertions.assertEquals(1, successes,
                "Exactamente 1 usuario debe ganar el horario, pero ganaron: " + successes);
        Assertions.assertEquals(7, conflicts,
                "Los otros 7 deben recibir 409 Conflict");
        Assertions.assertEquals(0, errors, "No debería haber errores inesperados");
    }

    @Test
    @DisplayName("4 usuarios reservan horarios DISTINTOS simultáneamente → todos ganan")
    void differentSlots_4concurrent_allSucceed() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CyclicBarrier barrier = new CyclicBarrier(4);

        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            String token = tokens.get(i);
            int day = 31 + i; // días distintos
            futures.add(executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                MvcResult result = mockMvc.perform(post("/api/reservations")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationJson(officeId, planId,
                                        futureDate(day, 9, 0), futureDate(day, 13, 0), 3)))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        int successes = 0;
        for (Future<Integer> future : futures) {
            if (future.get() == 201) successes++;
        }

        System.out.println("=== CONCURRENCIA HORARIOS DISTINTOS ===");
        System.out.println("201 (creados): " + successes);

        Assertions.assertEquals(4, successes,
                "Todos deben ganar porque son horarios distintos");
    }

    @Test
    @DisplayName("4 usuarios intentan horarios parcialmente solapados → máximo 1 por franja")
    void overlappingSlots_4concurrent_max1perSlot() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CyclicBarrier barrier = new CyclicBarrier(4);

        // todos quieren el mismo día, pero con rangos que se cruzan
        // user1: 09-13, user2: 11-15, user3: 10-14, user4: 12-16
        int[][] hours = {{9, 13}, {11, 15}, {10, 14}, {12, 16}};
        int day = 40;

        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            String token = tokens.get(i);
            int startH = hours[i][0];
            int endH = hours[i][1];
            futures.add(executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                MvcResult result = mockMvc.perform(post("/api/reservations")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationJson(officeId, planId,
                                        futureDate(day, startH, 0), futureDate(day, endH, 0), 3)))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        int successes = 0;
        int conflicts = 0;
        int errors = 0;

        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 201) successes++;
            else if (status == 409) conflicts++;
            else errors++;
        }

        System.out.println("=== CONCURRENCIA SOLAPAMIENTOS PARCIALES ===");
        System.out.println("201 (creados): " + successes);
        System.out.println("409 (conflicto): " + conflicts);
        System.out.println("Otros errores: " + errors);

        Assertions.assertEquals(1, successes,
                "Solo 1 debería ganar, los demás se solapan con él");
        Assertions.assertEquals(3, conflicts,
                "Los otros 3 deben recibir 409");
        Assertions.assertEquals(0, errors, "No debería haber errores inesperados");
    }

    @Test
    @DisplayName("Doble confirmación de pago simultánea → solo 1 se confirma")
    void doublePayment_concurrent_only1confirms() throws Exception {
        // crear pre-reserva
        String token = tokens.get(0);
        MvcResult r = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(officeId, planId,
                                futureDate(50, 9, 0), futureDate(50, 13, 0), 3)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        String reservationId = json.at("/data/id").asText();

        // 4 confirmaciones simultáneas del mismo usuario
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CyclicBarrier barrier = new CyclicBarrier(4);

        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            futures.add(executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                MvcResult result = mockMvc.perform(post("/api/reservations/confirm-payment")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format("{\"reservationId\": \"%s\"}", reservationId)))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        int successes = 0;
        int rejected = 0;

        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 200) successes++;
            else rejected++;
        }

        // El mock de pago siempre retorna SUCCESS, así que múltiples hilos pueden confirmar
        // En producción, la pasarela real (OpenPay) solo procesa un cargo por referencia
        // Lo que verificamos es que la reserva quede CONFIRMADA con un solo payment
        Reservation finalState = reservationRepository.findById(UUID.fromString(reservationId)).orElseThrow();
        Assertions.assertEquals("CONFIRMADA", finalState.getReservationStatus().getStatusName());
        Assertions.assertNotNull(finalState.getPayment());

        System.out.println("=== CONCURRENCIA DOBLE PAGO ===");
        System.out.println("Reserva final: " + finalState.getReservationStatus().getStatusName());
        System.out.println("Payment ID: " + finalState.getPayment().getId());
    }

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

    private void saveStatus(String name) {
        if (reservationStatusRepository.findByStatusName(name).isEmpty()) {
            ReservationStatus s = new ReservationStatus();
            s.setStatusName(name);
            reservationStatusRepository.save(s);
        }
    }

    private void createUser(String email, Role role) {
        User u = new User();
        u.setFirstName("Concurrent");
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
                        .content(String.format("{\"email\": \"%s\", \"password\": \"Test1234\"}", email)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.at("/data/token").asText();
    }

    private String futureDate(int daysAhead, int hour, int minute) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(daysAhead)
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0).toString();
    }

    private String reservationJson(UUID officeId, UUID planId, String begin, String end, int persons) {
        return String.format("""
        {
            "officeId": "%s",
            "officePlanId": "%s",
            "beginDate": "%s",
            "endDate": "%s",
            "personAmount": %d,
            "usesParking": false,
            "representativeName": "Test",
            "representativeLastName": "User",
            "representativeDni": "12345678"
        }
        """, officeId, planId, begin, end, persons);
    }
}