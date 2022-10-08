package com.jq.findapp.service;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Ticket.Type;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Strings;

@Service
public class ExternalService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Value("${app.google.key}")
	private String googleKey;

	public String google(String param) {
		final String result = WebClient
				.create("https://maps.googleapis.com/maps/api/" + param + (param.contains("?") ? "&" : "?")
						+ "key=" + googleKey)
				.get().retrieve().toEntity(String.class).block().getBody();
		try {
			final ObjectMapper om = new ObjectMapper();
			notificationService.createTicket(Type.GOOGLE, param,
					om.writerWithDefaultPrettyPrinter().writeValueAsString(om.readTree(result)));
		} catch (Exception e) {
			notificationService.createTicket(Type.GOOGLE, param, result);
		}
		return result;
	}

	public GeoLocation convertGoogleAddress(JsonNode google) {
		if ("OK".equals(google.get("status").asText()) && google.get("results") != null) {
			JsonNode data = google.get("results").get(0).get("address_components");
			final GeoLocation geoLocation = new GeoLocation();
			for (int i = 0; i < data.size(); i++) {
				if (geoLocation.getStreet() == null && "route".equals(data.get(i).get("types").get(0).asText()))
					geoLocation.setStreet(data.get(i).get("long_name").asText());
				else if (geoLocation.getNumber() == null
						&& "street_number".equals(data.get(i).get("types").get(0).asText()))
					geoLocation.setNumber(data.get(i).get("long_name").asText());
				else if (geoLocation.getTown() == null &&
						("locality".equals(data.get(i).get("types").get(0).asText()) ||
								data.get(i).get("types").get(0).asText().startsWith("administrative_area_level_")))
					geoLocation.setTown(data.get(i).get("long_name").asText());
				else if (geoLocation.getZipCode() == null
						&& "postal_code".equals(data.get(i).get("types").get(0).asText()))
					geoLocation.setZipCode(data.get(i).get("long_name").asText());
				else if (geoLocation.getCountry() == null && "country".equals(data.get(i).get("types").get(0).asText()))
					geoLocation.setCountry(data.get(i).get("short_name").asText());
			}
			data = google.get("results").get(0).get("geometry");
			if (data != null) {
				data = data.get("location");
				if (data != null) {
					geoLocation.setLatitude((float) data.get("lat").asDouble());
					geoLocation.setLongitude((float) data.get("lng").asDouble());
				}
			}
			return geoLocation;
		}
		return null;
	}

	public GeoLocation googleAddress(float latitude, float longitude) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_geoLocation);
		final float roundingFactor = 10000f;
		params.setSearch("geoLocation.latitude like '" + (Math.round(latitude * roundingFactor) / roundingFactor)
				+ "%' and geoLocation.longitude like '" + (Math.round(longitude * roundingFactor) / roundingFactor)
				+ "%'");
		final Map<String, Object> persistedAddress = repository.one(params);
		if (persistedAddress != null)
			return repository.one(GeoLocation.class, (BigInteger) persistedAddress.get("_id"));
		final GeoLocation geoLocation = convertGoogleAddress(
				new ObjectMapper().readTree(google("geocode/json?latlng=" + latitude + ',' + longitude)));
		if (geoLocation != null) {
			geoLocation.setLongitude(longitude);
			geoLocation.setLatitude(latitude);
			repository.save(geoLocation);
		} else
			notificationService.createTicket(Type.ERROR, "No google address", latitude + "\n" + longitude);
		return geoLocation;
	}

	public String map(String source, String destination, Contact contact) {
		String url;
		if (source == null || source.length() == 0)
			url = "https://maps.googleapis.com/maps/api/staticmap?{destination}&markers=icon:" + Strings.URL_APP
					+ "/images/mapMe.png|shadow:false|{destination}&scale=2&size=200x200&maptype=roadmap&key=";
		else {
			url = "https://maps.googleapis.com/maps/api/staticmap?{source}|{destination}&markers=icon:"
					+ Strings.URL_APP
					+ "/images/mapMe.png|shadow:false|{source}&markers=icon:" + Strings.URL_APP
					+ "/images/mapLoc.png|shadow:false|{destination}&scale=2&size=600x200&maptype=roadmap&sensor=true&key=";
			url = url.replaceAll("\\{source}", source);
		}
		url = url.replaceAll("\\{destination}", destination);
		url = url.replaceAll("\\{gender}", contact.getGender() == null ? "2" : "" + contact.getGender()) + googleKey;
		return Base64.getEncoder().encodeToString(
				WebClient.create(url).get().retrieve().toEntity(byte[].class).block().getBody());
	}
}