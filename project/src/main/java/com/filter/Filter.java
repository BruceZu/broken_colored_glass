package com.filter;

import com.util.JsonParser;
import com.util.JsonParser.Function;
import com.util.RequestWrapper;
import com.util.ResponseWrapper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.charset.Charset;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringEscapeUtils;
 
// Use ResponseBodyAdvice after Spring 4.1
public class Filter implements Filter {
  public static final String JSONOBJECT_KEY_FOR_JQURY_DATA_TABLE = "aaData";
  public static final Function<String> ESCAPE_FUNCTION =
      new Function<String>() {
        @Override
        public String apply(String value) {
          if (value.toLowerCase().contains("<script")) {
            return StringEscapeUtils.escapeHtml(value);
          }
          return value;
        }
      };

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    ResponseWrapper responseWrapper =
        new ResponseWrapper((HttpServletResponse) response);

    chain.doFilter(new RequestWrapper((HttpServletRequest) request), responseWrapper);

    if (responseWrapper.needCheckXss()) {
      byte[] conentInBytes = responseWrapper.toByteArray();

      Charset charset = Charset.forName(response.getCharacterEncoding());
      String responseContent = new String(conentInBytes, charset);

      // proj has cases where content type is declared as application/json
      // but in fact content is not json .
      if (!responseContent.isEmpty()
          && (responseContent.startsWith("[") || responseContent.startsWith("{"))) {
        JsonElement parsed = PROJJsonParser.parse(responseContent, ESCAPE_FUNCTION);
        if (parsed.isJsonObject()
            && parsed.getAsJsonObject().keySet().contains(JSONOBJECT_KEY_FOR_JQURY_DATA_TABLE)) {
          // Only escape Json string used by front end Jquery datatable and when there is Javascript
          // is injected in some String
          // value

          String parsedStr =
              new GsonBuilder().disableHtmlEscaping().serializeNulls().create().toJson(parsed);
          if (!responseContent.equals(parsedStr)) {
            Log.warn("Escape |" + responseContent + "| to be |" + parsed + "|");
            conentInBytes = parsedStr.getBytes(charset);
            response.setContentLength(conentInBytes.length);
          }
        }
      }
      response.getOutputStream().write(conentInBytes);
    }
  }

  @Override
  public void init(FilterConfig arg0) throws ServletException {}
}

