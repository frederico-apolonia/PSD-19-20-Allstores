package DatabaseServer;

import DatabaseServer.Interfaces.IDataBase;
import ZooKeeper.ZooKeeperConnector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Enumeration;
import java.util.Scanner;

// import zookeeper classes
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

public class DatabaseServer {

    private static final String FILE_SEPARATOR = File.separator;
    // property fetches the home path
    private static final String ZK_PATH = System.getProperty("user.home")
            + FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;

    private static ZooKeeperConnector zooKeeperConnector = new ZooKeeperConnector();

    public static void main(String[] args) throws Exception {
        // connect and register in zookeeper
        String zooKeeperHost = getZooKeeperHost();
        ZooKeeper zooKeeper = zooKeeperConnector.connect(zooKeeperHost);

        Stat appStat = zooKeeper.exists("/db/clients", false);
        if (appStat == null) {
            zooKeeper.create("/db", "15500".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            zooKeeper.create("/db/clients", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            zooKeeper.create("/db/shared", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            appStat = zooKeeper.exists("/db/clients", false);
        }

        String znodePath = zooKeeper.create("/db/clients/", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        String[] znodePathSplit = znodePath.split("/");
        String zooKeeperIdString = znodePathSplit[znodePathSplit.length - 1];
        System.out.println("ID: " + zooKeeperIdString);
        int zooKeeperId = Integer.parseInt(zooKeeperIdString.replaceFirst("^0+(?!$)", ""));
        int basePort = Integer.parseInt(new String(zooKeeper.getData("/db", false, appStat)));
        int port = basePort + zooKeeperId;
        String host = getInetAddress();
        //String host = InetAddress.getLocalHost().getHostAddress();

        // port must be higher than 15500 so it doesn't collide with AllStoresApp
        assert port > 15500;

        zooKeeper.setData(znodePath, String.format("%s:%d", host, port).getBytes(),
                zooKeeper.exists(znodePath, false).getVersion());

        IDataBase database = new DatabaseImpl(zooKeeper, zooKeeperId);

        /* Create registry and rebind it to port DATABASE_PORT */
        Registry registry = null;
        try {
            registry = LocateRegistry.createRegistry(port);
            registry.rebind("AllstoresDatabaseServer", database);
        } catch (Exception e) {
            System.err.println("DataBase: ERROR trying to start server");
            e.printStackTrace();
            System.exit(0);
        }

        String myID = zooKeeperHost + ":" + "AllstoresDatabaseServer";
        System.out.println(myID);
    }

    private static String getInetAddress() throws IOException {
    	String networkInterface = getNetworkInterface();
    	String result = InetAddress.getLocalHost().getHostAddress();
    	
    	Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
        for (; n.hasMoreElements();)
        {
                NetworkInterface e = n.nextElement();
                if(e.getName().equals(networkInterface)) {
                	Enumeration<InetAddress> addresses = e.getInetAddresses();
                	addresses.nextElement();
                	result = addresses.nextElement().getHostAddress();
                }
        }
        
        return result;
	}

	private static String getZooKeeperHost() throws IOException {
        BufferedReader fileReader = new BufferedReader(new FileReader(ZK_PATH + "conf.cfg"));
        String result = fileReader.readLine();
        fileReader.close();
        return result;
    }
    
    private static String getNetworkInterface() throws IOException {
    	// BufferedReader fileReader = new BufferedReader(new FileReader(ZK_PATH + "conf.cfg"));
    	Scanner sc = new Scanner(new FileReader(ZK_PATH + "conf.cfg"));
    	sc.nextLine();
    	String result = null;
    	if (sc.hasNext()) {
    		result = sc.nextLine();
    	}
    	sc.close();
    	return result;
    }

}
