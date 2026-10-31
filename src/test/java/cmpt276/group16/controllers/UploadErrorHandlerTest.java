package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

// MockMvc skips the multipart size limit, so this starts the real server and sends a real upload
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadErrorHandlerTest {

	@LocalServerPort
	private int port;

	@Value("${spring.servlet.multipart.max-file-size}")
	private DataSize maxFileSize;

	private ResponseEntity<String> upload(byte[] bytes) {
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return "transcript.pdf";
			}
		});
		return RestClient.create("http://localhost:" + port).post()
				.uri("/student/transcript")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(form)
				.retrieve()
				.toEntity(String.class);
	}

	@Test
	void fileOverTheLimitShowsAnErrorInsteadOfCrashing() {
		byte[] tooBig = new byte[(int) maxFileSize.toBytes() + 1];

		ResponseEntity<String> response = upload(tooBig);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody())
				.contains("Upload your transcript")
				.contains("Upload a file under " + UploadErrorHandler.readable(maxFileSize) + ".");
	}

	// Tomcat only reads 2 MB past the limit by default, then drops the connection before the page arrives
	@Test
	void muchLargerFileStillGetsTheErrorPage() {
		ResponseEntity<String> response = upload(new byte[10 * 1024 * 1024]);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("Upload a file under " + UploadErrorHandler.readable(maxFileSize) + ".");
	}

	@Test
	void sizeIsShownInTheLargestWholeUnit() {
		assertThat(UploadErrorHandler.readable(DataSize.ofMegabytes(1))).isEqualTo("1 MB");
		assertThat(UploadErrorHandler.readable(DataSize.ofKilobytes(512))).isEqualTo("512 KB");
	}
}
