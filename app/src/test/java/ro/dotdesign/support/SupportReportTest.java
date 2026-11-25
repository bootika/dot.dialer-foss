package ro.dotdesign.support;

import org.junit.Test;
import static org.junit.Assert.*;

public class SupportReportTest {
    @Test public void recipientsAreProductSpecific() {
        assertEquals("dictate@dotdesign.ro", SupportReport.recipient(true));
        assertEquals("dialer@dotdesign.ro", SupportReport.recipient(false));
    }
    @Test public void decliningTechnicalDetailsOmitsEveryAutomaticField() {
        String body = SupportReport.body("Other", "Test Alexandru Butica", false,
                "3.04", "16", "Sample phone", "openai");
        assertEquals("Other\n\nTest Alexandru Butica", body);
    }
    @Test public void unknownProviderCannotBecomeAnAutomaticReportField() {
        String body = SupportReport.body("Other", "Test Alexandru Butica", true,
                "3.04", "16", "Sample phone", "secret-untrusted-value");
        assertFalse(body.contains("secret-untrusted-value"));
        assertFalse(body.contains("AI provider"));
    }
    @Test public void knownProviderIsIncludedOnlyWhenRequested() {
        assertTrue(SupportReport.body("Other", "Test Alexandru Butica", true,
                "3.04", "16", "Sample phone", "groq").contains("AI provider: groq"));
    }
    @Test public void nullFieldsAreSafe() {
        assertEquals("\n\n", SupportReport.body(null, null, false, null, null, null, null));
        assertFalse(SupportReport.body(null, null, true, null, null, null, null).contains("null"));
    }
    @Test public void freeTextHasAFiniteSize() {
        assertEquals(3002, SupportReport.body("", new String(new char[4000]).replace('\0','x'),
                false, "", "", "", "").length());
    }
}
