package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Strings;

@Service
public class RssService {
	@Autowired
	private Repository repository;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	private final Set<String> failed = Collections.synchronizedSet(new HashSet<>());

	public SchedulerResult run() {
		final SchedulerResult result = new SchedulerResult();
		final Result list = this.repository.list(new QueryParams(Query.misc_listClient));
		final List<CompletableFuture<?>> futures = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			try {
				final JsonNode json = new ObjectMapper().readTree(list.get(i).get("client.storage").toString());
				if (json.has("rss")) {
					for (int i2 = 0; i2 < json.get("rss").size(); i2++)
						futures.add(this.syncFeed(json.get("rss").get(i2), (BigInteger) list.get(i).get("client.id"),
								json.has("publishingPostfix") ? json.get("publishingPostfix").asText() : null));
				}
			} catch (final Exception e) {
				if (result.exception == null)
					result.exception = e;
			}
		}
		CompletableFuture
				.allOf(futures.toArray(new CompletableFuture[futures.size()]))
				.thenApply(ignored -> futures.stream()
						.map(CompletableFuture::join).collect(Collectors.toList()))
				.join();
		result.result = futures.stream().map(e -> {
			try {
				return e.get().toString();
			} catch (final Exception ex) {
				return ex.getMessage();
			}
		}).collect(Collectors.joining("\n"));
		while (result.result.contains("\n\n"))
			result.result = result.result.replace("\n\n", "\n");
		if (this.failed.size() > 0)
			this.notificationService.createTicket(TicketType.ERROR, "ImportRss",
					this.failed.size() + " errors:\n" + this.failed.stream().sorted().collect(Collectors.joining("\n")),
					null);
		return result;
	}

	@Async
	private CompletableFuture<Object> syncFeed(final JsonNode json, final BigInteger clientId,
			final String publishingPostfix) {
		return CompletableFuture.supplyAsync(() -> {
			return new ImportFeed().run(json, clientId, publishingPostfix);
		});
	}

	private class ImportFeed {
		private final Pattern[] imageRegex = new Pattern[] {
				Pattern.compile("\\<meta.*?property=\"og:image\".*?content=\\\"(.*?)\\\""),
				Pattern.compile("\\<article.*?\\<figure.*?\\<img .*?src=\\\"(.*?)\\\""),
				Pattern.compile("\\<div.*?\\<picture.*?\\<img .*?src=\\\"(.*?)\\\"")
		};
		private final SimpleDateFormat dateParser = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ROOT);
		private final SimpleDateFormat dateParser2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
		private final Set<String> urls = new HashSet<>();

		private String run(final JsonNode json, final BigInteger clientId, final String publishingPostfix) {
			try {
				final String url = json.get("url").asText();
				ArrayNode rss = null;
				while (rss == null) {
					try {
						rss = (ArrayNode) new XmlMapper().readTree(URI.create(url).toURL()).findValues("item")
								.get(0);
					} catch (JsonParseException ex) {
						if (!ex.getMessage().contains("EOF"))
							throw new RuntimeException(ex);
						Thread.sleep(1000);
					}
				}
				if (rss.size() == 0)
					return "";
				final QueryParams params = new QueryParams(Query.misc_listNews);
				params.setUser(new Contact());
				params.getUser().setClientId(clientId);
				int count = 0;
				boolean chonological = true;
				long lastPubDate = 0;
				Timestamp first = new Timestamp(System.currentTimeMillis());
				final boolean addDescription = json.has("description") && json.get("description").asBoolean();
				final String source = json.has("source") ? json.get("source").asText() : null;
				final String category = json.has("category") ? json.get("category").asText() : null;
				for (int i = 0; i < rss.size(); i++) {
					try {
						final ClientNews clientNews = this.createNews(params, rss.get(i), addDescription, clientId, url,
								source, category);
						if (clientNews != null && clientNews.getImage() != null) {
							if (clientNews.getPublish().getTime() > lastPubDate)
								chonological = false;
							else
								lastPubDate = clientNews.getPublish().getTime();
							if (clientNews.getId() == null)
								count++;
							clientNews.setLatitude((float) json.get("latitude").asDouble());
							clientNews.setLongitude((float) json.get("longitude").asDouble());
							final boolean b = json.get("publish").asBoolean() && clientNews.getId() == null;
							RssService.this.repository.save(clientNews);
							if (first.getTime() > clientNews.getPublish().getTime())
								first = clientNews.getPublish();
							this.urls.add(clientNews.getUrl());
							if (b)
								RssService.this.externalService.publishOnFacebook(clientId,
										clientNews.getDescription()
												+ (Strings.isEmpty(source) ? "" : "\n" + source)
												+ (Strings.isEmpty(publishingPostfix) ? ""
														: "\n\n" + publishingPostfix),
										"/rest/marketing/news/" + clientNews.getId());
						}
					} catch (final Exception ex) {
						this.addFailure(ex, url);
					}
				}
				int deleted = 0;
				if (chonological) {
					params.setSearch(
							"clientNews.publish>cast('" + first + "' as timestamp) and clientNews.clientId="
									+ clientId);
					final Result result = RssService.this.repository.list(params);
					for (int i = 0; i < result.size(); i++) {
						if (!this.urls.contains(result.get(i).get("clientNews.url"))) {
							RssService.this.repository.delete(RssService.this.repository.one(ClientNews.class,
									(BigInteger) result.get(i).get("clientNews.id")));
							RssService.this.notificationService.createTicket(TicketType.ERROR, "RSS Deletion",
									result.get(i).get("clientNews.id") + "\n"
											+ result.get(i).get("clientNews.description")
											+ "\n" + result.get(i).get("clientNews.url"),
									clientId);
							deleted++;
						}
					}
				}
				if (count != 0 || deleted != 0)
					return count + " on " + clientId + " " + url + (deleted > 0 ? ", " + deleted + " deleted" : "");
			} catch (final Exception ex) {
				this.addFailure(ex, json.get("url").asText());
			}
			return "";
		}

		private synchronized void addFailure(final Exception ex, final String url) {
			RssService.this.failed.add((ex.getMessage().startsWith("IMAGE_") ? "" : ex.getClass().getName() + ": ")
					+ ex.getMessage().replace("\n", "\n  ") + "\n  " + url);
		}

		private ClientNews createNews(final QueryParams params, final JsonNode rss, final boolean addDescription,
				final BigInteger clientId, final String url, final String source, final String category)
				throws Exception {
			String uid = null;
			if (rss.has("link"))
				uid = rss.get("link").asText().trim();
			if (uid == null) {
				uid = rss.get("guid").asText().trim();
				if (Strings.isEmpty(uid))
					uid = rss.get("guid").get("").asText().trim();
			}
			final int max = 1000;
			final String description = Strings.sanitize(rss.get("title").asText() +
					(addDescription && rss.has("description")
							? "\n" + rss.get("description").asText()
							: ""),
					max).trim();
			params.setSearch("clientNews.url='" + uid + "' and clientNews.clientId=" + clientId);
			Result result = RssService.this.repository.list(params);
			final ClientNews clientNews;
			if (result.size() == 0) {
				params.setSearch("clientNews.description like '" + description.replace('\'', '_').replace('\n', '_')
						+ "' and clientNews.clientId=" + clientId);
				result = RssService.this.repository.list(params);
			}
			if (result.size() == 0)
				clientNews = new ClientNews();
			else {
				clientNews = RssService.this.repository.one(ClientNews.class,
						(BigInteger) result.get(0).get("clientNews.id"));
				clientNews.historize();
			}
			clientNews.setSource(source);
			clientNews.setClientId(clientId);
			clientNews.setDescription(description);
			clientNews.setCategory(category);
			clientNews.setUrl(uid);
			if (rss.has("pubDate"))
				clientNews.setPublish(new Timestamp(this.dateParser.parse(rss.get("pubDate").asText()).getTime()));
			else
				clientNews.setPublish(new Timestamp(this.dateParser2.parse(rss.get("date").asText()).getTime()));
			clientNews.setImage(null);
			if (rss.has("media:content") && rss.get("media:content").has("url"))
				clientNews.setImage(EntityUtil.getImage(rss.get("media:content").get("url").asText(),
						EntityUtil.IMAGE_SIZE, 200));
			else {
				final String html = IOUtils.toString(new URI(uid), StandardCharsets.UTF_8)
						.replace('\r', ' ').replace('\n', ' ');
				String s = null;
				for (Pattern pattern : imageRegex) {
					final Matcher matcher = pattern.matcher(html);
					if (matcher.find()) {
						s = matcher.group(1);
						break;
					}
				}
				if (s != null) {
					if (s.startsWith("/"))
						s = url.substring(0, url.indexOf("/", 10)) + s;
					clientNews.setImage(EntityUtil.getImage(s, EntityUtil.IMAGE_SIZE, 200));
				}
			}
			return clientNews;
		}
	}
}
