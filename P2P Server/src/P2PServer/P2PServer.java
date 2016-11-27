/*
AUTHOR: Nickolas Reid

Purpose: This is the main server of the P2P application. This server only acts as an adminstrator.
         It supplies clients with data to connect to one another and which files are on the network.
         It does not provide users with any files.

Methods:

Main:
Is the first and only method to be called. It creates an orb based on the arguements passed into it. The basic orb is then passed into a P2PObject
the P2PObject will contain the methods needed for the server to run. The P2PObject is given a name to be referenced by and finally is set to listen
for anyone to contact it.

*/



package P2PServer;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import Peer2Peer.*;

public class P2PServer {

    public static void main(String args[]) {
        try {
            // create the orb
            ORB orb = ORB.init(args, null);
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            // create servant and register it with the main ORB
            P2PObject p2pObj = new P2PObject();
            p2pObj.setORB(orb);

            // grab the refence.
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(p2pObj);
            P2P href = P2PHelper.narrow(ref);

            
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            //Name the server MainServer
            NameComponent path[] = ncRef.to_name("MainServer");
            ncRef.rebind(path, href);

            //Display that the user is alive.
            System.out.println("P2P Server ready and waiting for clients!");

            // wait for invocations from clients
            for (;;) {
                orb.run();
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e);
            e.printStackTrace(System.out);
        }

    }
}
