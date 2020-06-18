
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;

import com.google.common.base.Charsets;

/**
 * For security concern this Filter need be called before other Filters
 *
 * A case is to validate the post size where for calculating the size sometimes code needs to
 * read the inputStream which only allow read once. thus need multi-time readable inputStream.
 */
public class MultiReadableRequestFilter implements Filter {
	@Override
	public void destroy() {}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		RequestWrapper responseWrapper = new RequestWrapper((HttpServletRequest) request);
		if (!responseWrapper.isPostSizeAcceptable()) {
			((HttpServletResponse) response).sendError(HttpStatus.BAD_REQUEST.value(), "Post too large");
		}
		chain.doFilter(responseWrapper, response);
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {}
}

class RequestWrapper extends HttpServletRequestWrapper {
	private static Logger log = LogManager.getLogger(MultiReadableRequestFilter.class.getName());
	public static final int TOMCAT_DEFAULT_MAX_POST_SIZE = 2097152;

	private ServletInputStream wrappedInputSteam;
	private BufferedReader wrappedReader;
	private boolean postSizeAcceptable = false;
	private boolean inPutStreamIscached = false;

	private boolean validatePostSize(HttpServletRequest request, ByteArrayOutputStream buff)
			throws IOException {
		// TODO: logic
		/** Tomcat maxPoseSize is set -1. That means 2G, its default value 2097152 (2 megabytes) */
		return true;
	}

	private void wrap(ByteArrayInputStream cached) {
		this.wrappedInputSteam =
				new ServletInputStream() {
			@Override
			public boolean isFinished() {
				return cached.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener readListener) {
				// TODO Auto-generated method stub
			}

			@Override
			public int read() throws IOException {
				return cached.read();
			}
		};
		this.wrappedReader = new BufferedReader(new InputStreamReader(cached, Charsets.UTF_8));
	}

	public RequestWrapper(HttpServletRequest request) throws IOException {
		super(request);
		ByteArrayOutputStream cached = new ByteArrayOutputStream();
		this.postSizeAcceptable = validatePostSize(request, cached);
		if (cached.size() > 0) {
			this.inPutStreamIscached = true;
			// The data from this InputStream can be read only once, so need cached it for later call
			wrap(new ByteArrayInputStream(cached.toByteArray()));
		}
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		return isPostSizeAcceptableAndCached() ? wrappedInputSteam : getRequest().getInputStream();
	}

	@Override
	public BufferedReader getReader() throws IOException {
		return isPostSizeAcceptableAndCached() ? wrappedReader : getRequest().getReader();
	}

	public boolean isPostSizeAcceptable() {
		return postSizeAcceptable;
	}

	public boolean isPostSizeAcceptableAndCached() {
		return postSizeAcceptable && inPutStreamIscached;
	}
}
