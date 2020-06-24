package com.coustomer.projs.web.rest.controller.serviceProvider.transfer;

import java.lang.reflect.Proxy;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;

@Configuration
public class MultipartResolverFactory {
  // For thread safe and performance concern, use special file item factory and file uploader
  @Autowired
  @Qualifier(value = "commonResolver")
  private MultipartResolver common;

  @Autowired
  @Qualifier(value = "largeFileResolver")
  private MultipartResolver forLargeFile;

  private boolean isUploadingImageRequest(HttpServletRequest request) {
    return request
        .getRequestURL()
        .toString()
        .endsWith(PROJFirmwareImageUpgradeController.IMAGE_PUT_MAPPING_URL);
  }

  @Bean(DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)
  public MultipartResolver get() {
    return (MultipartResolver)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[] {MultipartResolver.class},
            (proxy, method, args) -> {
              if (method.getName().equals("isMultipart")) {
                return method.invoke(common, args);
              }
              if (args != null
                  && args[0] != null
                  && HttpServletRequest.class.isAssignableFrom(args[0].getClass())) {
                return method.invoke(
                    isUploadingImageRequest((HttpServletRequest) args[0]) ? forLargeFile : common,
                    args);
              }
              return method.invoke(common, args);
            });
  }
}
