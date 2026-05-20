CREATE TABLE notifications (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    message     VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    read_flag   BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_notif_user ON notifications(user_id, created_at DESC);

