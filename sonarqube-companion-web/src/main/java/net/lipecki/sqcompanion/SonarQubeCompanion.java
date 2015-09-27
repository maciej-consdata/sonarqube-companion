package net.lipecki.sqcompanion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;

@Controller
@EnableAutoConfiguration
@EnableScheduling
@ComponentScan
public class SonarQubeCompanion {

    public static void main(final String[] args) throws Exception {
        SpringApplication.run(SonarQubeCompanion.class, args);
    }

}
