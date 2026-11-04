package com.wherelee.edu.invitation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationTokenServiceTest {

    private final InvitationTokenService service = new InvitationTokenService();

    @Test
    void generatesUrlSafeTokensAndStableHashes() {
        String first = service.generate();
        String second = service.generate();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("+", "/", "=");
        assertThat(service.hash(first)).hasSize(64).isEqualTo(service.hash(first));
        assertThat(service.hash(first)).isNotEqualTo(service.hash(second));
    }
}
