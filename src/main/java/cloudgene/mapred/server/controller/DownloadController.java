package cloudgene.mapred.server.controller;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cloudgene.mapred.core.User;
import cloudgene.mapred.database.DownloadDao;
import cloudgene.mapred.database.ParameterDao;
import cloudgene.mapred.jobs.AbstractJob;
import cloudgene.mapred.jobs.CloudgeneParameterOutput;
import cloudgene.mapred.jobs.Download;
import cloudgene.mapred.server.Application;
import cloudgene.mapred.server.auth.AuthenticationService;
import cloudgene.mapred.server.auth.AuthenticationType;
import cloudgene.mapred.server.exceptions.JsonHttpStatusException;
import cloudgene.mapred.server.services.DownloadService;
import cloudgene.mapred.server.services.JobService;
import genepi.io.FileUtil;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import jakarta.inject.Inject;

@Controller
public class DownloadController {

	protected static final Log log = LogFactory.getLog(DownloadController.class);

	@Inject
	protected Application application;

	@Inject
	protected AuthenticationService authenticationService;

	@Inject
	protected DownloadService downloadService;

	@Inject
	protected JobService jobService;

	@Get("/results/{jobId}/{paramId}/{filename}")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	public HttpResponse<File> download(Authentication authentication, String jobId, String paramId, String filename)
			throws URISyntaxException {

		User user = authenticationService.getUserByAuthentication(authentication, AuthenticationType.ALL_TOKENS);

		AbstractJob job = jobService.getByIdAndUser(jobId, user);

		DownloadDao dao = new DownloadDao(application.getDatabase());
		Download download = dao.findByJobAndPath(jobId, FileUtil.path(paramId, filename));
		System.out.println(jobId);
		System.out.println(paramId);
		System.out.println(filename);

		// job is running and not in database --> download possible of
		// autoexport params
		if (download == null) {
			for (CloudgeneParameterOutput param : job.getOutputParams()) {
				if ((param.isAutoExport() || !job.isRunning()) && param.getFiles() != null) {
					for (Download download2 : param.getFiles()) {
						if (download2.getPath().equals(FileUtil.path(jobId, paramId, filename))) {
							download = download2;
						}
						if (download2.getPath().startsWith("s3://") && download2.getName().equals(filename)) {
							download = download2;
						}
					}
				}
			}
		}

		return downloadService.download(download);

	}

	@Get("/downloads/{jobId}/{hash}/{filename}")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	public HttpResponse<File> downloadExternalResults(Authentication authentication, String jobId, String hash,
			String filename) throws URISyntaxException {

		User user = authenticationService.getUserByAuthentication(authentication, AuthenticationType.ALL_TOKENS);

		AbstractJob job = jobService.getByIdAndUser(jobId, user);

		DownloadDao dao = new DownloadDao(application.getDatabase());
		Download download = dao.findByHash(hash);

		// job is running and not in database --> download possible of
		// autoexport params
		if (download == null) {
			for (CloudgeneParameterOutput param : job.getOutputParams()) {
				if (param.isAutoExport() && param.getFiles() != null) {
					for (Download download2 : param.getFiles()) {
						if (download2.getHash().equals(hash)) {
							download = download2;
						}
					}
				}
			}
		}

		return downloadService.download(download);

	}

	@Get("/get/{paramId}/{hash}")
	@Secured(SecurityRule.IS_ANONYMOUS)
	public String downloadScript(String paramId, String hash) {

		int id = -1;
		try {
			id = Integer.parseInt(paramId);
		} catch (NumberFormatException e) {
			throw new JsonHttpStatusException(HttpStatus.BAD_REQUEST, "Parameter ID is not numeric.");
		}

		ParameterDao parameterDao = new ParameterDao(application.getDatabase());
		CloudgeneParameterOutput param = parameterDao.findById(id);

		if (param == null) {
			throw new JsonHttpStatusException(HttpStatus.NOT_FOUND, "Param " + param + " not found.");
		}

		if (!hash.equals(param.createHash())) {
			throw new JsonHttpStatusException(HttpStatus.FORBIDDEN, "Download forbidden.");
		}

		DownloadDao dao = new DownloadDao(application.getDatabase());
		List<Download> downloads = dao.findAllByParameter(param);

		String hostname = application.getSettings().getHostname();

		StringBuffer script = new StringBuffer();
		script.append("#!/bin/bash\n");
		script.append("set -e\n");
		script.append("GREEN='\033[0;32m'\n");
		script.append("NC='\033[0m'\n");
		int i = 1;
		for (Download download : downloads) {
			script.append("echo \"\"\n");
			script.append(
					"echo \"Downloading file " + download.getName() + " (" + i + "/" + downloads.size() + ")...\"\n");
			script.append("curl -L " + hostname + "/share/results/" + download.getHash() + "/" + download.getName()
					+ " -o " + download.getName() + " --create-dirs \n");
			i++;
		}
		script.append("echo \"\"\n");
		script.append("echo -e \"${GREEN}All " + downloads.size() + " file(s) downloaded.${NC}\"\n");
		script.append("echo \"\"\n");
		script.append("echo \"\"\n");
		return script.toString();

	}

	@Get("/api/v2/jobs/{jobId}/chunks/{filename}")
	@Secured(SecurityRule.IS_AUTHENTICATED)
	public File downloadChunk(Authentication authentication, String jobId, String filename) {

		User user = authenticationService.getUserByAuthentication(authentication, AuthenticationType.ALL_TOKENS);

		AbstractJob job = jobService.getByIdAndUser(jobId, user);

		String resultFile = FileUtil.path(application.getSettings().getLocalWorkspace(), job.getId(), "chunks",
				filename);

		return new File(resultFile);

	}

	@Get("/share/results/{hash}/{filename}")
	@Secured(SecurityRule.IS_ANONYMOUS)
	public HttpResponse<File> downloadPublicLink(String hash, String filename) throws URISyntaxException {

		DownloadDao dao = new DownloadDao(application.getDatabase());
		Download download = dao.findByHash(hash);

		return downloadService.download(download);

	}

}