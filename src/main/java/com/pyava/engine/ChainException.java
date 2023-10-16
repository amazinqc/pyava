package com.pyava.engine;

import com.alibaba.fastjson2.JSONArray;

public class ChainException extends RuntimeException {

    public ChainException(String message) {
        super(message, null, true, false);
    }

    /**
     * {@code c}.{@code m}({@code args...}): {@code message}
     */
    ChainException(String c, String m, JSONArray args, String message) {
        this(c + '.' + m + '(' + (m = MessageFactory.stringify(args)).substring(1, m.length() - 1) + "): " + message);
    }
}
