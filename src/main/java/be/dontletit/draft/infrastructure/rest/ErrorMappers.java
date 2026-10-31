package be.dontletit.draft.infrastructure.rest;

import be.dontletit.draft.api.SeatAccessDeniedException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Map;
import java.util.NoSuchElementException;

/** Domain and upstream failures as JSON `{"error": ...}` with a fitting status. */
public class ErrorMappers {

    private static final Logger LOG = Logger.getLogger(ErrorMappers.class);

    @ServerExceptionMapper
    public Response denied(SeatAccessDeniedException e) {
        return json(Response.Status.FORBIDDEN, e.getMessage());
    }

    @ServerExceptionMapper
    public Response badInput(IllegalArgumentException e) {
        return json(Response.Status.BAD_REQUEST, e.getMessage());
    }

    @ServerExceptionMapper
    public Response wrongState(IllegalStateException e) {
        return json(Response.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    public Response missing(NoSuchElementException e) {
        return json(Response.Status.NOT_FOUND, e.getMessage());
    }

    @ServerExceptionMapper
    public Response upstreamUnreachable(ProcessingException e) {
        LOG.warnf("MTGODatabase unreachable: %s", e.getMessage());
        return json(Response.Status.BAD_GATEWAY, "Card database unreachable");
    }

    @ServerExceptionMapper
    public Response upstreamOrLocal(WebApplicationException e) {
        int status = e.getResponse() != null ? e.getResponse().getStatus() : 500;
        if (status >= 400 && status < 500 && e.getResponse().hasEntity()) return e.getResponse();
        if (status >= 400 && status < 500) return json(Response.Status.fromStatusCode(status), e.getMessage());
        LOG.warnf("MTGODatabase answered %d", status);
        return json(Response.Status.BAD_GATEWAY, "Card database failed with status " + status);
    }

    private static Response json(Response.StatusType status, String message) {
        return Response.status(status).entity(Map.of("error", message == null ? "" : message)).type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON).build();
    }
}
