package com.anpi.app.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import net.sf.json.JSONException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.json.simple.parser.ParseException;
import org.springframework.stereotype.Service;

import com.anpi.app.constants.Constants;
import com.anpi.app.domain.TagMapDTO;
import com.anpi.app.dao.DbConnect;
import com.anpi.app.util.CommonUtil;
import com.anpi.app.util.URLReaderUtil;
import com.google.common.base.Strings;


/**
 * Provides relayEmail implementation for generating content,insert in email
 * logs,update logger table and generate query
 */
@Service
public class RelayEmailDAO {

	private static final Logger logger = Logger.getLogger(RelayEmailDAO.class);

	
	/**
	 * Tag value in content and subject are replaced with appropriate value from
	 * API call, Tag map or relay email.
	 */
	public String[] generateActualContent(Map<String, String> elementMap, Map<String, String> configMap) throws SQLException, JSONException, IOException {
		logger.info("Entering generateActualContent");
		
		String						content			= null;
		String						subject			= null;
		String						query			= null;
		List<String>				tagsUsedList	= null;
		Map<String, String>			apiMap			= new HashMap<String, String>();
		String 						isPublished 	= CommonUtil.getValueForMap(configMap, "is_published");
		Map<String, String> 		map				= new HashMap<String, String>();
		List<String>				dbTagList		= new ArrayList<String>();
		
		if (!configMap.isEmpty()) {
			
			subject = StringEscapeUtils.unescapeHtml(configMap.get("subject"));
			content = StringEscapeUtils.unescapeHtml(configMap.get("content_template"));
			query = "select tag_name, tag_value, source from email_tags where email_config_id = '" + configMap.get("id") + "'";
		}
		
		/* Retrieve Tag values from TagMaster table */
		HashMap<String, TagMapDTO> tagMap = new DbConnect().getTags(query);
		
		logger.info("tagMap -> " + tagMap);

		/* If partner is published, get branding parameters  */
		if(!Strings.isNullOrEmpty(isPublished) && "1".equals(isPublished)){
			String 	partnerId	= CommonUtil.getValueForMap(configMap, "partner_id");
					apiMap 		= URLReaderUtil.getInputFromUrl(Constants.API_CALL_URL+"partner_id="+partnerId);
		}
		
		String tagsUsed = CommonUtil.getValueForMap(elementMap,"TAGS USED");
		if (!Strings.isNullOrEmpty(tagsUsed)) {
			tagsUsedList = new ArrayList<String>(Arrays.asList(tagsUsed.split(",")));
		}
		
		/* Replace signature */
		String signature = CommonUtil.getValueForMap(configMap, "signature");
		if (!Strings.isNullOrEmpty(signature) && (!signature.contains("generated"))) {
			signature	= StringEscapeUtils.unescapeHtml(signature);
			signature 	= replaceDollarSign(signature);
			content 	= content.replaceAll("<<.SIGNATURE CONTENT>>", signature);
		} else {
			content 	= content.replaceAll("<<.SIGNATURE CONTENT>>", "");
		}
		
		for (String tagName : tagMap.keySet()) {
			TagMapDTO tagMapDTO = tagMap.get(tagName);
			if(tagMapDTO.getSource().equals("2")){
				dbTagList.add(tagMapDTO.getTagValue());
			}
		}
		
		if(dbTagList!=null && !dbTagList.isEmpty()){
			String dbTagQuery = "select netx_id,product_type from products where netx_id in ('" + dbTagList + "' ) and   partner_id ='" + configMap.get("actual_partner_id") + "'";
			System.out.println("dbTagQuery :" + dbTagQuery);
		}
		
		for (String tag : tagMap.keySet()) {
			do {
				for (String tagName : tagMap.keySet()) {
					
					if (content.contains("<<." + tagName + ">>") || subject.contains("<<." + tagName + ">>")) {
						String 		replacedValue 	= "";
						TagMapDTO 	tagMapDTO 		= tagMap.get(tagName);
						
						logger.info("tagName:" + tagName);
						
						/* Tags used condition */
						if (tagsUsedList != null && !tagsUsedList.isEmpty()) {
							if (!tagsUsedList.contains(tagName)) {
								content = content.replaceAll("<<." + tagName + ">>", "");
								subject = subject.replaceAll("<<." + tagName + ">>", "");
								logger.info(tagName + "tagName not found");
							}
						}
						
						/* Replace tags with partner branding configuration */
						if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(apiMap, tagName))) {
							
							if (tagName.contains("PHONE")) {
								String 	phoneNumber 	= CommonUtil.formatNumber(apiMap.get(tagName),Constants.COMMA);
										replacedValue	= phoneNumber;
							} else {
										replacedValue 	= apiMap.get(tagName);
							}
						}
						
						/* CONDITION TO HANDLE DB TAGS */
						else if (tagMap.containsKey(tagName) && tagMapDTO.getSource()==2) {
							/* Retrieve partner product information for the actual partner Id */
							String dbTagQuery = "select product_type from products where netx_id='" + tagName + "' and   partner_id ='" + configMap.get("actual_partner_id") + "'";
							
							logger.info("dbTagQuery:" + dbTagQuery);
							
							map = new DbConnect().getConfigsFromSingleQuery(dbTagQuery);
							if(map!=null && !map.isEmpty()){
								replacedValue = map.get("product_type");
							}
						} 
						
						/* replace tags with tag value  */
						else if (tagMap.containsKey(tagName) && !Strings.isNullOrEmpty(tagMapDTO.getTagValue())) {
							String 	temp 			=  StringEscapeUtils.unescapeHtml(tagMapDTO.getTagValue());
									temp			= replaceDollarSign(temp);
									replacedValue 	= temp;
						} 
						
						/* replace tags with elements map (relay email) */
						else if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(elementMap, tagName))) {
							
							String 	temp 			= StringEscapeUtils.unescapeHtml(elementMap.get(tagName));
									temp 			= replaceDollarSign(temp);
									replacedValue	= temp;
						}	
						
						/* replace value with VFTTND */
						 else {
							logger.info("value for " + tagName + " is not defined");
							replacedValue = Constants.VFTTND;
						}
						
						logger.info(tagName +" replaced by " + replacedValue);
						
						subject = subject.replaceAll("<<." + tagName + ">>", replacedValue);
						content = content.replaceAll("<<." + tagName + ">>", replacedValue);
					}
				}
			} while (content.contains("<<." + tag + ">>") || subject.contains("<<." + tag + ">>"));
		}
		
		logger.info("Exiting generateActualContent -->" + content);
		String[] arr = {subject,content};
		return arr;
	}

	
	/** Replace dollar sign */
	private static String replaceDollarSign(String value) {
		String replacedValue = "";
		Pattern pattern = Pattern.compile("$");
		Matcher matcher = pattern.matcher(value);
		while (matcher.find()) {
			System.out.println("matcher");
			replacedValue = value.replaceAll("\\$", "\\\\\\$");
		}
		return replacedValue;
	}
	
 
	/**
	 * Get configuration for given emailname and setId
	 */
	public Map<String, String> getConfig(Map<String, String> partnerMap,String emailName,String setId) throws MalformedURLException, IOException, ParseException, SQLException  {
		
		Map<String, String>		configMap	= new HashMap<String, String>();
		String 					partnerId  	= null;
		String 					setID 		= null;			
		String					partnerType = CommonUtil.getValueForMap(partnerMap, "partner_type");
		String					parentId 	= CommonUtil.getValueForMap(partnerMap, "parent_partner_id");
		String 					isPublished = CommonUtil.getValueForMap(partnerMap, "is_published");
		
		/* If channel partner, apply configuration of parent partner */
		if (!Strings.isNullOrEmpty(partnerType) && "2".equals(partnerType)	&& !Strings.isNullOrEmpty(parentId)) {
			logger.info("Inside channel Partner");
			
			partnerId	 	= parentId;
			partnerMap 		= URLReaderUtil.getJsonFromUrl(Constants.PARTNER_SMTP_DETAILS+ "partners?id=" + partnerId);
			setID 			= CommonUtil.getValueForMap(partnerMap, "partner_id");
			
			// If partner is non-published, generic config SetID = "*****"
			if ((Strings.isNullOrEmpty(isPublished) || "0".equals(isPublished))) {
				setID = Constants.DUMMY_PARTNER_ID;
			}
		}
		
		/* If WS partner is non-published, generic config for setId as "*****" */
		else if ((Strings.isNullOrEmpty(isPublished) || "0".equals(isPublished))) {
			setID 		= Constants.DUMMY_PARTNER_ID;
			partnerId 	= partnerMap.get("id").toString();
		}
		
		else {
			setID 		= partnerMap.get("partner_id");
			partnerId 	= CommonUtil.getValueForMap(partnerMap, "id");
		}
		
		logger.info("SetID:" + setID + "partnerId:" + partnerId);
		
		String query1 = "select * from email_configs_vw where setId='" + setID
				+ "' and email_name='" + emailName + "'  AND customer_id=-1";
		
		String query2 = "select cc_email,reply_to_email from email_configs_opt_vw where setId='"
				+ setID + "' AND email_name='" + emailName + "'  AND customer_id=-1";
		
		String 	query 		= query1 + "###" + query2;
				configMap	= new DbConnect().getConfigsFromMultipleQuery(query.split("###"));
		
		if(configMap!=null && !configMap.isEmpty()){
			configMap.put("actual_partner_id",partnerId);
			configMap.put("partner_type", partnerType);
		}
		
		return configMap;
	}
	
	
	/**
	 * Insert into email_logs.
	 * @param parameterMap the hashmap of key,values to be inserted in email_logs
	 * @return the id 
	 */
	public int insert(Map<String, String> parameterMap) {
		logger.info("Entering insert");
		
		String	columns	= "";
		String	val		= "";
		
		for (String key : parameterMap.keySet()) {
			columns 	+= "`" + key + "`" + ",";
			val 		+= "'" + parameterMap.get(key) + "'" + ",";
		}
		
		columns 		= columns.substring(0, columns.lastIndexOf(','));
		val 			= val.substring(0, val.lastIndexOf(','));
		
		String sql = "INSERT INTO email_logs (" + columns + ") VALUES (" + val + ");";
		
		logger.info("Exiting insert relayDAO==>" + sql);
		return new DbConnect().putLogs(sql);
	}

	
	/**
	 * Updates sending status from created to sent.
	 * @param id the id
	 */
	public void updateLogger(int emailId) {
		
		String sql = "UPDATE email_logs SET sending_status='Sent' WHERE id=" + emailId;
		new DbConnect().updateLogs(sql);
	
	}
	

	/**
	 * Increments resend count in email_logs.
	 */
	public void updateLogsForResend(int emailId, int count) {
		logger.info("Entering updateLogsForResend==>" + count);
		
		int resendCount = count + 1;
		String sql = "UPDATE email_logs SET resend_count=" + resendCount + " WHERE id=" + emailId;
		
		logger.info("Exiting updateLogsForResend");
		new DbConnect().updateResendCount(sql);
	}

	
	/**
	 * Query to fetch email contents for resend.
	 */
	public Map<String, String> generateQueryForResend(String emailId) throws SQLException {
		logger.info("Entering generateQueryForResend==>" + emailId);
		
		String query = "select * from email_logs where id='" + emailId + "'";
		
		logger.info("Exiting generateQueryForResend");
		return new DbConnect().getConfigsFromSingleQuery(query);
	}

	
	/** Get configuration for given orderId and emailName */
	public Map<String, String> getConfigForOrderId(String orderId, String emailName)
			throws SQLException {
		logger.info("Entering generateMultiQueryStringToObtainConfig --> orderId,emailName "
				+ orderId + " , " + emailName);
		
		String query1 = "select * from email_configs_vw where order_id='" + orderId
				+ "' AND email_name='" + emailName + "'";
		
		String query2 = "select cc_email,reply_to_email from email_configs_opt_vw where order_id='"
				+ orderId + "' AND email_name='" + emailName + "'";
		
		String query = query1 + "###" + query2;
		
		logger.info("Exiting generateMultiQueryStringToObtainConfig");
		
		Map<String, String> result = new DbConnect().getConfigsFromMultipleQuery(query
				.split("###"));
		return result;
	}

	
	/** Get configuration for given customerId and emailName */
	public Map<String, String> getConfigForCustomerId(String customerId, String emailName)
			throws SQLException {
		logger.info("Entering generateMultiQueryStringToObtainConfig --> customerId,emailName "
				+ customerId + " , " + emailName);
		
		String query1 = "select * from email_configs_vw where customer_id='" + customerId
				+ "' AND email_name='" + emailName + "'  AND order_id=-1";
		
		String query2 = "select cc_email,reply_to_email from email_configs_opt_vw where customer_id='"
				+ customerId + "' AND email_name='" + emailName + "' AND order_id=-1";
		
		String query = query1 + "###" + query2;
		
		logger.info("Exiting generateMultiQueryStringToObtainConfig");
		
		Map<String, String> result = new DbConnect().getConfigsFromMultipleQuery(query
				.split("###"));
		return result;
	}
	
}
	
	
