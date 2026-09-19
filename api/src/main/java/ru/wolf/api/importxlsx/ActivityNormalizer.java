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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.importxlsx;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;

/**
 * Release 1.4 ticket 05 (decision И-E): the two-stage normalisation of activity spellings.
 *
 * <p><b>Stage 1 — trivial, automatic, silent.</b> Trim + lowercase. {@code «Сон»} / {@code «сон»} /
 * {@code « Сон »} collapse to one spelling and therefore to one Дело. It is a pure function of the
 * raw text and never touches the database — callers canonicalise the lookup key with
 * {@link #canonicalStage1(String)} so earlier mappings (which may have been stored in any case)
 * still match case-insensitively.
 *
 * <p><b>Stage 2 — semantic, only with explicit acknowledgment.</b> {@code «в спортзал»} vs
 * {@code «Спортзал»}, {@code «Клуб английского»} vs {@code «Английский клуб»} mean the same
 * activity to the user but the system must not assume so. {@link #normalize(User, String,
 * Optional, boolean)} creates an {@code activity_mapping} row pointing the raw text at an existing
 * Дело — but only when {@code acknowledged} is true. Without acknowledgment it returns no mapping,
 * so the activity stays {@code UNKNOWN} and raises an import question exactly like any untaught
 * activity: the merge is the user's choice, never the importer's guess. Activities that differ in
 * meaning ({@code «Проект»} / {@code «Проект игры»}) are simply never merged.
 */
@Component
@RequiredArgsConstructor
public class ActivityNormalizer {

    private final ActivityMappingRepository mappings;

    /** Stage 1 key: trimmed and lower-cased. {@code null} becomes the empty string. */
    public String canonicalStage1(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    /**
     * Resolves the Дело an activity text maps to, applying stage 2 only when the user confirmed it.
     *
     * @param user         current user
     * @param rawActivity  the activity text exactly as written in the sheet
     * @param existingDelo a Дело the user explicitly chose to merge this text onto (stage 2)
     * @param acknowledged true only after the user confirmed the semantic merge in the UI
     * @return the {@code activity_mapping} to use, or {@code null} when the activity stays UNKNOWN
     */
    @Transactional
    public ActivityMapping normalize(User user, String rawActivity, Optional<Delo> existingDelo,
                                     boolean acknowledged) {
        // Stage 1 already did its job if any stored mapping resolves to this text (case-insensitively).
        Optional<ActivityMapping> existing = mappings.findByUserAndActivityTextIgnoreCase(user, rawActivity);
        if (existing.isPresent()) {
            return existing.get();
        }
        // Without an explicit, acknowledged merge we must not invent one: leave it UNKNOWN.
        if (existingDelo.isEmpty() || !acknowledged) {
            return null;
        }
        // Stage 2: persist the merge under the canonical (stage-1) key so future spellings collapse.
        return mappings.save(ActivityMapping.builder()
                .user(user)
                .activityText(canonicalStage1(rawActivity))
                .delo(existingDelo.get())
                .build());
    }
}
