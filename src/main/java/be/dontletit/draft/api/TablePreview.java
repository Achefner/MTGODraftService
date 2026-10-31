package be.dontletit.draft.api;

import java.util.List;

public record TablePreview(String id, String format, String source, String status, boolean timed, List<SeatView> seats) {}
