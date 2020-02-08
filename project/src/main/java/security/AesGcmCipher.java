 

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Optional;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Symmetric bulk Cipher AES in GCM mode */
public enum AesGcmCipher implements Cryption {
  INSTANCE;
  private final Logger log = LoggerFactory.getLogger(AesGcmCipher.class);
  private int AES_KEY_SIZE = 256;
  private final int IV_SIZE = 96;
  // must be one of {128, 120, 112, 104, 96}, at least 128 bits length
  private int AUTHEN_TAG_BIT_LENGTH = 128;
  // AEAD (GCM/CCM) mode
  // PKCS5Padding for symmetric encryption.
  private String ALGO_TRANSFORMATION_STRING = "AES/GCM/PKCS5Padding";
  // use same key, IV, GCM for encrypt and decrypt.
  private byte[] aaData = "aadata.compnet.com".getBytes();
  private SecretKey aesKey;
  private byte iv[] = new byte[IV_SIZE];
  private GCMParameterSpec gcmParamSpec;
  private Cipher cipher;
  // It's most secure to use default algorithm and seeding in Unix-like OS or Windows.
  // Explicitly seeded SHA1PRNG is predictable and not secure
  private SecureRandom secRandom = new SecureRandom();

  private AesGcmCipher() {
    try {
      KeyGenerator keygen = KeyGenerator.getInstance("AES");
      // Install JCE Unlimited Strength explicitly for version < Oracle Java 8 Update 151.
      // In openJDK it is enabled by default.
      keygen.init(AES_KEY_SIZE);
      aesKey = keygen.generateKey();
      secRandom.nextBytes(iv);
      gcmParamSpec = new GCMParameterSpec(AUTHEN_TAG_BIT_LENGTH, iv);
      cipher = Cipher.getInstance(ALGO_TRANSFORMATION_STRING, "SunJCE");
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | NoSuchProviderException e) {
      log.error("Failed to initial Cipher object", e);
    }
  }

  public void updateIv() {
    secRandom.nextBytes(iv);
    gcmParamSpec = new GCMParameterSpec(AUTHEN_TAG_BIT_LENGTH, iv);
  }

  @Override
  public Optional<byte[]> convert(byte[] input, int mode) throws GeneralSecurityException {
    String task = mode == Cipher.ENCRYPT_MODE ? "encrpt" : "decrypt";
    if (cipher == null) {
      log.error(String.format("Failed to %s %s: Cipher is not ready", task, input));
      return Optional.empty();
    }

    try {
      synchronized (cipher) {
        cipher.init(mode, aesKey, gcmParamSpec, secRandom);
        // add AAD tag data before encrypting
        cipher.updateAAD(aaData);
        return Optional.of(cipher.doFinal(input));
      }
    } catch (InvalidKeyException
        | InvalidAlgorithmParameterException
        | IllegalBlockSizeException
        | BadPaddingException e) {
      String error = String.format("Failed to %s %s:", task, input);
      log.error(error, e);
      throw new GeneralSecurityException(error, e);
    }
  }
}
