CREATE TABLE bets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    sport VARCHAR(100) NOT NULL,
    league VARCHAR(255) NOT NULL,
    home_team VARCHAR(255) NOT NULL,
    away_team VARCHAR(255) NOT NULL,
    market VARCHAR(100) NOT NULL,
    selection VARCHAR(255) NOT NULL,
    odds NUMERIC(19, 4) NOT NULL,
    stake NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    profit NUMERIC(19, 2),
    return_amount NUMERIC(19, 2),
    placed_at TIMESTAMPTZ NOT NULL,
    settled_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bets_user_id ON bets (user_id);
CREATE INDEX idx_bets_user_id_placed_at ON bets (user_id, placed_at);
