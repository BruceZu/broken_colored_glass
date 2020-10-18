
import com.coustomer.projs.model.PrjPermissionsModel;
import com.coustomer.projs.model.PrjRoleModel;
import com.coustomer.projs.service.PrjRadiusRolesService;
import com.coustomer.pmc.model.UserModel;
import com.coustomer.pmc.util.PrjPermissionEvaluator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * <pre>
 * Manage PROJ cache fresh triggered by related PROJ status update. The under
 * controlled caches:
 *
 * - user-permission cache
 *
 * - TODO: user-site cache
 *
 * Note: Do not access or expose the cache implement directly to couple PROJ with
 * cache implement closely. Instead always access cache via CacheManager or
 * PrjPermissionEvaluator. Because currently cache implementation is based on
 * Guava Cache.
 *
 * TODO:
 *
 * - replace Guava Cache with Caffeine Cache for performance concern
 *
 * - refactor out the cache implement out of PROJ to an independent deployed
 * Cache service in multi PROJ nodes env.
 *
 */
@Component(value = "cacheManager")
public class CacheManagerImp implements CacheManager {
  private UserPermissionCache cache;
  private PrjPermissionEvaluator permissionEvaluator;

  private PrjRadiusRolesService projRadiusRolesService;

  @Autowired()
  public void setPrjRadiusRolesService(PrjRadiusRolesService projRadiusRolesService) {
    this.projRadiusRolesService = projRadiusRolesService;
  }

  @Autowired()
  @Qualifier(value = "userPermissions")
  public void setCache(UserPermissionCache cache) {
    this.cache = cache;
  }

  @Autowired()
  @Qualifier(value = "PrjPermissionEvaluator")
  public void setPermissionEvaluator(PrjPermissionEvaluator permissionEvaluator) {
    this.permissionEvaluator = permissionEvaluator;
  }

  @Override
  public void freshCacheByUpdateDeleteLocalUser(UserModel user) {
    switch (permissionEvaluator.getCurrentAuthType()) {
      case LOCAL:
      case LOCALADMIN:
        if (user != null) {
          cache.invalidateLocal(user.getUserId());
        }
        break;
      case REMOTE:
      default:
    }
  }

  @Override
  public void freshCacheByDeleteLocalUsers(Set<UserModel> users) {
    switch (permissionEvaluator.getCurrentAuthType()) {
      case LOCAL:
      case LOCALADMIN:
        if (users != null && !users.isEmpty()) {
          Set<Integer> keys = new HashSet<>();
          for (UserModel user : users) {
            keys.add(user.getUserId());
          }
          cache.invalidateAllLocal(keys);
        }
        break;
      case REMOTE:
      default:
    }
  }

  // Note: use this method only in test case under local authentication
  @Override
  @Deprecated
  public void invalidateAllLocal() {
    switch (permissionEvaluator.getCurrentAuthType()) {
      case LOCAL:
      case LOCALADMIN:
        cache.invalidateAllLocal();
        break;
      case REMOTE:
      default:
    }
  }

  @Override
  public void freshCacheByUpdatedPermission(PrjPermissionsModel toBeUpdatedPermissionModel) {
    Set<PrjRoleModel> relatedPrjRoles = toBeUpdatedPermissionModel.getPrjRoleModel();
    if (relatedPrjRoles.isEmpty()) {
      return;
    }

    relatedPrjRoles
        .parallelStream()
        .flatMap(role -> role.getUsers().stream())
        .distinct()
        .forEach(
            user -> {
              cache.invalidateLocal(user.getUserId());
            });

    if (!toBeUpdatedPermissionModel.getPrjRoleModel().isEmpty()) {
      cache.invalidateRemoteIdAndRolesCacheWith(toBeUpdatedPermissionModel);
      cache.invalidateAllDefaultOf(toBeUpdatedPermissionModel.getPermissionType());
    }
  }

  @Override
  public void freshCacheByAddOrDeleteRemoteRole(String remoteRoleName) {
    switch (permissionEvaluator.getCurrentAuthType()) {
      case LOCAL:
        break;
      case LOCALADMIN:
      case REMOTE:
        cache.invalidateRemoteIdAndRolesCacheWith(remoteRoleName);
        break;
      default:
    }
  }

  @Override
  public void freshCacheByUpdateRemoteRole(Set<String> roleNames) {
    switch (permissionEvaluator.getCurrentAuthType()) {
      case LOCAL:
        break;
      case LOCALADMIN:
      case REMOTE:
        for (String roleName : roleNames) {
          cache.invalidateRemoteIdAndRolesCacheWith(roleName);
        }
        break;
      default:
    }
  }

  @Override
  public void freshCacheByDeletePrjRole(PrjRoleModel role) {
    cache.invalidateRemoteIdAndRolesCacheWith(role.getRoleName());
    cache.invalidateAllDefaultOf(role.getRoleType());
  }

  @Override
  public void freshCacheByAddPrjRole(PrjRoleModel role) {
    freshCacheByDeletePrjRole(role);
  }

  @Override
  public void freshCacheByUpdatePrjRole(
      PrjRoleModel newRole, RoleStatus oldRoleStatus, BiPredicate<PrjRoleModel, RoleStatus> judge) {
    // LOCALADMIN is used to mock remote admin in REMOTE authentication to change
    // authentication settings when remote provider user has not enough permission
    // to do it.
    boolean projRolePermissionAssociationChanged = judge.test(newRole, oldRoleStatus);

    if (oldRoleStatus.userIds != null
        && oldRoleStatus.userIds.size() > 0
        && projRolePermissionAssociationChanged) {
      cache.invalidateAllLocal(oldRoleStatus.userIds);
    }

    boolean namaChanged = !oldRoleStatus.roleName.equalsIgnoreCase(newRole.getRoleName());
    boolean typeChanged = !oldRoleStatus.roleType.equals(newRole.getRoleType());

    if (projRolePermissionAssociationChanged) {
      // via 'configured remote role name - Remote Role - (Remote Role - PROJ role) -
      // PROJ role'.
      if (!projRadiusRolesService.notUsedByRadiusPrjRoles(newRole.getRoleId())) {
        projRadiusRolesService
            .getAllRemoteRoleHodingPrjRole(newRole.getRoleId())
            .parallelStream()
            .map(remoteRole -> remoteRole.getRoleName())
            .forEach(
                remoteRoleName -> {
                  cache.invalidateRemoteIdAndRolesCacheWith(remoteRoleName);
                });
      }

      // update all default, see below.
      // update those via 'name of configured remote role - name of PROJ role', see
      // below.
    }

    if (namaChanged || projRolePermissionAssociationChanged) {
      cache.invalidateRemoteIdAndRolesCacheWith(newRole.getRoleName());
    }
    if (namaChanged) {
      cache.invalidateRemoteIdAndRolesCacheWith(oldRoleStatus.roleName);
    }
    if (typeChanged || projRolePermissionAssociationChanged) {
      cache.invalidateAllDefaultOf(newRole.getRoleType());
    }
    if (typeChanged) {
      cache.invalidateAllDefaultOf(oldRoleStatus.roleType);
    }
  }

  @Override
  public boolean neeFreshLocalUserCache(PrjRoleModel newRole, @Nullable RoleStatus oldRoleStatus) {
    Set<PrjPermissionsModel> p2 = newRole.getPermissions();
    if (oldRoleStatus.permissionIds.size() != p2.size()) {
      return true;
    }

    Set<Integer> ids2 =
        p2.parallelStream().map(p -> p.getPermissionId()).collect(Collectors.toSet());
    return !oldRoleStatus.permissionIds.equals(ids2);
  }

  public static class RoleStatus {
    public String roleType;
    public String roleName;
    public Set<Integer> userIds;
    public Set<Integer> permissionIds;

    public RoleStatus(
        String roleType,
        String roleName,
        @Nullable Set<Integer> permissionIds,
        @Nullable Set<Integer> userIds) {
      this.roleName = roleName;
      this.roleType = roleType;
      this.permissionIds = permissionIds;
      this.userIds = userIds;
    }

    public static RoleStatus getRoleStatus(PrjRoleModel role) {
      return new RoleStatus(
          role.getRoleType(),
          role.getRoleName(),
          role.getPermissions()
              .parallelStream()
              .map(p -> p.getPermissionId())
              .collect(Collectors.toSet()),
          role.getUsers().parallelStream().map(u -> u.getUserId()).collect(Collectors.toSet()));
    }
  }
}
