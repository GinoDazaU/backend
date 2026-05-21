CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Columna que indica si la reserva ocupa un slot activo (bloquea solapamiento)
ALTER TABLE reservations ADD COLUMN active_slot BOOLEAN NOT NULL DEFAULT true;

-- Constraint: no puede haber dos reservas con active_slot=true en la misma oficina y rango
ALTER TABLE reservations
    ADD CONSTRAINT no_overlapping_reservations
    EXCLUDE USING gist (
            office_id WITH =,
            tstzrange(begin_date, end_date) WITH &&
        )
        WHERE (active_slot = true);

-- Lo mismo para bloqueos de oficinas
ALTER TABLE office_blocks
    ADD CONSTRAINT no_overlapping_blocks
    EXCLUDE USING gist (
            office_id WITH =,
            tstzrange(begin_date, end_date) WITH &&
        )
        WHERE (is_active = true);