package cloudgene.mapred.api.v2.jobs;

import java.io.File;

import javax.validation.constraints.NotBlank;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cloudgene.mapred.Application;
import cloudgene.mapred.auth.AuthenticationService;
import cloudgene.mapred.auth.AuthenticationType;
import cloudgene.mapred.core.User;
import cloudgene.mapred.database.DownloadDao;
import cloudgene.mapred.database.JobDao;
import cloudgene.mapred.exceptions.JsonHttpStatusException;
import cloudgene.mapred.jobs.AbstractJob;
import cloudgene.mapred.jobs.CloudgeneParameterOutput;
import cloudgene.mapred.jobs.Download;
import cloudgene.mapred.jobs.workspace.ExternalWorkspaceFactory;
import cloudgene.mapred.util.PublicUser;
import cloudgene.sdk.internal.IExternalWorkspace;
import genepi.io.FileUtil;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import jakarta.inject.Inject;

@Controller
public class DownloadResults {

	private static final Log log = LogFactory.getLog(DownloadResults.class);

	@Inject
	protected Application application;

	@Inject
	protected AuthenticationService authenticationService;
	
	@Get("/results/{jobId}/{paramId}/{filename}")
	@Secured(SecurityRule.IS_ANONYMOUS)
	public File download(@PathVariable @NotBlank String jobId, @PathVariable @NotBlank String paramId,
			@PathVariable @NotBlank String filename, @Nullable Authentication authentication) {
		try {

			JobDao jobDao = new JobDao(application.getDatabase());
			AbstractJob job = jobDao.findById(jobId);

			if (job == null) {
				throw new JsonHttpStatusException(HttpStatus.NOT_FOUND, "Job " + jobId + " not found.");
			}

			// job is running -> load it from queue
			if (job.getState() == AbstractJob.STATE_WAITING || job.getState() == AbstractJob.STATE_RUNNING
					|| job.getState() == AbstractJob.STATE_EXPORTING) {
				job = application.getWorkflowEngine().getJobById(jobId);
			}

			User user = authenticationService.getUserByAuthentication(authentication, AuthenticationType.ALL_TOKENS);

			// public mode
			if (user == null) {
				user = PublicUser.getUser(application.getDatabase());
			}

			if (!user.isAdmin() && job.getUser().getId() != user.getId()) {
				throw new JsonHttpStatusException(HttpStatus.FORBIDDEN, "Access denied.");
			}

			DownloadDao dao = new DownloadDao(application.getDatabase());
			Download download = dao.findByJobAndPath(jobId, FileUtil.path(paramId, filename));

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

			if (download == null) {
				throw new JsonHttpStatusException(HttpStatus.NOT_FOUND, "download not found.");
			}

			if (download.getCount() == 0) {
				throw new JsonHttpStatusException(HttpStatus.NOT_FOUND, "number of max downloads exceeded.");
			}

			// update download counter if it not set to unlimited
			if (download.getCount() != -1) {
				download.decCount();
				dao.update(download);
			}

			IExternalWorkspace externalWorkspace = ExternalWorkspaceFactory.get(download.getPath());
			if (externalWorkspace != null) {
				// external workspace found, use link method and create redirect response
				String publicUrl = externalWorkspace.createPublicLink(download.getPath());
				//redirectTemporary(publicUrl);
				//return new StringRepresentation(publicUrl);
				//TODO: redirect to externak URL!
				throw new JsonHttpStatusException(HttpStatus.NOT_IMPLEMENTED, "Redirection not yet implemented!!");
			} else {
				// no external workspace found, use local workspace
				String localWorkspace = application.getSettings().getLocalWorkspace();
				String resultFile = FileUtil.path(localWorkspace, download.getPath());
				log.debug("Downloading file from local workspace " + resultFile);
				return new File(resultFile);
			}

		} catch (Exception e) {
			log.error("Processing download failed.", e);
			throw new JsonHttpStatusException(HttpStatus.BAD_REQUEST, "Processing download failed.");
		}
	}

}
