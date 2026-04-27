package org.example.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.example.realtime", "org.example.common"})
public class RealtimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(RealtimeApplication.class, args);
    }
}
