package kr.co.himatch.ai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RouteController {

    @GetMapping("/pdfRag")
    public String korea(){
        return "rag"; // rag.html
    }
}
