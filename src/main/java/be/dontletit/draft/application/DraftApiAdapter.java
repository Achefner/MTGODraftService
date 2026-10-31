package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftAction;
import be.dontletit.draft.api.DraftApiPort;
import be.dontletit.draft.api.DraftCardView;
import be.dontletit.draft.api.DraftEventView;
import be.dontletit.draft.api.DraftFormatView;
import be.dontletit.draft.api.DraftSourceView;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.SavedDeckView;
import be.dontletit.draft.api.SeatView;
import be.dontletit.draft.api.WinstonView;
import be.dontletit.draft.domain.ClassicDraft;
import be.dontletit.draft.domain.Draft;
import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.domain.DraftFormat;
import be.dontletit.draft.domain.RandomBot;
import be.dontletit.draft.domain.RochesterDraft;
import be.dontletit.draft.domain.WinstonDraft;
import be.dontletit.draft.domain.command.DraftCommand;
import be.dontletit.draft.domain.command.PassPile;
import be.dontletit.draft.domain.command.PickCard;
import be.dontletit.draft.domain.command.TakePile;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.spi.CardPoolSpiPort;
import be.dontletit.draft.spi.DraftStoreSpiPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * One person drafts at seat {@value #HUMAN_SEAT}; every other seat is a {@link RandomBot}.
 * After each of the person's actions the bots play until it is the person's turn again.
 */
@ApplicationScoped
public class DraftApiAdapter implements DraftApiPort {

    static final int HUMAN_SEAT = 0;
    /** How many of the latest visible events a view carries. */
    static final int LOG_SIZE = 30;

    private final CardPoolSpiPort cardPool;
    private final DraftStoreSpiPort store;
    private final Supplier<Random> randomSupplier;
    /** Which source each draft was started from, and its bots (the domain cares about neither). */
    private final Map<String, String> sources = new ConcurrentHashMap<>();
    private final Map<String, RandomBot> bots = new ConcurrentHashMap<>();

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
    public List<DraftFormatView> listFormats() {
        return Arrays.stream(DraftFormat.values())
                .map(f -> new DraftFormatView(f.name(), f.displayName(), f.seats(), f.cardsNeeded(), f.description()))
                .toList();
    }

    @Override
    public DraftView startRandomDraft(String format) {
        DraftFormat draftFormat = parseFormat(format);
        return start(draftFormat, "random", cardPool.randomCards(draftFormat.cardsNeeded()));
    }

    @Override
    public DraftView startCubeDraft(String format, String collectionId) {
        DraftFormat draftFormat = parseFormat(format);
        List<DraftCard> cards = cardPool.collectionCards(collectionId)
                .orElseThrow(() -> new NoSuchElementException("Collection not found: " + collectionId));
        if (cards.size() < draftFormat.cardsNeeded()) {
            throw new IllegalArgumentException("A " + draftFormat.displayName() + " draft needs " + draftFormat.cardsNeeded()
                    + " cards but the cube has " + cards.size());
        }
        return start(draftFormat, "cube:" + collectionId, cards);
    }

    private static DraftFormat parseFormat(String format) {
        if (format == null || format.isBlank()) return DraftFormat.CLASSIC;
        try {
            return DraftFormat.valueOf(format.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown format: " + format + " (expected one of " + Arrays.toString(DraftFormat.values()) + ")");
        }
    }

    private DraftView start(DraftFormat format, String source, List<DraftCard> cards) {
        Random random = randomSupplier.get();
        Draft draft = Draft.start(format, UUID.randomUUID().toString(), cards, random);
        RandomBot bot = new RandomBot(random);
        bot.playUntilBlocked(draft, HUMAN_SEAT);
        sources.put(draft.id(), source);
        bots.put(draft.id(), bot);
        store.save(draft);
        return toView(draft);
    }

    @Override
    public Optional<DraftView> getDraft(String draftId) {
        return store.find(draftId).map(this::toView);
    }

    @Override
    public Optional<DraftView> act(String draftId, DraftAction action) {
        return store.find(draftId).map(draft -> {
            DraftCommand command = toCommand(action);
            synchronized (draft) {
                draft.handle(command);
                bots.computeIfAbsent(draft.id(), id -> new RandomBot(randomSupplier.get())).playUntilBlocked(draft, HUMAN_SEAT);
                store.save(draft);
            }
            return toView(draft);
        });
    }

    private static DraftCommand toCommand(DraftAction action) {
        if (action == null || action.type() == null) throw new IllegalArgumentException("An action type is required: PICK, TAKE_PILE or PASS_PILE");
        return switch (action.type().trim().toUpperCase(Locale.ROOT)) {
            case "PICK" -> new PickCard(HUMAN_SEAT, action.cardName());
            case "TAKE_PILE" -> new TakePile(HUMAN_SEAT);
            case "PASS_PILE" -> new PassPile(HUMAN_SEAT);
            default -> throw new IllegalArgumentException("Unknown action: " + action.type() + " (expected PICK, TAKE_PILE or PASS_PILE)");
        };
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
            List<String> names = draft.picksOf(HUMAN_SEAT).stream().map(DraftCard::name).toList();
            if (names.isEmpty()) throw new IllegalStateException("Nothing has been picked yet");
            String collectionId = cardPool.saveDeck(deckName.trim(), names);
            return new SavedDeckView(collectionId, deckName.trim(), names.size());
        });
    }

    private DraftView toView(Draft draft) {
        String id = draft.id();
        String source = sources.getOrDefault(id, "unknown");
        String format = draft.format().name();
        boolean yourTurn = draft.canAct(HUMAN_SEAT);
        List<DraftCardView> picks = EventProjection.cards(draft.picksOf(HUMAN_SEAT));
        List<SeatView> seats = IntStream.range(0, draft.format().seats())
                .mapToObj(seat -> new SeatView(seat, seat == HUMAN_SEAT, draft.picksOf(seat).size()))
                .toList();
        List<DraftEventView> log = log(draft.history());
        return switch (draft) {
            case ClassicDraft classic -> new DraftView(id, source, format, HUMAN_SEAT, yourTurn, classic.finished(),
                    classic.round(), classic.pickNumber(HUMAN_SEAT), classic.direction().name(), null, null, null,
                    EventProjection.cards(classic.packFor(HUMAN_SEAT)), picks, null, seats, log);
            case RochesterDraft rochester -> new DraftView(id, source, format, HUMAN_SEAT, yourTurn, rochester.finished(),
                    rochester.round(), rochester.pickInPack(), rochester.direction().name(),
                    rochester.packNumber(), rochester.opener(), rochester.activeSeat(),
                    EventProjection.cards(rochester.table()), picks, null, seats, log);
            case WinstonDraft winston -> new DraftView(id, source, format, HUMAN_SEAT, yourTurn, winston.finished(),
                    null, null, null, null, null, winston.activeSeat(),
                    yourTurn ? EventProjection.cards(winston.pile(winston.currentPile())) : List.of(), picks,
                    new WinstonView(winston.pileSizes(), winston.currentPile(), winston.stackSize()), seats, log);
        };
    }

    private static List<DraftEventView> log(List<DraftEvent> history) {
        List<DraftEventView> visible = history.stream()
                .map(event -> EventProjection.forSeat(event, HUMAN_SEAT))
                .flatMap(Optional::stream)
                .toList();
        return visible.subList(Math.max(0, visible.size() - LOG_SIZE), visible.size());
    }
}
