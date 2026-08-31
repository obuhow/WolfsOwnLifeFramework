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
package ru.wolf.api.instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DB-free smoke (release 1.1, тикет 08): singleton-настройка экземпляра.
 * Отсутствие строки = флаг включён (прежнее поведение); явное false сохраняется.
 */
@ExtendWith(MockitoExtension.class)
class InstanceConfigServiceUnitTest {

    @Mock
    private InstanceConfigRepository instanceConfigRepository;

    @Test
    void isInviteAccessOpen_defaultsTrue_whenNoRow() {
        when(instanceConfigRepository.findById(1)).thenReturn(Optional.empty());

        InstanceConfigService service = new InstanceConfigService(instanceConfigRepository);

        assertThat(service.isInviteAccessOpen()).isTrue();
    }

    @Test
    void isInviteAccessOpen_reflectsStoredFlag() {
        InstanceConfig config = InstanceConfig.builder().id(1).inviteAccessOpen(false).build();
        when(instanceConfigRepository.findById(1)).thenReturn(Optional.of(config));

        InstanceConfigService service = new InstanceConfigService(instanceConfigRepository);

        assertThat(service.isInviteAccessOpen()).isFalse();
    }

    @Test
    void setInviteAccessOpen_savesAndReturnsNewValue() {
        InstanceConfig saved = InstanceConfig.builder().id(1).inviteAccessOpen(false).build();
        when(instanceConfigRepository.findById(1)).thenReturn(Optional.of(saved));
        when(instanceConfigRepository.save(saved)).thenReturn(saved);

        InstanceConfigService service = new InstanceConfigService(instanceConfigRepository);

        assertThat(service.setInviteAccessOpen(false)).isFalse();
        verify(instanceConfigRepository).save(saved);
    }
}
