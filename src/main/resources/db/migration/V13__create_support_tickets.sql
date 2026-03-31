-- ============================================
-- V13__create_support_tickets.sql
-- Support ticket system
-- ============================================

CREATE TABLE support_tickets (
  id              BIGSERIAL PRIMARY KEY,
  tenant_id       BIGINT         NOT NULL REFERENCES tenants(id),
  created_by      BIGINT         NOT NULL REFERENCES users(id),
  title           VARCHAR(255)   NOT NULL,
  description     TEXT           NOT NULL,
  priority        VARCHAR(20)    DEFAULT 'MEDIUM',
  status          VARCHAR(30)    DEFAULT 'OPEN',
  category        VARCHAR(30)    DEFAULT 'GENERAL',
  assigned_to     BIGINT         REFERENCES users(id),
  created_at      TIMESTAMP      DEFAULT NOW(),
  updated_at      TIMESTAMP      DEFAULT NOW()
);

CREATE INDEX idx_support_tickets_tenant ON support_tickets(tenant_id);
CREATE INDEX idx_support_tickets_status ON support_tickets(status);
CREATE INDEX idx_support_tickets_assigned ON support_tickets(assigned_to);
CREATE INDEX idx_support_tickets_created_by ON support_tickets(created_by);

CREATE TABLE support_messages (
  id              BIGSERIAL PRIMARY KEY,
  ticket_id       BIGINT         NOT NULL REFERENCES support_tickets(id),
  sender_id       BIGINT         NOT NULL REFERENCES users(id),
  sender_type     VARCHAR(20)    NOT NULL,
  message         TEXT           NOT NULL,
  created_at      TIMESTAMP      DEFAULT NOW()
);

CREATE INDEX idx_support_messages_ticket ON support_messages(ticket_id);

CREATE TABLE support_attachments (
  id              BIGSERIAL PRIMARY KEY,
  ticket_id       BIGINT         NOT NULL REFERENCES support_tickets(id),
  file_url        VARCHAR(500)   NOT NULL,
  uploaded_by     BIGINT         NOT NULL REFERENCES users(id),
  created_at      TIMESTAMP      DEFAULT NOW()
);

CREATE INDEX idx_support_attachments_ticket ON support_attachments(ticket_id);
