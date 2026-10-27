package com.warehouse.ai;

/** Helpers for model replies that should be JSON but sometimes arrive wrapped in prose or code fences. */
public final class JsonReplies {

    private JsonReplies() {
    }

    /** Returns the outermost {@code {...}} block of the reply, or the reply unchanged if none is found. */
    public static String extractObject(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        return start >= 0 && end > start ? reply.substring(start, end + 1) : reply;
    }
}
