/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByAccountTypeAndStatus(String accountType, String status);
    
    long countByAccountTypeAndStatus(String accountType, String status);
    
    @Query("SELECT u FROM User u WHERE u.accountType <> 'DEMO' ORDER BY u.createdAt DESC")
    List<User> findAllRegularUsers();
    
    @Query("SELECT u FROM User u ORDER BY u.createdAt DESC")
    List<User> findAllUsersIncludeDemo();
    
    long countByRoleAndStatus(String role, String status);

    @Query("SELECT u FROM User u WHERE u.accountType = 'DEMO' AND u.status = 'ACTIVE' AND u.expiresAt < :now")
    List<User> findExpiredDemoAccounts(@Param("now") Instant now);
}