package be.dontletit.draft.infrastructure.data;

import be.dontletit.draft.domain.event.DraftEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Draft events as stored: the event's simple class name plus its JSON. */
@ApplicationScoped
public class EventCodec {

    public record Encoded(String type, String payload) {}

    private final ObjectMapper mapper;
    private final Map<String, Class<?>> types = Arrays.stream(DraftEvent.class.getPermittedSubclasses())
            .collect(Collectors.toMap(Class::getSimpleName, Function.identity()));

    @Inject
    public EventCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public Encoded encode(DraftEvent event) {
        try {
            return new Encoded(event.getClass().getSimpleName(), mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot store event " + event, e);
        }
    }

    public DraftEvent decode(String type, String payload) {
        Class<?> eventClass = types.get(type);
        if (eventClass == null) throw new IllegalStateException("Unknown stored event type: " + type);
        try {
            return (DraftEvent) mapper.readValue(payload, eventClass);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read stored " + type + " event", e);
        }
    }
}
