package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftAction;
import be.dontletit.draft.api.DraftApiPort;
import be.dontletit.draft.api.DraftFormatView;
import be.dontletit.draft.api.DraftListener;
import be.dontletit.draft.api.DraftSourceView;
import be.dontletit.draft.api.DraftUpdate;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.OpenTableRequest;
import be.dontletit.draft.api.SavedDeckView;
import be.dontletit.draft.api.SeatAccessDeniedException;
import be.dontletit.draft.api.SeatTicket;
import be.dontletit.draft.api.Subscription;
import be.dontletit.draft.api.TablePreview;
import be.dontletit.draft.domain.Draft;
import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.domain.DraftFormat;
import be.dontletit.draft.domain.PickTimer;
import be.dontletit.draft.domain.RandomBot;
import be.dontletit.draft.domain.command.DraftCommand;
import be.dontletit.draft.domain.command.PassPile;
import be.dontletit.draft.domain.command.PickCard;
import be.dontletit.draft.domain.command.TakePile;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.spi.CardPoolSpiPort;
import be.dontletit.draft.spi.DraftRepositorySpiPort;
import be.dontletit.draft.spi.PlayerRecord;
import be.dontletit.draft.spi.TableRecord;
import be.dontletit.draft.spi.TableStatus;
import be.dontletit.draft.spi.TimerSpiPort;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Runs tables: lobbies, seats and their tokens, bots on the seats nobody took, pick clocks, and
 * live updates to every listening seat. The draft rules stay in the domain; every change is stored
 * as events before anyone is told about it.
 */
@ApplicationScoped
public class DraftApiAdapter implements DraftApiPort {

    private static final System.Logger LOG = System.getLogger(DraftApiAdapter.class.getName());
    static final int HOST_SEAT = 0;
    private static final int MAX_NAME_LENGTH = 40;

    private final CardPoolSpiPort cardPool;
    private final DraftRepositorySpiPort repository;
    private final TimerSpiPort timer;
    private final PickTimer pickTimer;
    private final Supplier<Random> randomSupplier;
    private final Map<String, DraftSession> sessions = new ConcurrentHashMap<>();

    @Inject
    public DraftApiAdapter(CardPoolSpiPort cardPool, DraftRepositorySpiPort repository, TimerSpiPort timer, PickTimer pickTimer) {
        this(cardPool, repository, timer, pickTimer, Random::new);
    }

    DraftApiAdapter(CardPoolSpiPort cardPool, DraftRepositorySpiPort repository, TimerSpiPort timer, PickTimer pickTimer,
                    Supplier<Random> randomSupplier) {
        this.cardPool = cardPool;
        this.repository = repository;
        this.timer = timer;
        this.pickTimer = pickTimer;
        this.randomSupplier = randomSupplier;
    }

    // ---------------------------------------------------------------- catalogue

    @Override
    public List<DraftFormatView> listFormats() {
        return Arrays.stream(DraftFormat.values())
                .map(f -> new DraftFormatView(f.name(), f.displayName(), f.seats(), f.cardsNeeded(), f.description()))
                .toList();
    }

    @Override
    public List<DraftSourceView> listCubes() {
        return cardPool.listCubes().stream()
                .map(cube -> new DraftSourceView(cube.id(), cube.name(), cube.cardCount()))
                .toList();
    }

    // ---------------------------------------------------------------- tables

    @Override
    public SeatTicket openTable(OpenTableRequest request) {
        if (request == null) throw new IllegalArgumentException("source is required: random or cube");
        DraftFormat format = parseFormat(request.format());
        String source = parseSource(request.source(), request.collectionId());
        List<DraftCard> cubeCards = source.startsWith("cube:") ? cards(source, format) : null;

        String token = SeatTokens.newToken();
        PlayerRecord host = new PlayerRecord(HOST_SEAT, playerName(request.playerName(), HOST_SEAT), SeatTokens.hash(token));
        TableRecord table = new TableRecord(UUID.randomUUID().toString(), format, source, TableStatus.LOBBY,
                request.timed(), timer.now(), List.of(host));
        repository.saveTable(table);
        DraftSession session = new DraftSession(table, null, new RandomBot(randomSupplier.get()));
        sessions.put(table.id(), session);

        synchronized (session) {
            if (request.startNow()) startDraft(session, cubeCards != null ? cubeCards : cards(source, format));
            return new SeatTicket(table.id(), HOST_SEAT, token, DraftViews.view(session, HOST_SEAT, timer.now()));
        }
    }

    @Override
    public TablePreview preview(String draftId) {
        DraftSession session = session(draftId);
        synchronized (session) {
            return DraftViews.preview(session);
        }
    }

    @Override
    public SeatTicket join(String draftId, String playerName) {
        DraftSession session = session(draftId);
        synchronized (session) {
            if (session.table.status() != TableStatus.LOBBY) throw new IllegalStateException("The draft has already started");
            int seat = 0;
            while (seat < session.table.format().seats() && session.player(seat).isPresent()) seat++;
            if (seat == session.table.format().seats()) throw new IllegalStateException("The table is full");
            String token = SeatTokens.newToken();
            TableRecord table = session.table.withPlayer(new PlayerRecord(seat, playerName(playerName, seat), SeatTokens.hash(token)));
            repository.saveTable(table);
            session.table = table;
            notifyListeners(session);
            return new SeatTicket(draftId, seat, token, DraftViews.view(session, seat, timer.now()));
        }
    }

    @Override
    public DraftView start(String draftId, String token) {
        DraftSession session = session(draftId);
        synchronized (session) {
            int seat = seatOf(session, token);
            if (seat != HOST_SEAT) throw new SeatAccessDeniedException("Only the host can start the draft");
            if (session.table.status() != TableStatus.LOBBY) throw new IllegalStateException("The draft has already started");
            startDraft(session, cards(session.table.source(), session.table.format()));
            return DraftViews.view(session, seat, timer.now());
        }
    }

    private void startDraft(DraftSession session, List<DraftCard> cards) {
        Draft draft = Draft.start(session.table.format(), session.id(), cards, randomSupplier.get());
        session.bot.playUntilBlocked(draft, session.humanSeats());
        repository.appendEvents(session.id(), 0, draft.history());
        session.draft = draft;
        setStatus(session, draft.finished() ? TableStatus.FINISHED : TableStatus.RUNNING);
        rearmClocks(session);
        notifyListeners(session);
    }

    // ---------------------------------------------------------------- playing

    @Override
    public DraftView view(String draftId, String token) {
        DraftSession session = session(draftId);
        synchronized (session) {
            return DraftViews.view(session, seatOf(session, token), timer.now());
        }
    }

    @Override
    public DraftView act(String draftId, String token, DraftAction action) {
        DraftSession session = session(draftId);
        synchronized (session) {
            int seat = seatOf(session, token);
            DraftCommand command = toCommand(action, seat);
            if (session.draft == null) throw new IllegalStateException("The draft has not started yet");
            if (!session.running()) throw new IllegalStateException("The draft is over");
            int moves = session.draft.moveCount(seat);
            if (action.expectedMoves() != null && action.expectedMoves() != moves) {
                throw new IllegalStateException("Your view is out of date: you have made " + moves
                        + " moves, the action was meant for move " + (action.expectedMoves() + 1) + ". Reload the draft.");
            }
            play(session, command);
            return DraftViews.view(session, seat, timer.now());
        }
    }

    /** Runs the command and the bots' answers, stores them, then rearms clocks and tells the listeners. */
    private void play(DraftSession session, DraftCommand command) {
        Draft draft = session.draft;
        int from = draft.history().size();
        try {
            draft.handle(command);
            session.bot.playUntilBlocked(draft, session.humanSeats());
            repository.appendEvents(session.id(), from, draft.history().subList(from, draft.history().size()));
        } catch (RuntimeException e) {
            if (draft.history().size() > from) session.draft = Draft.replay(repository.loadEvents(session.id()));
            throw e;
        }
        if (draft.finished()) setStatus(session, TableStatus.FINISHED);
        rearmClocks(session);
        notifyListeners(session);
    }

    private static DraftCommand toCommand(DraftAction action, int seat) {
        if (action == null || action.type() == null) throw new IllegalArgumentException("An action type is required: PICK, TAKE_PILE or PASS_PILE");
        return switch (action.type().trim().toUpperCase(Locale.ROOT)) {
            case "PICK" -> new PickCard(seat, action.cardName());
            case "TAKE_PILE" -> new TakePile(seat);
            case "PASS_PILE" -> new PassPile(seat);
            default -> throw new IllegalArgumentException("Unknown action: " + action.type() + " (expected PICK, TAKE_PILE or PASS_PILE)");
        };
    }

    @Override
    public SavedDeckView saveDeck(String draftId, String token, String deckName) {
        DraftSession session = session(draftId);
        List<String> names;
        synchronized (session) {
            int seat = seatOf(session, token);
            if (deckName == null || deckName.isBlank()) throw new IllegalArgumentException("Deck name must not be blank");
            names = session.draft == null ? List.of() : session.draft.picksOf(seat).stream().map(DraftCard::name).toList();
        }
        if (names.isEmpty()) throw new IllegalStateException("Nothing has been picked yet");
        String collectionId = cardPool.saveDeck(deckName.trim(), names);
        return new SavedDeckView(collectionId, deckName.trim(), names.size());
    }

    // ---------------------------------------------------------------- clocks

    /** Every human seat that can act has a clock for its current decision; nobody else has one. */
    private void rearmClocks(DraftSession session) {
        if (!session.table.timed() || !session.running()) {
            session.cancelClocks();
            return;
        }
        for (int seat : session.humanSeats()) {
            DraftSession.SeatClock clock = session.clocks.get(seat);
            if (!session.draft.canAct(seat)) {
                if (clock != null) session.clocks.remove(seat).alarm().cancel();
                continue;
            }
            int moves = session.draft.moveCount(seat);
            if (clock != null && clock.moves() == moves) continue;
            if (clock != null) clock.alarm().cancel();
            Duration time = pickTimer.timeFor(session.draft, seat);
            String id = session.id();
            TimerSpiPort.Cancellable alarm = timer.schedule(time, () -> timeRanOut(id, seat, moves));
            session.clocks.put(seat, new DraftSession.SeatClock(moves, timer.now().plus(time), (int) time.toSeconds(), alarm));
        }
    }

    /** The seat did not decide in time: it makes a random legal move. */
    private void timeRanOut(String draftId, int seat, int moves) {
        DraftSession session = sessions.get(draftId);
        if (session == null) return;
        synchronized (session) {
            DraftSession.SeatClock clock = session.clocks.get(seat);
            if (clock == null || clock.moves() != moves || !session.running() || !session.draft.canAct(seat)) return;
            session.clocks.remove(seat);
            try {
                play(session, session.bot.choose(session.draft, seat));
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Automatic move for seat " + seat + " of draft " + draftId + " failed", e);
                rearmClocks(session);
            }
        }
    }

    // ---------------------------------------------------------------- live updates

    @Override
    public Subscription subscribe(String draftId, String token, Long lastSeenVersion, DraftListener target) {
        DraftSession session = session(draftId);
        synchronized (session) {
            int seat = seatOf(session, token);
            boolean wasConnected = session.connected(seat);
            long version = session.version();
            long from = lastSeenVersion == null || lastSeenVersion < 0 || lastSeenVersion > version ? version : lastSeenVersion;
            DraftSession.Listener listener = new DraftSession.Listener(seat, target, from);
            session.listeners.add(listener);
            if (!send(session, listener)) session.listeners.remove(listener);
            else if (!wasConnected) notifyListeners(session, listener);
        }
        return () -> {
            synchronized (session) {
                DraftSession.Listener found = session.listeners.stream().filter(l -> l.target == target).findFirst().orElse(null);
                if (found == null) return;
                session.listeners.remove(found);
                if (!session.connected(found.seat)) notifyListeners(session);
            }
        };
    }

    /** Sends every listener what changed; drops the ones that fail and tells the others. */
    private void notifyListeners(DraftSession session) {
        notifyListeners(session, null);
    }

    private void notifyListeners(DraftSession session, DraftSession.Listener except) {
        List<DraftSession.Listener> dropped = new ArrayList<>();
        for (DraftSession.Listener listener : List.copyOf(session.listeners)) {
            if (listener != except && !send(session, listener)) dropped.add(listener);
        }
        if (!dropped.isEmpty()) {
            session.listeners.removeAll(dropped);
            notifyListeners(session);
        }
    }

    private boolean send(DraftSession session, DraftSession.Listener listener) {
        long version = session.version();
        List<DraftEvent> history = session.draft == null ? List.of() : session.draft.history();
        DraftUpdate update = new DraftUpdate(version, DraftViews.visibleSince(history, listener.lastSent, listener.seat),
                DraftViews.view(session, listener.seat, timer.now()));
        try {
            listener.target.onUpdate(update);
            listener.lastSent = version;
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- loading and restarting

    @Override
    public void resumeRunningDrafts() {
        for (TableRecord table : repository.findTables(TableStatus.RUNNING)) {
            try {
                session(table.id());
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "Could not reload draft " + table.id(), e);
            }
        }
    }

    /** Stops every clock, as the service shuts down. */
    @PreDestroy
    void shutdown() {
        sessions.values().forEach(session -> {
            synchronized (session) {
                session.cancelClocks();
            }
        });
    }

    private DraftSession session(String draftId) {
        if (draftId == null) throw new NoSuchElementException("Draft not found: null");
        return sessions.computeIfAbsent(draftId, this::load);
    }

    private DraftSession load(String draftId) {
        TableRecord table = repository.findTable(draftId).orElseThrow(() -> new NoSuchElementException("Draft not found: " + draftId));
        List<DraftEvent> events = repository.loadEvents(draftId);
        DraftSession session = new DraftSession(table, events.isEmpty() ? null : Draft.replay(events), new RandomBot(randomSupplier.get()));
        synchronized (session) {
            if (session.draft != null && table.status() == TableStatus.LOBBY) setStatus(session, TableStatus.RUNNING);
            if (session.draft != null && session.draft.finished() && table.status() != TableStatus.FINISHED) setStatus(session, TableStatus.FINISHED);
            rearmClocks(session);
        }
        return session;
    }

    private void setStatus(DraftSession session, TableStatus status) {
        session.table = session.table.withStatus(status);
        repository.saveTable(session.table);
    }

    // ---------------------------------------------------------------- input checks

    private static int seatOf(DraftSession session, String token) {
        if (token == null || token.isBlank()) throw new SeatAccessDeniedException("A seat token is required");
        String hash = SeatTokens.hash(token);
        return session.table.players().stream()
                .filter(p -> p.tokenHash().equals(hash))
                .mapToInt(PlayerRecord::seat)
                .findFirst()
                .orElseThrow(() -> new SeatAccessDeniedException("This seat token does not belong to draft " + session.id()));
    }

    private static DraftFormat parseFormat(String format) {
        if (format == null || format.isBlank()) return DraftFormat.CLASSIC;
        try {
            return DraftFormat.valueOf(format.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown format: " + format + " (expected one of " + Arrays.toString(DraftFormat.values()) + ")");
        }
    }

    private static String parseSource(String source, String collectionId) {
        if (source == null) throw new IllegalArgumentException("source is required: random or cube");
        return switch (source.trim().toLowerCase(Locale.ROOT)) {
            case "random" -> "random";
            case "cube" -> {
                if (collectionId == null || collectionId.isBlank()) throw new IllegalArgumentException("collectionId is required for a cube draft");
                yield "cube:" + collectionId.trim();
            }
            default -> throw new IllegalArgumentException("Unknown source: " + source + " (expected random or cube)");
        };
    }

    /** The cards for a draft: random ones, or the cube's, checked against the format's needs. */
    private List<DraftCard> cards(String source, DraftFormat format) {
        if (source.equals("random")) return cardPool.randomCards(format.cardsNeeded());
        String collectionId = source.substring("cube:".length());
        List<DraftCard> cards = cardPool.collectionCards(collectionId)
                .orElseThrow(() -> new NoSuchElementException("Collection not found: " + collectionId));
        if (cards.size() < format.cardsNeeded()) {
            throw new IllegalArgumentException("A " + format.displayName() + " draft needs " + format.cardsNeeded()
                    + " cards but the cube has " + cards.size());
        }
        return cards;
    }

    private static String playerName(String name, int seat) {
        if (name == null || name.isBlank()) return "Player " + (seat + 1);
        String trimmed = name.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
