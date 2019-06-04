package com.ftnt.fpcs.rest.controller;

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

  /**
   * <pre>
   * Add a bar for CSRF attacker trying to get CSRF token even
   * the session in cookie can compromise.
   * Require custom request header 'Fpc_Cors_Req_Refligh'.
   * It is designed to let CORS supported browsers issue an
   * option preflighted request. Then Spring rejects it with
   * the default configuration. Without CORS it is not possible
   * to add 'Fpc_Cors_Req_Refligh' to a cross domain XHR request.
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
    return request.getHeader("Fpc_Cors_Req_Prefligh") != null;
  }

  private boolean hasLoggedIn() {
    Authentication authen = SecurityContextHolder.getContext().getAuthentication();
    if (resolver == null) {
      resolver = new AuthenticationTrustResolverImpl();
    }
    return authen != null && !resolver.isAnonymous(authen);
  }

  @GetMapping(value = "/v1/csrf", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<String> get(
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

  @GetMapping(value = "/v1/hasloggedin", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<String> get(HttpServletRequest request, HttpServletResponse response) {
    return ResponseEntity.ok(new JSONObject().put("result", hasLoggedIn()).toString());
  }
}
