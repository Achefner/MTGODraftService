package be.dontletit.draft.infrastructure.data;

import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.domain.ManaValue;
import be.dontletit.draft.spi.CardPoolSpiPort;
import be.dontletit.draft.spi.CubeInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Optional;

/** CardPoolSpiPort backed by the MTGODatabase microservice. */
@ApplicationScoped
public class MtgoDatabaseCardPool implements CardPoolSpiPort {

    @Inject
    @RestClient
    MtgoDatabaseClient database;

    @Override
    public List<DraftCard> randomCards(int count) {
        return database.randomCards(count).stream().map(MtgoDatabaseCardPool::toCard).toList();
    }

    @Override
    public Optional<List<DraftCard>> collectionCards(String collectionId) {
        try {
            return Optional.of(database.collectionCards(collectionId).stream().map(MtgoDatabaseCardPool::toCard).toList());
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) return Optional.empty();
            throw e;
        }
    }

    @Override
    public List<CubeInfo> listCubes() {
        return database.collections().stream()
                .filter(c -> "CUBE".equalsIgnoreCase(c.type()))
                .map(c -> new CubeInfo(c.id(), c.name(), c.cardCount()))
                .toList();
    }

    @Override
    public String saveDeck(String name, List<String> cardNames) {
        List<CollectionEntryDto> entries = cardNames.stream().map(n -> new CollectionEntryDto(n, 1)).toList();
        return database.createCollection(new CreateCollectionDto(name, "DECK", entries)).id();
    }

    private static DraftCard toCard(CardDto dto) {
        return new DraftCard(dto.name(), dto.imgUrl(), ManaValue.of(dto.manaCost()));
    }
}
