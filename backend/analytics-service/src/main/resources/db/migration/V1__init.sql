CREATE TABLE log_facts (
    id                UUID PRIMARY KEY,
    event_id          UUID NOT NULL UNIQUE,
    user_id           UUID NOT NULL,
    activity_id       UUID NOT NULL,
    activity_title    VARCHAR(255) NOT NULL,
    activity_category VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    occurred_on       DATE         NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_fact_user_act_date ON log_facts(user_id, activity_id, occurred_on);

