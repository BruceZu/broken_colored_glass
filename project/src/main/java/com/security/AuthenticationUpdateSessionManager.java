package com;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

public interface AuthenticationUpdateSessionManager {

  /**
   * <pre>
   * When an authenticated provider/customer user changed his/her own password:
   * - change CSRF token.
   * - session ID and cookie 'JSESSIONID'.
   * - expire the original session ID.
   *
   * prevents"session-fixation" attacks
   */
  void onIChangeMyOwnPassword(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response)
      throws SessionAuthenticationException;

  /**
   * <pre>
   * When provider/customer user B's password is changed by other authenticated provider user A, if
   * user B is online, force user B logout by:
   * - remove CSRF token.
   * - invalidate user B's session and remove the Authentication from the SecurityContext.
   * - clean the defined 'JSESSIONID' cookie.
   *
   * prevents"session-fixation" attacks
   */
  void onIChangeOtherPassword(
      Object beChangedUserPrincial, HttpServletRequest request, HttpServletResponse response)
      throws SessionAuthenticationException;

  /**
   * <pre>
   * When provider/customer user B's account is disable/deleted by other
   *  authenticated provider user A, if user B is online, force user B logout by:
   * - remove CSRF token.
   * - invalidate user B's session and remove the Authentication from the SecurityContext.
   * - clean the defined 'JSESSIONID' cookie.
   *
   * prevents"session-fixation" attacks
   */
  void onIDisableOtherAccount(
      Object disabledUserPrincial, HttpServletRequest request, HttpServletResponse response)
      throws SessionAuthenticationException;
}

