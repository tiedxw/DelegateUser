package jp.ricksoft.plugins.delegate_usermanager_for_jira.action;

import static com.google.common.collect.Iterables.transform;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.atlassian.collectors.CollectorsUtil;
import com.atlassian.crowd.embedded.api.Directory;
import com.atlassian.crowd.embedded.api.DirectoryType;
import com.atlassian.crowd.embedded.api.OperationType;
import com.atlassian.crowd.embedded.impl.IdentifierUtils;
import com.atlassian.crowd.embedded.impl.ImmutableUser;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.gzipfilter.org.apache.commons.lang.StringEscapeUtils;
import com.atlassian.jira.bc.ServiceResultImpl;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.user.UserProfileUpdatedEvent;
import com.atlassian.jira.event.user.UserRenamedEvent;
import com.atlassian.jira.plugin.user.PasswordPolicyManager;
import com.atlassian.jira.plugin.user.WebErrorMessage;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.DelegatingApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.util.SimpleWarningCollection;
import com.atlassian.jira.util.WarningCollection;
import com.atlassian.jira.web.action.user.GenericEditProfile;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.net.UrlEscapers;
import com.opensymphony.util.TextUtils;

@SuppressWarnings({ "deprecation", "serial"})
public class DelegateEditUser extends GenericEditProfile {

	private final UserManager userManager;
	private final EventPublisher eventPublisher;

	private UpdateUserValidationResult updateUserValidationResult;
	private String editName;
	private ApplicationUser oldUser;
	private String rickcloud;
	private String silver;
	private String gold;
	private String evaluation;
	private String properties;

	public DelegateEditUser(UserManager userManager, UserPropertyManager userPropertyManager,
			EventPublisher eventPublisher) {
		super(userPropertyManager);
		this.userManager = userManager;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public String doDefault() throws Exception {
		ApplicationUser editedUser = getEditedUser();
		if (editedUser != null) {
			this.rickcloud = DelegateManager.getUserInRickCloud(editedUser);
			this.silver = DelegateManager.getUserInSilverSupport(editedUser);
			this.gold = DelegateManager.getUserInGoldSupport(editedUser);
			this.evaluation = DelegateManager.getUserInEvaluationSupport(editedUser);
			this.properties = DelegateManager.getProperties(editedUser);
		}
		return super.doDefault();
	}

	public void doValidation() {
		super.doValidation();
		final ApplicationUser newUser = buildNewUser();

		updateUserValidationResult = validateUpdateUser(newUser);
		addErrorCollection(updateUserValidationResult.getErrorCollection());
	}

	private ApplicationUser buildNewUser() {
		ApplicationUser editedUser = getEditedUser();
		ImmutableUser.Builder builder = ImmutableUser.newUser(editedUser.getDirectoryUser());
		if (showRenameUser()) {
			builder.name(getUsername());
		}
		builder.displayName(getFullName());
		builder.emailAddress(StringUtils.trim(getEmail()));
		if (showActiveCheckbox()) {
			builder.active(isActive());
		}
		return new DelegatingApplicationUser(editedUser.getId(), editedUser.getKey(), builder.toUser());
	}

	@RequiresXsrfCheck
	protected String doExecute() throws Exception {
		updateUser(updateUserValidationResult);
		ApplicationUser user = updateUserValidationResult.getApplicationUser();
		if (user != null) {
			DelegateManager.putUserToGroups(user, getRickcloud(), getSilver(), getGold(), getEvaluation());
			DelegateManager.putProperties(user, getProperties());
		}
		String result = getResult();
		if (SUCCESS.equals(result)) {
			return returnCompleteWithInlineRedirectAndMsg(userBrowserUrl(),
					StringEscapeUtils.escapeHtml(getFullName() + " を更新しました"), MessageType.SUCCESS, false, null);
		}

		return result;
	}

	private String userBrowserUrl() {
		return DelegateUserBrowser.getActionUrl(Optional.of(Joiner.on("&").join(updatedUsersWithCurrentUser())),
				Optional.of("userUpdatedFlag"));
	}

	@VisibleForTesting
	protected List<String> updatedUsers() {
		if (editName == null) {
			return ImmutableList.of();
		}
		String[] updatedUsers = new String[] { editName };
		return Arrays.stream(updatedUsers).map(SAFE_EDIT_USER_PARAM).collect(CollectorsUtil.toImmutableList());
	}

	private List<String> updatedUsersWithCurrentUser() {
		return Stream.concat(updatedUsers().stream(), Stream.of(SAFE_UPDATED_USER_PARAM.apply(getUsername())))
				.collect(CollectorsUtil.toImmutableList());
	}

	private static final Function<String, String> SAFE_EDIT_USER_PARAM = s -> "editUser="
			+ UrlEscapers.urlFormParameterEscaper().escape(s);

	private static final Function<String, String> SAFE_UPDATED_USER_PARAM = s -> "updatedUser="
			+ UrlEscapers.urlFormParameterEscaper().escape(s);

	public String getEditName() {
		return editName;
	}

	public void setEditName(String editName) {
		this.editName = editName;
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

	public boolean showActiveCheckbox() {
		final Directory directory = userManager.getDirectory(getEditedUser().getDirectoryId());
		return directory.getType() != DirectoryType.CONNECTOR;
	}

	public boolean showRenameUser() {
		return userManager.canRenameUser(getEditedUser());
	}

	@Override
	public ApplicationUser getEditedUser() {
		if (oldUser == null) {
			oldUser = userManager.getUserByName(editName);
		}
		return oldUser;
	}

	/**
	 * @see jira-project/jira-components/jira-api/src/main/java/com/atlassian/jira/bc/user/UserService.java
	 *      jira-project/jira-components/jira-api/src/main/java/com/atlassian/jira/bc/user/DefaultUserService.java
	 * @author ohsaki.kengo
	 *
	 */
	public UpdateUserValidationResult validateUpdateUser(ApplicationUser user) {
		final ApplicationUser loggedInUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
		final I18nHelper i18nBean = ComponentAccessor.getI18nHelperFactory().getInstance(loggedInUser);
		final ErrorCollection errors = new SimpleErrorCollection();
		final UserUtil userUtil = ComponentAccessor.getUserUtil();

		if (isUpdateUser(loggedInUser) == false) {
			errors.addErrorMessage(i18nBean.getText("admin.errors.users.update.no.permission"));
			return new UpdateUserValidationResult(errors);
		}
		ApplicationUser userToUpdate = userManager.getUserByKey(user.getKey());
		if (userToUpdate == null) {
			errors.addErrorMessage(i18nBean.getText("admin.errors.users.user.does.not.exist"));
			return new UpdateUserValidationResult(errors);
		}
		if (userManager.canUpdateUser(userToUpdate) == false) {
			errors.addErrorMessage(i18nBean.getText("admin.errors.cannot.edit.user.directory.read.only"));
			return new UpdateUserValidationResult(errors);
		}
		if (isSysAdmin(loggedInUser) == false && isSysAdmin(userToUpdate)) {
			errors.addErrorMessage(i18nBean.getText("admin.errors.must.be.sysadmin.to.edit.sysadmin"));
			return new UpdateUserValidationResult(errors);
		}
		if (isAdministrator(loggedInUser) == false && isSysAdmin(loggedInUser) == false
				&& (isAdministrator(userToUpdate) || isSysAdmin(userToUpdate))) {
			errors.addErrorMessage(i18nBean.getText("admin.errors.must.be.sysadmin.to.edit.sysadmin"));
			return new UpdateUserValidationResult(errors);
		}

		if (user.isActive() == false) {
			final Collection<ProjectComponent> components = userUtil.getComponentsUserLeads(userToUpdate);
			if (components.size() > 0) {
				String projectList = getDisplayableProjectList(getProjectsFor(components));
				errors.addError("active",
						i18nBean.getText("admin.errors.users.cannot.deactivate.due.to.component.lead", projectList));
			}

			Collection<Project> projects = userUtil.getProjectsLeadBy(userToUpdate);
			if (projects.size() > 0) {
				String projectList = getDisplayableProjectList(projects);
				errors.addError("active",
						i18nBean.getText("admin.errors.users.cannot.deactivate.due.to.project.lead", projectList));
			}

			if (loggedInUser.getName().equalsIgnoreCase(user.getUsername())) {
				errors.addErrorMessage(i18nBean.getText("admin.errors.users.cannot.deactivate.currently.logged.in"));
			}
		}

		if (IdentifierUtils.equalsInLowerCase(userToUpdate.getUsername(), user.getUsername()) == false) {
			if (userManager.canRenameUser(userToUpdate)) {
				final UserValidationHelper validationsHelper = new UserValidationHelper(
						ComponentAccessor.getI18nHelperFactory(), ComponentAccessor.getJiraAuthenticationContext(),
						ComponentAccessor.getPermissionManager(), userManager,
						ComponentAccessor.getComponent(PasswordPolicyManager.class));
				final DelegateEditUser.UserValidationHelper.Validations validations = validationsHelper
						.validations(loggedInUser);
				if (validations.hasValidUsername(user.getUsername(), null) == false) {
					errors.addErrors(validations.getErrors().getErrors());
				}
			} else {
				errors.addErrorMessage(i18nBean.getText("admin.errors.cannot.rename.due.to.configuration"));
			}

		}

		if (errors.hasAnyErrors()) {
			return new UpdateUserValidationResult(errors);
		} else {
			return new UpdateUserValidationResult(user);
		}
	}

	private void updateUser(UpdateUserValidationResult updateUserValidationResult) {
		if (updateUserValidationResult.isValid()) {
			final ApplicationUser oldUser = userManager
					.getUserByKey(updateUserValidationResult.getApplicationUser().getKey());
			userManager.updateUser(updateUserValidationResult.getApplicationUser());
			if (IdentifierUtils.equalsInLowerCase(oldUser.getUsername(),
					updateUserValidationResult.getApplicationUser().getUsername())) {
				eventPublisher.publish(new UserProfileUpdatedEvent(updateUserValidationResult.getApplicationUser(),
						ComponentAccessor.getJiraAuthenticationContext().getUser()));
			} else {
				eventPublisher.publish(new UserRenamedEvent(updateUserValidationResult.getApplicationUser(),
						ComponentAccessor.getJiraAuthenticationContext().getUser(), oldUser.getUsername()));
			}
		} else {
			throw new IllegalStateException("Invalid UpdateUserValidationResult");
		}
	}

	private Collection<Project> getProjectsFor(Collection<ProjectComponent> components) {
		ProjectManager projectManager = ComponentAccessor.getProjectManager();
		HashSet<Project> projects = new HashSet<Project>(components.size());
		for (ProjectComponent component : components) {
			projects.add(projectManager.getProjectObj(component.getProjectId()));
		}
		return projects;
	}

	private boolean isUpdateUser(ApplicationUser user) {
		return ComponentAccessor.getPermissionManager().hasPermission(Permissions.USER_PICKER, user);
	}

	private boolean isAdministrator(ApplicationUser user) {
		return ComponentAccessor.getPermissionManager().hasPermission(Permissions.ADMINISTER, user);
	}

	private boolean isSysAdmin(ApplicationUser user) {
		return ComponentAccessor.getPermissionManager().hasPermission(Permissions.SYSTEM_ADMIN, user);
	}

	private String getDisplayableProjectList(Collection<Project> projects) {
		final Iterable<String> projectKeys = transform(projects, Project::getKey);
		return StringUtils.join(projectKeys, ", ");
	}

	/**
	 * @see copy from
	 *      jira-project/jira-components/jira-core/src/main/java/com/atlassian/jira/bc/user/UserService.java
	 * @author ohsaki.kengo
	 *
	 */
	@SuppressWarnings("unused")
	private class UpdateUserValidationResult extends ServiceResultImpl {
		private final ApplicationUser user;

		UpdateUserValidationResult(ErrorCollection errorCollection) {
			super(errorCollection);
			user = null;
		}

		UpdateUserValidationResult(final ApplicationUser user) {
			super(new SimpleErrorCollection());
			this.user = user;
		}

		public ApplicationUser getUser() {
			return user;
		}

		public ApplicationUser getApplicationUser() {
			return user;
		}
	}

	/**
	 * @see copy from
	 *      jira-project/jira-components/jira-core/src/main/java/com/atlassian/jira/bc/user/UserValidationHelper.java
	 * @author ohsaki.kengo
	 *
	 */
	@SuppressWarnings("unused")
	private class UserValidationHelper {
		private final I18nHelper.BeanFactory i18nFactory;
		private final JiraAuthenticationContext jiraAuthenticationContext;
		private final PermissionManager permissionManager;
		private final UserManager userManager;
		private final PasswordPolicyManager passwordPolicyManager;

		public UserValidationHelper(final I18nHelper.BeanFactory i18nFactory,
				final JiraAuthenticationContext jiraAuthenticationContext, final PermissionManager permissionManager,
				final UserManager userManager, final PasswordPolicyManager passwordPolicyManager) {
			this.i18nFactory = i18nFactory;
			this.jiraAuthenticationContext = jiraAuthenticationContext;
			this.permissionManager = permissionManager;
			this.userManager = userManager;
			this.passwordPolicyManager = passwordPolicyManager;
		}

		/**
		 * Create a validations object using the provided users locale. Each validation
		 * adds an error to the {@link Validations#getErrors()} collection.
		 *
		 * @return a validations object that contains user related validations.
		 */
		public Validations validations(ApplicationUser applicationUser) {
			return new Validations(Optional.ofNullable(applicationUser));
		}

		public class Validations {
			private final int MAX_FIELD_LENGTH = 255;
			private final char[] INVALID_USERNAME_CHARS = { '<', '>', '&' };

			private final ErrorCollection errors;
			private final WarningCollection warnings;
			private final I18nHelper i18nBean;

			public Validations(Optional<ApplicationUser> applicationUser) {
				errors = new SimpleErrorCollection();
				warnings = new SimpleWarningCollection();
				if (applicationUser.isPresent()) {
					i18nBean = i18nFactory.getInstance(applicationUser.get());
				} else {
					i18nBean = jiraAuthenticationContext.getI18nHelper();
				}
			}

			public ErrorCollection getErrors() {
				return errors;
			}

			public WarningCollection getWarnings() {
				return warnings;
			}

			private void addErrorMessage(String i18nKey) {
				errors.addErrorMessage(i18nBean.getText(i18nKey));
			}

			private void addErrorMessage(String i18nKey, Object param) {
				errors.addErrorMessage(i18nBean.getText(i18nKey, param));
			}

			private void addError(final String fieldName, String i18nKey) {
				errors.addError(fieldName, i18nBean.getText(i18nKey));
			}

			/**
			 * Determine whether there is a writable user directory.
			 *
			 * @return true if there is a writable user directory else false.
			 */
			public boolean hasWritableDirectory() {
				if (!userManager.hasWritableDirectory()) {
					addErrorMessage("admin.errors.cannot.add.user.all.directories.read.only");
					return false;
				}
				return true;
			}

			public boolean writableDirectory(final Long directoryId) {
				Directory directory = userManager.getDirectory(directoryId);
				if (directory == null) {
					addErrorMessage("admin.errors.cannot.add.user.no.such.directory", directoryId);
					return false;
				} else {
					if (!directory.getAllowedOperations().contains(OperationType.CREATE_USER)) {
						addErrorMessage("admin.errors.cannot.add.user.read.only.directory", directory.getName());
						return false;
					}
				}
				return true;
			}

			public boolean hasCreateAccess(final ApplicationUser userPerformingCreate) {
				if (!permissionManager.hasPermission(Permissions.ADMINISTER, userPerformingCreate)) {
					addErrorMessage("admin.errors.user.no.permission.to.create");
					return false;
				}
				return true;
			}

			public boolean passwordRequired(final String password, final boolean shouldConfirmPassword) {
				if (StringUtils.isEmpty(password)) {
					final String errorI18nKey = shouldConfirmPassword ? "signup.error.password.required"
							: "signup.error.password.required.without.confirmation";
					errors.addError(FieldName.PASSWORD, i18nBean.getText(errorI18nKey));
					return false;
				}
				return true;
			}

			public boolean validateConfirmPassword(final String password, final String confirmPassword) {
				// If a password has been specified then we need to check they
				// are the same
				// else there is no password specified then check to see if we
				// need one.
				if (StringUtils.isNotEmpty(confirmPassword) || StringUtils.isNotEmpty(password)) {
					if (password == null || !password.equals(confirmPassword)) {
						addError(FieldName.CONFIRM_PASSWORD, "signup.error.password.mustmatch");
						return false;
					}
				}
				return true;
			}

			/**
			 * Validate that the provided password conforms to the password policy.
			 *
			 * @param password
			 *            the user password.
			 * @param username
			 *            the username.
			 * @param displayName
			 *            the user display name.
			 * @param email
			 *            the user email address.
			 * @return error messages if password has been rejected else an empty list.
			 */
			public List<WebErrorMessage> validatePasswordPolicy(final String password, String username,
					String displayName, String email) {
				final Collection<WebErrorMessage> webErrorMessages = passwordPolicyManager.checkPolicy(username,
						displayName, email, password);
				if (!webErrorMessages.isEmpty()) {
					addError(FieldName.PASSWORD, "signup.error.password.rejected");
				}
				return ImmutableList.copyOf(webErrorMessages);
			}

			/**
			 * Validate required user email address.
			 *
			 * @param emailAddress
			 *            user email address.
			 * @return true if user email address has been provided and match email address
			 *         policy.
			 */
			public boolean validateEmailAddress(final String emailAddress) {
				if (StringUtils.isEmpty(emailAddress)) {
					addError(FieldName.EMAIL, "signup.error.email.required");
					return false;
				} else if (emailAddress.length() > MAX_FIELD_LENGTH) {
					addError(FieldName.EMAIL, "signup.error.email.greater.than.max.chars");
					return false;
				} else if (!TextUtils.verifyEmail(emailAddress)) {
					addError(FieldName.EMAIL, "signup.error.email.valid");
					return false;
				}
				return true;
			}

			/**
			 * Validate required user display name.
			 *
			 * @param displayName
			 *            user display name.
			 * @return true if the user display name is valid else false.
			 */
			public boolean validateDisplayName(final String displayName) {
				if (StringUtils.isEmpty(displayName)) {
					addError(FieldName.FULLNAME, "signup.error.fullname.required");
					return false;
				} else if (displayName.length() > MAX_FIELD_LENGTH) {
					addError(FieldName.FULLNAME, "signup.error.full.name.greater.than.max.chars");
					return false;
				}
				return true;
			}

			/**
			 * Validate the provided username. Make sure that username has been provided and
			 * that it meets the username policy. This would also validate that the username
			 * is not already in use.
			 *
			 * @param username
			 *            new username.
			 * @param directoryId
			 *            id used to identify user directory, default user directory is
			 *            indicated by providing {@link null}.
			 * @return true if the username is valid else false.
			 */
			public boolean hasValidUsername(final String username, final Long directoryId) {
				if (hasRequiredUsername(username)) {
					if (validateUsernamePolicy(username)) {
						return usernameDoesNotExist(directoryId, username);
					}
				}
				return false;
			}

			/**
			 * Determine if a username has been provided.
			 *
			 * @param username
			 *            new username.
			 * @return true if the username has been provided.
			 */
			public boolean hasRequiredUsername(final String username) {
				if (StringUtils.isEmpty(username)) {
					addError(FieldName.NAME, "signup.error.username.required");
					return false;
				}
				return true;
			}

			/**
			 * Validate username to ensure that username meet username policy.
			 *
			 * @param username
			 *            username that should meet username policy.
			 * @return true if specified username meet username policy else false.
			 */
			public boolean validateUsernamePolicy(final String username) {
				if (username.length() > MAX_FIELD_LENGTH) {
					addError(FieldName.NAME, "signup.error.username.greater.than.max.chars");
					return false;
				} else if (StringUtils.containsAny(username, INVALID_USERNAME_CHARS)) {
					addError(FieldName.NAME, "signup.error.username.invalid.chars");
					return false;
				}
				return true;
			}

			/**
			 * Validate that provided username does not already exist.
			 *
			 * @param directoryId
			 *            id used to identify user directory, default user directory is
			 *            indicated by providing {@link null}.
			 * @param username
			 *            the username that should not exist in the specified user
			 *            directory.
			 * @return false if the username is already in use else true.
			 */
			public boolean usernameDoesNotExist(Long directoryId, final String username) {
				if (directoryId != null) {
					// Check if the username exists in the given directory - we
					// allow duplicates in other directories
					if (userManager.findUserInDirectory(username, directoryId) != null) {
						addError(FieldName.NAME, "signup.error.username.exists");
						return false;
					}
					return true;
				} else {
					// Check if the username exists in any directory
					if (userManager.getUserByName(username) != null) {
						addError(FieldName.NAME, "signup.error.username.exists");
						return false;
					}
					return true;
				}
			}

			/**
			 * Validate whether this JIRA Instance has a default directory that is writable
			 * for create user.
			 *
			 * @return true if this instance has a writable directory, false if this
			 *         instance does not have a writable directory.
			 */
			public boolean hasWritableDefaultCreateDirectory() {
				final Optional<Directory> defaultDirectory = userManager.getDefaultCreateDirectory();
				if (!defaultDirectory.isPresent()) {
					addErrorMessage("admin.errors.cannot.add.user.all.directories.read.only");
					return false;
				}
				return true;
			}
		}
	}

	/**
	 * @see copy from
	 *      jira-project/jira-components/jira-api/src/main/java/com/atlassian/jira/bc/user/UserService.java
	 * @author ohsaki.kengo
	 *
	 */
	private static final class FieldName {
		private FieldName() {
		}

		/**
		 * The default name of HTML fields containing a User's email. Validation methods
		 * on this service will return an
		 * {@link com.atlassian.jira.util.ErrorCollection} with error messages keyed to
		 * this field name.
		 */
		static String EMAIL = "email";
		/**
		 * The default name of HTML fields containing a User's username. Validation
		 * methods on this service will return an
		 * {@link com.atlassian.jira.util.ErrorCollection} with error messages keyed to
		 * this field name.
		 */
		static String NAME = "username";
		/**
		 * The default name of HTML fields containing a User's full name. Validation
		 * methods on this service will return an
		 * {@link com.atlassian.jira.util.ErrorCollection} with error messages keyed to
		 * this field name.
		 */
		static String FULLNAME = "fullname";
		/**
		 * The default name of HTML fields containing a User's password. Validation
		 * methods on this service will return an
		 * {@link com.atlassian.jira.util.ErrorCollection} with error messages keyed to
		 * this field name.
		 */
		static String PASSWORD = "password";
		/**
		 * The default name of HTML fields containing a User's password confirmation.
		 * Validation methods on this service will return an
		 * {@link com.atlassian.jira.util.ErrorCollection} with error messages keyed to
		 * this field name.
		 */
		static String CONFIRM_PASSWORD = "confirm";
	}
}
