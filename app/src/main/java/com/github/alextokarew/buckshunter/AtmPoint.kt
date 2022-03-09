package com.github.alextokarew.buckshunter

data class AtmPoint(val id: String, val address: String, val amount: Int, val lat: Double, val lon: Double) {
    override fun toString(): String {
        return "$id;$amount;$lat;$lon;$address"
    }

    companion object {
        fun fromString(str: String): AtmPoint {
            val items = str.split(';', ignoreCase = false, limit = 5)
            return AtmPoint(items[0], items[4], items[1].toInt(), items[2].toDouble(), items[3].toDouble())
        }
    }
}

