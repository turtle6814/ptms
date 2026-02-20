package com.example.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards all non-API, non-static-resource requests to index.html
 * so that React Router can handle client-side routing.
 */
@Controller
public class SpaForwardingController {

    @RequestMapping(value = {
            "/",
            "/login",
            "/signup",
            "/admin",
            "/admin/**",
            "/setup",
            "/setup/**",
            "/events",
            "/events/**",
            "/view/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
