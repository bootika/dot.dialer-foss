package ro.dotdesign.support;
import org.junit.Test;
import static org.junit.Assert.*;
public class SupportReferenceTest {
 @Test public void knownCrossLanguageVector(){assertEquals("DIC-PR5D-MLKF",SupportReference.code("dictate","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));}
 @Test public void stableAndProductScoped(){String id="12345678-1234-4234-8234-123456789abc";assertEquals(SupportReference.code("dialer",id),SupportReference.code("dialer",id));assertNotEquals(SupportReference.code("dictate",id),SupportReference.code("dialer",id));}
 @Test public void missingOrUntrustedIdentityHasNoInventedReference(){assertEquals("",SupportReference.code("dictate",null));assertEquals("",SupportReference.code("other","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));assertEquals("",SupportReference.code("dialer","user@example.test"));}
 @Test public void optingOutOmitsReferenceFromSubject(){assertEquals("Help",SupportReference.subject("Help","DIC-PR5D-MLKF",false));}
 @Test public void optedInSubjectIncludesOnlyAValidReference(){assertEquals("Help [DIC-PR5D-MLKF]",SupportReference.subject("Help","DIC-PR5D-MLKF",true));assertEquals("Help",SupportReference.subject("Help","credential-or-invalid-value",true));}
}
