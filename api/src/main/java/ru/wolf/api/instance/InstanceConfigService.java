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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Чтение и запись глобальных настроек экземпляра (release 1.1, тикет 08).
 * Singleton-строка {@code id=1}; при отсутствии строки флаг считается включённым,
 * чтобы существующие инсталляции (до миграции V40) вели себя как раньше.
 */
@Service
@RequiredArgsConstructor
public class InstanceConfigService {

    private static final int SINGLETON_ID = 1;

    private final InstanceConfigRepository instanceConfigRepository;

    @Transactional(readOnly = true)
    public boolean isInviteAccessOpen() {
        return instanceConfigRepository.findById(SINGLETON_ID)
                .map(InstanceConfig::isInviteAccessOpen)
                .orElse(true);
    }

    @Transactional
    public boolean setInviteAccessOpen(boolean open) {
        InstanceConfig config = instanceConfigRepository.findById(SINGLETON_ID)
                .orElseGet(() -> InstanceConfig.builder().id(SINGLETON_ID).build());
        config.setInviteAccessOpen(open);
        return instanceConfigRepository.save(config).isInviteAccessOpen();
    }
}
