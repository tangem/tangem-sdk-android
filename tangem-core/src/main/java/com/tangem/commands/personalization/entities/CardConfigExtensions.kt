package com.tangem.commands.personalization.entities

import com.tangem.commands.Settings
import com.tangem.commands.SettingsMask
import com.tangem.commands.SettingsMaskBuilder

internal fun CardConfig.createSettingsMask(): SettingsMask {
    val builder = SettingsMaskBuilder()

    if (allowSetPIN1) builder.add(Settings.AllowSetPIN1)
    if (allowSetPIN2) builder.add(Settings.AllowSetPIN2)
    if (useCvc) builder.add(Settings.UseCvc)
    if (isReusable) builder.add(Settings.IsReusable)

    if (useOneCommandAtTime) builder.add(Settings.UseOneCommandAtTime)
    if (useNDEF) builder.add(Settings.UseNDEF)
    if (useDynamicNDEF) builder.add(Settings.UseDynamicNDEF)
    if (disablePrecomputedNDEF) builder.add(Settings.DisablePrecomputedNDEF)

    if (allowUnencrypted) builder.add(Settings.AllowUnencrypted)
    if (allowFastEncryption) builder.add(Settings.AllowFastEncryption)

    if (prohibitDefaultPIN1) builder.add(Settings.ProhibitDefaultPIN1)

    if (useActivation) builder.add(Settings.UseActivation)

    if (useBlock) builder.add(Settings.UseBlock)
    if (smartSecurityDelay) builder.add(Settings.SmartSecurityDelay)

    if (protectIssuerDataAgainstReplay) builder.add(Settings.ProtectIssuerDataAgainstReplay)

    if (prohibitPurgeWallet) builder.add(Settings.ProhibitPurgeWallet)
    if (allowSelectBlockchain) builder.add(Settings.AllowSelectBlockchain)

    if (skipCheckPIN2CVCIfValidatedByIssuer) builder.add(Settings.SkipCheckPIN2CVCIfValidatedByIssuer)
    if (skipSecurityDelayIfValidatedByIssuer) builder.add(Settings.SkipSecurityDelayIfValidatedByIssuer)

    if (skipSecurityDelayIfValidatedByLinkedTerminal) builder.add(Settings.SkipSecurityDelayIfValidatedByLinkedTerminal)
    if (restrictOverwriteIssuerExtraData) builder.add(Settings.RestrictOverwriteIssuerExtraData)

    if (requireTerminalTxSignature) builder.add(Settings.RequireTermTxSignature)

    if (requireTerminalCertSignature) builder.add(Settings.RequireTermCertSignature)

    if (checkPIN3OnCard) builder.add(Settings.CheckPIN3OnCard)

    return builder.build()
}

internal fun CardConfig.createCardId(): String? {
    if (series == null) return null
    if (startNumber <= 0 || (series.length != 2 && series.length != 4)) return null

    val Alf = "ABCDEF0123456789"
    fun checkSeries(series: String): Boolean {
        val containsList = series.filter { Alf.contains(it) }
        return containsList.length == series.length
    }
    if (!checkSeries(series)) return null

    val tail = if (series.length == 2) String.format("%013d", startNumber) else String.format("%011d", startNumber)
    var cardId = (series + tail).replace(" ", "")
    if (cardId.length != 15 || Alf.indexOf(cardId[0]) == -1 || Alf.indexOf(cardId[1]) == -1)
        return null

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
    return cardId.substring(0, 15) + String.format("%d", lunh)
}