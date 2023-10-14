package com.pyava;

import java.util.HashMap;
import java.util.Map;

public class Locals {

    private static final ThreadLocal<Map<String, Object>> locals = ThreadLocal.withInitial(HashMap::new);

    private Locals() {
    }

    public static Object getVar(String name) {
        return locals.get().get(name);
    }

    public static Object setVar(String name, Object value) {
        if (name != null && !name.isEmpty()) {
            locals.get().put(name, value);
        }
        return value;
    }

    public static void remove() {
        locals.remove();
    }
}
