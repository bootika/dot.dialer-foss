package io.github.bootika.dotdialer.core.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DialNumberTraceTest {
    @Test fun everyStageAndFormUsesOnlyTypedShapeAndChangeFlag() {
        for (stage in DialNumberStage.entries) for(form in DiagnosticNumberForm.entries) {
            val event=DiagnosticDetail.DialNumber(stage,form,false)
            assertEquals(DiagnosticLevel.NORMAL,event.level)
            assertEquals("dial_number | stage=$stage | form=$form | changed=false",DiagnosticDetailFormatter.format(event))
        }
        assertFalse(DiagnosticDetail.DialNumber::class.java.declaredFields.any {it.type==String::class.java || it.type.isArray})
    }
}
