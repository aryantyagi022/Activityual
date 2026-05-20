CREATE TABLE activities (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    title       VARCHAR(255) NOT NULL,
    category    VARCHAR(64)  NOT NULL,
    frequency   VARCHAR(32)  NOT NULL,
    notes       VARCHAR(2000),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_act_user ON activities(user_id);
CREATE INDEX idx_act_user_cat ON activities(user_id, category);

