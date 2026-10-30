package com.wherelee.etry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.etry.mail.CapturedMailSender;
import com.wherelee.etry.user.User;
import com.wherelee.etry.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关键链路集成测试：真 MySQL（etry_test）+ 本地磁盘存储 + 内存邮箱。
 * 覆盖 注册 → 验证码 → 激活 → 登录 → 预签名 → 直传 → 确认 → 列表 → 下载 → 删除 → 回收站。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndFileFlowIntegrationTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("(\\d{6})");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CapturedMailSender mailSender;
    @Autowired
    private UserRepository userRepository;

    @Test
    void registerVerifyLoginUploadAndTrash() throws Exception {
        String email = "it-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "Passw0rd123";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s","nickname":"集成测试"}
                                """.formatted(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String code = latestCodeFor(email);
        assertThat(code).hasSize(6);

        // 未验证不能登录
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password))))
                .andExpect(status().isForbidden());

        // 错误验证码被拒
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","code":"000000"}
                                """.formatted(email))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, code))))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.quotaBytes").value(524288000))
                .andReturn();

        String cookie = loginResult.getResponse().getHeader("Set-Cookie");
        String token = cookie.split(";", 2)[0];
        assertThat(cookie).contains("HttpOnly");

        long size = 2048;
        MvcResult presignResult = mockMvc.perform(post("/api/files/presign")
                        .cookie(new jakarta.servlet.http.Cookie("etry_token", token.split("=", 2)[1]))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"report.txt","sizeBytes":%d}
                                """.formatted(size))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode presign = objectMapper.readTree(presignResult.getResponse().getContentAsString()).path("data");
        long fileId = presign.path("fileId").asLong();
        String uploadUrl = presign.path("uploadUrl").asText();
        assertThat(presign.path("contentType").asText()).isEqualTo("text/plain");

        jakarta.servlet.http.Cookie authCookie =
                new jakarta.servlet.http.Cookie("etry_token", token.split("=", 2)[1]);

        // 模拟浏览器直传：直接打到本地存储端点
        mockMvc.perform(multipart("/local-storage/upload")
                        .file(new MockMultipartFile("file", "report.txt", "text/plain", new byte[(int) size]))
                        .param("key", extractKey(uploadUrl)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/files/" + fileId + "/commit").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("report.txt"))
                .andExpect(jsonPath("$.data.sizeBytes").value(size));

        mockMvc.perform(get("/api/files").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("report.txt"));

        mockMvc.perform(get("/api/files/" + fileId + "/download-url").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").isNotEmpty());

        // 配额已结算，预占归零
        mockMvc.perform(get("/api/auth/me").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedBytes").value(size))
                .andExpect(jsonPath("$.data.reservedBytes").value(0));

        // 删除进回收站
        mockMvc.perform(delete("/api/files/" + fileId).cookie(authCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/trash").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // 恢复
        mockMvc.perform(post("/api/trash/" + fileId + "/restore").cookie(authCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/files").cookie(authCookie))
                .andExpect(jsonPath("$.data.total").value(1));

        // 改密后旧 cookie 立即失效
        mockMvc.perform(post("/api/me/password")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"oldPassword":"%s","newPassword":"NewPassw0rd456"}
                                """.formatted(password))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(authCookie))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 配额是真预占：签凭证时先占住字节，挤不进去就拒；取消后预占必须退回，额度重新可用。
     */
    @Test
    void quotaIsReservedOnPresignAndReleasedOnCancel() throws Exception {
        String email = "quota-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "Passw0rd123";
        registerAndActivate(email, password);
        jakarta.servlet.http.Cookie cookie = login(email, password);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setQuotaBytes(3000);
        userRepository.save(user);

        MvcResult first = mockMvc.perform(post("/api/files/presign")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"first.bin","sizeBytes":2000}
                                """)))
                .andExpect(status().isOk())
                .andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("fileId").asLong();

        mockMvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(jsonPath("$.data.reservedBytes").value(2000))
                .andExpect(jsonPath("$.data.usedBytes").value(0));

        // 额度已被预占，第二个传不下
        mockMvc.perform(post("/api/files/presign")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"second.bin","sizeBytes":2000}
                                """)))
                .andExpect(status().isPayloadTooLarge());

        // 取消上传后退回预占，额度重新可用
        mockMvc.perform(delete("/api/files/" + firstId + "/pending").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(jsonPath("$.data.reservedBytes").value(0));

        mockMvc.perform(post("/api/files/presign")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"third.bin","sizeBytes":2000}
                                """)))
                .andExpect(status().isOk());
    }

    @Test
    void oversizedFileIsRejectedByQuotaCheck() throws Exception {
        String email = "big-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "Passw0rd123";
        registerAndActivate(email, password);
        jakarta.servlet.http.Cookie cookie = login(email, password);

        mockMvc.perform(post("/api/files/presign")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"too-big.bin","sizeBytes":%d}
                                """.formatted(60L * 1024 * 1024))))
                .andExpect(status().isBadRequest());
    }

    private void registerAndActivate(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s","nickname":"大文件"}
                                """.formatted(email, password))))
                .andExpect(status().isOk());
        String code = latestCodeFor(email);
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, code))))
                .andExpect(status().isOk());
    }

    private jakarta.servlet.http.Cookie login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        String cookie = result.getResponse().getHeader("Set-Cookie");
        String value = cookie.split(";", 2)[0].split("=", 2)[1];
        return new jakarta.servlet.http.Cookie("etry_token", value);
    }

    private String latestCodeFor(String email) {
        return mailSender.recent().stream()
                .filter(mail -> mail.to().equals(email))
                .findFirst()
                .map(mail -> {
                    Matcher matcher = CODE_PATTERN.matcher(mail.body());
                    return matcher.find() ? matcher.group(1) : "";
                })
                .orElse("");
    }

    private String extractKey(String uploadUrl) {
        String query = URI.create(uploadUrl).getQuery();
        for (String part : query.split("&")) {
            if (part.startsWith("key=")) {
                return java.net.URLDecoder.decode(part.substring(4), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private String json(String value) {
        return value.strip();
    }
}
