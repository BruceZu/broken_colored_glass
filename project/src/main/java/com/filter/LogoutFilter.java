package com.coustomer.projs.security.web.authentication;

import proj.MetaDataGenerator;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml.SAMLLogoutFilter;
import org.springframework.web.filter.GenericFilterBean;

public class PrjLogoutFilter extends GenericFilterBean {
  private static Logger logger = LogManager.getLogger(PrjLogoutFilter.class);
  public static final String FILTER_URL = "/projlogout";

  private MetaDataGenerator metaDataGenerator;

  @Autowired
  public PrjLogoutFilter(MetaDataGenerator mg) {
    this.metaDataGenerator = mg;
  }

  protected boolean requiresLogout(HttpServletRequest request, HttpServletResponse response) {
    return (request.getRequestURI().contains(FILTER_URL));
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    boolean debug = logger.isDebugEnabled();
    if (requiresLogout(req, resp)) {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (debug) {
        logger.debug("Logging out user '" + auth);
      }

      String location =
          metaDataGenerator.isSAMLEnabled()
              ? metaDataGenerator.provideIdpLogoutServiceEndpointUrl()
                  ? SAMLLogoutFilter.FILTER_URL
                  : null
              : "/logout";
      // "/logout" is used by org.springframework.security.web.authentication.logout.LogoutFilter.

      if (location == null) {
        String errorMessage =
            "'IDP Logout Service Endpoint' is not configured, so logout function is not available.";
        logger.error(errorMessage);

        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, errorMessage);
        return;
      }
      if (debug) {
        logger.debug("Forward to '" + location + "'");
      }
      request.getRequestDispatcher(location).forward(request, response);
      return;
    }
    chain.doFilter(req, resp);
  }
}
