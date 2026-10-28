package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftApiPort;
import be.dontletit.draft.api.DraftCardView;
import be.dontletit.draft.api.DraftSourceView;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.SavedDeckView;
import be.dontletit.draft.domain.Draft;
import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.spi.CardPoolSpiPort;
import be.dontletit.draft.spi.DraftStoreSpiPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@ApplicationScoped
public class DraftApiAdapter implements DraftApiPort {

    private final CardPoolSpiPort cardPool;
    private final DraftStoreSpiPort store;
    private final Supplier<Random> randomSupplier;
    /** Which source each draft was started from (the domain does not care). */
    private final Map<String, String> sources = new ConcurrentHashMap<>();

    @Inject
    public DraftApiAdapter(CardPoolSpiPort cardPool, DraftStoreSpiPort store) {
        this(cardPool, store, Random::new);
    }

    DraftApiAdapter(CardPoolSpiPort cardPool, DraftStoreSpiPort store, Supplier<Random> randomSupplier) {
        this.cardPool = cardPool;
        this.store = store;
        this.randomSupplier = randomSupplier;
    }

    @Override
    public DraftView startRandomDraft() {
        return start("random", cardPool.randomCards(Draft.CARDS_NEEDED));
    }

    @Override
    public DraftView startCubeDraft(String collectionId) {
        List<DraftCard> cards = cardPool.collectionCards(collectionId)
                .orElseThrow(() -> new NoSuchElementException("Collection not found: " + collectionId));
        if (cards.size() < Draft.CARDS_NEEDED) {
            throw new IllegalArgumentException("A draft needs " + Draft.CARDS_NEEDED + " cards but the cube has " + cards.size());
        }
        return start("cube:" + collectionId, cards);
    }

    private DraftView start(String source, List<DraftCard> cards) {
        Draft draft = Draft.start(UUID.randomUUID().toString(), cards, randomSupplier.get());
        sources.put(draft.id(), source);
        store.save(draft);
        return toView(draft);
    }

    @Override
    public Optional<DraftView> getDraft(String draftId) {
        return store.find(draftId).map(this::toView);
    }

    @Override
    public Optional<DraftView> pick(String draftId, String cardName) {
        return store.find(draftId).map(draft -> {
            synchronized (draft) {
                draft.pick(cardName);
                store.save(draft);
            }
            return toView(draft);
        });
    }

    @Override
    public List<DraftSourceView> listCubes() {
        return cardPool.listCubes().stream()
                .map(cube -> new DraftSourceView(cube.id(), cube.name(), cube.cardCount()))
                .toList();
    }

    @Override
    public Optional<SavedDeckView> saveDeck(String draftId, String deckName) {
        return store.find(draftId).map(draft -> {
            if (deckName == null || deckName.isBlank()) throw new IllegalArgumentException("Deck name must not be blank");
            List<String> names = draft.picks().stream().map(DraftCard::name).toList();
            if (names.isEmpty()) throw new IllegalStateException("Nothing has been picked yet");
            String collectionId = cardPool.saveDeck(deckName.trim(), names);
            return new SavedDeckView(collectionId, deckName.trim(), names.size());
        });
    }

    private DraftView toView(Draft draft) {
        return new DraftView(
                draft.id(),
                sources.getOrDefault(draft.id(), "unknown"),
                draft.round(),
                draft.pickNumber(),
                draft.direction().name(),
                draft.finished(),
                draft.currentPack().stream().map(DraftApiAdapter::toView).toList(),
                draft.picks().stream().map(DraftApiAdapter::toView).toList()
        );
    }

    private static DraftCardView toView(DraftCard card) {
        return new DraftCardView(card.name(), card.imageUrl());
    }
}
