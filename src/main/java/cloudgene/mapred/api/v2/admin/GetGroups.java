package cloudgene.mapred.api.v2.admin;

import java.util.List;
import java.util.Vector;

import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;

import cloudgene.mapred.apps.Application;
import cloudgene.mapred.apps.ApplicationRespository;
import cloudgene.mapred.core.User;
import cloudgene.mapred.util.BaseResource;
import net.sf.json.JSONArray;

public class GetGroups extends BaseResource {

	private ApplicationRespository repository = ApplicationRespository.getInstance();
	
	@Get
	public Representation get() {

		User user = getAuthUser();

		if (user == null) {

			setStatus(Status.CLIENT_ERROR_UNAUTHORIZED);
			return new StringRepresentation("The request requires user authentication.");

		}

		if (!user.isAdmin()) {
			setStatus(Status.CLIENT_ERROR_UNAUTHORIZED);
			return new StringRepresentation("The request requires administration rights.");
		}

		List<Group> groups = new Vector<Group>();
		groups.add(new Group("admin"));
		groups.add(new Group("user"));
		for (Application application : repository.getAll()) {
			Group group = new Group(application.getPermission());
			if (!groups.contains(group)) {
				group.addApp(application.getId());
				groups.add(group);
			} else {
				int index = groups.indexOf(group);
				group = groups.get(index);
				group.addApp(application.getId());
			}
		}

		JSONArray jsonArray = JSONArray.fromObject(groups);

		return new StringRepresentation(jsonArray.toString());

	}

	public static class Group {

		private String name;

		private List<String> apps = new Vector<String>();

		public Group(String name) {
			this.name = name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setApps(List<String> apps) {
			this.apps = apps;
		}

		public List<String> getApps() {
			return apps;
		}
		
		public void addApp(String app){
			apps.add(app);
		}

		@Override
		public boolean equals(Object obj) {
			Group g = (Group) obj;
			return g.getName().toLowerCase().equals(getName().toLowerCase());
		}
	}

}
