
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
import java.net.URISyntaxException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
  private static final String BPJ_PROJ_DOCKER_GATEWAY_IP = "169.254.255.49";

  private static final String BPJ_REQ_XSRF_TOKEN_KEY = "XSRF-TOKEN";
  private static final String BPJ_REQ_SESSION_KEY = "CURRENT_SESSION";
  //
  private static final String PROTOCOL = "https";
  private static final int PORT = 443;
  private static final String HOSTNAME = getFMHostIp();
  private static final String BPJ_HEART_BEAT_RESOUCE_PATH = "/cgi-bin/module/flatui_proxy";

  public static final String PROXIED_BPJ_USER_KEY = "proxied_managerapp_user";

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
   * Currently its default value is "169.254.255.49" checked by 'docker inspect'
   * The default behavior might change in the future.
   */
  private static String getFMHostIp() {
    return BPJ_PROJ_DOCKER_GATEWAY_IP;
  }

  /**
   * According to the design document valid BPJ request should have cookie and the cookie should
   * have "XSRF-TOKEN" and CURRENT_SESSION, If Cookie does not contain "XSRF-TOKEN", double check it
   * from request header.
   */
  private static boolean isValidBPJHttpServletRequest(HttpServletRequest req) {
    Cookie[] cookies = req.getCookies();
    if (cookies == null) {
      return false;
    }

    boolean hasSession = false;
    boolean hasXsrfToken = false;
    for (Cookie c : cookies) {
      if (c.getName().equalsIgnoreCase(BPJ_REQ_XSRF_TOKEN_KEY)) {
        hasXsrfToken = true;
      }
      if (c.getName().equalsIgnoreCase(BPJ_REQ_SESSION_KEY)) {
        hasSession = true;
      }
    }
    if (!hasXsrfToken) {
      hasXsrfToken = req.getHeader(BPJ_REQ_XSRF_TOKEN_KEY) != null;
    }
    log.info(
        String.format(
            "The request coockie contains session: %s; The request contains %s: %s",
            hasSession, BPJ_REQ_XSRF_TOKEN_KEY, hasXsrfToken));
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
   * According to the design document, request payload format is: '{"gSessionInfo":{"type":"sys"}}'
   * '{"url":"/gui/sys/session" , "method": "get"}'
   */
  private static JsonElement getBPJSessionValidateRequestPayLoad() {
    JsonObject payload = new JsonObject();
    payload.addProperty("url", "/gui/sys/session");
    payload.addProperty("method", "get");
    return payload;
  }

  private static boolean logErrorReturnFalse(String error) {
    return logErrorReturnFalse(error, null);
  }

  private static boolean logErrorReturnFalse(String error, @Nullable RuntimeException exception) {
    String errorMessage =
        "Error: BPJ API " + BPJ_HEART_BEAT_RESOUCE_PATH + " Response payload:" + error;

    if (exception == null) {
      log.error(errorMessage);
    } else {
      log.error(errorMessage, exception);
    }
    return false;
  }

  /**
   * Response(with 200) message format is like:
   *
   * <pre>
   * <code>
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
   * </code>
   * </pre>
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
      HttpServletRequest req, RestAPIEcho response) {
    log.info(
        String.format(
            "BPJ heartbreat call response code: %d, message: %s ",
            response.code, response.message));
    if (response.code != HttpURLConnection.HTTP_OK) {
      log.error("Resonse Code:" + response.code);
      return false;
    }

    try {
      JsonElement result = response.message.getAsJsonObject().get("result");
      if (result == null) {
        return logErrorReturnFalse("Has not the member with the specified 'result'");
      }

      JsonArray array = result.getAsJsonArray();
      if (array.size() == 0) {
        return logErrorReturnFalse("'result' array is empty");
      }
      JsonElement data = array.get(0).getAsJsonObject().get("data");
      if (data == null) {
        return logErrorReturnFalse("Has not the member with the specified 'data'");
      }
      JsonElement valid = data.getAsJsonObject().get("valid");
      if (valid == null) {
        return logErrorReturnFalse("Has not the member with the specified 'valid'");
      }

      int value = -1;
      try {
        value = valid.getAsInt();
      } catch (ClassCastException e) {
        return logErrorReturnFalse("'valid' value is not expected integer type");
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
      return logErrorReturnFalse("JSON syntax exception", e);
    } catch (JsonParseException e) {
      return logErrorReturnFalse("Failure in parsing", e);
    } catch (IllegalStateException e) {
      return logErrorReturnFalse("Value is not expected JSON type ", e);
    }
  }

  /**
   * Assume caller of this method has verified the PROJ is running in docker deployed in BPJ VM
   *
   * @throws URISyntaxException
   * @throws MalformedURLException
   */
  private static boolean isHttpServletRequestFromBackProjectVm(HttpServletRequest req)
      throws MalformedURLException, URISyntaxException {
    if (!isValidBPJHttpServletRequest(req)) {
      return false;
    }
    return validBPJHeartBeatResponseAndKeepValidBPJUserName(
        req,
        callRestAPI(
            new URIBuilder()
                .setScheme(PROTOCOL)
                .setHost(HOSTNAME)
                .setPort(PORT)
                .setPath(BPJ_HEART_BEAT_RESOUCE_PATH)
                .build(),
            HttpMethod.POST,
            10000,
            Optional.of(getBPJSessionValidateRequestPayLoad()),
            (con) ->
                con.setRequestProperty(
                    HttpHeaders.COOKIE, buildSessionCookie(req, BPJ_REQ_SESSION_KEY)),
            (con) ->
                con.setRequestProperty(
                    BPJ_REQ_XSRF_TOKEN_KEY,
                    buildXsrfTokenHeaderValue(req, BPJ_REQ_XSRF_TOKEN_KEY))));
  }

  private class WrappedRequest extends HttpServletRequestWrapper {
    public WrappedRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getParameter(String name) {
      if (name.equalsIgnoreCase(
          UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY)) {
        return "spuser";
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

  @RequestMapping(
    value = DEFAULT_AUTHEN_URL,
    method = {RequestMethod.GET}
  )
  void authenticateDefaultLocalAdmin(ModelMap map, HttpServletRequest req, HttpServletResponse res)
      throws Exception {
    log.info(
        String.format(
            "Request: %s %s from %s ", req.getMethod(), req.getRequestURI(), req.getRemoteHost()));
    if (!isHttpServletRequestFromBackProjectVm(req)) {
      throw new AccessDeniedException("Only support this request send from BPJ");
    }
    if (!inBackProjectVm()) {
      throw new AccessDeniedException("Current PROJ is not in docker within BPJ VM");
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
