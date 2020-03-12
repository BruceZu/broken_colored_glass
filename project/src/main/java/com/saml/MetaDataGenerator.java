package saml;

import com.coustomer.projs.db.PrjDBConnection;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.cert.CertificateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import javax.servlet.ServletContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.util.Log;
import org.opensaml.util.resource.FilesystemResource;
import org.opensaml.util.resource.Resource;
import org.opensaml.util.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.saml.SAMLLogoutProcessingFilter;
import org.springframework.security.saml.SAMLProcessingFilter;
import org.springframework.stereotype.Component;

@Component
public class MetaDataGenerator {
  private static final Logger logger = LogManager.getLogger(MetaDataGenerator.class);
  private static final String CLASS_PATH = "/WEB-INF/classes";
  private static final String RESOURCE_IDP = "/spring/metadata/idp.xml";
  private static final String RESOURCE_SP = "/spring/metadata/sp.xml";
  private ServletContext servletContext;

  @Autowired
  public void setServletContext(ServletContext servletContext) {
    this.servletContext = servletContext;
  }

  /**
   * <pre >
   * Logic: PROJ only support one serviceProvider now.
   * the value `SSO` of coustomerpmcdb.proj_sso_saml_profile.sso_enabled means it is remote not local
   * authentication.
   * when it is SAML there is one record in table
   * coustomerpmcdb.proj_sso_saml_profile else there is not record.
   * May 14, 2018.
   */
  public static boolean isSAMLEnabled() {
    boolean isSAMLEnabled = false;
    try (Statement stmt = PrjDBConnection.getInstance().getConnection().createStatement(); ) {
      ResultSet rs = stmt.executeQuery("SELECT sso_enabled FROM coustomerpmcdb.pmc_service_serviceProvider;");

      while (rs.next()) {
        if (rs.getBoolean(1)) {
          isSAMLEnabled = true;
          break;
        }
      }
      if (isSAMLEnabled) {
        rs = stmt.executeQuery("SELECT count(sso_id) as size FROM coustomerpmcdb.proj_sso_saml_profile;");
        while (rs.next()) {
          return rs.getInt(1) != 0;
        }
      }

      return false;
    } catch (Exception e) {
      logger.error("Failed to get authentication info of PROJ", e);
      return false;
    }
  }

  public boolean provideIdpLogoutServiceEndpointUrl() {
    return !getIdpConfig().getSingleLogout().trim().isEmpty();
  }

  public static final String IDP = CLASS_PATH + RESOURCE_IDP;
  public static final String SP = CLASS_PATH + RESOURCE_SP;

  public static class SAMLSecurityCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return isSAMLEnabled();
    }
  }

  public static class NoSAMLSecurityCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return !isSAMLEnabled();
    }
  }

  @Configuration
  @ImportResource("classpath:/spring/applicationSecurity.xml")
  @Conditional(value = NoSAMLSecurityCondition.class)
  class NoSAMLSecurity {}

  @Configuration
  @ImportResource("classpath:/spring/applicationSecurity_Saml.xml")
  @Conditional(value = SAMLSecurityCondition.class)
  class SAMLSecurity {
    @Bean("IDP")
    public Resource getIDPResource() throws Exception {
      try {
        Path idpPath = null;
        if (servletContext.getResource(IDP) == null) {
          idpPath = generateIdpMetaData();
        } else {
          idpPath = pathOf(IDP);
        }
        return new FilesystemResource(idpPath.toUri());
      } catch (IOException | URISyntaxException | ResourceException e) {
        logger.error("Failed to provide IDP Resouce", e);
        throw e;
      }
    }

    @Bean("SP")
    public Resource getSPResource() throws Exception {
      try {
        Path spPath = null;
        if (servletContext.getResource(SP) == null) {
          spPath = generateSpMetaData();
        } else {
          spPath = pathOf(SP);
        }
        return new FilesystemResource(spPath.toUri());
      } catch (IOException | URISyntaxException | ResourceException e) {
        logger.error("Failed to provide SP Resouce", e);
        throw e;
      }
    }
  }

  // Todo: user jdbc connection pool
  private PrjIdpConfig getIdpConfig() {

    PrjIdpConfig idpConfig = null;
    logger.info("Building the idp config ");

    String query =
        "SELECT Sso_Idp_Entity_Id, Sso_Service_EndPoint_Post_Url,  "
            + "Sso_Service_EndPoint_Redirect_Url , Sso_Certificate, "
            + "Sso_Logout_Service_EndPoint_Url  FROM proj_sso_saml_profile  limit 1";

    try (Statement stmt = PrjDBConnection.getInstance().getConnection().createStatement(); ) {

      ResultSet rs = stmt.executeQuery(query);
      idpConfig = new PrjIdpConfig();

      while (rs.next()) {
        logger.info("Fetched the idp config row ");

        String entityId = rs.getString(1);
        String ssoUrlPost = rs.getString(2);
        String ssoUrlRirect = rs.getString(3);
        String idpCert = rs.getString(4);
        String singleLogout = rs.getString(5);
        idpConfig.setCert(idpCert);
        idpConfig.setEntityId(entityId);
        idpConfig.setLoginUrlPost(ssoUrlPost);
        idpConfig.setLoginUrlRedirect(ssoUrlRirect);
        idpConfig.setSingleLogout(singleLogout);
        // now we are making the asserting signed as true;
        idpConfig.setWantAssertionSigned(true);
        return idpConfig;
      }

    } catch (SQLException se) {
      logger.error("getting buildIdpConfig " + se);
    }
    logger.info("No idp config found ");

    return null;
  }

  // Todo: user jdbc connection pool
  private PrjSpConfig getSpConfig() {

    PrjSpConfig spConfig = null;

    logger.info("Building the sp config ");
    String query = "SELECT Sso_Sp_Entity_Id, Sso_Audience_Url FROM proj_sso_saml_profile  limit 1";

    try (Statement stmt = PrjDBConnection.getInstance().getConnection().createStatement(); ) {

      ResultSet rs = stmt.executeQuery(query);
      spConfig = new PrjSpConfig();
      while (rs.next()) {
        spConfig.setEntityId(rs.getString(1));
        spConfig.setAcs(rs.getString(2));
        return spConfig;
      }

    } catch (Exception ce) {
      Log.error(ce);
      spConfig = null;
    }

    logger.info("No sp config found ");
    return spConfig;
  }

  private Path generateIdpMetaData() throws MalformedURLException, IOException, URISyntaxException {
    PrjIdpConfig idpConfig = getIdpConfig();
    if (idpConfig == null) {
      Log.error("can not got configuration of IDP");
      return null;
    }
    logger.info("Generating the idp metadata ");

    StringBuffer sb = new StringBuffer();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<EntityDescriptor xmlns=\"urn:oasis:names:tc:SAML:2.0:metadata\" entityID=\"");
    sb.append(idpConfig.getEntityId());
    sb.append("\">\n");
    sb.append(
        "<IDPSSODescriptor WantAuthnRequestsSigned=\"true\" "
            + "protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">\n");
    sb.append("<KeyDescriptor use=\"signing\">\n");
    sb.append("<ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n");
    sb.append("<ds:X509Data>\n");
    sb.append("<ds:X509Certificate>");
    sb.append(idpConfig.getCert());
    sb.append("</ds:X509Certificate>\n");
    sb.append("</ds:X509Data>\n");
    sb.append("</ds:KeyInfo>\n");
    sb.append("</KeyDescriptor>\n");

    sb.append("<KeyDescriptor use=\"encryption\">\n");
    sb.append("<ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n");
    sb.append("<ds:X509Data>\n");
    sb.append("<ds:X509Certificate>");

    sb.append(idpConfig.getCert());

    sb.append("</ds:X509Certificate>\n");
    sb.append("</ds:X509Data>\n");
    sb.append("</ds:KeyInfo>\n");
    sb.append("</KeyDescriptor>\n");

    sb.append(
        "<NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</NameIDFormat>\n");
    sb.append(
        "<NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified</NameIDFormat>\n");
    sb.append("<NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:transient</NameIDFormat>\n");
    sb.append(
        "<NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:persistent</NameIDFormat>\n");
    sb.append(
        "<NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName</NameIDFormat>\n");
    sb.append(
        "<SingleSignOnService "
            + "Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" "
            + "Location=\"");
    sb.append(idpConfig.getLoginUrlPost());
    sb.append("\"/>\n");
    sb.append(
        "<SingleSignOnService "
            + "Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect\" "
            + "Location=\"");
    sb.append(idpConfig.getLoginUrlRedirect());
    sb.append("\"/>\n");

    if (idpConfig.getSingleLogout().trim().isEmpty()) {
      logger.warn("'IDP Logout Service Endpoint' is not provided, so no logout function.");
    } else {
      sb.append(
          "<SingleLogoutService "
              + "Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" "
              + "Location=\"");
      sb.append(idpConfig.getSingleLogout());
      sb.append("\"/>\n");

      sb.append(
          "<SingleLogoutService "
              + "Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect\" L"
              + "ocation=\"");
      sb.append(idpConfig.getSingleLogout());
      sb.append("\"/>\n");
    }

    sb.append("</IDPSSODescriptor>\n");
    sb.append("</EntityDescriptor>\n");

    logger.info("The idp configuration " + sb.toString());
    return Files.write(
        pathOf(IDP),
        sb.toString().getBytes(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private Path pathOf(String file) throws URISyntaxException, IOException {
    Path path;
    if (servletContext.getResource(file) == null) {
      // getRealPath() is not recommended. It returns null if the servlet container cannot
      // translate the virtual path to a real path for any reason such as when the content is
      // being made available from a .war archive.
      path = new File(servletContext.getRealPath(file)).toPath();
      Path parentDir = path.getParent();
      // Files.write(..., StandardOpenOption.CREATE) can create a file, can't create a directory.
      // need to check the directory
      if (!Files.exists(parentDir)) {
        Files.createDirectories(parentDir);
      }
      return path;
    } else {
      return new File(servletContext.getResource(file).toURI()).toPath();
    }
  }

  private Path generateSpMetaData()
      throws MalformedURLException, IOException, URISyntaxException, CertificateException {

    PrjSpConfig spConfig = getSpConfig();
    if (spConfig == null) {
      Log.error("can not got configuration of SP");
      return null;
    }

    String assertionConsumerServiceLocation = spConfig.getAcs();
    String singleLogoutServiceLocation =
        assertionConsumerServiceLocation.replaceAll(
            SAMLProcessingFilter.FILTER_URL, SAMLLogoutProcessingFilter.FILTER_URL);

    String spCert; // TODO: use customized certificate: projCertService.getCert(true) to replace the
    // current default one;

    spCert =
        "MIIDUjCCAjqgAwIBAgIEUOLIQTANBgkqhkiG9w0BAQUFADBrMQ"
            + "swCQYDVQQGEwJGSTEQMA4GA1UECBMHVXVzaW1hYTERMA8GA1"
            + "UEBxMISGVsc2lua2kxGDAWBgNVBAoTD1JNNSBTb2Z0d2FyZS"
            + "BPeTEMMAoGA1UECwwDUiZEMQ8wDQYDVQQDEwZhcG9sbG8wHh"
            + "cNMTMwMTAxMTEyODAxWhcNMjIxMjMwMTEyODAxWjBrMQswCQ"
            + "YDVQQGEwJGSTEQMA4GA1UECBMHVXVzaW1hYTERMA8GA1UEBx"
            + "MISGVsc2lua2kxGDAWBgNVBAoTD1JNNSBTb2Z0d2FyZSBPeT"
            + "EMMAoGA1UECwwDUiZEMQ8wDQYDVQQDEwZhcG9sbG8wggEiMA"
            + "0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCXqP0wqL2Ai1"
            + "haeTj0alwsLafhrDtUt00E5xc7kdD7PISRA270ZmpYMB4W24"
            + "Uk2QkuwaBp6dI/yRdUvPfOT45YZrqIxMe2451PAQWtEKWF5Z"
            + "13F0J4/lB71TtrzyH94RnqSHXFfvRN8EY/rzuEzrpZrHdtNs"
            + "9LRyLqcRTXMMO4z7QghBuxh3K5gu7KqxpHx6No83WNZj4B3g"
            + "vWLRWv05nbXh/F9YMeQClTX1iBNAhLQxWhwXMKB4u1iPQ/KS"
            + "aal3R26pONUUmu1qVtU1quQozSTPD8HvsDqGG19v2+/N3uf5"
            + "dRYtvEPfwXN3wIY+/R93vBA6lnl5nTctZIRsyg0Gv5AgMBAA"
            + "EwDQYJKoZIhvcNAQEFBQADggEBAFQwAAYUjso1VwjDc2kypK"
            + "/RRcB8bMAUUIG0hLGL82IvnKouGixGqAcULwQKIvTs6uGmlg"
            + "bSG6Gn5ROb2mlBztXqQ49zRvi5qWNRttir6eyqwRFGOM6A8r"
            + "xj3Jhxi2Vb/MJn7XzeVHHLzA1sV5hwl/2PLnaL2h9WyG9QwB"
            + "bwtmkMEqUt/dgixKb1Rvby/tBuRogWgPONNSACiW+Z5o8UdA"
            + "OqNMZQozD/i1gOjBXoF0F5OksjQN7xoQZLj9xXefxCFQ69FP"
            + "cFDeEWbHwSoBy5hLPNALaEUoa5zPDwlixwRjFQTc5XXaRpgI"
            + "jy/2gsL8+Y5QRhyXnLqgO67BlLYW/GuHE=";

    logger.info("Generating the sp metadata ");
    StringBuffer sb = new StringBuffer();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append(
        "<EntityDescriptor xmlns=\"urn:oasis:names:tc:SAML:2.0:metadata\" \n"
            + "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"\n"
            + "xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"\n"
            + "entityID=\"");
    sb.append(spConfig.getEntityId());
    sb.append("\">\n");
    sb.append(
        "<SPSSODescriptor AuthnRequestsSigned=\"true\" "
            + "WantAssertionsSigned=\"true\" "
            + "protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:1.1:protocol"
            + " urn:oasis:names:tc:SAML:2.0:protocol\">\n");
    sb.append("<KeyDescriptor>\n");
    sb.append("<ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n");
    sb.append("<ds:X509Data>\n");
    sb.append("<ds:X509Certificate>");
    sb.append(spCert);
    sb.append("</ds:X509Certificate>\n");
    sb.append("</ds:X509Data>\n");
    sb.append("</ds:KeyInfo>\n");
    sb.append("</KeyDescriptor>\n");

    sb.append(
        "<SingleLogoutService "
            + "Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" "
            + "Location=\"");
    sb.append(singleLogoutServiceLocation);
    sb.append("\"/>\n");

    sb.append(
        "<SingleLogoutService "
            + "Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect\" "
            + "Location=\"");
    sb.append(singleLogoutServiceLocation);
    sb.append("\"/>\n");
    sb.append(
        "<NameIDFormat>urn:oasis:names:tc:SAML:1.1:"
            + "nameid-format:emailAddress</NameIDFormat>\n");
    sb.append(
        "<NameIDFormat>urn:oasis:names:tc:SAML:1.1:"
            + "nameid-format:unspecified</NameIDFormat>\n");
    sb.append(
        "<AssertionConsumerService "
            + "Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" "
            + "Location=\"");
    sb.append(assertionConsumerServiceLocation);
    sb.append("\" index=\"0\" isDefault=\"true\"/>\n");
    sb.append(
        "<AssertionConsumerService "
            + "Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Artifact\" "
            + "Location=\"");
    sb.append(assertionConsumerServiceLocation);
    sb.append("\" index=\"1\"/>\n");
    sb.append(
        "<AssertionConsumerService "
            + "Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST-SimpleSign\" "
            + "Location=\"");
    sb.append(assertionConsumerServiceLocation);
    sb.append("\" index=\"2\"/>\n");

    sb.append(
        "<AssertionConsumerService "
            + "Binding=\"urn:oasis:names:tc:SAML:1.1:profiles:browser-post\" "
            + "Location=\"");
    sb.append(assertionConsumerServiceLocation);
    sb.append("\" index=\"3\"/>\n");

    sb.append(
        "<AssertionConsumerService "
            + "Binding=\"urn:oasis:names:tc:SAML:1.1:profiles:artifact-01\" "
            + "Location=\"");
    sb.append(assertionConsumerServiceLocation);
    sb.append("\" index=\"4\"/>\n");

    sb.append("</SPSSODescriptor>\n");
    sb.append("</EntityDescriptor>\n");

    logger.info("The string is " + sb.toString());
    return Files.write(
        pathOf(SP),
        sb.toString().getBytes(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  /** The caller should check the status of return code */
  public int enableSAMLAuthenticationAndGenerateConfigureFiles() {
    try {
      generateIdpMetaData();
      generateSpMetaData();
      logger.info("Created new SAML configure files");
      return 0;
    } catch (Throwable e) {
      logger.error("Failed to configure SAML ", e);
      return -1;
    }
  }

  // Todo: should get JDBC connect from the pool
  public HashMap<String, String> ssoAttrubiteValuesFromDB() {

    HashMap<String, String> attributeValues = new HashMap<String, String>();

    String query =
        "SELECT Sso_Saml_Attr_GroupName, Sso_Saml_Attr_MemberOf, "
            + "Sso_Logout_Service_EndPoint_Url, Sso_Error_Url, "
            + "Sso_Saml_Attr_Site FROM proj_sso_saml_profile  limit 1";

    try (Connection con = PrjDBConnection.getInstance().getConnection();
        Statement stmt = con.createStatement()) {
      attributeValues.put("SSORoleAttribute", "");
      attributeValues.put("SSOMemberOfAttribute", "");
      attributeValues.put("SSOLogoutURL", "");
      attributeValues.put("SSOErrorURL", "");
      attributeValues.put("SSOSiteAttribute", "");
      ResultSet rs = stmt.executeQuery(query);
      while (rs.next()) {
        attributeValues.put(
            "SSORoleAttribute",
            rs.getString("Sso_Saml_Attr_GroupName") != null
                ? rs.getString("Sso_Saml_Attr_GroupName")
                : "");
        attributeValues.put(
            "SSOMemberOfAttribute",
            rs.getString("Sso_Saml_Attr_MemberOf") != null
                ? rs.getString("Sso_Saml_Attr_MemberOf")
                : "");
        attributeValues.put(
            "SSOLogoutURL",
            rs.getString("Sso_Logout_Service_EndPoint_Url") != null
                ? rs.getString("Sso_Logout_Service_EndPoint_Url")
                : "");
        attributeValues.put(
            "SSOErrorURL",
            rs.getString("Sso_Error_Url") != null ? rs.getString("Sso_Error_Url") : "");
        attributeValues.put(
            "SSOSiteAttribute",
            rs.getString("Sso_Saml_Attr_Site") != null ? rs.getString("Sso_Saml_Attr_Site") : "");
      }
    } catch (SQLException se) {
      logger.error("getting collector list" + se);
    }
    return attributeValues;
  }
}
