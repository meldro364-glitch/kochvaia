-- Email magic-code login. Used for devices without Google Play Services
-- (Amazon Fire) and as a universal fallback elsewhere.
--
-- We store sha256(code) at rest so a DB leak doesn't expose live codes.
-- A given (email, code) combination is one-shot; consumed_at flips it off.
-- TTL is enforced by expires_at; expired rows are swept lazily by inserts.

CREATE TABLE email_login_codes (
  email          TEXT NOT NULL,
  code_hash      TEXT NOT NULL,
  expires_at     INTEGER NOT NULL,
  consumed_at    INTEGER,
  created_at     INTEGER NOT NULL,
  ip             TEXT,
  PRIMARY KEY (email, code_hash)
);
CREATE INDEX idx_email_login_codes_expires ON email_login_codes(expires_at);
