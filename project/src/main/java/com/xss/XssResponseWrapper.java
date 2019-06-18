

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XssResponseWrapper extends HttpServletResponseWrapper {
  private static Logger log = LoggerFactory.getLogger(PrjXssResponseWrapper.class);

  private Boolean needCheckXss;

  private ByteArrayOutputStream standIn;

  class CopyServletOutputStream extends ServletOutputStream {
    private ServletOutputStream out;
    private OutputStream buffer;

    public CopyServletOutputStream(ServletOutputStream out, OutputStream copy) {
      this.out = out;
      this.buffer = copy;
    }

    @Override
    public void write(int buf) throws IOException {
      if (needCheckXss()) {
        buffer.write(buf);
      } else {
        out.write(buf);
      }
    }

    @Override
    public boolean isReady() {
      if (needCheckXss()) {
        return true;
      } else {
        return out.isReady();
      }
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
      // TODO Auto-generated method stub
    }
  }

  public XssResponseWrapper(HttpServletResponse response) {
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
   * Not parse response with content type as text/css;charset=UTF-8, image/x-icon;charset=UTF-8,
   * text/plain;charset=UTF-8, image/jpeg;charset=UTF-8, text/html;charset=UTF-8,
   * application/javascript;charset=UTF-8, image/png;charset=UTF-8, image/gif;charset=UTF-8
   */
  public boolean needCheckXss() {
    if (needCheckXss == null) {
      Object contentType = getResponse().getContentType();
      needCheckXss = contentType != null && ((String) contentType).contains("application/json");
    }
    return needCheckXss;
  }
}
