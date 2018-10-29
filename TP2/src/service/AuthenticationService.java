package service;

import shared.AuthenticationServiceInterface;
import shared.OperationServerSharedInfo;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

public class AuthenticationService implements AuthenticationServiceInterface {

    private ArrayList<OperationServerSharedInfo> availableServersInfo;

    public static void main(String[] args)
    {
        AuthenticationService service = new AuthenticationService();
        service.run();
    }

    public AuthenticationService()
    {
        availableServersInfo = new ArrayList<>();
    }

    private void run()
    {
        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManager());
        }

        try
        {
            AuthenticationServiceInterface stub = (AuthenticationServiceInterface) UnicastRemoteObject
                    .exportObject(this, 0);

            Registry registry = LocateRegistry.getRegistry();
            registry.rebind("authenticationservice", stub);
            System.out.println("AuthenticationService ready.");
        }
        catch (ConnectException e)
        {
            System.err
                    .println("Cannot connect to RMI register. Please try 'rmiregistry &' command in bin directory.");
            System.err.println();
            System.err.println("Error: " + e.getMessage());
        }
        catch (Exception e)
        {
            System.err.println("Error: " + e.getMessage());
        }
    }

    @Override
    public void registerOperationServer(OperationServerSharedInfo operationServerInfo) throws RemoteException
    {
        availableServersInfo.add(operationServerInfo);
    }

    @Override
    public void unregisterOperationServer(OperationServerSharedInfo operationServerInfo) throws RemoteException
    {
        for(OperationServerSharedInfo serverInfo : this.availableServersInfo)
        {
            if(serverInfo.getIpAddress().equals(operationServerInfo.getIpAddress()))
            {
                availableServersInfo.remove(serverInfo);
                System.out.println("Removed server with IP " + serverInfo.getIpAddress() + " from the list.");
                break;
            }
        }
    }

    @Override
    public ArrayList<OperationServerSharedInfo> getAvailableServersInfo() throws RemoteException
    {
        return this.availableServersInfo;
    }

    @Override
    public boolean verifyLoadBalancerIdentity(String username, String password) throws RemoteException
    {
        return false;
    }
}
