package com.dev.fastfood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling turns on Spring's @Scheduled annotation support —
// needed for OrderReconciliationScheduler (Milestone 3). Without it,
// @Scheduled methods are silently never called.
@EnableScheduling
@EnableCaching
@SpringBootApplication
public class 	FastfoodApplication {

	public static void main(String[] args) {
		SpringApplication.run(FastfoodApplication.class, args);
	}

}
