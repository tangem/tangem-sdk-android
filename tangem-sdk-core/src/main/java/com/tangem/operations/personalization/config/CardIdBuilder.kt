package com.tangem.operations.personalization.config

@Suppress("ComplexMethod", "ImplicitDefaultLocale", "MagicNumber", "LongParameterList")
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
            useLuhn = config.useLuhn ?: true,
        )
    }

    fun createCardId(
        series: String?,
        startNumber: Long,
        cardNumber: Long = 0,
        numberFormat: String = "",
        useLuhn: Boolean = true,
    ): String? {
        // With Luhn the 16th CID character is the appended check digit, so the body is 15 characters.
        // Without Luhn the body occupies the entire 16-character CID.
        val bodyLength = if (useLuhn) 15 else 16

        val checkNumber = startNumber < 0 || cardNumber < 0
        val checkSeries = series == null || !checkSeries(series, bodyLength)
        if (checkSeries || checkNumber) {
            return null
        }

        return if (numberFormat.isEmpty()) {
            createSimpleCardId(series, startNumber + cardNumber, bodyLength, useLuhn)
        } else {
            createFormattedCardId(series, startNumber, cardNumber, numberFormat, bodyLength, useLuhn)
        }
    }

    private fun createSimpleCardId(series: String, startNumber: Long, bodyLength: Int, useLuhn: Boolean): String? {
        val minDigits = bodyLength - series.length
        val tail = startNumber.toString().padStart(minDigits, '0')
        val cardId = series + tail
        return completeCardId(cardId, useLuhn)
    }

    private fun createFormattedCardId(
        series: String,
        startNumber: Long,
        cardNumber: Long,
        numberFormat: String,
        bodyLength: Int,
        useLuhn: Boolean,
    ): String? {
        val letterCondition = fun(char: Char) = char == 'N' || char == 'R' || ALF.contains(char)
        if (numberFormat.isNotEmpty() && !numberFormat.all { letterCondition(it) }) {
            return null
        }

        val tailLength = bodyLength - series.length
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

        return completeCardId(series + tail, useLuhn)
    }

    private fun completeCardId(cardId: String, useLuhn: Boolean): String? {
        if (cardId.length != (if (useLuhn) 15 else 16)) return null
        if (ALF.indexOf(cardId[0]) == -1 || ALF.indexOf(cardId[1]) == -1) return null

        if (!useLuhn) return cardId

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

    private fun checkSeries(series: String, bodyLength: Int): Boolean {
        // The series must leave room for at least one number digit in the CID body
        if (series.length !in 1 until bodyLength) return false
        return series.all { ALF.contains(it) }
    }
}