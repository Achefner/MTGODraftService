package be.dontletit.draft.infrastructure.rest;

import be.dontletit.draft.api.DraftApiPort;
import be.dontletit.draft.api.DraftSourceView;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.SavedDeckView;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

@Path("/drafts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DraftController {

    @Inject
    DraftApiPort draftApi;

    @POST
    public Response start(StartDraftRequest request, UriInfo uriInfo) {
        if (request == null || request.source() == null) throw new BadRequestException("source is required: random or cube");
        DraftView draft = switch (request.source().toLowerCase()) {
            case "random" -> draftApi.startRandomDraft();
            case "cube" -> {
                if (request.collectionId() == null || request.collectionId().isBlank()) throw new BadRequestException("collectionId is required for a cube draft");
                yield draftApi.startCubeDraft(request.collectionId());
            }
            default -> throw new BadRequestException("Unknown source: " + request.source() + " (expected random or cube)");
        };
        URI location = uriInfo.getBaseUriBuilder().path("drafts").path(draft.id()).build();
        return Response.created(location).entity(draft).build();
    }

    /** Cube collections a draft can be started from. */
    @GET
    @Path("/sources")
    public List<DraftSourceView> sources() {
        return draftApi.listCubes();
    }

    @GET
    @Path("/{id}")
    public DraftView get(@PathParam("id") String id) {
        return draftApi.getDraft(id).orElseThrow(() -> notFound(id));
    }

    @POST
    @Path("/{id}/picks")
    public DraftView pick(@PathParam("id") String id, PickRequest request) {
        if (request == null) throw new BadRequestException("cardName is required");
        return draftApi.pick(id, request.cardName()).orElseThrow(() -> notFound(id));
    }

    @POST
    @Path("/{id}/deck")
    public Response saveDeck(@PathParam("id") String id, SaveDeckRequest request) {
        if (request == null) throw new BadRequestException("name is required");
        SavedDeckView saved = draftApi.saveDeck(id, request.name()).orElseThrow(() -> notFound(id));
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    private static NotFoundException notFound(String id) {
        return new NotFoundException("Draft not found: " + id);
    }
}
