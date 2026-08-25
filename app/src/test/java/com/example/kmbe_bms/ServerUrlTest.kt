package com.example.kmbe_bms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `adds scheme default port and dashboard path`() {
        assertEquals(
            "http://192.168.1.50:1880/ui/",
            ServerUrl.normalize(" 192.168.1.50 ")
        )
    }

    @Test
    fun `preserves https and explicit port`() {
        assertEquals(
            "https://bms.example.com:8443/ui/",
            ServerUrl.normalize("https://bms.example.com:8443/")
        )
    }

    @Test
    fun `normalizes scheme casing`() {
        assertEquals(
            "https://bms.example.com:443/ui/",
            ServerUrl.normalize("HTTPS://BMS.EXAMPLE.COM:443")
        )
    }

    @Test
    fun `rejects empty input`() {
        assertNull(ServerUrl.normalize("   "))
    }

    @Test
    fun `rejects unsupported scheme`() {
        assertNull(ServerUrl.normalize("ftp://bms.example.com"))
    }

    @Test
    fun `rejects credentials`() {
        assertNull(ServerUrl.normalize("http://admin:secret@bms.example.com"))
    }

    @Test
    fun `rejects fragments`() {
        assertNull(ServerUrl.normalize("http://bms.example.com#dashboard"))
    }

    @Test
    fun `rejects invalid ports`() {
        assertNull(ServerUrl.normalize("http://bms.example.com:70000"))
    }

    @Test
    fun `allows navigation on the configured origin`() {
        assertEquals(
            true,
            ServerUrl.isAllowed(
                "http://bms.example.com:1880/ui/",
                "http://BMS.EXAMPLE.COM:1880/ui/dashboard"
            )
        )
    }

    @Test
    fun `allows omitted standard target port`() {
        assertEquals(
            true,
            ServerUrl.isAllowed(
                "https://bms.example.com:443/ui/",
                "https://bms.example.com/ui/dashboard"
            )
        )
    }

    @Test
    fun `rejects a different host`() {
        assertEquals(
            false,
            ServerUrl.isAllowed(
                "http://bms.example.com:1880/ui/",
                "http://attacker.example.com:1880/ui/"
            )
        )
    }

    @Test
    fun `rejects a different scheme`() {
        assertEquals(
            false,
            ServerUrl.isAllowed(
                "http://bms.example.com:1880/ui/",
                "https://bms.example.com:1880/ui/"
            )
        )
    }

    @Test
    fun `rejects a different port`() {
        assertEquals(
            false,
            ServerUrl.isAllowed(
                "http://bms.example.com:1880/ui/",
                "http://bms.example.com:1881/ui/"
            )
        )
    }

    @Test
    fun `rejects navigation without a configured server`() {
        assertEquals(
            false,
            ServerUrl.isAllowed(null, "http://bms.example.com:1880/ui/")
        )
    }
}