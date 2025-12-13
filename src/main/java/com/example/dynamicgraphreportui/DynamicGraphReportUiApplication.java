package com.example.dynamicgraphreportui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example", "com.telstra.tni.commonutils.neo4j"})
public class DynamicGraphReportUiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DynamicGraphReportUiApplication.class, args);
	}

}
