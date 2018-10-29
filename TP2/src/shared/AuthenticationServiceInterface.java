package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface AuthenticationServiceInterface extends Remote {
    void registerOperationServer(OperationServerSharedInfo operationServerInfo) throws RemoteException;
    void unregisterOperationServer(OperationServerSharedInfo operationServerInfo) throws RemoteException;
    ArrayList<OperationServerSharedInfo> getAvailableServersInfo() throws RemoteException;
    boolean verifyLoadBalancerIdentity(String username, String password) throws RemoteException;
}
