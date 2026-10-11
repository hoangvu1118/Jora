CREATE TABLE resume(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL ,
    file_path VARCHAR(255) NOT NULL ,
    file_name VARCHAR(255),
    file_size BIGINT,
    raw_text TEXT,
    content_type VARCHAR(100),
    archived_at  TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_resume_user_id
        FOREIGN KEY (user_id)
            REFERENCES users(id)
);

CREATE TABLE resume_version(
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT ,
    edit_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    type VARCHAR(16) NOT NULL,
    parent_version_id BIGINT DEFAULT NULL,
    resume_id BIGINT NOT NULL,
    content JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_resume_version_parent_version
        FOREIGN KEY (parent_version_id)
                REFERENCES resume_version(id),

    CONSTRAINT fk_resume_version_job_id
        FOREIGN KEY (job_id)
            REFERENCES jobs(id),

    CONSTRAINT fk_resume_version_resume_id
        FOREIGN KEY (resume_id)
            REFERENCES resume(id)
);

CREATE INDEX idx_resume_user_id ON resume(user_id);
CREATE INDEX idx_resume_version_resume_id ON resume_version(resume_id);