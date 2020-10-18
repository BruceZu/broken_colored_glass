
import com.coustomer.projs.dao.PrjRadiusRolesDao;
import com.coustomer.projs.dao.PrjRoleDao;
import com.coustomer.projs.model.PrjPermissionsModel;
import com.coustomer.projs.model.PrjRadiusPrjRolesModel;
import com.coustomer.projs.model.PrjRadiusRoleModel;
import com.coustomer.projs.model.PrjRoleModel;
import com.coustomer.projs.service.impl.DaoUtil;
import com.coustomer.pmc.dao.UserDao;
import com.coustomer.pmc.model.UserModel;
import com.google.common.annotations.VisibleForTesting;
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
import javax.annotation.Nullable;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 *<pre>
 * Note: Do not access or expose the cache implement directly to couple
 *       PROJ with cache implement closely. Instead always access cache
 *       via CacheManager or PrjPermissionEvaluator
 * Because currently cache implementation is based on Guava Cache.
 * TODO:
 * - replace Guava Cache with Caffeine Cache for performance concern.
 * - refactor out the cache implement out of PROJ to an
 *   independent deployed Cache service in multi PROJ nodes env.
 *
 */
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
      if (!(obj instanceof RemoteKey)) {
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
    public final Integer permissionId;
    public final String objectName;
    public final Integer operationId;
    public final String detail;

    @VisibleForTesting
    public CacheValue(
        @Nullable Integer permissionId, String objectName, Integer operationId, String detail) {
      this.permissionId = permissionId;
      this.objectName = objectName;
      this.operationId = operationId;
      this.detail = detail;
    }

    @Override
    public int hashCode() {
      return Objects.hash(objectName, operationId, detail);
    }

    @Override
    public boolean equals(Object thatObject) {
      if (this == thatObject) {
        return true;
      }
      if (thatObject == null || getClass() != thatObject.getClass()) {
        return false;
      }
      CacheValue that = (CacheValue) thatObject;
      return Objects.equals(permissionId, that.permissionId);
    }

    public static CacheValue createCacheValue(PrjPermissionsModel permission) {
      return new CacheValue(
          permission.getPermissionId(),
          permission.getObjectsName(),
          permission.getOperations(),
          permission.getDetail());
    }
  }

  private final LoadingCache<Integer, Set<CacheValue>> byUserIdForLocal;
  private final Cache<RemoteKey, Set<CacheValue>> byUserIdAndRolesForRemote;
  private final Cache<String, Set<CacheValue>> byDefaultUserTypeForRemote;

  @Autowired private UserDao userDao;
  @Autowired private PrjRoleDao roleDao;

  private Map<String, Set<PrjRoleModel>> getPrjRolesNameBy(String userType) {
    List<PrjRoleModel> roles = roleDao.getRoles(userType);
    Map<String, Set<PrjRoleModel>> byName = new HashMap<>();
    for (PrjRoleModel role : roles) {
      Set<PrjRoleModel> set = byName.get(role.getRoleName());
      if (set == null) {
        set = new HashSet<PrjRoleModel>();
        byName.put(role.getRoleName(), set);
      }
      set.add(role);
    }
    return byName;
  }

  /**
   * @param remoteRoleModels
   * @param remoteRoleName remoteRoleName map(name:ID is 1:n) remoteRoleModels each of which map
   *     (ID:ID is 1:n) PrjRadiusPrjRolesModels then each of which map (ID:ID is 1:1) PrjRoleModel
   */
  private Set<PrjRoleModel> getMappedPrjRoles(
      List<PrjRadiusRoleModel> remoteRoleModels, String remoteRoleName) {
    if (remoteRoleModels.isEmpty()) {
      logger.warn("Unable to find PROJ roles for Radius server in PROJ Server.");
    }
    Set<PrjRoleModel> mappedPrjRoles = new HashSet<PrjRoleModel>();
    for (PrjRadiusRoleModel rrModel : remoteRoleModels) {
      if (rrModel.getRoleName().equalsIgnoreCase(remoteRoleName)) {
        Set<PrjRadiusPrjRolesModel> maps = rrModel.getPrjRadiusprojRoles();
        for (PrjRadiusPrjRolesModel map : maps) {
          mappedPrjRoles.add(map.getPrjRoleModel());
        }
      }
    }
    if (mappedPrjRoles.isEmpty()) {
      logger.warn(
          "Roles [ "
              + remoteRoleName
              + " ] exists in Radius server but there is no mapping in PROJ.");
    }
    return mappedPrjRoles;
  }

  /**
   * @param remoteRolesNames: configured roles by remote Radius server or compauth server
   * @param remoteAuthType: "compRadius" or "compSSO"
   * @param userType 'SP' provider; or 'CUST' customer.
   */
  private Set<PrjRoleModel> mappedRoles(
      Set<String> remoteRolesNames, String remoteAuthType, String userType) {
    Set<PrjRoleModel> result = new HashSet<PrjRoleModel>();
    Map<String, Set<PrjRoleModel>> projRolesByName = getPrjRolesNameBy(userType);
    for (String remoteRoleName : remoteRolesNames) {
      result.addAll(
          projRolesByName.containsKey(remoteRoleName)
              ? projRolesByName.get(remoteRoleName)
              : getMappedPrjRoles(
                  ((PrjRadiusRolesDao) DaoUtil.getDao(PrjRadiusRolesDao.class, null))
                      .getAllRadiusRoles(remoteAuthType),
                  remoteRoleName));
    }
    if (result.isEmpty()) {
      logger.warn("Unable to find matching PROJ roles fromRadius Server.");
    }
    return result;
  }

  public UserPermissionCache() {
    // Made it configurable for end user to tune the performance.
    byUserIdForLocal =
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
                    Set<PrjRoleModel> roles = userModel.getRoles();
                    for (PrjRoleModel role : roles) {
                      Set<PrjPermissionsModel> permissions = role.getPermissions();
                      for (PrjPermissionsModel permission : permissions) {
                        cached.add(CacheValue.createCacheValue(permission));
                      }
                    }
                    return cached;
                  }
                });

    byUserIdAndRolesForRemote =
        CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .concurrencyLevel(3)
            .initialCapacity(100)
            .recordStats()
            .maximumSize(1000)
            .build();
    byDefaultUserTypeForRemote =
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

  private Set<CacheValue> permissionsCacheValueOf(Iterable<PrjRoleModel> roles) {
    Set<CacheValue> result = new HashSet<>();
    for (PrjRoleModel role : roles) {
      Set<PrjPermissionsModel> permissions = role.getPermissions();
      for (PrjPermissionsModel permission : permissions) {
        result.add(CacheValue.createCacheValue((permission)));
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
    CacheStats stats = byUserIdForLocal.stats();
    if (!stats.equals(new CacheStats(0, 0, 0, 0, 0, 0))) {
      cacheStatsToString(sb, stats, "Local User-Permissions");
    }
    stats = byUserIdAndRolesForRemote.stats();
    if (!stats.equals(new CacheStats(0, 0, 0, 0, 0, 0))) {
      cacheStatsToString(sb, stats, "Remote compRadius/compSSOSAML Mapped User-Permissions");
    }
    stats = byDefaultUserTypeForRemote.stats();
    if (!stats.equals(new CacheStats(0, 0, 0, 0, 0, 0))) {
      cacheStatsToString(sb, stats, "Remote User Default Permissions");
    }
    return sb.toString();
  }

  @Transactional(readOnly = true)
  public Set<CacheValue> get(Integer userId) throws ExecutionException {
    return byUserIdForLocal.get(userId);
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

    return byUserIdAndRolesForRemote.get(
        new RemoteKey(remoteUserId, remoteRoles),
        new Callable<Set<CacheValue>>() {
          // Todo: Add query cache on PrjRoleDaoImpl.getRoles() and
          // PrjRadiusRolesDaoImpl.getAllRadiusRoles()
          @Override
          public Set<CacheValue> call() throws Exception {
            return permissionsCacheValueOf(mappedRoles(remoteRoles, remoteAuthType, userType));
          }
        });
  }

  @Transactional(readOnly = true)
  public Set<CacheValue> getDefault(final String userType) throws ExecutionException {
    validUserType(userType);
    return byDefaultUserTypeForRemote.get(
        userType,
        new Callable<Set<CacheValue>>() {
          @Override
          public Set<CacheValue> call() throws Exception {
            return permissionsCacheValueOf(roleDao.getRoles(userType));
          }
        });
  }

  public void invalidateLocal(Integer userId) {
    byUserIdForLocal.invalidate(userId);
  }

  public void invalidateAllLocal(Iterable<Integer> keys) {
    byUserIdForLocal.invalidateAll(keys);
  }

  // Note: use this method only in test case using local authentication
  @Deprecated
  public void invalidateAllLocal() {
    byUserIdForLocal.invalidateAll();
  }

  /**
   * <pre>
   * Scenario: remote authentication + remote user has configured remote role name(s):
   * remote user cache maybe affected by the following event and need to refresh:
   * - create remote/PROJ role: it is possible that the remote role's name has already be
   *     configured on the remote authentication server for some online user.
   * - delete the remote/PROJ role.
   * - the remote role is unchanged but the 'remote role -PROJ role' association is updated.
   * - the PROJ role is unchanged but the 'PROJ role - permission(s)' association is updated
   * - the remote/PROJ role's name is updated
   */
  public boolean invalidateRemoteIdAndRolesCacheWith(String roleName) {
    final Boolean[] findAndFreshed = new Boolean[1];
    findAndFreshed[0] = false;
    byUserIdAndRolesForRemote
        .asMap()
        .keySet()
        .parallelStream()
        .forEach(
            k -> {
              k.remoteRoles
                  .parallelStream()
                  .forEach(
                      configuredRoleName -> {
                        if (configuredRoleName.equalsIgnoreCase(roleName)) {
                          byUserIdAndRolesForRemote.invalidate(k);
                          findAndFreshed[0] = true;
                        }
                      });
            });

    return findAndFreshed[0];
  }

  /**
   * <pre>
   * When it is remote authentication and the remote user has remote role(s),
   * if some PROJ permission
   * is updated and some association of 'PROJ role - this permission' exists.
   * it need to refresh cache:
   * if the online user:
   *  - is using the related role directly via roleName
   *  - is using the related role via 'remote role - PROJ role' association if there is any.
   * Need call this method to invalid affected all `remote user - permission` cache entry
   */
  public void invalidateRemoteIdAndRolesCacheWith(PrjPermissionsModel toBeUpdatedPermissionModel) {
    if (toBeUpdatedPermissionModel.getPrjRoleModel().isEmpty()) {
      return;
    }
    byUserIdAndRolesForRemote
        .asMap()
        .forEach(
            (key, set) -> {
              set.parallelStream()
                  .forEach(
                      v -> {
                        if (v.permissionId.equals(toBeUpdatedPermissionModel.getPermissionId())) {
                          byUserIdAndRolesForRemote.invalidate(key);
                        }
                      });
            });
  }

  public void invalidateAllDefaultOf(String userType) {
    validUserType(userType);
    byDefaultUserTypeForRemote.invalidate(userType);
  }
}
