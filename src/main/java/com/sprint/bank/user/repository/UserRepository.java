package com.sprint.bank.user.repository;

import com.sprint.bank.user.entity.User;
import com.sprint.bank.user.enums.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "customerProfile")
    Optional<User> findWithProfileByEmail(String email);

    @EntityGraph(attributePaths = "customerProfile")
    Optional<User> findWithProfileById(Long id);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    @EntityGraph(attributePaths = "customerProfile")
    List<User> findAllByRoleOrderByIdDesc(Role role);
}
