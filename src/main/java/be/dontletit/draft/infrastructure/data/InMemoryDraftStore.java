package be.dontletit.draft.infrastructure.data;

import be.dontletit.draft.domain.Draft;
import be.dontletit.draft.spi.DraftStoreSpiPort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Drafts live only as long as the service: they are short-lived sessions. */
@ApplicationScoped
public class InMemoryDraftStore implements DraftStoreSpiPort {

    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();

    @Override
    public void save(Draft draft) {
        drafts.put(draft.id(), draft);
    }

    @Override
    public Optional<Draft> find(String draftId) {
        return Optional.ofNullable(draftId == null ? null : drafts.get(draftId));
    }
}
