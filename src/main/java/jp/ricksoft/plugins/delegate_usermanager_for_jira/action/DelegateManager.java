package jp.ricksoft.plugins.delegate_usermanager_for_jira.action;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.AddException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.jira.user.util.UserUtil;
import com.opensymphony.module.propertyset.PropertySet;

public class DelegateManager {

	private static final String RICKCLOUD_USRES_GRUOP_NAME = "rickcloud-users";
	private static final String SILVER_SUPPORT_USRES_GRUOP_NAME = "silver-support-users";
	private static final String GOLD_SUPPORT_USRES_GRUOP_NAME = "gold-support-users";
	private static final String EVALUATION_SUPPORT_USRES_GRUOP_NAME = "evaluation-support-users";
	private static final String PROPERTIES_PREFIX_NAME = "jira.meta.support_property";
	private static final String GROUP_CHECK_NAME = "true";

	private static final int PROPERTIES_MAX_PER_LENGTH = 200;

	private static final String ENCRYPT_KEY = "DRK9nbYruScEzwFX";
	private static final String ENCRYPT_ALGORITHM = "AES";

	public static List<String> splitByLength(String s, int length) {
		List<String> list = new ArrayList<>();
		if (!StringUtils.isEmpty(s)) {
			Matcher m = Pattern.compile("[\\s\\S]{1," + length + "}").matcher(s);
			while (m.find()) {
				list.add(m.group());
			}
		}
		return list;
	}

	public static String getUserInRickCloud(ApplicationUser user) {
		GroupManager groupManager = ComponentAccessor.getGroupManager();
		Group rickcloudUsersGroup = groupManager.getGroup(RICKCLOUD_USRES_GRUOP_NAME);
		if (groupManager.isUserInGroup(user, rickcloudUsersGroup)) {
			return GROUP_CHECK_NAME;
		}
		return null;
	}

	public static String getUserInSilverSupport(ApplicationUser user) {
		GroupManager groupManager = ComponentAccessor.getGroupManager();
		Group silverSupportUsersGroup = groupManager.getGroup(SILVER_SUPPORT_USRES_GRUOP_NAME);
		if (groupManager.isUserInGroup(user, silverSupportUsersGroup)) {
			return GROUP_CHECK_NAME;
		}
		return null;
	}

	public static String getUserInGoldSupport(ApplicationUser user) {
		GroupManager groupManager = ComponentAccessor.getGroupManager();
		Group goldSupportUsersGroup = groupManager.getGroup(GOLD_SUPPORT_USRES_GRUOP_NAME);
		if (groupManager.isUserInGroup(user, goldSupportUsersGroup)) {
			return GROUP_CHECK_NAME;
		}
		return null;
	}

	public static String getUserInEvaluationSupport(ApplicationUser user) {
		GroupManager groupManager = ComponentAccessor.getGroupManager();
		Group evaluationSupportUsersGroup = groupManager.getGroup(EVALUATION_SUPPORT_USRES_GRUOP_NAME);
		if (groupManager.isUserInGroup(user, evaluationSupportUsersGroup)) {
			return GROUP_CHECK_NAME;
		}
		return null;
	}

	public static void postUserToGroups(ApplicationUser user, String rickcloud, String silver, String gold,
			String evaluation) {
		GroupManager groupManager = ComponentAccessor.getGroupManager();
		UserUtil userUtil = ComponentAccessor.getUserUtil();
		try {
			if (rickcloud != null && rickcloud.equals(GROUP_CHECK_NAME)) {
				Group rickcloudUsersGroup = groupManager.getGroup(RICKCLOUD_USRES_GRUOP_NAME);
				userUtil.addUserToGroup(rickcloudUsersGroup, user);
			}

			if (silver != null && silver.equals(GROUP_CHECK_NAME)) {
				Group silverSupportUsersGroup = groupManager.getGroup(SILVER_SUPPORT_USRES_GRUOP_NAME);
				userUtil.addUserToGroup(silverSupportUsersGroup, user);
			}
			if (gold != null && gold.equals(GROUP_CHECK_NAME)) {
				Group goldSupportUsersGroup = groupManager.getGroup(GOLD_SUPPORT_USRES_GRUOP_NAME);
				userUtil.addUserToGroup(goldSupportUsersGroup, user);
			}
			if (evaluation != null && evaluation.equals(GROUP_CHECK_NAME)) {
				Group evaluationSupportUsersGroup = groupManager.getGroup(EVALUATION_SUPPORT_USRES_GRUOP_NAME);
				userUtil.addUserToGroup(evaluationSupportUsersGroup, user);
			}
		} catch (PermissionException | AddException e) {
			e.printStackTrace();
		}
	}

	public static void deleteUserToGroups(ApplicationUser user, String rickcloud, String silver, String gold,
			String evaluation) {
		GroupManager groupManager = ComponentAccessor.getGroupManager();
		UserUtil userUtil = ComponentAccessor.getUserUtil();
		try {
			if (rickcloud == null || rickcloud.equals(GROUP_CHECK_NAME) == false) {
				Group rickcloudUsersGroup = groupManager.getGroup(RICKCLOUD_USRES_GRUOP_NAME);
				if (groupManager.isUserInGroup(user, rickcloudUsersGroup)) {
					userUtil.removeUserFromGroup(rickcloudUsersGroup, user);
				}
			}

			if (silver == null || silver.equals(GROUP_CHECK_NAME) == false) {
				Group silverSupportUsersGroup = groupManager.getGroup(SILVER_SUPPORT_USRES_GRUOP_NAME);
				if (groupManager.isUserInGroup(user, silverSupportUsersGroup)) {
					userUtil.removeUserFromGroup(silverSupportUsersGroup, user);
				}
			}

			if (gold == null || gold.equals(GROUP_CHECK_NAME) == false) {
				Group goldSupportUsersGroup = groupManager.getGroup(GOLD_SUPPORT_USRES_GRUOP_NAME);
				if (groupManager.isUserInGroup(user, goldSupportUsersGroup)) {
					userUtil.removeUserFromGroup(goldSupportUsersGroup, user);
				}
			}

			if (evaluation == null || evaluation.equals(GROUP_CHECK_NAME) == false) {
				Group evaluationSupportUsersGroup = groupManager.getGroup(EVALUATION_SUPPORT_USRES_GRUOP_NAME);
				if (groupManager.isUserInGroup(user, evaluationSupportUsersGroup)) {
					userUtil.removeUserFromGroup(evaluationSupportUsersGroup, user);
				}
			}
		} catch (PermissionException | RemoveException e) {
			e.printStackTrace();
		}
	}

	public static void putUserToGroups(ApplicationUser user, String rickcloud, String silver, String gold,
			String evaluation) {
		deleteUserToGroups(user, rickcloud, silver, gold, evaluation);
		postUserToGroups(user, rickcloud, silver, gold, evaluation);
	}

	public static String getProperties(ApplicationUser user) {
		UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();
		StringBuilder sb = new StringBuilder();
		try {
			PropertySet propertySet = userPropertyManager.getPropertySet(user);
			if (propertySet != null) {
				String sizeString = propertySet.getString(PROPERTIES_PREFIX_NAME);
				if (sizeString != null) {
					Long size = Long.parseLong(decrypt(sizeString));
					StringBuilder encryptStringBuilder = new StringBuilder();
					for (long index = 1; index <= size; index++) {
						String s = propertySet.getString(PROPERTIES_PREFIX_NAME + index);
						if (s != null && s.length() > 0) {
							encryptStringBuilder.append(s);
						}
					}
					if (encryptStringBuilder.length() > 0) {
						sb.append(decrypt(encryptStringBuilder.toString()));
					}
				}
			}
		} catch (NumberFormatException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException
				| IllegalBlockSizeException | BadPaddingException e) {
			e.printStackTrace();
		}
		return sb.toString();
	}

	public static void postProperties(ApplicationUser user, String properties) {
		UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();
		if (properties != null && properties.length() > 0) {
			try {
				String encryptProperties = encrypt(properties);
				List<String> split = splitByLength(encryptProperties, PROPERTIES_MAX_PER_LENGTH);
				String encryptPropertiesSize = encrypt(String.valueOf(split.size()));
				PropertySet propertySet = userPropertyManager.getPropertySet(user);
				if (propertySet != null) {
					long index = 1;
					for (String s : split) {
						propertySet.setString(PROPERTIES_PREFIX_NAME + index, s);
						index++;
					}
					propertySet.setString(PROPERTIES_PREFIX_NAME, encryptPropertiesSize);
				}
			} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException
					| BadPaddingException e) {
				e.printStackTrace();
			}
		}
	}

	public static void deleteProperties(ApplicationUser user) {
		UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();
		try {
			PropertySet propertySet = userPropertyManager.getPropertySet(user);
			if (propertySet != null) {
				String sizeString = propertySet.getString(PROPERTIES_PREFIX_NAME);
				if (sizeString != null) {
					Long size = Long.parseLong(decrypt(sizeString));
					for (long index = 1; index <= size; index++) {
						propertySet.remove(PROPERTIES_PREFIX_NAME + index);
					}
				}
				propertySet.remove(PROPERTIES_PREFIX_NAME);
			}
		} catch (NumberFormatException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException
				| IllegalBlockSizeException | BadPaddingException e) {
			e.printStackTrace();
		}
	}

	public static void putProperties(ApplicationUser user, String properties) {
		deleteProperties(user);
		postProperties(user, properties);
	}

	public static String encrypt(String source) throws NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		Cipher cipher = Cipher.getInstance(ENCRYPT_ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(ENCRYPT_KEY.getBytes(), ENCRYPT_ALGORITHM));
		return new String(Base64.getEncoder().encode(cipher.doFinal(source.getBytes())));
	}

	public static String decrypt(String encryptSource) throws NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		Cipher cipher = Cipher.getInstance(ENCRYPT_ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(ENCRYPT_KEY.getBytes(), ENCRYPT_ALGORITHM));
		return new String(cipher.doFinal(Base64.getDecoder().decode(encryptSource.getBytes())));
	}

}