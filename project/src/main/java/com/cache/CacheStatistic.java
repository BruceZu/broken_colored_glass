
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import javax.annotation.PostConstruct;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.hibernate.SessionFactory;
import org.hibernate.cache.ehcache.EhCacheRegionFactory;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.internal.CacheImpl;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class CacheStatistic {
  private static final Logger logger = LoggerFactory.getLogger(CacheStatistic.class);

  private boolean someCacheNeedStatistic = false;
  @Autowired private UserPermissionCache userPemissionsCache;
  @Autowired private SessionFactory sessionFactory;

  // Todo: Exposing Hibernate (cache) statistics through JMX with Spring in Tomcat
  private void watchStatus() {
    if (!logger.isDebugEnabled()) {
      return;
    }

    Map<String, CacheConfiguration> configs =
        ConfigurationFactory.parseConfiguration().getCacheConfigurations();
    Statistics statics = sessionFactory.getStatistics();
    String[] regainNames = statics.getSecondLevelCacheRegionNames();
    StringBuilder sb = new StringBuilder();
    sb.append(userPemissionsCache.statisticInfo());
    for (String name : regainNames) {
      if (configs.get(name).getStatistics()) {
        SecondLevelCacheStatistics secondStatics = statics.getSecondLevelCacheStatistics(name);
        sb.append("\n===Cache: " + name)
            .append("\nElements in Memory: " + secondStatics.getElementCountInMemory())
            .append("\nElements on Disk: " + secondStatics.getElementCountOnDisk())
            .append("\nSize in Memory: " + secondStatics.getSizeInMemory())
            .append("\nHit Count: " + secondStatics.getHitCount())
            .append("\nPut Count: " + secondStatics.getPutCount())
            .append("\nMiss Count: " + secondStatics.getMissCount());
      }
    }
    logger.debug(sb.toString());
  }

  @PostConstruct
  void checkStatisticConfig() {
    if (((SessionFactoryImpl) sessionFactory)
        .getProperties()
        .get("hibernate.generate_statistics")
        .equals("true")) {
      CacheImpl cacheAccess = (CacheImpl) sessionFactory.getCache();
      RegionFactory regionFactory = cacheAccess.getRegionFactory();
      try {
        if (EhCacheRegionFactory.class.isAssignableFrom(regionFactory.getClass())) {
          Field field = regionFactory.getClass().getSuperclass().getDeclaredField("manager");
          field.setAccessible(true);
          CacheManager manager = (CacheManager) field.get(regionFactory);
          Collection<CacheConfiguration> configs =
              ConfigurationFactory.parseConfiguration(
                      new ByteArrayInputStream(
                          manager
                              .getActiveConfigurationText()
                              .getBytes(StandardCharsets.UTF_8.name())))
                  .getCacheConfigurations()
                  .values();
          for (CacheConfiguration config : configs) {
            if (config.getStatistics()) {
              someCacheNeedStatistic = true;
              break;
            }
          }
        }
      } catch (NoSuchFieldException
          | SecurityException
          | IllegalArgumentException
          | IllegalAccessException
          | UnsupportedEncodingException
          | CacheException e) {
        logger.error("Failed to check Hibernate second level entity cache statistic status", e);
      }
    }
  }

  public void log() {
    if (someCacheNeedStatistic) {
      watchStatus();
    }
  }
}
