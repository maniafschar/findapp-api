package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationRating;
import com.jq.findapp.service.NotificationService.NotificationID;

public class LocationRatingListener extends AbstractRepositoryListener {
	@PostPersist
	public void postPersist(LocationRating locationRating) throws Exception {
		repository.executeUpdate(
				"update Location location set rating=(select sum(rating)/count(*) from LocationRating where locationId=location.id) where location.id="
						+ locationRating.getLocationId());
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, locationRating.getContactId()),
				locationRating.getLocationId(), NotificationID.ratingLocMat,
				repository.one(Location.class, locationRating.getLocationId()).getName());
	}
}