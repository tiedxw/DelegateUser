package jp.ricksoft.plugins.delegate_usermanager_for_jira.action;

import static com.atlassian.jira.avatar.Avatar.Size.SMALL;

import java.net.URI;

import com.atlassian.jira.auditing.AuditingFilter;
import com.atlassian.jira.auditing.AuditingManager;
import com.atlassian.jira.auditing.Records;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.web.action.JiraWebActionSupport;

public class DelegateAuditUser extends JiraWebActionSupport {

	private String userName;
	private AvatarService avatarService;
	private AuditingManager auditingManager;
	private Records records;

	public DelegateAuditUser(AvatarService avatarService) {
		this.avatarService = avatarService;
		this.auditingManager = ComponentAccessor.getComponent(AuditingManager.class);
	}

	@Override
	public String doDefault() {
		AuditingFilter.Builder builder = AuditingFilter.builder();
		String name = getUserName();
		if (name != null && name.length() > 0) {
			builder.filter("management");
			builder.filter(name);
			records = auditingManager.getRecords(Long.MAX_VALUE, Long.MIN_VALUE, Integer.MAX_VALUE, 0, builder.build());
		} else {
			records = null;
		}
		return SUCCESS;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public Records getRecords() {
		return records;
	}

	public URI getAvatarUrl(final String username) {
		return avatarService.getAvatarURL(getLoggedInUser(), username, SMALL);
	}

}
