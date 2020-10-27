
import com.coustomer.projs.rest.controller.PrjSpaCommonRequestController;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class FilterNonUserCallInterceptor implements HandlerInterceptor {
  private static String CURRENT_USER_LAST_ACCESS_TIME =
      "_customized_last_access_time_point_second_";
  private static Set<String> NON_USER_CALL_URI =
      new HashSet<>(Arrays.asList("/proj/v1/api/heartbeat"));

  private static boolean isOfNonUserCall(HttpServletRequest request) {
    String currentUri = request.getRequestURI();
    for (String call : NON_USER_CALL_URI) {
      if (currentUri.startsWith(call)) {
        return true;
      }
    }
    return false;
  }

  private boolean isExpired(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (null != session) {
      Object lastAccess = session.getAttribute(CURRENT_USER_LAST_ACCESS_TIME);
      return null != lastAccess
          && Instant.now().getEpochSecond() - (long) lastAccess > session.getMaxInactiveInterval();
    }
    return false;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    HttpSession session = request.getSession(false);
    if (null != session && PrjSpaCommonRequestController.hasLoggedIn()) {
      if (!isOfNonUserCall(request)) {
        session.setAttribute(CURRENT_USER_LAST_ACCESS_TIME, Instant.now().getEpochSecond());
      } else if(isExpired(request)){
        session.invalidate();
        return false;
      }
    }
    return true;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView)
      throws Exception {
    // TODO Auto-generated method stub
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
      throws Exception {
    // TODO Auto-generated method stub
  }
}
