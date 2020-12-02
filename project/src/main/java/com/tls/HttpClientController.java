
import static com.coustomer.projs.listener.InitBackProjectConfigure.getHostBackProjectVmInnerIp;
import static com.coustomer.projs.listener.InitBackProjectConfigure.getHostBackProjectVmPassword;
import static com.coustomer.projs.listener.InitBackProjectConfigure.getHostBackProjectVmPortJson;
import static com.coustomer.projs.listener.InitBackProjectConfigure.getHostBackProjectVmUserName;
import static com.coustomer.projs.util.PrjHttpsConnection.callRestAPI;
import static com.coustomer.tool.PrjRuningEnv.isDockerRuningInBackProjectVm;
import static com.coustomer.tool.PrjRuningEnv.isRunningInsideDocker;

import com.coustomer.projs.util.PrjHttpsConnection.RestAPIEcho;
import com.coustomer.projs.util.MetaDataGenerator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotAllowedException;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/** Used by PROJ docker in BPJ VM only */
@Controller
public class PrjLocalAdminDefaultController {
  private static Logger log = LogManager.getLogger(PrjLocalAdminDefaultController.class);
  private static final String DEFAULT_AUTHEN_URL = "/adminuser/local/default";

  private static final String BPJ_REQ_XSRF_TOKEN_KEY = "XSRF-TOKEN";
  private static final String BPJ_REQ_SESSION_KEY = "CURRENT_SESSION";
  public static final String PROXIED_BPJ_USER_KEY = "proxied_managerapp_user";
  //
  private static final String PROTOCOL = "https";
  private static final String BPJ_HEART_BEAT_RESOUCE_PATH = "/cgi-bin/module/flatui_proxy";

  private static final String BPJ_JSON_RPC_URL = "/jsonrpc";
  private static final String BPJ_LOGIN_URL = "/sys/login/user";
  private static final String BPJ_SETTING_URL = "/cli/global/system/admin/setting";

  /**
   * <pre>
   * Only in BPJ VM, not in
   * - other VM,
   * - Docker
   * - non-VM
   * - common non-Docker/VM
   */
  public static boolean inBackProjectVm() {
    return isRunningInsideDocker() && isDockerRuningInBackProjectVm();
  }

  /**
   * <pre>
   * * Docker PROJ host machine IP: use the default gateway IP of docker container.
   *
   * This rely on the fact that the Docker host is reachable through the address
   * of the Docker bridge, which happens to be the default gateway for the
   * container. And the default gateway happens to be an IP address of the Docker
   * host. This might change in the future.
   *
   * Check the default route of the container: /sbin/ip route|awk '/default/ {
   * print $3 }'
   *
   * Before Docker providing an introspection API to get the host machine IP, an
   * alternative to get host machine IP is to replace docker-compose with docker
   * command once only 1 docker image is left, like:
   *
   * docker run --rm -it --add-host "managerapp.localhost:$(<ip command here>)"
   *
   * * Default router gateway value:
   *
   * From docker compose yml version 3, the gateway in not configurable. See
   * https://github.com/docker/compose/issues/6569
   *
   * Gateway value is decided by the docker compose yml
   * 'networks>app_net>ipam>config>': 'subnet: "169.254.255.48/28"' according to
   * design document
   * https://pmdb.compnet.com/ProjectManagement/viewDocument.php?id=9360
   *
   * Currently its default value is "159.244.245.39" checked by 'docker inspect'
   * The default behavior might change in the future.
   */
  private String getFMHostIp() {
    return getHostBackProjectVmInnerIp(env);
  }

  /**
   * According to the design document valid BPJ request should have cookie and the cookie should
   * have "XSRF-TOKEN" and CURRENT_SESSION, If Cookie does not contain "XSRF-TOKEN", double check it
   * from request header.
   */
  private static boolean isValidBPJHttpServletRequest(
      HttpServletRequest req, String managerappReqSessionKey, String managerappReqXsrfTokenKey) {
    Cookie[] cookies = req.getCookies();
    if (cookies == null) {
      return false;
    }

    boolean hasSession = false;
    boolean hasXsrfToken = false;
    for (Cookie c : cookies) {
      if (c.getName().equalsIgnoreCase(managerappReqXsrfTokenKey)) {
        hasXsrfToken = true;
      }
      if (c.getName().equalsIgnoreCase(managerappReqSessionKey)) {
        hasSession = true;
      }
    }
    if (!hasXsrfToken) {
      hasXsrfToken = req.getHeader(managerappReqXsrfTokenKey) != null;
    }
    log.info(
        String.format(
            "The request coockie contains session %s: %s; The request contains %s: %s",
            managerappReqSessionKey, hasSession, managerappReqXsrfTokenKey, hasXsrfToken));
    return hasSession && hasXsrfToken;
  }

  /** Assure request is valid */
  private static String getCookieValue(HttpServletRequest req, String key) {
    Cookie[] cookies = req.getCookies();
    for (Cookie c : cookies) {
      if (c.getName().equalsIgnoreCase(key)) {
        return c.getValue();
      }
    }
    return null;
  }

  /** Assure request is valid, Search order: request Cookie, then request header. */
  private static String buildXsrfTokenHeaderValue(HttpServletRequest req, String key) {
    String result = getCookieValue(req, key);
    if (result == null) {
      return req.getHeader(key);
    }
    return result;
  }

  /** Assure request is valid */
  private static String buildSessionCookie(HttpServletRequest req, String key) {
    return new StringBuilder().append(key).append("=").append(getCookieValue(req, key)).toString();
  }
  /**
   * According to the design document, request payload format is:
   *
   * <pre><code>
   * {
   *   "url":"/gui/sys/session",
   *   "method":"get"
   * }
   * </code></pre>
   */
  private static JsonElement getBPJSessionValidateRequestPayLoad() {
    JsonObject payload = new JsonObject();
    payload.addProperty("url", "/gui/sys/session");
    payload.addProperty("method", "get");
    return payload;
  }

  /**
   * According to the design document, request payload format is:
   *
   * <pre><code>
   * - https://fndn.compnet.net/index.php?/compapi/5-compmanager/173/
   * - https://pmdb.compnet.com/ProjectManagement/viewDocument.php?id=9360
   *
   * {
   *   "id":1,
   *   "method":"exec",
   *   "params":[
   *      {
   *         "data":{
   *            "passwd":"",
   *            "user":"__docker_compportal"
   *         },
   *         "url":"/sys/login/user"
   *      }
   *   ]
   * }
   *
   * </code></pre>
   */
  private static JsonElement getBPJLoginPayLoad(String user, String passwd, String url) {
    log.info(
        String.format(
            "Prepare BPJ JSON RPC login API payload with user:%s, passwd:%s, URL:%s",
            user, passwd, url));

    JsonObject up = new JsonObject();
    up.addProperty("passwd", passwd);
    up.addProperty("user", user);

    JsonObject du = new JsonObject();
    du.add("data", up);
    du.addProperty("url", url);

    JsonArray ar = new JsonArray();
    ar.add(du);

    JsonObject payload = new JsonObject();
    payload.addProperty("id", 1);
    payload.addProperty("method", "exec");
    payload.add("params", ar);
    log.info(payload.toString());
    return payload;
  }

  /**
   * According to
   * https://fndn.compnet.net/index.php?/compapi/5-compmanager/691/5/cli/system/admin/
   *
   * <pre><code>
   * {
   *   "session":"LLJchxPmICAxSZKt5l9Q2NMkerTZ1mj3A074Rj35TbO8n+blhQIKZvbTW",
   *   "id":1,
   *   "method":"get",
   *   "params":[
   *      {
   *         "url":"/cli/global/system/admin/setting"
   *      }
   *   ]
   * }
   * </code></pre>
   */
  private static JsonElement getBPJSettingsRequestPayLoad(String session) {
    JsonObject url = new JsonObject();
    url.addProperty("url", BPJ_SETTING_URL);

    JsonArray params = new JsonArray();
    params.add(url);

    JsonObject payload = new JsonObject();
    payload.addProperty("session", session);
    payload.addProperty("id", 1);
    payload.addProperty("method", "get");
    payload.add("params", params);
    log.info(payload.toString());
    return payload;
  }

  private static void logError(String error) {
    logError(error, null);
  }

  private static void logError(String error, @Nullable RuntimeException exception) {

    if (exception == null) {
      log.error(error);
    } else {
      log.error(error, exception);
    }
  }
  /**
   * Response(with 200) message format is like:
   *
   * <pre><code>
   * {
   *   "result":[
   *      {
   *         "data":{
   *            "admin_adom":"root",
   *            "admin_prof":"Super_User",
   *            "admin_user":"admin",
   *            "adom_list":[
   *
   *            ],
   *            "adom_override":0,
   *            "current_adom_name":"root",
   *            "login_user":"admin",
   *            "time_left":283,
   *            "timestamp":1597340634,
   *            "valid":1
   *         },
   *         "id":null,
   *         "status":{
   *            "code":0,
   *            "message":""
   *         },
   *         "url":"/gui/sys/session"
   *      }
   *   ]
   * }
   * </code></pre>
   *
   ** <pre>
   * Check
   *
   * - response code
   *
   * - response data, recommended by BPJ team 'Haijun Qiao <hjqiao@compnet.com>'
   *
   **
   * If BPJ user session is valid then keep its name, which will be used in audit
   * log.
   */
  private static boolean validBPJHeartBeatResponseAndKeepValidBPJUserName(
      HttpServletRequest req,
      RestAPIEcho response,
      TriConsumer<String, RestAPIEcho, Integer> restApiEchoValidConsumer) {
    restApiEchoValidConsumer.accept("Call BPJ heartbreat API", response, HttpURLConnection.HTTP_OK);

    try {
      String errorMessagePrefix = "BPJ heartbreat API response:";
      log.info(response.message.toString());
      JsonElement result = response.message.getAsJsonObject().get("result");
      if (result == null) {
        logError(errorMessagePrefix + "has not the 'result'");
        return false;
      }

      JsonArray array = result.getAsJsonArray();
      if (array.size() == 0) {
        logError(errorMessagePrefix + "'result' array is empty");
        return false;
      }
      JsonElement data = array.get(0).getAsJsonObject().get("data");
      if (data == null) {
        logError(errorMessagePrefix + "has not 'data'");
        return false;
      }
      JsonElement valid = data.getAsJsonObject().get("valid");
      if (valid == null) {
        logError(errorMessagePrefix + "has not 'valid'");
        return false;
      }

      int value = -1;
      try {
        value = valid.getAsInt();
      } catch (ClassCastException e) {
        logError(errorMessagePrefix + "'valid' value is not expected Integer type");
        return false;
      }
      if (value == 1) {
        JsonElement loginUser = data.getAsJsonObject().get("login_user");
        String managerappUser = loginUser.getAsString().trim();
        if (!managerappUser.isEmpty()) {
          req.setAttribute(PROXIED_BPJ_USER_KEY, managerappUser);
        }
      }
      return value == 1;

    } catch (JsonSyntaxException e) {
      logError("JSON syntax exception", e);
      return false;
    } catch (JsonParseException e) {
      logError("Failure in parsing", e);
      return false;
    } catch (IllegalStateException e) {
      logError("Value is not expected JSON type ", e);
      return false;
    }
  }

  private class WrappedRequest extends HttpServletRequestWrapper {
    public WrappedRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getParameter(String name) {
      if (name.equalsIgnoreCase(
          UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY)) {
        return "default-admin";
      }
      if (name.equalsIgnoreCase(
          UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY)) {
        return "test123";
      }
      return this.getRequest().getParameter(name);
    }
  }

  private UsernamePasswordAuthenticationFilter authenticator;
  @Autowired private ApplicationContext ctx;
  private Environment env;
  private URI hostBackProjectJsonRpcApiUri;
  private TriConsumer<String, RestAPIEcho, Integer> restApiEchoValidConsumer =
      (apiDes, response, rightCode) -> {
        if (response.code != rightCode) {
          String message =
              String.format("Error: %s: %d: %s", apiDes, response.code, response.message);
          logError(message);
          throw new RuntimeException(message);
        }
      };

  @Autowired
  void setEnvironment(Environment env) {
    this.env = env;
  }

  private URI getHostBackProjectJsonRpcApiUri() throws URISyntaxException {
    if (hostBackProjectJsonRpcApiUri == null) {
      hostBackProjectJsonRpcApiUri =
          new URIBuilder()
              .setScheme(PROTOCOL)
              .setHost(getFMHostIp())
              .setPort(getHostBackProjectVmPortJson(env))
              .setPath(BPJ_JSON_RPC_URL)
              .build();
    }
    log.info(hostBackProjectJsonRpcApiUri.toString());
    return hostBackProjectJsonRpcApiUri;
  }

  /**
   * BPJ Login API Response
   *
   * <pre><code>
   * According to
   * - https://fndn.compnet.net/index.php?/compapi/5-compmanager/173/
   * - https://pmdb.compnet.com/ProjectManagement/viewDocument.php?id=9360
   * {
   *   "id":1,
   *   "result":[
   *      {
   *         "status":{
   *            "code":0,
   *            "message":"OK"
   *         },
   *         "url":"\/sys\/login\/user"
   *      }
   *   ],
   *   "session":"p+oChRfdWoRH6U0IOAj+vKal\/0fjJ\/VPus8fB03h0uQs0OWg=="
   * }
   *
   * </code></pre>
   */
  private Optional<String> getSessionFromBPJLoginApiResponse(
      RestAPIEcho response, TriConsumer<String, RestAPIEcho, Integer> restApiEchoValidConsumer) {
    String managerappBuildInAdminUserForPrjDocker = getHostBackProjectVmUserName(env);
    restApiEchoValidConsumer.accept(
        "Call BPJ JSON RPC login API with build in user" + managerappBuildInAdminUserForPrjDocker,
        response,
        HttpURLConnection.HTTP_OK);

    try {
      String message = "";
      JsonElement resultArray = response.message.getAsJsonObject().get("result");
      log.info(response.message.toString());
      String errorMessagePrefix = "BPJ login API response:";
      if (resultArray == null) {
        message = errorMessagePrefix + "has not the expected 'result'";
        logError(message);
        throw new RuntimeException(message);
      }

      JsonArray array = resultArray.getAsJsonArray();
      if (array.size() == 0) {
        message = errorMessagePrefix + "'result' is empty array";
        logError(message);
        throw new RuntimeException(message);
      }
      JsonElement status = array.get(0).getAsJsonObject().get("status");
      if (status == null) {
        message = errorMessagePrefix + "has not the expected 'result'.'status'";
        logError(message);
        throw new RuntimeException(message);
      }

      JsonObject statusValue = status.getAsJsonObject();
      JsonElement code = statusValue.get("code");
      if (code == null) {
        message = errorMessagePrefix + "has not the expected 'result'.'status'.'code'";
        logError(message);
        throw new RuntimeException(message);
      }
      int statusCode = -1;

      try {
        statusCode = code.getAsInt();
      } catch (ClassCastException e) {
        message = errorMessagePrefix + "'result'.'status'.'code' is not Int type";
        logError(message);
        throw new RuntimeException(message);
      }
      if (statusCode != 0) {
        message = errorMessagePrefix + "'result'.'status'.'code' value is not 0";
        logError(message);
        throw new RuntimeException(message);
      }

      JsonElement session = response.message.getAsJsonObject().get("session");
      if (session == null) {
        message = errorMessagePrefix + "has not the expected 'session'";
        logError(message);
        throw new RuntimeException(message);
      }

      String value = session.getAsString();
      if (value == null || value.length() == 0) {
        message = String.format(errorMessagePrefix + "'session' value is invalid: %s", value);
        logError(message);
        throw new RuntimeException(message);
      }

      return Optional.of(value);
    } catch (JsonSyntaxException e) {
      logError("JSON syntax exception", e);
      throw e;
    } catch (JsonParseException e) {
      logError("Failure in parsing", e);
      throw e;
    } catch (IllegalStateException e) {
      logError("Value is not expected JSON type ", e);
      throw e;
    }
  }

  /**
   * Call BPJ login API to get session of the build-in admin user for PROJ docker: "user":
   * "__docker_compportal","passwd": ""
   *
   * <p>BPJ need make sure the build-in admin user for PROJ docker is ready in advance
   *
   * <p>see design document: https://pmdb.compnet.com/ProjectManagement/viewDocument.php?id=9360
   *
   * <pre>
   * <code>
   * "-BPJ will create special account for each product by prefix "__docker_" + product name so
   * sdwancontroller's special admin will be "__docker_sdwancontroller".
   * -This special admin will be created dynamically and will not have password, but it will have
   * a trust host of a private IP of that dokcer container.
   * -All private IP will be allocated by BPJ."
   * </code></pre>
   *
   * and 'How to get the BPJ session token from the docker container app'
   *
   * <p>*case: Call BPJ login API with curl
   *
   * <pre><code>
   * curl -k -S \
   * -H "Content-Type:application/json;charset=UTF-8" \
   * -H "Accept:application/json;charset=UTF-8" \
   * -X POST \
   * --data '{ "id": 1, "method": "exec",  "params": [ {  "data": { "passwd": "",
   *   "user": "__docker_compportal"  },  "url": "/sys/login/user"  } ] }' \
   * -D- https://159.244.245.39:443/jsonrpc
   * </code></pre>
   *
   * *Feedback
   *
   * <pre><code>
   * {
   *    "id":1,
   *    "result":[
   *       {
   *         "status":{
   *             "code":0,
   *            "message":"OK"
   *          },
   *          "url":"\/sys\/login\/user"
   *       }
   *   ],
   *    "session":"p+oChRfdWoRH6U0IOAj+vKal\/0fjJ\/VPus8fB03h0uZhQHcQs0OWg=="
   * }
   * </code></pre>
   *
   * @throws URISyntaxException
   * @throws GeneralSecurityException
   */
  private Optional<String> getHostBPJSessionForBuildInUser()
      throws URISyntaxException, GeneralSecurityException {
    return getSessionFromBPJLoginApiResponse(
        callRestAPI(
            getHostBackProjectJsonRpcApiUri(),
            HttpMethod.POST,
            10000,
            Optional.of(
                getBPJLoginPayLoad(
                    getHostBackProjectVmUserName(env), getHostBackProjectVmPassword(env), BPJ_LOGIN_URL))),
        restApiEchoValidConsumer);
  }

  /**
   * Note: The "cookie-name-prefix" only exists in special demo BPJ image. It does exist in normal
   * BPJ image. Refer
   * https://fndn.compnet.net/index.php?/compapi/5-compmanager/691/5/cli/system/admin/
   *
   * <pre><code>
   * {
   *   "id":1,
   *   "result":[
   *      {
   *         "data":{
   *            "_comment":"other elements of date are cut to save space here",
   *            "cookie-name-prefix":"7f3eb41c-b7e6-11ea-9199-00090f000c03"
   *         },
   *         "status":{
   *            "code":0,
   *            "message":"OK"
   *         },
   *         "url":"\/cli\/global\/system\/admin\/setting"
   *      }
   *   ]
   * }
   * </code></pre>
   */
  private Optional<String> getCookieNamePrefixFromBPJSettingsResponse(
      RestAPIEcho response, TriConsumer<String, RestAPIEcho, Integer> restApiEchoValidConsumer) {
    restApiEchoValidConsumer.accept(
        "Call BPJ JSON RPC setting API", response, HttpURLConnection.HTTP_OK);
    try {
      String message = "";
      JsonElement result = response.message.getAsJsonObject().get("result");
      log.info(response.message.toString());
      String errorMessagePrefix = "BPJ setting API response:";
      if (result == null) {
        message = errorMessagePrefix + "has not the expected 'result'";
        logError(message);
        throw new RuntimeException(message);
      }

      JsonArray array = result.getAsJsonArray();
      if (array.size() == 0) {
        message = errorMessagePrefix + "'result' is empty array";
        logError(message);
        throw new RuntimeException(message);
      }
      JsonElement data = array.get(0).getAsJsonObject().get("data");
      if (data == null) {
        message = errorMessagePrefix + "'result'.'data' is empty";
        logError(message);
        throw new RuntimeException(message);
      }

      JsonElement cookieNamePrefix = data.getAsJsonObject().get("cookie-name-prefix");
      if (cookieNamePrefix == null) { // it is normal
        log.warn(errorMessagePrefix + "'result'.'data'.'cookie-name-prefix' does not exist");
        return Optional.empty();
      } else { // for special demo BPJ image
        String prefixValue = cookieNamePrefix.getAsString();
        if (prefixValue == null || prefixValue.length() == 0) {
          log.warn(
              errorMessagePrefix
                  + "'result'.'data'.'cookie-name-prefix' value is null/empty."
                  + " Took as no exist");
          // not valid prefix
          return Optional.empty();
        }
        return Optional.of(prefixValue);
      }
    } catch (JsonSyntaxException e) {
      logError("JSON syntax exception", e);
      throw e;
    } catch (JsonParseException e) {
      logError("Failure in parsing", e);
      throw e;
    } catch (IllegalStateException e) {
      logError("Value is not expected JSON type ", e);
      throw e;
    }
  }

  /**
   * According to
   * https://fndn.compnet.net/index.php?/compapi/5-compmanager/691/5/cli/system/admin/
   *
   * <pre><code>
   * case with curl
   * curl -k -S \
   *   -H "Content-Type:application/json;charset=UTF-8" \
   *   -H "Accept:application/json;charset=UTF-8"  \
   *   -X POST \
   *   --data '{"session":"LLJchxPmICAxSZXiUMm983MgkQ==","id":1,"method":"get",
   *   "params":[{"url":"/cli/global/system/admin/setting"}]}' \
   *   -D- https://172.30.71.135:443/jsonrpc
   * </code></pre>
   *
   * @throws GeneralSecurityException
   */
  private Optional<String> getHostBPJHttpRequestSessionCookieNamePrefix()
      throws URISyntaxException, GeneralSecurityException {
    Optional<String> session = getHostBPJSessionForBuildInUser();
    if (session.isPresent()) {
      return getCookieNamePrefixFromBPJSettingsResponse(
          callRestAPI(
              getHostBackProjectJsonRpcApiUri(),
              HttpMethod.POST,
              10000,
              Optional.of(getBPJSettingsRequestPayLoad(session.get()))),
          restApiEchoValidConsumer);
    }
    return Optional.empty();
  }
  /**
   * Assume caller of this method has verified the PROJ is running in docker deployed in BPJ VM
   *
   * @throws URISyntaxException
   * @throws MalformedURLException
   * @throws GeneralSecurityException
   */
  private boolean isHttpServletRequestFromBackProjectVm(HttpServletRequest req)
      throws MalformedURLException, URISyntaxException, GeneralSecurityException {
    Optional<String> profixForSessionAndToken = getHostBPJHttpRequestSessionCookieNamePrefix();
    final String sessionCookieName =
        profixForSessionAndToken.isPresent()
            ? profixForSessionAndToken.get() + "_" + BPJ_REQ_SESSION_KEY
            : BPJ_REQ_SESSION_KEY;
    final String sessionCsrfTokenName =
        profixForSessionAndToken.isPresent()
            ? profixForSessionAndToken.get() + "_" + BPJ_REQ_XSRF_TOKEN_KEY
            : BPJ_REQ_XSRF_TOKEN_KEY;

    if (!isValidBPJHttpServletRequest(req, sessionCookieName, sessionCsrfTokenName)) {
      return false;
    }
    return validBPJHeartBeatResponseAndKeepValidBPJUserName(
        req,
        callRestAPI(
            new URIBuilder()
                .setScheme(PROTOCOL)
                .setHost(getFMHostIp())
                .setPort(getHostBackProjectVmPortJson(env))
                .setPath(BPJ_HEART_BEAT_RESOUCE_PATH)
                .build(),
            HttpMethod.POST,
            10000,
            Optional.of(getBPJSessionValidateRequestPayLoad()),
            (con) ->
                con.setRequestProperty(
                    HttpHeaders.COOKIE, buildSessionCookie(req, sessionCookieName)),
            (con) ->
                con.setRequestProperty(
                    sessionCsrfTokenName, buildXsrfTokenHeaderValue(req, sessionCsrfTokenName))),
        restApiEchoValidConsumer);
  }

  @RequestMapping(
    value = DEFAULT_AUTHEN_URL,
    method = {RequestMethod.GET}
  )
  void authenticateDefaultLocalAdmin(ModelMap map, HttpServletRequest req, HttpServletResponse res)
      throws Exception {
    if (!inBackProjectVm()) {
      throw new AccessDeniedException("Current PROJ is not in docker within BPJ VM");
    }
    log.info(
        String.format(
            "Request: %s %s from %s ", req.getMethod(), req.getRequestURI(), req.getRemoteHost()));
    if (!isHttpServletRequestFromBackProjectVm(req)) {
      throw new AccessDeniedException("Only support this request send from BPJ");
    }

    if (MetaDataGenerator.isSAMLEnabled()) {
      throw new NotAllowedException("Does not support for SAML authenticon");
    }
    if (this.authenticator == null) {
      this.authenticator =
          (UsernamePasswordAuthenticationFilter)
              ctx.getBean(
                  "org.springframework.security.web.authentication."
                      + "UsernamePasswordAuthenticationFilter#1");
    }
    try {
      authenticator.setRequiresAuthenticationRequestMatcher(
          new AntPathRequestMatcher(DEFAULT_AUTHEN_URL, "GET"));
      authenticator.setPostOnly(false);
      authenticator.doFilter(new WrappedRequest(req), res, null);
    } finally {
      authenticator.setRequiresAuthenticationRequestMatcher(
          new AntPathRequestMatcher("/login", "POST"));
      authenticator.setPostOnly(true);
      req.getSession(false)
          .setAttribute(PROXIED_BPJ_USER_KEY, req.getAttribute(PROXIED_BPJ_USER_KEY));
    }
  }
}
