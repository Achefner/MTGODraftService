package be.dontletit.draft.api;

import java.util.List;

/** The Winston table as the player sees it: pile sizes, the pile being looked at (0-based) and the stack size. */
public record WinstonView(List<Integer> pileSizes, int currentPile, int stackSize) {}
