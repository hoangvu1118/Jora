-- 1. Match reasons get their own table (matches @CollectionTable on the entity)
CREATE TABLE job_recommendation_match_reasons (
      job_recommendation_id BIGINT      NOT NULL,
      match_reason          VARCHAR(50) NOT NULL,

      CONSTRAINT pk_job_recommendation_match_reasons
          PRIMARY KEY (job_recommendation_id, match_reason),

      CONSTRAINT fk_match_reason_recommendation
          FOREIGN KEY (job_recommendation_id)
              REFERENCES job_recommendation(id)
              ON DELETE CASCADE
    );

-- 2. Drop the old unused column
ALTER TABLE job_recommendation DROP COLUMN match_reason;

-- 3. Deleting a search profile deletes its recommendations
ALTER TABLE job_recommendation DROP CONSTRAINT fk_search_profile;
ALTER TABLE job_recommendation
    ADD CONSTRAINT fk_search_profile
        FOREIGN KEY (search_profile_id)
            REFERENCES search_profiles(id)
            ON DELETE CASCADE;