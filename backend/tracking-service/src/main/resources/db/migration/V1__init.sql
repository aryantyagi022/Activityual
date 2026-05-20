CREATE TABLE activity_logs (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL,
    activity_id       UUID NOT NULL,
    activity_title    VARCHAR(255) NOT NULL,
    activity_category VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_log_user_time ON activity_logs(user_id, occurred_at);

CREATE TABLE outbox_events (
    id          UUID PRIMARY KEY,
    routing_key VARCHAR(64)  NOT NULL,
    payload     TEXT         NOT NULL,
    sent        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    sent_at     TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unsent ON outbox_events(sent) WHERE sent = FALSE;

