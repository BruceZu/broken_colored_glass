package tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

public class PrjRuningEnv {
  private static final String PROD_KEY = "PROJ";
  private static final String PATH_PROD_TYPE = "/etc/prod_type.txt";
  private static final String BPJ_VM_DOCKER_ENV_KEY = "IS_IN_BPJ_VM";
  private static boolean isRunningInPrjVm;

  static {
    try (Stream<String> stream = Files.lines(Paths.get(PATH_PROD_TYPE))) {
      isRunningInPrjVm = PROD_KEY.equals(stream.findFirst().get().trim());
    } catch (Exception e) {
      isRunningInPrjVm = false;
    }
  }

  public static boolean isRunningInPrjVm() {
    return isRunningInPrjVm;
  }

  public static Boolean isRunningInsideDocker() {
    try (Stream<String> stream = Files.lines(Paths.get("/proc/self/cgroup"))) {
      return stream.anyMatch(line -> line.contains("/docker"));
    } catch (IOException e) {
      return false;
    }
  }

  public static boolean isDockerRuningInBackProjectVm() {
    Map<String, String> sysEnv = System.getenv();
    if (sysEnv.containsKey(BPJ_VM_DOCKER_ENV_KEY)) {
      return sysEnv.get(BPJ_VM_DOCKER_ENV_KEY).equalsIgnoreCase("true");
    }
    return false;
  }
}
