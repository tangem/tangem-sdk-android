package com.tangem.operations.personalization.config

@Suppress("ComplexMethod", "ImplicitDefaultLocale", "MagicNumber")
internal object CardIdBuilder {

    private const val ALF = "ABCDEF0123456789"

    fun createCardId(config: CardConfig, cardNumber: Long = 0): String? {
        return createCardId(
            series = config.series,
            startNumber = config.startNumber,
            cardNumber = cardNumber,
            numberFormat = config.numberFormat,
        )
    }

    fun createCardId(config: CardConfigV8, cardNumber: Long = 0): String? {
        return createCardId(
            series = config.series,
            startNumber = config.startNumber,
            cardNumber = cardNumber,
            numberFormat = config.numberFormat,
        )
    }

    fun createCardId(
        series: String?,
        startNumber: Long,
        cardNumber: Long = 0,
        numberFormat: String = "",
    ): String? {
        if (series == null || startNumber < 0 || cardNumber < 0 || !checkSeries(series)) {
            return null
        }

        return if (numberFormat.isEmpty()) {
            createSimpleCardId(series, startNumber + cardNumber)
        } else {
            createFormattedCardId(series, startNumber, cardNumber, numberFormat)
        }
    }

    private fun createSimpleCardId(series: String, startNumber: Long): String? {
        val minDigits = if (series.length == 2) 13 else 11
        val tail = startNumber.toString().padStart(minDigits, '0')
        val cardId = series + tail
        return completeCardId(cardId)
    }

    private fun createFormattedCardId(
        series: String,
        startNumber: Long,
        cardNumber: Long,
        numberFormat: String,
    ): String? {
        if (numberFormat.isNotEmpty() && !numberFormat.all { it == 'N' || it == 'R' || ALF.contains(it) }) {
            return null
        }

        val tailLength = 15 - series.length
        if (numberFormat.length != tailLength) return null

        val actualNumber = startNumber + cardNumber
        if (actualNumber.toString().length > tailLength) return null

        val paddedNumber = actualNumber.toString().padStart(tailLength, '0')
        val numberChars = paddedNumber.reversed().iterator()
        val randomChars = numberFormat.filter { it == 'R' }
            .map { ('0'.code + (0..9).random()).toChar() }
            .reversed().iterator()

        val tail = numberFormat.reversed().map { char ->
            when (char) {
                'N' -> if (numberChars.hasNext()) numberChars.next() else '0'
                'R' -> if (randomChars.hasNext()) randomChars.next() else '0'
                else -> char
            }
        }.reversed().joinToString("")

        return completeCardId(series + tail)
    }

    private fun completeCardId(cardId: String): String? {
        if (cardId.length != 15) return null
        if (ALF.indexOf(cardId[0]) == -1 || ALF.indexOf(cardId[1]) == -1) return null

        val withZero = cardId + "0"
        val length = withZero.length
        var sum = 0
        for (i in 0 until length) {
            val cDigit = withZero[length - i - 1]
            var digit = if (cDigit in '0'..'9') cDigit - '0' else cDigit - 'A'
            if (i % 2 == 1) digit *= 2
            sum += if (digit > 9) digit - 9 else digit
        }
        val luhn = (10 - sum % 10) % 10
        return cardId + luhn.toString()
    }

    private fun checkSeries(series: String): Boolean {
        if (series.length != 2 && series.length != 4) return false
        return series.all { ALF.contains(it) }
    }
}