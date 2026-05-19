package com.chunbaetour.domain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
})
@Testcontainers
class ChunbaeTourApplicationTests {

	@Container
	@ServiceConnection
	static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

	@Test
	void contextLoads() {
	}

}
