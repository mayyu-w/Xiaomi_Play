package com.xiaomi.sdk.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA 路由兜底：将所有非 /api 请求转发到 index.html
 * @author awen
 */
@Controller
public class SpaController {

    @RequestMapping(value = {"/", "/login", "/devices", "/control/**", "/voice"})
    public String forward() {
        return "forward:/index.html";
    }
}
