package AllStoresServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.rmi.registry.LocateRegistry;
import AllStoresServer.Interfaces.AllStoresServerInterface;
import ZooKeeper.*;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.rmi.registry.Registry;
import java.util.Enumeration;
import java.util.Scanner;


public class Server {

	private static ZooKeeperConnector zooKeeperConnector = new ZooKeeperConnector();
	private static final String FILE_SEPARATOR = File.separator;
	// property fetches the home path
	private static final String ZK_PATH = System.getProperty("user.home")
			+ FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;

	public static void main(String[] args) throws Exception {

		String zooKeeperHost = getZooKeeperHost();
		ZooKeeper zooKeeper = zooKeeperConnector.connect(zooKeeperHost);

		Stat appStat = zooKeeper.exists("/app", false);
		if (appStat == null) {
			zooKeeper.create("/app", "15000".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			appStat = zooKeeper.exists("/app", false);
		}

		String znodePath = zooKeeper.create("/app/", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
		String[] znodePathSplit = znodePath.split("/");

		String zooKeeperIdString = znodePathSplit[znodePathSplit.length - 1];
		int zooKeeperId = Integer.parseInt(zooKeeperIdString.replaceFirst("^0+(?!$)", ""));
		int basePort = Integer.parseInt(new String(zooKeeper.getData("/app", false, appStat)));
		int port = basePort + zooKeeperId;
		String host = getInetAddress();
		
		// port must be lower than 15500 so it doesn't collide with DB
		assert port < 15500;

		zooKeeper.setData(znodePath, String.format("%s:%d", host, port).getBytes(),
				zooKeeper.exists(znodePath, false).getVersion());

		AllStoresServerInterface testing = new AllStoresServerImp(zooKeeper);
		
		Registry registry =  null;
		try {
			registry = LocateRegistry.createRegistry(port);
			registry.rebind("ClientServerInterface", testing);
			System.out.println("Server ready!");
		}
		catch (Exception e) {
			System.err.println("Server exception: Error trying to start.");
			System.exit(0);
		}

		String myID = host + ":" + "ClientServerInterface";
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
                System.out.println("Interface: " + e.getName());
                Enumeration<InetAddress> a = e.getInetAddresses();
                for (; a.hasMoreElements();)
                {
                        InetAddress addr = a.nextElement();
                        System.out.println("  " + addr.getHostAddress());
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
