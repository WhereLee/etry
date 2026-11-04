package com.wherelee.edu.payment.alipay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlipayAmountsTest {

    @Test
    void convertsCentsToYuanWithTwoDecimals() {
        assertThat(AlipayAmounts.toYuan(1)).isEqualTo("0.01");
        assertThat(AlipayAmounts.toYuan(100)).isEqualTo("1.00");
        assertThat(AlipayAmounts.toYuan(19900)).isEqualTo("199.00");
    }

    @Test
    void convertsYuanToCents() {
        assertThat(AlipayAmounts.toCents("0.01")).isEqualTo(1);
        assertThat(AlipayAmounts.toCents("1.00")).isEqualTo(100);
        assertThat(AlipayAmounts.toCents("199.00")).isEqualTo(19900);
    }

    @Test
    void rejectsNegativeCentsAndMissingYuan() {
        assertThatThrownBy(() -> AlipayAmounts.toYuan(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlipayAmounts.toCents(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlipayAmounts.toCents("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
