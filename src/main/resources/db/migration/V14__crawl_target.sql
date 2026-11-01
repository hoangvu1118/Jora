INSERT INTO platforms (name) VALUES ('greenhouse') ON CONFLICT (name) DO NOTHING;

CREATE TABLE crawl_targets(
    id BIGSERIAL PRIMARY KEY ,
    platform_id BIGINT not null,
    company_board_token VARCHAR(50) not null ,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_platform_id
        FOREIGN KEY (platform_id)
            REFERENCES platforms(id),

    CONSTRAINT unique_platform_id_board_token
        UNIQUE (platform_id, company_board_token)
);

INSERT INTO crawl_targets (platform_id, company_board_token)
SELECT id, 'axon' FROM platforms WHERE name = 'greenhouse';

