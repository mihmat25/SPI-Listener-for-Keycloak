# Setting up a Custom Keycloak Event Listener Provider

## Maven Project Setup
- Create a plain Maven project with `database-sync` name. Or whatever name we want.
- In the `pom.xml` add the following dependencies:
```xml
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-dependencies-server-all</artifactId>
    <version>24.0.1</version>
    <type>pom</type>
</dependency>

<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.32</version>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <version>2.17.0</version>
</dependency>

<dependency>
    <groupId>org.mariadb.jdbc</groupId>
    <artifactId>mariadb-java-client</artifactId>
    <version>3.3.3</version>
</dependency>
```
## Provider classes
- Under the `src/main/java` (optionally we can create a separate package under `java`) Create two classes `ExternalDbSyncProvider` and the `ExternalDbSyncProviderFactory` (naming does not matter).
- For the `ExternalDbSyncProviderFactory` we have the following code:
```java
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalDbSyncProviderFactory implements EventListenerProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger(ExternalDbSyncProviderFactory.class);
    private static final String PROVIDER_ID = "external-db-sync";

    @Override
    public EventListenerProvider create(KeycloakSession keycloakSession) {
        // print statement for debug, remove after testing
        logger.info("New ExternalDbSyncProvider created!");
        return new ExternalDbSyncProvider();
    }

    @Override
    public void init(Config.Scope scope) {
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
```
- For the `ExternalDbSyncProvider` we can use the following code. The accent is on the `onEvent()` function, which we should customize. We have two of them, `Event` and `AdminEvent`. We can listen user events and admin events as well.
```java
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
        //Here we load the properties from our EXTERNAL application.properties file.
        Properties configuration;
        try {
            configuration = loadProperties();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //Here we get the properties from the loaded file.
        REALM_UUID = configuration.getProperty("keycloak.realm.uuid");
        DB_URL = configuration.getProperty("database.url");
        DB_USER = configuration.getProperty("database.username");
        DB_PASSWORD = configuration.getProperty("database.password");
        DB_INSERT_QUERY = "INSERT INTO " + DB_USER + ".`user` " +
                "(id, family_name, given_name, username, email, phone_number, role_ids, contract_from, contract_to, contract_type, available, company_id, created_by, create_time, updated_by, update_time, deleted, country, county, city, address) " + "VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)";

        // print statement for debug, remove after testing
        logger.info("ADMIN EVENT DETECTED!");

        logger.info(event.getRealmId());

        if (event.getRealmId().equals(REALM_UUID) && event.getOperationType() == OperationType.CREATE) { //todo add another check to resourceType()== ResourceType.USER
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

            //Here we create the connection with the database and build a query.
            try (Connection conn = createConnection(DB_URL, DB_USER, DB_PASSWORD);
                 var prepareStatement = conn.prepareStatement(DB_INSERT_QUERY)) {
                prepareStatement.setObject(1, userIdBytes);
                prepareStatement.setString(2, dto.lastName);
                prepareStatement.setString(3, dto.firstName);
                prepareStatement.setString(4, dto.username);
                prepareStatement.setString(5, dto.email);

                int affectedRows = prepareStatement.executeUpdate();
                logger.info("preparedstatement " + prepareStatement.toString());

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
    //Here we extract the UUID from the RESOURCE_PATH of the AdminEvent:
    private static String extractUuidFromPath(String path) {
        Pattern pattern = Pattern.compile("users/([a-f0-9\\-]+)");
        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    //Here we load the properties from EXTERNAL application.properties file.
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
        return DriverManager.getConnection(url, user, password);
    }
}
```
## Configuration and Integration
- application.properties key=value example:
```properties
database.url=jdbc:mariadb://<db_server_uri>:3306/<your_db_name>
database.username=<your_username>
database.password=<your_password>
keycloak.realm.uuid=<your_realm_uuid> #we can get it from the Keycloak db, in the REALM table.
```
## Packaging and Deployment
- Create the directory structure src/main/resources/META-INF/services.
- Inside services, create a file named `org.keycloak.events.EventListenerProviderFactory`. Add the next line to it: `com.<your_package_path>.ExternalDbSyncProviderFactory` 
- Build the project using Maven: In IntelliJ go to View > Tool Windows > Maven, expand your project, expand Lifecycle and double-click on "package"; or run `mvn package`.
- From the generated `target` folder place the newly generated JAR file in `<keycloak_root_folder>/providers`. Add the application.properties file to this folder as well.
- Open a command prompt in `<keycloak_root_folder>` and run `bin\kc.sh build`.
## Testing and Configuration
- Launch keycloak, and you should see a similar message in the console, which means that keycloak was able to identify and register our custom provider:
  `KC-SERVICES0047: external-db-sync (ExternalDbSyncProviderFactory) is implementing
  the internal SPI eventsListener. This SPI is internal and may change without notice`
- Execute the command `bin\kc.bat start-dev` to launch Keycloak. Once Keycloak is running, navigate to `localhost:8080` in your web browser. By default, our custom provider won't be active for any realm. To enable it for a specific realm, follow these steps:<br>Go to the realm settings.<br> Select "Events". <br>Navigate to "Events Listeners".<br> Add your custom provider from the available list of event listeners.
- Test your custom provider by simulating the event you've implemented the code for, such as user creation in this instance. Upon triggering this event, observe the corresponding logs on the console, confirming the successful execution of your custom logic.