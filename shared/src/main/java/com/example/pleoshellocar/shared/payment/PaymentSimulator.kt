package com.example.pleoshellocar.shared.payment

interface PaymentSimulator { fun approve(orderId: String): Boolean }
/** No network, financial credentials, tokens, or real charge. */
class MockPaymentSimulator(var failNext: Boolean = false) : PaymentSimulator {
    override fun approve(orderId: String): Boolean {
        val success = !failNext
        failNext = false
        return success
    }
}
