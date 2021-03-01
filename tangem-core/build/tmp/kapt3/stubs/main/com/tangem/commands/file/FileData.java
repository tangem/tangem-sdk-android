package com.tangem.commands.file;

import java.lang.System;

@kotlin.Metadata(mv = {1, 4, 1}, bv = {1, 0, 3}, k = 1, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0002\u0007\bB\u000f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u0082\u0001\u0002\t\n\u00a8\u0006\u000b"}, d2 = {"Lcom/tangem/commands/file/FileData;", "Lcom/tangem/commands/file/FirmwareRestrictible;", "data", "", "([B)V", "getData", "()[B", "DataProtectedByPasscode", "DataProtectedBySignature", "Lcom/tangem/commands/file/FileData$DataProtectedBySignature;", "Lcom/tangem/commands/file/FileData$DataProtectedByPasscode;", "tangem-core"})
public abstract class FileData implements com.tangem.commands.file.FirmwareRestrictible {
    @org.jetbrains.annotations.NotNull()
    private final byte[] data = null;
    
    @org.jetbrains.annotations.NotNull()
    public final byte[] getData() {
        return null;
    }
    
    private FileData(byte[] data) {
        super();
    }
    
    @kotlin.Metadata(mv = {1, 4, 1}, bv = {1, 0, 3}, k = 1, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0007\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0013\u0010\b\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0014\u0010\u000e\u001a\u00020\u000fX\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0014\u0010\u0012\u001a\u00020\u000fX\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0011R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015\u00a8\u0006\u0016"}, d2 = {"Lcom/tangem/commands/file/FileData$DataProtectedBySignature;", "Lcom/tangem/commands/file/FileData;", "data", "", "counter", "", "signature", "Lcom/tangem/commands/file/FileDataSignature;", "issuerPublicKey", "([BILcom/tangem/commands/file/FileDataSignature;[B)V", "getCounter", "()I", "getIssuerPublicKey", "()[B", "maxFirmwareVersion", "Lcom/tangem/commands/common/card/FirmwareVersion;", "getMaxFirmwareVersion", "()Lcom/tangem/commands/common/card/FirmwareVersion;", "minFirmwareVersion", "getMinFirmwareVersion", "getSignature", "()Lcom/tangem/commands/file/FileDataSignature;", "tangem-core"})
    public static final class DataProtectedBySignature extends com.tangem.commands.file.FileData {
        @org.jetbrains.annotations.NotNull()
        private final com.tangem.commands.common.card.FirmwareVersion minFirmwareVersion = null;
        @org.jetbrains.annotations.NotNull()
        private final com.tangem.commands.common.card.FirmwareVersion maxFirmwareVersion = null;
        private final int counter = 0;
        @org.jetbrains.annotations.NotNull()
        private final com.tangem.commands.file.FileDataSignature signature = null;
        @org.jetbrains.annotations.Nullable()
        private final byte[] issuerPublicKey = null;
        
        @org.jetbrains.annotations.NotNull()
        @java.lang.Override()
        public com.tangem.commands.common.card.FirmwareVersion getMinFirmwareVersion() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        @java.lang.Override()
        public com.tangem.commands.common.card.FirmwareVersion getMaxFirmwareVersion() {
            return null;
        }
        
        public final int getCounter() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.tangem.commands.file.FileDataSignature getSignature() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final byte[] getIssuerPublicKey() {
            return null;
        }
        
        public DataProtectedBySignature(@org.jetbrains.annotations.NotNull()
        byte[] data, int counter, @org.jetbrains.annotations.NotNull()
        com.tangem.commands.file.FileDataSignature signature, @org.jetbrains.annotations.Nullable()
        byte[] issuerPublicKey) {
            super(null);
        }
    }
    
    @kotlin.Metadata(mv = {1, 4, 1}, bv = {1, 0, 3}, k = 1, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0014\u0010\u0005\u001a\u00020\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\u00020\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\b\u00a8\u0006\u000b"}, d2 = {"Lcom/tangem/commands/file/FileData$DataProtectedByPasscode;", "Lcom/tangem/commands/file/FileData;", "data", "", "([B)V", "maxFirmwareVersion", "Lcom/tangem/commands/common/card/FirmwareVersion;", "getMaxFirmwareVersion", "()Lcom/tangem/commands/common/card/FirmwareVersion;", "minFirmwareVersion", "getMinFirmwareVersion", "tangem-core"})
    public static final class DataProtectedByPasscode extends com.tangem.commands.file.FileData {
        @org.jetbrains.annotations.NotNull()
        private final com.tangem.commands.common.card.FirmwareVersion minFirmwareVersion = null;
        @org.jetbrains.annotations.NotNull()
        private final com.tangem.commands.common.card.FirmwareVersion maxFirmwareVersion = null;
        
        @org.jetbrains.annotations.NotNull()
        @java.lang.Override()
        public com.tangem.commands.common.card.FirmwareVersion getMinFirmwareVersion() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        @java.lang.Override()
        public com.tangem.commands.common.card.FirmwareVersion getMaxFirmwareVersion() {
            return null;
        }
        
        public DataProtectedByPasscode(@org.jetbrains.annotations.NotNull()
        byte[] data) {
            super(null);
        }
    }
}