package com.example.gadgetariumb8;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootApplication
@Controller
public class GadgetariumB8Application {

    public static void main(String[] args) {
        SpringApplication.run(GadgetariumB8Application.class, args);
    }

    @GetMapping
    String introduction(){
        return "introduction";
    }

}
