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
package ru.wolf.api.invite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.wolf.api.auth.JwtUtil;
import ru.wolf.api.instance.InstanceConfigService;
import ru.wolf.api.lifesphere.LifeSphereSeeder;
import ru.wolf.api.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DB-free smoke (release 1.1, тикет 08): при выключенном «Открыть доступ по инвайтам»
 * регистрация по коду закрыта на бэкенде, код не потребляется и пользователь не
 * создаётся. Полный кластерный IT — на релиз-гейте.
 */
@ExtendWith(MockitoExtension.class)
class InviteServiceInviteAccessUnitTest {

    @Mock
    private InviteCodeRepository inviteCodeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private LifeSphereSeeder lifeSphereSeeder;
    @Mock
    private InstanceConfigService instanceConfigService;

    @Test
    void registerWithInvite_throws_whenInviteAccessClosed() {
        when(instanceConfigService.isInviteAccessOpen()).thenReturn(false);

        InviteService service = new InviteService(
                inviteCodeRepository, userRepository, passwordEncoder, jwtUtil, lifeSphereSeeder, instanceConfigService);

        assertThatThrownBy(() -> service.registerWithInvite("SOMECODE", "newuser", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Регистрация по инвайтам отключена");

        // Пока флаг выключен — код не ищем и не тратим, пользователя не создаём.
        verify(inviteCodeRepository, never()).findByCode("SOMECODE");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
