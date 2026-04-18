-- Migration: Add quest_sessions table for graph execution
-- Separate table keeps graph traversal state distinct from progress tracking

CREATE TABLE IF NOT EXISTS quest_sessions (
    player_uuid VARCHAR(36) NOT NULL,
    quest_id VARCHAR(255) NOT NULL,
    current_node_id VARCHAR(255),
    completed_nodes TEXT,
    context_data TEXT,
    start_time BIGINT,
    last_active BIGINT,
    status VARCHAR(20),
    PRIMARY KEY (player_uuid, quest_id),
    INDEX idx_player (player_uuid),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;