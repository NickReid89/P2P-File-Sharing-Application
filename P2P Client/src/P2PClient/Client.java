/*
 Author: Nickolas Reid
 Application: Client of Peer to Peer Server

 Purpose: To connect to a main server, to gain information about a network
 and transfer files between clients anonymously.
 */
package P2PClient;

import P2PClient.Peer2Peer.P2P;
import P2PClient.Peer2Peer.P2PHelper;
import java.io.*;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.omg.CORBA.ORB;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.NamingContextPackage.CannotProceed;
import org.omg.CosNaming.NamingContextPackage.NotFound;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import org.omg.PortableServer.POAManagerPackage.AdapterInactive;
import org.omg.PortableServer.POAPackage.ServantNotActive;
import org.omg.PortableServer.POAPackage.WrongPolicy;

public final class Client extends javax.swing.JFrame {

    private ORB clientToServerORB;
    private String clientName;
    private String compName;
    private P2P serverRequests;
    String serverName;

    public Client() {
        //Set up GUI
        initComponents();
        //Ask the user where they're connection to.
        serverName = (String) JOptionPane.showInputDialog(null, "What is the servers name or ip address?( do not type localhost or 127.0.0.1)", JOptionPane.PLAIN_MESSAGE);
        //Connection properties to start the server orb.
        String[] connectToMain = {"-ORBInitialPort", "1050", "-ORBInitialHost", serverName};

        try {
            //This connects me to the main server.
            clientToServerORB = ORB.init(connectToMain, null);
            org.omg.CORBA.Object objRef;
            objRef = clientToServerORB.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            //Using the naming server I set the main server to be identified as MainServer so that it stands out.
            serverRequests = (P2P) P2PHelper.narrow(ncRef.resolve_str("MainServer"));
            //Ask the user who the files should be associated with.(No one ever sees this username, it is only for DB identification.
            clientName = (String) JOptionPane.showInputDialog(null, "What would you like your username to be?", JOptionPane.PLAIN_MESSAGE);
            //This is so client orbs will know who to connect to.
            compName = (String) JOptionPane.showInputDialog(null, "What is your computer name or internal IP address?(do not put localhost or 127.0.0.1)", JOptionPane.PLAIN_MESSAGE);
            //If the IP address is wrong the computer will ask for a new one.
            while (compName.equals("localhost") || compName.equals("127.0.0.1")) {
                compName = (String) JOptionPane.showInputDialog(null, "Please correct your computer name or internal IP address?(do not put localhost or 127.0.0.1", JOptionPane.PLAIN_MESSAGE);
            }
            //If the user name is taken , the computer will keep asking.
            while (serverRequests.registerIP(clientName, compName) != true) {
                clientName = (String) JOptionPane.showInputDialog(null, "That name is taken. Please try again.", JOptionPane.PLAIN_MESSAGE);

            }
            //This adds to the x button the screen. When pressed it erases a users identity.
            modifyExitButton();
            //Welcome the user.
            this.setTitle("Welcome to COMP489 Assignment Two: " + clientName);
            //Tell the user they are connected to the server.
            jtxtEvents.append(clientName + " has connected to the server.\n");

            /*
             This is a little listener orb for a client. This is important, as it listens for other clients. If a different client
             wants a file from this client, this listener needs to hear the request and transfer the data to that user. However, since there
             could be multiple clients, I have made an executor service to ensure multiple clients can contact each other. Since the probability of
             many users in this specific application is small, I have left the pool size as 10.
             */
            //This holds the executor threads
            Thread listener = new Thread() {
                @Override
                public void run() {
                    //Create the thread pool
                    ExecutorService listeners = Executors.newFixedThreadPool(10);
                    //execute the threads
                    listeners.execute(() -> {
                       // Create the listeners.
                        try {
                            listenerORB(clientName);
                        } catch (ServantNotActive | WrongPolicy ex) {
                            jtxtEvents.setText("The listener orb could not be started. Make sure orbd is started and restart the program.");
                        }
                    });

                }
            };
            listener.start();
            //Tell the user the application is ready to listen for requests.
            jtxtEvents.append(clientName + " Client listener has begun.\n");
            jFileList.doClick();
        } catch (InvalidName | NotFound | CannotProceed | org.omg.CosNaming.NamingContextPackage.InvalidName ex) {
            //Inform the user if the application can't even connect to the main server.
            jtxtEvents.setText("Application cannot connect to server. Please start the server and restart application.");
        }
    }

    //This is what listens for requests to send data to clients.
    public void listenerORB(String orbName) throws ServantNotActive, WrongPolicy {

        try {
            //Users connection string.
            String[] listenerArgs = {"-ORBInitialPort", "1050", "-ORBInitialHost", "localhost"};
            // create and initialize the orb
            ORB orb = ORB.init(listenerArgs, null);
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            // create servant and register it with the orb
            P2PObject p2pObj = new P2PObject();
            p2pObj.setORB(orb);

            // get object reference from the servant
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(p2pObj);
            P2P href = P2PHelper.narrow(ref);

            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            //This is using the clients name for the naming service of the orb. 
            NameComponent path[] = ncRef.to_name(orbName);
            ncRef.rebind(path, href);

            // wait for client requests.
            for (;;) {
                orb.run();
            }
        } catch (AdapterInactive | org.omg.CORBA.ORBPackage.InvalidName | org.omg.CosNaming.NamingContextPackage.InvalidName | NotFound | CannotProceed ex) {
            jtxtEvents.setText("Something went wrong. Please restart the client.");
        }

    }

    //While not fullproof this cleans up after a user is finished. One problem I encountered
    //is if the user forces the end of the application via task manager. The data is still alive
    //but not connected to any client. 
    public void modifyExitButton() {
        //Adds a new listener for when the user exits.
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                //When the user goes to exit, it wil delete the users data.
                serverRequests.deleteUser(clientName);

            }
        });
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jbtnAdd = new javax.swing.JButton();
        jbtnRemove = new javax.swing.JButton();
        jFileList = new javax.swing.JButton();
        jbtnRequest = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jtxtEvents = new javax.swing.JTextArea();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        jListIems = new javax.swing.JList();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jbtnAdd.setFont(new java.awt.Font("Times New Roman", 0, 18)); // NOI18N
        jbtnAdd.setText("Add File");
        jbtnAdd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbtnAddActionPerformed(evt);
            }
        });

        jbtnRemove.setFont(new java.awt.Font("Times New Roman", 0, 18)); // NOI18N
        jbtnRemove.setText("Remove File");
        jbtnRemove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbtnRemoveActionPerformed(evt);
            }
        });

        jFileList.setFont(new java.awt.Font("Times New Roman", 0, 18)); // NOI18N
        jFileList.setText("File List");
        jFileList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jFileListActionPerformed(evt);
            }
        });

        jbtnRequest.setFont(new java.awt.Font("Times New Roman", 0, 18)); // NOI18N
        jbtnRequest.setText("Request File");
        jbtnRequest.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbtnRequestActionPerformed(evt);
            }
        });

        jtxtEvents.setColumns(20);
        jtxtEvents.setRows(5);
        jScrollPane1.setViewportView(jtxtEvents);

        jLabel1.setFont(new java.awt.Font("Times New Roman", 0, 24)); // NOI18N
        jLabel1.setText("File List:");

        jLabel2.setFont(new java.awt.Font("Times New Roman", 0, 24)); // NOI18N
        jLabel2.setText("Client Events:");

        jListIems.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jListIems.setToolTipText("");
        jScrollPane3.setViewportView(jListIems);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jbtnAdd)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jbtnRemove)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jFileList)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jbtnRequest))
                    .addComponent(jScrollPane1)
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane3)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 209, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jbtnAdd)
                            .addComponent(jbtnRemove)
                            .addComponent(jFileList)
                            .addComponent(jbtnRequest)))
                    .addComponent(jScrollPane3)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jbtnAddActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbtnAddActionPerformed

        //Asks the user which file they would like to add.
        JFileChooser jfc = new JFileChooser();
        //Puts them in their home directory.
        jfc.setCurrentDirectory(new File(System.getProperty("user.home")));
        //Grabs whether the user picked a file.
        int result = jfc.showOpenDialog(this);
        //The file name is to show users what files are avilable and 
        //The file path is to tell an orb where to find the file. 
        String fileName = "";
        String filePath = "";
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = jfc.getSelectedFile();
            filePath = selectedFile.getAbsolutePath();
            fileName = selectedFile.getName();
        }
        //If neither are empty the file should be good.
        if (!fileName.equals("") || !fileName.equals("")) {
            //Attempt to register file.
            if (serverRequests.registerFile(fileName, filePath, clientName)) {
                jtxtEvents.append("The file: " + fileName + "was successfully registered!\n");
            } else {
                jtxtEvents.append("The file: " + fileName + "was not registered!\n");
            }
        }
        jFileList.doClick();
    }//GEN-LAST:event_jbtnAddActionPerformed

    private void jbtnRemoveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbtnRemoveActionPerformed
        /*
         This method removes a file
         */

        //Grab file name.
        String fileToRemove = (String) JOptionPane.showInputDialog(null, "Which file would you like to unregister?", JOptionPane.PLAIN_MESSAGE);

        //If it exists, tell user, if not tell them.
        if (serverRequests.removeFile(fileToRemove, clientName)) {
            jtxtEvents.append(fileToRemove + " has been successfully unregistered.\n");
        } else {
            jtxtEvents.append(fileToRemove + " has not been unregistered please make sure it exists.\n");
        }
        
        jFileList.doClick();
    }//GEN-LAST:event_jbtnRemoveActionPerformed

    private void jFileListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jFileListActionPerformed

        //This grabs all the files on the server, then it puts them in an array, 
        //adds the items to a list model and finally updates the list to show the user the files.
        String list = serverRequests.requestFileList();
        String lines[] = list.split("\\n");
        DefaultListModel listModel = new DefaultListModel();
        for (String line : lines) {
            listModel.addElement(line);
        }
        jListIems.setModel(listModel);
        jListIems.updateUI();
    }//GEN-LAST:event_jFileListActionPerformed

    private void jbtnRequestActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbtnRequestActionPerformed
        try {

            String fileToDownload;
            //Make sure the user selected a file.
            if (jListIems.getSelectedIndex() == -1) {
                jtxtEvents.append("Please select a file before pressing request!\n");
            } else {
                //Grab the items name
                fileToDownload = (String) jListIems.getSelectedValue();
                //Grabs the clients name and ip who owns the file
                String[] orbInfo = serverRequests.requestFile(fileToDownload).split("\\n");
                //If nothing is returned it means the file is no longer there.
                if (orbInfo[0].equals("") || orbInfo[1].equals("")) {
                    JOptionPane.showMessageDialog(null, "Sorry, that file is no longer available.");
                    //update client list.
                    jFileList.doClick();
                } else {
                    //Set up connection to client orb.
                    String[] clientConnect = {"-ORBInitialPort", "1050", "-ORBInitialHost", orbInfo[1]};

                    ORB orb = ORB.init(clientConnect, null);
                    org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
                    NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
                    //The client who owns the file name will be at orbInfo[0]
                    P2P clientObj = (P2P) P2PHelper.narrow(ncRef.resolve_str(orbInfo[0]));

                    //Grab the file that's been turned into a string and turn it back into bytes.
                    byte[] decoded = Base64.getDecoder().decode(clientObj.filePath(serverName, fileToDownload));

                    //Write the file
                    try ( //Write the file
                            //Set up where the file is written. In this case it'll be in the same place as where the project is.
                            FileOutputStream fos = new FileOutputStream(fileToDownload)) {
                        //Write the file
                        fos.write(decoded);
                        //Close fileoutput stream
                    }
                    jtxtEvents.append("File has been successfully downloaded \n");
                }
            }
        } catch (InvalidName | NotFound | CannotProceed | org.omg.CosNaming.NamingContextPackage.InvalidName ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        } 
        jFileList.doClick();
    }//GEN-LAST:event_jbtnRequestActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;

                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Client.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> {
            new Client().setVisible(true);
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jFileList;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JList jListIems;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JButton jbtnAdd;
    private javax.swing.JButton jbtnRemove;
    private javax.swing.JButton jbtnRequest;
    private javax.swing.JTextArea jtxtEvents;
    // End of variables declaration//GEN-END:variables
}
