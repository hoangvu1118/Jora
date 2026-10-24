CREATE UNIQUE INDEX uq_resume_version_job_resumeId_tailored
    ON resume_version (resume_id, job_id)
    WHERE type = 'TAILORED';