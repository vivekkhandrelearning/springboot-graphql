package com.example.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.abcde.tni.graphql", "com.abcde.tni.commonutils.neo4j"})
public class DynamicGraphReportUiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DynamicGraphReportUiApplication.class, args);
	}

}
