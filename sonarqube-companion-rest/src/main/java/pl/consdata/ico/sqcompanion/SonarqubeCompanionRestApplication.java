package pl.consdata.ico.sqcompanion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
@EnableMongoRepositories
public class SonarqubeCompanionRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SonarqubeCompanionRestApplication.class, args);
    }

}
