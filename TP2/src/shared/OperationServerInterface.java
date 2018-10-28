package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface OperationServerInterface extends Remote {
    int calculateResult(ArrayList<String> operationsList) throws RemoteException;
}
