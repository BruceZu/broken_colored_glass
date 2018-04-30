
import static URN1841.ASSIGNED_NAME;
import static URN1841.NID;
import static URN1841.NSS;
import static URN1841.PCHAR;
import static URN1841.RQ_COMPONENTS;
import static URN1841.URN_RFC8141;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

public class URN1841Test {
  private static Logger log = LoggerFactory.getLogger(TestURNValidator.class);
  // University Washington cases:
  private static String IDP_ENTITY_ID_1 = "urn:mace:incommon:washington.edu";
  private static String IDP_ENTITY_ID_2 = "urn:oasis:names:specification:docbook:dtd:xml:4.1.2";

  private static Collection<String> DIGITS =
      Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");

  /**
   * <pre>
   * "
   * HEXDI = DIGIT / "A" / "B" / "C" / "D" / "E" / "F"
   * "
   */
  private static String[] HEXDIG =
      new String[] {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};

  /**
   * <pre>
   * "
   * sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
   * "
   */
  private static Collection<String> SUB_DELIMS =
      Arrays.asList("!", "$", "&", "'", "(", ")", "*", "+", ",", ";", "=");

  /**
   * <pre>
   * "
   * ALPHA = A-Z / a-z
   * "
   */
  private static Collection<String> ALPHA;

  /**
   * <pre>
   * "
   * DIGIT       =  0-9
   * ALPHA       =  A-Z / a-z
   * alphanum    =  ALPHA / DIGIT
   * "
   */
  private static Collection<String> ALPHANUM;

  /**
   * <pre>
   * "
   * DIGIT       =  0-9
   * ALPHA       =  A-Z / a-z
   * alphanum    =  ALPHA / DIGIT
   * ldh         = 　alphanum / "-"
   * unreserved  =  ALPHA / DIGIT / "-" / "." / "_" / "~"
   * "
   */
  private static Collection<String> UNRESERVED;

  /**
   * <pre>
   * "
   * pct-encoded = "%" HEXDIG HEXDIG　;
   * "
   */
  private static Collection<String> PCT_ENCODED;

  /**
   * <pre>
   * "
   * pchar = unreserved / pct-encoded / sub-delims / ":" / "@"
   * "
   */
  private static Collection<String> TRUE_PCHAR;

  /**
   * <pre>
   * Include 2 parts:
   * - Those of "Code 32 (decimal) is a non-printing spacing character. Codes 33 through 126
   * (decimal) are printable graphic characters." in <a href="http://www.columbia.edu/kermit/ascii.html">US-ASCII</a> but not in pChar:
   *  " ", """, "#", "%", "/", "<", ">", "?", "[", "\", "]", "^", "`", "{", "|", "}"
   * - % DIGIT/[a-f] DIGIT/[a-f]
   * May add more later
   */
  private static Collection<String> FALSE_PCHAR;

  /**
   * <pre>
   * "
   * DIGIT         =  0-9
   * ALPHA         =  A-Z / a-z
   * alphanum      =  ALPHA / DIGIT
   * ldh           = 　alphanum / "-"
   * "
   */
  private static Collection<String> LDH;

  /**
   * <pre>
   * "
   * NID = (alphanum) 0*30(ldh) (alphanum)
   * "
   */
  private static Collection<String> TRUE_NID;

  /**
   * <pre>
   * Includes:
   * - alphanum
   * -  "-" alphanum
   * -  alphanum "-"
   * - those in "Code 32 (decimal) is a nonprinting spacing character. Codes 33 through 126 (decimal) are printable graphic characters."
   * of <a href="http://www.columbia.edu/kermit/ascii.html">US-ASCII</a> but not in ALPHANUM. Including "-".
   * - with length than 32
   *
   * May add others later
   */
  private static Collection<String> FALSE_NID;

  /**
   * <pre>
   * 6 parts:
   *  - pChar
   *  - pChar '/'
   *  - pChar pChar
   *  - pChar '/' pChar
   *  - pChar pChar '/'
   *  - pChar concatenation
   *  May add others later
   *  @see #TRUE_PCHAR
   */
  private static Collection<String> TRUE_NSS;

  /**
   * <pre>
   * 4 parts:
   * false pChar
   * ""
   * '/'
   * '/' pChar
   *  May add others later
   */
  private static Collection<String> FALSE_NSS;

  /** @see #ALPHA */
  private static Collection<String> getAlpha() {
    List<String> re = new ArrayList<>(52);
    for (Character lowerCaseAlpha = 'a'; lowerCaseAlpha <= 'z'; lowerCaseAlpha++) {
      String charStr = lowerCaseAlpha.toString();
      re.add(charStr);
      re.add(charStr.toUpperCase());
    }
    return re;
  }

  /** @see #ALPHANUM */
  private static Collection<String> getAlphaNum() {
    Set<String> re = Sets.newHashSet();
    re.addAll(DIGITS);
    re.addAll(ALPHA);
    return re;
  }

  /** @see #LDH */
  private static Collection<String> getLDH() {
    Set<String> re = Sets.newHashSet();
    re.addAll(ALPHANUM);
    re.add("-");
    return re;
  }

  /** @see #UNRESERVED */
  private static Collection<String> getUnReserved() {
    Set<String> re = Sets.newHashSet();
    re.addAll(LDH);
    re.add(".");
    re.add("_");
    re.add("~");
    return re;
  }

  /** @see #PCT_ENCODED */
  private static Collection<String> getPctEncoded() {
    Set<String> re = new HashSet<>((int) Math.sqrt(16d));
    for (int i = 0; i < HEXDIG.length; i++) {
      for (int j = 0; j < HEXDIG.length; j++) {
        re.add("%" + HEXDIG[i] + HEXDIG[j]);
      }
    }
    return re;
  }

  /** @see #TRUE_PCHAR */
  private static Collection<String> getTruePChar() {
    Set<String> re = Sets.newHashSet();
    re.addAll(UNRESERVED);
    re.addAll(PCT_ENCODED);
    re.addAll(SUB_DELIMS);
    re.add(":");
    re.add("@");
    return re;
  }

  /** @see #FALSE_PCHAR */
  private static Collection<String> getFalsePChar() {
    Set<String> re = Sets.newHashSet();
    re.addAll(
        Arrays.asList(
            " ", "\"", "#", "%", "/", "<", ">", "?", "[", "\\", "]", "^", "`", "{", "|", "}"));
    String[] tmp = new String[] {"a", "b", "c", "d", "e", "f"};
    for (String digit : DIGITS) {
      for (String each : tmp) {
        re.add("%" + digit + each);
        re.add("%" + each + digit);
      }
    }
    return re;
  }

  /**
   * Get all permutations from given Collection 'from' with the length <= 'maxLeng'
   *
   * @param from
   * @param maxLeng
   * @return
   */
  private static Set<String> permutationsOf(Collection<String> from, int maxLeng) {
    Preconditions.checkState(from != null && !from.isEmpty() && maxLeng <= from.size());
    Set<String> re = new HashSet<>();
    Set<String> current = new HashSet<>();
    current.add("");
    re.addAll(current);
    while (maxLeng-- > 0) {
      Set<String> next = new HashSet<>();
      for (String each : current) {
        for (String currentChar : from) {
          next.add(each + currentChar);
        }
      }
      re.addAll(next);
      current = next;
    }
    return re;
  }

  /** @see #TRUE_NID */
  private static Collection<String> getTrueNID() {
    Set<String> re = Sets.newHashSet();
    // This method will run a long time with the max length of middle set to be great than 1.
    Set<String> middles = permutationsOf(LDH, 1);
    for (String left : ALPHANUM) {
      for (String middle : middles) {
        for (String right : ALPHANUM) {
          re.add(left + middle + right);
        }
      }
    }
    return re;
  }

  private static void addUSASCIIPrintableChars(char start, char end, Set<String> re) {
    Preconditions.checkArgument(32 <= start && start <= end && end <= 126);
    Character code = start;
    while (code <= end) {
      re.add(code.toString());
      code++;
    }
  }
  /** @see #FALSE_NID */
  private static Collection<String> getFalseNID() {
    Set<String> re = Sets.newHashSet();
    for (String each : ALPHANUM) {
      re.add(each);
      re.add("-" + each);
      re.add(each + "-");
    }
    addUSASCIIPrintableChars(' ', '/', re);
    addUSASCIIPrintableChars(':', '@', re);
    addUSASCIIPrintableChars('[', '`', re);
    addUSASCIIPrintableChars('{', '~', re);
    re.add("withLengthMoreThan32-9876543210-9876543210-9876543210");
    return re;
  }

  /** @see #TRUE_NSS */
  private static Collection<String> getTrueNSS() {
    Set<String> re = Sets.newHashSet();
    re.addAll(TRUE_PCHAR);
    StringBuilder concatenation = new StringBuilder();
    for (String i : TRUE_PCHAR) {
      re.add(i + "/");
      concatenation.append(i);
      for (String j : TRUE_PCHAR) {
        re.add(i + "/" + j);
        re.add(i + j);
        re.add(i + j + "/");
      }
    }
    re.add(concatenation.toString());
    return re;
  }

  /** @see #FALSE_NSS */
  private static Collection<String> getFalseNSS() {
    Set<String> re = Sets.newHashSet();
    re.addAll(FALSE_PCHAR);
    re.add("");
    re.add("/");
    for (String i : TRUE_PCHAR) {
      re.add("/" + i);
    }
    return re;
  }

  private static String randomOf(Collection<String> in) {
    String[] ins = new String[in.size()];
    in.toArray(ins);
    return ins[new Random().nextInt(in.size())];
  }

  /**
   * <pre>
   * "
   * assigned-name = "urn" ":" NID ":" NSS
   * The leading scheme (urn:) is case-insensitive. see <a href="https://en.wikipedia.org/wiki/Uniform_Resource_Name"> wiki</a>
   * "
   */
  private Collection<String> getTrueAssignedName() {
    Set<String> re = Sets.newHashSet();
    re.add(IDP_ENTITY_ID_1);
    re.add(IDP_ENTITY_ID_2);
    int i = 0;
    while (i++ < 100) {
      String nid = randomOf(TRUE_NID);
      String nss = randomOf(TRUE_NSS);
      re.add("urn:" + nid + ":" + nss);
      re.add("Urn:" + nid + ":" + nss);
      re.add("uRn:" + nid + ":" + nss);
      re.add("urN:" + nid + ":" + nss);
      re.add("URn:" + nid + ":" + nss);
      re.add("uRN:" + nid + ":" + nss);
      re.add("UrN:" + nid + ":" + nss);
      re.add("URN:" + nid + ":" + nss);
    }
    return re;
  }

  /**
   * <pre>
   * 1> Wrong constructors with right component
   *
   * ""
   *
   * urn
   * :
   * NID
   * NSS
   *
   * urn:
   * urnNID
   * urnNSS
   * :NID
   * ::
   * :NSS
   * NID:
   * NIDNSS
   *
   * urn:NID
   * urn::
   * urn:NSS
   * urnNID:
   * urnNIDNSS
   * :NID:
   * :NIDNSS
   * ::NSS
   * NID:NSS
   *
   * urn:NID:
   * urn:NIDNSS
   * urn::NSS
   * urnNID:NSS
   * :NID:NSS
   *
   *  May add more later
   *
   * 2> Right constructors with wrong component
   *  FALSE urn:NID      :NSS
   *  urn      :FALSE NID:NSS
   *  urn      :NID      :FALSE NSS
   *
   *  FALSE urn:FALSE NID:NSS
   *  FALSE urn:NID      :FALSE NSS
   *  urn      :FALSE NID:FALSE NSS
   *
   *  FALSE urn:FALSE NID:FALSE NSS
   *
   * May add others later
   *
   * @see  URN#ASSIGNED_NAME
   * @see  URN#NID
   * @see  URN#NSS
   */
  private Collection<String> getFalseAssignedName() {
    Collection<String> falseUrns = Arrays.asList(new String[] {"u", "urnx"});
    Set<String> re = Sets.newHashSet();
    re.add("");
    re.add("urn");
    re.add(":");
    re.add("urn:");
    re.add("::");
    re.add("urn::");

    int i = 0;
    while (i++ < 100) {
      String nid = randomOf(TRUE_NID);
      String nss = randomOf(TRUE_NSS);
      if (nss.startsWith(":")) {
        continue;
      }
      log.debug("nid [" + nid + "] nss [" + nss + "]");
      re.add(nid);
      re.add(nss);
      re.add("urn" + nid);
      re.add("urn" + nss);
      re.add(":" + nid);
      re.add(":" + nss);
      re.add(nid + ":");
      re.add(nid + nss);
      re.add("urn:" + nid);
      re.add("urn:" + nss);
      re.add("urn" + nid + ":");
      re.add("urn" + nid + nss);
      re.add(":" + nid + ":");
      re.add(":" + nid + nss);
      re.add("::" + nss);
      re.add(nid + ":" + nss);
      re.add("urn:" + nid + ":");
      re.add("urn" + ":" + nid + nss);
      re.add("urn" + "::" + nss);
      re.add("urn" + nid + ":" + nss);
      re.add(":" + nid + ":" + nss);

      String falseNid = randomOf(FALSE_NID);
      String falseNss = randomOf(FALSE_NSS);
      log.debug("falseNid [" + falseNid + "] falseNss [" + falseNss + "]");
      re.add("urn:" + falseNid + ":" + nss);
      re.add("urn:" + nid + ":" + falseNss);
      re.add("urn:" + falseNid + ":" + falseNss);

      for (String falseUrn : falseUrns) {
        re.add(falseUrn + ":" + nid + ":" + nss);
        re.add(falseUrn + ":" + falseNid + ":" + nss);
        re.add(falseUrn + ":" + nid + ":" + falseNss);
        re.add(falseUrn + ":" + falseNid + ":" + falseNss);
      }
    }
    return re;
  }

  /**
   * <pre>
   * @see URN#FRAGMENT
   */
  private Collection<String> getTrueFragments() {
    Set<String> re = Sets.newHashSet();
    re.add("");
    re.add("/");
    re.add("?");
    re.addAll(TRUE_PCHAR);
    return re;
  }

  /**
   * <pre>
   * @see URN#R_Q_COMPONENT
   */
  private Collection<String> getTruceRQComponet() {
    Set<String> re = Sets.newHashSet();
    Collection<String> fragments = getTrueFragments();
    for (String pchar : TRUE_PCHAR) {
      for (String fragment : fragments) {
        re.add(pchar + fragment);
      }
    }
    return re;
  }

  /**
   * <pre>
   * Include
   *  - ""
   *  - "?+ R_Q_COMPONENT"
   *  - "?= R_Q_COMPONENT"
   *  - "?+ R_Q_COMPONENT ?= R_Q_COMPONENT"
   *
   * @see URN#RQ_COMPONENTS
   * @see URN#R_Q_COMPONENT
   */
  private Collection<String> getTruceRQComponents() {
    Set<String> re = Sets.newHashSet();
    re.add("");
    Collection<String> RQComponent = getTruceRQComponet();
    for (String each : RQComponent) {
      re.add("?+" + each);
      re.add("?=" + each);
    }
    int i = 0;
    while (i++ < 100) {
      String rComponent = randomOf(RQComponent);
      String qComponent = randomOf(RQComponent);
      re.add("?+" + rComponent + "?=" + qComponent);
    }
    return re;
  }

  /**
   * <pre>
   * Include
   * - "?+"
   * - "?="
   * - "R_Q_COMPONENT"
   * May add more later
   * @see URN#RQ_COMPONENTS
   * @see URN#R_Q_COMPONENT
   */
  private Collection<String> getFalseRQComponents() {
    Set<String> re = Sets.newHashSet();
    re.add("?+");
    re.add("?=");
    Collection<String> RQCOMPONENT = getTruceRQComponet();
    re.addAll(RQCOMPONENT);

    return re;
  }

  /**
   * <pre>
   *Include
   * - assigned-name
   * - assigned-name  rq-components
   * - assigned-name  rq-components "#" f-component
   * - special cases
   * May add more later
   * @see URN#URN_RFC8141
   */
  private Collection<String> geTrueURNRFC8141() {
    Set<String> re = Sets.newHashSet();
    Collection<String> trueAssignedNames = getTrueAssignedName();
    re.addAll(trueAssignedNames);

    Collection<String> trueRQComponents = getTruceRQComponents();
    Collection<String> trueFragments = getTrueFragments();
    int i = 0;
    while (i++ < 100) {
      String trueAssignedName = randomOf(trueAssignedNames);
      String trueRQComponent = randomOf(trueRQComponents);
      String trueFragment = randomOf(trueFragments);
      re.add(trueAssignedName + trueRQComponent);
      re.add(trueAssignedName + trueRQComponent + "#");
      re.add(trueAssignedName + trueRQComponent + "#" + trueFragment);
    }

    re.addAll(getSpecialCase());
    return re;
  }

  private Collection<String> getSpecialCase() {
    // Some cases got from <a href="https://tools.ietf.org/html/rfc8141">RFC 8141</a>
    return Arrays.asList(
        "urn:example:foo",
        "urn:example:1/406/47452/2",
        "urn:example:foo-bar-baz-qux?+CCResolve:cc=uk#",
        "urn:example:foo-bar-baz-qux?+CCResolve:cc=uk",
        "urn:example:weather?=op=map&lat=39.56&lon=-104.85&datetime=1969-07-21T02:56:15Z",
        "urn:example:foo-bar-baz-qux#somepart",
        "urn:example:a123,z456",
        "URN:example:a123,z456",
        "urn:EXAMPLE:a123,z456",
        "urn:example:A123,z456",
        "urn:example:a123,Z456",
        "urn:example:a123%2Cz456",
        "URN:EXAMPLE:a123%2Cz4566",
        "urn:example:a123,z456?+abc",
        "urn:example:a123,z456?=xyz",
        "urn:example:a123,z456#789",
        "urn:example:a123,z456/foo",
        "urn:example:a123,z456/bar",
        "urn:example:a123,z456/baz",
        "urn:example:%D0%B0123,z456",
        "urn:example:apple:pear:plum:cherry",
        // University Washington cases:
        IDP_ENTITY_ID_1,
        IDP_ENTITY_ID_2);
  }

  /**
   * <pre>
   *Include
   * - FALSE assigned-name
   * - rq-components
   * - "#" f-component
   *
   * May add more later
   * @see URN#URN_RFC8141
   */
  private Iterable<String> getFalseURNRFC8141() {
    Set<String> re = Sets.newHashSet();
    Collection<String> flaseAssignedNames = getFalseAssignedName();
    Collection<String> trueRQComponents = getTruceRQComponents();

    re.addAll(flaseAssignedNames);
    re.addAll(trueRQComponents);

    Collection<String> trueFragments = getTrueFragments();
    for (String each : trueFragments) {
      re.add("#" + each);
    }
    return re;
  }

  private void isTrues(String regex, Iterable<String> trues) {
    for (String each : trues) {
      if (!each.matches(regex)) {
        log.error("It shoulbe be true [" + regex + "]->[" + each + "]");
      }
      Assert.assertTrue(Pattern.compile(regex).matcher(each).matches());
    }
  }

  private void isFalses(String regex, Iterable<String> falses) {
    for (String each : falses) {
      if (each.matches(regex)) {
        log.error("It should be false: [" + regex + "]->[" + each + "]");
      }
      Assert.assertFalse(Pattern.compile(regex).matcher(each).matches());
    }
  }

  @BeforeClass
  public static void init() {
    ALPHA = getAlpha();
    ALPHANUM = getAlphaNum();
    LDH = getLDH();
    UNRESERVED = getUnReserved();
    PCT_ENCODED = getPctEncoded();
    TRUE_PCHAR = getTruePChar();
    FALSE_PCHAR = getFalsePChar();
    TRUE_NID = getTrueNID();
    FALSE_NID = getFalseNID();
    TRUE_NSS = getTrueNSS();
    FALSE_NSS = getFalseNSS();
  }

  @Test
  public void testNID() {
    isTrues(NID, getTrueNID());
    isFalses(NID, getFalseNID());
  }

  @Test
  public void testPChar() {
    isTrues(PCHAR, TRUE_PCHAR);
    isFalses(PCHAR, FALSE_PCHAR);
  }

  @Test
  public void testNSS() {
    isTrues(NSS, getTrueNSS());
    isFalses(NSS, getFalseNSS());
  }

  @Test
  public void testAssignedName() {
    isTrues(ASSIGNED_NAME, getTrueAssignedName());
    isFalses(ASSIGNED_NAME, getFalseAssignedName());
  }

  @Test
  public void testRQComponents() {
    isTrues(RQ_COMPONENTS, getTruceRQComponents());
    isFalses(RQ_COMPONENTS, getFalseRQComponents());
  }

  @Test
  public void testURNRFC8141() {
    isTrues(URN_RFC8141, geTrueURNRFC8141());
    isFalses(URN_RFC8141, getFalseURNRFC8141());
  }
}
