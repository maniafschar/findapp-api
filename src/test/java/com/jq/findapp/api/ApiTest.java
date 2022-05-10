package com.jq.findapp.api;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.JpaTestConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith({ SpringExtension.class })
@SpringBootTest(classes = { FindappApplication.class,
		JpaTestConfiguration.class }, webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "app.admin.id=1" })
@ActiveProfiles("test")
public class ApiTest {
	@LocalServerPort
	private int port;

	@Test
	public void test() throws Exception {
		// given
		String body = "{\"user\":{\"email\":\"ea95mHClVyMB5EwBMwpHUBVCZr9uEuUGqboGXoJgACsjLpJ8rw5XqOj+UdOhwk3Rfsf7i3t5tHIybIusMeCvzfwxkSOLl3Fr6Hh2bI9g+XeDzVy1B5HZDuzJiu40fengQ3hkPN6YLHMw8sFSfgE9hGUHnY1Nly12uvY5w0xvL/caeGxLCX8Ua8HY/dxvZf7Qd6ycp3s6yZN0klp2gJjkMkiNMTmFE3Ikm3BKaB5hVmNANddWvmaQHc/zj2RLSkb3T0KFRbIhuml/H50uRX6w9ZJ7R9DUX+1bFCxyldqY3F71U38Y9XAU9HdAaP42AVwGb5nFm5cRLaV33qs/Q6lng8gRKa1XZlI+yfU0/PgqkxD0fFCFiGMZYV18Aj00ZntbFn328W/uOcaYqsjMiS/ubu/Ax85YI4vE0uXCYVe1y9+CtNgMs0lSOTv9BxOh76Opf2eBgd1qpZwh6mm7WLo54c+MXq5fkbPHbJ15tWG0PSvi4CsREY0gray4Ua6nAmHegmZhVLj4tMe1WbYVOanHhjBJ7eWltku42GC3i8Jv0s361VHohO3o2eQbXFB5zNNual76bshmKA5otq9GTcS0Rd1tF2r0pQRDd5PLYU/K1y96AXX7LwKi/ddHBuE994UAsql+SWXS2kX2bcjL0JlnV2d5RBd79BPcfpsBgpgG5bo=\",\"authorizationCode\":\"xyz\",\"state\":\"\",\"identityToken\":\"xyz\",\"name\":\"Büşılay Ömäer\",\"id\":\"AMOm3mNt7GDoIdWT2/xI+KM/v4qW4Vd53yRW6lEr2FqFCEplOCHnL9saE8njBmP9sZ4zpAVgIxlThZ4KQuAwdE56LJZfJ/WTkI3tNU/0315FuofyYlrxdHtAPfxgD6bQyUJz765AOFhmf8cwZfPjKCFDZkInGB3cI9hsSWlVen9RKv90sU6M1eY3dAXjuOK7vwyKguCeo1l1OxTbXiTjBECE2DwERDRTVfRlvTCGAOK4V+XFlG16g1vUX1NqlSCmvJ9dVnyywOpAeVl21Z2nS8fP498GWYPCDEZbB/tMe5heQsiFyZL+PL0x+QglIZLhk0WIIzZnSslBHgN9Qd2DE7T1bNlTXNiEJqNCBM2VhBCG03Zq+lWIrzxwO28N9znN1tdKK28hnsUcQuE5mLKfaQFyIxMeepBr2byaE/c7M5q3tOirp6LsYtWWvGPik0B0Ns91/O9DNV3qjBYaKsO1GvhsNzE12Gmbk4XdyxGPTmfZXptC1ZWYblqbLRdiS3cXKCzfTR1lLOvkgAXsTNxB1KoXBBGIuL2NT1Lpk7l1yXcil/FD+74jsKZmQ324TPBcHXZHKquADVt2ksu9GcvAlIRGe8Ls+dGwXLKnSxRwINky05A5NtGjyP6kOBb5xPAtKc/KokUf932X9xvPk6XidmrfJitKIFn+sRhQAxL2yho=\"},\"from\":\"Apple\",\"language\":\"DE\",\"version\":\"0.9.4\",\"device\":\"phone\",\"os\":\"ios\",\"publicKey\":\"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCyQHZFhhEmQgXFECZgYaaiRo/ur5ZZf7K/nBd2yv6SQdXtPnXFteH1Uax7jOo/fgjkIjHTWYZ0np7H3eP6k4TEAYmTqAnNryzNQ/YBExrFuP9LQQEzGlEFj6vz4FLWhIISik6/eOWF+2oFT84720wWpvx1yx/NEhot0Cj5vcxn/wIDAQAB\"}";
		String path = "authentication/loginExternal";

		// when
		String response = WebClient.create("http://localhost:" + port + "/" + path)
				.put().contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().toEntity(String.class).block()
				.getBody();

		// then
		System.out.println(response);
	}
}
