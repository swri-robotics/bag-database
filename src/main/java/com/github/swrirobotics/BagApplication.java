package com.github.swrirobotics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class BagApplication {
    public static void main(String[] args) {
        SpringApplication.run(BagApplication.class, args);
    }
}
