package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationFavorite;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.service.ExternalService;

@Component
public class LocationListener extends AbstractRepositoryListener<Location> {
	@Autowired
	private ExternalService externalService;

	@Override
	public void prePersist(final Location location)
			throws JsonMappingException, JsonProcessingException, IllegalArgumentException {
		lookupAddress(location);
		final QueryParams params = new QueryParams(Query.location_list);
		location.getCategory();
		location.getName();
		params.setUser(repository.one(Contact.class, location.getContactId()));
		params.setSearch(
				"location.zipCode='" + location.getZipCode() + "' and LOWER(location.street)='"
						+ (location.getStreet() == null ? ""
								: location.getStreet().toLowerCase().replace("traße", "tr.").replaceAll("'", "\\'"))
						+ "'");
		final Result list = repository.list(params);
		for (int i = 0; i < list.size(); i++) {
			if (isNameMatch((String) list.get(i).get("location.name"), location.getName(), true)) {
				String category = (String) list.get(i).get("location.category");
				for (int i2 = 0; i2 < location.getCategory().length(); i2++) {
					if (category.contains(location.getCategory().substring(i2, i2 + 1)))
						throw new IllegalArgumentException("location exists");
				}
			}
		}
	}

	@Override
	public void preUpdate(final Location location) throws Exception {
		if (location.old("address") != null)
			lookupAddress(location);
	}

	@Override
	public void postPersist(final Location location) throws Exception {
		final LocationFavorite locationFavorite = new LocationFavorite();
		locationFavorite.setContactId(location.getContactId());
		locationFavorite.setLocationId(location.getId());
		locationFavorite.setFavorite(true);
		repository.save(locationFavorite);
	}

	private void lookupAddress(Location location)
			throws JsonMappingException, JsonProcessingException, IllegalArgumentException {
		final JsonNode address = new ObjectMapper().readTree(
				externalService.google("geocode/json?address="
						+ location.getAddress().replaceAll("\n", ", "), location.getContactId()));
		if (!"OK".equals(address.get("status").asText())) {
			throw new IllegalArgumentException(
					"invalid address:\n" + location.getName() + "\n" + location.getAddress() + "\n"
							+ new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(address));
		}
		final JsonNode result = address.get("results").get(0);
		JsonNode n = result.get("geometry").get("location");
		final GeoLocation geoLocation = externalService.convertAddress(address);
		location.setAddress(geoLocation.getFormatted());
		location.setCountry(geoLocation.getCountry());
		location.setTown(geoLocation.getTown());
		location.setZipCode(geoLocation.getZipCode());
		location.setStreet(geoLocation.getStreet());
		location.setNumber(geoLocation.getNumber());
		if (geoLocation.getStreet() != null && geoLocation.getStreet().trim().length() > 0) {
			if (location.getStreet().contains("traße")) {
				final String street = location.getStreet().replace("traße", "tr.");
				location.setAddress(location.getAddress().replace(location.getStreet(), street));
				location.setStreet(street);
			}
			if (geoLocation.getLatitude() != null && geoLocation.getLongitude() != null) {
				location.setLatitude(geoLocation.getLatitude());
				location.setLongitude(geoLocation.getLongitude());
			}
		}
		n = result.get("address_components");
		String s = "";
		for (int i = 0; i < n.size(); i++) {
			if (!location.getAddress().contains(n.get(i).get("long_name").asText()))
				s += '\n' + n.get(i).get("long_name").asText();
		}
		location.setAddress2(s.trim());
	}

	private boolean isNameMatch(String name1, String name2, boolean tryReverse) {
		name1 = name1.trim().toLowerCase();
		name2 = name2.trim().toLowerCase();
		while (name1.contains("  "))
			name1 = name1.replaceAll("  ", " ");
		final String[] n = name1.split(" ");
		int count = 0;
		for (int i = 0; i < n.length; i++) {
			if (name2.contains(n[i]))
				count++;
		}
		if (count == n.length || n.length > 3 && count > n.length - 2)
			return true;
		if (tryReverse)
			return isNameMatch(name2, name1, false);
		return false;
	}
}