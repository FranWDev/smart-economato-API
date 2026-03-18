package com.economato.inventory.infrastructure.adapter.out.external.reports;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test que valida que todas las claves de MessageKey usadas en CrisisReportPdfService
 * existan en los archivos de propiedades de internacionalización.
 * 
 * Previene errores de NoSuchMessageException en tiempo de ejecución.
 */
@DisplayName("CrisisReportPdfService i18n Messages Validation")
class CrisisReportPdfServiceI18nTest {

    private static Properties messagesEn;
    private static Properties messagesEs;

    @BeforeAll
    static void loadProperties() throws IOException {
        messagesEn = loadProperties("/i18n/messages.properties");
        messagesEs = loadProperties("/i18n/messages_es.properties");
    }

    private static Properties loadProperties(String resourcePath) throws IOException {
        Properties props = new Properties();
        try (var stream = CrisisReportPdfServiceI18nTest.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            props.load(stream);
        }
        return props;
    }

    @Test
    @DisplayName("All CrisisReportPdfService message keys should exist in messages.properties")
    void testAllKeysExistInEnglish() {
        var requiredKeys = new String[] {
            "crisis.report.title",
            "report.label.crisis.id",
            "report.column.supplier",
            "report.label.reason",
            "report.label.date",
            "crisis.report.section.quarantine.products",
            "report.column.name",
            "report.column.barcode",
            "report.column.stock.current",
            "report.column.unit",
            "report.column.latest.hash",
            "crisis.report.section.affected.orders",
            "report.column.id",
            "report.label.status",
            "crisis.report.section.affected.cookings",
            "report.column.datetime",
            "report.column.recipe",
            "report.column.quantity",
            "crisis.report.section.traceability.chain",
            "report.label.integrity.status",
            "status.ok",
            "status.corrupt",
            "report.legal.notice"
        };

        for (String key : requiredKeys) {
            assertTrue(
                messagesEn.containsKey(key),
                "Missing i18n key in messages.properties: " + key
            );
        }
    }

    @Test
    @DisplayName("All CrisisReportPdfService message keys should exist in messages_es.properties")
    void testAllKeysExistInSpanish() {
        var requiredKeys = new String[] {
            "crisis.report.title",
            "report.label.crisis.id",
            "report.column.supplier",
            "report.label.reason",
            "report.label.date",
            "crisis.report.section.quarantine.products",
            "report.column.name",
            "report.column.barcode",
            "report.column.stock.current",
            "report.column.unit",
            "report.column.latest.hash",
            "crisis.report.section.affected.orders",
            "report.column.id",
            "report.label.status",
            "crisis.report.section.affected.cookings",
            "report.column.datetime",
            "report.column.recipe",
            "report.column.quantity",
            "crisis.report.section.traceability.chain",
            "report.label.integrity.status",
            "status.ok",
            "status.corrupt",
            "report.legal.notice"
        };

        for (String key : requiredKeys) {
            assertTrue(
                messagesEs.containsKey(key),
                "Missing i18n key in messages_es.properties: " + key
            );
        }
    }

    @Test
    @DisplayName("No invalid key format like 'traceability_chain' should exist (must be 'traceability.chain')")
    void testNoInvalidKeyFormats() {
        assertFalse(
            messagesEs.containsKey("crisis.report.section.traceability_chain"),
            "Invalid key format found: 'crisis.report.section.traceability_chain'. "
            + "Should use dots not underscores: 'crisis.report.section.traceability.chain'"
        );
    }

    @Test
    @DisplayName("Message values should not be empty")
    void testMessageValuesNotEmpty() {
        var requiredKeys = new String[] {
            "crisis.report.title",
            "report.label.crisis.id",
            "report.column.supplier",
            "crisis.report.section.quarantine.products",
            "crisis.report.section.affected.orders",
            "crisis.report.section.affected.cookings",
            "crisis.report.section.traceability.chain",
            "report.label.integrity.status",
            "report.legal.notice"
        };

        for (String key : requiredKeys) {
            String value = messagesEs.getProperty(key);
            assertNotNull(value, "Key has null value: " + key);
            assertFalse(value.trim().isEmpty(), "Key has empty value: " + key);
        }
    }
}
