package download;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/sec/serviceProvider/logviewer/log")
@PreAuthorize("hasPermission('LogViewer', 2) or hasPermission('LogViewer', 3)")
public class PrjsLogViewerController {
  private static Logger log = LogManager.getLogger(PrjsLogViewerController.class.getName());
  @Autowired ServletContext servletContext;
  @Autowired private ApplicationContext applicationContext;
  public static final String LOG_EXTENSION = ".log";
  private static final short DFLT_LOGVIEWER_TRAVERSAL_ROW_COUNT = 200;
  private static final String PROJ_LOG_FILE_NAME = "coustomer_proj" + LOG_EXTENSION;
  public static String PROJ_LOG_FILE_PATH = null;
  private static String PROJ_LOG_FILE_CURRENT = null;

  @PostConstruct
  private void initPrjLog() {
    if (PROJ_LOG_FILE_CURRENT == null) {
      PROJ_LOG_FILE_PATH =
          applicationContext.getEnvironment().getProperty("catalina.base")
              + File.separator
              + "util";
      PROJ_LOG_FILE_CURRENT = getFileName(PROJ_LOG_FILE_NAME);
    }
  }

  private static String getFileName(String fileName) {
    return PROJ_LOG_FILE_PATH + File.separator + fileName;
  }

  @RequestMapping(method = RequestMethod.GET)
  @PreAuthorize("hasPermission('LogViewer', 2) or hasPermission('LogViewer', 3)")
  @ResponseBody
  public String downloadLog(
      @RequestParam(value = "action") String action,
      @RequestParam(value = "filename") String filename,
      HttpServletRequest request,
      HttpServletResponse response) {
    JSONObject status = new JSONObject();
    if (action != null && action.equalsIgnoreCase("download")) {
      if (filename.contains(File.separator) || !filename.endsWith(LOG_EXTENSION)) {
        status.put("status", "failed with an invalid filename");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return status.toString();
      }
      response.setContentType("application/octet-stream");
      String downloadFileName =
          filename.equalsIgnoreCase(PROJ_LOG_FILE_NAME)
              ? "coustomer_proj-" + Long.toString(System.currentTimeMillis()) + LOG_EXTENSION
              : filename;
      response.setHeader(
          "Content-Disposition", String.format("attachment; filename=\"%s\"", downloadFileName));

      FileSystemResource resource = new FileSystemResource(getFileName(filename));
      // servletContext.getResourceAsStream(LOG_PATH);
      try (InputStream inStream = resource.getInputStream();
          OutputStream outStream = response.getOutputStream()) {
        Files.copy(Paths.get(getFileName(filename)).toFile(), outStream);
      } catch (IOException error) {
        log.error(error);
      }
    }
    status.put("status", "success");
    return status.toString();
  }
}
