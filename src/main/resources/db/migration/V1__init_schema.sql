-- V1: Initial schema

CREATE TABLE families
(
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    created_by_user_id  BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    family_id     BIGINT REFERENCES families (id) ON DELETE SET NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- Add FK after users table exists (circular dependency: family <-> user)
ALTER TABLE families
    ADD CONSTRAINT fk_families_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE TABLE family_invites
(
    id                 BIGSERIAL PRIMARY KEY,
    family_id          BIGINT      NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    code               VARCHAR(32) NOT NULL UNIQUE,
    expires_at         TIMESTAMP   NOT NULL,
    used               BOOLEAN     NOT NULL DEFAULT false,
    created_by_user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at         TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE events
(
    id                 BIGSERIAL PRIMARY KEY,
    family_id          BIGINT       NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    title              VARCHAR(150) NOT NULL,
    description        TEXT,
    starts_at          TIMESTAMP    NOT NULL,
    ends_at            TIMESTAMP    NOT NULL,
    private_event      BOOLEAN      NOT NULL DEFAULT false,
    created_by_user_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recurrence_type    VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    recurrence_until   DATE,
    created_at         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE pets
(
    id         BIGSERIAL PRIMARY KEY,
    family_id  BIGINT      NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    name       VARCHAR(80) NOT NULL,
    type       VARCHAR(40),
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE event_participants
(
    id               BIGSERIAL PRIMARY KEY,
    event_id         BIGINT     NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    participant_type VARCHAR(10) NOT NULL,
    user_id          BIGINT REFERENCES users (id) ON DELETE CASCADE,
    pet_id           BIGINT REFERENCES pets (id) ON DELETE CASCADE,
    CONSTRAINT uq_event_user UNIQUE (event_id, user_id),
    CONSTRAINT uq_event_pet UNIQUE (event_id, pet_id)
);

CREATE TABLE tasks
(
    id                 BIGSERIAL PRIMARY KEY,
    family_id          BIGINT       NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    title              VARCHAR(150) NOT NULL,
    description        TEXT,
    status             VARCHAR(20)  NOT NULL DEFAULT 'TODO',
    priority           VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    assigned_to_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    due_date           DATE,
    created_by_user_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    completed_at       TIMESTAMP
);

CREATE TABLE notifications
(
    id                  BIGSERIAL PRIMARY KEY,
    recipient_user_id   BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type                VARCHAR(30)  NOT NULL,
    message             VARCHAR(500) NOT NULL,
    is_read             BOOLEAN      NOT NULL DEFAULT false,
    related_entity_type VARCHAR(20),
    related_entity_id   BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);
