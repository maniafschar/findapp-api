package com.jq.findapp.service.backend;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.batik.ext.awt.RadialGradientPaint;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.ClientMarketing.ClientMarketingMode;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Strings;

@Service
public class SurveyService {
	private static final Map<BigInteger, Integer> clients = new HashMap<>();
	private static final int width = 600, height = 315, padding = 20;

	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Value("${app.sports.api.token}")
	private String token;

	@Value("${app.mail.host}")
	private String emailHost;

	@Value("${app.mail.port}")
	private int emailPort;

	@Value("${app.mail.password}")
	private String emailPassword;

	private static final AtomicLong lastRun = new AtomicLong(0);

	static {
		clients.put(BigInteger.valueOf(4), 157);
	}

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		clients.keySet().forEach(e -> {
			try {
				result.result = updateMatchdays(e, clients.get(e));
				final BigInteger id = updateLastMatch(e, clients.get(e));
				if (id != null)
					result.result += "\n" + "updateLastMatchId: " + id;
				updateResultAndNotify(e);
			} catch (final Exception ex) {
				result.exception = ex;
			}
		});
		return result;
	}

	public BigInteger testPoll() throws Exception {
		final int teamId = 0;
		lastRun.set(0);
		updateMatchdays(BigInteger.ONE, teamId);
		return updateLastMatch(BigInteger.ONE, teamId);
	}

	public String testResult(BigInteger clientMarketingId) throws Exception {
		final int max = (int) (10 + Math.random() * 100);
		for (int i = 0; i < max; i++) {
			final ContactMarketing contactMarketing = new ContactMarketing();
			contactMarketing.setClientMarketingId(clientMarketingId);
			contactMarketing.setContactId(BigInteger.ONE);
			contactMarketing.setFinished(Boolean.TRUE);
			contactMarketing.setStorage("{\"q1\":{\"a\":[" + (int) (Math.random() * 11) + "]}}");
			repository.save(contactMarketing);
		}
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, clientMarketingId);
		clientMarketing.setEndDate(new Timestamp(System.currentTimeMillis() - 1000));
		repository.save(clientMarketing);
		return updateResultAndNotify(clientMarketing.getClientId());
	}

	private String updateMatchdays(final BigInteger clientId, final int teamId)
			throws Exception {
		if (System.currentTimeMillis() - lastRun.get() < 24 * 60 * 60 * 1000)
			return "Matchdays already run in last 24 hours";
		lastRun.set(System.currentTimeMillis());
		int count = 0;
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setSearch("clientMarketing.startDate>'" + Instant.now() + "' and clientMarketing.clientId=" + clientId +
				" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
		if (repository.list(params).size() == 0) {
			JsonNode matchDays = get("https://v3.football.api-sports.io/fixtures?team=" + teamId + "&season="
					+ LocalDateTime.now().getYear());
			if (matchDays != null) {
				matchDays = matchDays.get("response");
				for (int i = 0; i < matchDays.size(); i++) {
					if ("NS".equals(matchDays.get(i).get("fixture").get("status").get("short").asText())) {
						final ClientMarketing clientMarketing = new ClientMarketing();
						clientMarketing.setStartDate(
								new Timestamp(matchDays.get(i).get("fixture").get("timestamp").asLong() * 1000
										+ (2 * 60 * 60 * 1000)));
						clientMarketing.setEndDate(
								new Timestamp(clientMarketing.getStartDate().getTime() + (24 * 60 * 60 * 1000)));
						clientMarketing.setClientId(clientId);
						clientMarketing.setStorage(matchDays.get(i).get("fixture").get("id").asText());
						clientMarketing.setMode(teamId == 0 ? ClientMarketingMode.Test
								: ClientMarketingMode.Live);
						repository.save(clientMarketing);
						count++;
					}
				}
			}
		}
		return "Matchdays update: " + count;
	}

	private BigInteger updateLastMatch(final BigInteger clientId, final int teamId) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setSearch(
				"clientMarketing.startDate<='" + Instant.now() + "' and clientMarketing.endDate>'" + Instant.now() +
						"' and clientMarketing.clientId=" + clientId +
						" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
		final Result list = repository.list(params);
		if (list.size() > 0) {
			JsonNode matchDay = get("https://v3.football.api-sports.io/fixtures?id="
					+ list.get(0).get("clientMarketing.storage"));
			if (matchDay != null) {
				matchDay = matchDay.get("response");
				JsonNode e = matchDay.findPath("players");
				e = e.get(e.get(0).get("team").get("id").asInt() == teamId ? 0 : 1).get("players");
				final ObjectNode poll = new ObjectMapper().createObjectNode();
				poll.put("prolog",
						"Umfrage <b>Spieler des Spiels</b> zum "
								+ matchDay.findPath("league").get("name").asText() +
								" Spiel<div style=\"padding:1em 0;font-weight:bold;\">"
								+ matchDay
										.findPath("teams").get("home").get("name").asText().replace("Munich", "München")
								+ " - "
								+ matchDay.findPath("teams").get("away").get("name").asText().replace("Munich",
										"München")
								+
								"</div>vom <b>"
								+ LocalDateTime.ofInstant(
										Instant.ofEpochMilli(
												matchDay.findPath("fixture").get("timestamp").asLong() * 1000),
										TimeZone.getTimeZone(Strings.TIME_OFFSET).toZoneId())
										.format(DateTimeFormatter.ofPattern("d.M.yyyy HH:mm"))
								+ "</b>. Möchtest Du teilnehmen?");
				poll.put("epilog",
						"Lieben Dank für die Teilnahme!\n\nÜbrigens, Bayern Fans treffen sich neuerdings zum gemeinsam Spiele anschauen und feiern in dieser coolen, neuen App.\n\nKlicke auf weiter und auch Du kannst mit ein paar Klicks dabei sein.");
				poll.put("home", matchDay.findPath("home").get("logo").asText());
				poll.put("away", matchDay.findPath("away").get("logo").asText());
				poll.put("league", matchDay.findPath("league").get("logo").asText());
				poll.put("timestamp", matchDay.findPath("fixture").get("timestamp").asLong());
				poll.put("venue", matchDay.findPath("fixture").get("venue").get("name").asText());
				poll.put("city", matchDay.findPath("fixture").get("venue").get("city").asText());
				poll.put("location",
						matchDay.findPath("teams").get("home").get("id").asInt() == teamId ? "home" : "away");
				final ObjectNode question = poll.putArray("questions").addObject();
				question.put("question", "Wer war für Dich Spieler des Spiels?");
				final ArrayNode answers = question.putArray("answers");
				for (int i = 0; i < e.size(); i++) {
					final JsonNode statistics = e.get(i).get("statistics").get(0);
					if (!statistics.get("games").get("minutes").isNull()) {
						String s = e.get(i).get("player").get("name").asText() +
								"<explain>" + statistics.get("games").get("minutes").asInt() +
								(statistics.get("games").get("minutes").asInt() > 1 ? " gespielte Minuten"
										: " gespielte Minute");
						if (!statistics.get("goals").get("total").isNull())
							s += getLine(statistics.get("goals").get("total").asInt(), " Tor", " Tore");
						if (!statistics.get("shots").get("total").isNull())
							s += getLine(statistics.get("shots").get("total").asInt(), " Torschuss, ", " Torschüsse, ")
									+ (statistics.get("shots").get("on").isNull() ? 0
											: statistics.get("shots").get("on").asInt())
									+ " aufs Tor";
						if (!statistics.get("goals").get("assists").isNull())
							s += getLine(statistics.get("goals").get("assists").asInt(), " Assist", " Assists");
						if (!statistics.get("passes").get("total").isNull())
							s += getLine(statistics.get("passes").get("total").asInt(), " Pass, ", " Pässe, ")
									+ statistics.get("passes").get("accuracy").asInt() + " angekommen";
						if (!statistics.get("duels").get("total").isNull())
							s += getLine(statistics.get("duels").get("total").asInt(), " Duell, ", " Duelle, ")
									+ statistics.get("duels").get("won").asInt() + " gewonnen";
						if (statistics.get("cards").get("yellow").asInt() > 0
								&& statistics.get("cards").get("red").asInt() > 0)
							s += "<br/>Gelberote Karte erhalten";
						else if (statistics.get("cards").get("yellow").asInt() > 0)
							s += "<br/>Gelbe Karte erhalten";
						if (statistics.get("cards").get("red").asInt() > 0)
							s += "<br/>Rote Karte erhalten";
						s += "</explain>";
						answers.addObject().put("answer", s);
					}
				}
				final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
						(BigInteger) list.get(0).get("clientMarketing.id"));
				clientMarketing.setStorage(new ObjectMapper().writeValueAsString(poll));
				clientMarketing.setImage(Attachment.createImage(".png",
						createImage(poll, repository.one(Client.class, clientId).getName(), null)));
				repository.save(clientMarketing);
				sendNotificationsPoll(clientMarketing);
				publish(clientId, "Umfrage Spieler des Spiels",
						"/rest/action/marketing/init/" + clientMarketing.getId());
			}
			return (BigInteger) list.get(0).get("clientMarketing.id");
		}
		return null;
	}

	private String getLine(final int x, final String singular, final String plural) {
		return "<br/>" + x + (x > 1 ? plural : singular);
	}

	private void publish(final BigInteger clientId, String message, String link) throws Exception {
		final Client client = repository.one(Client.class, clientId);
		if (!Strings.isEmpty(client.getFbPageAccessToken())) {
			final Map<String, String> body = new HashMap<>();
			body.put("message", message);
			body.put("link", Strings.removeSubdomain(client.getUrl()) + link);
			body.put("access_token", client.getFbPageAccessToken());
			final String response = WebClient
					.create("https://graph.facebook.com/v18.0/" + client.getFbPageId() + "/feed")
					.post().bodyValue(body).retrieve()
					.toEntity(String.class).block().getBody();
			if (response == null || !response.contains("\"id\":"))
				notificationService.createTicket(TicketType.ERROR, "FB", response, null);
		}
	}

	private String updateResultAndNotify(final BigInteger clientId) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
		params.setSearch("clientMarketingResult.published=false and clientMarketing.endDate<='" + Instant.now()
				+ "' and clientMarketing.clientId=" + clientId);
		final Result list = repository.list(params);
		String result = "";
		for (int i = 0; i < list.size(); i++) {
			final ClientMarketingResult clientMarketingResult = updateResult(
					(BigInteger) list.get(0).get("clientMarketing.id"));
			clientMarketingResult.setImage(Attachment.createImage(".png",
					createImage(
							new ObjectMapper().readTree(
									Attachment.resolve(repository
											.one(ClientMarketing.class, clientMarketingResult.getClientMarketingId())
											.getStorage())),
							repository.one(Client.class, clientId).getName(), clientMarketingResult)));
			clientMarketingResult.setPublished(true);
			repository.save(clientMarketingResult);
			sendNotificationsResult(
					repository.one(ClientMarketing.class, clientMarketingResult.getClientMarketingId()));
			publish(clientId, "Ergebnis der Umfrage Spieler des Spiels",
					"/rest/action/marketing/result/" + clientMarketingResult.getClientMarketingId());
			result += clientMarketingResult.getId() + "\n";
		}
		return result;
	}

	public synchronized ClientMarketingResult updateResult(final BigInteger clientMarketingId) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
		params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketingId);
		Result result = repository.list(params);
		final ClientMarketingResult clientMarketingResult;
		if (result.size() == 0) {
			clientMarketingResult = new ClientMarketingResult();
			clientMarketingResult.setClientMarketingId(clientMarketingId);
		} else
			clientMarketingResult = repository.one(ClientMarketingResult.class,
					(BigInteger) result.get(0).get("clientMarketingResult.id"));
		params.setQuery(Query.contact_listMarketing);
		params.setSearch("contactMarketing.clientMarketingId=" + clientMarketingId);
		result = repository.list(params);
		final ObjectMapper om = new ObjectMapper();
		final ObjectNode json = om.createObjectNode();
		json.put("participants", result.size());
		json.put("finished", 0);
		for (int i2 = 0; i2 < result.size(); i2++) {
			final String answers = (String) result.get(i2).get("contactMarketing.storage");
			if (answers != null && answers.length() > 2)
				om.readTree(answers).fields()
						.forEachRemaining(e -> {
							if ("finished".equals(e.getKey())) {
								if (e.getValue().asBoolean())
									json.put("finished", json.get("finished").asInt() + 1);
							} else {
								if (!json.has(e.getKey())) {
									json.set(e.getKey(), om.createObjectNode());
									((ObjectNode) json.get(e.getKey())).set("a", om.createArrayNode());
								}
								for (int i = 0; i < e.getValue().get("a").size(); i++) {
									final int index = e.getValue().get("a").get(i).asInt();
									final ArrayNode a = ((ArrayNode) json.get(e.getKey()).get("a"));
									for (int i3 = a.size(); i3 <= index; i3++)
										a.add(0);
									a.set(index, a.get(index).asInt() + 1);
								}
								if (e.getValue().has("t") && !Strings.isEmpty(e.getValue().get("t").asText())) {
									final ObjectNode o = (ObjectNode) json.get(e.getKey());
									if (!o.has("t"))
										o.put("t", "");
									o.put("t", o.get("t").asText() +
											"<div>" + e.getValue().get("t").asText() + "</div>");
								}
							}
						});
		}
		clientMarketingResult.setStorage(om.writeValueAsString(json));
		repository.save(clientMarketingResult);
		return clientMarketingResult;
	}

	private void sendNotificationsPoll(final ClientMarketing clientMarketing) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		String s = "contact.verified=true";
		if (!Strings.isEmpty(clientMarketing.getLanguage())) {
			String s2 = "";
			String[] langs = clientMarketing.getLanguage().split(Attachment.SEPARATOR);
			for (int i = 0; i < langs.length; i++)
				s2 += " or contact.language='" + langs[i] + "'";
			s += " and (" + s2.substring(4) + ")";
		}
		if (!Strings.isEmpty(clientMarketing.getGender())) {
			String s2 = "";
			String[] genders = clientMarketing.getGender().split(Attachment.SEPARATOR);
			for (int i = 0; i < genders.length; i++)
				s2 += " or contact.gender=" + genders[i];
			s += " and (" + s2.substring(4) + ")";
		}
		if (!Strings.isEmpty(clientMarketing.getAge()))
			s += " and (contact.age>=" + clientMarketing.getAge().split(",")[0] + " and contact.age<="
					+ clientMarketing.getAge().split(",")[1] + ")";
		params.setSearch(s);
		final Result users = repository.list(params);
		if (!Strings.isEmpty(clientMarketing.getRegion())) {
			for (int i = users.size() - 1; i >= 0; i--) {
				params.setQuery(Query.contact_listGeoLocationHistory);
				params.setSearch("contactGeoLocationHistory.contactId=" + users.get(i).get("contact.id"));
				final Result result = repository.list(params);
				if (result.size() > 0) {
					final GeoLocation geoLocation = repository.one(GeoLocation.class,
							(BigInteger) result.get(0).get("contactGeoLocationHistory.geoLocationId"));
					if ((Strings.isEmpty(geoLocation.getTown())
							&& Strings.isEmpty(geoLocation.getCountry())
							&& Strings.isEmpty(geoLocation.getZipCode()))
							|| (!clientMarketing.getRegion().contains(geoLocation.getTown())
									&& !clientMarketing.getRegion()
											.contains(" " + geoLocation.getCountry() + " ")))
						users.getList().remove(i);
					else {
						boolean remove = true;
						for (int i2 = geoLocation.getZipCode().length(); i2 > 1; i2--) {
							if (clientMarketing.getRegion().contains(
									geoLocation.getCountry() + "-" + geoLocation.getZipCode().substring(0, i))) {
								remove = false;
								break;
							}
						}
						if (remove)
							users.getList().remove(i);
					}
				}
			}
		}
		sendNotifications(users, clientMarketing, ContactNotificationTextType.clientMarketing, "contact.id");
	}

	private void sendNotifications(Result users, ClientMarketing clientMarketing, ContactNotificationTextType type,
			String field) throws Exception {
		if (clientMarketing.getMode() == ClientMarketingMode.Test)
			return;
		for (int i = 0; i < users.size(); i++)
			notificationService.sendNotification(null,
					repository.one(Contact.class, (BigInteger) users.get(i).get(field)),
					type, "m=" + clientMarketing.getId());
	}

	private void sendNotificationsResult(final ClientMarketing clientMarketing) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listMarketing);
		params.setSearch(
				"contactMarketing.finished=true and contactMarketing.contactId is not null and contactMarketing.clientMarketingId="
						+ clientMarketing.getId());
		sendNotifications(repository.list(params), clientMarketing, ContactNotificationTextType.clientMarketingResult,
				"contactMarketing.contactId");
	}

	protected JsonNode get(final String url) throws Exception {
		if (url.contains("?team=0&"))
			return new ObjectMapper().readTree(IOUtils
					.toString(getClass().getResourceAsStream("/surveyMatchdaysOne.json"),
							StandardCharsets.UTF_8)
					.replace("\"{date}\"", "" + (Instant.now().getEpochSecond() - 60 * 60 * 2 - 5)));
		if (url.endsWith("?id=0"))
			return new ObjectMapper().readTree(getClass().getResourceAsStream("/surveyLastMatch.json"));
		return WebClient
				.create(url)
				.get()
				.header("x-rapidapi-key", token)
				.header("x-rapidapi-host", "v3.football.api-sports.io")
				.retrieve()
				.toEntity(JsonNode.class).block().getBody();
	}

	private byte[] createImage(final JsonNode poll, final String name,
			final ClientMarketingResult clientMarketingResult) throws Exception {
		final String urlLeague = poll.get("league").asText();
		final String urlHome = poll.findPath("home").asText();
		final String urlAway = poll.findPath("away").asText();
		final boolean homeMatch = "home".equals(poll.findPath("location").asText());
		final BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = output.createGraphics();
		g2.setComposite(AlphaComposite.Src);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final RadialGradientPaint gradient = new RadialGradientPaint(width / 2 - 2 * padding, height / 2 - 2 * padding,
				height,
				new float[] { .3f, 1f },
				new Color[] { new Color(245, 239, 232), new Color(246, 194, 166) });
		g2.setPaint(gradient);
		g2.fill(new Rectangle2D.Float(0, 0, width, height));
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clientMarketingResult == null ? 1 : 0.1f));
		final int h = (int) (height * 0.4);
		if (!homeMatch)
			drawImage(urlHome, g2, width / 2, padding, h, -1);
		drawImage(urlAway, g2, width / 2, padding, h, 1);
		if (homeMatch)
			drawImage(urlHome, g2, width / 2, padding, h, -1);
		drawImage(urlLeague, g2, width - padding, padding, height / 4, 0);
		final Font customFont = Font
				.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/Comfortaa-Regular.ttf"))
				.deriveFont(50f);
		GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
		g2.setFont(customFont);
		g2.setColor(Color.BLACK);
		String s = "Umfrage";
		if (clientMarketingResult != null)
			s += "ergebnis";
		g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 14.5f);
		g2.setFont(customFont.deriveFont(24f));
		s = "Spieler des Spiels vom " +
				Instant.ofEpochMilli(poll.get("timestamp").asLong() * 1000)
						.atZone(TimeZone.getTimeZone(Strings.TIME_OFFSET).toZoneId()).toLocalDateTime()
						.format(DateTimeFormatter.ofPattern("d.M.yyyy H:mm"));
		g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 17.5f);
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP));
		g2.setFont(customFont.deriveFont(12f));
		s = "© " + LocalDateTime.now().getYear() + " " + name;
		g2.drawString(s, width - g2.getFontMetrics().stringWidth(s) - padding, height - padding);
		if (clientMarketingResult != null)
			createImageResult(g2, customFont, poll,
					new ObjectMapper().readTree(Attachment.resolve(clientMarketingResult.getStorage())));
		// final BufferedImageTranscoder imageTranscoder = new
		// BufferedImageTranscoder();
		// imageTranscoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width);
		// imageTranscoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height);
		// final TranscoderInput input = new TranscoderInput(svgFile);
		// imageTranscoder.transcode(input, null);
		// g2.drawImage(imageTranscoder.getBufferedImage(), 770 - image.getWidth(), 470
		// - image.getHeight(), null);
		g2.dispose();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(output, "png", out);
		return out.toByteArray();
	}

	private void createImageResult(final Graphics2D g2, final Font customFont, JsonNode poll, JsonNode result)
			throws Exception {
		g2.setFont(customFont.deriveFont(16f));
		final int h = g2.getFontMetrics().getHeight();
		final int total = result.get("participants").asInt();
		int y = padding;
		poll = poll.get("questions").get(0).get("answers");
		result = result.get("q1").get("a");
		final List<String> x = new ArrayList<>();
		String leftPad = "000000000000000";
		for (int i = 0; i < result.size(); i++)
			x.add(leftPad.substring(result.get(i).asText().length()) + result.get(i).asText() + "_"
					+ poll.get(i).get("answer").asText());
		Collections.sort(x);
		for (int i = x.size() - 1; i >= 0 && i > x.size() - 7; i--) {
			String[] s = x.get(i).split("_");
			if (s[1].indexOf("<explain") > 0)
				s[1] = s[1].substring(0, s[1].indexOf("<explain")).trim();
			int percent = (int) (100.0 * Integer.parseInt(s[0]) / total + 0.5);
			if (percent < 1)
				break;
			g2.setColor(new Color(255, 100, 0, 50));
			g2.fillRoundRect(padding, y, width - 2 * padding, h * 2, 10, 10);
			g2.setColor(new Color(255, 100, 0, 120));
			g2.fillRoundRect(padding, y, (width - 2 * padding) * percent / 100, h * 2, 10, 10);
			g2.setColor(new Color(0, 0, 0));
			g2.drawString(percent + "%", padding * 1.8f, y + h + 5);
			g2.drawString(s[1], padding * 4.5f, y + h + 5);
			y += 2.3 * padding;
		}
	}

	private void drawImage(final String url, final Graphics2D g, final int x, final int y, final int height,
			final int pos) throws Exception {
		final BufferedImage image = ImageIO.read(new URL(url).openStream());
		final int paddingLogos = -10;
		final int w = image.getWidth() / image.getHeight() * height;
		g.drawImage(image, x - (pos == 1 ? 0 : w) + pos * paddingLogos, y,
				x + (pos == 1 ? w : 0) + pos * paddingLogos,
				height + y, 0, 0, image.getWidth(), image.getHeight(), null);
	}
}