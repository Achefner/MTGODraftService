package be.dontletit.draft.infrastructure.data;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/** REST client for MTGODatabase (URL: quarkus.rest-client.mtgo-database.url). */
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "mtgo-database")
public interface MtgoDatabaseClient {

    @GET
    @Path("/cards/random/{count}")
    List<CardDto> randomCards(@PathParam("count") int count);

    @GET
    @Path("/collections")
    List<CollectionSummaryDto> collections();

    @GET
    @Path("/collections/{id}/cards")
    List<CardDto> collectionCards(@PathParam("id") String id);

    @POST
    @Path("/collections")
    @Consumes(MediaType.APPLICATION_JSON)
    CollectionCreatedDto createCollection(CreateCollectionDto collection);
}
