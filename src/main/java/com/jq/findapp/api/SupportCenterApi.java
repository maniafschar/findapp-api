package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.EventService;
import com.jq.findapp.service.ImportLocationsService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.DbService;
import com.jq.findapp.service.backend.EngagementService;
import com.jq.findapp.service.backend.ImportLogService;
import com.jq.findapp.service.backend.StatisticsService;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@RestController
@Transactional
@CrossOrigin(origins = { "https://sc.spontify.me" })
@RequestMapping("support")
public class SupportCenterApi {
	private static volatile boolean schedulerRunning = false;

	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private EngagementService engagementService;

	@Autowired
	private ImportLogService importLogService;

	@Autowired
	private EventService eventService;

	@Autowired
	private DbService dbService;

	@Autowired
	private StatisticsService statisticsService;

	@Autowired
	private ImportLocationsService importLocationsService;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@Value("${app.scheduler.secret}")
	private String schedulerSecret;

	@Value("${app.supportCenter.secret}")
	private String supportCenterSecret;

	@DeleteMapping("user/{id}")
	public void userDelete(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			authenticationService.deleteAccount(repository.one(Contact.class, id));
		}
	}

	@GetMapping("user")
	public List<Object[]> user(@RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret) {
		if (supportCenterSecret.equals(secret)) {
			final QueryParams params = new QueryParams(Query.contact_listSupportCenter);
			params.setUser(authenticationService.verify(adminId, password, salt));
			params.setLimit(Integer.MAX_VALUE);
			return repository.list(params).getList();
		}
		return null;
	}

	@GetMapping("ticket")
	public List<Object[]> ticket(String search, @RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret) {
		if (supportCenterSecret.equals(secret)) {
			final QueryParams params = new QueryParams(Query.misc_listTicket);
			params.setUser(authenticationService.verify(adminId, password, salt));
			params.setSearch(search);
			params.setLimit(Integer.MAX_VALUE);
			return repository.list(params).getList();
		}
		return null;
	}

	@DeleteMapping("ticket/{id}")
	public void ticketDelete(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			repository.delete(repository.one(Ticket.class, id));
		}
	}

	@GetMapping("log")
	public List<Object[]> log(String search, @RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret) {
		if (supportCenterSecret.equals(secret)) {
			final QueryParams params = new QueryParams(Query.misc_listLog);
			params.setUser(authenticationService.verify(adminId, password, salt));
			params.setSearch(search);
			params.setLimit(Integer.MAX_VALUE);
			return repository.list(params).getList();
		}
		return null;
	}

	@PostMapping("email")
	@Produces(MediaType.TEXT_PLAIN)
	public void email(final BigInteger id, final String text, final String action,
			@RequestHeader String password, @RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			final Contact contact = authenticationService.verify(adminId, password, salt);
			notificationService.sendNotificationEmail(contact, repository.one(Contact.class, id), text, action);
		}
	}

	@PostMapping("marketing")
	public void marketing(@RequestBody Map<String, Object> data, @RequestHeader String password,
			@RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			final List<String> ids = (List<String>) data.get("ids");
			if (ids.size() == 0) {
				final QueryParams params = new QueryParams(
						Strings.isEmpty(data.get("search")) || !((String) data.get("search")).contains("geoLocation")
								? Query.contact_listId
								: Query.contact_listByLocation);
				params.setLimit(0);
				params.setSearch((Strings.isEmpty(data.get("search")) ? "" : data.get("search") + " and ") +
						"contact.id<>" + adminId + " and contact.verified=true and contact.version is not null");
				final Result result = repository.list(params);
				for (int i = 0; i < result.size(); i++)
					ids.add(result.get(i).get("contact.id").toString());
			}
			String action = (String) data.get("action");
			if (action != null && action.startsWith("https://"))
				action = "ui.navigation.openHTML(&quot;" + action + "&quot;)";
			for (String id : ids)
				engagementService.sendChat(Text.valueOf((String) data.get("text")),
						repository.one(Contact.class, BigInteger.valueOf(Long.parseLong(id))), null, action);
		}
	}

	@PutMapping("resend/{id}")
	public void resend(@PathVariable final BigInteger id, @RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret)
			throws Exception {
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			authenticationService.recoverSendEmailReminder(repository.one(Contact.class, id));
		}
	}

	@PostMapping("import/location/{id}/{category}")
	public String importLocation(@PathVariable final BigInteger id, @PathVariable final String category,
			@RequestHeader String password, @RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			return importLocationsService.importLocation(id, category);
		}
		return null;
	}

	@PutMapping("scheduler")
	public void scheduler(@RequestHeader String secret) throws Exception {
		if (schedulerSecret.equals(secret) && !schedulerRunning) {
			try {
				schedulerRunning = true;
				dbService.update();
				engagementService.sendChats();
				engagementService.sendNearBy();
				eventService.findMatchingSpontis();
				eventService.notifyParticipation();
				eventService.notifyCheckInOut(null);
				importLogService.importLog();
				statisticsService.update();
				engagementService.sendRegistrationReminder();
				dbService.backup();
			} finally {
				schedulerRunning = false;
			}
		}
	}

	@GetMapping("healthcheck")
	public void healthcheck(@RequestHeader String secret) throws Exception {
		if (schedulerSecret.equals(secret))
			repository.one(Contact.class, adminId);
	}
}