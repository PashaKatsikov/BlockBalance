package bp.goalsgames.blockbalance.ui

import kotlin.math.abs

/** Compact credit formatting so long balances still fit the chips. */
fun formatCredits(value: Long): String {
    val sign = if (value < 0) "-" else ""
    val amount = abs(value)
    return when {
        amount < 10_000 -> sign + groupDigits(amount)
        amount < 1_000_000 -> sign + trimTenths(amount / 1_000.0) + "K"
        else -> sign + trimTenths(amount / 1_000_000.0) + "M"
    }
}

fun groupDigits(value: Long): String {
    val text = value.toString()
    if (text.length <= 3) return text
    val builder = StringBuilder()
    var counter = 0
    for (index in text.lastIndex downTo 0) {
        builder.append(text[index])
        counter++
        if (counter % 3 == 0 && index > 0) builder.append(' ')
    }
    return builder.reverse().toString()
}

private fun trimTenths(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

/** Multipliers always show two decimals: what is shown is what is paid. */
fun formatMultiplier(value: Double): String {
    val scaled = Math.round(value * 100.0)
    val whole = scaled / 100
    val cents = (scaled % 100).toInt()
    val padded = if (cents < 10) "0$cents" else cents.toString()
    return "$whole.$padded"
}
