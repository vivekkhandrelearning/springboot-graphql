package com.telstra.tni.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.telstra.tni.graphql", "com.telstra.tni.commonutils.neo4j"})
public class InventoryGraphQlApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryGraphQlApplication.class, args);
	}

}
