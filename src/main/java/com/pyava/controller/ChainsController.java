package com.pyava.controller;

import com.alibaba.fastjson2.JSONObject;
import com.pyava.engine.Engine;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChainsController {

    private final Engine engine = new Engine();

    @PostMapping(value = "Local")
    public JSONObject debug(@RequestBody JSONObject json) {
        return engine.agent(json);
    }
}
