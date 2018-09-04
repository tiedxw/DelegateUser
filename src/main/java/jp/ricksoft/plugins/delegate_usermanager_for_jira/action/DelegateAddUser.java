package jp.ricksoft.plugins.delegate_usermanager_for_jira.action;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.atlassian.collectors.CollectorsUtil;
import com.atlassian.crowd.embedded.api.Directory;
import com.atlassian.crowd.embedded.api.DirectoryType;
import com.atlassian.crowd.exception.runtime.GroupNotFoundException;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.gzipfilter.org.apache.commons.lang.StringEscapeUtils;
import com.atlassian.jira.bc.user.UserService;
import com.atlassian.jira.bc.user.UserService.CreateUserRequest;
import com.atlassian.jira.event.web.action.admin.UserAddedEvent;
import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.net.UrlEscapers;

@SuppressWarnings({ "deprecation", "unchecked", "serial", "static-access" })
public class DelegateAddUser extends JiraWebActionSupport {

	private final UserService userService;
	private final UserManager userManager;
	private final EventPublisher eventPublisher;

	private String username;
	private String fullname;
	private String email;
	private String rickcloud;
	private String silver;
	private String gold;
	private String evaluation;
	private String properties;

	private UserService.CreateUserValidationResult result;
	private String[] createdUser;

	public DelegateAddUser(UserService userService, UserManager userManager, EventPublisher eventPublisher) {
		this.userService = userService;
		this.userManager = userManager;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public String doDefault() {
		if (getDirectoryId() == null) {
			addError("username", getText("admin.errors.user.cannot.create"));
		}
		return INPUT;
	}

	protected void doValidation() {
		final CreateUserRequest createUserRequest = CreateUserRequest
				.withUserDetails(getLoggedInUser(), getUsername(), null, getEmail(), getFullname())
				.inDirectory(getDirectoryId()).withNoApplicationAccess().performPermissionCheck(false)
				.sendNotification(true);
		result = userService.validateCreateUser(createUserRequest);
		if (result.isValid() == false) {
			addErrorCollection(result.getErrorCollection());
		}
	}

	@Override
	@RequiresXsrfCheck
	protected String doExecute() {
		try {
			// ユーザー作成
			ApplicationUser user = userService.createUser(result);
			if (user != null) {
				// グループ設定
				DelegateManager.postUserToGroups(user, getRickcloud(), getSilver(), getGold(), getEvaluation());
				// プロパティ設定(暗号化して設定)
				DelegateManager.postProperties(user, getProperties());
			}
			eventPublisher.publish(new UserAddedEvent(request.getParameterMap()));
		} catch (PermissionException e) {
			addError("username", getText("admin.errors.user.no.permission.to.create"));
		} catch (CreateException e) {
			addError("username", getText("admin.errors.user.cannot.create", e.getMessage()));
		} catch (GroupNotFoundException e) {
			final String directoryName = userManager.getDirectory(getDirectoryId()).getName();
			final String warningMessage = getText("admin.warn.user.create.no.group", getUsername(), e.getMessage(),
					directoryName);
			return returnCompleteWithInlineRedirectAndMsg(userBrowserUrl(),
					StringEscapeUtils.escapeHtml(warningMessage), MessageType.WARNING, true, null);
		}

		if (getHasErrorMessages()) {
			return ERROR;
		} else {
			return returnCompleteWithInlineRedirectAndMsg(userBrowserUrl(),
					StringEscapeUtils.escapeHtml(getFullname() + " が作成されました"), MessageType.SUCCESS, false, null);
		}
	}

	private String userBrowserUrl() {
		return DelegateUserBrowser.getActionUrl(Optional.of(Joiner.on("&").join(createdUsersWithCurrentUser())),
				Optional.of("userCreatedFlag"));
	}

	@VisibleForTesting
	protected List<String> createdUsers() {
		if (createdUser == null) {
			return ImmutableList.of();
		}
		return Arrays.stream(createdUser).map(SAFE_CREATED_USER_PARAM).collect(CollectorsUtil.toImmutableList());
	}

	private List<String> createdUsersWithCurrentUser() {
		return Stream.concat(createdUsers().stream(), Stream.of(SAFE_CREATED_USER_PARAM.apply(getUsername())))
				.collect(CollectorsUtil.toImmutableList());
	}

	private static final Function<String, String> SAFE_CREATED_USER_PARAM = s -> "createdUser="
			+ UrlEscapers.urlFormParameterEscaper().escape(s);

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = StringUtils.trim(username);
	}

	public String getFullname() {
		return fullname;
	}

	public void setFullname(String fullname) {
		this.fullname = fullname;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = StringUtils.trim(email);
	}

	public String getRickcloud() {
		return rickcloud;
	}

	public void setRickcloud(String rickcloud) {
		this.rickcloud = StringUtils.trim(rickcloud);
	}

	public String getSilver() {
		return silver;
	}

	public void setSilver(String silver) {
		this.silver = StringUtils.trim(silver);
	}

	public String getGold() {
		return gold;
	}

	public void setGold(String gold) {
		this.gold = StringUtils.trim(gold);
	}

	public String getEvaluation() {
		return evaluation;
	}

	public void setEvaluation(String evaluation) {
		this.evaluation = StringUtils.trim(evaluation);
	}

	public String getProperties() {
		return properties;
	}

	public void setProperties(String properties) {
		this.properties = StringUtils.trim(properties);
	}

	public Long getDirectoryId() {
		if (userManager.hasWritableDirectory()) {
			for (Directory directory : userManager.getWritableDirectories()) {
				if (directory.getType().INTERNAL.equals(DirectoryType.INTERNAL)) {
					return directory.getId();
				}
			}
		}
		return null;
	}

}
