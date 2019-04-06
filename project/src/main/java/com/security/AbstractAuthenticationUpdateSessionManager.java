package com;

import com.MetaDataGenerator.NoSAMLSecurityCondition;
import com.MetaDataGenerator.SAMLSecurityCondition;
import java.lang.reflect.Field;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public abstract class AbstractAuthenticationUpdateSessionManager
    implements AuthenticationUpdateSessionManager {
  private static Logger logger =
      LogManager.getLogger(AbstractAuthenticationUpdateSessionManager.class);

  private SessionRegistry sessionRegistry;
  protected SessionAuthenticationStrategy sessionAuthenticationStrategy;

  protected void validateInternalStrategysBean() {
    String error =
        "Injected unexpected internal SessionAuthenticationStrategy bean. "
            + "(possibly due to security filter chain configration update).";
    Assert.isInstanceOf(
        CompositeSessionAuthenticationStrategy.class, sessionAuthenticationStrategy, error);
    try {
      Field privateDelegate =
          ReflectionUtils.findField(
              CompositeSessionAuthenticationStrategy.class, "delegateStrategies");
      ReflectionUtils.makeAccessible(privateDelegate);
      List<?> strategyList =
          (List<?>) ReflectionUtils.getField(privateDelegate, sessionAuthenticationStrategy);
      Assert.isTrue(strategyList.size() == 4, error);
    } catch (IllegalArgumentException | SecurityException e) {
      logger.error(e);
      throw e;
    }
  }

  public abstract void setSessionAuthenticationStrategy(
      SessionAuthenticationStrategy internalStrategysBean);

  @Autowired
  @Qualifier(value = "sessionRegistry")
  public void setSessionRegistry(SessionRegistry sr) {
    this.sessionRegistry = sr;
  }

  @Override
  public void onIChangeMyOwnPassword(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response)
      throws SessionAuthenticationException {

    boolean debugEnabled = logger.isDebugEnabled();
    if (AuthenticationType.LOCAL.getName().equals(request.getSession(false).getAttribute("auth"))) {
      final String orignalSessionId = request.getSession(false).getId();

      sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
      if (debugEnabled) {
        logger.debug("SessionAuthenticationStrategy applied on my password changed");
      }

      List<SessionInformation> sessions =
          sessionRegistry.getAllSessions(authentication.getPrincipal(), false);
      final String newSessioinId = request.getSession(false).getId();
      sessions
          .stream()
          .filter(t -> !t.getSessionId().equals(newSessioinId))
          .forEach(t -> t.expireNow());

      if (debugEnabled) {
        logger.debug(
            String.format(
                "Original session %s is expired, the new one is %s",
                orignalSessionId, newSessioinId));
      }
    }
  }

  private void invalidateSessionOf(
      Object pricipal, HttpServletRequest request, HttpServletResponse response) {
    if (AuthenticationType.LOCAL.getName().equals(request.getSession(false).getAttribute("auth"))) {
      sessionRegistry
          .getAllPrincipals()
          .parallelStream()
          .filter(user -> ((User) user).getUsername().equals(pricipal))
          .flatMap(user -> sessionRegistry.getAllSessions(user, false).stream())
          .forEach(session -> session.expireNow());
    }
  }

  @Override
  public void onIChangeOtherPassword(
      Object beChangedUserPrincial, HttpServletRequest request, HttpServletResponse response)
      throws SessionAuthenticationException {
    invalidateSessionOf(beChangedUserPrincial, request, response);
  }

  @Override
  public void onIDisableOtherAccount(
      Object disabledUserPrincial, HttpServletRequest request, HttpServletResponse response)
      throws SessionAuthenticationException {
    invalidateSessionOf(disabledUserPrincial, request, response);
  }

  static class Security {
    protected static final String NAME = "projAuthenticationUpdateSessionManagerImp";
    protected static final String PROFIX =
        "org.springframework.security.web.authentication.session"
            + ".CompositeSessionAuthenticationStrategy";
  }

  @Configuration
  @Conditional(value = NoSAMLSecurityCondition.class)
  static class NoSAMLSecurity extends Security {
    @Bean(NAME)
    public PrjAuthenticationUpdateSessionManager get() {
      return new AbstractAuthenticationUpdateSessionManager() {
        @Override
        @Autowired
        @Qualifier(value = PROFIX + "#1")
        public void setSessionAuthenticationStrategy(
            SessionAuthenticationStrategy internalStrategysBean) {
          this.sessionAuthenticationStrategy = internalStrategysBean;
          validateInternalStrategysBean();
        }
      };
    }
  }

  @Configuration
  @Conditional(value = SAMLSecurityCondition.class)
  static class SAMLSecurity extends Security {
    @Bean(NAME)
    public PrjAuthenticationUpdateSessionManager get() {
      return new AbstractAuthenticationUpdateSessionManager() {
        @Override
        @Autowired
        @Qualifier(value = PROFIX + "#2")
        public void setSessionAuthenticationStrategy(
            SessionAuthenticationStrategy internalStrategysBean) {
          this.sessionAuthenticationStrategy = internalStrategysBean;
          validateInternalStrategysBean();
        }
      };
    }
  }
}

