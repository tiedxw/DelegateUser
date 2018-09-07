package jp.ricksoft.plugins.delegate_usermanager_for_jira.action;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.jira.util.json.JSONException;
import java.util.*;

@Component
public class DelegateListener implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(DelegateListener.class);
    
	private static final String PUBLIC_COMMENT_KEY = "sd.public.comment";
	
	
    @JiraImport
    private final EventPublisher eventPublisher;

    @Autowired
    public DelegateListener(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Called when the plugin has been enabled.
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Enabling plugin");
        eventPublisher.register(this);
    }

    /**
     * Called when the plugin is being disabled or removed.
     * @throws Exception
     */
    @Override
    public void destroy() throws Exception {
        log.info("Disabling plugin");
        eventPublisher.unregister(this);
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        Long eventTypeId = issueEvent.getEventTypeId();
        Issue issue = issueEvent.getIssue();
        CommentManager commentManager = ComponentAccessor.getCommentManager();
        UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();
        GroupManager groupManager = ComponentAccessor.getGroupManager();
        
        
        /*
         * 実際にコメントするユーザー
         * プロジェクトリーダー
         */
        ApplicationUser projectLeader = issue.getProjectObject().getProjectLead();
        
        ApplicationUser reporter = issue.getReporter();
        //PropertySet properties = userPropertyManager.getPropertySet(reporter);
        
        if (eventTypeId.equals(EventType.ISSUE_CREATED_ID)) {
            
            /*
             * 内部コメントプロパティの作成
             */
            JSONObject internalJsonObject = new JSONObject();
            try {
            	internalJsonObject.put("internal", true);
            }catch(JSONException e) {
            	e.printStackTrace();
            }
            Map<String, JSONObject> internalProperty = new HashMap<>();
            internalProperty.put(PUBLIC_COMMENT_KEY, internalJsonObject);
            
            /*
             * グループをチェックしてコメントを作成
             */
            Collection<String> groups = groupManager.getGroupNamesForUser(reporter);
            StringBuilder sb = new StringBuilder();
            for (String group : groups) {
                if (group.equals(DelegateManager.getGoldSupportUsresGruopName())) {
                    sb.append("ゴールドサポートご利用です : ").append(DelegateManager.getProperties(reporter)).append("\n");
                } else if (group.equals(DelegateManager.getSilverSupportUsresGruopName())) {
                    sb.append("シルバーサポートご利用です : ").append(DelegateManager.getProperties(reporter)).append("\n");
                } else if (group.equals(DelegateManager.getRickcloudUsresGruopName())) {
                    sb.append("リッククラウドご利用です : ").append("\n");
                } else if (group.equals(DelegateManager.getEvaluationSupportUsresGruopName())) {
                    sb.append("評価利用です ").append("\n");
                }
            }
            
            if (sb.toString().length() > 0) {
            	commentManager.create(issue, projectLeader, sb.toString(), null, null, new Date(), internalProperty, true);
            }   
        }
    }
}