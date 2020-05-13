package tls;

import com.google.common.base.Charsets;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.sql.DataSource;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.PrivateKeyStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.openjsse.net.ssl.OpenJSSE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class ConfigureHttpsURLConnection {
  private static final Logger log = LoggerFactory.getLogger(ConfigureHttpsURLConnection.class);
  private static final String JDK8_DEFAULT_TLS_PROTOCOL = "TLSv1.3";
  private static final String JDK8_SUPPORTED_TOP_TLS_PROTOCOL = "TLSv1.3";
  private static final String DEFAULT_TLS_PROTOCOL = JDK8_DEFAULT_TLS_PROTOCOL;

  private static final String SSLCONTEXT_ALGORITHM = JDK8_SUPPORTED_TOP_TLS_PROTOCOL;
  private static String configuredEnabledTLSProtocols = DEFAULT_TLS_PROTOCOL;
  // Default trust manager that does not validate peer certificate/chains
  private static TrustManager defaultTrustManager =
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }
      };

  private static SSLSocketFactory getDefaultSSLSocketFactory()
      throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException,
          CertificateException, IOException, UnrecoverableKeyException {
    // Create a trust manager that does not validate certificate chains
    SSLContext sc = SSLContext.getInstance(SSLCONTEXT_ALGORITHM);
    sc.init(null, new TrustManager[] {defaultTrustManager}, new SecureRandom());
    log.debug(
        "SSLContext supported TLS protocols:"
            + Arrays.toString(sc.getSupportedSSLParameters().getProtocols()));
    log.debug(
        "SSLContext default TLS protocols:"
            + Arrays.toString(sc.getDefaultSSLParameters().getProtocols()));
    return sc.getSocketFactory();
  }

  private static SSLSocketFactory getSSLSocketFactory(
      @Nullable File clientCert,
      @Nullable String storePassword,
      @Nullable String keyPassword,
      @Nullable PrivateKeyStrategy aliasStrategy,
      @Nullable File peerCert,
      @Nullable String peerStorePassword,
      @Nullable TrustStrategy trustStrategy)
      throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException,
          CertificateException, IOException, UnrecoverableKeyException {
    SSLContextBuilder builder = SSLContexts.custom();
    if (clientCert != null) {
      // provide certificate for this side authentication
      builder.loadKeyMaterial(
          clientCert, storePassword.toCharArray(), keyPassword.toCharArray(), aliasStrategy);
    }

    if (peerCert != null) {
      // provide certificate for peer authentication
      builder.loadTrustMaterial(peerCert, peerStorePassword.toCharArray(), trustStrategy);
    } else {
      Field trustmanagersField =
          ReflectionUtils.findField(SSLContextBuilder.class, "trustmanagers");
      ReflectionUtils.makeAccessible(trustmanagersField);
      @SuppressWarnings("unchecked")
      Set<TrustManager> trustmanagers =
          (Set<TrustManager>) ReflectionUtils.getField(trustmanagersField, builder);
      trustmanagers.add(defaultTrustManager);
    }
    builder.setSecureRandom(new SecureRandom());
    builder.useProtocol(SSLCONTEXT_ALGORITHM);
    SSLContext sc = builder.build();

    log.debug(
        "SSLContext supported TLS protocols:"
            + Arrays.toString(sc.getSupportedSSLParameters().getProtocols()));
    log.debug(
        "SSLContext default TLS protocols:"
            + Arrays.toString(sc.getDefaultSSLParameters().getProtocols()));
    SSLSocketFactory result = sc.getSocketFactory();
    return result;
  }

  /** based on org.apache.httpcomponents httpclient 4.5.3 */
  public static CloseableHttpClient getCloseableHttpClient(
      @Nullable File clientCert,
      @Nullable String storePassword,
      @Nullable String keyPassword,
      @Nullable PrivateKeyStrategy aliasStrategy,
      @Nullable File peerCert,
      @Nullable String peerStorePassword,
      @Nullable TrustStrategy trustStrategy)
      throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException,
          CertificateException, IOException, UnrecoverableKeyException {
    LayeredConnectionSocketFactory sslConnectionSocketFactory =
        new SSLConnectionSocketFactory(
            getSSLSocketFactory(
                clientCert,
                storePassword,
                keyPassword,
                aliasStrategy,
                peerCert,
                peerStorePassword,
                trustStrategy),
            configuredEnabledTLSProtocols.split(","),
            null,
            (hostname, session) -> true);
    return HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory).build();
  }

  public static class RestAPIEcho {
    /** status code from an HTTP response message. */
    public int code;

    public JsonElement message;

    public RestAPIEcho() {}

    public RestAPIEcho(int code, JsonElement message) {
      this.code = code;
      this.message = message;
    }
  }

/** based on org.apache.httpcomponents httpclient 4.5.3
 * <pre>
 * Usage case as an alternative of HttpClientController.callRestAPI():
 *
 * {@code
 * public static RestAPIEcho callRestAPIWithHttpClinet(HttpServletRequest req)
 * throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException,
 * URISyntaxException {
 * HttpHeaders requestHeaders = new HttpHeaders();
 * requestHeaders.add(HttpHeaders.COOKIE, buildSessionCookie(req, BPJ_REQ_SESSION_KEY));
 * requestHeaders.add(
 * BPJ_REQ_XSRF_TOKEN_KEY, buildXsrfTokenHeaderValue(req, BPJ_REQ_XSRF_TOKEN_KEY));
 * URI uri =
 * new URIBuilder()
 * .setScheme(PROTOCOL)
 * .setHost("10.106.6.221")
 * .setPort(PORT)
 * .setPath(BPJ_HEART_BEAT_RESOUCE_PATH)
 * .build();
 * return   ConfigureHttpsURLConnection.callRestAPIWithRestTemplate(
 * uri, 10000, requestHeaders, HttpMethod.POST, getBPJHeartBeatRequestPayLoad());
 *
 * }
 * }
 * </pre>
 */
  /** based on org.apache.httpcomponents httpclient 4.5.3 */
  public RestAPIEcho callRestAPIWithRestTemplate(
      URI uri,
      int timeoutInMilliseconds,
      @Nullable HttpHeaders requestHeaders,
      HttpMethod method,
      @Nullable JsonElement requestPayLoad,
      @Nullable File clientCert,
      @Nullable String storePassword,
      @Nullable String keyPassword,
      @Nullable PrivateKeyStrategy aliasStrategy,
      @Nullable File peerCert,
      @Nullable String peerStorePassword,
      @Nullable TrustStrategy trustStrategy)
      throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException,
          CertificateException, UnrecoverableKeyException {
    try (CloseableHttpClient httpClient =
        getCloseableHttpClient(
            clientCert,
            storePassword,
            keyPassword,
            aliasStrategy,
            peerCert,
            peerStorePassword,
            trustStrategy)) {
      HttpComponentsClientHttpRequestFactory requestFactory =
          new HttpComponentsClientHttpRequestFactory();
      requestFactory.setHttpClient(httpClient);
      requestFactory.setConnectTimeout(timeoutInMilliseconds);
      if (requestHeaders == null) {
        requestHeaders = new HttpHeaders();
      }
      requestHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
      requestHeaders.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_UTF8_VALUE);
      @SuppressWarnings("unchecked")
      HttpEntity<?> requestEntity =
          requestPayLoad == null
              ? new HttpEntity(requestHeaders)
              : new HttpEntity(requestPayLoad.toString(), requestHeaders);
      ResponseEntity<String> response =
          new RestTemplate(requestFactory)
              .exchange(uri.toURL().toString(), method, requestEntity, String.class);
      return new RestAPIEcho(
          response.getStatusCode().value(), new JsonParser().parse(response.getBody()));
    }
  }

  /**
   * @param url
   * @param method
   * @param timeoutInMilliseconds
   * @param payload
   * @return
   */
  @SafeVarargs
  public static RestAPIEcho callRestAPI(
      URI uri,
      HttpMethod method,
      int timeoutInMilliseconds,
      Optional<JsonElement> payload,
      Consumer<URLConnection>... requestPropertyConsumers) {
    HttpsURLConnection con = null;
    RestAPIEcho result = new RestAPIEcho();
    try {
      con = (HttpsURLConnection) uri.toURL().openConnection();
      con.setRequestMethod(method.toString());
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
      log.error("Error in callRestAPI()", excep);
    } finally {
      if (con != null) {
        con.disconnect();
      }
    }
    return result;
  }

  private DataSource dataSource;

  private Object lock = new Object();
  private boolean noInitialized = true;

  @Autowired()
  @Qualifier("transactionManager")
  void setDataSource(HibernateTransactionManager transactionManager) {
    this.dataSource = transactionManager.getDataSource();
  }

  private String getConfiguredTLSvProtocols() {
    String result = DEFAULT_TLS_PROTOCOL;
    try (Connection con = dataSource.getConnection();
        Statement stmt = con.createStatement()) {
      stmt.executeQuery("use coustomerpmcdb");
      ResultSet rs =
          stmt.executeQuery("select protocol from proj_protocols where enabled  = 'true'");
      StringBuilder sb = new StringBuilder();
      int cnt = 0;
      while (rs.next()) {
        if (cnt > 0) {
          sb.append(",");
        }
        sb.append(rs.getString("protocol"));
        cnt++;
      }
      if (cnt != 0) {
        result = sb.toString();
      }
      log.info("HttpsURLConnection configured default protocols:" + result);
    } catch (SQLException exc) {
      log.error("Unable to get the protocol in the portal database ", exc);
    }
    return result;
  }

  /**
   * <pre>
   * JVM system property for enabled TLS protocols used by Java clients which
   * obtain HTTPS connections through use of the HttpsURLConnection class or via
   * URL.openStream() operations.
   *
   * JDK 8 support TLSv1.2 (default), TLSv1.1, TLSv1, SSLv3
   *
   * JDK 7 support TLSv1.2, TLSv1.1, TLSv1 (default), SSLv3
   *
   * From GUI: Admin>Settings>Other>"TLS/SSL Versions" end-user can configure the
   * value according to the peers situation e.g. BPJ.
   *
   * In case end-user did not configure any TLS protocols, use the JDK8 default
   * TLSv1.2
   *
   * Note: - PROJ as the Java client of HTTPS/TLS communication will negotiate with
   * the peer, e.g.BPJ. Both sides will negotiate the strongest shared
   * protocol which may be not the default one. - In practice some servers were
   * not implemented properly and do not support protocol version negotiation.
   * This is a server bug called "version intolerance'
   *
   * @see javax.net.ssl.SSLSocket#setEnabledProtocols(String[])
   */
  private void setDefaultEnabledTLSProcotols() {
    configuredEnabledTLSProtocols = getConfiguredTLSvProtocols();
    System.setProperty("https.protocols", configuredEnabledTLSProtocols);
    // https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#https_porocols
    System.setProperty("jdk.tls.client.protocols", configuredEnabledTLSProtocols);
  }

  private void setDefaultSSLSocketFactoryAndDefaultHostnameVerifier() {
    try {
      Security.insertProviderAt(new OpenJSSE(), 4);
      HttpsURLConnection.setDefaultSSLSocketFactory(getDefaultSSLSocketFactory());
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    } catch (NoSuchAlgorithmException
        | KeyManagementException
        | KeyStoreException
        | CertificateException
        | IOException
        | UnrecoverableKeyException exception) {
      log.error("Exception in configure secure context for HttpsURLConnection ", exception);
    }
  }

  private void configure() {
    setDefaultEnabledTLSProcotols();
    setDefaultSSLSocketFactoryAndDefaultHostnameVerifier();
  }

  @EventListener
  public void onContextInitialized(ContextRefreshedEvent sce) throws Exception {
    synchronized (lock) {
      if (noInitialized) {
        try {
          configure();
        } catch (Exception excep) {
          log.error("Failed to configure for HttpsURLConnection", excep);
          throw excep;
        }
        noInitialized = false;
      }
    }
  }
}
}
