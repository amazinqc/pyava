package com.pyava;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Engine {

    private static final Logger log = LoggerFactory.getLogger(Engine.class);

    private static Method getMethod(String methodName, JSONArray args, Class<?> clz) {
        int argSize = args.size();
        List<Method> methods = Stream.concat(Arrays.stream(clz.getDeclaredMethods()), Arrays.stream(clz.getMethods()))
                .filter(m -> m.getName().equals(methodName))
                .filter(m -> m.getParameterCount() == argSize || m.isVarArgs())
                .distinct()
                .collect(Collectors.toList());
        if (methods.isEmpty()) {
            return null;
        }
        for (Iterator<Method> iterator = methods.iterator(); iterator.hasNext(); ) {
            Method method = iterator.next();
            Class<?>[] params = method.getParameterTypes();
            boolean isVarArgs = method.isVarArgs();
            int last = params.length - 1;
            for (int i = 0; i < argSize; ++i) {
                Object arg = args.get(i);
                if (arg != null) {
                    Class<?> parameterType = isVarArgs && i >= last ? params[last].getComponentType() : params[i];
                    Class<?> argClass = arg.getClass();
                    if (parameterType.isAssignableFrom(argClass)) {
                        continue;
                    }
                    if (parameterType.isPrimitive()) {
                        try {
                            if (((Class<?>) argClass.getField("TYPE").get(null)).isPrimitive()) {
                                continue;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    if (Number.class.isAssignableFrom(parameterType) && Number.class.isAssignableFrom(argClass)) {
                        continue;
                    }
                    iterator.remove();
                }
            }
        }
        if (methods.isEmpty()) {
            return null;
        }
        if (methods.size() > 1) {
            methods = methods.stream().filter(m -> !m.isBridge() && !m.isSynthetic()).collect(Collectors.toList());
            if (methods.size() > 1) {
                log.warn("发现重复的可执行方法: {}", methods);
            }
        }
        return methods.get(0);
    }


    private boolean validate(JSONObject data) {
        return data != null;
    }

    public JSONObject agent(JSONObject data) {
        if (!validate(data)) {
            log.warn(JSON.toJSONString(data));
            throw new IllegalStateException();
        }
        JSONObject json = data.getJSONObject("json");
        if (json == null) {
            return MessageFactory.error("数据错误");
        }
        try {
            Object result = handleInvoke(null, json);
            return MessageFactory.ok(result);
        } catch (DebugException e) {
            return MessageFactory.error(e.getMessage());
        } catch (Exception e) {
            return MessageFactory.error(e.toString());
        } finally {
            Locals.remove();
        }
    }

    private Object handleInvoke(Object invoker, JSONObject details) {
        JSONArray chains = details.getJSONArray("chains");
        if (chains == null) {
            Object returned = parseInvoke(invoker, details);
            return Locals.setVar((details.getString("local")), returned);
        }
        for (int i = 0, size = chains.size(); i < size; ++i) {
            JSONObject detail = chains.getJSONObject(i);
            Object returned = parseInvoke(invoker, detail);

            if (returned == null && i + 1 != size) {
                String type = chains.getJSONObject(i + 1).getString("type");
                if (!"local".equals(type) && !"class".equals(type)) {
                    Class<?> clz = invoker instanceof Class ? (Class<?>) invoker : invoker.getClass();
                    throw new DebugException(clz.getSimpleName(), detail.getString("method"), detail.getJSONArray("args"), "返回为null");
                }
            }
            invoker = Locals.setVar(detail.getString("local"), returned);
        }
        return invoker;
    }

    private Object parseInvoke(Object self, JSONObject detail) {
        String type = detail.getString("type");
        if ("self".equals(type)) {
            return self;
        }
        if ("local".equals(type)) {
            return Locals.getVar(detail.getString("ref"));
        }
        if ("class".equals(type)) {
            String clazz = detail.getString("ref");
            try {
                return Class.forName(clazz);
            } catch (ClassNotFoundException e) {
                throw new DebugException("class(" + clazz + ")不存在");
            }
        }
        String method = detail.getString("method");
        JSONArray args = detail.getJSONArray("args");
        if (method == null || method.isEmpty()) {
            throw new DebugException("行为缺失：" + detail);
        }
        if (args == null) {
            args = new JSONArray();
        }
        if (type == null) {
            return methodInvoke(self, method, args);
        }
        if ("iter".equals(type)) {
            if (!(self instanceof Iterable)) {
                throw new DebugException("目标对象不支持迭代操作");
            }
            List<Object> list = new ArrayList<>();
            for (Object item : (Iterable<?>) self) {
                list.add((methodInvoke(item, method, args)));
            }
            return list;
        }
        throw new DebugException("未知的请求类型：" + detail);
    }

    private Object methodInvoke(Object invoker, String methodName, JSONArray args) {
        int argSize = args.size();
        for (int i = 0; i < argSize; ++i) {
            Object arg = args.get(i);
            if (arg instanceof Map) {
                @SuppressWarnings("unchecked")
                JSONObject nested = new JSONObject((Map<String, Object>) arg);
                args.set(i, handleInvoke(invoker, nested));
            }
        }

        Class<?> clz = invoker.getClass();
        Method method = getMethod(methodName, args, clz);
        if (method == null && invoker instanceof Class) {
            clz = (Class<?>) invoker;
            invoker = null;
            method = getMethod(methodName, args, clz);
        }
        if (method == null) {
            throw new DebugException(clz.getSimpleName(), methodName, args, "不存在");
        }
        method.setAccessible(true);
        Class<?>[] parameters = method.getParameterTypes();
        boolean isVarArgs = method.isVarArgs();
        for (int i = 0; i < parameters.length; ++i) {
            if (isVarArgs && i >= parameters.length - 1) {
                ArrayList<Object> varArgs = new ArrayList<>(args.subList(i, argSize));
                args = new JSONArray(args.subList(0, parameters.length));
                args.set(i, varArgs);
            }
            args.set(i, args.getObject(i, parameters[i]));
        }
        try {
            if (invoker instanceof AccessibleObject) {
                ((AccessibleObject) invoker).setAccessible(true);
            }
            return method.invoke(invoker, args.toArray());
        } catch (IllegalAccessException e) {
            throw new DebugException(clz.getSimpleName(), methodName, args, "调用错误: " + e.getMessage());
        } catch (InvocationTargetException e) {
            throw new DebugException(clz.getSimpleName(), methodName, args, "调用错误: " + e.getTargetException());
        }
    }
}

