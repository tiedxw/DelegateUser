package jp.ricksoft.plugins.delegate_usermanager_for_jira.action;

import static com.atlassian.jira.avatar.Avatar.Size.SMALL;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;

import com.atlassian.collectors.CollectorsUtil;
import com.atlassian.crowd.embedded.api.CrowdDirectoryService;
import com.atlassian.crowd.embedded.api.CrowdService;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.crowd.model.group.GroupType;
import com.atlassian.crowd.search.EntityDescriptor;
import com.atlassian.crowd.search.builder.QueryBuilder;
import com.atlassian.crowd.search.query.entity.EntityQuery;
import com.atlassian.crowd.search.query.entity.GroupQuery;
import com.atlassian.crowd.search.query.entity.restriction.NullRestrictionImpl;
import com.atlassian.crowd.search.query.membership.MembershipQuery;
import com.atlassian.jira.application.ApplicationRole;
import com.atlassian.jira.application.ApplicationRoleComparator;
import com.atlassian.jira.application.ApplicationRoleManager;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.plugin.webfragment.SimpleLinkManager;
import com.atlassian.jira.plugin.webfragment.model.JiraHelper;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.web.SessionKeys;
import com.atlassian.jira.web.action.AbstractBrowser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.web.bean.UserBrowserFilter;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.opensymphony.util.TextUtils;

import webwork.action.ActionContext;
import webwork.action.ServletActionContext;
import webwork.util.BeanUtil;

@SuppressWarnings({ "deprecation", "unchecked", "serial", "unused", "rawtypes" })
public class DelegateUserBrowser extends AbstractBrowser {

	private static ObjectMapper OBJECT_MAPPER;

	private List<ApplicationUser> users;
	private String[] createdUser;
	private final UserUtil userUtil;
	private final CrowdService crowdService;
	private final CrowdDirectoryService crowdDirectoryService;
	private final UserManager userManager;
	private final AvatarService avatarService;
	private final SimpleLinkManager simpleLinkManager;
	private final ApplicationRoleManager applicationRoleManager;
	private final PageBuilderService pageBuilderService;

	private final static String EMPTY_FILTER_VALUE = "";

	static {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
		OBJECT_MAPPER = mapper;
	}

	public DelegateUserBrowser(final UserUtil userUtil, final CrowdService crowdService,
			final CrowdDirectoryService crowdDirectoryService, final UserManager userManager,
			final AvatarService avatarService, final SimpleLinkManager simpleLinkManager,
			final ApplicationRoleManager applicationRoleManager, final PageBuilderService pageBuilderService) {
		this.userUtil = userUtil;
		this.crowdService = crowdService;
		this.crowdDirectoryService = crowdDirectoryService;
		this.userManager = userManager;
		this.avatarService = avatarService;
		this.simpleLinkManager = simpleLinkManager;
		this.applicationRoleManager = applicationRoleManager;
		this.pageBuilderService = pageBuilderService;
	}

	protected String doExecute() throws Exception {
		String userSearchFilter = getSingleParam("userSearchFilter");
		String applicationFilterParam = getSingleParam("applicationFilter");
		String groupParam = getSingleParam("group");
		String activeFilter = getSingleParam("activeFilter");
		String maxUsersPerPage = getSingleParam("max");

		if (EMPTY_FILTER_VALUE.equals(userSearchFilter) || EMPTY_FILTER_VALUE.equals(groupParam)
				|| StringUtils.isNotEmpty(applicationFilterParam) || getCreatedUsers().findAny().isPresent()
				|| EMPTY_FILTER_VALUE.equals(activeFilter)) {
			resetPager();
			setStart("0");
		}

		BeanUtil.setProperties(params, getFilter());

		if (TextUtils.stringSet(maxUsersPerPage)) {
			getFilter().setMax(Integer.parseInt(maxUsersPerPage));
		}

		if (getBrowsableItems().size() <= getPager().getStart()) {
			setStart("0");
		}

		requireCreatedUsersNames();

		return super.doExecute();
	}

	protected void requireCreatedUsersNames() {
		pageBuilderService.assembler().data().requireData("UserBrowser:createdUsersDisplayNames", writer -> {
			OBJECT_MAPPER.writeValue(writer, getCreatedUsersDisplayNames());
		});
	}

	public PagerFilter getPager() {
		return getFilter();
	}

	public void resetPager() {
		ActionContext.getSession().put(SessionKeys.USER_FILTER, null);
	}

	public UserBrowserFilter getFilter() {
		UserBrowserFilter filter = (UserBrowserFilter) ActionContext.getSession().get(SessionKeys.USER_FILTER);

		if (filter == null) {
			filter = new UserBrowserFilter(getLocale(), applicationRoleManager);
			ActionContext.getSession().put(SessionKeys.USER_FILTER, filter);
		}

		return filter;
	}

	public List<ApplicationUser> getCurrentPage() {
		final Set<ApplicationUser> createdUsers = getCreatedUsers().collect(CollectorsUtil.toImmutableSet());
		final Stream<ApplicationUser> currentPage = getFilter().getCurrentPage(getBrowsableItems()).stream()
				.filter(user -> !createdUsers.contains(user));

		return Stream.concat(createdUsers.stream(), currentPage).collect(CollectorsUtil.toImmutableList());
	}

	public List<ApplicationUser> getBrowsableItems() {
		if (users == null) {
			try {
				users = getFilter().getFilteredUsers();
			} catch (Exception e) {
				log.error("Exception getting users: " + e, e);
				throw new RuntimeException(e);
			}
		}

		return users;
	}

	public Iterator getGroups() {
		final GroupQuery<Group> query = new GroupQuery<Group>(Group.class, GroupType.GROUP,
				NullRestrictionImpl.INSTANCE, 0, EntityQuery.ALL_RESULTS);
		return crowdService.search(query).iterator();
	}

	public Iterator getGroupsForUser(ApplicationUser user) {
		final MembershipQuery<String> membershipQuery = QueryBuilder.queryFor(String.class, EntityDescriptor.group())
				.parentsOf(EntityDescriptor.user()).withName(user.getName()).returningAtMost(EntityQuery.ALL_RESULTS);
		return crowdService.search(membershipQuery).iterator();
	}

	public Iterator<ApplicationRole> getApplicationRolesForUser(ApplicationUser user) {
		return applicationRoleManager.getOccupiedLicenseRolesForUser(user).stream()
				.sorted(new ApplicationRoleComparator(getLocale())).iterator();
	}

	public String getDirectoryForUser(ApplicationUser user) {
		return crowdDirectoryService.findDirectoryById(user.getDirectoryId()).getName();
	}

	public boolean canUpdateUser(ApplicationUser user) {
		return userManager.canUpdateUser(user);
	}

	public Collection<ApplicationUser> getUsers() {
		return getBrowsableItems();
	}

	@Nonnull
	private Stream<ApplicationUser> getCreatedUsers() {
		return getCreatedUserNames().map(userManager::getUserByName).filter(Objects::nonNull);
	}

	private Stream<String> getCreatedUserNames() {
		if (createdUser == null) {
			return Stream.empty();
		}

		return Arrays.stream(getCreatedUser());
	}

	@Nonnull
	private List<String> getCreatedUsersDisplayNames() {
		return getCreatedUsers().map(ApplicationUser::getDisplayName).collect(CollectorsUtil.toImmutableList());
	}

	public boolean isUserFocused(ApplicationUser user) {
		return getCreatedUserNames().anyMatch(user.getUsername()::equals);
	}

	public String[] getCreatedUser() {
		return createdUser;
	}

	public void setCreatedUser(final String[] createdUser) {
		this.createdUser = createdUser;
	}

	public UserUtil getUserUtil() {
		return userUtil;
	}

	public URI getAvatarUrl(final String username) {
		return avatarService.getAvatarURL(getLoggedInUser(), username, SMALL);
	}

	private JiraHelper getJiraHelper() {
		final Map<String, Object> params = new HashMap<String, Object>();
		return new JiraHelper(ServletActionContext.getRequest(), null, params);
	}

	public static class ApplicationRoleSelectItem {
		private final String name;
		private final String value;

		public ApplicationRoleSelectItem(final String value, final String name) {
			this.value = value;
			this.name = name;
		}

		public static ApplicationRoleSelectItem of(ApplicationRole role) {
			return new ApplicationRoleSelectItem(role.getKey().value(), role.getName());
		}

		public String getValue() {
			return value;
		}

		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return new ToStringBuilder(this).append("name", name).append("value", value).toString();
		}
	}

	public static class ActiveUserSelectItem {
		private final String name;
		private final Boolean value;

		public ActiveUserSelectItem(String name, Boolean value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public Boolean getValue() {
			return value;
		}

		@Override
		public String toString() {
			return new ToStringBuilder(this).append("name", name).append("value", value).toString();
		}
	}

	public static String getActionUrl(Optional<String> queryString, Optional<String> flag) {
		return "DelegateUserBrowser.jspa" + queryString.map(s -> "?" + s).orElse("")
				+ flag.map(s -> "#" + s).orElse("");
	}

	public static String getProperties(ApplicationUser user) {
		return DelegateManager.getProperties(user);
	}
}
