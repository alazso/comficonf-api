package com.comficonf.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IncompatibleBridgeTest {

    @Test
    void namesTheInstalledVersionAndTheMissingClass() {
        String message = ComfiConfDemoPlugin.incompatibleBridgeMessage("0.1.0",
                new NoClassDefFoundError("com/comficonf/api/ConfigProvider"));

        assertThat(message)
                .startsWith("The installed ComfiConf 0.1.0 does not include the ComfiConf API this plugin needs")
                .contains("com.comficonf.api.ConfigProvider")
                .contains("Update ComfiConf")
                .endsWith("vouchers are configured through config.yml only until then.");
    }

    @Test
    void namesTheErrorWhenItHasNoMessage() {
        String message = ComfiConfDemoPlugin.incompatibleBridgeMessage("0.2.0", new IncompatibleClassChangeError());

        assertThat(message).contains("ComfiConf 0.2.0").contains("(IncompatibleClassChangeError)");
    }
}
