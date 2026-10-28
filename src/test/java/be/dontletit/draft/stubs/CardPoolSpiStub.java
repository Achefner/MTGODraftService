package be.dontletit.draft.stubs;

import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.spi.CardPoolSpiPort;
import be.dontletit.draft.spi.CubeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

public class CardPoolSpiStub implements CardPoolSpiPort {

    public static List<DraftCard> cards(String prefix, int size) {
        return IntStream.range(0, size).mapToObj(i -> new DraftCard(prefix + " " + i, "https://img/" + prefix + i + ".jpg")).toList();
    }

    public final Map<String, List<DraftCard>> collections = new HashMap<>();
    public final List<CubeInfo> cubes = new ArrayList<>();
    public final List<Integer> randomRequests = new ArrayList<>();
    public String savedDeckName;
    public List<String> savedDeckCards;

    @Override
    public List<DraftCard> randomCards(int count) {
        randomRequests.add(count);
        return cards("Random", count);
    }

    @Override
    public Optional<List<DraftCard>> collectionCards(String collectionId) {
        return Optional.ofNullable(collections.get(collectionId));
    }

    @Override
    public List<CubeInfo> listCubes() {
        return cubes;
    }

    @Override
    public String saveDeck(String name, List<String> cardNames) {
        savedDeckName = name;
        savedDeckCards = cardNames;
        return "deck-1";
    }
}
