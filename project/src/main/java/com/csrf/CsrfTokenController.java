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
  private static final String IS_CSRF_TOKEN_RETURNED_MARK = "login_user_got_the_token";
  private AuthenticationTrustResolver resolver = null;

  private boolean hasLoggedIn() {
    Authentication authen = SecurityContextHolder.getContext().getAuthentication();
    if (resolver == null) {
      resolver = new AuthenticationTrustResolverImpl();
    }
    return authen != null && !resolver.isAnonymous(authen);
  }

  private boolean isLoginUserFirstCall(HttpServletRequest request) {
    return hasLoggedIn()
        && request.getSession(false).getAttribute(IS_CSRF_TOKEN_RETURNED_MARK) == null;
  }

  /**
   * <pre>
   * When to use it:
   * -1- Before user login FPC. n times.
   *
   * -2- After login. 1 time.
   *   This API only return token for the first time for CSRF security reason.
   *   To make sure attackers can not got the token even they know this API.
   *
   * -3- After user change his/her password. 1 time.
   *   As the session and token are changed for fixation security concern.
   *   Front end need to get updated token.
   *
   * Note: 2 and 3 are @deprecated and will be deleted
   */
  @GetMapping(value = "/v1/csrf", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<String> get(
      NativeWebRequest webRequest, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
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

    if (!hasLoggedIn()) {
      return ResponseEntity.ok(tokenInfo);
    }
    // The following lines will be deleted.
    // Instead include token in feedback of login and change password request.
    if (isLoginUserFirstCall(request)) {
      request.getSession(false).setAttribute(IS_CSRF_TOKEN_RETURNED_MARK, true);
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
