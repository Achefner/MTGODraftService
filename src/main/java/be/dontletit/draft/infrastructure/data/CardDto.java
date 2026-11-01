package be.dontletit.draft.infrastructure.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The fields of an MTGODatabase card this service needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardDto(String name, String imgUrl, String manaCost) {

    public CardDto(String name, String imgUrl) {
        this(name, imgUrl, null);
    }
}
