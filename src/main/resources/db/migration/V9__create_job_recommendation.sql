CREATE TABLE job_recommendation (
      id BIGSERIAL PRIMARY KEY,

      search_profile_id BIGINT NOT NULL,
      job_id BIGINT NOT NULL,

      match_score INT,

      match_reason VARCHAR(1000),

      status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

      CONSTRAINT unique_searchProfile_jobId
          UNIQUE (search_profile_id, job_id)

);
