package tls;

import HttpClientController.RestAPIEcho;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
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
import org.springframework.web.client.RestTemplate;

@Component
public class ConfigureHttpsURLConnection {
  private static final Logger log = LoggerFactory.getLogger(ConfigureHttpsURLConnection.class);
  private static final String JDK8_DEFAULT_TLS_PROTOCOL = "TLSv1.2";
  private static final String JDK8_SUPPORTED_TOP_TLS_PROTOCOL = "TLSv1.2";
  private static final String DEFAULT_TLS_PROTOCOL = JDK8_DEFAULT_TLS_PROTOCOL;
  private static final String SSLCONTEXT_ALGORITHM = JDK8_SUPPORTED_TOP_TLS_PROTOCOL;
  private static String configuredEnabledTLSProtocols = DEFAULT_TLS_PROTOCOL;

  private static SSLSocketFactory getSSLSocketFactory()
      throws NoSuchAlgorithmException, KeyManagementException {
    // Create a trust manager that does not validate certificate chains
    TrustManager[] trustManagers =
        new TrustManager[] {
          new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
              return null;
            }
          }
        };

    SSLContext sc;

    sc = SSLContext.getInstance(SSLCONTEXT_ALGORITHM);
    sc.init(null, trustManagers, new SecureRandom());
    log.info(
        "SSLContext supported TLS protocols:"
            + Arrays.toString(sc.getSupportedSSLParameters().getProtocols()));
    log.info(
        "SSLContext default TLS protocols:"
            + Arrays.toString(sc.getDefaultSSLParameters().getProtocols()));
    return sc.getSocketFactory();
  }

  /**
   * based on org.apache.httpcomponents httpclient 4.5.3
   *
   * <p>Make sure use end-user configured enabled TLS version
   */
  private static CloseableHttpClient getCloseableHttpClient()
      throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
    LayeredConnectionSocketFactory sslConnectionSocketFactory =
        new SSLConnectionSocketFactory(
            getSSLSocketFactory(),
            configuredEnabledTLSProtocols.split(","),
            null,
            (hostname, session) -> true);
    return HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory).build();
  }

  /**
   * based on org.apache.httpcomponents httpclient 4.5.3
   *
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
  public static RestAPIEcho callRestAPIWithRestTemplate(
      URI uri,
      int timeoutInMilliseconds,
      @Nullable HttpHeaders requestHeaders,
      HttpMethod method,
      @Nullable JsonElement requestPayLoad)
      throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException {
    try (CloseableHttpClient httpClient = getCloseableHttpClient()) {
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
   * value according to the peers situation e.g. BPJ, FAC.
   *
   * In case end-user did not configure any TLS protocols, use the JDK8 default
   * TLSv1.2
   *
   * Note: - PROJ as the Java client of HTTPS/TLS communication will negotiate with
   * the peer, e.g.BPJ, FAC. Both sides will negotiate the strongest shared
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
      HttpsURLConnection.setDefaultSSLSocketFactory(getSSLSocketFactory());
      HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    } catch (NoSuchAlgorithmException | KeyManagementException exception) {
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
