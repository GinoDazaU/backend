CREATE TABLE user_permissions (
                                  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  permission_name VARCHAR(100) NOT NULL UNIQUE,
                                  is_enabled  BOOLEAN NOT NULL DEFAULT true,
                                  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
                            id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            role_name   VARCHAR(100) NOT NULL UNIQUE,
                            is_enabled  BOOLEAN NOT NULL DEFAULT true,
                            created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_role_permissions (
                                       role_id       UUID NOT NULL REFERENCES user_roles(id),
                                       permission_id UUID NOT NULL REFERENCES user_permissions(id),
                                       PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
                       id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       first_name      VARCHAR(100) NOT NULL,
                       last_name       VARCHAR(100) NOT NULL,
                       email           VARCHAR(255) NOT NULL UNIQUE,
                       phone_number    VARCHAR(20) NOT NULL,
                       birth_date      DATE,
                       password_hash   VARCHAR(255) NOT NULL,
                       user_role_id    UUID NOT NULL REFERENCES user_roles(id),
                       failed_attempts INT NOT NULL DEFAULT 0,
                       locked_until    TIMESTAMPTZ,
                       is_enabled      BOOLEAN NOT NULL DEFAULT true,
                       created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE verification_tokens (
                                     id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     user_id     UUID NOT NULL REFERENCES users(id),
                                     token       UUID NOT NULL UNIQUE,
                                     expires_at  TIMESTAMPTZ NOT NULL,
                                     created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
                                       id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       user_id     UUID NOT NULL REFERENCES users(id),
                                       token       UUID NOT NULL UNIQUE,
                                       used        BOOLEAN NOT NULL DEFAULT false,
                                       expires_at  TIMESTAMPTZ NOT NULL,
                                       created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE office_kinds (
                              id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              kind_name   VARCHAR(100) NOT NULL UNIQUE,
                              is_enabled  BOOLEAN NOT NULL DEFAULT true,
                              created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                              updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE offices (
                         id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         name            VARCHAR(200) NOT NULL,
                         description     TEXT,
                         capacity        INT NOT NULL,
                         office_kind_id  UUID NOT NULL REFERENCES office_kinds(id),
                         conditions      TEXT,
                         is_enabled      BOOLEAN NOT NULL DEFAULT true,
                         created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE office_plans (
                              id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              office_kind_id      UUID NOT NULL REFERENCES office_kinds(id),
                              price_per_hour      NUMERIC(10,2) NOT NULL,
                              plan_duration_hours INT NOT NULL,
                              is_enabled          BOOLEAN NOT NULL DEFAULT true,
                              created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                              updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE office_blocks (
                               id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               office_id   UUID NOT NULL REFERENCES offices(id),
                               blocked_by  UUID NOT NULL REFERENCES users(id),
                               begin_date  TIMESTAMPTZ NOT NULL,
                               end_date    TIMESTAMPTZ NOT NULL,
                               reason      VARCHAR(500) NOT NULL,
                               is_active   BOOLEAN NOT NULL DEFAULT true,
                               created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_office_blocks_range ON office_blocks USING spgist (tstzrange(begin_date, end_date));
CREATE INDEX idx_offices_kind ON offices (office_kind_id) WHERE is_enabled = true;
CREATE INDEX idx_users_email ON users (email);