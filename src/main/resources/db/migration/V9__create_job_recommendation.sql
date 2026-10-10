CREATE TABLE job_recommendation (
      id BIGSERIAL PRIMARY KEY,

      search_profile_id BIGINT NOT NULL,
      job_id BIGINT NOT NULL,

      match_score INT,

      match_reason VARCHAR(1000),

      status VARCHAR(50) NOT NULL DEFAULT 'NEW',

      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

      CONSTRAINT unique_searchProfile_jobId
          UNIQUE (search_profile_id, job_id),

      CONSTRAINT fk_search_profile
          FOREIGN KEY  (search_profile_id)
              REFERENCES search_profiles(id),

      CONSTRAINT fk_job
          FOREIGN KEY (job_id)
              REFERENCES jobs(id)
);
