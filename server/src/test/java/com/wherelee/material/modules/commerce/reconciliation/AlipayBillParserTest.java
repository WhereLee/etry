package com.wherelee.material.modules.commerce.reconciliation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlipayBillParserTest {

    private final AlipayBillParser parser = new AlipayBillParser();

    @Test
    void parsesPreambleQuotedFieldsAndRefundRows() {
        String csv = """
                支付宝交易记录明细查询
                商户订单号,支付宝交易号,订单金额,交易状态,业务类型,创建时间,退款单号
                ORD1,TRADE1,199.00,交易成功,交易,2026-11-16 10:00:00,
                ORD2,TRADE2,-50.00,退款成功,退款,2026-11-16 11:00:00,REF2
                """;

        ParsedBill bill = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(bill.rowCount()).isEqualTo(2);
        assertThat(bill.totalAmountCents()).isEqualTo(19900);
        assertThat(bill.items().get(0).businessType()).isEqualTo("PAYMENT");
        assertThat(bill.items().get(0).tradeStatus()).isEqualTo("SUCCESS");
        assertThat(bill.items().get(0).amountCents()).isEqualTo(19900);
        assertThat(bill.items().get(1).businessType()).isEqualTo("REFUND");
        assertThat(bill.items().get(1).refundNo()).isEqualTo("REF2");
    }

    @Test
    void parsesGb18030Statement() {
        String csv = "商户订单号,支付宝交易号,订单金额,交易状态\n"
                + "ORD1,TRADE1,1.00,TRADE_SUCCESS\n";

        ParsedBill bill = parser.parse(csv.getBytes(Charset.forName("GB18030")));

        assertThat(bill.encoding()).isEqualTo("GB18030");
        assertThat(bill.items()).hasSize(1);
        assertThat(bill.items().get(0).merchantTradeNo()).isEqualTo("ORD1");
    }

    @Test
    void parsesZipStatement() throws Exception {
        String csv = "商户订单号,支付宝交易号,订单金额,交易状态\n"
                + "ORD1,TRADE1,2.50,TRADE_SUCCESS\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("bill.csv"));
            zip.write(csv.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        ParsedBill bill = parser.parse(output.toByteArray());

        assertThat(bill.items()).hasSize(1);
        assertThat(bill.totalAmountCents()).isEqualTo(250);
    }

    @Test
    void rejectsZipSlipEntry() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("../bill.csv"));
            zip.write("商户订单号,支付宝交易号,订单金额,交易状态\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThatThrownBy(() -> parser.parse(output.toByteArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法路径");
    }

    @Test
    void rejectsMissingAmountAndMalformedRows() {
        String csv = "商户订单号,支付宝交易号,订单金额,交易状态\n"
                + "ORD1,TRADE1,,交易成功\n";

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少金额");
    }
}
