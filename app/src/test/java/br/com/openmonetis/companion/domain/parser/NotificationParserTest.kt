package br.com.openmonetis.companion.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationParserTest {

    private val parser = NotificationParser()

    @Test
    fun `extracts only merchant from Mercado Pago credit card notification`() {
        val parsed = parser.parse(
            packageName = "com.mercadopago.wallet",
            title = null,
            text = """
                Você pagou R$ 164,45 a XsollaEpic

                O valor vai entrar na próxima fatura do seu Cartão Mercado Pago.
            """.trimIndent()
        )

        assertEquals("XsollaEpic", parsed.merchantName)
        assertEquals(164.45, parsed.amount!!, 0.0)
    }

    @Test
    fun `preserves Mercado Pago merchant prefix and asterisk`() {
        val parsed = parser.parse(
            packageName = "com.mercadopago.wallet",
            title = null,
            text = """
                Você pagou R$ 10,50 a MP*LOTERIASONLINEK74S

                O valor vai entrar na próxima fatura do seu Cartão Mercado Pago.
            """.trimIndent()
        )

        assertEquals("MP*LOTERIASONLINEK74S", parsed.merchantName)
        assertEquals(10.50, parsed.amount!!, 0.0)
    }

    @Test
    fun `ignores informational lines after transaction regardless of card message`() {
        val parsed = parser.parse(
            packageName = "com.example.bank",
            title = null,
            text = """
                Você pagou R$ 25,90 a MERCADO CENTRAL
                Esta linha informativa pode variar conforme o cartão.
            """.trimIndent()
        )

        assertEquals("MERCADO CENTRAL", parsed.merchantName)
        assertEquals(25.90, parsed.amount!!, 0.0)
    }

    @Test
    fun `ignores Mercado Pago informational suffix when notification is flattened`() {
        val parsed = parser.parse(
            packageName = "com.mercadopago.wallet",
            title = null,
            text = "Você pagou R$ 164,45 a XsollaEpic O valor vai entrar na próxima fatura do seu Cartão Mercado Pago."
        )

        assertEquals("XsollaEpic", parsed.merchantName)
        assertEquals(164.45, parsed.amount!!, 0.0)
    }
}
