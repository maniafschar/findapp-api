package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.jq.findapp.service.AuthenticationService;

@Controller
public class WebSocket {
	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	private Pattern numbers = Pattern.compile("\\d+");

	private static final Map<String, String> USERS = new Hashtable<>();

	@MessageMapping("video")
	public void video(VideoMessage message) throws Exception {
		authenticationService.verify(message.getUser(), message.getPassword(), message.getSalt());
		if (USERS.containsKey("" + message.getId())) {
			message.setPassword(null);
			message.setSalt(null);
			messagingTemplate.convertAndSendToUser("" + message.getId(), "/video", message);
		} else {
			final VideoMessage answer = new VideoMessage();
			answer.setAnswer(Collections.singletonMap("userState", "offline"));
			messagingTemplate.convertAndSendToUser("" + message.getUser(), "/video", answer);
		}
	}

	@PostMapping("refresh/{id}")
	public void refresh(@RequestBody final Object payload, @PathVariable final BigInteger id) throws Exception {
		messagingTemplate.convertAndSendToUser("" + id, "/refresh", payload);
	}

	@EventListener
	public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
		final Matcher matcher = numbers.matcher((String) event.getMessage().getHeaders().get("simpDestination"));
		if (matcher.find())
			USERS.put(matcher.group(), (String) event.getMessage().getHeaders().get("simpSessionId"));
	}

	@EventListener
	public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
		USERS.values().remove(event.getMessage().getHeaders().get("simpSessionId"));
	}

	private static class VideoMessage {
		BigInteger id;
		BigInteger user;
		Map<String, Object> answer;
		Map<String, Object> candidate;
		Map<String, Object> offer;
		String name;
		String password;
		String salt;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public BigInteger getId() {
			return id;
		}

		public void setId(BigInteger id) {
			this.id = id;
		}

		public BigInteger getUser() {
			return user;
		}

		public void setUser(BigInteger user) {
			this.user = user;
		}

		public Map<String, Object> getCandidate() {
			return candidate;
		}

		public void setCandidate(Map<String, Object> candidate) {
			this.candidate = candidate;
		}

		public Map<String, Object> getOffer() {
			return offer;
		}

		public void setOffer(Map<String, Object> offer) {
			this.offer = offer;
		}

		public Map<String, Object> getAnswer() {
			return answer;
		}

		public void setAnswer(Map<String, Object> answer) {
			this.answer = answer;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public String getSalt() {
			return salt;
		}

		public void setSalt(String salt) {
			this.salt = salt;
		}
	}
}