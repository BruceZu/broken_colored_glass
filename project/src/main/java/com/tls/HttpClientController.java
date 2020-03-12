package tls;

import static  tool.RuningEnv.isDockerRuningInBackProjectVm;
import static  tool.RuningEnv.isRunningInsideDocker;

import com.coustomer.projs.util.MetaDataGenerator;
import com.google.common.base.Charsets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Optional;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotAllowedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/** Used by PROJ docker in BPJ VM only */
@Controller
public class HttpClientController {
  private static Logger log = LogManager.getLogger(PrjLocalAdminDefaultController.class);
  private static final String DEFAULT_AUTHEN_URL = "/adminuser/local/default";
  private static final String BPJ_PROJ_DOCKER_GATEWAY_IP = "106.2.3.49";

  private static final String BPJ_REQ_XSRF_TOKEN_KEY = "XSRF-TOKEN";
  private static final String BPJ_REQ_SESSION_KEY = "CURRENT_SESSION";
  //
  private static final String PROTOCOL = "https";
  private static final int PORT = 443;
  private static final String HOSTNAME = getFMHostIp();
  private static final String BPJ_HEART_BEAT_RESOUCE_PATH = "/cgi-bin/module/HeartBeat";

  /**
   * <pre>
   * Only in BPJ VM, not in
   * - other VM,
   * - Docker
   * - non-VM
   * - common non-Docker/VM
   */
  private static boolean inBackProjectVm() {
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
   * 'networks>app_net>ipam>config>': 'subnet: "106.2.3.48/28"' according to
   * design document
   * https://pmdb.compnet.com/ProjectManagement/viewDocument.php?id=9360
   *
   * Currently its default value is "106.2.3.49" checked by 'docker inspect'
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

    boolean hasSession = false, hasXsrfToken = false;
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
   */
  private static JsonElement getBPJHeartBeatRequestPayLoad() {
    JsonObject value = new JsonObject();
    value.addProperty("type", "sys");

    JsonObject payload = new JsonObject();
    payload.add("gSessionInfo", value);
    return payload;
  }

  /**
   * Response(with 200) message format is like:
   *
   * <p>{ "data":{ "gSessionInfo":{ "time_left":814, "timestamp":1583194561, "valid":true } } }
   */
  private static boolean validBPJHeartBeatResponse(RestAPIEcho response) {
    log.info(
        String.format(
            "BPJ heartbreat call response code: %d, message: %s ",
            response.code, response.message));
    if (response.code != HttpURLConnection.HTTP_OK) {
      log.error(response.message);
      return false;
    }
    JsonObject jobject =
        response.message.getAsJsonObject().getAsJsonObject("data").getAsJsonObject("gSessionInfo");
    return Boolean.valueOf(jobject.get("valid").getAsString()).booleanValue();
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
    return validBPJHeartBeatResponse(
        callRestAPI(
            new URI(PROTOCOL, null, HOSTNAME, PORT, BPJ_HEART_BEAT_RESOUCE_PATH, null, null)
                .toURL(),
            HttpMethod.POST,
            10000,
            Optional.of(getBPJHeartBeatRequestPayLoad()),
            (con) ->
                con.setRequestProperty(
                    HttpHeaders.COOKIE, buildSessionCookie(req, BPJ_REQ_SESSION_KEY)),
            (con) ->
                con.setRequestProperty(
                    BPJ_REQ_XSRF_TOKEN_KEY,
                    buildXsrfTokenHeaderValue(req, BPJ_REQ_XSRF_TOKEN_KEY))));
  }

  public static class RestAPIEcho {
    /** status code from an HTTP response message. */
    int code;

    JsonElement message;

    public RestAPIEcho() {};

    public RestAPIEcho(int code, JsonElement message) {
      this.code = code;
      this.message = message;
    }
  }
  /**
   * @param url
   * @param mthod
   * @param timeoutInMilliseconds
   * @param payload
   * @return
   */
  @SafeVarargs
  public static RestAPIEcho callRestAPI(
      URL url,
      HttpMethod mthod,
      int timeoutInMilliseconds,
      Optional<JsonElement> payload,
      Consumer<URLConnection>... requestPropertyConsumers) {
    HttpsURLConnection con = null;
    RestAPIEcho result = new RestAPIEcho();
    try {
      con = (HttpsURLConnection) url.openConnection();
      con.setRequestMethod(mthod.toString());
      con.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
      con.setRequestProperty(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_UTF8_VALUE);
      for (Consumer<URLConnection> consumer : requestPropertyConsumers) {
        consumer.accept(con);
      }
      con.setDoInput(true);
      con.setConnectTimeout(timeoutInMilliseconds);

      if (payload.isPresent()) {
        con.setDoOutput(true);
        try (OutputStreamWriter out =
            new OutputStreamWriter(con.getOutputStream(), Charsets.UTF_8); ) {
          out.write(payload.get().toString());
          out.flush();
        }
      }

      try (BufferedReader readIn =
          new BufferedReader(new InputStreamReader(con.getInputStream(), Charsets.UTF_8))) {
        result.code = con.getResponseCode();

        String str;
        StringBuilder content = new StringBuilder(1024);
        while ((str = readIn.readLine()) != null) {
          content.append(str);
        }
        result.message = new JsonParser().parse(content.toString());
      }
    } catch (IOException excep) {
      log.error(excep);
    } finally {
      if (con != null) {
        con.disconnect();
      }
    }
    return result;
  }

  private class WrappedRequest extends HttpServletRequestWrapper {
    public WrappedRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getParameter(String name) {
      if (name.equalsIgnoreCase(
          UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY)) {
        return "defaultuser";
      }
      if (name.equalsIgnoreCase(
          UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY)) {
        return "xxxxxx";
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
    }
  }
}
