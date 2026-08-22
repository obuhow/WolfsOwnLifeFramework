package ru.wolf.api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByAccountTypeAndStatus(String accountType, String status);
    
    @Query("SELECT u FROM User u WHERE u.accountType <> 'DEMO' ORDER BY u.createdAt DESC")
    List<User> findAllRegularUsers();
    
    @Query("SELECT u FROM User u ORDER BY u.createdAt DESC")
    List<User> findAllUsersIncludeDemo();
    
    long countByRoleAndStatus(String role, String status);
}