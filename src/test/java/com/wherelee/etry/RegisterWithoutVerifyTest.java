package com.wherelee.etry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * etry.mail.verify-register=false 时：注册直接生效，不用收验证码就能登录。
 */
@SpringBootTest(properties = "etry.mail.verify-register=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegisterWithoutVerifyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerTakesEffectImmediatelyWhenVerificationIsOff() throws Exception {
        String email = "noverify-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String body = """
                {"email":"%s","password":"Passw0rd123","nickname":"免验证"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.strip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needVerify").value(false));

        // 没有验证码也能直接登录
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd123"}
                                """.formatted(email).strip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }
}
