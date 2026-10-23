package com.comficonf.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessageLimitTest {

    private static final String GIFT = "\uD83C\uDF81";

    @Test
    void shortensIssueMessagesWithoutSeparatingSurrogatePairs() {
        String message = "m".repeat(496) + GIFT + "tail";
        assertThat(ValidationIssue.error("", message).message()).isEqualTo("m".repeat(496) + "...");
        String fits = "m".repeat(495) + GIFT + "tai";
        assertThat(ValidationIssue.warning("", fits + "l").message()).isEqualTo("m".repeat(495) + GIFT + "...");
        assertThat(ValidationIssue.error("", "m".repeat(500)).message()).hasSize(500);
    }

    @Test
    void shortensApplyMessagesWithoutSeparatingSurrogatePairs() {
        String message = "m".repeat(996) + GIFT + "tail";
        assertThat(ApplyResult.failed(message).message()).isEqualTo("m".repeat(996) + "...");
        assertThat(ApplyResult.applied("rev", "m".repeat(1000)).message()).hasSize(1000);
    }
}
