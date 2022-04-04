package cloudgene.mapred.server.controller;

import java.util.List;
import java.util.Map;

import cloudgene.mapred.apps.Application;
import cloudgene.mapred.apps.ApplicationRepository;
import cloudgene.mapred.core.Template;
import cloudgene.mapred.core.User;
import cloudgene.mapred.server.auth.AuthenticationService;
import cloudgene.mapred.server.auth.AuthenticationType;
import cloudgene.mapred.server.responses.ApplicationResponse;
import cloudgene.mapred.server.services.ApplicationService;
import cloudgene.mapred.util.JSONConverter;
import cloudgene.mapred.wdl.WdlApp;
import cloudgene.mapred.wdl.WdlParameterInput;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import jakarta.inject.Inject;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@Controller
public class AppController {

	@Inject
	protected cloudgene.mapred.server.Application application;

	@Inject
	protected AuthenticationService authenticationService;

	@Inject
	protected ApplicationService applicationService;

	@Get("/api/v2/server/apps/{appId}")
	@Secured(SecurityRule.IS_ANONYMOUS)
	public String getApp(@Nullable Authentication authentication, String appId) {

		User user = authenticationService.getUserByAuthentication(authentication, AuthenticationType.ALL_TOKENS);
		Application app = applicationService.getbyIdAndUser(user, appId);

		applicationService.chcekRequirements(app);

		List<WdlApp> apps = application.getSettings().getApplicationRepository().getAllByUser(user,
				ApplicationRepository.APPS_AND_DATASETS);

		JSONObject jsonObject = JSONConverter.convert(app.getWdlApp());
		List<WdlParameterInput> params = app.getWdlApp().getWorkflow().getInputs();

		JSONArray jsonArray = JSONConverter.convert(params, apps);

		jsonObject.put("params", jsonArray);
		jsonObject.put("s3Workspace", application.getSettings().getExternalWorkspaceType().equalsIgnoreCase("S3")
				&& application.getSettings().getExternalWorkspaceLocation().isEmpty());
		String footer = this.application.getTemplate(Template.FOOTER_SUBMIT_JOB);
		if (footer != null && !footer.trim().isEmpty()) {
			jsonObject.put("footer", footer);
		}

		return jsonObject.toString();

	}

	@Delete("/api/v2/server/apps/{appId}")
	@Secured(User.ROLE_ADMIN)
	public ApplicationResponse removeApp(String appId) {
		Application app = applicationService.removeApp(appId);
		return ApplicationResponse.build(app, this.application.getSettings());
	}

	@Put("/api/v2/server/apps/{appId}")
	@Secured(User.ROLE_ADMIN)
	public ApplicationResponse updateApp(String appId, @Nullable String enabled, @Nullable String permission,
			@Nullable String reinstall, @Nullable Map<String, String> config) {

		ApplicationRepository repository = application.getSettings().getApplicationRepository();
		Application application = repository.getById(appId);

		// enable or disable
		applicationService.enableApp(appId, enabled);
		// update permissions
		applicationService.updatePermissions(appId, permission);
		// update config
		applicationService.updateConfig(appId, config);
		// reinstall application
		applicationService.reinstallApp(appId, reinstall);

		application.checkForChanges();

		ApplicationResponse appResponse = ApplicationResponse.buildWithDetails(application,
				this.application.getSettings(), repository);

		return appResponse;
	}

	@Post("/api/v2/server/apps")
	@Secured(User.ROLE_ADMIN)
	public ApplicationResponse install(@Nullable String url) {
		Application app = applicationService.installApp(url);
		ApplicationRepository repository = application.getSettings().getApplicationRepository();
		return ApplicationResponse.buildWithDetails(app, application.getSettings(), repository);
	}

	@Get("/api/v2/server/apps")
	@Secured(User.ROLE_ADMIN)
	public List<ApplicationResponse> list(@Nullable @QueryValue("reload") String reload) {
		List<Application> apps = applicationService.listApps(reload);
		ApplicationRepository repository = application.getSettings().getApplicationRepository();
		return ApplicationResponse.buildWithDetails(apps, application.getSettings(), repository);
	}

}
