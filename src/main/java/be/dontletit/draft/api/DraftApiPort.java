package be.dontletit.draft.api;

import java.util.List;
import java.util.Optional;

public interface DraftApiPort {

    List<DraftFormatView> listFormats();

    /**
     * Starts a draft from random cards of the database.
     * @param format a {@link DraftFormatView#name()}, case-insensitive; null means CLASSIC
     * @throws IllegalArgumentException on an unknown format
     */
    DraftView startRandomDraft(String format);

    /**
     * Starts a draft from a cube collection.
     * @throws java.util.NoSuchElementException when the collection does not exist
     * @throws IllegalArgumentException on an unknown format or when the cube is too small for it
     */
    DraftView startCubeDraft(String format, String collectionId);

    Optional<DraftView> getDraft(String draftId);

    /**
     * The player acts, then the other seats play until it is the player's turn again or the draft ends.
     * @throws IllegalArgumentException when the action does not exist, does not fit the format or names a card that is not there
     * @throws IllegalStateException when the draft is finished
     */
    Optional<DraftView> act(String draftId, DraftAction action);

    /** Shortcut for a {@code PICK} action. */
    default Optional<DraftView> pick(String draftId, String cardName) {
        return act(draftId, DraftAction.pick(cardName));
    }

    List<DraftSourceView> listCubes();

    /**
     * Saves the player's picks so far as a DECK collection in the card database.
     * @throws IllegalArgumentException on a blank name
     * @throws IllegalStateException when nothing has been picked yet
     */
    Optional<SavedDeckView> saveDeck(String draftId, String deckName);
}
