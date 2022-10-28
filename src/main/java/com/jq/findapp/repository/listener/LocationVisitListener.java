package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.LocationVisit;

@Component
public class LocationVisitListener extends AbstractRepositoryListener<LocationVisit> {
	@Override
	public void postPersist(final LocationVisit locationVisit) throws Exception {
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, locationVisit.getContactId()),
				locationVisit.getLocationId(), ContactNotificationTextType.contactVisitLocation, null);
	}
}