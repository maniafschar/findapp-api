package com.jq.findapp.service.backend;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Storage;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.CronService.CronResult;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;

@Service
public class ImportSportsBarService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	private static final String URL = "https://skyfinder.sky.de/sf/skyfinder.servlet?";
	private static final String URL2 = "https://api.sportsbarfinder.net/map?rq=";

	public CronResult run() {
		final CronResult result = new CronResult();
		final Results results = new Results();
		try {
			final String zipCodePrefix = "" + (LocalDateTime.now().getDayOfYear() % 10);
			final JsonNode zip = Json
					.toNode(IOUtils.toString(getClass().getResourceAsStream("/json/zip.json"), StandardCharsets.UTF_8));
			for (int i = 0; i < zip.size(); i++) {
				final String s = zip.get(i).get("zip").asText();
				if (s.startsWith("" + zipCodePrefix)) {
					final Results r = zipCode(s);
					results.imported += r.imported;
					results.updated += r.updated;
					results.processed += r.processed;
					results.errors += r.errors;
					results.errorsScroll += r.errorsScroll;
					results.alreadyImported += r.alreadyImported;
					results.unchanged += r.unchanged;
				}
			}
			result.body = "prefix " + zipCodePrefix + "*" +
					"\nprocessed " + results.processed +
					"\nimported " + results.imported +
					"\nupdated " + results.updated +
					"\nunchanged " + results.unchanged +
					"\nalreadyImported " + results.alreadyImported +
					"\nerrors " + results.errors +
					"\nerrorsScroll " + results.errorsScroll;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	public CronResult runDazn() {
		final CronResult result = new CronResult();
		final double longitudeMax = 54.92, latitudeMin = 5.88, longitudeMin = 47.27, latitudeMax = 15.03,
				delta = 0.02;
		int count = 0;
		for (double longitude = longitudeMin; longitude < longitudeMax; longitude += delta) {
			for (double latitude = latitudeMin; latitude < latitudeMax; latitude += delta) {
				try {
					final String s = WebClient
							.create(URL2 + "%7B%22region%22%3A%7B%22zoomLevel%22%3A15%2C%22minLon%22%3A" + longitude
									+ "%2C%22minLat%22%3A" + latitude + "%7D%7D")
							.get().accept(MediaType.APPLICATION_JSON)
							.retrieve().bodyToMono(String.class).block();
					IOUtils.write(s, new FileOutputStream("dazn/" + longitude + "-" + latitude + ".json"),
							StandardCharsets.UTF_8);
					count++;
				} catch (Exception ex) {
					result.exception = ex;
				}
			}
		}
		result.body = count + " imports";
		return result;
	}

	Results zipCode(String zip) throws Exception {
		final Results result = new Results();
		final MultiValueMap<String, String> cookies = new LinkedMultiValueMap<>();
		JsonNode list = Json.toNode(WebClient
				.create(URL + "detailedSearch=Suchen&group=H&group=B&group=A&country=de&action=search&zip="
						+ zip)
				.get()
				.accept(MediaType.APPLICATION_JSON)
				.exchangeToMono(response -> {
					if (response.statusCode().is2xxSuccessful()) {
						response.cookies()
								.forEach((key, respCookies) -> cookies.add(key, respCookies.get(0).getValue()));
						return response.bodyToMono(String.class);
					}
					return response.createError();
				}).block());
		if (list.get("currentPageIndexEnd").intValue() > 0) {
			final QueryParams params = new QueryParams(Query.misc_listStorage);
			params.setSearch("storage.label='importSportBars'");
			final Map<String, Object> storage = repository.one(params);
			@SuppressWarnings("unchecked")
			final Set<String> imported = Json.toObject(storage.get("storage.storage").toString(), Set.class);
			for (int i = 0; i < list.get("numberOfPages").asInt(); i++) {
				if (i > 0) {
					try {
						list = Json.toNode(WebClient.create(URL + "action=scroll&page=" + (i + 1))
								.get()
								.cookies(cookieMap -> cookieMap.addAll(cookies))
								.retrieve()
								.toEntity(String.class).block().getBody());

					} catch (Exception ex) {
						result.errorsScroll++;
						notificationService.createTicket(TicketType.ERROR, "ImportSportsBarService.scroll",
								Strings.stackTraceToString(ex), null);
						continue;
					}
				}
				for (int i2 = 0; i2 < list.get("currentPageIndexEnd").intValue()
						- list.get("currentPageIndexStart").intValue() + 1; i2++) {
					final JsonNode data = list.get("currentData").get("" + i2);
					result.processed++;
					if (imported.contains(data.get("number").asText()))
						result.alreadyImported++;
					else {
						final String street = data.get("description").get("street").asText();
						Location location = new Location();
						location.setName(data.get("name").asText());
						if (street.contains(" ") && street.substring(street.lastIndexOf(' ')).trim().matches("\\d.*")) {
							location.setStreet(street.substring(0, street.lastIndexOf(' ')));
							location.setNumber(street.substring(street.lastIndexOf(' ')).trim());
						} else
							location.setStreet(street);
						location.setZipCode(zip);
						location.setTown(data.get("description").get("city").asText().replace(zip, "").trim());
						location.setAddress(
								location.getStreet() + " " + location.getNumber() + "\n" + location.getZipCode()
										+ " " + location.getTown() + "\nDeutschland");
						location.setCountry("DE");
						if (data.has("mapdata") && data.get("mapdata").has("latitude")) {
							location.setLatitude(data.get("mapdata").get("latitude").floatValue());
							location.setLongitude(data.get("mapdata").get("longitude").floatValue());
						}
						updateFields(location, data);
						location.setContactId(BigInteger.ONE);
						try {
							repository.save(location);
							imported.add(data.get("number").asText());
							result.imported++;
						} catch (IllegalArgumentException ex) {
							if (ex.getMessage().startsWith("location exists: ")) {
								location = repository.one(Location.class,
										new BigInteger(ex.getMessage().substring(17)));
								if (updateFields(location, data)) {
									repository.save(location);
									result.updated++;
								} else
									result.unchanged++;
								imported.add(data.get("number").asText());
							} else {
								result.errors++;
								if (!ex.getMessage().contains("OVER_QUERY_LIMIT"))
									notificationService.createTicket(TicketType.ERROR, "ImportSportsBar",
											Strings.stackTraceToString(ex), null);
							}
						} catch (Exception ex) {
							result.errors++;
							notificationService.createTicket(TicketType.ERROR, "ImportSportsBar",
									Strings.stackTraceToString(ex), null);
						}
					}
				}
			}
			final Storage s = repository.one(Storage.class, (BigInteger) storage.get("storage.id"));
			s.setStorage(Json.toString(imported));
			repository.save(s);
		}
		return result;
	}

	private boolean updateFields(final Location location, final JsonNode data) {
		if (!("|" + location.getSkills() + "|").contains("|x.1|"))
			location.setSkills(Strings.isEmpty(location.getSkills()) ? "x.1" : location.getSkills() + "|x.1");
		if (Strings.isEmpty(location.getDescription()) && data.get("description").has("program"))
			location.setDescription(data.get("description").get("program").asText());
		if (data.has("contactData")) {
			if (Strings.isEmpty(location.getTelephone()) && data.get("contactData").has("phoneNumber"))
				location.setTelephone((data.get("contactData").has("phoneAreaCode")
						? data.get("contactData").get("phoneAreaCode").asText() + "/"
						: "") + data.get("contactData").get("phoneNumber").asText());
			if (Strings.isEmpty(location.getUrl()) && data.get("contactData").has("homepageUrl"))
				location.setUrl(data.get("contactData").get("homepageUrl").asText());
			if (Strings.isEmpty(location.getEmail()) && data.get("contactData").has("mail"))
				location.setEmail(data.get("contactData").get("mail").asText());
		}
		return location.modified();
	}

	class Results {
		int processed = 0;
		int imported = 0;
		int updated = 0;
		int errors = 0;
		int errorsScroll = 0;
		int unchanged = 0;
		int alreadyImported = 0;
	}
}