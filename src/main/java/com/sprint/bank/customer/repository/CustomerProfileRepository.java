package com.sprint.bank.customer.repository;

import com.sprint.bank.customer.entity.CustomerProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<CustomerProfile> findByUserEmail(String email);

    @EntityGraph(attributePaths = "user")
    Optional<CustomerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByPan(String pan);

    @Query("SELECT c FROM CustomerProfile c JOIN FETCH c.user u WHERE u.role = com.sprint.bank.user.enums.Role.CUSTOMER ORDER BY c.id DESC")
    List<CustomerProfile> findAllCustomersWithUser();
}
