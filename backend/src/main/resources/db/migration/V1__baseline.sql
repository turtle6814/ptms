CREATE TABLE users (
    id UUID NOT NULL,
    username VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE (username),
    UNIQUE (phone_number)
);

CREATE TABLE tournaments (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    owner_id UUID,
    PRIMARY KEY (id),
    FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE events (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    tournament_id UUID NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id),
    FOREIGN KEY (tournament_id) REFERENCES tournaments (id)
);

CREATE TABLE pools (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    event_id UUID NOT NULL,
    is_complete BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (event_id) REFERENCES events (id)
);

CREATE TABLE teams (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    event_id UUID NOT NULL,
    pool_id UUID,
    created_at TIMESTAMP(6),
    PRIMARY KEY (id),
    FOREIGN KEY (event_id) REFERENCES events (id),
    FOREIGN KEY (pool_id) REFERENCES pools (id)
);

CREATE TABLE matches (
    id UUID NOT NULL,
    event_id UUID NOT NULL,
    pool_id UUID,
    bracket_round INTEGER,
    bracket_position INTEGER,
    team1_id UUID,
    team2_id UUID,
    team1_score INTEGER,
    team2_score INTEGER,
    winner_id UUID,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id),
    FOREIGN KEY (event_id) REFERENCES events (id),
    FOREIGN KEY (pool_id) REFERENCES pools (id),
    FOREIGN KEY (team1_id) REFERENCES teams (id),
    FOREIGN KEY (team2_id) REFERENCES teams (id),
    FOREIGN KEY (winner_id) REFERENCES teams (id)
);

CREATE TABLE pool_standings (
    id UUID NOT NULL,
    pool_id UUID NOT NULL,
    team_id UUID NOT NULL,
    wins INTEGER NOT NULL,
    losses INTEGER NOT NULL,
    points_for INTEGER NOT NULL,
    points_against INTEGER NOT NULL,
    point_differential INTEGER NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (team_id),
    FOREIGN KEY (pool_id) REFERENCES pools (id),
    FOREIGN KEY (team_id) REFERENCES teams (id)
);
