CREATE TABLE analytics_bets (
    id UUID PRIMARY KEY,
    bet_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    sport VARCHAR(100) NOT NULL,
    league VARCHAR(255) NOT NULL,
    home_team VARCHAR(255) NOT NULL,
    away_team VARCHAR(255) NOT NULL,
    market VARCHAR(100) NOT NULL,
    selection VARCHAR(255) NOT NULL,
    odds NUMERIC(19, 4) NOT NULL,
    stake NUMERIC(19, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    profit NUMERIC(19, 2),
    return_amount NUMERIC(19, 2),
    placed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
