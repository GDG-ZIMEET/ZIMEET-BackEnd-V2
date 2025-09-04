package com.gdg.z_meet;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticController {

    @GetMapping("/")
    public String home() {
        return "forward:/static/index.html"; // index.html 을 서빙
    }

    @GetMapping("/favicon.ico")
    public String favicon() {
        return "forward:/static/favicon.ico"; // favicon.ico 서빙
    }
}