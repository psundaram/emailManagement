package com.anpi.app.util;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class PropUtils {

    private static final Logger logger = Logger.getLogger(PropUtils.class);
    private static   PropUtils urlInstance = null;
    private static PropUtils instance = null;
    private static   Properties   props = null;

    static
    {
//        ClassPathResource resource = new ClassPathResource("config.properties");
        Resource resource = new FileSystemResource("/apps/properties/url.properties");
        try {
            urlInstance = new PropUtils(resource.getInputStream());
            URL url = new URL(getVal("url"));
            instance = new PropUtils(url.openStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Instantiates a new prop utils.
     *
     * @param inputStream the input stream
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private PropUtils(InputStream inputStream) throws IOException
    {
        Properties p = new Properties();
        try
        {
            p.load(inputStream);
        }
        finally
        {
            IOUtils.closeQuietly(inputStream);
        }
        props = p;
    }


    /**
     * Gets the val.
     *
     * @param prop the prop
     * @return the val
     */
    public static String getVal(String prop)
    {
        if (props.containsKey(prop))
        {
            return (String)props.getProperty(prop).trim();
        }
        else
        {
            return null;
        }
    }

}
