package com.olivamore.api;

import org.springframework.boot.SpringApplication;

public class TestOliveApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(OliveApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
