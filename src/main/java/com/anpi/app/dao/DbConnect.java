/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.anpi.app.dao;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import org.apache.log4j.Logger;

import com.anpi.app.constants.Constants;
import com.anpi.app.domain.TagMapDTO;
import com.mysql.jdbc.Connection;

/**
 * Provides DBConnect implementation to retrieve resultset from database
 */
public class DbConnect {
    
	/** Logging for DBconnect */
	private static final Logger logger = Logger.getLogger(DbConnect.class);
	
   /**
    * Creates the database connection.
    * @return the connection
    */
   private Connection createConnection(){
        Connection connection_mysql = null;
        try {  
            Class.forName("com.mysql.jdbc.Driver");  
             connection_mysql = (Connection) DriverManager.getConnection(Constants.MYSQL_URL, Constants.MYSQL_UNAME, Constants.MYSQL_PWD);  
     } catch (Exception e) {  
           e.printStackTrace();  
        } 
        return connection_mysql;
    }
    
	/**
	 * Executes single query.
	 * @param query the query string
	 * @return the configs from single query
	 * @throws SQLException 
	 */
	public HashMap<String, String> getConfigsFromSingleQuery(String query) throws SQLException {
		logger.info("Entering getConfigsFromSingleQuery");
    	System.out.println("Entering getConfigsFromSingleQuery");
		DbConnect dbConnect = new DbConnect();
		Connection con = dbConnect.createConnection();
		Statement stmt = con.createStatement();
		ResultSet rs = stmt.executeQuery(query);
		ResultSetMetaData rsMeta = rs.getMetaData();
		int columnCnt = rsMeta.getColumnCount();
		HashMap<String, String> resultMap = new HashMap<String, String>();
		// The above query is NOT SUPPOSED to return more than one row
		// For some reason if the requirement changes to return more than one row, the map will have to be handled accordingly.
		while (rs.next()) {
			for (int i = 1; i <= columnCnt; i++) {
				resultMap.put(rsMeta.getColumnName(i), rs.getString(i));
			}
		}
		con.close();
		logger.info("Exiting getConfigsFromSingleQuery");
    	System.out.println("Exiting getConfigsFromSingleQuery");
		return resultMap;
	}
    
    /**
	 * Executes multiple query string 
	 * @param queryArr the query string
	 * @return the configs from multiple query
	 * @throws SQLException 
	 *             
	 */
    public HashMap<String, String> getConfigsFromMultipleQuery(String [] queryArr) throws SQLException{
    	logger.info("Entering getConfigsFromMultipleQuery");
    	System.out.println("Entering getConfigsFromMultipleQuery");
        HashMap<String, String> resultMap = new HashMap<String, String>();
        DbConnect dbConnect = new DbConnect();
        Connection con = dbConnect.createConnection();
        Statement stmt = con.createStatement();
        boolean rsBool = true;
        for(int j=0; j<queryArr.length; j++){
        	if(j > 0 && rsBool){
        		break;
        	}
        	System.out.println("Executing query -->"+queryArr[j]);
        	logger.info("Executing query -->"+queryArr[j]);
            ResultSet rs = stmt.executeQuery(queryArr[j]);
            ResultSetMetaData rsMeta = rs.getMetaData();
            int columnCnt = rsMeta.getColumnCount();
            while(rs.next()){
            	rsBool = false;
                for(int i=1; i<=columnCnt; i++){
                    resultMap.put(rsMeta.getColumnName(i), rs.getString(i));
                }
            }
        }
        System.out.println("resultMap-->"+resultMap);
        con.close();
        logger.info("Exiting getConfigsFromMultipleQuery");
    	System.out.println("Exiting getConfigsFromMultipleQuery");
        return resultMap;
    }
    
    /**
	 * Inserts the mail content into email_logs 
	 * @param sql the query to insert in email_logs
	 * @return the id from database 
	 */
    public int putLogs(String sql) {
    	System.out.println("entering putlogs");
    	logger.info("entering putlogs");
    	// html content need to handed in logs column
    	boolean success = false;
    	int insertId = 0;
    	DbConnect dbConnect = new DbConnect();
    	Connection con = dbConnect.createConnection();
    	Statement stmt;
    	try {
    		stmt = con.createStatement();
    		stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    		ResultSet rs = stmt.getGeneratedKeys();
    		while (rs.next()) {
    			insertId = rs.getInt(1);
    		}
    		success = true;
    	} catch (SQLException ex) {
    		System.out.println(ex.getMessage());
    	} finally {
    		// Close connections be handled as functions with exception handling.
    		try {
    			con.close();
    		} catch (SQLException ex) {
    			System.out.println(ex.getMessage());
    			logger.info(ex.getMessage());
    		}
    	}
    	System.out.println("exiting putlogs -->"+insertId);
    	logger.info("exiting putlogs-->"+insertId);
    	return insertId;
    }
    
	/**
	 * Increments the resend count in email_logs when resend is triggered
	 * @param sql the query to update resend count
	 * @return the int
	 */
	public int updateResendCount(String sql) {
		System.out.println("Entering updateResendCount");
		logger.info("Entering updateResendCount");
		boolean success = false;
		int insertId = 0;
		DbConnect dbConnect = new DbConnect();
		Connection con = dbConnect.createConnection();
		Statement stmt;
		try {
			stmt = con.createStatement();
           /* Formatting date to UTC STring
            String sentDate = DateUtil.convertToUTCString(new Date());
            String sql = "UPDATE email_logs SET sending_status='Created',sent_date='"+sentDate+"',resend_count="+act_resend_count+" WHERE id="+id;*/
			stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
			ResultSet rs = stmt.getGeneratedKeys();
			while (rs.next()) {
				insertId = rs.getInt(1);
			}
			success = true;
		} catch (SQLException ex) {
			System.out.println(ex.getMessage());
		} finally {
			// Close connections be handled as functions with exception
			// handling.
			try {
				con.close();
			} catch (SQLException ex) {
				System.out.println(ex.getMessage());
				logger.info(ex.getMessage());
			}
		}
		System.out.println("Exiting updateResendCount");
		logger.info("Exiting updateResendCount");
		return insertId;
	}
    
    /**
	 * Updates the status of mail from created to sent once the mail is sent.
	 * @param sql the query to update email_logs
	 * @return true, if successful
	 */
    public boolean updateLogs(String sql) {
    	System.out.println("Entering updateLogs");
    	logger.info("Entering updateLogs");
    	boolean success = false;
    	DbConnect dbConnect = new DbConnect();
    	Connection con = dbConnect.createConnection();
    	Statement stmt;
    	try {
    		stmt = con.createStatement();
    		stmt.execute(sql);
    		System.out.println("update of logs happened successfully");
    		logger.info("update of logs happened successfully");
    		success = true;
    	} catch (SQLException ex) {
    		System.out.println(ex.getMessage());
    		logger.info("Exception"+ex.getMessage());
    	} finally {
    		//  Close connections be handled as functions with exception handling.
    		try {
    			con.close();
    		} catch (SQLException ex) {
    			System.out.println(ex.getMessage());
    			logger.info("Exception"+ex.getMessage());
    		}
    	}
    	System.out.println("Exiting updateLogs-->"+success);
    	logger.info("Exiting updateLogs-->"+success);
    	return success;
    }
    
    /**
	 * Hashmap of tags corresponding to the email identifier  
	 * @param query the query to return tags 
	 * @return the tags
	 * @throws SQLException 
	 */
    public HashMap<String,TagMapDTO> getTags(String query) throws SQLException{
    	System.out.println("Entering getTags");
    	logger.info("Entering getTags");
        DbConnect dbConnect = new DbConnect();
        Connection con = dbConnect.createConnection();
        Statement stmt = con.createStatement();
        System.out.println("query -->"+query);
        ResultSet rs = stmt.executeQuery(query);
        HashMap<String, TagMapDTO> resultMap = new HashMap<String, TagMapDTO>();
        TagMapDTO tagMapDTO;
        while(rs.next()){
        	tagMapDTO = new TagMapDTO();
        	tagMapDTO.setSource(rs.getInt("source"));
        	tagMapDTO.setTagValue(rs.getString("tag_value"));
            resultMap.put(rs.getString("tag_name"), tagMapDTO);
        }
        con.close();
        System.out.println("Exiting getTags"+resultMap);
    	logger.info("Exiting getTags");
        return resultMap;
    }
   
    public static void main(String[] args) throws SQLException {
        System.out.println("BEGIN");
        String query = "select * from email_configs_vw where setId='ANPI' AND email_name='Atlas-SystemAdmin-ResetPassword' AND status='ACTIVE'";
        //HashMap<String, String> resultMap = new DbConnect().getConfigs(query);
        String query2 = "select cc_email,reply_to_email from email_configs_opt_vw where setId='ANPI' AND email_name='Atlas - System Admin - Reset Password'";
        /*   HashMap<String, String> resultMap2 = new DbConnect().getConfigs(query);
        System.out.println("Map => "+resultMap);*/
        String [] q = new String [2];
        q[0] = query;
        q[1] = query2;
        HashMap<String, String> resultMap = new DbConnect().getConfigsFromMultipleQuery(q);
        System.out.println("Map 2 => "+resultMap);
        System.out.println("END");
    }

	/**
	 * inserts resend email into resend_email log
	 * @param sql the query string to insert in resend_email_lists
	 */
	public void updateLogger(String sql) {
		// html content need to handled in logs column
		// System.out.println("parameterMap to be inserted as logs => "+parameterMap);
		logger.info("Entering updateLogger");
		System.out.println("Entering updateLogger");
		boolean success = false;
		int insertId = 0;
		DbConnect dbConnect = new DbConnect();
		Connection con = dbConnect.createConnection();
		Statement stmt;
		try {
			stmt = con.createStatement();
			stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
			ResultSet rs = stmt.getGeneratedKeys();
			while (rs.next()) {
				insertId = rs.getInt(1);
			}
			success = true;
		} catch (SQLException ex) {
			System.out.println(ex.getMessage());
		} finally {
			// Close connections be handled as functions with exception
			// handling.
			try {
				con.close();
			} catch (SQLException ex) {
				System.out.println(ex.getMessage());
			}
			logger.info("Exiting UpdateLogger");
			System.out.println("Exiting updateLogger");
		}

	}
    
}
