package com.example.pleoshellocar.navigation

import android.content.Context
import com.example.pleoshellocar.shared.domain.CompletedOrderSnapshot
import com.example.pleoshellocar.shared.domain.CompletedOrderStore
import com.example.pleoshellocar.shared.domain.Order

/** Persists only store/menu IDs and quantities from a completed demo order. */
class PreferencesCompletedOrderStore(context: Context) : CompletedOrderStore {
    private val prefs = context.getSharedPreferences("completed-order", Context.MODE_PRIVATE)

    override fun last(): CompletedOrderSnapshot? {
        val store = prefs.getString("store", null) ?: return null
        val count = prefs.getInt("count", 0)
        if (count !in 1..4) return null
        val lines = (0 until count).map { index ->
            val item = prefs.getString("item-$index", null) ?: return null
            val quantity = prefs.getInt("quantity-$index", 0)
            if (quantity !in 1..9) return null
            item to quantity
        }
        return CompletedOrderSnapshot(store, lines)
    }

    override fun save(order: Order) {
        if (!order.terminal || order.status != com.example.pleoshellocar.shared.domain.OrderStatus.COMPLETED) return
        prefs.edit().clear().putString("store", order.store.id).putInt("count", order.lines.size).apply {
            order.lines.forEachIndexed { index, line ->
                putString("item-$index", line.item.id)
                putInt("quantity-$index", line.quantity)
            }
        }.apply()
    }
}
