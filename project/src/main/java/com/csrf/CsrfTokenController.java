package csrf;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

@RestController
public class CsrfTokenController {
  private static Logger log = LogManager.getLogger(CsrfTokenController.class.getName());

  private AuthenticationTrustResolver resolver = null;
  private PrjAuthProcessor authProcessor;

  private static final String SP_URL = "/app/**/*";
  private static final String SP_LOCATION = "/resources/react/index.html";

  /**
   * Show the single page of PROJ.
   *
   * <p>Once All JSP pages are replaced, use {@link UrlBasedViewResolver} resolver view.
   */
  @GetMapping(value = SP_URL)
  public ModelAndView showIndex(
      ModelMap model, HttpServletRequest request, HttpServletResponse response) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("Forward to '" + SP_LOCATION + "'");
    }

    return new ModelAndView("forward:" + SP_LOCATION, model);
  }

  @Autowired
  void setPrjAuthProcessor(PrjAuthProcessor authProcessor) {
    this.authProcessor = authProcessor;
  }

  /**
   * <pre>
   * Add a bar for CSRF attacker trying to get CSRF token even
   * the session in cookie can compromise.
   * Require custom request header 'Prj_Cors_Req_Refligh'.
   * It is designed to let CORS supported browsers issue an
   * option preflighted request. Then Spring rejects it with
   * the default configuration. Without CORS it is not possible
   * to add 'Prj_Cors_Req_Refligh' to a cross domain XHR request.
   * So with checking it the server knows: The request
   * - The request is not from an attacker's domain attempting
   *   to make a request on behalf of the user with JavaScript.
   * - The request is not from a regular HTML form, of which it
   *   is harder to verify it is not cross domain without the use
   *   of CSRF token.
   * @see {@link org.springframework.web.cors.DefaultCorsProcessor}.
   * @see <a href=
   * 'https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS#Preflighted_requests'>Preflighted requests</a>
   */
  public static boolean withRequiredRequestHead(HttpServletRequest request) {
    return request.getHeader("Prj_Cors_Req_Prefligh") != null;
  }

  private boolean hasLoggedIn() {
    Authentication authen = SecurityContextHolder.getContext().getAuthentication();
    if (resolver == null) {
      resolver = new AuthenticationTrustResolverImpl();
    }
    return authen != null && !resolver.isAnonymous(authen);
  }

  @GetMapping(value = "/v1/user/self/csrf", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<String> getMyCsrf(
      NativeWebRequest webRequest, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    if (withRequiredRequestHead(request)) {
      CsrfToken csrfToken =
          (CsrfToken)
              webRequest.getAttribute(CsrfToken.class.getName(), NativeWebRequest.SCOPE_REQUEST);

      String tokenInfo =
          new JSONObject()
              .put("headerName", csrfToken.getHeaderName())
              .put("parameterName", csrfToken.getParameterName())
              .put("token", csrfToken.getToken())
              .put("status", "success")
              .toString();

      return ResponseEntity.ok(tokenInfo);
    }
    log.warn("Somebody is trying to get the CSRF token");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
  }

  @GetMapping(value = "/v1/user/self/usertype", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<String> getMyUserType(
      HttpServletRequest request, HttpServletResponse response) {
    if (hasLoggedIn()) {
      HttpSession session = request.getSession(false);
      String userEmail = (String) session.getAttribute("user_email");
      Integer userId = (Integer) session.getAttribute("user_id");

      boolean isProviderUser = (boolean) session.getAttribute("serviceProvider");
      String userType = isProviderUser ? USER_TYPE_PROVIDER : USER_TYPE_CUSTOMER;
      return ResponseEntity.ok(
          new JSONObject()
              .put("myId", userId.toString())
              .put("myEmail", userEmail)
              .put("myUserType", userType)
              .toString());
    }
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
  }

  @GetMapping(value = "/v1/user/self/hasloggedin", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<String> getMyLoginStatus(
      HttpServletRequest request, HttpServletResponse response) {
    return ResponseEntity.ok(new JSONObject().put("result", hasLoggedIn()).toString());
  }

  /**
   * <pre>
   * In PROJ the locale only contain language info.
   * How does PROJ work with locale at present:
   * - Local authentication:
   *   -- For the page got from GET /proj/login, the default selected locale language is
   *   the default serviceProvider's locale language. If the end user selects another language
   *   than the default one. The new option will have the highest priority
   *   and be used till logout even serviceProvider or serviceConsumer language option is updated before
   *   logout.
   *
   * - After login successfully no matter what kind of authentication. If the user did not
   *   have a chance or did not want to change the language before login. Then now
   *   -- For serviceProvider user: The serviceProvider language option set in
   *   settings will be used before logout no matter serviceProvider change it or
   *   not before logout.
   *   -- For the serviceConsumer user: If the serviceConsumer does not prefer to use serviceProvider language.
   *   serviceConsumer own language will be used before logout.
   *
   */
  @GetMapping(value = "/v1/user/self/locale", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<String> getLocale(
      HttpServletRequest request, HttpServletResponse response) {
    if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
      if (hasLoggedIn()) {
        return ResponseEntity.ok(
            new JSONObject().put("lang", LocaleContextHolder.getLocale().getLanguage()).toString());
      } else if (!MetaDataGenerator.isSAMLEnabled()) {
        return ResponseEntity.ok(
            new JSONObject().put("lang", authProcessor.getProviderLocale()).toString());
      }
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
  }
}
