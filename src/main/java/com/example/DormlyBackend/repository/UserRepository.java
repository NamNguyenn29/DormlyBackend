package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.authentication.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @Query("""
            SELECT u
            FROM User u
            LEFT JOIN FETCH u.roles
            WHERE u.id = :id
            """)
    Optional<User> findUserWithRoles(UUID id);

    @Query("""
                SELECT u
                FROM User u
                LEFT JOIN FETCH u.roles
                WHERE u.email = :email
            """)
    Optional<User> findUserWithRolesByEmail(@Param("email") String email);

    Optional<User> findByEmail(String username);


    @Query("""
    SELECT DISTINCT u FROM User u
    LEFT JOIN FETCH u.roles r
    LEFT JOIN FETCH r.permissions
    WHERE u.email = :email
      AND u.isActive = true
    """)
    Optional<User> findByEmailWithRolesAndPermissions(@Param("email") String email);

    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN u.roles r
            WHERE UPPER(r.name) IN :roleNames
            """)
    java.util.List<User> findByRoleNameIn(@Param("roleNames") java.util.Set<String> roleNames);
}
