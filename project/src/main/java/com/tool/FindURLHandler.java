package com.tool;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * E.g.
 *
 * <p>http://localhost:8080/<context>/tool/req_maps <br>
 * show all information of Request-Handler mapping.
 *
 * <p>http://localhost:8080/<context>/tool/req_maps?p=.*router.* <br>
 * will narrow down the result to be those match the pattern of ".*router.*"
 *
 * <p>http://localhost:8080/<context>/tool/req_maps/router/add <br>
 * will narrow down the result to be those containing "router/add"
 */
@RestController()
public class FindURLHandler {
  private final RequestMappingHandlerMapping handlerMapping;
  private boolean isRunningInVM;

  private List<String> handlersInfoOf(String urlCondition, BiPredicate<String, String> pre) {
    return handlerMapping
        .getHandlerMethods()
        .entrySet()
        .parallelStream()
        .flatMap(
            e -> {
              RequestMappingInfo requestInfo = e.getKey();
              Set<String> ps = requestInfo.getPatternsCondition().getPatterns();
              return ps.parallelStream()
                  .map(
                      urlPattern -> {
                        if (urlCondition == null
                            || urlCondition != null && pre.test(urlPattern, urlCondition)) {
                          return new StringBuilder()
                              .append(requestInfo.toString())
                              .append(" -> ")
                              .append(e.getValue().getShortLogMessage())
                              .append(" \n")
                              .toString();
                        }
                        return "";
                      })
                  .distinct()
                  .filter(i -> i != "");
            })
        .sorted(new AntPathMatcher().getPatternComparator(urlCondition))
        .collect(Collectors.toList());
  }

  @Autowired
  public FindURLHandler(RequestMappingHandlerMapping handlerMapping) {

    this.handlerMapping = handlerMapping;
    // This can only be used for local debug and test purpose.
    // Not available for released VM due to security concern.
    try (Stream<String> stream = Files.lines(Paths.get("/etc/prod_type.txt"))) {
      this.isRunningInVM = stream.findFirst().equals("PROJ");
    } catch (Exception e) {
      this.isRunningInVM = false;
    }
  }

  @GetMapping(value = {"/tool/req_maps/**/"})
  public List<String> getby(HttpServletRequest request) {
    if (isRunningInVM) return null;
    String keyWord =
        ((String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE))
            .substring("/tool/req_maps/".length());
    return handlersInfoOf(keyWord, (url, key) -> url.contains(key));
  }

  @GetMapping("/tool/req_maps")
  public List<String> get(@RequestParam(name = "p", required = false) String unescapedPattern) {
    if (isRunningInVM) return null;

    String pattern =
        unescapedPattern == null ? null : StringEscapeUtils.unescapeHtml(unescapedPattern);

    List<String> r = new ArrayList<>();
    if (unescapedPattern == null)
      r.add(
          "The value of parameter 'p' (pattern) is null or not recognizable. So display all information."
              + " It should be like http://localhost:8080/proj/tool/req_maps?p=.*log.*");
    r.addAll(handlersInfoOf(pattern, (url, p) -> Pattern.compile(p).matcher(url).matches()));
    return r;
  }
}
