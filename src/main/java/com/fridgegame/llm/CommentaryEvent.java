package com.fridgegame.llm;

/**
 * Something worth remarking on.
 *
 * <p>Events arrive far faster than a player can read captions, so they carry a
 * {@link #priority()} and the throttle keeps only the most interesting one pending. The
 * ordering is by how much the player would want it acknowledged: a top-out is the loudest
 * thing that can happen, an idle nudge the quietest.
 *
 * @param detail a short factual description, put in front of the model as the thing to
 *               react to — never a suggested line, so the wording stays the model's
 */
public record CommentaryEvent(Kind kind, String detail) {

    public enum Kind {
        /** The stack reached the top. */
        TOP_OUT(100),
        /** An item went into a zone that does not accept it. */
        WRONG_DROP(90),
        /** The clock is running down with items still unsorted. */
        CLOCK_LOW(70),
        /** A placement buried cells that can no longer be reached. */
        NEW_HOLES(60),
        /** Rows went down. */
        ROWS_CLEARED(50),
        /** A run of correct drops. */
        STREAK(40),
        /** The level just started. */
        LEVEL_START(30),
        /** Nothing has happened for a while. */
        STALLED(10);

        private final int priority;

        Kind(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }
    }

    public int priority() {
        return kind.priority();
    }

    public static CommentaryEvent of(Kind kind, String detail) {
        return new CommentaryEvent(kind, detail);
    }
}
