package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface OperationServerInterface extends Remote {
    int calculateResult(String loadBalancerUser, String loadBalancerPassword, ArrayList<String> operationsList) throws RemoteException, TaskRejectedException, FalseIdentityException;
}
