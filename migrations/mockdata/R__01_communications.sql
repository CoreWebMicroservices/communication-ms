-- ============================================================================
-- R__01 - Seed communication data (Dev/Stage only)
-- ============================================================================
-- Based on MessageEntity, EmailMessageEntity, SMSMessageEntity
-- ============================================================================

SET search_path TO communication_ms;

-- ----------------------------------------------------------------------------
-- Sample email messages
-- ----------------------------------------------------------------------------
INSERT INTO message (uuid, type, user_id, status, sent_at, sent_by_type, sent_by_id) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'email', '20000000-0000-0000-0000-000000000006', 'sent', CURRENT_TIMESTAMP - INTERVAL '5 days', 'user', '20000000-0000-0000-0000-000000000001'),
    ('c0000000-0000-0000-0000-000000000002', 'email', '20000000-0000-0000-0000-000000000007', 'sent', CURRENT_TIMESTAMP - INTERVAL '4 days', 'user', '20000000-0000-0000-0000-000000000001'),
    ('c0000000-0000-0000-0000-000000000003', 'email', '20000000-0000-0000-0000-000000000008', 'sent', CURRENT_TIMESTAMP - INTERVAL '3 days', 'system', NULL),
    ('c0000000-0000-0000-0000-000000000004', 'email', '20000000-0000-0000-0000-000000000009', 'created', NULL, system, NULL),
    ('c0000000-0000-0000-0000-000000000005', 'email', '20000000-0000-0000-0000-000000000010', 'failed', NULL, 'system', NULL)
ON CONFLICT (uuid) DO NOTHING;

INSERT INTO email (id, email_type, subject, sender, sender_name, recipient, body) 
SELECT m.id, e.email_type, e.subject, e.sender, e.sender_name, e.recipient, e.body
FROM message m
JOIN (VALUES 
    ('c0000000-0000-0000-0000-000000000001'::uuid, 'html', 'Welcome to CoreMS!', 'noreply@corems.local', 'CoreMS Team', 'alice.johnson@corems.local', 'Welcome to CoreMS! Your account has been created.'),
    ('c0000000-0000-0000-0000-000000000002'::uuid, 'txt', 'New document shared', 'noreply@corems.local', 'CoreMS', 'bob.wilson@corems.local', 'A new document has been shared with you.'),
    ('c0000000-0000-0000-0000-000000000003'::uuid, 'txt', 'Password Reset Request', 'security@corems.local', 'CoreMS Security', 'charlie.brown@corems.local', 'Click here to reset your password.'),
    ('c0000000-0000-0000-0000-000000000004'::uuid, 'html', 'Verify your email', 'noreply@corems.local', 'CoreMS', 'diana.prince@corems.local', 'Please verify your email address.'),
    ('c0000000-0000-0000-0000-000000000005'::uuid, 'txt', 'Security Alert', 'security@corems.local', 'CoreMS Security', 'edward.stark@corems.local', 'Unusual login activity detected.')
) AS e(uuid, email_type, subject, sender, sender_name, recipient, body) ON m.uuid = e.uuid
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- Sample SMS messages
-- ----------------------------------------------------------------------------
INSERT INTO message (uuid, type, user_id, status, sent_at, sent_by_type) VALUES
    ('c0000000-0000-0000-0000-000000000011', 'sms', '20000000-0000-0000-0000-000000000011', 'sent', CURRENT_TIMESTAMP - INTERVAL '2 days', 'system'),
    ('c0000000-0000-0000-0000-000000000012', 'sms', '20000000-0000-0000-0000-000000000012', 'sent', CURRENT_TIMESTAMP - INTERVAL '1 day', 'system'),
    ('c0000000-0000-0000-0000-000000000013', 'sms', '20000000-0000-0000-0000-000000000013', 'created', NULL, 'system'),
    ('c0000000-0000-0000-0000-000000000014', 'sms', '20000000-0000-0000-0000-000000000014', 'failed', NULL, 'system')
ON CONFLICT (uuid) DO NOTHING;

INSERT INTO sms (id, phone_number, message, sid)
SELECT m.id, s.phone_number, s.message, s.sid
FROM message m
JOIN (VALUES 
    ('c0000000-0000-0000-0000-000000000011'::uuid, '+1234567890', 'Your verification code is 123456', 'SM001'),
    ('c0000000-0000-0000-0000-000000000012'::uuid, '+1234567891', 'Your password has been changed', 'SM002'),
    ('c0000000-0000-0000-0000-000000000013'::uuid, '+1234567892', 'Login attempt from new device', NULL),
    ('c0000000-0000-0000-0000-000000000014'::uuid, '+1234567893', 'Account locked due to suspicious activity', NULL)
) AS s(uuid, phone_number, message, sid) ON m.uuid = s.uuid
ON CONFLICT (id) DO NOTHING;

RESET search_path;
