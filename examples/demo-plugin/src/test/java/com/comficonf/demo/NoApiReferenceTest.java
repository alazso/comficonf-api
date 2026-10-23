package com.comficonf.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The plugin must load without the bridge: no class outside the integration package may refer to the API. */
class NoApiReferenceTest {

    private static final String API = "com/comficonf/api";

    @Test
    void mainClassContainsNoReferenceToTheApi() throws Exception {
        byte[] bytes;
        try (InputStream in = ComfiConfDemoPlugin.class.getResourceAsStream("ComfiConfDemoPlugin.class")) {
            assertThat(in).isNotNull();
            bytes = in.readAllBytes();
        }
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(content).contains("com/comficonf/demo/integration/ComfiConfIntegration");
        assertThat(content).doesNotContain(API);
    }

    @Test
    void onlyTheIntegrationPackageRefersToTheApi() throws Exception {
        Path classes = Path.of(ComfiConfDemoPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(classes)) {
            files = walk.filter(path -> path.toString().endsWith(".class")).toList();
        }
        assertThat(files).hasSizeGreaterThan(10);
        for (Path file : files) {
            String relative = classes.relativize(file).toString().replace('\\', '/');
            String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            if (relative.startsWith("com/comficonf/demo/integration/")) {
                continue;
            }
            assertThat(content).as(relative).doesNotContain(API);
        }
    }
}
