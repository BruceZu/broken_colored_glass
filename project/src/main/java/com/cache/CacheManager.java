
import com.coustomer.projs.cache.CacheManagerImp.RoleStatus;
import com.coustomer.projs.model.PrjPermissionsModel;
import com.coustomer.projs.model.PrjRoleModel;
import com.coustomer.pmc.model.UserModel;
import java.util.Set;
import java.util.function.BiPredicate;

public interface CacheManager {

  /**
   * Scenario: Update local user(provider user or customer user) or delete local user For update
   * case: To make it easy, always assume the user-permission association is changed.
   */
  void freshCacheByUpdateDeleteLocalUser(UserModel user);

  /**
   * <pre>
   * Scenario: Delete customer and all his users
   * this will force any one line deleted user can not access any PROJ resource.
   * @param users
   */
  void freshCacheByDeleteLocalUsers(Set<UserModel> users);

  // Note: use this method only in test case under local authentication
  void invalidateAllLocal();

  /**
   * <pre>
   * Scenario: Update permission:
   * Only when a permission is updated, it is possible that cache need to update.
   * Because
   * - delete permission: require end user firstly to delete this permission's
   *   association with roles if have any
   * - create permission: the to-be created brand new permission
   *   still has not any association with any role.
   * For updating an existing permission:
   *
   *  - For local authentication: if there is association of 'PROJ role - this permission'
   *    and the role is used by some online user. need refresh these user's permission cache
   *  - For remote authentication, only when some association of 'PROJ role - this permission' exists
   *    it maybe need to refresh cache:
   *    -- For remote user with roleName(s), if the online user:
   *       --- is using the related role directly via roleName
   *       --- is using the related role via 'remote role - PROJ role' association if there is any.
   *    -- For remote user without rolesName(s), if the online user
   *      is using the role's permission via user type
   * @param toBeUpdatedPermissionModel
   */
  void freshCacheByUpdatedPermission(PrjPermissionsModel toBeUpdatedPermissionModel);

  /**
   * <pre>
   * Scenario: Add/Delete/Update remote role:
   *  - Add a new remote role
   *  - remote an remote role
   * Affect only remote authentication and remote user has configured role name(s)
   */
  void freshCacheByAddOrDeleteRemoteRole(String remoteRoleName);

  /**
   * <pre>
   * Scenario: Update remote role:
   *  - update remote role name which require refresh cache by old name and new name.
   *  - update 'remote role - PROJ role' association
   * Affect only remote authentication and remote user has configured role name(s)
   * @param remoteRoleName
   */
  void freshCacheByUpdateRemoteRole(Set<String> roleNames);

  /**
   * <pre>
   * Scenario: Delete a PROJ role:
   * End user has already been required to remove the role from
   * - 'local user - PROJ role' association
   * - 'remote role - PROJ role' association
   * So we can assume
   *  - No local user use this PROJ role
   *  - No remote user with remote role(s) use this PROJ role via
   *   'remote role - PROJ role' association
   * But in the following both cases the cache can be affected
   *  and need to refresh
   *  - remote user with remote role(s) use this PROJ role via
   *    role name
   *  - remote user without remote role(s) use this PROJ role
   *    via user type
   * @param roleType
   */
  void freshCacheByDeletePrjRole(PrjRoleModel role);

  /**
   * <pre>
   * Scenario: Add a PROJ role: similar the scenario of delete a
   *           PROJ role.
   * For a just created brand new role, it can not be found in
   * - 'local user - PROJ role' association
   * - 'remote role - PROJ role' association
   * So we can assume
   *  - No local user use this PROJ role
   *  - No remote user with remote role(s) use this PROJ role
   *    via 'remote role - PROJ role' association
   * But in the following both cases the cache can be affected
   * and need to refresh
   *  - remote user with remote role(s) use this PROJ role via
   *    role name
   *  - remote user without remote role(s) use this PROJ role
   *    via user type
   * @param roleType
   */
  void freshCacheByAddPrjRole(PrjRoleModel role);

  /**
   * <pre>
   * Scenario: update PROJ role
   * - update user type
   *   -- only affect remote user without configured role name(s)
   * - update role name
   *   -- only affect remote user with configured role name(s)
   * - update 'PROJ role - permission' association
   *   -- affect local authentication
   *   -- affect remote authentication
   *      --- affect remote user with configured role name(s)
   *          ---- via 'configured remote role name - PROJ role name' directly
   *          ---- via 'configured remote role name - Remote Role -
   *              (Remote Role - PROJ role) - PROJ role'
   *      --- affect remote user without configured role name(s)
   */
  void freshCacheByUpdatePrjRole(
      PrjRoleModel newRole, RoleStatus oldRoleStatus, BiPredicate<PrjRoleModel, RoleStatus> judge);

  boolean neeFreshLocalUserCache(PrjRoleModel newRole, RoleStatus oldRoleStatus);
}
