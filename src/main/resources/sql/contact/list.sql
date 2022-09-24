SELECT
	contact.age,
	contact.ageDivers,
	contact.ageFemale,
	contact.ageMale,
	contact.aboutMe,
	contact.attr,
	contact.attrEx,
	contact.attr0,
	contact.attr0Ex,
	contact.attr1,
	contact.attr1Ex,
	contact.attr2,
	contact.attr2Ex,
	contact.attr3,
	contact.attr3Ex,
	contact.attr4,
	contact.attr4Ex,
	contact.attr5,
	contact.attr5Ex,
	contact.attrInterest,
	contact.attrInterestEx,
	contact.birthday,
	contact.birthdayDisplay,
	contact.budget,
	contact.filter,
	contact.findMe,
	contact.gender,
	contact.guide,
	contact.id,
	contact.idDisplay,
	contact.introState,
	contact.image,
	contact.imageList,
	contact.language,
	contact.latitude,
	contact.longitude,
	contact.notificationBirthday,
	contact.notificationChat,
	contact.notificationFriendRequest,
	contact.notificationMarkEvent,
	contact.notificationVisitLocation,
	contact.notificationVisitProfile,
	contact.pseudonym,
	contact.rating,
	contact.search,
	contact.storage,
	contact.verified,
	contact.visitPage,
	contactLink.contactId,
	contactLink.contactId2,
	contactLink.id,
	contactLink.status,
	contactWhatToDo.keywords,
	contactWhatToDo.message,
	contactWhatToDo.time,
	'' as geolocationDistance
FROM
	Contact contact 
	left join ContactLink contactLink on
		contactLink.contactId={USERID} and contactLink.contactId2=contact.id 
	or 
		contactLink.contactId2={USERID} and contactLink.contactId=contact.id 
	left join ContactWhatToDo contactWhatToDo on
		contactWhatToDo.id = 
		(
				select
					max(cwtd2.id)
				from
					ContactWhatToDo cwtd2
				where
					cwtd2.contactId=contact.id and
					cwtd2.active=true and
					cwtd2.time>current_timestamp
		)
WHERE
	contact.verified=1 and
	{search}