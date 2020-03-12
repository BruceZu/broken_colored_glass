import static proj.cache.UserPermissionCache.REMOTE_AUTH_TYPE_RADIUS;
import static proj.cache.UserPermissionCache.REMOTE_AUTH_TYPE_SSO;
import static proj.cache.UserPermissionCache.USER_TYPE_CUSTOMER;
import static proj.cache.UserPermissionCache.USER_TYPE_PROVIDER;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import proj.cache.UserPermissionCache;
import proj.cache.UserPermissionCache.CacheValue;

@Component(value = "PermissionEvaluator")
public class CustomizedPermissionEvaluator implements PermissionEvaluator {
  private static final Logger logger = LoggerFactory.getLogger(PermissionEvaluator.class);
  public static final String REMOTE_AUTH_TYPE_PROAUTH = "ProAuth";
  private final UserPermissionCache cache;

  @Autowired
  public PermissionEvaluator(UserPermissionCache cache) {
    this.cache = cache;
  }

  /**
   * <pre>
   *
   * @param authType: authType 'local' for 'local' and 'localAdmin' authorized user; authType
   *        'ProRadius' for 'ProRadius'authorized user and has roles need mapping authType
   *        'ProSSO' for 'ProSSO' authorized user and has roles need mapping
   * @param userType: "SP" : "CUST"
   */
  private boolean checkPermission(
      Integer permission,
      Integer userId,
      String targetDomainObject,
      String authType,
      @Nullable Set<String> remoteRoles,
      @Nullable String userType) {
    try {
      Set<CacheValue> userPermissions;
      userPermissions =
          Objects.equals(authType, AuthenticationType.LOCAL.getName())
              ? cache.get(userId)
              : cache.getRadiusOrSSO(userId, remoteRoles, userType, authType);
      for (CacheValue userPermission : userPermissions) {
        if (userPermission.objectName.equalsIgnoreCase(targetDomainObject)
            && Objects.equals(userPermission.operationId, permission)) {
          return true;
        }
      }
      return false;
    } catch (ExecutionException e) {
      logger.error("Abort ", e);
      throw new RuntimeException(e);
    }
  }

  /** @param userType: "SP" : "CUST" */
  private boolean checkDefaultPermission(
      Integer permission, String targetDomainObject, String userType) {
    try {
      Set<CacheValue> userPermissions;
      userPermissions = cache.getDefault(userType);
      for (CacheValue userPermission : userPermissions) {
        if (userPermission.objectName.equalsIgnoreCase(targetDomainObject)
            && userPermission.operationId.equals(permission)) {
          return true;
        }
      }
      return false;
    } catch (ExecutionException e) {
      logger.error("Abort ", e);
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean hasPermission(
      Authentication authentication, Object targetDomainObject, Object permission) {
    boolean hasPerm = false;
    if (authentication != null) {
      ServletRequestAttributes sra =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      HttpSession session = sra.getRequest().getSession();
      if (session != null) {
        Object auth = session.getAttribute("auth");
        if (auth == null) {
          return false;
        }

        String authStr = auth.toString();
        if (authStr.equals(AuthenticationType.LOCAL.getName())
            || authStr.equals(AuthenticationType.LOCALADMIN.getName())) {
          return checkPermission(
              (Integer) permission,
              (Integer) session.getAttribute("user_id"),
              targetDomainObject.toString(),
              AuthenticationType.LOCAL.getName(),
              null,
              null);
        }

        if (authStr.equals(AuthenticationType.REMOTE.getName())) {
          String remoteAuthType = (String) session.getAttribute("remoteAuthType");
          Object roleSet = session.getAttribute("rolesFromRadiusOrProSSOSAML");
          String userType =
              ((boolean) session.getAttribute("serviceProvider"))
                  ? USER_TYPE_PROVIDER
                  : USER_TYPE_CUSTOMER;
          if (roleSet != null
              && (remoteAuthType.equals(REMOTE_AUTH_TYPE_RADIUS)
                  || remoteAuthType.equals(REMOTE_AUTH_TYPE_SSO))) {
            return checkPermission(
                (Integer) permission,
                (Integer) session.getAttribute("user_id"),
                targetDomainObject.toString(),
                remoteAuthType,
                (Set<String>) roleSet,
                userType);
          }
          if (roleSet == null
              && (remoteAuthType.equals(REMOTE_AUTH_TYPE_RADIUS)
                  || remoteAuthType.equals(REMOTE_AUTH_TYPE_PROAUTH)
                  || remoteAuthType.equals(REMOTE_AUTH_TYPE_SSO))) {
            return checkDefaultPermission(
                (Integer) permission, targetDomainObject.toString(), userType);
          }
        }
      }
    }

    return hasPerm;
  }

  @Override
  public boolean hasPermission(
      Authentication authentication,
      Serializable targetId,
      String targetType,
      Object permission) { // TODO Auto-generated method stub
    return false;
  }
}

enum AuthenticationType {
  LOCAL("local"),
  REMOTE("remote"),
  LOCALADMIN("localadmin");

  private String name;

  AuthenticationType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
