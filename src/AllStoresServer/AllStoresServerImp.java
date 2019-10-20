package AllStoresServer;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import AllStoresServer.Interfaces.AllStoresServerInterface;
import DatabaseServer.DatabaseImpl;
import DatabaseServer.Product;
import DatabaseServer.Reservation;
import DatabaseServer.Interfaces.*;

public class AllStoresServerImp extends UnicastRemoteObject implements AllStoresServerInterface {

	private String databaseHost;
	private int databasePort;

	public AllStoresServerImp(String databaseHost, int databasePort) throws Exception {
		this.databaseHost = databaseHost;
		this.databasePort = databasePort;
	}

	/*
	 * Looks up and connects to the registry
	 */
	private IDataBase connectToDatabaseServer() throws RemoteException, NotBoundException {
		Registry registry = LocateRegistry.getRegistry(this.databaseHost, this.databasePort);
		return (IDataBase) registry.lookup("AllstoresDatabaseServer");
	}

	public String addReservation(int storeID, int productID, int quantity, int clientID) throws RemoteException {

		IDataBase connectionDB;
		StringBuilder message = new StringBuilder();
		try {
			connectionDB = connectToDatabaseServer();

			List<Product> shopProducts = connectionDB.getShopProducts(storeID);

			// find the product
			for (Product p : shopProducts) {
				if (productID == p.getProductID()) {
					// verify if there is enough quantity to reserve
					if (p.getAvailable() >= quantity) {
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
						int remainingAvailable = p.getAvailable() - quantity;
						message.append(String.format("Remaining stock: %d", remainingAvailable));
						return message.toString();
					// not enough stock to fulfill this reservation request
					}  else {
						message.append("There's no available stock to fulfill your request.");
						message.append(String.format("Remaining stock: %d", p.getAvailable()));
						return message.toString();
					}
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
		IDataBase connectionBD;
		boolean result = false;
		List<String> returnReserves = new ArrayList<>();

		try {
			connectionBD = connectToDatabaseServer();

			// obtem todas as reservas
			List<Reservation> clientReservations = connectionBD.getClientReservations(clientID);
			
			if(clientReservations.size() != 0) {
				for (Reservation r : clientReservations) {
					returnReserves.add(String.format("Product ID %d: canceled %d products.", r.getProductID(),
							r.getQuantity()));
				}
				result = connectionBD.cancelAllReservations(clientID);
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
		IDataBase connectionDB;
		List<String> stockList = new ArrayList<String>();

		try {
			connectionDB = connectToDatabaseServer();
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
		IDataBase connectionDB;

		try {
			connectionDB = connectToDatabaseServer();
			List<Product> prodList = connectionDB.getShopProducts(storeID);

			// verifica se o produto existe nessa loja
			for (Product p : prodList) {
				if (productID == p.getProductID()) {

					// verifica se o cliente já tem reservas desse produto e
					// se tiver, verifica se é em menor ou maior quantidade da pretendida
					Reservation reservedProduct = connectionDB.findClientReservation(clientID, storeID, productID);

					if (reservedProduct != null) {

						// se qnt reservado > qnt, compra o produto e atualiza a reserva
						if (reservedProduct.getQuantity() > quantity) {

							Boolean check = connectionDB.buyProduct(storeID, productID, quantity, clientID);
							
							if(check) {
								return "Compra efetuada com sucesso! Unidades ainda disponíveis: " + reservedProduct.getQuantity()
									+ " reservadas e " + p.getAvailable() + " em stock.";
							}

						} else { // se qnt reservado <= qnt, verifica se há a diferença em loja
							if (quantity - reservedProduct.getQuantity() <= p.getAvailable()) {

								Boolean check = connectionDB.buyProduct(storeID, productID, quantity, clientID);

								if(check) {
									return "Compra efetuada com sucesso! Unidades ainda disponíveis: 0 reservadas e "
											+ p.getAvailable() + " em stock.";
								}

							} else {
								return "Erro: Não é possível efetuar a compra! Tem " + reservedProduct.getQuantity()
										+ " unidades desse produto reservado e existem " + p.getAvailable()
										+ " unidades disponíveis em stock.";
							}
						}

					} else { // se não tiver nenhuma reserva desse produto, verifica se existe a quantidade
								// em loja e efetua ou não a compra
						if (quantity <= p.getAvailable()) {

							Boolean check = connectionDB.buyProduct(storeID, productID, quantity, clientID);

							if(check) {
								return "Compra efetuada com sucesso! Unidades ainda disponíveis: " + p.getAvailable();
							}

						} else {
							return "Erro: só existem " + p.getAvailable() + " unidades desse produto em stock!";
						}
					}
				}
			}
			return "Esse produto não existe nessa loja!";

		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD!");
			e.printStackTrace();
		}
		return " ";
	}

	public List<String> buyAll(int clientID) throws RemoteException {
		IDataBase connectionDB;
		List<String> soldList = new ArrayList<String>();

		try {
			connectionDB = connectToDatabaseServer();

			List<Reservation> reservedList = connectionDB.getClientReservations(clientID);

			if (reservedList != null) {
				for (Reservation r : reservedList) { // para cada reserva do cliente, efetua a compra
					int prod = r.getProductID();
					int qnt = r.getQuantity();

					connectionDB.buyProduct(r.getShopID(), r.getProductID(), r.getQuantity(), clientID);

					String info = "Produto " + prod + ", " + qnt + "unidades.";
					soldList.add(info);
				}

				return soldList;

			} else {

				soldList.add(" ");
				return soldList;

			}

		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD!");
			e.printStackTrace();
		}

		return null;
	}

}
