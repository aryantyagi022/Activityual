CREATE TABLE log_facts (
    id                UUID PRIMARY KEY,
    event_id          UUID NOT NULL UNIQUE,
    user_id           UUID NOT NULL,
    activity_id       UUID NOT NULL,
    activity_title    VARCHAR(255) NOT NULL,
    activity_category VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    hour_of_day       INT          NOT NULL,
    day_of_week       INT          NOT NULL
);
CREATE INDEX idx_rf_user_act ON log_facts(user_id, activity_id);

CREATE TABLE recommendations (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    activity_id     UUID NOT NULL,
    activity_title  VARCHAR(255) NOT NULL,
    kind            VARCHAR(32)  NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    confidence      DOUBLE PRECISION NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    accepted        BOOLEAN      NOT NULL DEFAULT FALSE,
    dismissed       BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_reco_user_act ON recommendations(user_id, activity_id);

