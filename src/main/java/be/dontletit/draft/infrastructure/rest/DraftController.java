package be.dontletit.draft.infrastructure.rest;

import be.dontletit.draft.api.DraftAction;
import be.dontletit.draft.api.DraftApiPort;
import be.dontletit.draft.api.DraftFormatView;
import be.dontletit.draft.api.DraftSourceView;
import be.dontletit.draft.api.Subscription;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.OpenTableRequest;
import be.dontletit.draft.api.SavedDeckView;
import be.dontletit.draft.api.SeatTicket;
import be.dontletit.draft.api.TablePreview;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.util.List;

/** Tables and drafts. Seat calls carry the seat token in the {@value #TOKEN_HEADER} header. */
@Path("/drafts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DraftController {

    public static final String TOKEN_HEADER = "X-Seat-Token";

    @Inject
    DraftApiPort draftApi;

    @Inject
    StreamConnections streams;

    /** The draft formats: CLASSIC, ROCHESTER and WINSTON, with seats and cards needed. */
    @GET
    @Path("/formats")
    public List<DraftFormatView> formats() {
        return draftApi.listFormats();
    }

    /** Cube collections a draft can be started from. */
    @GET
    @Path("/sources")
    public List<DraftSourceView> sources() {
        return draftApi.listCubes();
    }

    /** Opens a table and seats its host; returns the seat ticket with its token. */
    @POST
    public Response open(StartDraftRequest request, UriInfo uriInfo) {
        if (request == null) throw new BadRequestException("source is required: random or cube");
        SeatTicket ticket = draftApi.openTable(new OpenTableRequest(request.source(), request.collectionId(), request.format(),
                request.playerName(), request.timed() == null || request.timed(), request.startNow() == null || request.startNow()));
        return Response.created(uriInfo.getBaseUriBuilder().path("drafts").path(ticket.draftId()).build()).entity(ticket).build();
    }

    /** Public facts about a table, for the join page. */
    @GET
    @Path("/{id}/table")
    public TablePreview table(@PathParam("id") String id) {
        return draftApi.preview(id);
    }

    @POST
    @Path("/{id}/seats")
    public Response join(@PathParam("id") String id, JoinRequest request) {
        SeatTicket ticket = draftApi.join(id, request == null ? null : request.playerName());
        return Response.status(Response.Status.CREATED).entity(ticket).build();
    }

    @POST
    @Path("/{id}/start")
    @Consumes(MediaType.WILDCARD)
    public DraftView start(@PathParam("id") String id, @HeaderParam(TOKEN_HEADER) String token) {
        return draftApi.start(id, token);
    }

    @GET
    @Path("/{id}")
    public DraftView get(@PathParam("id") String id, @HeaderParam(TOKEN_HEADER) String token) {
        return draftApi.view(id, token);
    }

    /** Picks for Classic and Rochester, taking or passing piles for Winston. */
    @POST
    @Path("/{id}/actions")
    public DraftView act(@PathParam("id") String id, @HeaderParam(TOKEN_HEADER) String token, ActionRequest request) {
        if (request == null) throw new BadRequestException("type is required: PICK, TAKE_PILE or PASS_PILE");
        return draftApi.act(id, token, new DraftAction(request.type(), request.cardName(), request.expectedMoves()));
    }

    @POST
    @Path("/{id}/deck")
    public Response saveDeck(@PathParam("id") String id, @HeaderParam(TOKEN_HEADER) String token, SaveDeckRequest request) {
        if (request == null) throw new BadRequestException("name is required");
        SavedDeckView saved = draftApi.saveDeck(id, token, request.name());
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    /**
     * The seat's live updates as server-sent events. Browsers' EventSource cannot send headers, so
     * the token comes as a query parameter; on reconnection the browser sends Last-Event-ID itself.
     */
    @GET
    @Path("/{id}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void stream(@PathParam("id") String id, @QueryParam("token") String token,
                       @HeaderParam("Last-Event-ID") String lastEventId, @Context SseEventSink sink, @Context Sse sse) {
        draftApi.view(id, token);  // a wrong token or draft is refused before the stream opens
        StreamConnections.Connection connection = streams.open(sink, sse);
        try {
            Subscription subscription = draftApi.subscribe(id, token, parseVersion(lastEventId), connection::send);
            connection.attach(subscription);
        } catch (RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    private static Long parseVersion(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) return null;
        try {
            return Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
