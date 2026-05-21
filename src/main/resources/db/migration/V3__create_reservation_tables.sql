CREATE TABLE reservation_status (
                                    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    status_name VARCHAR(50) NOT NULL UNIQUE,
                                    is_enabled  BOOLEAN NOT NULL DEFAULT true,
                                    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                                    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments (
                          id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          external_id     VARCHAR(255) UNIQUE,
                          amount_usd      NUMERIC(10,2) NOT NULL,
                          amount_pen      NUMERIC(10,2) NOT NULL,
                          exchange_rate   NUMERIC(10,4) NOT NULL,
                          payment_method  VARCHAR(50),
                          status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                          error_message   VARCHAR(500),
                          created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservations (
                              id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              user_id                 UUID NOT NULL REFERENCES users(id),
                              office_id               UUID NOT NULL REFERENCES offices(id),
                              payment_id              UUID REFERENCES payments(id),
                              reservation_status_id   UUID NOT NULL REFERENCES reservation_status(id),
                              begin_date              TIMESTAMPTZ NOT NULL,
                              end_date                TIMESTAMPTZ NOT NULL,
                              person_amount           INT NOT NULL,
                              uses_parking            BOOLEAN NOT NULL DEFAULT false,
                              price_per_hour          NUMERIC(10,2) NOT NULL,
                              total_price_usd         NUMERIC(10,2) NOT NULL,
                              total_price_pen         NUMERIC(10,2) NOT NULL,
                              exchange_rate           NUMERIC(10,4) NOT NULL,
                              representative_name     VARCHAR(200) NOT NULL,
                              representative_last_name VARCHAR(200) NOT NULL,
                              representative_dni      VARCHAR(20) NOT NULL,
                              created_by_admin        BOOLEAN NOT NULL DEFAULT false,
                              expires_at              TIMESTAMPTZ,
                              is_enabled              BOOLEAN NOT NULL DEFAULT true,
                              created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
                              updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE guests (
                        id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        name        VARCHAR(100) NOT NULL,
                        last_name   VARCHAR(100) NOT NULL,
                        dni         VARCHAR(20) NOT NULL,
                        created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation_guests (
                                    reservation_id UUID NOT NULL REFERENCES reservations(id),
                                    guest_id       UUID NOT NULL REFERENCES guests(id),
                                    PRIMARY KEY (reservation_id, guest_id)
);

CREATE TABLE vehicles (
                          id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          plate       VARCHAR(20) NOT NULL,
                          created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation_vehicles (
                                      reservation_id UUID NOT NULL REFERENCES reservations(id),
                                      vehicle_id     UUID NOT NULL REFERENCES vehicles(id),
                                      PRIMARY KEY (reservation_id, vehicle_id)
);

CREATE TABLE reservation_reschedule_status (
                                               id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               reschedule_status_name  VARCHAR(50) NOT NULL UNIQUE,
                                               is_enabled              BOOLEAN NOT NULL DEFAULT true,
                                               created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
                                               updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation_reschedule_requests (
                                                 id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                 user_id                 UUID NOT NULL REFERENCES users(id),
                                                 reservation_id          UUID NOT NULL REFERENCES reservations(id),
                                                 reschedule_status_id    UUID NOT NULL REFERENCES reservation_reschedule_status(id),
                                                 created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
                                                 updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índices para solapamiento de reservas
CREATE INDEX idx_reservations_range ON reservations USING spgist (tstzrange(begin_date, end_date));
CREATE INDEX idx_reservations_office ON reservations (office_id, reservation_status_id);
CREATE INDEX idx_reservations_user ON reservations (user_id);
CREATE INDEX idx_reservations_expires ON reservations (expires_at) WHERE expires_at IS NOT NULL;