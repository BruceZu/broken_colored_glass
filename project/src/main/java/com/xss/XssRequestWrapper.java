

import java.lang.reflect.Field;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.util.ReflectionUtils;

 
public class XssRequestWrapper extends HttpServletRequestWrapper {

  public XssRequestWrapper(HttpServletRequest request) {
    super(request);
  }

  @Override
  public String[] getParameterValues(String parameter) {
    String[] values = super.getParameterValues(parameter);

    if (values == null) {
      return null;
    }

    int count = values.length;
    String[] encodedValues = new String[count];
    for (int i = 0; i < count; i++) {
      encodedValues[i] = sanitizeXSS(values[i]);
    }

    return encodedValues;
  }

  @Override
  public String getParameter(String parameter) {
    String value = super.getParameter(parameter);

    return sanitizeXSS(value);
  }

  @Override
  public String getHeader(String name) {
    String value = super.getHeader(name);
    return sanitizeXSS(value);
  }

  private String sanitizeXSS(String value) {
    if (value != null) {
      value = StringEscapeUtils.escapeHtml(value);
      value = StringEscapeUtils.escapeSql(value);
      value =
          value
              .replace("SLEEP", "")
              .replace("BENCHMARK", "")
              .replace("(", "&#40;")
              .replace(")", "&#41;");
    }
    return value;
  }

  /**
   * <pre>
   * Required to get origin value before send request to other server than PROJ,e.g Radius, BPJ,
   * SAML, compAuth.
   *
   */
  public static String getOriginalParameter(HttpServletRequest request, String parameterName) {
    Field requestField;
    while (!request.getClass().isAssignableFrom(PrjXssRequestWrapper.class)) {
      requestField = ReflectionUtils.findField(request.getClass(), "request");
      ReflectionUtils.makeAccessible(requestField);
      request = (HttpServletRequest) ReflectionUtils.getField(requestField, request);
    }
    return ((PrjXssRequestWrapper) request).getOriginalParameter(parameterName);
  }

  public String getOriginalParameter(String pamareterName) {
    return super.getParameter(pamareterName);
  }
}
