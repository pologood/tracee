package io.tracee.servlet;

import io.tracee.Tracee;
import io.tracee.TraceeBackend;
import io.tracee.TraceeConstants;
import io.tracee.Utilities;
import io.tracee.configuration.TraceeFilterConfiguration;
import io.tracee.transport.HttpJsonHeaderTransport;
import io.tracee.transport.TransportSerialization;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static io.tracee.configuration.TraceeFilterConfiguration.Channel.IncomingRequest;

/**
 * Manages the TracEE lifecycle.
 */
public final class TraceeServletRequestListener implements ServletRequestListener {

	private static final String HTTP_HEADER_NAME = TraceeConstants.HTTP_HEADER_NAME;

	private final TraceeBackend backend;

	private final TransportSerialization<String> transportSerialization;

	protected TraceeServletRequestListener(TraceeBackend backend, TransportSerialization<String> transportSerialization) {
		this.backend = backend;
		this.transportSerialization = transportSerialization;
	}

	public TraceeServletRequestListener() {
		this(Tracee.getBackend(), new HttpJsonHeaderTransport(Tracee.getBackend().getLoggerFactory()));
	}

	@Override
	public void requestDestroyed(ServletRequestEvent sre) {
		backend.clear();
	}

	@Override
	public void requestInitialized(ServletRequestEvent sre) {
		final ServletRequest servletRequest = sre.getServletRequest();
		if (servletRequest instanceof HttpServletRequest) {
			httpRequestInitialized((HttpServletRequest) servletRequest);
		}
	}

	private void httpRequestInitialized(HttpServletRequest request) {
		final TraceeFilterConfiguration configuration = backend.getConfiguration();

		if (configuration.shouldProcessContext(IncomingRequest)) {
			mergeIncomingContextToBackend(request);
		}

		Utilities.generateRequestIdIfNecessary(backend);

		final HttpSession session = request.getSession(false);
		if (session != null) {
			Utilities.generateSessionIdIfNecessary(backend, session.getId());
		}
	}

	private void mergeIncomingContextToBackend(HttpServletRequest request) {
		final Enumeration headers = request.getHeaders(HTTP_HEADER_NAME);
		if (headers == null) {
			throw new IllegalStateException("Could not read headers with name '"
					+ HTTP_HEADER_NAME + "'. The access seem to be forbidden by the container.");
		}

		final Map<String, String> parsed = new HashMap<String, String>();
		while (headers.hasMoreElements()) {
			parsed.putAll(transportSerialization.parse((String) headers.nextElement()));
		}

		final Map<String, String> filtered = backend.getConfiguration().filterDeniedParams(parsed, IncomingRequest);
		backend.putAll(filtered);
	}
}
