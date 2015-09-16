package com.anpi.app.util;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.anpi.app.constants.Constants;
import com.anpi.app.domain.TagMapDTO;
import com.mysql.jdbc.Connection;

/**
 * Provides DBConnect implementation to retrieve resultset from database
 */
@Component
public class DbConnect {
    
	/** Logging for DBconnect */
	private static final Logger logger = Logger.getLogger(DbConnect.class);
	
   /**
    * Creates the database connection.
    * @return the connection
    */
	private Connection createConnection() {
		Connection connection = null;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			connection = (Connection) DriverManager.getConnection(Constants.MYSQL_URL,
					Constants.MYSQL_UNAME, Constants.MYSQL_PWD);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return connection;
	}
    
	
	/**
	 * Executes single query.
	 */
	public HashMap<String, String> getConfigsFromSingleQuery(String query) throws SQLException {
		logger.info("Entering getConfigsFromSingleQuery");
		
		DbConnect				dbConnect	= new DbConnect();
		Connection				con			= dbConnect.createConnection();
		Statement				stmt		= con.createStatement();
		ResultSet				rs			= stmt.executeQuery(query);
		ResultSetMetaData		rsMeta		= rs.getMetaData();
		int						columnCnt	= rsMeta.getColumnCount();
		HashMap<String, String>	resultMap	= new HashMap<String, String>();
		
		/* The above query is NOT SUPPOSED to return more than one row
		 For some reason if the requirement changes to return more than one row, the map will have to be handled accordingly.*/
		while (rs.next()) {
			for (int i = 1; i <= columnCnt; i++) {
				resultMap.put(rsMeta.getColumnName(i), rs.getString(i));
			}
		}
		
		con.close();
		
		logger.info("Exiting getConfigsFromSingleQuery");
		return resultMap;
	}
    
	
    /**
	 * Executes multiple query string 
	 */
    public HashMap<String, String> getConfigsFromMultipleQuery(String [] queryArr) throws SQLException{
    	logger.info("Entering getConfigsFromMultipleQuery");
    	
    	//TODO - email_configs_opt_vw - query only if cc,reply_to value exists
    	
    	HashMap<String, String>	resultMap	= new HashMap<String, String>();
    	DbConnect				dbConnect	= new DbConnect();
    	Connection				con			= dbConnect.createConnection();
    	Statement				stmt		= con.createStatement();
        boolean 				rsBool 		= true;
        
        for(int j=0; j<queryArr.length; j++){
        	if(j > 0 && rsBool){
        		break;
        	}
        	
        	logger.info("Executing query -->"+queryArr[j]);
        	
        	ResultSet			rs			= stmt.executeQuery(queryArr[j]);
        	ResultSetMetaData	rsMeta		= rs.getMetaData();
        	int					columnCnt	= rsMeta.getColumnCount();
        	
            while(rs.next()){
            	rsBool = false;
                for(int i=1; i<=columnCnt; i++){
                    resultMap.put(rsMeta.getColumnName(i), rs.getString(i));
                }
            }
        }
        con.close();
        
        logger.info("Exiting getConfigsFromMultipleQuery");
        return resultMap;
    }

   
    /**
	 * Inserts the mail content into email_logs 
	 */
    public int putLogs(String sql) {
    	logger.info("entering putlogs");
    	
    	boolean		success		= false;
    	int			insertId	= 0;
    	DbConnect	dbConnect	= new DbConnect();
    	Connection	con			= dbConnect.createConnection();
    	Statement	stmt;
    	
    	try {
    				stmt 		= con.createStatement();
    		stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    		ResultSet rs 		= stmt.getGeneratedKeys();
    		
    		while (rs.next()) {
    			insertId = rs.getInt(1);
    		}
    		success = true;
    		
    	} catch (SQLException ex) {
    		
    		logger.info("Exception : " + ex );
    		
    	} finally {
    		// Close connections be handled as functions with exception handling.
    		try {
    			con.close();
    		} catch (SQLException ex) {
    			
    			logger.info("Exception : " + ex );
    		}
    	}
    	
    	logger.info("exiting putlogs-->"+insertId);
    	return insertId;
    }
    
    
    /**
	 * Updates the status of mail from created to sent once the mail is sent.
	 */
    public boolean updateLogs(String sql) {
    	logger.info("Entering updateLogs");
    	
    	boolean		success		= false;
    	DbConnect	dbConnect	= new DbConnect();
    	Connection	con			= dbConnect.createConnection();
    	Statement	stmt;
    	
    	try {
    		
    		stmt = con.createStatement();
    		stmt.execute(sql);
    		success = true;
    		
    	} catch (SQLException ex) {
    		logger.info("Exception : "+ex);
    	} finally {
    		//  Close connections be handled as functions with exception handling.
    		try {
    			con.close();
    		} catch (SQLException ex) {
    			
    			logger.info("Exception"+ex.getMessage());
    		}
    	}
    	logger.info("Exiting updateLogs-->"+success);
    	return success;
    }
    

    /**
	 * Hashmap of tags corresponding to the email identifier  
	 */
    public HashMap<String,TagMapDTO> getTags(String query) throws SQLException{
    	logger.info("Entering getTags");
    	
    	DbConnect					dbConnect	= new DbConnect();
    	Connection					con			= dbConnect.createConnection();
    	Statement					stmt		= con.createStatement();
    	ResultSet					rs			= stmt.executeQuery(query);
    	HashMap<String, TagMapDTO>	resultMap	= new HashMap<String, TagMapDTO>();
    	TagMapDTO					tagMapDTO;
    	
        while(rs.next()){
        	tagMapDTO = new TagMapDTO();
        	tagMapDTO.setSource(rs.getInt("source"));
        	tagMapDTO.setTagValue(rs.getString("tag_value"));
            resultMap.put(rs.getString("tag_name"), tagMapDTO);
        }
        
        con.close();

        logger.info("Exiting getTags");
        return resultMap;
    }
   

}
