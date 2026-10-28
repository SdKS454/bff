package bff;

import bff.servlet.FrontendConfigServlet;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontendConfigTest {
    private final String original = System.getProperty("moderation.panel.url");

    @AfterEach
    void restore() {
        if (original == null) System.clearProperty("moderation.panel.url");
        else System.setProperty("moderation.panel.url", original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "//attacker.example/panel", "https://user:pass@example.com/panel", "https://example.com/#token=old"})
    void refusesDestinationsThatCouldMisrouteOrExposeAdminToken(String url) {
        System.setProperty("moderation.panel.url", url);
        assertThrows(ServletException.class, () -> new FrontendConfigServlet().init());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8082/admin/users.html", "https://moderation.example/panel/"})
    void acceptsAbsoluteConfiguredPanelUrls(String url) {
        System.setProperty("moderation.panel.url", url);
        assertDoesNotThrow(() -> new FrontendConfigServlet().init());
    }
}
