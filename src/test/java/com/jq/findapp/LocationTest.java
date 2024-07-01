package com.jq.findapp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;

import com.jq.findapp.util.Utils;

public class LocationTest {
	private WebDriver driver;

	@BeforeEach
	public void start() throws Exception {
		driver = new ChromeDriver();
		driver.manage().timeouts().implicitlyWait(Duration.ofMillis(50));
		driver.manage().window().setSize(new Dimension(1200, 900));
	}

	@AfterEach
	public void stop() throws Exception {
		driver.close();
	}

	@Test
	public void run() throws Exception {
		try (final BufferedReader reader = new BufferedReader(new FileReader("sample.txt"))) {
			final Pattern email = Pattern.compile(".*(\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,8}\\b).*", Pattern.CASE_INSENSITIVE);
			String line;
			while ((line = reader.readLine()) != null) {
				final String s[] = line.split("\\|");
				driver.get("https://www.google.com/search?q=" + s[0]);
				((JavascriptExecutor) driver).executeScript("document.getElementById('center_col').querySelector('span>a').click()");
				((JavascriptExecutor) driver).executeScript("Array.from(document.querySelectorAll('a')).find(el => el.textContent.toLowerCase().indexOf('impressum')>-1)");
				final String html = ((JavascriptExecutor) driver).executeScript("document.body.innerHTML");
				Matcher matcher = email.matcher(html);
				if (matcher.find())
					System.out.println(s[1] + ": " + matcher.group(1));
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
			Util.sleep(600000);
		}
	}
}
