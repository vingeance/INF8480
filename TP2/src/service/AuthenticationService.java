package service;

import shared.ApplicationProperties;
import shared.AuthenticationServiceInterface;
import shared.OperationServerSharedInfo;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationService implements AuthenticationServiceInterface {

    private Map<String, String> loadBalancerIdentity = new HashMap<>(); // key: username, value: password
    private ArrayList<OperationServerSharedInfo> availableServersInfo;

    public static void main(String[] args)
    {
        String serviceIp = ApplicationProperties.getPropertyValueFromKey("serviceIp");
        System.setProperty("java.rmi.server.hostname", serviceIp);
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
            int servicePort = Integer.parseInt(ApplicationProperties.getPropertyValueFromKey("servicePort"));
            AuthenticationServiceInterface stub = (AuthenticationServiceInterface) UnicastRemoteObject
                    .exportObject(this, servicePort);

            String serviceIp = System.getProperty("java.rmi.server.hostname");
            int rmiPort = Integer.parseInt(ApplicationProperties.getPropertyValueFromKey("rmiPort"));
            Registry registry = LocateRegistry.getRegistry(serviceIp, rmiPort);
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
    public void registerLoadBalancer(String username, String password) throws RemoteException
    {
        System.out.println("Added load balancer with username " + username);
        loadBalancerIdentity.put(username, password);
    }

    @Override
    public void unregisterLoadBalancer(String username) throws RemoteException
    {
        System.out.println("Removed load balancer with username " + username);
        loadBalancerIdentity.remove(username);
    }

    @Override
    public void registerOperationServer(OperationServerSharedInfo operationServerInfo) throws RemoteException
    {
        System.out.println("Added server with IP " + operationServerInfo.getIpAddress() + " and port " + operationServerInfo.getPort());
        availableServersInfo.add(operationServerInfo);
    }

    @Override
    public void unregisterOperationServer(OperationServerSharedInfo operationServerInfo) throws RemoteException
    {
        for(OperationServerSharedInfo serverInfo : this.availableServersInfo)
        {
            if(serverInfo.getIpAddress().equals(operationServerInfo.getIpAddress()))
            {
                if(serverInfo.getPort().equals(operationServerInfo.getPort()))
                {
                    availableServersInfo.remove(serverInfo);
                    System.out.println("Removed server with IP " + serverInfo.getIpAddress() + " and port " + serverInfo.getPort());
                    break;
                }
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
        if(!loadBalancerIdentity.containsKey(username))
        {
            System.out.println("Verification failed. User does not exist.");
            return false;
        }
        if(!loadBalancerIdentity.get(username).equals(password))
        {
            System.out.println("Verification failed. Password is incorrect.");
            return false;
        }
        //System.out.println("Verification success. Credentials are valid.");
        return true;
    }
}
