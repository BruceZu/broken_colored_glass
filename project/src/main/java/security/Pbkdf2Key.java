 
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * <pre>
 * PBKDF2 key generation. It also can be used in encoding passwords where the
 * BCrypt is preferred. E.g. {@link BCryptPasswordEncoder} <a href=
 * "https://hashcat.net/wiki/#howtos_videos_papers_articles_etc_in_the_wild"> A
 * cheat-sheet for password crackers</a>.
 */
public class Pbkdf2Key {
  // PDKDF for key generation or Password Based Encryption (PBE): use SHA2 algorithms,
  // a salt value of at least 64 bits and iteration count of 10,000.
  private static String PDKDF_ALGORITHM = "PBKDF2WithHmacSHA512";
  // a minimum of 10000 iterations is recommended. See rfc2898
  private static int ITERATION_COUNT = 65536;
  // Key sizes: use AES 256 if you can, else 128 is secure enough for time being.
  private static int DERIVED_KEY_LENGTH = 256;
  // at least 64 bit
  private static byte[] SALT =
      new byte[] {
        -74, 80, 95, -28, 53, -41, 89, -84, -93, -2, -91, -10, -88, -72, -97, -2, 11, -37, 10, -17,
        68, -39, -16, -58, -54, -60, 8, -59, 84, -15, -11, -8, 58, 2, -52, 55, -21, 7, -124, -21,
        88, -93, 96, -119, 118, 15, -117, 125, -117, 14, -53, -78, 0, 78, 9, 98, 97, 47, -117, 74,
        125, -103, 93, 94, 6, -111, 40, -87, 30, 27, 48, -118, -50, -2, 103, -28, 25, 13, 107, 96,
        86, -105, -12, -93, -95, 13, -106, 111, 59, -69, 6, -4, 39, -61, -14, 108, -77, -116, -99,
        -117, 32, 3, 123, -50, -24, -48, -123, -1, -37, -70, 34, -53, -100, 109, 38, -12, 73, 108,
        86, 36, -37, 114, -56, -47, 110, 62, -2, -34
      };

  public static SecretKey getKey(char[] password) throws GeneralSecurityException {
    try {
      PBEKeySpec keySpec = new PBEKeySpec(password, SALT, ITERATION_COUNT, DERIVED_KEY_LENGTH);
      SecretKeyFactory pbkdfKeyFactory = SecretKeyFactory.getInstance(PDKDF_ALGORITHM);
      return pbkdfKeyFactory.generateSecret(keySpec);
    } catch (InvalidKeySpecException
        | NullPointerException
        | IllegalArgumentException
        | NoSuchAlgorithmException e) {
      throw new GeneralSecurityException("Failed to create PBKDF2WithHmacSHA512 key", e);
    }
  }
}
