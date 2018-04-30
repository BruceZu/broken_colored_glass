
import org.apache.commons.lang.NotImplementedException;

/** <a href https://tools.ietf.org/html/rfc8141> RFC8141 </a> compliant URNs. */
public class URN1841 {
  /**
   * <pre>
   * "
   * DIGIT         =  0-9
   * ALPHA         =  A-Z / a-z
   * alphanum      =  ALPHA / DIGIT
   * ldh           = 　alphanum / "-"
   * NID           = (alphanum) 0*30(ldh) (alphanum)
   * "
   */
  static String NID = "\\p{Alnum}[a-zA-Z0-9\\-]{0,30}\\p{Alnum}";

  /**
   * <pre>
   * "
   * unreserved  =  ALPHA / DIGIT / "-" / "." / "_" / "~"
   * HEXDIG      =  DIGIT / "A" / "B" / "C" / "D" / "E" / "F"
   * pct-encoded =  "%" HEXDIG HEXDIG　;
   * sub-delims  =  "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
   * pchar = unreserved / pct-encoded / sub-delims / ":" / "@"
   * "
   */
  static String PCHAR = "[a-zA-Z0-9\\-\\._~!\\$&'\\(\\)\\*\\+,;=:@]|(%[0-9A-F]{2})";

  /**
   * <pre>
   * "
   * NSS = pchar *(pchar / "/")
   * "
   * @see #PCHAR
   */
  static String NSS = "(" + PCHAR + ")((" + PCHAR + ")|/)*";

  /**
   * <pre>
   * "
   * assigned-name = "urn" ":" NID ":" NSS
   * "
   * The leading scheme (urn:) is case-insensitive
   * @see <a href="https://en.wikipedia.org/wiki/Uniform_Resource_Name">Wiki: Uniform Resource Name</a>
   */
  static String ASSIGNED_NAME = "([uU][rR][nN]):(" + NID + "):(" + NSS + ")";

  /**
   * <pre>
   * "
   * fragment      =       *( pchar / "/" / "?" )
   * "
   */
  static String FRAGMENT = "((" + PCHAR + ")|/|\\?)*";
  /**
   * <pre>
   * "
   * r-component   = pchar *( pchar / "/" / "?" )
   * q-component   = pchar *( pchar / "/" / "?" )
   * "
   */
  static String R_Q_COMPONENT = "(" + PCHAR + ")" + FRAGMENT;
  /**
   * <pre>
   * "
   * rq-components = [ "?+" r-component ] [ "?=" q-component ]
   * "
   * The order is not gurantee as the '?' can be used in fragment, and
   * the '+' and '=' can be used in pchar.
   * @see #R_Q_COMPONENT
   */
  static String RQ_COMPONENTS =
      "((\\?\\+)(" + R_Q_COMPONENT + "))?((\\?=)(" + R_Q_COMPONENT + "))?";

  /**
   * <pre>
   * "
   * f-component   = fragment
   * namestring    = assigned-name  [ rq-components ]  [ "#" f-component ]
   * "
   */
  static String URN_RFC8141 = "^" + ASSIGNED_NAME + RQ_COMPONENTS + "(#" + FRAGMENT + ")?$";

  static boolean isRFC8141URN(String in) {
    return in.matches(URN_RFC8141);
  }

  static boolean isRFC2141URN(String in) {
    // Todo: If end user need it backwards compatible with RFC2141
    throw new NotImplementedException();
  }

  public static boolean isValidURN(String in) {
    return isRFC8141URN(in);
  }
}
