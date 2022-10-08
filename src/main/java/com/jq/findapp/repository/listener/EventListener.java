package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;

import javax.persistence.PostRemove;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.util.Strings;

public class EventListener extends AbstractRepositoryListener {
	@PostRemove
	public void postPersist(Event event) throws Exception {
		final QueryParams params = new QueryParams(Query.event_participate);
		params.setSearch("eventParticipate.eventId=" + event.getId());
		final Result result = repository.list(params);
		for (int i = 0; i < result.size(); i++) {
			final EventParticipate eventParticipate = repository.one(EventParticipate.class,
					(BigInteger) result.get(i).get("eventParticipate.id"));
			if (eventParticipate.getState() == 1 && !eventParticipate.getContactId().equals(event.getContactId())) {
				final LocalTime eventDate = event.getStartDate().toInstant().atZone(ZoneOffset.UTC).toLocalTime();
				final Instant time = eventParticipate.getEventDate().toInstant()
						.plus(Duration.ofSeconds((eventDate.getHour() * 60 + eventDate.getMinute()) * 60));
				if (time.isAfter(Instant.now())) {
					final Contact contactTo = repository.one(Contact.class, eventParticipate.getContactId());
					final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
					time.minus(Duration.ofMinutes(contactTo.getTimezoneOffset()));
					notificationService.sendNotification(contactFrom, contactTo,
							NotificationID.eventDelete,
							Strings.encodeParam("p=" + contactFrom.getId()),
							new SimpleDateFormat("dd.MM.yyyy").format(eventParticipate.getEventDate()) +
									new SimpleDateFormat(" HH:mm").format(new Date(time.toEpochMilli())),
							event.getText(),
							repository.one(Location.class, event.getLocationId()).getName());
				}
			}
		}
	}
}