package com.healthcare.sandbox.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@CrossOrigin(origins = "*")
public class AdminController {

    /**
     * Forwards requests from /admin/adt-console to the static /admin/adt-console.html resource,
     * maintaining a clean URL structure in the browser.
     */
    @GetMapping("/admin/adt-console")
    public String adtConsole() {
        return "forward:/admin/adt-console.html";
    }
}
