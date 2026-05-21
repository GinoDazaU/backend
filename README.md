# Guía de arquitectura — Workpool

## Arquitectura elegida
Package by Feature: cada módulo agrupa Controller + Service + Repository + DTOs del mismo dominio. No hay capas horizontales globales. Es la misma estructura que defiende Josh Long (Spring) y que usa JHipster por defecto.

```text
com.workpool
├── common/        # Config, excepciones RFC-9457, respuestas, validadores, AOP audit
├── auth/          # Login, logout, tokens JWT, bloqueo de intentos
├── user/          # CRUD de usuarios, roles, permissions many-to-many dinámicos
├── office/        # Espacios, tipos de oficina, planes de precios
├── reservation/   # Núcleo: pre-reserva, pagos, estados, invitados, vehículos
├── payment/       # Integración con pasarela Culqi, reintentos, trazabilidad
├── notification/  # Correos transaccionales por cambio de estado (SMTP/SES)
└── report/        # Reportes administrativos, exportación PDF/Excel
```

## Flujo interno de cada módulo
Tres capas nada más. La entidad JPA nunca sale del Service hacia arriba.

```text
Controller → recibe/devuelve DTOs → Service → lógica + reglas RN → Repository
La entidad JPA vive en el Service. Nunca llega al Controller.
```

## Dependencias entre módulos
```text
auth → user
reservation → office, user, payment, notification
report → reservation, office, user
payment → Culqi (externo)
notification → SMTP (externo)
common ← todos lo usan, nadie lo produce
```

## Reglas — qué hacer

* **Queries complejas en SQL nativo:** Disponibilidad (RN-013), reportes (RF-035) y ocupación van con `@Query(nativeQuery=true)` y proyecciones a DTOs. Nunca joins en Java. Esto es lo que evita la degradación de rendimiento real.
* **SP-GIST + tstzrange para solapamiento de horarios:** La validación de solapamiento (RN-013, RNF-005) usa `tstzrange(begin_date, end_date) && tstzrange(:start, :end)` en SQL nativo con el índice SP-GIST. Una sola query, resultado atómico.
* **@Transactional en el Service de reserva completa:** Todo el flujo: bloqueo de horario → creación de pre-reserva → registro de pago → cambio de estado va en un solo `@Transactional`. Todo o nada (RNF-008, ACID).
* **SELECT FOR UPDATE SKIP LOCKED para concurrencia:** En la query de validación de disponibilidad dentro de la transacción. Previene doble asignación concurrente (RF-014) sin Redis ni mecanismos externos.
* **Interface solo donde hay razón real:** `CulqiClient` (interfaz) + `CulqiClientImpl` (implementación) para poder testear sin llamar a Culqi. Lo mismo para `NotificationService` porque el proveedor de correo puede cambiar. Solo estos dos.
* **ApplicationEvent de Spring para notificaciones:** El envío de correo se desacopla de la transacción principal con `@TransactionalEventListener`. Si falla el correo, no revierte la reserva. Se registra el fallo (RN-091).
* **@Aspect para auditoría (RECORD_HISTORY):** Un aspecto intercepta operaciones críticas y escribe en `RECORD_HISTORY`. No contamina la lógica de negocio de ningún módulo.
* **DTO con método estático fromEntity():** `ReservationResponse.fromEntity(Reservation r)` dentro del mismo DTO. Simple, legible, sin dependencias extra. Solo agrega MapStruct si los DTOs se vuelven muy repetitivos.

## Reglas — qué NO hacer

* **No sacar entidades JPA fuera del Service:** Una entidad JPA que llega al Controller puede ser serializada con campos que no deben exponerse, triggerea lazy loading fuera de sesión, y acopla tu API a tu schema de DB. Devuelve siempre un DTO.
* **No interfaces para todo:** No hagas `UserService` (interfaz) + `UserServiceImpl` (implementación) por defecto. Solo donde haya razón real (Culqi, SMTP). El patrón vacío genera archivos inútiles y confusión.
* **No capas horizontales globales:** No crear paquetes `com.workpool.service`, `com.workpool.repository`, `com.workpool.controller` que agrupen todo. Eso es Package by Layer y es lo que se decidió no usar.
* **No domain model separado de las entidades JPA:** No crear `reservation/domain/Reservation.java` y `reservation/entity/ReservationEntity.java` por separado. Es overhead de DDD puro sin beneficio en este tamaño de proyecto.
* **No joins en Java / no N+1:** No hagas `reservation.getGuests().forEach(...)` fuera de una transacción. Usa `JOIN FETCH` en JPQL o SQL nativo con proyección directa al DTO cuando necesites datos relacionados.
* **No patrones de diseño por moda:** No agregues Saga, CQRS, Event Sourcing, Hexagonal, ni Ports & Adapters. Son sobreingeniería para un monolito MVP. Si en el futuro se necesitan, se migra incrementalmente.
* **No lógica de negocio en el Controller:** El Controller valida entrada, llama al Service, y devuelve respuesta. Nada más. Las reglas RN viven en el Service. La construcción de queries vive en el Repository.
* **No romper el grafo acíclico de dependencias:** `notification` no puede depender de `reservation`. `payment` no puede depender de `user`. El grafo fluye hacia arriba: `common → domain → integration`. Si dos módulos se necesitan mutuamente, algo está mal modelado.

## Decisiones puntuales críticas

* **Pre-reserva de 30 min (RN-002):** El bloqueo temporal se gestiona con un campo `expires_at` en la reserva + estado "pendiente". Un job de Spring Scheduler (`@Scheduled`) libera expiradas cada minuto. El temporizador visible al usuario (RF-015) se calcula en frontend con `expires_at - now()`.
* **Resiliencia ante fallos de Culqi (RNF-007):** Envolver la llamada a Culqi con timeout explícito + retry con backoff exponencial (máx 3 intentos). Si falla, registrar en `PAYMENTS` con estado fallido (RN-059) y devolver error al usuario (RF-036). La reserva queda en "pendiente" mientras el horario siga vigente.
* **Conversión USD → PEN (RN-098, RN-099):** El tipo de cambio se consulta a una API externa al momento del checkout. Se registra el TC usado en la operación (inmutable). La pasarela Culqi recibe el monto en PEN. El usuario acepta explícitamente antes de iniciar el pago (RN-100).
* **Sistema de permisos dinámicos (RN-014, RN-015):** La tabla `USER_PERMISSIONS` ↔ `USER_ROLES` es many-to-many. Spring Security evalúa permisos en cada request. El "Administrador Principal" puede modificar qué permisos tiene cada rol desde el panel. Los permisos se cargan y cachean por sesión.
* **Borrador de reserva (RN-007, RN-101):** Cuando la sesión expira a mitad de una reserva, el formulario se guarda como borrador vinculado al `user_id` con estado "pendiente" y sin `payment_id`. Al re-ingresar, el Service detecta el borrador y lo ofrece al usuario para retomar.

## Stack tecnológico

* Spring Boot 3.x
* Spring Security
* Spring Data JPA
* PostgreSQL 18
* SP-GIST index
* JWT
* Bcrypt cost=10
* HTTPS obligatorio
* RFC-9457 errors
* @Scheduled jobs
* AOP Aspects