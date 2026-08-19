ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE users ADD CONSTRAINT chk_user_role CHECK (
    role IN ('ADMIN', 'ORGANIZER', 'REFEREE', 'USER')
);

-- Every pre-existing account defaults to USER above, which can't create tournaments once
-- createTournament's role gate ships - promote whichever account is oldest so at least one
-- admin exists to bootstrap the rest via PATCH /users/{id}/role. No-op on a fresh, empty table.
UPDATE users SET role = 'ADMIN'
WHERE id = (SELECT id FROM users ORDER BY created_at ASC LIMIT 1);
