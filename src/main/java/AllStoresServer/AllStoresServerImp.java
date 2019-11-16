package AllStoresServer;

import AllStoresServer.Interfaces.AllStoresServerInterface;
import DatabaseServer.Interfaces.IDataBase;
import ZooKeeper.ZooKeeperConnector;
import DatabaseServer.Product;
import DatabaseServer.Reservation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class AllStoresServerImp extends UnicastRemoteObject implements AllStoresServerInterface {

	private ZooKeeper zooKeeper;

	private static final int NUMBER_OF_STORES = 600;
	private static final String FILE_SEPARATOR = File.separator;
	// property fetches the home path
	private static final String ZK_PATH = System.getProperty("user.home")
			+ FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;

	public AllStoresServerImp(ZooKeeper zooKeeper) throws Exception {
		this.zooKeeper = zooKeeper;
	}

	private List<String> getNumberOfChildren() {
		try {
			Stat stat = zooKeeper.exists(ZK_PATH.concat("db/clients"), false);
			if(stat != null) {
				// fetch all children from /db
				List<String> childrenList = zooKeeper.getChildren(ZK_PATH.concat("db/clients"), false);
				Collections.sort(childrenList);

				return childrenList;
			} else { System.out.println("Node does not exist."); } 
		} catch (Exception e) { System.out.println(e.getMessage()); }

		return null;
	}

	private String findDatabaseServer(int storeID) throws RemoteException, NotBoundException {
		try {
			List<String> children = getNumberOfChildren();

			int inf = 0;
			for (int i = 1; i <= children.size(); i++) {
				int sup = (NUMBER_OF_STORES * i) / children.size();

				if(storeID > inf && storeID <= sup) {
					//get the znode related to the storeID
					String znode = children.get(i-1);
					return znode;
				} else {
					inf = sup;
				}
			}
		} catch (Exception e) { }

		return null;
	}

	/*
	 * Looks up and connects to the registry
	 */
	private IDataBase connectToDatabaseServer(String znode) throws RemoteException, NotBoundException {

		try {
			//get the data associated with znode, that will give "host:port" of db server
			byte[] bp = zooKeeper.getData(ZK_PATH.concat("db/clients/").concat(znode), false, null);
			String s = new String(bp);

			//com setData(path, "host:port".getBytes(), version) no znode do servidor db quando se conecta??
			String[] data = s.split(":");

			if(data.length == 2) {
				String databaseHost = data[0];
				int databasePort = Integer.parseInt(data[1]);

				Registry registry = LocateRegistry.getRegistry(databaseHost, databasePort);
				return (IDataBase) registry.lookup("AllstoresDatabaseServer");
			}
		} catch (Exception e) { System.out.println(e.getMessage()); }

		return null;
	}

	public String addReservation(int storeID, int productID, int quantity, int clientID) throws RemoteException {
		String znodeServer;
		IDataBase connectionDB;
		StringBuilder message = new StringBuilder();
		
		try {
			znodeServer = findDatabaseServer(storeID);
			connectionDB = connectToDatabaseServer(znodeServer);

			// find the product
			Product product = getProductFromList(connectionDB.getShopProducts(storeID), productID);
			if(product != null) {
				// verify if there is enough quantity to reserve
				if (product.getAvailable() >= quantity) {
					// check if the client already has a current reservation for this product
					Reservation reservation = connectionDB.findClientReservation(clientID, storeID, productID);
					if (reservation != null) {
						int updateQuantity = reservation.getQuantity() + quantity;
						if(connectionDB.updateClientReservation(reservation, updateQuantity)) {
							message.append("Reservation updated with success.\n");
						} else {
							message.append("There was a problem while updating your reservation.\n");
						}
						// client doesn't have an existing reservation for this product
					} else {
						reservation = new Reservation(clientID, storeID, productID, quantity);

						if(connectionDB.addReservation(reservation)) {
							message.append("New reservation added with success.\n");
						} else {
							message.append("There was a problem while adding your reservation.\n");
						}
					}
					int remainingAvailable = product.getAvailable() - quantity;
					message.append(String.format("Remaining stock: %d", remainingAvailable));
					return message.toString();
					// not enough stock to fulfill this reservation request
				}  else {
					message.append("There's no available stock to fulfill your request.");
					message.append(String.format("Remaining stock: %d", product.getAvailable()));
					return message.toString();
				}
			}
			return message.append(String.format("Product %d doesn't exist", productID)).toString();

		} catch (Exception e) {
			System.err.println("Something happened while exchanging messages with the DB Server!");
			e.printStackTrace();
		}
		return message.append("Server is Down.").toString();
	}


	public List<String> cancel(int clientID) throws RemoteException {
		IDataBase connectionDB;
		boolean result = false;
		List<String> returnReserves = new ArrayList<>();

		try {
			List<String> children = getNumberOfChildren();
			for (int i = 0; i < children.size(); i++) {
				connectionDB = connectToDatabaseServer(children.get(i));

				List<Reservation> clientReservations = connectionDB.getClientReservations(clientID);

				if(clientReservations.size() != 0) {
					for (Reservation r : clientReservations) {
						returnReserves.add(String.format("Product ID %d: canceled %d products.", r.getProductID(),
								r.getQuantity()));
					}
					result = connectionDB.cancelAllReservations(clientID);
				}
			} 
		} catch (Exception e) {
			System.err.println("Error while connecting to Database Server");
			e.printStackTrace();
		}

		return returnReserves;

	}

	public List<String> getList(int storeID) throws RemoteException {
		// devolve uma lista com a informação de cada produto existente na loja
		// <storeID>
		String znodeServer;
		IDataBase connectionDB;
		List<String> stockList = new ArrayList<String>();

		try {
			znodeServer = findDatabaseServer(storeID);
			connectionDB = connectToDatabaseServer(znodeServer);

			List<Product> listaProd = connectionDB.getShopProducts(storeID); // vai buscar a lista de produtos da loja
			// pedida
			for (Product p : listaProd) {
				stockList.add(p.toString()); // para cada produto na lista vai buscar a sua informação completa (String)
			}
			return stockList;
		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD!");
			e.printStackTrace();
		}
		return stockList;
	}


	public String buy(int clientID, int storeID, int productID, int quantity) throws RemoteException {
		String znodeServer;
		IDataBase connectionDB;
		StringBuilder sb = new StringBuilder();

		try {
			znodeServer = findDatabaseServer(storeID);
			connectionDB = connectToDatabaseServer(znodeServer);
			Product product = getProductFromList(connectionDB.getShopProducts(storeID), productID);

			if(product != null) {
				// verifica se o cliente já tem reservas desse produto e
				// se tiver, verifica se é em menor ou maior quantidade da pretendida
				Reservation reservedProduct = connectionDB.findClientReservation(clientID, storeID, productID);

				// cliente tem reserva
				if (reservedProduct != null) {
					int shopRemainingAvailable = product.getAvailable();
					int reservationRemainingAvailable = reservedProduct.getQuantity() - quantity;
					// se qnt reservado > qnt, compra o produto e atualiza a reserva
					if (reservedProduct.getQuantity() > quantity) {

						if (connectionDB.buyProduct(storeID, productID, quantity, clientID)) {
							sb.append(String.format("Product bought with success! You still have %d reserved units and there " +
									"are %d units available on the shop.",reservationRemainingAvailable, shopRemainingAvailable));
						} else {
							sb.append("Something happened while your product was being bought. Try again later.");
						}

					} else { // se qnt reservado <= qnt, verifica se há a diferença em loja
						if (quantity - reservedProduct.getQuantity() <= product.getAvailable()) {

							if (connectionDB.buyProduct(storeID, productID, quantity, clientID)) {
								sb.append(String.format("Product bought with success! You have 0 reserved units and there" +
										" are %d units available on the shop.",shopRemainingAvailable));
							} else {
								sb.append("Something happened while your product was being bought. Try again later.");
							}

						} else {
							sb.append(String.format("ERROR! You're trying to buy more than what's available! There's %d available " +
									"units reserved and there are %d products in stock.", reservedProduct.getQuantity(), product.getAvailable()));
						}
					}

				} else { // se não tiver nenhuma reserva desse produto, verifica se existe a quantidade
					// em loja e efetua ou não a compra
					if (quantity <= product.getAvailable()) {

						if(connectionDB.buyProduct(storeID, productID, quantity, clientID)) {
							sb.append(String.format("Product bought with success! Number of remaining units: %d ", product.getAvailable()));
						}

					} else {
						sb.append(String.format("ERROR! You're trying to buy more than what's currently available! There " +
								"are %d units left in stock", product.getAvailable()));
					}
				}
			} else {
				sb.append("ERROR! The product that you're trying to buy doesn't exist!");
			}

		} catch (Exception e) {
			sb.append("Error while connecting to the Database server, try again later.");
			e.printStackTrace();
		}
		return sb.toString();
	}


	private Product getProductFromList(List<Product> prodList, int productID) {
		Product result = null;
		for (Product p : prodList) {
			if (productID == p.getProductID()) {
				result = p;
				break;
			}
		}
		return result;
	}

	public List<String> buyAll(int clientID) throws RemoteException {
		IDataBase connectionDB;
		List<String> soldList = new ArrayList<String>();

		try {
			List<String> children = getNumberOfChildren();
			for (int i = 0; i < children.size(); i++) {
				connectionDB = connectToDatabaseServer(children.get(i));

				List<Reservation> reservedList = connectionDB.getClientReservations(clientID);

				if (reservedList != null) {
					for (Reservation r : reservedList) { // para cada reserva do cliente, efetua a compra
						int prod = r.getProductID();
						int qnt = r.getQuantity();

						connectionDB.buyProduct(r.getShopID(), r.getProductID(), r.getQuantity(), clientID);

						String info = "Produto " + prod + ", " + qnt + "unidades.";
						soldList.add(info);
					}
				} else {
					soldList.add(" ");
				}
			}
			return soldList;
		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD!");
			e.printStackTrace();
		}

		return null;
	}
}
