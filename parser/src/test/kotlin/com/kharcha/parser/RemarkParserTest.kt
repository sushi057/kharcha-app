package com.kharcha.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class RemarkParserTest {

    @Test
    fun `rail and counterparty are independent - eSewa channel does not overwrite the bank counterparty`() {
        val result = RemarkParser.parse("Fund Trf to NABIL BANK LTD (ESEW-9815618427,79578/FUN MOB/SBLMOB01")
        assertEquals("Nabil Bank Ltd", result.merchant)
        assertEquals("eSewa", result.channel)
        assertEquals(RemarkParser.Kind.TRANSFER, result.kind)
    }

    @Test
    fun `connectIPS channel with a real counterparty keeps the counterparty as merchant`() {
        val result = RemarkParser.parse("cIPS Fund Trf frm IPS E-PAYMENT I-/FUN IPS/SBLIPS02")
        assertEquals("Ips E-payment", result.merchant)
        assertEquals("connectIPS", result.channel)
        assertEquals(RemarkParser.Kind.TRANSFER, result.kind)
    }

    @Test
    fun `connectIPS channel falls back to the rail name when no counterparty is extractable`() {
        val result = RemarkParser.parse("cIPS Fund Trf Charge")
        assertEquals("connectIPS", result.merchant)
        assertEquals("connectIPS", result.channel)
    }

    @Test
    fun `IBFT internal ledger payee falls through to the rail name`() {
        // "A/C PAYABLE IBFT" names the bank's own ledger account, not a person, so the
        // rail name is the more useful merchant. See RemarkParser.genericLedgerNames.
        val result = RemarkParser.parse("Fund Trf to A/C PAYABLE IBFT (IN-401573123,Mobile/FUN MOB/SBLMOB01")
        assertEquals("IBFT", result.merchant)
        assertEquals("IBFT", result.channel)
        assertEquals(RemarkParser.Kind.TRANSFER, result.kind)
    }

    @Test
    fun `Khalti channel keeps the counterparty as merchant`() {
        val result = RemarkParser.parse("Fund Trf to KHALTI DIGITAL WALLET (KHALTI-12345/FUN MOB/SBLMOB01")
        assertEquals("Khalti Digital Wallet", result.merchant)
        assertEquals("Khalti", result.channel)
    }

    @Test
    fun `IME Pay channel keeps the counterparty as merchant`() {
        val result = RemarkParser.parse("Fund Trf to IME LTD (IMEPAY-556677/FUN MOB/SBLMOB01")
        assertEquals("Ime Ltd", result.merchant)
        assertEquals("IME Pay", result.channel)
    }

    @Test
    fun `FonePay channel keeps the counterparty as merchant`() {
        val result = RemarkParser.parse("Fund Trf to SOME MERCHANT (FONEPAY-11223/FUN MOB/SBLMOB01")
        assertEquals("Some Merchant", result.merchant)
        assertEquals("FonePay", result.channel)
    }

    @Test
    fun `bare FONE is not mistaken for the FonePay rail`() {
        val result = RemarkParser.parse("Fund Trf to PHONE ACCESSORIES SHOP (POS-11223")
        assertEquals(false, "FonePay" == result.channel)
    }

    @Test
    fun `bare IPS word is not mistaken for the connectIPS rail`() {
        val result = RemarkParser.parse("Fund Trf to CHIPS AND DIPS TRADERS SOMEWHERE")
        assertEquals(false, "connectIPS" == result.channel)
    }

    @Test
    fun `mobile banking channel marker never wins over a specific rail like eSewa`() {
        val result = RemarkParser.parse("Fund Trf to NABIL BANK LTD (ESEW-9815618427,79578/FUN MOB/SBLMOB01")
        assertEquals("eSewa", result.channel)
    }

    @Test
    fun `mobile banking channel marker is used only as a last resort fallback`() {
        val result = RemarkParser.parse("Fund Trf to SOME BUSINESS (REF-11223,Mobile/FUN MOB/SBLMOB01")
        assertEquals("Mobile banking", result.channel)
        assertEquals("Some Business", result.merchant)
    }

    @Test
    fun `does not leak reference number soup into merchant`() {
        val result = RemarkParser.parse("Fund Trf to NABIL BANK LTD (ESEW-9815618427,79578/FUN MOB/SBLMOB01")
        assertEquals(false, (result.merchant ?: "").contains("9815618427"))
        assertEquals(false, (result.merchant ?: "").contains("SBLMOB01"))
    }

    @Test
    fun `falls back to bank party name when no rail token matches`() {
        val result = RemarkParser.parse("Fund Trf to NABIL BANK LTD SOMEBRANCH")
        assertEquals("Nabil Bank Ltd Somebranch", result.merchant)
        assertEquals(RemarkParser.Kind.TRANSFER, result.kind)
    }

    @Test
    fun `preserves QR Payment merchant behavior with QR channel`() {
        val result = RemarkParser.parse("QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU")
        assertEquals("JAWALAKHEL HANKOOK SARANG RESTAU", result.merchant)
        assertEquals("QR", result.channel)
        assertEquals(RemarkParser.Kind.PURCHASE, result.kind)
    }

    @Test
    fun `unknown remark with no rail and no fund transfer pattern is unknown kind`() {
        val result = RemarkParser.parse("Int.Pd:14-04-2026 to 16-07-2026")
        assertEquals(RemarkParser.Kind.UNKNOWN, result.kind)
    }

    @Test
    fun `generic ledger counterparty falls through to the rail name`() {
        // "A/C PAYABLE IBFT" is the bank's internal ledger account, not a payee, so the
        // row must read as an IBFT transfer rather than "A/c Payable Ibft".
        val result = RemarkParser.parse(
            "Fund Trf to A/C PAYABLE IBFT (IN-401573123,Mobile/FUN MOB/SBLMOB01"
        )

        assertEquals("IBFT", result.merchant)
        assertEquals("IBFT", result.channel)
        assertEquals(RemarkParser.Kind.TRANSFER, result.kind)
    }

    @Test
    fun `a real payee that merely contains payable is not rejected`() {
        val result = RemarkParser.parse("Fund Trf to PAYABLE TRADERS PVT LTD")

        assertEquals("Payable Traders Pvt Ltd", result.merchant)
    }

    @Test
    fun `a trailing rail token is not part of the payee's name`() {
        val result = RemarkParser.parse("Fund Trf to NEA ELECTRICITY ESEW")

        assertEquals("Nea Electricity", result.merchant)
        assertEquals("eSewa", result.channel)
    }

    @Test
    fun `the same payee over two rails is one merchant`() {
        val overEsewa = RemarkParser.parse("Fund Trf to DARAZ NEPAL PVT LTD ESEW").merchant
        val overIps = RemarkParser.parse("Fund Trf to DARAZ NEPAL PVT LTD cIPS").merchant

        assertEquals(overEsewa, overIps)
    }

    @Test
    fun `the mobile-banking marker is stripped from the payee too`() {
        val result = RemarkParser.parse("Fund Trf to WORLDLINK COMMUNICATIONS SBLMOB")

        assertEquals("Worldlink Communications", result.merchant)
        assertEquals("Mobile banking", result.channel)
    }

    @Test
    fun `a rail token inside a name is left alone`() {
        // "Atm" here is part of the payee, not the rail that moved the money.
        val result = RemarkParser.parse("Fund Trf to ATM SERVICES NEPAL PVT LTD")

        assertEquals("Atm Services Nepal Pvt Ltd", result.merchant)
    }

    @Test
    fun `an incoming transfer names its payer, whether abbreviated or spelled out`() {
        assertEquals("Acme Technologies Pvt Ltd", RemarkParser.parse("Fund Trf frm ACME TECHNOLOGIES PVT LTD").merchant)
        assertEquals("Acme Technologies Pvt Ltd", RemarkParser.parse("Fund Trf from ACME TECHNOLOGIES PVT LTD").merchant)
    }
}
