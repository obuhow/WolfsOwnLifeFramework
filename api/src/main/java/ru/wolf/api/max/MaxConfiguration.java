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
 * GNU Affero General License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. if not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.max;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the Max import channel: configuration properties and the dedicated
 * {@code RestClient} used by {@link HttpMaxAdapter}. No web/HTTP annotations here
 * — this is infrastructure only. {@link MaxProperties} is enabled here; the shared
 * {@code ImportBotProperties} rate-limit is enabled by
 * {@code ru.wolf.api.importer.ImporterConfiguration}.
 */
@Configuration
@EnableConfigurationProperties(MaxProperties.class)
public class MaxConfiguration {

    @Bean
    RestClient maxRestClient() {
        return RestClient.builder().build();
    }
}
