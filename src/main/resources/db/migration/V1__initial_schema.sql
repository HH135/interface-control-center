CREATE TABLE contract (
    id UUID PRIMARY KEY,
    contract_number VARCHAR(64) NOT NULL UNIQUE,
    customer_name VARCHAR(200) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE interface_message (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL UNIQUE REFERENCES contract(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED')),
    scenario VARCHAR(24) NOT NULL CHECK (scenario IN ('SUCCESS','FAIL_ONCE','ALWAYS_FAIL','TIMEOUT')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE batch_history (
    id UUID PRIMARY KEY,
    job_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING','SUCCESS','FAILED')),
    total_count INTEGER NOT NULL,
    success_count INTEGER NOT NULL,
    failure_count INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE interface_history (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES interface_message(id),
    batch_id UUID NOT NULL REFERENCES batch_history(id),
    attempt_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('SUCCESS','FAILED')),
    error_code VARCHAR(64),
    detail VARCHAR(500) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(message_id, attempt_number)
);
CREATE INDEX idx_history_processed ON interface_history(processed_at);
CREATE INDEX idx_message_status ON interface_message(status, created_at);
