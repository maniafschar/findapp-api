SELECT
	ticket.createdAt,
	ticket.note,
	ticket.id,
	ticket.modifiedAt,
	ticket.subject,
	ticket.contactId,
	ticket.type
FROM
	Ticket ticket
WHERE
	{search}
ORDER BY
	ticket.createdAt DESC