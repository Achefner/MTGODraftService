package be.dontletit.draft.infrastructure.config;

import be.dontletit.draft.domain.PickTimer;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** The pick timer settings from application.properties ({@code draft.timer.*}). */
public class DraftConfig {

    @Produces
    @Singleton
    PickTimer pickTimer(@ConfigProperty(name = "draft.timer.classic-seconds-per-card") int classicSecondsPerCard,
                        @ConfigProperty(name = "draft.timer.classic-min-seconds") int classicMinSeconds,
                        @ConfigProperty(name = "draft.timer.rochester-first-pick-seconds") int rochesterFirstPickSeconds,
                        @ConfigProperty(name = "draft.timer.rochester-pick-seconds") int rochesterPickSeconds,
                        @ConfigProperty(name = "draft.timer.winston-move-seconds") int winstonMoveSeconds) {
        return new PickTimer(classicSecondsPerCard, classicMinSeconds, rochesterFirstPickSeconds, rochesterPickSeconds, winstonMoveSeconds);
    }
}
