package com.jq.findapp.service.push;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.service.NotificationService.Environment;
import com.jq.findapp.util.Strings;

import jakarta.ws.rs.NotFoundException;

@Component
public class Ios {

	@Autowired
	private JwtGenerator jwtGenerator;

	@Value("${push.apns.keyId}")
	private String keyId;

	@Value("${push.apns.teamId}")
	private String teamId;

	@Value("${push.apns.topic}")
	private String topic;

	@Value("${push.apns.url}")
	private String url;

	@Value("${push.apns.url.test}")
	private String urlTest;

	@Value("${app.admin.email}")
	private String adminEmail;

	private static final String template;

	static {
		try (final InputStream in = Ios.class.getResourceAsStream("/template/push.ios")) {
			template = IOUtils.toString(in, StandardCharsets.UTF_8);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Environment send(final String from, final Contact contactTo, final String text, final String action,
			final int badge, final String notificationId) throws Exception {
		try {
			send(from, contactTo, url, text, action, badge, notificationId);
			return Environment.Production;
		} catch (final NotFoundException ex) {
			if (contactTo.getEmail().contains(adminEmail.substring(adminEmail.indexOf("@")))) {
				send(from, contactTo, urlTest, text, action, badge, notificationId);
				return Environment.Development;
			}
			throw ex;
		}
	}

	private void send(final String from, final Contact contactTo, final String url, final String text,
			final String action, final int badge, final String notificationId) throws Exception {
		final HttpRequest request = HttpRequest.newBuilder()
				.POST(BodyPublishers.ofString(
						template.replace("{text}", text)
								.replace("{from}", from)
								.replace("{badge}", "" + badge)
								.replace("{notificationId}", notificationId)
								.replace("{exec}", Strings.isEmpty(action) ? "" : action)))
				.header("apns-push-type", "alert")
				.header("apns-topic",
						BigInteger.ONE.equals(contactTo.getClientId()) ? topic
								: "com.jq.fanclub.client" + contactTo.getClientId())
				.header("authorization", "bearer " + jwtGenerator.generateToken(getHeader(), getClaims(), "EC"))
				.header("Content-Type", "application/json")
				.uri(new URI(url + contactTo.getPushToken()))
				.build();
		final HttpResponse<String> response = HttpClient.newBuilder().version(Version.HTTP_2).build().send(request,
				BodyHandlers.ofString());
		if (response.statusCode() >= 300 || response.statusCode() < 200)
			throw new NotFoundException(
					"Failed to push to " + contactTo.getId() + ": " + text + "\n" + response.statusCode() + " "
							+ response.body());
	}

	private Map<String, String> getHeader() {
		final Map<String, String> map = new HashMap<>(2);
		map.put("alg", "ES256");
		map.put("kid", keyId);
		return map;
	}

	private Map<String, String> getClaims() throws Exception {
		final Map<String, String> map = new HashMap<>(2);
		map.put("iss", teamId);
		map.put("iat", "" + jwtGenerator.getLastGeneration(keyId, false));
		return map;
	}
}