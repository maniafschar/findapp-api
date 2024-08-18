package com.jq.findapp.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.jq.findapp.repository.Repository.Attachment;

public class ClientTest {

	@Test
	public void modified() throws Exception {
		// given
		final Client client = create();
		final String emailOld = client.getEmail();
		client.setEmail(client.getEmail() + "i");

		// when
		final boolean modified = client.modified();

		// then
		assertTrue(modified);
		assertEquals(emailOld, client.old("email"));
		assertNull(client.old("storage"));
	}

	@Test
	public void modified_false() throws Exception {
		// given
		final Client client = create();
		client.setEmail(client.getEmail());

		// when
		final boolean modified = client.modified();

		// then
		assertFalse(modified);
		assertNull(client.old("email"));
		assertNull(client.old("storage"));
	}

	private Client create() {
		final Client client = new Client();
		client.setId(BigInteger.ONE);
		client.setEmail("abc@def.gh");
		client.setStorage(Attachment.SEPARATOR + "1");
		client.historize();
		return client;
	}
}