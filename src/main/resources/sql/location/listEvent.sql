SELECT
	event.confirm,
	event.contactId,
	event.endDate,
	event.id,
	event.image,
	event.imageList,
	event.locationId,
	event.maxParticipants,
	event.price,
	event.skills,
	event.skillsText,
	event.startDate,
	event.text,
	event.type,
	event.visibility,
	contact.id,
	contact.image,
	contact.imageList,
	contact.pseudonym,
	contact.age,
	contact.aboutMe,
	contact.skills,
	contact.skillsText,
	contact.birthday,
	contact.birthdayDisplay,
	contact.gender,
	contact.latitude,
	contact.longitude,
	contactLink.contactId,
	contactLink.contactId2,
	contactLink.id,
	contactLink.status,
	location.address,
	location.category,
	location.description,
	location.id,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	location.town,
	locationFavorite.id,
	locationFavorite.favorite,
	'' as geolocationDistance
FROM
	Event event left join EventParticipate eventParticipate on eventParticipate.contactId={USERID} and eventParticipate.eventId=event.id left join
	Location location on event.locationId=location.id left join
	LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID},
	Contact contact left join
	ContactLink contactLink on
		contactLink.contactId={USERID} and contactLink.contactId2=contact.id
	or
		contactLink.contactId2={USERID} and contactLink.contactId=contact.id
WHERE
	event.contactId=contact.id and
	{search}
GROUP BY
	event.id