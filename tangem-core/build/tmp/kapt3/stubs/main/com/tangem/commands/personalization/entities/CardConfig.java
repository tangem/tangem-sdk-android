package com.tangem.commands.personalization.entities;

import java.lang.System;

/**
 * It is a configuration file with all the card settings that are written on the card
 * during [PersonalizeCommand].
 */
@kotlin.Metadata(mv = {1, 4, 1}, bv = {1, 0, 3}, k = 1, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u001b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0019\n\u0002\u0010\u0012\n\u0002\b\u001c\u0018\u0000 i2\u00020\u0001:\u0001iB\u00ed\u0002\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u0012\b\b\u0002\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u0003\u0012\u0006\u0010\u000b\u001a\u00020\u0003\u0012\u0006\u0010\f\u001a\u00020\u0003\u0012\b\u0010\r\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u000e\u001a\u00020\u0003\u0012\u0006\u0010\u000f\u001a\u00020\t\u0012\u0006\u0010\u0010\u001a\u00020\u0011\u0012\u0006\u0010\u0012\u001a\u00020\u0013\u0012\u0006\u0010\u0014\u001a\u00020\u0015\u0012\u0006\u0010\u0016\u001a\u00020\t\u0012\u0006\u0010\u0017\u001a\u00020\u0011\u0012\u0006\u0010\u0018\u001a\u00020\u0011\u0012\u0006\u0010\u0019\u001a\u00020\u0011\u0012\u0006\u0010\u001a\u001a\u00020\u0011\u0012\u0006\u0010\u001b\u001a\u00020\u0011\u0012\u0006\u0010\u001c\u001a\u00020\u0011\u0012\u0006\u0010\u001d\u001a\u00020\u0011\u0012\u0006\u0010\u001e\u001a\u00020\u0011\u0012\u0006\u0010\u001f\u001a\u00020\u0011\u0012\u0006\u0010 \u001a\u00020\u0011\u0012\u0006\u0010!\u001a\u00020\u0011\u0012\u0006\u0010\"\u001a\u00020\u0011\u0012\u0006\u0010#\u001a\u00020\u0011\u0012\u0006\u0010$\u001a\u00020\u0011\u0012\u0006\u0010%\u001a\u00020\u0011\u0012\u0006\u0010&\u001a\u00020\u0011\u0012\u0006\u0010\'\u001a\u00020\u0011\u0012\u0006\u0010(\u001a\u00020\u0011\u0012\u0006\u0010)\u001a\u00020\u0011\u0012\u0006\u0010*\u001a\u00020\u0011\u0012\u0006\u0010+\u001a\u00020\u0011\u0012\u0006\u0010,\u001a\u00020\u0011\u0012\u0006\u0010-\u001a\u00020\u0011\u0012\u0006\u0010.\u001a\u00020\u0011\u0012\u0006\u0010/\u001a\u00020\t\u0012\u0006\u00100\u001a\u000201\u0012\f\u00102\u001a\b\u0012\u0004\u0012\u00020403\u00a2\u0006\u0002\u00105R\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b6\u00107R\u0011\u0010#\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b8\u00109R\u0011\u0010 \u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b:\u00109R\u0011\u0010\u0018\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b;\u00109R\u0011\u0010\u0019\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b<\u00109R\u0011\u0010\"\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b=\u00109R\u0011\u00100\u001a\u000201\u00a2\u0006\b\n\u0000\u001a\u0004\b>\u0010?R\u0011\u0010-\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b@\u00109R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\bA\u0010BR\u0011\u0010.\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bC\u00109R\u0011\u0010\u0012\u001a\u00020\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\bD\u0010ER\u0011\u0010\u000e\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\bF\u00107R\u0011\u0010&\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bG\u00109R\u0013\u0010\r\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\bH\u00107R\u0011\u0010\u0017\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u00109R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\bI\u00107R\u0011\u0010\u0016\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\bJ\u0010BR\u0017\u00102\u001a\b\u0012\u0004\u0012\u00020403\u00a2\u0006\b\n\u0000\u001a\u0004\bK\u0010LR\u0011\u0010\u000f\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\bM\u0010BR\u0011\u0010\n\u001a\u00020N\u00a2\u0006\b\n\u0000\u001a\u0004\bO\u0010PR\u0011\u0010\u000b\u001a\u00020N\u00a2\u0006\b\n\u0000\u001a\u0004\bQ\u0010PR\u0011\u0010\f\u001a\u00020N\u00a2\u0006\b\n\u0000\u001a\u0004\bR\u0010PR\u0011\u0010%\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bS\u00109R\u0011\u0010!\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bT\u00109R\u0011\u0010$\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bU\u00109R\u0011\u0010,\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bV\u00109R\u0011\u0010+\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bW\u00109R\u0011\u0010*\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bX\u00109R\u0013\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\bY\u00107R\u0011\u0010\u0014\u001a\u00020\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\bZ\u0010[R\u0011\u0010(\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\\\u00109R\u0011\u0010\'\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b]\u00109R\u0011\u0010)\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b^\u00109R\u0011\u0010\u0010\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b_\u00109R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b`\u0010aR\u0011\u0010\u001a\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bb\u00109R\u0011\u0010\u001f\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bc\u00109R\u0011\u0010\u001b\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bd\u00109R\u0011\u0010\u001d\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\be\u00109R\u0011\u0010\u001c\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bf\u00109R\u0011\u0010\u001e\u001a\u00020\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\bg\u00109R\u0011\u0010/\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\bh\u0010B\u00a8\u0006j"}, d2 = {"Lcom/tangem/commands/personalization/entities/CardConfig;", "", "issuerName", "", "acquirerName", "series", "startNumber", "", "count", "", "pin", "pin2", "pin3", "hexCrExKey", "cvc", "pauseBeforePin2", "smartSecurityDelay", "", "curveID", "Lcom/tangem/commands/common/card/EllipticCurve;", "signingMethods", "Lcom/tangem/commands/common/card/masks/SigningMethodMask;", "maxSignatures", "isReusable", "allowSetPIN1", "allowSetPIN2", "useActivation", "useCvc", "useNDEF", "useDynamicNDEF", "useOneCommandAtTime", "useBlock", "allowSelectBlockchain", "prohibitPurgeWallet", "allowUnencrypted", "allowFastEncryption", "protectIssuerDataAgainstReplay", "prohibitDefaultPIN1", "disablePrecomputedNDEF", "skipSecurityDelayIfValidatedByIssuer", "skipCheckPIN2CVCIfValidatedByIssuer", "skipSecurityDelayIfValidatedByLinkedTerminal", "restrictOverwriteIssuerExtraData", "requireTerminalTxSignature", "requireTerminalCertSignature", "checkPIN3OnCard", "createWallet", "walletsCount", "cardData", "Lcom/tangem/commands/common/card/CardData;", "ndefRecords", "", "Lcom/tangem/commands/personalization/entities/NdefRecord;", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IZLcom/tangem/commands/common/card/EllipticCurve;Lcom/tangem/commands/common/card/masks/SigningMethodMask;IZZZZZZZZZZZZZZZZZZZZZZZZILcom/tangem/commands/common/card/CardData;Ljava/util/List;)V", "getAcquirerName", "()Ljava/lang/String;", "getAllowFastEncryption", "()Z", "getAllowSelectBlockchain", "getAllowSetPIN1", "getAllowSetPIN2", "getAllowUnencrypted", "getCardData", "()Lcom/tangem/commands/common/card/CardData;", "getCheckPIN3OnCard", "getCount", "()I", "getCreateWallet", "getCurveID", "()Lcom/tangem/commands/common/card/EllipticCurve;", "getCvc", "getDisablePrecomputedNDEF", "getHexCrExKey", "getIssuerName", "getMaxSignatures", "getNdefRecords", "()Ljava/util/List;", "getPauseBeforePin2", "", "getPin", "()[B", "getPin2", "getPin3", "getProhibitDefaultPIN1", "getProhibitPurgeWallet", "getProtectIssuerDataAgainstReplay", "getRequireTerminalCertSignature", "getRequireTerminalTxSignature", "getRestrictOverwriteIssuerExtraData", "getSeries", "getSigningMethods", "()Lcom/tangem/commands/common/card/masks/SigningMethodMask;", "getSkipCheckPIN2CVCIfValidatedByIssuer", "getSkipSecurityDelayIfValidatedByIssuer", "getSkipSecurityDelayIfValidatedByLinkedTerminal", "getSmartSecurityDelay", "getStartNumber", "()J", "getUseActivation", "getUseBlock", "getUseCvc", "getUseDynamicNDEF", "getUseNDEF", "getUseOneCommandAtTime", "getWalletsCount", "Companion", "tangem-core"})
public final class CardConfig {
    @org.jetbrains.annotations.NotNull()
    private final byte[] pin = null;
    @org.jetbrains.annotations.NotNull()
    private final byte[] pin2 = null;
    @org.jetbrains.annotations.NotNull()
    private final byte[] pin3 = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String issuerName = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String acquirerName = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String series = null;
    private final long startNumber = 0L;
    private final int count = 0;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String hexCrExKey = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String cvc = null;
    private final int pauseBeforePin2 = 0;
    private final boolean smartSecurityDelay = false;
    @org.jetbrains.annotations.NotNull()
    private final com.tangem.commands.common.card.EllipticCurve curveID = null;
    @org.jetbrains.annotations.NotNull()
    private final com.tangem.commands.common.card.masks.SigningMethodMask signingMethods = null;
    private final int maxSignatures = 0;
    private final boolean isReusable = false;
    private final boolean allowSetPIN1 = false;
    private final boolean allowSetPIN2 = false;
    private final boolean useActivation = false;
    private final boolean useCvc = false;
    private final boolean useNDEF = false;
    private final boolean useDynamicNDEF = false;
    private final boolean useOneCommandAtTime = false;
    private final boolean useBlock = false;
    private final boolean allowSelectBlockchain = false;
    private final boolean prohibitPurgeWallet = false;
    private final boolean allowUnencrypted = false;
    private final boolean allowFastEncryption = false;
    private final boolean protectIssuerDataAgainstReplay = false;
    private final boolean prohibitDefaultPIN1 = false;
    private final boolean disablePrecomputedNDEF = false;
    private final boolean skipSecurityDelayIfValidatedByIssuer = false;
    private final boolean skipCheckPIN2CVCIfValidatedByIssuer = false;
    private final boolean skipSecurityDelayIfValidatedByLinkedTerminal = false;
    private final boolean restrictOverwriteIssuerExtraData = false;
    private final boolean requireTerminalTxSignature = false;
    private final boolean requireTerminalCertSignature = false;
    private final boolean checkPIN3OnCard = false;
    private final boolean createWallet = false;
    private final int walletsCount = 0;
    @org.jetbrains.annotations.NotNull()
    private final com.tangem.commands.common.card.CardData cardData = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.tangem.commands.personalization.entities.NdefRecord> ndefRecords = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.tangem.commands.personalization.entities.CardConfig.Companion Companion = null;
    
    @org.jetbrains.annotations.NotNull()
    public final byte[] getPin() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final byte[] getPin2() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final byte[] getPin3() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getIssuerName() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getAcquirerName() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getSeries() {
        return null;
    }
    
    public final long getStartNumber() {
        return 0L;
    }
    
    public final int getCount() {
        return 0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getHexCrExKey() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getCvc() {
        return null;
    }
    
    public final int getPauseBeforePin2() {
        return 0;
    }
    
    public final boolean getSmartSecurityDelay() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.tangem.commands.common.card.EllipticCurve getCurveID() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.tangem.commands.common.card.masks.SigningMethodMask getSigningMethods() {
        return null;
    }
    
    public final int getMaxSignatures() {
        return 0;
    }
    
    public final boolean isReusable() {
        return false;
    }
    
    public final boolean getAllowSetPIN1() {
        return false;
    }
    
    public final boolean getAllowSetPIN2() {
        return false;
    }
    
    public final boolean getUseActivation() {
        return false;
    }
    
    public final boolean getUseCvc() {
        return false;
    }
    
    public final boolean getUseNDEF() {
        return false;
    }
    
    public final boolean getUseDynamicNDEF() {
        return false;
    }
    
    public final boolean getUseOneCommandAtTime() {
        return false;
    }
    
    public final boolean getUseBlock() {
        return false;
    }
    
    public final boolean getAllowSelectBlockchain() {
        return false;
    }
    
    public final boolean getProhibitPurgeWallet() {
        return false;
    }
    
    public final boolean getAllowUnencrypted() {
        return false;
    }
    
    public final boolean getAllowFastEncryption() {
        return false;
    }
    
    public final boolean getProtectIssuerDataAgainstReplay() {
        return false;
    }
    
    public final boolean getProhibitDefaultPIN1() {
        return false;
    }
    
    public final boolean getDisablePrecomputedNDEF() {
        return false;
    }
    
    public final boolean getSkipSecurityDelayIfValidatedByIssuer() {
        return false;
    }
    
    public final boolean getSkipCheckPIN2CVCIfValidatedByIssuer() {
        return false;
    }
    
    public final boolean getSkipSecurityDelayIfValidatedByLinkedTerminal() {
        return false;
    }
    
    public final boolean getRestrictOverwriteIssuerExtraData() {
        return false;
    }
    
    public final boolean getRequireTerminalTxSignature() {
        return false;
    }
    
    public final boolean getRequireTerminalCertSignature() {
        return false;
    }
    
    public final boolean getCheckPIN3OnCard() {
        return false;
    }
    
    public final boolean getCreateWallet() {
        return false;
    }
    
    public final int getWalletsCount() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.tangem.commands.common.card.CardData getCardData() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.tangem.commands.personalization.entities.NdefRecord> getNdefRecords() {
        return null;
    }
    
    public CardConfig(@org.jetbrains.annotations.Nullable()
    java.lang.String issuerName, @org.jetbrains.annotations.Nullable()
    java.lang.String acquirerName, @org.jetbrains.annotations.Nullable()
    java.lang.String series, long startNumber, int count, @org.jetbrains.annotations.NotNull()
    java.lang.String pin, @org.jetbrains.annotations.NotNull()
    java.lang.String pin2, @org.jetbrains.annotations.NotNull()
    java.lang.String pin3, @org.jetbrains.annotations.Nullable()
    java.lang.String hexCrExKey, @org.jetbrains.annotations.NotNull()
    java.lang.String cvc, int pauseBeforePin2, boolean smartSecurityDelay, @org.jetbrains.annotations.NotNull()
    com.tangem.commands.common.card.EllipticCurve curveID, @org.jetbrains.annotations.NotNull()
    com.tangem.commands.common.card.masks.SigningMethodMask signingMethods, int maxSignatures, boolean isReusable, boolean allowSetPIN1, boolean allowSetPIN2, boolean useActivation, boolean useCvc, boolean useNDEF, boolean useDynamicNDEF, boolean useOneCommandAtTime, boolean useBlock, boolean allowSelectBlockchain, boolean prohibitPurgeWallet, boolean allowUnencrypted, boolean allowFastEncryption, boolean protectIssuerDataAgainstReplay, boolean prohibitDefaultPIN1, boolean disablePrecomputedNDEF, boolean skipSecurityDelayIfValidatedByIssuer, boolean skipCheckPIN2CVCIfValidatedByIssuer, boolean skipSecurityDelayIfValidatedByLinkedTerminal, boolean restrictOverwriteIssuerExtraData, boolean requireTerminalTxSignature, boolean requireTerminalCertSignature, boolean checkPIN3OnCard, boolean createWallet, int walletsCount, @org.jetbrains.annotations.NotNull()
    com.tangem.commands.common.card.CardData cardData, @org.jetbrains.annotations.NotNull()
    java.util.List<com.tangem.commands.personalization.entities.NdefRecord> ndefRecords) {
        super();
    }
    
    @kotlin.Metadata(mv = {1, 4, 1}, bv = {1, 0, 3}, k = 1, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0003"}, d2 = {"Lcom/tangem/commands/personalization/entities/CardConfig$Companion;", "", "()V", "tangem-core"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}