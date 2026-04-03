package com.sotpn.transaction;

import com.sotpn.wallet.WalletInterface;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : MockWallet.java
 * Package  : com.sotpn.transaction (test)
 */
public class MockWallet implements WalletInterface {

    private String publicKey = "pub_" + UUID.randomUUID().toString().substring(0, 8);
    public long balance = 200_000L;
    
    public boolean lockShouldSucceed = true;
    public boolean signShouldSucceed = true;
    public boolean verifyShouldSucceed = true;

    public TokenInfo tokenToReturn = new TokenInfo(
            "tok_mock_001",
            50_000L,
            System.currentTimeMillis() + 60_000
    );

    public volatile boolean tokenWasLocked   = false;
    public volatile boolean tokenWasUnlocked = false;
    public volatile boolean tokenWasSpent    = false;
    public volatile boolean tokenWasReceived = false;

    // Call counters for testing
    public final AtomicInteger signCallCount   = new AtomicInteger(0);
    public final AtomicInteger verifyCallCount = new AtomicInteger(0);

    private final Map<String, Boolean> lockedTokens = new ConcurrentHashMap<>();
    private final Map<String, Boolean> spentTokens  = new ConcurrentHashMap<>();

    public void setPublicKey(String key) { this.publicKey = key; }

    @Override
    public String getPublicKey() {
        return publicKey;
    }

    @Override
    public long getBalance() {
        return balance;
    }

    @Override
    public TokenInfo getSpendableToken(long requiredValue) {
        if (tokenToReturn == null) return null;
        if (tokenToReturn.value < requiredValue) return null;
        return tokenToReturn;
    }

    @Override
    public synchronized boolean lockToken(String tokenId) {
        if (!lockShouldSucceed) return false;
        if (lockedTokens.containsKey(tokenId)) return false;
        lockedTokens.put(tokenId, true);
        tokenWasLocked = true;
        return true;
    }

    @Override
    public synchronized void unlockToken(String tokenId) {
        lockedTokens.remove(tokenId);
        tokenWasUnlocked = true;
    }

    @Override
    public synchronized void markTokenSpent(String tokenId, String proof) {
        spentTokens.put(tokenId, true);
        tokenWasSpent = true;
    }

    @Override
    public synchronized void receiveToken(String tokenId, String senderPublicKey, String proof) {
        tokenWasReceived = true;
    }

    @Override
    public String signTransaction(String dataToSign) {
        signCallCount.incrementAndGet();
        if (!signShouldSucceed) {
            // Throwing exception simulates real-world wallet failures (e.g. Keystore locked)
            // and allows PreparePhaseHandler to trigger the rollback logic correctly.
            throw new RuntimeException("Wallet signing failed");
        }
        // Deterministic signature based on OUR identity and the data
        return "sig:" + publicKey + ":" + dataToSign.hashCode();
    }

    @Override
    public boolean verifySignature(String data, String signature, String signerPublicKey) {
        verifyCallCount.incrementAndGet();
        if (!verifyShouldSucceed) return false;
        
        // Validation logic: must match the format sig:[claimed_signer]:[data_hash]
        String expectedSig = "sig:" + signerPublicKey + ":" + data.hashCode();
        return expectedSig.equals(signature);
    }

    @Override
    public String generateNonce() {
        return UUID.randomUUID().toString();
    }

    public boolean isTokenLocked(String tokenId) {
        return lockedTokens.containsKey(tokenId);
    }

    public void reset() {
        balance             = 200_000L;
        lockShouldSucceed   = true;
        signShouldSucceed   = true;
        verifyShouldSucceed = true;
        tokenWasLocked      = false;
        tokenWasUnlocked    = false;
        tokenWasSpent       = false;
        tokenWasReceived    = false;
        signCallCount.set(0);
        verifyCallCount.set(0);
        lockedTokens.clear();
        spentTokens.clear();
    }
}
