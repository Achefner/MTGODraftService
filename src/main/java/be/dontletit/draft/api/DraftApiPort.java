package be.dontletit.draft.api;

import java.util.List;

/**
 * Drafting at a table. Whoever opens a table sits at seat 0 and hosts it; others join with its id.
 * Every call about a seat carries the secret seat token handed out when the seat was taken.
 * Unknown drafts throw {@link java.util.NoSuchElementException}, wrong tokens {@link SeatAccessDeniedException}.
 */
public interface DraftApiPort {

    List<DraftFormatView> listFormats();

    List<DraftSourceView> listCubes();

    /**
     * Opens a table and seats its host. With {@code startNow} the draft starts at once and every
     * other seat is a bot.
     * @throws IllegalArgumentException on an unknown format, a cube too small for it, or a missing source
     * @throws java.util.NoSuchElementException when the cube does not exist
     */
    SeatTicket openTable(OpenTableRequest request);

    /** Public facts about a table: format, status and who sits where. No token needed. */
    TablePreview preview(String draftId);

    /** @throws IllegalStateException when the draft has started or the table is full */
    SeatTicket join(String draftId, String playerName);

    /**
     * The host starts the draft: empty seats become bots.
     * @throws SeatAccessDeniedException when the token is not the host's
     * @throws IllegalStateException when it has already started
     */
    DraftView start(String draftId, String token);

    DraftView view(String draftId, String token);

    /**
     * The seat plays, then the bots play as far as they can.
     * @throws IllegalArgumentException when the action does not exist, does not fit the format or names a card that is not there
     * @throws IllegalStateException when the draft is not running, it is not the seat's turn, or
     *         {@link DraftAction#expectedMoves()} does not match (the client's view is out of date)
     */
    DraftView act(String draftId, String token, DraftAction action);

    /**
     * Saves the seat's picks so far as a DECK collection in the card database.
     * @throws IllegalArgumentException on a blank name
     * @throws IllegalStateException when nothing has been picked yet
     */
    SavedDeckView saveDeck(String draftId, String token, String deckName);

    /**
     * Follows the draft from a seat. The listener first receives the current view, with the events
     * after {@code lastSeenVersion} when it is given, then an update after every change.
     */
    Subscription subscribe(String draftId, String token, Long lastSeenVersion, DraftListener listener);

    /** After a restart: reloads the running drafts from their history and rearms their timers. */
    void resumeRunningDrafts();
}
