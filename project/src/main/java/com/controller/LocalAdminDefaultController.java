
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.NoHandlerFoundException;

@Controller
public class LocalAdminDefaultController {
  private static final String DEFAULT_AUTHEN_URL = "/adminuser/local/default";

  private class SpuserHttpServletRequest extends HttpServletRequestWrapper {
    public SpuserHttpServletRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getParameter(String name) {
      if (name.equalsIgnoreCase(
          UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY)) {
        return "spuser";
      }
      if (name.equalsIgnoreCase(
          UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY)) {
        return "test123";
      }
      return this.getRequest().getParameter(name);
    }
  }

  private UsernamePasswordAuthenticationFilter service;

  @Autowired
  @Qualifier(
    value = "org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter#1"
  )
  private void setService(UsernamePasswordAuthenticationFilter service) {
    this.service = service;
  }

  /**
   * <pre>
   * Only in BPJ VM, not in
   * - other VM,
   * - Docker
   * - non-VM
   * - common non-Docker/VM
   */
  private boolean inBackProjectVm() {
    // decide it later
    return true;
  }

  @RequestMapping(
    value = DEFAULT_AUTHEN_URL,
    method = {RequestMethod.GET}
  )
  void authenticateDefaultLocalAdmin(ModelMap map, HttpServletRequest req, HttpServletResponse res)
      throws Exception {
    if (!inBackProjectVm()) {
      throw new NoHandlerFoundException(
          req.getMethod(), req.getRequestURI(), new ServletServerHttpRequest(req).getHeaders());
    }
    try {
      service.setRequiresAuthenticationRequestMatcher(
          new AntPathRequestMatcher(DEFAULT_AUTHEN_URL, "GET"));
      service.setPostOnly(false);
      service.doFilter(new SpuserHttpServletRequest(req), res, null);
    } finally {
      service.setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher("/login", "POST"));
      service.setPostOnly(true);
    }
  }
}
