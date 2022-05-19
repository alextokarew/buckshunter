package com.github.alextokarew.buckshunter

data class AtmPoint(val id: Int, val address: String, val amount: Int, val lat: Double, val lon: Double) {
    override fun toString(): String {
        return "$amount - $address -"
    }
}

