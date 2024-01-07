package com.jq.findapp;

import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import io.micrometer.core.instrument.MeterRegistry;

@SpringBootApplication
@EnableAsync
public class FindappApplication {

	public static void main(final String[] args) {
		final SpringApplicationBuilder app = new SpringApplicationBuilder(FindappApplication.class);
		app.application().addListeners(new ApplicationPidFileWriter(
				"./shutdown" + System.getProperties().getProperty("server.port") + ".pid"));
		app.run(args);
	}

	@Bean
	public MetricsEndpoint metricsEndpoint(final MeterRegistry registry) {
		return new MetricsEndpoint(registry);
	}
}