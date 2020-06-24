package com.coustomer.projs.web.rest.controller.serviceProvider.transfer;

import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

/**
 * Based on <a href="http://commons.apache.org/proper/commons-fileupload">Apache Commons
 * FileUpload</a> 1.4
 */
@Component(value = "largeFileResolver")
class ImageUploadCommonsFileUploadSupport extends CommonsMultipartResolver {
  public ImageUploadCommonsFileUploadSupport() {
    super();
    setResolveLazily(false); // explicitly
    DiskFileItemFactory fac = getFileItemFactory();
    // save to disk directly
    fac.setSizeThreshold(0);
    fac.setRepository(PROJFirmwareImageUpgradeController.imgFileDir);
  }

  @Override
  protected FileUpload prepareFileUpload(String encoding) {
    // Security concern:
    // Tomcat can handler Long.MAX_VALUE size request.
    // But user server disk space has not that much and changing.
    FileUpload fileUload = super.prepareFileUpload(encoding);
    long currentFreeSpace = getFileItemFactory().getRepository().getFreeSpace();
    // 1/2: other programming, current and future usage.
    // 1/2: cached and target file; does not support concurrent for security and integration
    // concern.
    long limit =
        Math.min(currentFreeSpace / 4, PROJFirmwareImageUpgradeController.PROJ_IMAGE_MAX_SIZE);
    fileUload.setSizeMax(limit);
    fileUload.setFileSizeMax(limit);
    return fileUload;
  }
}
