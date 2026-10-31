package be.dontletit.draft.infrastructure.config;

import be.dontletit.draft.api.DraftApiPort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/** After a restart, running drafts are reloaded and their pick clocks rearmed. */
@ApplicationScoped
public class DraftStartup {

    @Inject
    DraftApiPort draftApi;

    void onStart(@Observes StartupEvent event) {
        draftApi.resumeRunningDrafts();
    }
}
