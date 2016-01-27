package cloudgene.mapred.api.v2.jobs;

import genepi.io.FileUtil;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.MediaType;
import org.restlet.ext.html.FormData;
import org.restlet.ext.html.FormDataSet;
import org.restlet.representation.FileRepresentation;
import org.restlet.resource.ClientResource;

import cloudgene.mapred.api.v2.JobsApiTestCase;
import cloudgene.mapred.jobs.AbstractJob;
import cloudgene.mapred.util.TestEnvironment;

public class SubmitJobTest extends JobsApiTestCase {

	@Override
	protected void setUp() throws Exception {
		TestEnvironment.getInstance().startWebServer();

	}

	public void testSubmitWrongApplication() throws IOException, JSONException,
			InterruptedException {

		// form data

		FormDataSet form = new FormDataSet();
		form.setMultipart(true);
		form.getEntries().add(new FormData("input-input", "input-file"));

		// submit job
		ClientResource resource = createClientResource("/jobs/newsubmit/wrong-application");
		try {
			resource.post(form);
		} catch (Exception e) {
			assertEquals(404, resource.getStatus().getCode());
			JSONObject object = new JSONObject(resource.getResponseEntity()
					.getText());
			assertEquals(object.get("success"), false);
		}
	}

	public void testSubmitAllPossibleInputs() throws IOException,
			JSONException, InterruptedException {

		// form data

		FormDataSet form = new FormDataSet();
		form.setMultipart(true);
		form.getEntries().add(new FormData("input-text", "my-text"));
		form.getEntries().add(new FormData("input-number", "27"));
		// ignore checkbox
		form.getEntries().add(new FormData("input-list", "valuea"));
		//local-file
		FileUtil.writeStringBufferToFile("test.txt", new StringBuffer(
				"content-of-my-file"));
		form.getEntries().add(
				new FormData("input-file", new FileRepresentation("test.txt",
						MediaType.TEXT_PLAIN)));
		
		//local-folder
		FileUtil.writeStringBufferToFile("test1.txt", new StringBuffer(
				"content-of-my-file-in-folder1"));
		FileUtil.writeStringBufferToFile("test2.txt", new StringBuffer(
				"content-of-my-file-in-folder2"));		
		
		form.getEntries().add(
				new FormData("input-folder", new FileRepresentation("test1.txt",
						MediaType.TEXT_PLAIN)));		
		form.getEntries().add(
				new FormData("input-folder", new FileRepresentation("test2.txt",
						MediaType.TEXT_PLAIN)));

		// submit job
		String id = submitJob("all-possible-inputs", form);

		// check feedback
		waitForJob(id);

		JSONObject result = getJobDetails(id);

		assertEquals(AbstractJob.STATE_SUCCESS, result.get("state"));

	}

	public void testSubmitReturnTrueStepPublic() throws IOException,
			JSONException, InterruptedException {

		// form data

		FormDataSet form = new FormDataSet();
		form.setMultipart(true);
		form.getEntries().add(new FormData("input-input", "input-file"));

		// submit job
		String id = submitJob("return-true-step-public", form);

		// check feedback
		waitForJob(id);

		JSONObject result = getJobDetails(id);

		assertEquals(AbstractJob.STATE_SUCCESS, result.get("state"));

	}

	public void testSubmitReturnFalseStepPublic() throws IOException,
			JSONException, InterruptedException {

		// form data

		FormDataSet form = new FormDataSet();
		form.setMultipart(true);
		form.getEntries().add(new FormData("input-input", "input-file"));

		// submit job
		String id = submitJob("return-false-step-public", form);

		// check feedback
		waitForJob(id);

		JSONObject result = getJobDetails(id);

		assertEquals(AbstractJob.STATE_FAILED, result.get("state"));

	}

	public void testSubmitReturnExceptionStepPublic() throws IOException,
			JSONException, InterruptedException {

		// form data

		FormDataSet form = new FormDataSet();
		form.setMultipart(true);
		form.getEntries().add(new FormData("input-input", "input-file"));

		// submit job
		String id = submitJob("return-exception-step-public", form);

		// check feedback
		waitForJob(id);

		JSONObject result = getJobDetails(id);

		assertEquals(AbstractJob.STATE_FAILED, result.get("state"));

	}

	public void testSubmitWriteTextToFilePublic() throws IOException,
			JSONException, InterruptedException {

		// form data

		FormDataSet form = new FormDataSet();
		form.setMultipart(true);
		form.add("input-inputtext", "lukas_text");

		// submit job
		String id = submitJob("write-text-to-file", form);

		// check feedback
		waitForJob(id);

		// TODO: change!
		Thread.sleep(5000);

		// get details
		JSONObject result = getJobDetails(id);

		assertEquals(AbstractJob.STATE_SUCCESS, result.get("state"));

		// get path and download file
		String path = result.getJSONArray("outputParams").getJSONObject(0)
				.getJSONArray("files").getJSONObject(0).getString("path");

		String content = downloadResults(path);

		assertEquals("lukas_text", content);

	}

	
	//TODO: wrong permissions
	
	//TODO: wrong id
	
}
