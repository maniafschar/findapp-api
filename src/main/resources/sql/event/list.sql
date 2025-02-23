SELECT
	event.contactId,
	event.endDate,
	event.id,
	event.image,
	event.imageList,
	event.locationId,
	event.latitude,
	event.longitude,
	event.maxParticipants,
	event.price,
	event.publish,
	event.rating,
	event.skills,
	event.skillsText,
	event.startDate,
	event.type,
	event.description,
	event.repetition,
	event.url,
	contact.id,
	contact.authenticate,
	contact.image,
	contact.imageList,
	contact.pseudonym,
	contact.age,
	contact.description,
	contact.rating,
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
	location.description,
	location.id,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.name,
	location.rating,
	location.town,
	locationFavorite.id,
	locationFavorite.favorite,
	'' as geolocationDistance
FROM
	Event event left join 
	Location location on event.locationId=location.id left join
	LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID},
	Contact contact left join
	ContactLink contactLink on
		contactLink.contactId={USERID} and contactLink.contactId2=contact.id
	or
		contactLink.contactId2={USERID} and contactLink.contactId=contact.id
WHERE
	event.contactId=contact.id and
	contact.clientId={CLIENTID} and
	{search}