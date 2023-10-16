package com.pyava.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MessageFactory {

    public static JSONObject error(String message) {
        JSONObject json = new JSONObject(4);
        json.put("code", 500);
        json.put("message", message);
        return json;
    }

    public static JSONObject ok(Object result) {
        JSONObject json = new JSONObject(4);
        json.put("code", 200);
        json.put("data", flatJson(result));
        return JSON.parseObject(stringify(json));
    }

    private static Object flatJson(Object data) {
        if (data instanceof Message) {
            return pbJson((Message) data);
        }
        if (data instanceof Collection<?> collection) {
            JSONArray array = new JSONArray(collection.size());
            for (Object element : collection) {
                array.add(flatJson(element));
            }
            return array;
        }
        if (data instanceof Map<?, ?> map) {
            JSONObject jsonObject = new JSONObject(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                jsonObject.put(entry.getKey().toString(), flatJson(entry.getValue()));
            }
            return jsonObject;
        }
        return data;// else array
    }

    private static Object pbJson(Message pb) {
        try {
            return JSONObject.parseObject(JsonFormat.printer().includingDefaultValueFields().print(pb));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private static String stringify(Object obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.IgnoreErrorGetter, JSONWriter.Feature.IgnoreNoneSerializable);
    }

    static String stringify(JSONArray args) {
        String s = stringify((Object) args);
        if (s.length() <= 10) {
            return s;
        } else {
            Function<Object, String> mapper = s.length() > 30
                    ? v -> v == null ? "null" : v.getClass().getSimpleName()
                    : v -> v == null ? "null" : v + ":" + v.getClass().getSimpleName();
            return args.stream().map(mapper).collect(Collectors.toList()).toString();
        }
    }

}
