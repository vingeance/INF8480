package shared;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ApplicationProperties {

    private static final String PROPERTIES_FILENAME = "application.properties";

    public static String getPropertyValueFromKey(String propertyKey)
    {
        File propertiesFile = new File(PROPERTIES_FILENAME);
        try
        {
            BufferedReader in = new BufferedReader(new FileReader(propertiesFile));
            String readedLine = "";
            while ((readedLine = in.readLine()) != null)
            {
                String[] parts = readedLine.split("=");
                if(parts[0].equals(propertyKey))
                {
                    return parts[1];
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Error: " + e.getMessage());
        }

        System.err.println("Error: Could not find value of property : " + propertyKey + " in application.properties");
        return null;
    }
}
