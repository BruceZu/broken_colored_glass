
import static com.coustomer.projs.controller.provider.PrjscompManagerController.performAddcompmanager;

import com.coustomer.projs.dao.impl.util.QueryWrapper;
import com.coustomer.projs.model.PrjcompmanagerModel;
import com.coustomer.projs.service.PrjcompmanagerService;
import com.coustomer.projs.util.security.AesOfbCipher;
import com.coustomer.pmc.service.ServiceProviderService;
import com.coustomer.tool.PrjRuningEnv;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Initialization work for Docker PROJ in compManager virtual machine environment */
@PropertySource("classpath:managerapp.properties")
@Component
public class InitBackProjectConfigure {
  private static final Logger logger = LoggerFactory.getLogger(InitBackProjectConfigure.class);
  private static String BPJ_NAME_PREFIX = "LOCAL_BPJ_";
  private static int DEFAULT_PROVIDER_ID = 1;
  private static int DEFAULT_PROVIDER_USER_ID = 10001;
  private static String DEFAULT_PROVIDER_USER_EMAIL = "default-admin";
  private static String FREQUENCY_DAILY = "1 00:00:00";

  private Environment env;
  private PrjcompmanagerService projcompmanagerService;
  private ServiceProviderService serviceProviderService;
  private SessionFactory sessionFactory;

  private Object lock = new Object();
  private boolean noInitialized = true;

  @Autowired
  void setSessionFactory(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Autowired
  void setServiceProviderService(ServiceProviderService serviceProviderService) {
    this.serviceProviderService = serviceProviderService;
  }

  @Autowired
  void setPrjcompmanagerService(PrjcompmanagerService projcompmanagerService) {
    this.projcompmanagerService = projcompmanagerService;
  }

  @Autowired
  void setEnvironment(Environment env) {
    this.env = env;
  }

  private PrjcompmanagerModel initPrjcompmanagerModel() throws GeneralSecurityException {
    String managerappVmPassword = getHostBackProjectVmPassword(env);
    String managerappVmIp = getHostBackProjectVmInnerIp(env);
    int managerappVmPortJson = getHostBackProjectVmPortJson(env);
    int managerappVmPortXml = Integer.valueOf(env.getProperty("managerapp.vm.port.xml"));
    String managerappVmUserName = getHostBackProjectVmUserName(env);
    String managerappName = getBackProjectName(managerappVmIp);

    PrjcompmanagerModel model = new PrjcompmanagerModel();
    model.setcompManagerName(managerappName);
    model.setAdminUserName(managerappVmUserName);
    model.setAdminPassword(managerappVmPassword);
    model.setIpAddress(managerappVmIp);
    model.setFrequencyValue(FREQUENCY_DAILY);
    model.setPortNumber(managerappVmPortJson);
    model.setXmlPort(managerappVmPortXml);
    model.setHaMode(""); // default value in the case the compmanage is down.

    return model;
  }

  private void createBackProjectRecord() throws IOException, GeneralSecurityException {
    JSONObject re =
        performAddcompmanager(
            initPrjcompmanagerModel(),
            DEFAULT_PROVIDER_ID,
            DEFAULT_PROVIDER_USER_ID,
            DEFAULT_PROVIDER_USER_EMAIL,
            null,
            projcompmanagerService,
            serviceProviderService);
    logger.info("Status of initializing default compManager:" + re.toString());
  }

  private String getBackProjectName(String managerappIp) {
    return BPJ_NAME_PREFIX + managerappIp;
  }

  private boolean isExist() {
    String managerappIp = env.getProperty("managerapp.vm.ip");
    String managerappName = getBackProjectName(env.getProperty("managerapp.vm.ip"));
    long records =
        new QueryWrapper<>(PrjcompmanagerModel.class, sessionFactory.getCurrentSession(), true)
            .andEqual("compManagerName", managerappName)
            .andEqual("ipAddress", managerappIp)
            .rowCount(true);
    return records >= 1;
  }

  /** BPJ existence checking need to be in the same transaction with BPJ creating */
  private void initializeBackProject() throws IOException, GeneralSecurityException {
    if (!isExist()) {
      createBackProjectRecord();
    }
  }

  public static String getHostBackProjectVmUserName(Environment env) {
    return env.getProperty("managerapp.vm.user.name");
  }

  public static String getHostBackProjectVmPassword(Environment env) throws GeneralSecurityException {
    String managerappVmPassword = env.getProperty("managerapp.vm.user.password");
    Optional<String> plainTextmanagerappVmPassword = AesOfbCipher.getInstance().decrypt(managerappVmPassword);
    if (!plainTextmanagerappVmPassword.isPresent()) {
      throw new RuntimeException(
          "Failed to decrypt the configured password of BPJ build-in admin user for PROJ docker");
    }

    return plainTextmanagerappVmPassword.get();
  }

  public static String getHostBackProjectVmInnerIp(Environment env) {
    return env.getProperty("managerapp.vm.ip");
  }

  public static int getHostBackProjectVmPortJson(Environment env) {
    return Integer.valueOf(env.getProperty("managerapp.vm.port.json"));
  }

  @EventListener
  @Async
  @Transactional
  public void onContextInitialized(ContextRefreshedEvent sce) throws Exception {
    if (PrjRuningEnv.isDockerRuningInBackProjectVm()) {
      synchronized (lock) {
        if (noInitialized) {
          try {
            initializeBackProject();
          } catch (Exception e) {
            logger.error("Failed to initialize BPJ", e);
            throw e;
          }
          noInitialized = false;
        }
      }
    }
  }
}
