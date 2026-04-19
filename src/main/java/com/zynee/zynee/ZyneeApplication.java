package com.zynee.zynee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@ServletComponentScan // Enables @WebFilter
public class ZyneeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZyneeApplication.class, args);
    }
}