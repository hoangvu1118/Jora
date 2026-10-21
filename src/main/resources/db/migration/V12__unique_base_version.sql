CREATE UNIQUE INDEX uq_resume_version_one_base
    ON resume_version (resume_id)
    WHERE type = 'BASE';