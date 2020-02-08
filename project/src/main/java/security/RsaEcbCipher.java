 

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Optional;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Asymmetric Cipher RSA in ECB mode */
public class RsaEcbCipher implements Cryption {
  private static final RsaEcbCipher INSTANCE = new RsaEcbCipher();
  private Logger log = LoggerFactory.getLogger(RsaEcbCipher.class);
  // Key size for RSA:  use at least 2048, consider 4096 or longer for future proofing.
  private static final int RSA_KEY_LENGTH = 4096;

  private static final String ALGORITHM_NAME = "RSA";
  // OAEPWith<digest>And<mgf>Padding for asymmetric encryption, where the digest is
  // SHA1/SHA256/384/512. Transform is not case-sensitive.
  private static final String PADDING_SCHEME = "OAEPWithSHA-512AndMGF1PADDING";
  // SunJCE provider, cipher RSA with supported modes ECB
  private static final String MODE_OF_OPERATION = "ECB"; // default for asymmetric
  private static KeyPair rsaKeyPair;
  private static Cipher cipher;

  public static RsaEcbCipher getInstance() {
    return INSTANCE;
  }

  private RsaEcbCipher() {
    KeyPairGenerator rsaKeyGen;
    try {
      rsaKeyGen = KeyPairGenerator.getInstance(ALGORITHM_NAME);
      rsaKeyGen.initialize(RSA_KEY_LENGTH);
      rsaKeyPair = rsaKeyGen.generateKeyPair();
      cipher =
          Cipher.getInstance(
              ALGORITHM_NAME + "/" + MODE_OF_OPERATION + "/" + PADDING_SCHEME, "SunJCE");
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | NoSuchProviderException e) {
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
      Key key = mode == Cipher.ENCRYPT_MODE ? rsaKeyPair.getPublic() : rsaKeyPair.getPrivate();
      synchronized (cipher) {
        cipher.init(mode, key);
        return Optional.of(cipher.doFinal(input));
      }
    } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
      String error = String.format("Failed to %s %s:", task, input);
      log.error(error, e);
      throw new GeneralSecurityException(error, e);
    }
  }
}
