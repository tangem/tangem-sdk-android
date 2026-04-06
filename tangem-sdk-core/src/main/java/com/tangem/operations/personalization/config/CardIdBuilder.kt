package com.tangem.operations.personalization.config

@Suppress("ComplexMethod", "ImplicitDefaultLocale", "MagicNumber")
internal object CardIdBuilder {

    fun createCardId(config: CardConfig): String? {
        val startNumber = config.startNumber
        val series = config.series
        return createCardId(startNumber, series)
    }

    fun createCardId(config: CardConfigV8): String? {
        val startNumber = config.startNumber
        val series = config.series
        return createCardId(startNumber, series)
    }

    private fun createCardId(startNumber: Long, series: String?): String? {
        if (startNumber <= 0 || series?.length != 2 && series?.length != 4) return null

        val alf = "ABCDEF0123456789"
        fun checkSeries(series: String): Boolean {
            val containsList = series.filter { alf.contains(it) }
            return containsList.length == series.length
        }
        if (!checkSeries(series)) return null

        val tail = if (series.length == 2) String.format("%013d", startNumber) else String.format("%011d", startNumber)
        var cardId = (series + tail).replace(" ", "")
        if (cardId.length != 15 || alf.indexOf(cardId[0]) == -1 || alf.indexOf(cardId[1]) == -1) return null

        cardId += "0"
        val length = cardId.length
        var sum = 0
        for (i in 0 until length) {
            // get digits in reverse order
            var digit: Int
            val cDigit = cardId[length - i - 1]
            digit = if (cDigit in '0'..'9') cDigit - '0' else cDigit - 'A'

            // every 2nd number multiply with 2
            if (i % 2 == 1) digit *= 2
            sum += if (digit > 9) digit - 9 else digit
        }
        val lunh = (10 - sum % 10) % 10
        return cardId.substring(startIndex = 0, endIndex = 15) + String.format("%d", lunh)
    }
}