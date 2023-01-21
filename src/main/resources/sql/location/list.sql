select
	location.address,
	location.budget,
	location.category,
	location.contactId,
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
	locationFavorite.modifiedAt,
	'' as geolocationDistance
FROM
	Location location left join LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID}
WHERE
	{search}
GROUP BY
	location.id,
	locationFavorite.id