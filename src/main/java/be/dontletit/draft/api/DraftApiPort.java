package be.dontletit.draft.api;

import java.util.List;
import java.util.Optional;

public interface DraftApiPort {

    /** Starts a draft from random cards of the database. */
    DraftView startRandomDraft();

    /**
     * Starts a draft from a cube collection.
     * @throws java.util.NoSuchElementException when the collection does not exist
     * @throws IllegalArgumentException when it holds fewer cards than a draft needs
     */
    DraftView startCubeDraft(String collectionId);

    Optional<DraftView> getDraft(String draftId);

    /**
     * The human player picks a card; bots pick and packs are passed.
     * @throws IllegalArgumentException when the card is not in the current pack
     * @throws IllegalStateException when the draft is finished
     */
    Optional<DraftView> pick(String draftId, String cardName);

    List<DraftSourceView> listCubes();

    /**
     * Saves the human player's picks so far as a DECK collection in the card database.
     * @throws IllegalArgumentException on a blank name
     * @throws IllegalStateException when nothing has been picked yet
     */
    Optional<SavedDeckView> saveDeck(String draftId, String deckName);
}
