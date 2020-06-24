package com.coustomer.projs.web.rest.controller.serviceProvider.transfer;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

@RestController
public class PROJFirmwareImageUpgradeController extends AbstrctFileTransfer {
  private static Logger log =
      LogManager.getLogger(PROJFirmwareImageUpgradeController.class.getName());
  public static final String IMAGE_PUT_MAPPING_URL = "/sec/serviceProvider/firmwareimage";
  // For upload
  public static final String UPG_DIR = "/var/tmp";
  private static final String UPG_FNAME = "upg_image";
  static final int PROJ_IMAGE_MAX_SIZE = Integer.MAX_VALUE; // 2G.
  static File imgFileDir = new File(UPG_DIR);
  // For call 'reset 5'
  private static final long WAIT_TIME_IN_MILLISECONDES = 300000L; // 5 minutes

  private static Map<Integer, FirmwareImageUpgradeStatus> codeNameMap;
  private static int[] acceptedStatusCodes;

  private Object lock = new Object();
  private volatile boolean finished = true;
  private volatile boolean isResponseSend;

  enum FirmwareImageUpgradeStatus {
    // according to change 70849
    UPG_OK("Okay", 0),
    UPG_ERR("Upgrade image failed", 1),
    UPG_ERR_INVALID("Invalid image file", 2),
    UPG_ERR_TOO_BIG("New image is too big", 3),
    UPG_ERR_NOT_SUPPORTED("Upgrade to the specific version is not supported", 4),
    UPG_ERR_FIPS_INVALID_SIG("FIPS firmware signature verification failed", 10);

    private int statusCode;
    private String message;

    FirmwareImageUpgradeStatus(String message, int sc) {
      this.message = message;
      this.statusCode = sc;
    }

    public int getStatusCode() {
      return statusCode;
    }

    @Override
    public String toString() {
      return String.format("%d,%s,%s ", statusCode, super.toString(), message);
    }
  }

  public PROJFirmwareImageUpgradeController() {
    FirmwareImageUpgradeStatus[] values = FirmwareImageUpgradeStatus.values();
    acceptedStatusCodes = new int[values.length];
    codeNameMap = new HashMap<Integer, FirmwareImageUpgradeStatus>();
    for (int i = 0; i < values.length; i++) {
      codeNameMap.put(values[i].getStatusCode(), values[i]);
      acceptedStatusCodes[i] = values[i].getStatusCode();
    }

    if (!imgFileDir.mkdirs() && !imgFileDir.isDirectory()) {
      log.error("Failed to create image uploading dir: " + imgFileDir);
    }
  }

  private static void reject(String reason) {
    log.error(reason);
    throw new RequestRejectedException(reason);
  }

  private static void serverSideException(String message) {
    log.error(message);
    throw new RuntimeException(message);
  }

  private String getDestFilePath() {
    return UPG_DIR + File.separator + UPG_FNAME;
  }

  private void destFilePathChecking() {

    try {
      Path dirPath = Paths.get(UPG_DIR);
      Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-x---");
      FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
      if (!Files.isDirectory(dirPath)) {
        Files.createDirectory(dirPath, attr);
      }

      if (!Files.isWritable(dirPath)) {
        Files.setPosixFilePermissions(dirPath, perms);
      }

    } catch (IOException e) {
      log.error(e);
      serverSideException(
          String.format(
              "Directory %s is not ready and can not create it or set is writable.", UPG_DIR));
    }
  }

  private void run(String command) {
    Runnable reboot =
        () -> {
          try {
            while (!isResponseSend) {
              Thread.sleep(200);
            }
            // Wait 3 sec to let responce content is send back
            // but this depends on end-user machine performance
            //
            Thread.sleep(3000);
            forkAndRun(CommandLine.parse(command));
          } catch (Throwable e) {
            log.error(e);
          }
        };
    try {
      new Thread(reboot).start();
    } catch (Throwable e) {
      log.error(e);
    }
    // reboot.join();
  }

  private void authentication(HttpServletRequest request, String who) {
    Object obj = request.getSession(false).getAttribute(who);
    if (obj != null && !((boolean) obj)) {
      throw new AccessDeniedException("Caller should be serviceProvider");
    }
  }

  @Override
  JsonObject before(MultipartHttpServletRequest request, HttpServletResponse response) {
    //  @PreAuthorize() does not work on put method with unknown reason
    authentication(request, "serviceProvider");
    destFilePathChecking();
    return new JsonObject();
  }

  @Override
  JsonObject doTransfer(MultipartHttpServletRequest multiRequest, HttpServletResponse response)
      throws IllegalStateException, IOException, ServletException {
    log.info("Start save image");
    // only upload one image file by far
    if (multiRequest.getFileMap().size() != 1) {
      reject("The request was rejected because the loaded file is null");
    }

    MultipartFile multipartFile = multiRequest.getFile(multiRequest.getFileNames().next());
    if (multipartFile == null || multipartFile.isEmpty()) {
      reject("The request was rejected because the loaded file is null");
    }

    // rename
    String path = getDestFilePath();
    multipartFile.transferTo(new File(path));
    log.info(
        "Multipart file '"
            + multipartFile.getName()
            + "' with original filename ["
            + multipartFile.getOriginalFilename()
            + "], stored "
            + ((CommonsMultipartFile) multipartFile).getStorageDescription()
            + ": saved as["
            + path
            + "]");

    JsonObject result = new JsonObject();
    result.add("storagedPath", new JsonPrimitive(path));
    return result;
  }

  @Override
  JsonObject after(MultipartHttpServletRequest multipartRequest, HttpServletResponse response)
      throws ExecuteException, UnsupportedEncodingException, TimeoutException, IOException,
          InterruptedException {
    //  - TODO: integration checks

    Files.setPosixFilePermissions(
        Paths.get(getDestFilePath()), PosixFilePermissions.fromString("rwxrwxrwx"));

    String reset = "reset 5";
    String reboot = "reboot";
    BashResult bashRe =
        forkAndRun(CommandLine.parse(reset), acceptedStatusCodes, WAIT_TIME_IN_MILLISECONDES);
    FirmwareImageUpgradeStatus status = codeNameMap.get(bashRe.sc);
    if (!status.equals(FirmwareImageUpgradeStatus.UPG_OK)) {
      serverSideException(status.toString());
    }

    run(reboot);

    JsonObject result = new JsonObject();
    result.add(reset, new JsonPrimitive("done"));
    result.add(reboot, new JsonPrimitive("start now, all online user need re-login"));
    return result;
  }

  @PutMapping(
    value = IMAGE_PUT_MAPPING_URL,
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    produces = MediaType.APPLICATION_JSON_UTF8_VALUE
  )
  public void put(MultipartHttpServletRequest multiRequest, HttpServletResponse response)
      throws IllegalStateException, IOException, ServletException, TimeoutException,
          InterruptedException {
    if (!finished) {
      throw new IllegalStateException("Other is upgrading and not finished yet, please try later");
    }
    synchronized (lock) {
      // TODO: support uploading large file in most requests if performance is not
      // satisfied.
      finished = false;
      isResponseSend = false;
      try {
        JsonObject result = transfer(multiRequest, response);
        log.info("Request is valid. Saved file. Reset is done");

        int httpStatusAccepted = 202;
        response.setStatus(httpStatusAccepted);
        log.info("Response code is set");

        String resBody = "";
        try {
          resBody = result.toString();
        } catch (Throwable e) {
          log.error("Exception is found, ignore it and use plain content", e);
          resBody = "{file saved, reset is done, reboot in 3 second}";
        }

        try {
          response
              .getOutputStream()
              .write(resBody.getBytes(Charset.forName(response.getCharacterEncoding())));
          response.flushBuffer();
          log.info("Reponse is flushed");
        } catch (Throwable e) {
          log.error(
              "PROJ is corrupted by 'reset 5' and can not send out response content to client-side",
              e);
          // In this case:
          // - Still let reboot happen to complete the upgrading work.
          // - Client-side only get response code. no response body.
        }

        isResponseSend = true;
      } finally {
        finished = true;
      }
    }
  }
}
