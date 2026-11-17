package com.mentia;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MentiaApiApplication {

    public static void main(String[] args) {
        // Todo el sistema trabaja en UTC; además evita que el driver mande un ID de zona legacy que PostgreSQL rechaza.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(MentiaApiApplication.class, args);
    }
}
