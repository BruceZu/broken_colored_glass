package com.coustomer.projs.web.rest.controller.serviceProvider.transfer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.multipart.MultipartHttpServletRequest;

public abstract class AbstrctFileTransfer implements FileTransfer {
  private static Logger log = LogManager.getLogger(AbstrctFileTransfer.class.getName());
  private static final int TIME_OUT_BY_LINUX_SIGTERM = 143;
  private static final int[] DEFAULT_ACCEPT_SC = new int[] {0};

  static class BashResult {
    public final int sc;
    public final String stdoutMessage;
    public final String stderrMessage;

    public BashResult(int sc, @Nullable String stdoutMessage, @Nullable String stderrMessage) {
      this.sc = sc;
      this.stdoutMessage = stdoutMessage;
      this.stderrMessage = stderrMessage;
    }

    @Override
    public String toString() {
      JsonObject result = new JsonObject();
      result.add("status code", new JsonPrimitive(sc));
      if (!Strings.isNullOrEmpty(stdoutMessage)) {
        result.add("message", new JsonPrimitive(stdoutMessage));
      }
      if (!Strings.isNullOrEmpty(stderrMessage)) {
        result.add("error", new JsonPrimitive(stderrMessage));
      }
      return result.toString();
    }
  }

  public static BashResult forkAndRun(
      CommandLine commandline, @Nullable int[] acceptedExistCodes, @Nullable Long timeout)
      throws TimeoutException, ExecuteException, UnsupportedEncodingException, IOException,
          InterruptedException {
    return forkAndRun(commandline, null, null, acceptedExistCodes, timeout);
  }

  public static BashResult forkAndRun(CommandLine commandline, @Nullable Long timeout)
      throws TimeoutException, ExecuteException, UnsupportedEncodingException, IOException,
          InterruptedException {
    return forkAndRun(commandline, null, null, null, timeout);
  }

  public static BashResult forkAndRun(CommandLine commandline)
      throws TimeoutException, ExecuteException, UnsupportedEncodingException, IOException,
          InterruptedException {
    return forkAndRun(commandline, null, null, null, null);
  }
  /**
   * Synchronous execute one bash command and get the status code and normal/error message. If need
   * run more than one command, organize them in a executable script and call them
   *
   * <p>Based on org.apache.commons_commons-exec_1.3
   */
  public static BashResult forkAndRun(
      CommandLine commandline,
      @Nullable Map<String, String> environment,
      @Nullable String workDir,
      @Nullable int[] acceptedExistCodes,
      @Nullable Long timeout)
      throws TimeoutException, ExecuteException, UnsupportedEncodingException, IOException,
          InterruptedException {
    if (commandline == null) {
      return null;
    }

    if (acceptedExistCodes == null || acceptedExistCodes.length == 0) {
      acceptedExistCodes = DEFAULT_ACCEPT_SC;
    }

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

    Executor exe = new DefaultExecutor();
    exe.setStreamHandler(new PumpStreamHandler(stdout, stderr, null));
    exe.setExitValues(acceptedExistCodes);
    if (!Strings.isNullOrEmpty(workDir)) {

      File wd = new File(workDir);
      if (wd.isDirectory()) {
        exe.setWorkingDirectory(wd);
      } else {
        throw new IOException(
            "provided work directory does not exists or is not a directory:" + workDir);
      }
    }
    ExecuteWatchdog watchdog = null;
    if (timeout != null) {
      watchdog = new ExecuteWatchdog(timeout);
      exe.setWatchdog(watchdog);
    }
    TimeoutException te =
        new TimeoutException(
            String.format("Time out: process run over %d in ms, and is killed", timeout));
    try {
      exe.execute(commandline, environment, resultHandler);
      resultHandler.waitFor();
    } catch (ExecuteException e) {
      if (e.getExitValue() == TIME_OUT_BY_LINUX_SIGTERM) {
        log.info(
            "Process terminated without exception, caused by over time and is killed in Linux OS",
            e);
        throw te;
      }
      throw e;
    }
    // Over time and the exit code is user recognized
    if (timeout != null && watchdog.killedProcess()) {
      log.info("Process was killed on purpose by the watch dog");
      throw te;
    }

    ExecuteException ee = resultHandler.getException();
    if (ee != null) {
      log.error("The process exited with exception during process call", ee);
      throw ee;
    }
    BashResult result =
        new BashResult(
            resultHandler.getExitValue(),
            stdout.toString(UTF_8.name()),
            stderr.toString(UTF_8.name()));
    log.info("The process exited with user known exit status", result);
    return result;
  }

  abstract JsonObject before(MultipartHttpServletRequest request, HttpServletResponse response);

  abstract JsonObject doTransfer(MultipartHttpServletRequest request, HttpServletResponse response)
      throws IllegalStateException, IOException, ServletException;

  abstract JsonObject after(MultipartHttpServletRequest request, HttpServletResponse response)
      throws ExecuteException, UnsupportedEncodingException, TimeoutException, IOException,
          InterruptedException;

  @Override
  public JsonObject transfer(MultipartHttpServletRequest request, HttpServletResponse response)
      throws IllegalStateException, IOException, ServletException, TimeoutException,
          InterruptedException {
    before(request, response);
    JsonObject result = new JsonObject();
    result.add("upload:", doTransfer(request, response));
    result.add("upgrade:", after(request, response));
    return result;
  }
}
