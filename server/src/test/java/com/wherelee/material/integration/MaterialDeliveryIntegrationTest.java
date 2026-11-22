package com.wherelee.material.integration;

import com.wherelee.material.modules.commerce.api.OrderDtos;
import com.wherelee.material.modules.commerce.cart.CartCheckoutService;
import com.wherelee.material.modules.commerce.cart.CartDtos;
import com.wherelee.material.modules.commerce.cart.CartRepository;
import com.wherelee.material.modules.entitlement.api.EntitlementCommand;
import com.wherelee.material.modules.entitlement.api.ProductTypes;
import com.wherelee.material.modules.material.MaterialRecord;
import com.wherelee.material.modules.material.MaterialRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段5 交付链路验证：公开试读（截页+试读水印）、权益门槛下载（买家水印）、
 * 已购书架、下架后仍可下载、退款后回收下载权、并发下载懒生成文件只落一份。
 */
@AutoConfigureMockMvc
class MaterialDeliveryIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private EntitlementCommand entitlementCommand;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartCheckoutService cartCheckoutService;

    private long insertMaterial(String title, String saleStatus, String fileKey) {
        return materialRepository.insert(new MaterialRecord(
                null, title, "EXAM_PAPER", "SENIOR", "数学", "交付集成资料",
                990, "CNY", 10, 3, null, fileKey, saleStatus, null, null, null));
    }

    private String registerBuyer(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Buyer#2026x\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long userIdOf(String username) {
        return jdbc.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
    }

    private void grant(long userId, long materialId) {
        entitlementCommand.grantManual(new EntitlementCommand.GrantCommand(
                userId, materialId, ProductTypes.MATERIAL, null, null, null, 1));
    }

    private byte[] pdfBody(MvcResult result) throws Exception {
        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).startsWith('%', 'P', 'D', 'F');
        return body;
    }

    @Test
    void previewIsPublicTruncatedAndWatermarked() throws Exception {
        long id = insertMaterial("公开试读试卷", MaterialRecord.ON_SALE, "material/it-preview.pdf");

        MvcResult result = mockMvc.perform(get("/api/materials/" + id + "/preview"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);

        try (PDDocument doc = Loader.loadPDF(pdfBody(result))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s", "");
            assertThat(text).contains("试读");
            assertThat(text).doesNotContain("第4页");
        }
    }

    @Test
    void previewRejectsOffSaleMissingFileAndUnknownId() throws Exception {
        long offSale = insertMaterial("下架试读", MaterialRecord.OFF_SALE, "material/it-off.pdf");
        long noFile = insertMaterial("无文件试读", MaterialRecord.ON_SALE, null);

        mockMvc.perform(get("/api/materials/" + offSale + "/preview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("资料不存在或已下架"));
        mockMvc.perform(get("/api/materials/" + noFile + "/preview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("资料文件尚未上传"));
        mockMvc.perform(get("/api/materials/999999/preview"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadRequiresLoginAndEntitlement() throws Exception {
        long id = insertMaterial("门槛下载", MaterialRecord.ON_SALE, "material/it-gate.pdf");
        String token = registerBuyer("it_dl_gate");

        mockMvc.perform(get("/api/library/materials/" + id + "/download"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/library/materials/" + id + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("尚未购买该资料，无法下载"));
    }

    @Test
    void downloadWithEntitlementStampsBuyerOnEveryPage() throws Exception {
        long id = insertMaterial("买家水印试卷", MaterialRecord.ON_SALE, "material/it-buyer.pdf");
        String token = registerBuyer("it_dl_buyer");
        long userId = userIdOf("it_dl_buyer");
        grant(userId, id);

        MvcResult result = mockMvc.perform(get("/api/library/materials/" + id + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains(".pdf");

        try (PDDocument doc = Loader.loadPDF(pdfBody(result))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(10);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= 10; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertThat(stripper.getText(doc).replaceAll("\\s", ""))
                        .contains("已购·it_dl_buyer·ID" + userId);
            }
        }
    }

    @Test
    void bookshelfListsActiveEntitlementsOnly() throws Exception {
        long owned = insertMaterial("书架已购", MaterialRecord.ON_SALE, "material/it-shelf.pdf");
        insertMaterial("书架未购", MaterialRecord.ON_SALE, "material/it-shelf2.pdf");
        String token = registerBuyer("it_dl_shelf");
        grant(userIdOf("it_dl_shelf"), owned);

        mockMvc.perform(get("/api/library/materials"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/library/materials").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(owned))
                .andExpect(jsonPath("$[0].title").value("书架已购"))
                .andExpect(jsonPath("$[0].downloadable").value(true));
    }

    @Test
    void downloadSurvivesOffSaleButNotRefund() throws Exception {
        long id = insertMaterial("下架仍可下载", MaterialRecord.ON_SALE, "material/it-offdl.pdf");
        String token = registerBuyer("it_dl_offsale");
        long userId = userIdOf("it_dl_offsale");
        grant(userId, id);
        materialRepository.updateSaleStatus(id, MaterialRecord.OFF_SALE);

        mockMvc.perform(get("/api/library/materials/" + id + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        entitlementCommand.replaceManual(userId, ProductTypes.MATERIAL, List.of());
        mockMvc.perform(get("/api/library/materials/" + id + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullPurchaseDownloadRefundLoop() throws Exception {
        long id = insertMaterial("全链路资料", MaterialRecord.ON_SALE, "material/it-loop.pdf");
        String token = registerBuyer("it_dl_loop");
        long userId = userIdOf("it_dl_loop");
        cartRepository.add(userId, id);
        OrderDtos.OrderView order = cartCheckoutService.checkout(userId,
                new CartDtos.CheckoutRequest(List.of(id), nextRequestId()));
        paymentService.pay(userId, order.orderNo());
        paymentService.acceptVerified(successNotification(order.orderNo()));
        drainOutbox();

        mockMvc.perform(get("/api/library/materials/" + id + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        refundService.refund(order.orderNo(), fx.adminId(), "交付集成退款", false);
        drainOutbox();

        mockMvc.perform(get("/api/library/materials/" + id + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/library/materials").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void concurrentDownloadsGenerateFileOnce() throws Exception {
        String fileKey = "material/it-concurrent-" + System.nanoTime() + ".pdf";
        long id = insertMaterial("并发懒生成", MaterialRecord.ON_SALE, fileKey);
        String token = registerBuyer("it_dl_concurrent");
        grant(userIdOf("it_dl_concurrent"), id);
        Path file = Path.of("./target/it-materials").resolve(fileKey);
        Files.deleteIfExists(file);

        var results = runConcurrently(6, () -> mockMvc.perform(
                        get("/api/library/materials/" + id + "/download")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results).allSatisfy(result -> assertThat(result.value()).startsWith('%', 'P', 'D', 'F'));
        assertThat(file).exists();
    }
}