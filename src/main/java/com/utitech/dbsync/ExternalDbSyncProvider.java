package com.utitech.dbsync;

import com.utitech.Main;
import com.utitech.util.SerializeUtil;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExternalDbSyncProvider implements EventListenerProvider {

    private static final Logger logger = LoggerFactory.getLogger(ExternalDbSyncProvider.class);

    private String REALM_UUID;

    private String DB_URL;

    private String DB_USER;

    private String DB_PASSWORD;

    private String DB_INSERT_QUERY;

    public ExternalDbSyncProvider() {
    }

    @Override
    public void onEvent(Event event) {
    }

    @Override
    public void onEvent(AdminEvent event, boolean b) {
        Properties configuration;
        try {
            configuration = loadProperties();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        REALM_UUID = configuration.getProperty("keycloak.realm.uuid");
        DB_URL = configuration.getProperty("database.url");
        DB_USER = configuration.getProperty("database.username");
        DB_PASSWORD = configuration.getProperty("database.password");
        DB_INSERT_QUERY = "INSERT INTO " + DB_USER + ".`user` " +
                "(id, family_name, given_name, username, email, phone_number, role_ids, contract_from, contract_to, contract_type, available, company_id, created_by, create_time, updated_by, update_time, deleted, country, county, city, address) " +
                "VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)";

        logger.info("realm " + REALM_UUID);
        logger.info("db " + DB_URL);
        // print statement for debug, remove after testing
        logger.info("ADMIN EVENT DETECTED!");

        logger.info(event.getRealmId());

        if (event.getRealmId().equals(REALM_UUID) && event.getOperationType() == OperationType.CREATE) { //todo add another check to resourceType()
            logger.info("EVENT CREATE IN REALM DETECTED!");

            String adminEventRepresentation = event.getRepresentation();
            logger.info("adminrep " + adminEventRepresentation);
            UserCreateDto dto = SerializeUtil.deserializeFromJson(adminEventRepresentation, UserCreateDto.class);
            System.out.println("dto " + dto);

            String resourcePath = event.getResourcePath();
            logger.info("resourcepath " + resourcePath);
            String userId = extractUuidFromPath(resourcePath);
            logger.info("userid " + userId);
            UUID uuid = UUID.fromString(userId);

            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            byte[] userIdBytes = bb.array();
            logger.info("this is the userbytes" + userIdBytes.toString());

            try (Connection conn = createConnection(DB_URL, DB_USER, DB_PASSWORD);
                 var prepareStatement = conn.prepareStatement(DB_INSERT_QUERY)) {
                logger.info("in connection");
                prepareStatement.setObject(1, userIdBytes);
                prepareStatement.setString(2, dto.lastName);
                prepareStatement.setString(3, dto.firstName);
                prepareStatement.setString(4, dto.username);
                prepareStatement.setString(5, dto.email);

                int affectedRows = prepareStatement.executeUpdate();
                logger.info("prparestatement " + prepareStatement);

                // print statement for debug, remove after testing
                logger.info("Inserted " + affectedRows + " row(s)");

            } catch (SQLException ex) {
                logger.info(ex.getMessage());
            }
        }
    }

    @Override
    public void close() {
    }

    private static String extractUuidFromPath(String path) {
        Pattern pattern = Pattern.compile("users/([a-f0-9\\-]+)");
        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try {
            // Determine the directory of the JAR file
            String jarDirPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();

            // Construct the path to the properties file
            File propertiesFile = new File(jarDirPath, "application.properties");

            // Load the properties from the file
            try (FileInputStream fis = new FileInputStream(propertiesFile)) {
                properties.load(fis);
            }
        } catch (URISyntaxException e) {
            throw new IOException("Could not resolve the JAR file path.", e);
        }
        return properties;
    }

    private static Connection createConnection(String url, String user, String password) throws SQLException {
        logger.info("getconnection");
        return DriverManager.getConnection(url, user, password);
    }
}