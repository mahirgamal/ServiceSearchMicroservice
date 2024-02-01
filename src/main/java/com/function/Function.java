package com.function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {
    /**
     * This function listens at endpoint "/api/HttpExample". Two ways to invoke it
     * using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpExample
     * 2. curl "{your host}/api/HttpExample?name=HTTP%20Query"
     */

    private static final Logger logger = Logger.getLogger(Function.class.getName());
    private static final String DB_URL = "jdbc:mysql://leisadb.mysql.database.azure.com:3306/leisa";
    private static final String DB_USER = "lei";
    private static final String DB_PASSWORD = "mahirgamal123#";

    private String username, password;

    static {
        try {
            // Load the JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @FunctionName("findService")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.GET }, route = "list/{id}", authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                    @BindingName("id") Long id,
            final ExecutionContext context) {
        logger.info("Executing listService to retrieve all users.");

        if (isAuthorized(request)) {

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE id=?")) {

                stmt.setLong(1, id);
                ResultSet rs = stmt.executeQuery();
                

                List<User> users = new ArrayList<>();
                while (rs.next()) {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setQueuename(rs.getString("queuename"));
                    user.setAdmin(rs.getBoolean("admin"));
                    // Add other user fields as needed
                    users.add(user);
                }

                return request.createResponseBuilder(HttpStatus.OK).body(users).build();
            } catch (SQLException e) {
                logger.severe("SQL error: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving users")
                        .build();
            }
        } else {
            logger.warning("Authorization failed or user information exists for user");
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED).body("Unauthorized").build();
        }
    }

    private boolean isAuthorized(HttpRequestMessage<Optional<String>> request) {
        // Parse the Authorization header
        final String authHeader = request.getHeaders().get("authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        // Extract and decode username and password
        String base64Credentials = authHeader.substring("Basic ".length()).trim();
        byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(credDecoded);

        // credentials = username:password
        final String[] values = credentials.split(":", 2);

        if (values.length != 2) {
            return false; // Incorrect format of the header
        }

        username = values[0];
        password = values[1];


        logger.info(username);
        logger.info(password);

        String sql = "SELECT * FROM users WHERE username=?";

        try (java.sql.Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && BCrypt.checkpw(password, rs.getString("password"))) {
                    return true;
                } else
                    return false;
            }
        } catch (SQLException e) {
            // Handle exceptions (log it or throw as needed)
            e.printStackTrace();
        }

        return false;

    }
}
