package com.sefujo.job.repository;

import com.sefujo.job.entity.Job;
import com.sefujo.searchprofile.EmploymentType;
import com.sefujo.searchprofile.WorkplaceType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Set;

// Specification reduces the amount of COMBINATION written in Repository
// Query : the overall query
// CB: CriteriaBuilder : create conditions like =, Like, AND, OR

public class JobSpecification {

    public static Specification<Job> isAfterCursor(LocalDateTime lastCreated, Long lastId) {
        return (root, query, cb) ->
                lastCreated == null
                    ? cb.conjunction() // means 'TRUE', accept to return TRUE
                    : cb.or(
                            cb.lessThan(root.<LocalDateTime>get("createdDate"), lastCreated),
                            cb.and(
                                    cb.equal(root.<LocalDateTime>get("createdDate"), lastCreated),
                                    cb.lessThan(root.<Long>get("id"), lastId)
                            )
                    );
    }
    public static Specification<Job> isOpen() {
        return (root, query, cb) ->
                cb.equal(root.get("status"), "OPEN");
    }
    public static Specification<Job> hasTitle(String title) {
        return (root, query, cb) ->
                title == null
                        ? cb.conjunction() // means 'TRUE', accept to return TRUE
                        : cb.like(
                        cb.lower(root.get("normalizedTitle")),
                        "%" + title.toLowerCase() + "%"
                );
    }

    public static Specification<Job> hasEmploymentType(
            EmploymentType employmentType
    ) {
        return (root, query, cb) ->
                employmentType == null
                        ? cb.conjunction()
                        : cb.equal(
                        root.get("employmentType"),
                        employmentType
                );
    }
    public static Specification<Job> hasEmploymentTypes(
            Set<EmploymentType> employmentTypes
    ) {
        return (root, query, cb) ->
                employmentTypes == null || employmentTypes.isEmpty()
                        ? cb.conjunction()
                        : root.get("employmentType").in(employmentTypes);
    } // employment_type IN ('INTERNSHIP', 'PART_TIME')

    public static Specification<Job> hasWorkplaceType(
            WorkplaceType workplaceType
    ) {
        return (root, query, cb) ->
                workplaceType == null
                        ? cb.conjunction()
                        : cb.equal(
                        root.get("workplaceType"),
                        workplaceType
                );
    }
    public static Specification<Job> hasWorkplaceTypes(
            Set<WorkplaceType> workplaceTypes
    ) {
        return (root, query, cb) ->
                workplaceTypes == null || workplaceTypes.isEmpty()
                        ? cb.conjunction()
                        : root.get("workplaceType").in(workplaceTypes);
    }

    public static Specification<Job> hasLocation(String location) {
        return (root, query, cb) ->
                location == null
                        ? cb.conjunction()
                        : cb.like(
                        cb.lower(root.get("location")),
                        "%" + location.toLowerCase() + "%"
                );
    }
    public static Specification<Job> hasAnyLocation(
            Set<String> locations
    ) {
        return (root, query, cb) -> {
            if (locations == null || locations.isEmpty()) {
                return cb.conjunction();
            }

            return cb.or(
                    locations.stream()
                            .map(location ->
                                    cb.like(
                                            cb.lower(root.get("location")),
                                            "%" + location.toLowerCase() + "%"
                                    )
                            )
                            .toArray(jakarta.persistence.criteria.Predicate[]::new)
            );
        };
    }
}

// A Predicate is a condition to tell if the evaluation is true or false

//      cb.like(
//        cb.lower(root.get("location")),
//        "%hanoi%"
//       )
// THIS RETURNS A PREDICATE