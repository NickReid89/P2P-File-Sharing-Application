/*
 AUTHOR: Nickolas Reid

 Purpose: An implementation of the P2P.idl file. All methods except one are filled out. The reason is that one method is the only method
 implemented by the clients and it involves sending a user data. It doesn't make sense to have the server sending data about a file 
 it doesn't own so it is omitted. Otherwise this version acts as an administrator for clients. It only supplies information it does not
 send files.

 Methods:

 boolean registerIP(in string name,in string ip);
 This is called from a client orb. The client orb inputs the name of the user and the ip address.
 This is so the application can make future orbs. This data is never shared with users.

 boolean registerFile(in string fileName,in string fileLocation, in string user);
 This is called from the client orb. A user input the file name, the file path, and their name for association. 
 The file is then queried against a MySQL database which inputs the data for future use.

 boolean removeFile(in string fileName, in string userID);
 This is called from the client orb. A user input the file name, and their name for association. 
 The file is then queried against a MySQL database which deletes the row if it exists.

 string requestFile(in string fileName);
 This is called from the client orb. A user input the file name and then the server queries the database
 to see who owns the file. The server then tells the client who owns the file.

 string requestFileList();
 This is called from the client orb. The server requests a list of files from the database and gives them to the client

 void deleteUser(in string userName);
 This is called from the client orb. When a user exits, it asks the server to delete all traces of them to clean up the database.

 string sendFile(in string fileName);
 This is called from a client orb. Client A requests a file and the server tells client A who has it. Client A pings Client B a request for the file.
 Client B queries against the server to find out what the file path is. With the file path now known,Client B turns the file into a string and sends it to 
 Client A.

 string filePath(in string serverName, in string fileName);
 Only used by Client listener orb.

 void shutdown();



 */
package P2PServer;

import Peer2Peer.P2PPOA;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.omg.CORBA.ORB;

public class P2PObject extends P2PPOA {

    private ORB orb;
    static Connection con;
    //Connection to database. Only the main server contains this data.
    static String url = "jdbc:mysql://localhost:3306/P2PNetwork";
    static String user = "root";
    static String password = "";

    public void setORB(ORB orb_val) {
        orb = orb_val;
    }

    // Shuts down the orb.
    @Override
    public void shutdown() {

        orb.shutdown(false);
    }

    //Erases a users details to clean up the network.
    @Override
    public void deleteUser(String userName) {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            //connect to the MySQL database.
            con = DriverManager.getConnection(url, user, password);
            // I use ignore in my SQL query because if the user uploaded no files, and then exitted the application
            // this query would cause an exception due to not finding a result.
            String deleteUserFiles = "DELETE IGNORE FROM userfiles WHERE UserID = ?";
            PreparedStatement preparedStatement = con.prepareStatement(deleteUserFiles);
            preparedStatement.setString(1, userName);
            preparedStatement.executeUpdate();
            String deleteUser = "DELETE IGNORE FROM users WHERE UserID = ?";
            preparedStatement = con.prepareStatement(deleteUser);
            preparedStatement.setString(1, userName);
            preparedStatement.executeUpdate();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException ex) {
            Logger.getLogger(P2PObject.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //This registers a user into the database.
    @Override
    public boolean registerIP(String name, String ip) {
        //Assumes the user can register,but the SQL query may prove otherwise.
        boolean success = true;
        try {

            Class.forName("com.mysql.jdbc.Driver").newInstance();
            con = DriverManager.getConnection(url, user, password);
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT userID FROM users");
            while (rs.next()) {
                if (ip.equals(rs.getString("userID"))) {
                    success = false;
                }
            }
            //Insert user into database.
            if (success) {
                String insertTableSQL = "INSERT INTO users"
                        + "(userID,ComputerName) VALUES"
                        + "(?,?)";
                PreparedStatement preparedStatement = con.prepareStatement(insertTableSQL);
                preparedStatement.setString(1, name);
                preparedStatement.setString(2, ip);
                preparedStatement.executeUpdate();

            }
            return success;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException e) {
            System.out.println("Couldn't register name \n" + e);
            return false;
        }

    }

    //Adds a file name and path and who owns it to database.
    @Override
    public boolean registerFile(String fileName, String fileLocation, String userID
    ) {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            con = DriverManager.getConnection(url, user, password);
            String insertTableSQL = "INSERT INTO userfiles (userID,filePath, Files) VALUES (?, ?,?)";
            PreparedStatement preparedStatement = con.prepareStatement(insertTableSQL);
            preparedStatement.setString(1, userID);
            preparedStatement.setString(2, fileLocation);
            preparedStatement.setString(3, fileName);
            preparedStatement.executeUpdate();
            return true;

        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException e) {
            System.out.println("Couldn't register file. \n" + e);
            return false;
        }
    }

    //Removes a file from the database that a user asks for.
    @Override
    public boolean removeFile(String fileName, String userID
    ) {

        boolean exists = false;

        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            con = DriverManager.getConnection(url, user, password);

            Statement stmt = con.createStatement();

            //Make sure it exists.
            ResultSet rs = stmt.executeQuery("SELECT userID,Files FROM userFiles");
            while (rs.next()) {
                if (userID.equals(rs.getString("userID")) && fileName.equals(rs.getString("Files"))) {
                    exists = true;
                }
            }

            if (exists) {
                String deleteFileRecord = "DELETE FROM userfiles WHERE Files = ? AND userID = ?";
                PreparedStatement preparedStatement = con.prepareStatement(deleteFileRecord);
                preparedStatement.setString(1, fileName);
                preparedStatement.setString(2, userID);

                preparedStatement.executeUpdate();
            }
            return exists;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException e) {
            System.out.println("Couldn't register file. \n" + e);
            return false;
        }
    }

    //Returns information on who owns the file.
    @Override
    public String requestFile(String fileName) {

        String result = "";
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            con = DriverManager.getConnection(url, user, password);

            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT userFiles.userID,userFiles.Files,users.ComputerName FROM userFiles,users WHERE Files = '" + fileName + "' AND userFiles.userID = users.userID");
            while (rs.next()) {
                if (rs.getString("Files").equals(fileName)) {

                    result = rs.getString("userFiles.userID") + "\n" + rs.getString("users.ComputerName");
                }
            }
            return result;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException e) {
            System.out.println("Couldn't request file. \n" + e);
            return result;
        }
    }

    //This returns all the active files on the database.
    @Override
    public String requestFileList() {

        String resultList = "";
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            con = DriverManager.getConnection(url, user, password);

            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT Files FROM userFiles");
            while (rs.next()) {
                resultList += rs.getString("Files") + "\n";
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException e) {
            System.out.println("Couldn't register file. \n" + e);
            return "Something went wrong.";
        } finally {
            if (resultList.equals("")) {
                return "There are currently no files uploaded.";
            } else {
                return resultList;
            }
        }
    }

    @Override
    public String filePath(String serverName, String fileName) {
        return null;
    }

    //Turn the file into a string to send back to user
    @Override
    public String sendFile(String fileName) {

        String path = "";
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            con = DriverManager.getConnection(url, user, password);
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT FilePath FROM userFiles WHERE Files = '" + fileName + "'");
            while (rs.next()) {
                path = rs.getString("FilePath");
            }
        } catch (ClassNotFoundException | SQLException | InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(P2PObject.class.getName()).log(Level.SEVERE, null, ex);
        }

        return path;

    }

}
