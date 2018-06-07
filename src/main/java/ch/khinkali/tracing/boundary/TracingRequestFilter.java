package ch.khinkali.tracing.boundary;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static ch.khinkali.tracing.boundary.Networking.extractIpAddress;
import static ch.khinkali.tracing.boundary.Networking.extractServiceName;

@Provider
public class TracingRequestFilter implements ClientRequestFilter, ClientResponseFilter {
    public static final String TRACING_ID = "TRACING_ID";

    private Tracing tracEE;
    static final String SPAN_EXISTED = TracingRequestFilter.class.getName() + ".spanexisted";
    private final static ConcurrentHashMap<String, Long> concurrentRequests = new ConcurrentHashMap<>();


    public TracingRequestFilter(String host) {
        this.tracEE = new Tracing(() -> host, true);
    }


    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        System.out.println("Request: " + requestContext);
        String id = UUID.randomUUID().toString();
        requestContext.setProperty(TRACING_ID, id);
        concurrentRequests.put(id, System.nanoTime());
        Optional<String> existingSpanId = extractTraceId(requestContext);
        requestContext.setProperty(SPAN_EXISTED, existingSpanId.isPresent());
        storeSpandId(requestContext, existingSpanId.orElseGet(this.tracEE::createId));

    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        System.out.println("Response = " + requestContext + " " + responseContext);
        Long start = concurrentRequests.get(requestContext);
        Object tracingId = requestContext.getProperty(TRACING_ID);
        concurrentRequests.remove(tracingId);
        long duration = (System.nanoTime() - start);
        System.out.println("Duration: " + duration);
        URI uri = requestContext.getUri();

        String ipv4 = extractIpAddress(uri);
        System.out.println("ipv4 = " + ipv4);
        String serviceName = extractServiceName(uri);
        System.out.println("serviceName = " + serviceName);
        String spanName = uri.getPath();
        System.out.println("spanName = " + spanName);
        String traceId = extractTraceId(requestContext).orElse("--no spanid--");
        Boolean spanExists = Optional.ofNullable(requestContext.getProperty(SPAN_EXISTED)).
                map((o) -> (boolean) o).
                orElse(false);
        if (spanExists) {
            this.tracEE.saveChildSpan(traceId, spanName, serviceName, ipv4, duration);
        } else {
            this.tracEE.saveParentSpan(spanName, serviceName, ipv4, duration);
        }
    }

    Optional<String> extractTraceId(ClientRequestContext requestContext) {
        return Optional.ofNullable(requestContext.getHeaderString(Tracing.TRACEE_HEADER));
    }

    void storeSpandId(ClientRequestContext responseContext, String id) {
        System.out.println("Storing id: " + id);
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        headers.putSingle(Tracing.TRACEE_HEADER, id);
    }

}
