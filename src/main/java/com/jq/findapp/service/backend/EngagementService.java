package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.OS;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Setting;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Score;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@Service
public class EngagementService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private ExternalService externalService;

	private static String currentVersion;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	private static class ChatTemplate {
		private final Text textId;
		private final String action;
		private final Condition condition;

		interface Condition {
			boolean isTrue(Contact contact);
		}

		private ChatTemplate(Text textId, String action, Condition condition) {
			this.textId = textId;
			this.condition = condition;
			this.action = action;
		}

		boolean eligible(Contact contact) {
			return condition == null ? true : condition.isTrue(contact);
		}
	}

	private List<ChatTemplate> chatTemplates = new ArrayList<>();
	private final QueryParams paramsAdminBlocked = new QueryParams(Query.contact_block);

	enum REPLACMENT {
		EMOJI_DANCING((contact, location, externalService,
				repository) -> contact.getGender() != null && contact.getGender() == 1 ? "🕺🏻" : "💃"),
		EMOJI_WAVING(
				(contact, location, externalService,
						repository) -> contact.getGender() != null && contact.getGender() == 1 ? "🙋🏻‍♂️" : "🙋‍♀️"),
		CONTACT_MODIFIED_AT((contact, location, externalService, repository) -> new SimpleDateFormat("d.M. H:mm")
				.format(contact.getModifiedAt())),
		CONTACT_PSEUDONYM((contact, location, externalService, repository) -> contact.getPseudonym()),
		CONTACT_CURRENT_TOWN((contact, location, externalService, repository) -> {
			try {
				return externalService.getAddress(contact.getLatitude(), contact.getLongitude(), null).getTown();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}),
		CONTACT_VERSION((contact, location, externalService, repository) -> contact.getVersion()),
		LOCATION_NAME((contact, location, externalService, repository) -> location.getName()),
		NEW_CONTACTS_COUNT((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"contact.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
			return "" + repository.list(params).size();
		}),
		NEW_CONTACTS_DISTANCE((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"contact.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
			final Result result = repository.list(params);
			for (int i = result.size() - 1; i >= 0; i--) {
				if (result.get(i).get("_geolocationDistance") instanceof Number)
					return "" + (int) (((Number) result.get(i).get("_geolocationDistance")).doubleValue() + 0.5);
			}
			return "11";
		}),
		NEW_LOCATIONS_COUNT((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.location_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"location.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
			return "" + repository.list(params).size();
		}),
		NEW_LOCATIONS_DISTANCE((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.location_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"location.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
			final Result result = repository.list(params);
			return ""
					+ (int) (((Number) result.get(result.size() - 1).get("_geolocationDistance")).doubleValue() + 0.5);
		}),
		VERSION((contact, location, externalService, repository) -> currentVersion);

		private static interface Exec {
			String replace(Contact contact, Location location, ExternalService externalService, Repository repository);
		}

		private final Exec exec;

		private REPLACMENT(Exec exec) {
			this.exec = exec;
		}

		String replace(String s, Contact contact, Location location, ExternalService externalService,
				Repository repository) {
			return s.contains(name())
					? s.replaceAll(name(), exec.replace(contact, location, externalService, repository))
					: s;
		}

	}

	public EngagementService() {
		chatTemplates.add(new ChatTemplate(Text.engagement_uploadProfileImage,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> contact.getImage() == null));

		chatTemplates.add(new ChatTemplate(Text.engagement_uploadProfileAttributes,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> (Strings.isEmpty(contact.getAttr0()) && Strings.isEmpty(contact.getAttr1())
						&& Strings.isEmpty(contact.getAttr2()) && Strings.isEmpty(contact.getAttr3())
						&& Strings.isEmpty(contact.getAttr4()) && Strings.isEmpty(contact.getAttr5())
						&& Strings.isEmpty(contact.getAttr()))
						|| (Strings.isEmpty(contact.getAgeMale()) && Strings.isEmpty(contact.getAgeFemale())
								&& Strings.isEmpty(contact.getAgeDivers()))
						|| contact.getGender() == null || contact.getBirthday() == null));

		chatTemplates.add(new ChatTemplate(Text.engagement_newTown,
				"pageInfo.socialShare()",
				contact -> contact.getLatitude() != null
						&& contact.getModifiedAt() != null
						&& contact.getModifiedAt()
								.after(new Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli()))));

		chatTemplates.add(new ChatTemplate(Text.engagement_allowLocation,
				"",
				contact -> contact.getLongitude() == null && contact.getOs() != OS.web));

		chatTemplates.add(new ChatTemplate(Text.engagement_becomeGuide,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> contact.getGuide() == null || !contact.getGuide()));

		chatTemplates.add(new ChatTemplate(Text.engagement_guide,
				"pageInfo.socialShare()",
				contact -> contact.getGuide() != null && contact.getGuide()));

		chatTemplates.add(new ChatTemplate(Text.engagement_addEvent,
				"",
				contact -> {
					final QueryParams params = new QueryParams(Query.location_listEvent);
					params.setUser(contact);
					params.setSearch("event.contactId=" + contact.getId());
					if (repository.list(params).size() == 0) {
						params.setQuery(Query.location_listFavorite);
						params.setSearch("locationFavorite.contactId=" + contact.getId());
						return repository.list(params).size() > 0;
					}
					return false;
				}));

		chatTemplates.add(new ChatTemplate(Text.engagement_newLocations,
				"",
				contact -> {
					if (contact.getLongitude() == null)
						return false;
					final QueryParams params = new QueryParams(Query.location_list);
					params.setUser(contact);
					params.setLatitude(contact.getLatitude());
					params.setLongitude(contact.getLongitude());
					params.setSearch(
							"location.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
					return repository.list(params).size() > 1;
				}));

		chatTemplates.add(new ChatTemplate(Text.engagement_newContacts,
				"",
				contact -> {
					if (contact.getLongitude() == null)
						return false;
					final QueryParams params = new QueryParams(Query.contact_list);
					params.setUser(contact);
					params.setLatitude(contact.getLatitude());
					params.setLongitude(contact.getLongitude());
					params.setDistance(50);
					params.setSearch(
							"contact.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
					return repository.list(params).size() > 9;
				}));

		chatTemplates.add(new ChatTemplate(Text.engagement_bluetoothMatch,
				"pageInfo.socialShare()",
				contact -> {
					final QueryParams params = new QueryParams(Query.contact_notification);
					params.setUser(contact);
					params.setSearch(
							"contactNotification.textType='FindMe' and contactNotification.contactId="
									+ contact.getId());
					return repository.list(params).size() > 2;
				}));

		chatTemplates.add(new ChatTemplate(Text.engagement_bluetoothNoMatch,
				"pageInfo.socialShare()",
				contact -> {
					final QueryParams params = new QueryParams(Query.contact_notification);
					params.setUser(contact);
					params.setSearch(
							"contactNotification.textType='FindMe' and contactNotification.contactId="
									+ contact.getId());
					return repository.list(params).size() == 0;
				}));

		chatTemplates.add(new ChatTemplate(Text.engagement_addFriends,
				"pageInfo.socialShare()",
				null));

		chatTemplates.add(new ChatTemplate(Text.engagement_installCurrentVersion,
				"global.openStore()",
				contact -> !Strings.isEmpty(contact.getVersion()) && contact.getOs() != OS.web
						&& currentVersion.compareTo(contact.getVersion()) > 0));

		chatTemplates.add(new ChatTemplate(Text.engagement_praise,
				"",
				contact -> contact.getLongitude() != null && contact.getImage() != null
						&& contact.getBirthday() != null && !Strings.isEmpty(contact.getAboutMe())
						&& !Strings.isEmpty(contact.getAttr()) && !Strings.isEmpty(contact.getAttrInterest())
						&& (!Strings.isEmpty(contact.getAgeDivers()) || !Strings.isEmpty(contact.getAgeFemale())
								|| !Strings.isEmpty(contact.getAgeMale()))
						&& (!Strings.isEmpty(contact.getAttr0()) || !Strings.isEmpty(contact.getAttr1())
								|| !Strings.isEmpty(contact.getAttr2()) || !Strings.isEmpty(contact.getAttr3())
								|| !Strings.isEmpty(contact.getAttr4()) || !Strings.isEmpty(contact.getAttr5()))));

		chatTemplates.add(new ChatTemplate(Text.engagement_patience,
				"pageInfo.socialShare()",
				contact -> contact.getLongitude() != null));

		chatTemplates.add(new ChatTemplate(Text.engagement_like, null, null));
	}

	public String[] sendRegistrationReminder() {
		final String[] result = new String[] { getClass().getSimpleName() + "/sendRegistrationReminder", null };
		try {
			final GregorianCalendar gc = new GregorianCalendar();
			if (gc.get(Calendar.HOUR_OF_DAY) < 9 || gc.get(Calendar.HOUR_OF_DAY) > 18)
				return result;
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch("contact.createdAt>'" + Instant.now().minus(Duration.ofHours(3))
					+ "' and contact.verified=false and contact.notificationEngagement=true");
			params.setLimit(0);
			final Result list = repository.list(params);
			final long DAY = 86400000;
			String value = "", failedEmails = "";
			params.setQuery(Query.misc_setting);
			for (int i = 0; i < list.size(); i++) {
				final Contact to = repository.one(Contact.class, (BigInteger) list.get(i).get("contact.id"));
				params.setSearch("setting.label='registration-reminder' and concat('|', setting.value, '|') like '%|"
						+ to.getId() + "|%'");
				final Result result2 = repository.list(params);
				if (result2.size() == 0
						|| ((Timestamp) result2.get(0).get("setting.createdAt")).getTime() + 7 * DAY < System
								.currentTimeMillis()) {
					try {
						authenticationService.recoverSendEmailReminder(to);
						value += "|" + to.getId();
						Thread.sleep(10000);
					} catch (MailSendException ex) {
						failedEmails += "\n" + to.getEmail() + "\n" + Strings.stackTraceToString(ex);
					}
				}
			}
			if (value.length() > 0) {
				final Setting s = new Setting();
				s.setLabel("registration-reminder");
				s.setValue(value.substring(1));
				repository.save(s);
			}
			if (failedEmails.length() > 0)
				notificationService.createTicket(TicketType.ERROR, "sendRegistrationReminder",
						"Failed Emails:" + failedEmails, null);
		} catch (Exception e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}

	public String[] sendChats() {
		final String[] result = new String[] { getClass().getSimpleName() + "/sendChats", null };
		try {
			resetChatInstallCurrentVersion();
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setLimit(0);
			params.setSearch("contact.id<>" + adminId
					+ " and contact.verified=true and contact.version is not null");
			final Result ids = repository.list(params);
			params.setQuery(Query.contact_chat);
			for (int i = 0; i < ids.size(); i++) {
				params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + ids.get(i).get("contact.id"));
				if (repository.list(params).size() == 0)
					sendChat(Text.engagement_welcome,
							repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id")), null, null);
			}
			for (int i = 0; i < ids.size(); i++) {
				final Contact contact = repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id"));
				if (isTimeForNewChat(contact, params, false))
					sendChatTemplate(contact);
			}
		} catch (Exception e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}

	private boolean isTimeForNewChat(Contact contact, final QueryParams params, boolean nearBy) {
		final int hour = Instant.now().atZone(TimeZone.getTimeZone(contact.getTimezone()).toZoneId()).getHour();
		if (hour > 6 && hour < 22) {
			paramsAdminBlocked.setSearch("block.contactId=" + adminId + " and block.contactId2="
					+ contact.getId()
					+ " or block.contactId=" + contact.getId() + " and block.contactId2=" + adminId);
			if (repository.list(paramsAdminBlocked).size() == 0) {
				params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId()
						+ " and chat.createdAt>'"
						+ Instant.now().minus(Duration.ofDays(3 + (int) (Math.random() * 3)))
								.minus(Duration.ofHours((int) (Math.random() * 12))).toString()
						+ '\'');
				if (repository.list(params).size() == 0) {
					params.setSearch(
							"chat.textId is not null and chat.contactId=" + adminId + " and chat.contactId2="
									+ contact.getId());
					final Result lastChats = repository.list(params);
					if (lastChats.size() == 0)
						return true;
					final boolean isLastChatNearBy = ((Text) lastChats.get(0).get("chat.textId")).name().startsWith(
							Text.engagement_nearByLocation.name().substring(0,
									Text.engagement_nearByLocation.name().indexOf('L')));
					if (!nearBy && isLastChatNearBy || nearBy && !isLastChatNearBy)
						return true;
					if (((Timestamp) lastChats.get(0).get("chat.createdAt"))
							.before(new Timestamp(Instant.now().minus(Duration.ofDays(7)).toEpochMilli())))
						return true;
				}
			}
		}
		return false;
	}

	private void resetChatInstallCurrentVersion() throws Exception {
		if (currentVersion == null) {
			final QueryParams params = new QueryParams(Query.contact_maxAppVersion);
			params.setUser(repository.one(Contact.class, adminId));
			currentVersion = (String) repository.one(params).get("_c");
		}
		final QueryParams params = new QueryParams(Query.contact_listChatFlat);
		params.setLimit(0);
		params.setSearch("chat.textId='" + Text.engagement_installCurrentVersion.name() +
				"' and contact.version='" + currentVersion + "'");
		final Result ids = repository.list(params);
		for (int i = 0; i < ids.size(); i++) {
			final Chat chat = repository.one(Chat.class, (BigInteger) ids.get(i).get("chat.id"));
			chat.setTextId(null);
			repository.save(chat);
		}
	}

	public String[] sendNearBy() {
		final String[] result = new String[] { getClass().getSimpleName() + "/sendNearBy", null };
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch(
					"contact.id<>" + adminId + " and contact.verified=true and contact.notificationEngagement=true and "
							+ "contact.version is not null and contact.longitude is not null and ("
							+ "length(contact.attrInterest)>0 or length(contact.attrInterestEx)>0 or "
							+ "length(contact.attr0)>0 or length(contact.attr0Ex)>0 or "
							+ "length(contact.attr1)>0 or length(contact.attr1Ex)>0 or "
							+ "length(contact.attr2)>0 or length(contact.attr2Ex)>0 or "
							+ "length(contact.attr3)>0 or length(contact.attr3Ex)>0 or "
							+ "length(contact.attr4)>0 or length(contact.attr4Ex)>0 or "
							+ "length(contact.attr5)>0 or length(contact.attr5Ex)>0)");
			params.setLimit(0);
			final Result ids = repository.list(params);
			params.setQuery(Query.contact_chat);
			for (int i = 0; i < ids.size(); i++) {
				final Contact contact = repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id"));
				if (isTimeForNewChat(contact, params, true)) {
					final String action = getLastNearByAction(params, (BigInteger) ids.get(i).get("contact.id"));
					if (!action.startsWith("p=") && !sendContact(contact))
						sendLocation(contact);
				}
			}
		} catch (Exception e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}

	private String getLastNearByAction(final QueryParams params, final BigInteger id) {
		params.setSearch("chat.action is not null and chat.textId like '"
				+ Text.engagement_nearByLocation.name().substring(0, Text.engagement_nearByLocation.name().indexOf('L'))
				+ "%' and chat.contactId=" + adminId + " and chat.contactId2=" + id);
		final Result chats = repository.list(params);
		if (chats.size() == 0)
			return "";
		String action = (String) chats.get(0).get("chat.action");
		if (action == null)
			return "";
		if (action.contains("&quot;"))
			action = action.substring(action.indexOf("&quot;") + 6, action.lastIndexOf("&quot;"));
		if (action.contains("="))
			action = action.substring(0, action.lastIndexOf('='));
		return new String(Base64.getDecoder().decode(action), StandardCharsets.UTF_8);
	}

	private boolean sendChatTemplate(Contact contact) throws Exception {
		for (ChatTemplate chatTemplate : chatTemplates) {
			if (chatTemplate.eligible(contact) &&
					sendChat(chatTemplate.textId, contact, null, chatTemplate.action))
				return true;
		}
		return false;
	}

	private boolean sendLocation(Contact contact) throws Exception {
		String search = "";
		for (int i = 0; i < 6; i++) {
			String attr = (String) contact.getClass().getMethod("getAttr" + i).invoke(contact);
			if (!Strings.isEmpty(attr))
				search += "REGEXP_LIKE(location.attr" + i + ", '" + attr.replace('\u0015', '|') + "')=1 or ";
			attr = (String) contact.getClass().getMethod("getAttr" + i + "Ex").invoke(contact);
			if (!Strings.isEmpty(attr))
				search += "REGEXP_LIKE(location.attr" + i + "Ex, '" + attr.replace(',', '|') + "')=1 or ";
		}
		if (search.endsWith(" or ")) {
			final QueryParams params = new QueryParams(Query.location_list);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setUser(contact);
			params.setSearch("locationFavorite.id is null and location.image is not null and ("
					+ search.substring(0, search.length() - 4) + ")");
			final Result result = repository.list(params);
			params.setQuery(Query.contact_chat);
			params.setLatitude(null);
			double score = 0;
			Location location = null;
			for (int i = 0; i < result.size(); i++) {
				final Location l = repository.one(Location.class, (BigInteger) result.get(i).get("location.id"));
				params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId()
						+ " and chat.textId='" + Text.engagement_nearByLocation.name() + "' and chat.action like '%"
						+ Strings.encodeParam("l=" + l.getId()) + "%'");
				if (repository.list(params).size() == 0) {
					final double s = Score.getLocation(contact, l);
					if (s > score) {
						score = s;
						location = l;
					}
				}
			}
			if (location != null) {
				sendChat(Text.engagement_nearByLocation, contact, location,
						"ui.navigation.autoOpen(&quot;" + Strings.encodeParam("l=" + location.getId())
								+ "&quot;,event)");
				return true;
			}
		}
		return false;
	}

	private boolean sendContact(Contact contact) throws Exception {
		final String search = Score.getSearchContact(contact);
		if (search.length() > 0) {
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setSearch("contactLink.id is null and contact.id<>" + contact.getId()
					+ " and contact.image is not null and (" + search + ")");
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setUser(contact);
			final Result result = repository.list(params);
			params.setQuery(Query.contact_chat);
			params.setLatitude(null);
			double score = 0;
			Contact contact2 = null;
			for (int i = 0; i < result.size(); i++) {
				final Contact c = repository.one(Contact.class, (BigInteger) result.get(i).get("contact.id"));
				params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId()
						+ " and chat.textId='" + Text.engagement_nearByContact.name() + "' and chat.action like '%"
						+ Strings.encodeParam("p=" + c.getId()) + "%'");
				if (repository.list(params).size() == 0) {
					final double s = Score.getContact(contact, c);
					if (s > score) {
						score = s;
						contact2 = c;
					}
				}
			}
			if (score > 0.5) {
				final Location location = new Location();
				location.setName(contact2.getPseudonym());
				sendChat(Text.engagement_nearByContact, contact, location,
						"ui.navigation.autoOpen(&quot;" + Strings.encodeParam("p=" + contact2.getId())
								+ "&quot;,event)");
				return true;
			}
		}
		return false;
	}

	public boolean sendChat(Text textId, Contact contact, Location location, String action) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId()
				+ " and chat.textId='" + textId.name() + '\'');
		if (repository.list(params).size() == 0) {
			String s = textId.getText(contact.getLanguage());
			for (REPLACMENT rep : REPLACMENT.values())
				s = rep.replace(s, contact, location, externalService, repository);
			final Chat chat = new Chat();
			chat.setContactId(adminId);
			chat.setContactId2(contact.getId());
			chat.setSeen(false);
			chat.setAction(action);
			chat.setTextId(textId);
			chat.setNote(s);
			try {
				repository.save(chat);
				return true;
			} catch (IllegalArgumentException ex) {
				if (!"duplicate chat".equals(ex.getMessage()))
					throw new RuntimeException(ex);
			}
		}
		return false;
	}
}