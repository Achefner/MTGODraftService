package be.dontletit.draft.spi;

import be.dontletit.draft.domain.DraftCard;

import java.util.List;
import java.util.Optional;

/** Outbound port to the card database: where packs come from and where decks go. */
public interface CardPoolSpiPort {

    List<DraftCard> randomCards(int count);

    /** The cards of a collection, or empty when it does not exist. */
    Optional<List<DraftCard>> collectionCards(String collectionId);

    List<CubeInfo> listCubes();

    /** Stores a deck collection and returns its id. */
    String saveDeck(String name, List<String> cardNames);
}
