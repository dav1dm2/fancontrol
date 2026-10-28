package com.example.fancontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableIntegration
@SpringBootApplication
public class FancontrolApplication {

	public static void main(String[] args) {
		SpringApplication.run(FancontrolApplication.class, args);
	}
}