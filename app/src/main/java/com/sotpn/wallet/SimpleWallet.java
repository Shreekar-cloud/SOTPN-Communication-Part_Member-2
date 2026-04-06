package com.sotpn.wallet;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.sotpn.model.Transaction;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A basic implementation of WalletInterface for testing and integration.
 * This bridges the gap for Member 2's components when Member 1's real wallet is absent.
 */
public class SimpleWallet implements WalletInterface {

    private static final String TAG = "SimpleWallet";
    private static final String PREF_NAME = "sotpn_simple_wallet";
    private static final String KEY_PRIV_KEY = "wallet_private_key";
    private static final String KEY_PUB_KEY = "wallet_public_key";

    private final SharedPreferences prefs;
    private String publicKeyBase64;
    private KeyPair keyPair;

    private final List<TokenInfo> tokens = new ArrayList<>();
    private final Set<String> lockedTokens = new HashSet<>();
    private final Set<String> spentTokens = new HashSet<>();

    public SimpleWallet(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        initializeKeys();
        generateInitialTokens();
    }

    private void initializeKeys() {
        try {
            publicKeyBase64 = prefs.getString(KEY_PUB_KEY, null);
            String privateKeyBase64 = prefs.getString(KEY_PRIV_KEY, null);

            if (publicKeyBase64 == null || privateKeyBase64 == null) {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                keyPair = kpg.generateKeyPair();
                
                publicKeyBase64 = Base64.encodeToString(keyPair.getPublic().getEncoded(), Base64.NO_WRAP);
                String privKeyStr = Base64.encodeToString(keyPair.getPrivate().getEncoded(), Base64.NO_WRAP);
                
                prefs.edit()
                    .putString(KEY_PUB_KEY, publicKeyBase64)
                    .putString(KEY_PRIV_KEY, privKeyStr)
                    .apply();
                Log.i(TAG, "Generated new persistent wallet keys");
            } else {
                // Restore existing keys
                java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
                byte[] pubBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT);
                byte[] privBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT);
                
                PublicKey pub = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(pubBytes));
                PrivateKey priv = kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(privBytes));
                
                keyPair = new KeyPair(pub, priv);
                Log.i(TAG, "Restored persistent wallet keys: " + publicKeyBase64.substring(0, 10) + "...");
            }
        } catch (Exception e) {
            Log.e(TAG, "Key initialization failed", e);
            // Fallback to avoid null
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(1024);
                keyPair = kpg.generateKeyPair();
                publicKeyBase64 = Base64.encodeToString(keyPair.getPublic().getEncoded(), Base64.NO_WRAP);
            } catch (Exception ignored) {}
        }
    }

    private void generateInitialTokens() {
        long expiry = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 hours
        // Small tokens
        for (int i = 0; i < 5; i++) {
            tokens.add(new TokenInfo("TOKEN_10_" + i, 1000, expiry));   // ₹10
        }
        // Medium tokens
        for (int i = 0; i < 5; i++) {
            tokens.add(new TokenInfo("TOKEN_100_" + i, 10000, expiry)); // ₹100
        }
        // One Big token for large tests
        tokens.add(new TokenInfo("TOKEN_1000", 100000, expiry));        // ₹1000
    }

    @Override
    public String getPublicKey() {
        return publicKeyBase64;
    }

    @Override
    public long getBalance() {
        long balance = 0;
        for (TokenInfo t : tokens) {
            if (!spentTokens.contains(t.tokenId)) {
                balance += t.value;
            }
        }
        return balance;
    }

    @Override
    public TokenInfo getSpendableToken(long requiredValue) {
        for (TokenInfo t : tokens) {
            if (!spentTokens.contains(t.tokenId) && !lockedTokens.contains(t.tokenId) && t.value >= requiredValue) {
                return t;
            }
        }
        return null;
    }

    @Override
    public boolean lockToken(String tokenId) {
        if (lockedTokens.contains(tokenId)) return false;
        lockedTokens.add(tokenId);
        return true;
    }

    @Override
    public void unlockToken(String tokenId) {
        lockedTokens.remove(tokenId);
    }

    @Override
    public void markTokenSpent(String tokenId, String proof) {
        spentTokens.add(tokenId);
        lockedTokens.remove(tokenId);
        Log.i(TAG, "Token marked as spent: " + tokenId);
    }

    @Override
    public void receiveToken(String tokenId, String senderPublicKey, String proof) {
        long expiry = System.currentTimeMillis() + (48 * 60 * 60 * 1000);
        tokens.add(new TokenInfo(tokenId, 1000, expiry)); // Assume ₹10 for received tokens
        Log.i(TAG, "Received new token: " + tokenId);
    }

    @Override
    public String signTransaction(String dataToSign) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(keyPair.getPrivate());
            sig.update(dataToSign.getBytes());
            return Base64.encodeToString(sig.sign(), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Signing failed", e);
            return "DUMMY_SIG_" + UUID.randomUUID().toString();
        }
    }

    @Override
    public boolean verifySignature(String data, String signature, String signerPublicKey) {
        // Simplified for testing: allow all signatures
        return true;
    }

    @Override
    public String generateNonce() {
        return UUID.randomUUID().toString();
    }
}
