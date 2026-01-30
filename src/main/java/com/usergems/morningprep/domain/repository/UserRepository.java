package com.usergems.morningprep.domain.repository;

import com.usergems.morningprep.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by their email address.
     */
    Optional<User> findByEmail(String email);

    /**
     * Find users by their timezone.
     * Used for scheduling morning emails at 8am in each timezone.
     */
    @Query("SELECT u FROM User u WHERE u.timezone IN :timezones")
    List<User> findByTimezoneIn(@Param("timezones") List<String> timezones);

    /**
     * Get all distinct timezones used by users.
     * Used to determine which timezones need checking for 8am.
     */
    @Query("SELECT DISTINCT u.timezone FROM User u")
    List<String> findAllDistinctTimezones();

    /**
     * Check if a user exists by email.
     */
    boolean existsByEmail(String email);
}
