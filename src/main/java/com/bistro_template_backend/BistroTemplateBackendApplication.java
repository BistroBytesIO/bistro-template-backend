package com.bistro_template_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BistroTemplateBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BistroTemplateBackendApplication.class, args);
	}

}
