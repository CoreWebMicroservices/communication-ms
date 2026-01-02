-- ============================================================================
-- V1.0.0 - Create communication_ms schema
-- ============================================================================
-- Based on: MessageEntity, EmailMessageEntity, SMSMessageEntity, EmailAttachmentEntity
-- Uses JOINED inheritance strategy with discriminator column
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS communication_ms;

COMMENT ON SCHEMA communication_ms IS 'Communication service - emails, SMS';

SET search_path TO communication_ms;

-- ----------------------------------------------------------------------------
-- message table (base table for inheritance)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE,
    type            VARCHAR(31) NOT NULL,
    user_id         UUID NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         TIMESTAMP WITH TIME ZONE,
    sent_by_type    VARCHAR(20),
    sent_by_id      UUID
);

CREATE INDEX IF NOT EXISTS idx_message_uuid ON message(uuid);
CREATE INDEX IF NOT EXISTS idx_message_user ON message(user_id);
CREATE INDEX IF NOT EXISTS idx_message_type ON message(type);
CREATE INDEX IF NOT EXISTS idx_message_status ON message(status);
CREATE INDEX IF NOT EXISTS idx_message_created_at ON message(created_at);

-- ----------------------------------------------------------------------------
-- email table (extends message)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email (
    id              BIGINT PRIMARY KEY,
    email_type      VARCHAR(255) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    sender          VARCHAR(255) NOT NULL,
    sender_name     VARCHAR(255),
    cc              VARCHAR(255),
    bcc             VARCHAR(255),
    recipient       VARCHAR(255) NOT NULL,
    body            TEXT NOT NULL,
    
    CONSTRAINT fk_email_message FOREIGN KEY (id) REFERENCES message(id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- sms table (extends message)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sms (
    id              BIGINT PRIMARY KEY,
    phone_number    VARCHAR(50) NOT NULL,
    message         TEXT NOT NULL,
    sid             VARCHAR(255),
    
    CONSTRAINT fk_sms_message FOREIGN KEY (id) REFERENCES message(id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- email_attachment table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_attachment (
    id              BIGSERIAL PRIMARY KEY,
    uuid            BIGINT,
    document_uuid   UUID NOT NULL,
    checksum        VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_email_attachment_email FOREIGN KEY (uuid) REFERENCES email(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_email_attachment_email ON email_attachment(uuid);

RESET search_path;
