 

import java.security.GeneralSecurityException;
import java.util.Optional;
import javax.crypto.Cipher;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.net.util.Base64;

public interface Cryption {
  Optional<byte[]> convert(byte[] input, int mode) throws GeneralSecurityException;

  default Optional<String> encrypt(String plainTextStr) throws GeneralSecurityException {
    return Optional.of(
        Base64.encodeBase64String(
            convert(StringUtils.getBytesUtf8(plainTextStr), Cipher.ENCRYPT_MODE).get()));
  }

  default Optional<String> decrypt(String encryptedStr) throws GeneralSecurityException {
    return Optional.of(
        StringUtils.newStringUtf8(
            convert(Base64.decodeBase64(encryptedStr), Cipher.DECRYPT_MODE).get()));
  }

  public static void main(String[] args) throws GeneralSecurityException {
    // supplementary characters are represented as a pair of char values,
    // the first from the high-surrogates range (\uD800-\uDBFF),
    // the second from the low-surrogates range (\uDC00-\uDFFF).
    System.out.println(
        "is supplymentary characters?" + (Character.isSurrogatePair('\uD801', '\uDC00') == true));
    String[] test = new String[] {"", "comppotal", "中国胤禛", "\uD801\uDC00\u6771"};
    Cryption[] cryptions = {
      AesOfbCipher.getInstance(), AesGcmCipher.INSTANCE, RsaEcbCipher.getInstance()
    };
    System.out.println("===========");
    for (Cryption c : cryptions) {
      for (String input : test) {
        String encrypted = c.encrypt(input).get();
        String decrpted = c.decrypt(encrypted).get();
        if (c instanceof AesGcmCipher) ((AesGcmCipher) c).updateIv();
        System.out.println(String.format("intput: [%s]", input));
        System.out.println(String.format("encrypted:[%s]", encrypted));
        System.out.println(String.format("decrpted:[%s]", decrpted));
        System.out.println(String.format("input and decrpted:%s", input.equals(decrpted)));
        System.out.println("---");
      }
      System.out.println("===========");
    }
  }
}
