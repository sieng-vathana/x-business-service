package com.x.business;

import com.x.business.config.TelegramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(TelegramProperties.class)
public class XBusinessServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(XBusinessServiceApplication.class, args);
	}

}
