import proj.dao.ProRadiusRolesDao;
import proj.dao.ProRoleDao;
import proj.model.ProPermissionsModel;
import proj.model.ProRadiusProRolesModel;
import proj.model.ProRadiusRoleModel;
import proj.model.ProRoleModel;
import proj.service.impl.DaoUtil;
import com.coustomer.pmc.dao.UserDao;
import com.coustomer.pmc.model.UserModel;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Since Spring 4.1, Spring cache supports JSR-107.
// Caffeine is a high performance, near optimal caching library based on Java 8.
// Dev5.0 can introduce them.
@Component(value = "userPermissions")
public class UserPermissionCache {
  private static final Logger logger = LoggerFactory.getLogger(UserPermissionCache.class);

  private static class RemoteKey {
    private Integer remoteUserId;
    private Set<String> remoteRoles;

    public RemoteKey(int remoteUserId, Set<String> remoteRoles) {
      this.remoteUserId = remoteUserId;
      this.remoteRoles = remoteRoles;
    }

    @Override
    public int hashCode() {
      return remoteUserId.hashCode() + remoteRoles.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || !(obj instanceof RemoteKey)) {
        return false;
      }

      RemoteKey other = (RemoteKey) obj;
      if (other.hashCode() != this.hashCode()
          || !Objects.equals(remoteUserId, other.remoteUserId)
          || remoteRoles.size() != other.remoteRoles.size()
          || !other.remoteRoles.containsAll(remoteRoles)
          || !remoteRoles.containsAll(other.remoteRoles)) {
        return false;
      }

      return true;
    }
  }

  public static final String USER_TYPE_PROVIDER = "SP";
  public static final String USER_TYPE_CUSTOMER = "CUST";
  public static final String REMOTE_AUTH_TYPE_RADIUS = "compRadius";
  public static final String REMOTE_AUTH_TYPE_SSO = "compSSO";

  public static class CacheValue {
    public String objectName;
    public Integer operationId;

    public CacheValue(String name, Integer id) {
      this.objectName = name;
      this.operationId = id;
    }

    @Override
    public int hashCode() {
      return Objects.hash(objectName, operationId);
    }

    @Override
    public boolean equals(Object other) {
      boolean result = false;
      if (other instanceof CacheValue) {
        CacheValue that = (CacheValue) other;
        result =
            Objects.equals(this.objectName, that.objectName)
                && Objects.equals(this.operationId, that.operationId);
      }
      return result;
    }
  }

  private final LoadingCache<Integer, Set<CacheValue>> byLocalId;
  private final Cache<RemoteKey, Set<CacheValue>> byRemoteIdAndRoles;
  private final Cache<String, Set<CacheValue>> byUserType;

  @Autowired private UserDao userDao;
  @Autowired private ProRoleDao roleDao;

  private CacheValue getCacheValue(ProPermissionsModel permission) {
    return new CacheValue(permission.getObjectsName(), permission.getOperations());
  }

  private Map<String, Set<ProRoleModel>> getProRolesNameBy(String userType) {
    List<ProRoleModel> roles = roleDao.getRoles(userType);
    Map<String, Set<ProRoleModel>> byName = new HashMap<>();
    for (ProRoleModel role : roles) {
      Set<ProRoleModel> set = byName.get(role.getRoleName());
      if (set == null) {
        set = new HashSet<ProRoleModel>();
        byName.put(role.getRoleName(), set);
      }
      set.add(role);
    }
    return byName;
  }

  /**
   * @param remoteRolesModel
   * @param remoteRoleName remoteRoleName map(name:ID is 1:n) remoteRoleModels each of which map
   *     (ID:ID is 1:n) ProRadiusProRolesModels then each of which map (ID:ID is 1:1) ProRoleModel
   */
  private Set<ProRoleModel> getMappedProRoles(
      List<ProRadiusRoleModel> remoteRoleModels, String remoteRoleName) {
    if (remoteRoleModels.isEmpty()) {
      logger.warn("Unable to find Pro roles for Radius server in Pro Server.");
    }
    Set<ProRoleModel> mappedProRoles = new HashSet<ProRoleModel>();
    for (ProRadiusRoleModel rrModel : remoteRoleModels) {
      if (rrModel.getRoleName().equalsIgnoreCase(remoteRoleName)) {
        Set<ProRadiusProRolesModel> maps = rrModel.getProRadiusProRoles();
        for (ProRadiusProRolesModel map : maps) {
          mappedProRoles.add(map.getProRoleModel());
        }
      }
    }
    if (mappedProRoles.isEmpty()) {
      logger.warn(
          "Roles [ "
              + remoteRoleName
              + " ] exists in Radius server but there is no mapping in Pro.");
    }
    return mappedProRoles;
  }

  /**
   * @param remoteRolesNames: configured roles by remote Radius server or compauth server
   * @param remoteAuthType: "compRadius" or "compSSO"
   * @param userType 'SP' serviceProvider; or 'CUST' serviceConsumer.
   */
  private Set<ProRoleModel> mappedRoles(
      Set<String> remoteRolesNames, String remoteAuthType, String userType) throws Exception {
    Set<ProRoleModel> result = new HashSet<ProRoleModel>();
    Map<String, Set<ProRoleModel>> ProRolesByName = getProRolesNameBy(userType);
    for (String remoteRoleName : remoteRolesNames) {
      result.addAll(
          ProRolesByName.containsKey(remoteRoleName)
              ? ProRolesByName.get(remoteRoleName)
              : getMappedProRoles(
                  ((ProRadiusRolesDao) DaoUtil.getDao(ProRadiusRolesDao.class, null))
                      .getAllRadiusRoles(remoteAuthType),
                  remoteRoleName));
    }
    if (result.isEmpty()) {
      logger.warn("Unable to find matching Pro roles fromRadius Server.");
    }
    return result;
  }

  public UserPermissionCache() {
    // Made it configurable for end user to tune the performance.
    byLocalId =
        CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .concurrencyLevel(3)
            .initialCapacity(100)
            .recordStats()
            .maximumSize(1000)
            .build(
                new CacheLoader<Integer, Set<CacheValue>>() {
                  @Override
                  public Set<CacheValue> load(Integer userId) throws Exception {
                    Set<CacheValue> cached = new HashSet<CacheValue>();
                    UserModel userModel = userDao.getById(userId);
                    if (userModel == null) {
                      return Collections.emptySet();
                    }
                    if (userModel.getRoles().isEmpty()) {
                      Hibernate.initialize(userModel.getRoles());
                    }
                    Set<ProRoleModel> roles = userModel.getRoles();
                    for (ProRoleModel role : roles) {
                      Set<ProPermissionsModel> permissions = role.getPermissions();
                      for (ProPermissionsModel permission : permissions) {
                        cached.add(getCacheValue(permission));
                      }
                    }
                    return cached;
                  }
                });

    byRemoteIdAndRoles =
        CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .concurrencyLevel(3)
            .initialCapacity(100)
            .recordStats()
            .maximumSize(1000)
            .build();
    byUserType =
        CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .concurrencyLevel(3)
            .initialCapacity(100)
            .recordStats()
            .maximumSize(1000)
            .build();
  }

  private void cacheStatsToString(StringBuilder sb, CacheStats stats, String name) {
    sb.append("\n===Cache: " + name)
        .append("\nHit Count: " + stats.hitCount())
        .append("\nHit Rate: " + stats.hitRate())
        .append("\nEviction Count: " + stats.evictionCount())
        .append("\nLoad Count: " + stats.loadCount())
        .append("\nLoad Exception Count: " + stats.loadExceptionCount())
        .append("\nLoad Exception Rate: " + stats.loadExceptionRate())
        .append("\nload Success Count: " + stats.loadSuccessCount())
        .append("\nMiss Count: " + stats.missCount())
        .append("\nMiss Rate: " + stats.missRate())
        .append(
            "\nTotal Load Time : "
                + TimeUnit.SECONDS.convert(stats.totalLoadTime(), TimeUnit.NANOSECONDS)
                + " seconds");
  }

  private Set<CacheValue> permissionsCacheValueOf(Iterable<ProRoleModel> roles) {
    Set<CacheValue> result = new HashSet<>();
    for (ProRoleModel role : roles) {
      Set<ProPermissionsModel> permissions = role.getPermissions();
      for (ProPermissionsModel permission : permissions) {
        result.add(getCacheValue(permission));
      }
    }
    return result;
  }

  private void validUserType(String userType) {
    Preconditions.checkArgument(
        userType.equals(USER_TYPE_PROVIDER) || userType.equals(USER_TYPE_CUSTOMER),
        "Valid value is " + USER_TYPE_PROVIDER + " or " + USER_TYPE_CUSTOMER);
  }

  private void validAuthType(String remoteAuthType) {
    Preconditions.checkArgument(
        remoteAuthType.equals(REMOTE_AUTH_TYPE_RADIUS)
            || remoteAuthType.equals(REMOTE_AUTH_TYPE_SSO),
        "Valid value is " + REMOTE_AUTH_TYPE_RADIUS + " or " + REMOTE_AUTH_TYPE_SSO);
  }

  public String statisticInfo() {
    StringBuilder sb = new StringBuilder();
    CacheStats stats = byLocalId.stats();
    if (!stats.equals(new CacheStats(0, 0, 0, 0, 0, 0))) {
      cacheStatsToString(sb, stats, "Local User-Permissions");
    }
    stats = byRemoteIdAndRoles.stats();
    if (!stats.equals(new CacheStats(0, 0, 0, 0, 0, 0))) {
      cacheStatsToString(sb, stats, "Remote compRadius/compSSOSAML Mapped User-Permissions");
    }
    stats = byUserType.stats();
    if (!stats.equals(new CacheStats(0, 0, 0, 0, 0, 0))) {
      cacheStatsToString(sb, stats, "Remote User Default Permissions");
    }
    return sb.toString();
  }

  @Transactional(readOnly = true)
  public Set<CacheValue> get(Integer userId) throws ExecutionException {
    return byLocalId.get(userId);
  }

  @Transactional(readOnly = false) // need change Schema "use coustomerpmcdb"
  public Set<CacheValue> getRadiusOrSSO(
      Integer remoteUserId,
      final Set<String> remoteRoles,
      final String userType,
      final String remoteAuthType)
      throws ExecutionException {
    validUserType(userType);
    validAuthType(remoteAuthType);

    return byRemoteIdAndRoles.get(
        new RemoteKey(remoteUserId, remoteRoles),
        new Callable<Set<CacheValue>>() {
          // Todo: Add query cache on ProRoleDaoImpl.getRoles() and
          // ProRadiusRolesDaoImpl.getAllRadiusRoles()
          @Override
          public Set<CacheValue> call() throws Exception {
            return permissionsCacheValueOf(mappedRoles(remoteRoles, remoteAuthType, userType));
          }
        });
  }

  @Transactional(readOnly = true)
  public Set<CacheValue> getDefault(final String userType) throws ExecutionException {
    validUserType(userType);
    return byUserType.get(
        userType,
        new Callable<Set<CacheValue>>() {
          @Override
          public Set<CacheValue> call() throws Exception {
            return permissionsCacheValueOf(roleDao.getRoles(userType));
          }
        });
  }

  public void invalidateLocal(Integer userId) {
    byLocalId.invalidate(userId);
  }

  public void invalidateLocal(UserModel user) {
    if (user != null) {
      byLocalId.invalidate(user.getUserId());
    }
  }

  public void invalidateAllLocal(Iterable<Integer> keys) {
    byLocalId.invalidateAll(keys);
  }

  public void invalidateAllLocal() {
    byLocalId.invalidateAll();
  }

  public void invalidateAllLocal(Set<UserModel> users) {
    if (users != null && !users.isEmpty()) {
      Set<Integer> keys = new HashSet<>();
      for (UserModel user : users) {
        keys.add(user.getUserId());
      }
      byLocalId.invalidateAll(keys);
    }
  }

  /**
   * The remote user - roles relations exist only in cache. So for any update that can change the
   * computed user-permissions result, there is no way to know which remote user is affected, So it
   * need to invalidate all cached remote users' permissions.
   */
  public void invalidateAllRemoteUserPermissions() {
    byRemoteIdAndRoles.invalidateAll();
  }

  public void invalidateAllDefaultOf(String userType) {
    validUserType(userType);
    byUserType.invalidate(userType);
  }
}
