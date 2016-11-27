/*
AUTHOR: Nickolas Reid
Student ID: 3176950
Course: COMP 489

Purpose: This class implements the interface from the P2P.idl file. This file may look weird however. 
         The reason almost all of my methods are stripped, is that it condenses two files into one. I could
         have created a seperate client.idl file, but it would only have one method. Instead, I took the one
         method, added it to P2P.idl and stripped all the methods and the database connectivity information to
         prevent potential security risks. The only method the client should use is. filePath(). This creates an
         ORB that connects to the main server which grabs the file path for the client. The client can then send the 
         data to the user who requested the file.

Methods:

boolean registerIP(in string name,in string ip);
Only used by main server.

boolean registerFile(in string fileName,in string fileLocation, in string user);
Only used by main server.

boolean removeFile(in string fileName, in string userID);
Only used by main server.

string requestFile(in string fileName);
Only used by main server.

string requestFileList();
Only used by main server.

void deleteUser(in string userName);
Only used by main server.

string sendFile(in string fileName);
Only used by main server, this  returns the filepath of a file for ClientB to know where the file is to send to Client A

string filePath(in string serverName, in string fileName);
Called by clientA to ClientB. ClientB turns a file into a String and sends it to Client A.

void shutdown();



*/


package P2PClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.omg.CORBA.ORB;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.NamingContextPackage.CannotProceed;
import org.omg.CosNaming.NamingContextPackage.NotFound;

import P2PClient.Peer2Peer.P2P;
import P2PClient.Peer2Peer.P2PHelper;
import P2PClient.Peer2Peer.P2PPOA;

public class P2PObject extends P2PPOA {

    private ORB orb;

    //give the orb its information.
    public void setORB(ORB orb_val) {
        orb = orb_val;
    }

    //Shut down the orb.
    public void shutdown() {

        orb.shutdown(false);
    }

    //Grab the file data to send to a user.
    @Override
    public String filePath(String serverName, String fileName) {
        try {
            //Connection to main
            String[] connectToMain = {"-ORBInitialPort", "1050", "-ORBInitialHost", serverName};

            //This connects me to the main server.
            ORB callToMain = ORB.init(connectToMain, null);
            org.omg.CORBA.Object objRef;
            objRef = callToMain.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            P2P fileQuery = (P2P) P2PHelper.narrow(ncRef.resolve_str("MainServer"));

            String encoded = "";
            //read all content in file
            byte[] blines;
            try {
                blines = Files.readAllBytes(Paths.get(fileQuery.sendFile(fileName)));
                //Encode string
                encoded = Base64.getEncoder().encodeToString(blines);
            } catch (IOException ex) {
                Logger.getLogger(P2PObject.class.getName()).log(Level.SEVERE, null, ex);
            }

            return encoded; //Return the file as string to the client

        } catch (InvalidName | NotFound | CannotProceed | org.omg.CosNaming.NamingContextPackage.InvalidName ex) {
            Logger.getLogger(P2PObject.class.getName()).log(Level.SEVERE, null, ex);
        }

        return "";
    }
    
    public void deleteUser(String userName) {

    }

    public boolean registerIP(String name, String ip) {
        return false;

    }

    public boolean registerFile(String fileName, String fileLocation, String userID
    ) {
        return false;
    }

    public boolean removeFile(String fileName, String userID
    ) {
        return false;
    }

    public String requestFile(String fileName
    ) {
        return null;
    }

    public String requestFileList() {
        return null;
    }

    

    @Override
    public String sendFile(String fileName) {

        return null;

    }

}