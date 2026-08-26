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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
public class RegistrationApiIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InviteCodeRepository inviteCodeRepository;
    @Autowired
    private InviteService inviteService;
    @Autowired
    private ru.wolf.api.user.UserRepository userRepository;

    private String createInvite(int maxUses, java.time.Instant expiresAt) throws Exception {
        // admin user must exist (created by DataInitializer)
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var invite = inviteService.createInvite(admin, maxUses, expiresAt, null);
        return invite.getCode();
    }

    @Test
    void validInvite_createsUserAndReturnsJwt() throws Exception {
        String code = createInvite(1, null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"username\":\"newuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
        // verify user created
        var user = userRepository.findByUsername("newuser");
        assert user.isPresent();
    }

    @Test
    void expiredCode_returnsNeutralMessage() throws Exception {
        String code = createInvite(1, java.time.Instant.now().minusSeconds(60));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"username\":\"user2\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Код недействителен"));
    }

    @Test
    void revokedCode_returnsNeutralMessage() throws Exception {
        String code = createInvite(1, null);
        var invite = inviteCodeRepository.findByCode(code).orElseThrow();
        invite.setRevokedAt(java.time.Instant.now());
        inviteCodeRepository.save(invite);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"username\":\"user3\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Код недействителен"));
    }

    @Test
    void exhaustedCode_returnsNeutralMessage() throws Exception {
        String code = createInvite(1, null);
        // first use
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"username\":\"user4\",\"password\":\"password123\"}"));
        // second use
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"username\":\"user5\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Код недействителен"));
    }

    @Test
    void duplicateUsername_returnsSpecificError() throws Exception {
        String code1 = createInvite(1, null);
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code1 + "\",\"username\":\"duplicate\",\"password\":\"password123\"}"));
        String code2 = createInvite(1, null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code2 + "\",\"username\":\"duplicate\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Имя занято"));
    }

    @Test
    void shortPassword_returnsSpecificError() throws Exception {
        String code = createInvite(1, null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"username\":\"user6\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Пароль должен быть не менее 8 символов"));
    }

    @Test
    void raceCondition_twoRequests_oneSucceeds() throws Exception {
        String code = createInvite(1, null);
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"username\":\"race1\",\"password\":\"password123\"}"));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"username\":\"race2\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Код недействителен"));
    }
}