 

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Optional;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Symmetric bulk Cipher AES in OFB mode */
public class AesOfbCipher implements Cryption {
  private static final AesOfbCipher INSTANCE = new AesOfbCipher();
  private final Logger log = LoggerFactory.getLogger(AesOfbCipher.class);
  private char[] rawKey = "compnetPortalRawKey.compnet.com".toCharArray();
  // PKCS5Padding for symmetric encryption.
  // There is a limit on how much plain text can be safely encrypted using a single (key/IV) pair in
  // CBC and CTR modes.
  private final String TRANSFORMATION = "AES/OFB32/PKCS5Padding";

  // must be 16 bytes long
  // The randomness source of an IV comes from the IvParameterSpec class
  private byte[] initializationVectors =
      new byte[] {-25, -65, 86, -101, -90, 92, 126, -87, -45, -110, -19, 5, 72, 60, -117, -128};
  private final IvParameterSpec ivSpec = new IvParameterSpec(initializationVectors);
  private SecretKeySpec secretKeySpec;
  private Cipher cipher;

  public static AesOfbCipher getInstance() {
    return INSTANCE;
  }

  private AesOfbCipher() {
    try {
      SecretKey key = Pbkdf2Key.getKey(rawKey);
      secretKeySpec = new SecretKeySpec(key.getEncoded(), "AES");
      // CBC mode and PKCS5Padding padding scheme can lead to padding oracle attacks
      cipher = Cipher.getInstance(TRANSFORMATION, "SunJCE");
    } catch (GeneralSecurityException e) {
      log.error("Failed to initial Cipher object", e);
    }
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
        cipher.init(mode, secretKeySpec, ivSpec);
        byte[] r = cipher.doFinal(input);
        return Optional.of(r);
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
