package ch.khinkali.tracing.boundary;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static ch.khinkali.tracing.boundary.Tracing.TRACEE_HEADER;

@Provider
public class TracingContainerRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private final static ConcurrentHashMap<String, Long> concurrentRequests = new ConcurrentHashMap<>();
    public static final String TRACING_ID = "TRACING_ID";

    private Tracing tracee;

    public TracingContainerRequestFilter() {
        this.tracee = new Tracing(Networking::configureBaseURI, true);
    }


    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        System.out.println("Request: " + requestContext);
        String id = UUID.randomUUID().toString();
        requestContext.setProperty(TRACING_ID, id);
        concurrentRequests.put(id, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        System.out.println("Response = " + requestContext + " " + responseContext);
        Object tracingId = requestContext.getProperty(TRACING_ID);
        Long start = concurrentRequests.get(tracingId);
        concurrentRequests.remove(requestContext);
        System.out.println("requestContext = " + requestContext.getUriInfo());
        long duration = (System.nanoTime() - start);
        System.out.println("Duration: " + duration);
        UriInfo uriInfo = requestContext.getUriInfo();

        String ipv4 = extractIpAddress(uriInfo);
        System.out.println("ipv4 = " + ipv4);
        String serviceName = extractServiceName(uriInfo);
        System.out.println("serviceName = " + serviceName);
        String spanName = extractSpanName(uriInfo);
        System.out.println("spanName = " + spanName);
        Optional<String> traceId = extractTraceId(requestContext);
        String spanId = traceId.map(id -> this.tracee.saveChildSpan(id, spanName, serviceName, ipv4, 0)).
                orElseGet(() -> this.tracee.saveParentSpan(spanName, serviceName, ipv4, duration));
        System.out.println("Storing span id: " + spanId);
        storeSpandId(responseContext, spanId);
    }

    String extractSpanName(UriInfo info) {
        return info.getPath();
    }

    String extractIpAddress(UriInfo uriInfo) {
        return Networking.extractIpAddress(uriInfo.getBaseUri());
    }

    String extractServiceName(UriInfo uriInfo) {
        URI absolutePath = uriInfo.getBaseUri();
        return absolutePath.getPath();
    }

    Optional<String> extractTraceId(ContainerRequestContext requestContext) {
        final String correlationId = requestContext.getHeaderString(TRACEE_HEADER);
        System.out.println("correlationId = " + correlationId);
        return Optional.ofNullable(correlationId);
    }

    void storeSpandId(ContainerResponseContext responseContext, String id) {
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        headers.putSingle(TRACEE_HEADER, id);
    }

}
