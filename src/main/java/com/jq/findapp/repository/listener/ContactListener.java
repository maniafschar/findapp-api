package com.jq.findapp.repository.listener;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.util.Text;

@Component
public class ContactListener extends AbstractRepositoryListener {
	private static AuthenticationService authenticationService;

	@Autowired
	private void setExternalService(AuthenticationService authenticationService) {
		ContactListener.authenticationService = authenticationService;
	}

	@PrePersist
	public void prePersist(final Contact contact) {
		contact.setPseudonym(sanitizePseudonym(contact.getPseudonym()));
	}

	@PreUpdate
	public void preUpdate(final Contact contact) throws Exception {
		if (contact.old("visitPage") != null)
			contact.setVisitPage(new Timestamp(System.currentTimeMillis()));
		if (contact.old("pushToken") != null)
			repository.executeUpdate(
					"update Contact contact set contact.pushToken=null, contact.pushSystem=null where contact.pushToken='"
							+ contact.old("pushToken") + "' and contact.id<>" + contact.getId());
		if (contact.old("fbToken") != null)
			repository.executeUpdate(
					"update Contact contact set contact.fbToken=null where contact.fbToken='"
							+ contact.old("fbToken")
							+ "' and contact.id<>" + contact.getId());
		if (contact.old("pseudonym") != null)
			contact.setPseudonym(sanitizePseudonym(contact.getPseudonym()));
		if (contact.getBirthday() == null)
			contact.setAge(null);
		else {
			final GregorianCalendar now = new GregorianCalendar();
			final GregorianCalendar birthday = new GregorianCalendar();
			birthday.setTimeInMillis(contact.getBirthday().getTime());
			short age = (short) (now.get(Calendar.YEAR) - birthday.get(Calendar.YEAR));
			if (now.get(Calendar.MONTH) < birthday.get(Calendar.MONTH) ||
					now.get(Calendar.MONTH) == birthday.get(Calendar.MONTH) &&
							now.get(Calendar.DAY_OF_MONTH) < birthday.get(Calendar.DAY_OF_MONTH))
				age--;
			contact.setAge(age);
		}
		if (contact.getVerified()) {
			final QueryParams params = new QueryParams(Query.contact_chat);
			params.setSearch(
					"chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId());
			if (repository.list(params).size() == 0) {
				final Chat chat = new Chat();
				chat.setContactId(adminId);
				chat.setContactId2(contact.getId());
				chat.setSeen(false);
				chat.setTextId(Text.mail_welcome.name());
				chat.setNote(Text.mail_welcome.getText(contact.getLanguage()).replace("<jq:EXTRA_1 />",
						contact.getPseudonym()));
				repository.save(chat);
			}
		}
	}

	@PostUpdate
	public void postUpdate(final Contact contact) throws Exception {
		if (contact.old("email") != null)
			authenticationService.recoverSendEmail(contact.getEmail());
	}

	private String sanitizePseudonym(String pseudonym) {
		pseudonym = pseudonym.trim().replaceAll("[^a-zA-ZÀ-ÿ0-9-_.+*#§$%&/\\\\ \\^']", "");
		int i = 0;
		while (pseudonym.length() < 9)
			pseudonym += (char) ('a' + i++);
		return pseudonym;
	}
}