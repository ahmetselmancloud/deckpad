package com.dokunmatikekosistem.app.data.notification

import com.dokunmatikekosistem.app.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun `titleFor DISCONNECTED is Bagli degil`() {
        assertEquals("Bağlı değil", NotificationHelper.titleFor(ConnectionState.DISCONNECTED))
    }

    @Test
    fun `titleFor REGISTERING is Esleştiriliyor`() {
        assertEquals("Eşleştiriliyor", NotificationHelper.titleFor(ConnectionState.REGISTERING))
    }

    @Test
    fun `titleFor REGISTERED is Kayitli bekliyor`() {
        assertEquals("Kayıtlı, bağlantı bekleniyor", NotificationHelper.titleFor(ConnectionState.REGISTERED))
    }

    @Test
    fun `titleFor CONNECTED is Bagli`() {
        assertEquals("Bağlı", NotificationHelper.titleFor(ConnectionState.CONNECTED))
    }

    @Test
    fun `titleFor ERROR is Hata`() {
        assertEquals("Hata", NotificationHelper.titleFor(ConnectionState.ERROR))
    }
}
