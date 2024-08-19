package com.jq.findapp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class }, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class MarketingApiTest {
	@Autowired
	private MarketingApi marketingApi;

	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void news() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final ClientNews news = new ClientNews();
		news.setClientId(BigInteger.ONE);
		news.setDescription("abc");
		news.setSource("xyz");
		news.setUrl("https://def.gh");
		repository.save(news);
		marketingApi.news(news.getId());
		long time = System.currentTimeMillis();

		// when
		final String result = marketingApi.news(news.getId());

		// then
		time = System.currentTimeMillis() - time;
		assertTrue(time < 20, "time " + time);
		assertTrue(result.contains("<article>abc"));
	}

	@Test
	public void replaceFirst() {
		// given
		final String s = "<htmtl>\n\t<body>\n\t\t<meta property=\"og:url\" content=\"aaa\"/>\n\t</body>\n</html>";

		// when
		final String result = s.replaceFirst("<meta property=\"og:url\" content=\"([^\"].*)\"",
				"<meta property=\"og:url\" content=\"xxx\"");

		// then
		assertEquals("<htmtl>\n\t<body>\n\t\t<meta property=\"og:url\" content=\"xxx\"/>\n\t</body>\n</html>", result);
	}

	@Test
	public void replace_alternate() {
		// given
		final String s = "<htmtl>\n\t<body>\n\t\t<link rel=\"alternate\" href=\"aaa\"/>\n\t</body>\n</html>";

		// when
		final String result = s.replaceFirst("(<link rel=\"alternate\" ([^>].*)>)", "");

		// then
		assertEquals("<htmtl>\n\t<body>\n\t\t\n\t</body>\n</html>", result);
	}
}
