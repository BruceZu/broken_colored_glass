package com.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.apache.log4j.Logger;

public class ResponseWrapper extends HttpServletResponseWrapper {
  private static Logger log = Logger.getLogger(ResponseWrapper.class.getName());

  private Boolean needCheckXss;

  private ByteArrayOutputStream standIn;

  class CopyServletOutputStream extends ServletOutputStream {
    private OutputStream out;
    private OutputStream buffer;

    public CopyServletOutputStream(ServletOutputStream out, OutputStream copy) {
      this.out = out;
      this.buffer = copy;
    }

    @Override
    public void write(int b) throws IOException {
      if (needCheckXss()) {
        buffer.write(b);
      } else {
        out.write(b);
      }
    }
  }

  public ResponseWrapper(HttpServletResponse response) {
    super(response);
    standIn = new ByteArrayOutputStream();
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    return new CopyServletOutputStream(getResponse().getOutputStream(), standIn);
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    return getResponse().getWriter();
  }

  public byte[] toByteArray() {
    return standIn.toByteArray();
  }
  /**
   * <pre>
   * Not parse response with content type as
   * text/css;charset=UTF-8,
   * image/x-icon;charset=UTF-8,
   * text/plain;charset=UTF-8,
   * image/jpeg;charset=UTF-8,
   * text/html;charset=UTF-8,
   * application/javascript;charset=UTF-8,
   * image/png;charset=UTF-8,
   * image/gif;charset=UTF-8
   */
  public boolean needCheckXss() {
    if (needCheckXss == null) {
      Object contentType = getResponse().getContentType();
      needCheckXss = contentType != null && ((String) contentType).contains("application/json");
    }
    return needCheckXss;
  }
}


