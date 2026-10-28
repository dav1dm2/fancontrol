package com.example.fancontrol;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.flywaydb.core.Flyway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class FancontrolApplicationTests {

	@Autowired
	private Flyway flyway;

	@Test
	void contextLoads() {
	}

	@Test
	void flywayInitializesOrBaselinesTheDatabaseAtVersionOne() {
		var currentMigration = flyway.info().current();

		assertNotNull(currentMigration);
		assertEquals("1", currentMigration.getVersion().getVersion());
	}

}
