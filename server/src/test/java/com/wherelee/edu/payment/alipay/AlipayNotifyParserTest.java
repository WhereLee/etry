package com.wherelee.edu.payment.alipay;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlipayNotifyParserTest {

    @Test
    void parsesUtf8Form() {
        String body = "charset=utf-8&out_trade_no=ORD1_1&total_amount=0.01&subject="
                + URLEncoder.encode("物理必修一", StandardCharsets.UTF_8);
        Map<String, String> params = AlipayNotifyParser.parse(body.getBytes(StandardCharsets.UTF_8));
        assertThat(params).containsEntry("charset", "utf-8")
                .containsEntry("out_trade_no", "ORD1_1")
                .containsEntry("subject", "物理必修一");
    }

    @Test
    void parsesGbkFormUsingDeclaredCharset() {
        Charset gbk = Charset.forName("GBK");
        String body = "charset=GBK&total_amount=0.01&subject=" + URLEncoder.encode("物理必修一", gbk);
        Map<String, String> params = AlipayNotifyParser.parse(body.getBytes(gbk));
        assertThat(params).containsEntry("subject", "物理必修一");
    }

    @Test
    void fallsBackToUtf8WhenCharsetMissing() {
        String body = "out_trade_no=ORD1_1&total_amount=0.01";
        Map<String, String> params = AlipayNotifyParser.parse(body.getBytes(StandardCharsets.UTF_8));
        assertThat(params).containsEntry("out_trade_no", "ORD1_1");
    }

    @Test
    void decodesPlusAsSpaceAndPercentEscapes() {
        String body = "charset=utf-8&refund_reason=" + URLEncoder.encode("用户 申请+退款", StandardCharsets.UTF_8);
        Map<String, String> params = AlipayNotifyParser.parse(body.getBytes(StandardCharsets.UTF_8));
        assertThat(params).containsEntry("refund_reason", "用户 申请+退款");
    }
}
