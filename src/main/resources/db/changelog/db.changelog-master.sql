--liquibase formatted sql

        --changeset mateusz.fronczek:20260523-01-init-extensions
        --comment: Enable btree_gist extension for composite GiST indexes
        CREATE EXTENSION IF NOT EXISTS btree_gist;

        --changeset mateusz.fronczek:20260523-02-create-time-slots
        --comment: Create time_slots table with native PostgreSQL tstzrange type
        CREATE TABLE time_slots (
        id UUID PRIMARY KEY,
        user_id UUID NOT NULL,
        duration_range TSTZRANGE NOT NULL,
        is_free BOOLEAN NOT NULL DEFAULT TRUE,
        version BIGINT NOT NULL DEFAULT 0
        );
        CREATE INDEX idx_slots_user_range ON time_slots USING gist (user_id, duration_range);

        --changeset mateusz.fronczek:20260523-03-create-meetings
        --comment: Create meetings table linked to time_slots
        CREATE TABLE meetings (
        id UUID PRIMARY KEY,
        time_slot_id UUID NOT NULL UNIQUE,
        title VARCHAR(255) NOT NULL,
        description TEXT,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
        CONSTRAINT fk_meetings_time_slot FOREIGN KEY (time_slot_id) REFERENCES time_slots (id) ON DELETE CASCADE
        );

        --changeset mateusz.fronczek:20260523-04-create-meeting-participants
        --comment: Create meeting_participants table for multi-passenger tracking
        CREATE TABLE meeting_participants (
        meeting_id UUID NOT NULL,
        participant_email VARCHAR(255) NOT NULL,
        CONSTRAINT pk_meeting_participants PRIMARY KEY (meeting_id, participant_email),
        CONSTRAINT fk_participants_meeting FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE
        );
