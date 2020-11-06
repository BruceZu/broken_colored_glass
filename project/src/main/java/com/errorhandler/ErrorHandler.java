
import java.io.IOException;
import java.nio.charset.Charset;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.multiaction.NoSuchRequestHandlingMethodException;

/**
 *
 * Handle uncaught exception/error during the process of invoking the controller.
 *         <pre/>
 *         Once all JSP pages are replaced we will use a subclass of ResponseEntityExceptionHandler
 *         to handle Error/Exceptions to make things easy.
 */
@ControllerAdvice
public class ErrorHandler {
  private static Logger log = LogManager.getLogger(ErrorHandler.class.getName());
  public static final String ERROR_EXCEPT_KEY = "errorMessage";

  /** 400 (Bad Request). */
  @ExceptionHandler(
    value = {BindException.class, TypeMismatchException.class}
  )
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ModelAndView handleError400(
      HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
    return handleErrors(request, response, ex, HttpServletResponse.SC_BAD_REQUEST);
  }

  /** 403 and 401 */
  @ExceptionHandler(value = {AccessDeniedException.class})
  public ModelAndView handle403and401(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    // When user's session expires or user not login, Spring will take user as
    // anonymousUser( details like
    // Principal: anonymousUser; Credentials: [PROTECTED]; Authenticated: true; Granted Authorities:
    // ROLE_ANONYMOUS)
    // Spring will throw AccessDeniedException in AffirmativeBased.decide() when anonymousUser
    // try to call any API, except 2 cases:
    //  - 'POST /proj/login' for local authentication
    //  - 'POST /proj/login/ssologin' for remote authentication ,
    // and for the API the back end controller or its method require
    // @PreAuthorize("isAuthenticated()")  or @PreAuthorize( "hasPermission('xxxx', yyy),... ）
    //
    // While PROJ need to know the AccessDeniedException is caused by unauthorized 401 or forbidden
    // 403. This is decided by user has logged in or not:
    int responseStatusCode =
        CommonRequestController.hasLoggedIn()
            ? HttpServletResponse.SC_FORBIDDEN
            : HttpServletResponse.SC_UNAUTHORIZED;
    response.setStatus(responseStatusCode);
    return handleErrors(request, response, ex, responseStatusCode);
  }

  /** 404 (Not Found). */
  @ExceptionHandler(
    value = {
      NoSuchRequestHandlingMethodException.class,
      NoHandlerFoundException.class
    }
  )
  @ResponseStatus(value = HttpStatus.NOT_FOUND)
  public ModelAndView handleError404(
      HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
    return handleErrors(request, response, ex, HttpServletResponse.SC_NOT_FOUND);
  }

  /** 405 (Method Not Allowed). */
  @ExceptionHandler(
    value = {UnsupportedOperationException.class, HttpRequestMethodNotSupportedException.class}
  )
  @ResponseStatus(value = HttpStatus.METHOD_NOT_ALLOWED)
  public ModelAndView handleError405(
      HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
    return handleErrors(request, response, ex, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
  }

  /**
   * 500 (Internal Server Error).
   *
   * <p>Catch all Runtime Exceptions by default. if some of them does not belong to
   * INTERNAL_SERVER_ERROR category then process it explicitly in another or new handler.
   */
  @ExceptionHandler(
    value = {
      NullPointerException.class,
      ConversionNotSupportedException.class,
      HttpMessageNotWritableException.class,
      IOException.class,
      Throwable.class
    }
  )
  public ModelAndView handleError500(
      HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
    if (response.getStatus() < HttpServletResponse.SC_BAD_REQUEST) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    return handleErrors(request, response, ex, response.getStatus());
  }

  private String getErrorMessage(Throwable exception, HttpServletRequest req) {
    if (exception == null) {
      return (String) req.getAttribute(RequestDispatcher.ERROR_MESSAGE);
    }
    return exception.getMessage() == null ? "" : exception.getMessage();
  }

  private void handleRESTErrors(HttpServletRequest req, HttpServletResponse response, Throwable ex)
      throws IOException {
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    try {
      String errorMessage =
          new JSONObject().append(ERROR_EXCEPT_KEY, getErrorMessage(ex, req)).toString();
      response
          .getOutputStream()
          .write(errorMessage.getBytes(Charset.forName(response.getCharacterEncoding())));
    } catch (IOException ioe) {
      log.error(ioe.getMessage(), ioe);
      throw ioe;
    }
  }

  /** Create Error page future work so specific error reason in UI. */
  private ModelAndView handleJSPErrors(
      HttpServletRequest req, HttpServletResponse response, Throwable ex, int satus) {
    ModelAndView newView = new ModelAndView("error");
    newView.addObject(ERROR_EXCEPT_KEY, getErrorMessage(ex, req));
    newView.addObject("errorCode", satus);
    return newView;
  }

  public ModelAndView handleErrors(
      HttpServletRequest req, HttpServletResponse response, Throwable ex, int satus)
      throws IOException {
    if (ex == null) {
      log.error(((String) req.getAttribute(RequestDispatcher.ERROR_MESSAGE)));
    } else {
      log.error(ex.getMessage(), ex);
    }
    log.error("Handle error happened when {} {}", req.getMethod(), req.getRequestURI());
    String reqAccept = req.getHeader(HttpHeaders.ACCEPT);
    if (reqAccept != null && reqAccept.contains(MediaType.APPLICATION_JSON_VALUE)) {
      handleRESTErrors(req, response, ex);
      return null;
    }
    return handleJSPErrors(req, response, ex, satus);
  }
}

