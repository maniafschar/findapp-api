SELECT
	client.id,
	client.storage,
	client.name,
	client.email,
	client.url
FROM
	Client client
WHERE
	{search}