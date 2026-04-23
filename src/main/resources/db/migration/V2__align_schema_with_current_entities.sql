-- V2: Align schema with the current JPA model without breaking existing local data.
-- This migration is written defensively because the project previously relied on
-- Hibernate ddl-auto=update, so some databases may already contain part of this schema.

-- USERS -----------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS email_notifications_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512);

-- FAMILY_INVITES ---------------------------------------------------------------
ALTER TABLE family_invites
    ADD COLUMN IF NOT EXISTS role VARCHAR(20);

UPDATE family_invites
SET role = 'PARENT'
WHERE role IS NULL;

ALTER TABLE family_invites
    ALTER COLUMN role SET NOT NULL;

-- EVENTS ----------------------------------------------------------------------
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS type VARCHAR(20);

ALTER TABLE events
    ALTER COLUMN ends_at DROP NOT NULL;

-- PETS ------------------------------------------------------------------------
ALTER TABLE pets
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS photo_url VARCHAR(512);

-- FAMILY_MEMBERS --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS family_members
(
    id            BIGSERIAL PRIMARY KEY,
    family_id     BIGINT NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    name          VARCHAR(80) NOT NULL,
    date_of_birth DATE,
    photo_url     VARCHAR(512),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- EVENT_PARTICIPANTS ----------------------------------------------------------
ALTER TABLE event_participants
    ALTER COLUMN participant_type TYPE VARCHAR(15);

ALTER TABLE event_participants
    ADD COLUMN IF NOT EXISTS family_member_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_event_participants_family_member'
    ) THEN
        ALTER TABLE event_participants
            ADD CONSTRAINT fk_event_participants_family_member
                FOREIGN KEY (family_member_id) REFERENCES family_members (id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_event_family_member'
    ) THEN
        ALTER TABLE event_participants
            ADD CONSTRAINT uq_event_family_member UNIQUE (event_id, family_member_id);
    END IF;
END $$;

-- TASKS -----------------------------------------------------------------------
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS private_task BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS task_assigned_users
(
    task_id  BIGINT NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    user_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, user_id)
);

CREATE TABLE IF NOT EXISTS task_assigned_members
(
    task_id           BIGINT NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    family_member_id  BIGINT NOT NULL REFERENCES family_members (id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, family_member_id)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'tasks'
          AND column_name = 'assigned_to_user_id'
    ) THEN
        INSERT INTO task_assigned_users (task_id, user_id)
        SELECT id, assigned_to_user_id
        FROM tasks
        WHERE assigned_to_user_id IS NOT NULL
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

ALTER TABLE tasks
    DROP COLUMN IF EXISTS assigned_to_user_id;

-- PASSWORD RESET TOKENS -------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
