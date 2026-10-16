package dev.goodwy.rphone.controller.license

import org.junit.Assert.*
import org.junit.Test

class PlanInvitationLinkTest {
    private val code="a".repeat(64)
    @Test fun `fragment and explicit package handoff parse without losing token`() {
        assertEquals(code,PlanInvitationLink.token("https://dialer.dotdesign.ro/invite/test/#$code","dot.dialer.test"))
        assertEquals(code,PlanInvitationLink.token("https://dialer.dotdesign.ro/invite/test/?code=$code","dot.dialer.test"))
        assertEquals(code,PlanInvitationLink.token(code,"dot.dialer.test"))
        assertEquals(code,PlanInvitationLink.token("https://dialer.dotdesign.ro/invite/app/#$code","dot.dialer"))
    }
    @Test fun `reject spoofing wrong package insecure urls ports and extra query data`() {
        for(link in listOf("http://dialer.dotdesign.ro/invite/test/#$code","https://dialer.dotdesign.ro.evil.test/invite/test/#$code",
            "https://dialer.dotdesign.ro@evil.test/invite/test/#$code","https://dialer.dotdesign.ro:8443/invite/test/#$code",
            "https://dialer.dotdesign.ro/invite/app/#$code","https://dialer.dotdesign.ro/invite/test/?code=$code&other=1",
            "https://dialer.dotdesign.ro/invite/test/#short","x".repeat(64))) {
            assertNull(link,PlanInvitationLink.token(link,"dot.dialer.test"))
        }
    }
}
