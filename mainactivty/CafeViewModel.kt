
package com.example.kasihsayangcafe_tugaspemogramanmobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.math.roundToInt

data class MenuItem(
    val id: Int,
    val name: String,
    val price: Int,
    val emoji: String
)

object Catalog {
    val items = listOf(
        MenuItem(1, "Americano", price = null, "☕"),
        MenuItem(2, "Cappuccino", 20000, "☕"),
        MenuItem(3, "Matcha Latte", 25000, "🍵"),
        MenuItem(4, "Ice Lemon Tea", 15000, "🍋"),
        MenuItem(5, "Ice Leci Tea", 17000, "🧋"),
        MenuItem(6, "Mineral Water", 10000, "💧"),
        MenuItem(7, "Ice Tea", 12000, "🧊"),
        MenuItem(8, "Milkshake Coklat / Stroberi", 15000, "🥤"),
        MenuItem(9, "Spaghetti Carbonara", 37000, "🍝"),
        MenuItem(10, "Mix Platter", 40000, "🍟"),
        MenuItem(11, "Onion Ring", 20000, "🧅"),
        MenuItem(12, "Cheesecake", 25000, "🍰"),
        MenuItem(13, "Churros", 10000, "🥨"),
        MenuItem(14, "Burger", 25000, "🍔"),
        MenuItem(15, "Pisang Coklat Keju", 17000, "🍌"),
        MenuItem(16,"Cake",25000,"🍰")
    )
}

data class Member(
    val name: String,
    val email: String,
    val phone: String
)

data class PriceSummary(
    val total: Int,
    val percent: Int,
    val discount: Int,
    val finalPrice: Int
)

// PERHITUNGAN DISKON MEMBERSHIP
fun calculatePrice(
    total: Int,
    isMember: Boolean
): PriceSummary {

    val percent = when {
        !isMember -> 0
        total > 600000 -> 10
        total > 300000 -> 7
        total > 200000 -> 6
        total > 100000 -> 4
        else -> 0
    }

    val discount = (total * percent / 100.0).roundToInt()

    return PriceSummary(
        total = total,
        percent = percent,
        discount = discount,
        finalPrice = total - discount
    )
}

class CafeViewModel : ViewModel() {

    // DATA KERANJANG
    var cart by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set

    // DATA MEMBERSHIP
    var member by mutableStateOf<Member?>(null)
        private set

    val isMember: Boolean
        get() = member != null

    // TAMBAH MENU
    fun addItem(id: Int) {
        val quantity = cart[id] ?: 0
        cart = cart + (id to quantity + 1)
    }

    // KURANGI MENU
    fun removeItem(id: Int) {
        val quantity = cart[id] ?: 0

        cart = if (quantity <= 1) {
            cart - id
        } else {
            cart + (id to quantity - 1)
        }
    }

    // DAFTAR MEMBERSHIP
    fun registerMember(
        name: String,
        email: String,
        phone: String
    ) {
        member = Member(
            name = name,
            email = email,
            phone = phone
        )
    }

    // JUMLAH SELURUH ITEM
    val cartCount: Int
        get() = cart.values.sum()

    // TOTAL HARGA
    val total: Int
        get() = Catalog.items.sumOf { item ->
            item.price * (cart[item.id] ?: 0)
        }

    // DAFTAR ITEM DALAM KERANJANG
    val cartItems: List<Pair<MenuItem, Int>>
        get() = Catalog.items.mapNotNull { item ->
            val quantity = cart[item.id] ?: 0

            if (quantity > 0) item to quantity else null
        }

    // RINGKASAN PEMBAYARAN
    val priceSummary: PriceSummary
        get() = calculatePrice(total, isMember)

    // SELESAIKAN PESANAN
    fun clearCart() {
        cart = emptyMap()
    }
}
