package loadbalancer;

import shared.*;

import java.io.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static shared.ApplicationProperties.getPropertyValueFromKey;

public class LoadBalancer {


    private AuthenticationServiceInterface authenticationServiceStub;
    private ArrayList<OperationServerInterface> operationServerStubs;
    private ArrayList<OperationServerSharedInfo> operationServersInfos;
    private static final String OPERATIONS_DIRECTORY = "operations";
    public static AtomicInteger totalResult = new AtomicInteger(0);

    public static void main(String[] args)
    {
        if(args.length == 0)
        {
            System.err.println("Error: You need to specify the operations filename " +
                    "to execute this program.");
            return;
        }

        String secureValue = getPropertyValueFromKey("secure");
        if(secureValue == null)
        {
            System.err.println("Error: Could not read secure property value in application.properties.");
            return;
        }

        LoadBalancer loadBalancer = new LoadBalancer();
        if(Boolean.parseBoolean(secureValue))
        {
            // Mode securise
            loadBalancer.runSecurely(args[0]);
        }
        else
        {
            // Mode non-securise
            loadBalancer.runInsecurely(args[0]);
        }
    }

    private LoadBalancer()
    {
        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManager());
        }

        String authenticationServiceIp = getPropertyValueFromKey("serviceIp");
        authenticationServiceStub = loadAuthenticationServiceStub(authenticationServiceIp);
        try {
            operationServersInfos = authenticationServiceStub.getAvailableServersInfo();
        } catch (RemoteException e) {
            System.out.println("Error : " + e.getMessage());
        }
        operationServerStubs = loadOperationServerStubs();
        if(operationServerStubs.isEmpty())
        {
            System.out.println("Error: no server available to calculate.");
            System.exit(0);
        }
    }

    private AuthenticationServiceInterface loadAuthenticationServiceStub(String hostname)
    {
        AuthenticationServiceInterface stub = null;

        try
        {
            int rmiPort = Integer.parseInt(ApplicationProperties.getPropertyValueFromKey("rmiPort"));
            Registry registry = LocateRegistry.getRegistry(hostname, rmiPort);
            stub = (AuthenticationServiceInterface) registry.lookup("authenticationservice");
        }
        catch (NotBoundException e)
        {
            System.err.println("Error: The name '" + e.getMessage()
                    + "' is not defined in the registry.");
        }
        catch (RemoteException e)
        {
            System.err.println("Error: " + e.getMessage());
        }

        return stub;
    }

    private ArrayList<OperationServerInterface> loadOperationServerStubs() {

        ArrayList<OperationServerInterface> stubList = new ArrayList<>();
        try
        {
            int rmiPort = Integer.parseInt(ApplicationProperties.getPropertyValueFromKey("rmiPort"));
            for(OperationServerSharedInfo serverInfo : operationServersInfos)
            {
                Registry registry = LocateRegistry.getRegistry(serverInfo.getIpAddress(), rmiPort);
                OperationServerInterface stub = null;
                stub = (OperationServerInterface) registry.lookup(serverInfo.getIpAddress() + ":" + serverInfo.getPort());

                stubList.add(stub);
            }
        }
        catch (NotBoundException e)
        {
            System.err.println("Error: The name '" + e.getMessage()
                    + "' is not defined in the registry.");
        }
        catch (RemoteException e)
        {
            System.err.println("Error1: " + e.getMessage());
        }

        return stubList;
    }

    private void runInsecurely(String operationsFilename)
    {


    }

    private void runSecurely(String operationsFilename)
    {
        ArrayList<String> operationsList = readOperationsFile(operationsFilename);
        int nbAvailableServers = operationServerStubs.size();

        Thread[] threads = new Thread[nbAvailableServers];
        int[] activeServersCapacities = new int[nbAvailableServers];

        long start = System.nanoTime();
        boolean aThreadIsAlive = true;
        while(!operationsList.isEmpty() || (checkIfThreadAlive(threads)))
        {
            for (int i = 0; i < threads.length; i++)
            {
                if (threads[i] == null || !threads[i].isAlive())
                {
                    activeServersCapacities[i] = operationServersInfos.get(i).getCapacity();
                    //System.out.println("Thread " + i + " is dead. Capacity of the server : " + activeServersCapacities[i]);
                }
            }

            Integer threadNumber = getServerWithBiggestCapacity(activeServersCapacities);
            activeServersCapacities = new int[nbAvailableServers];

            if(threadNumber == null || operationsList.isEmpty())
            {
                // No thread available
                continue;
            }
            int serverCapacity = operationServersInfos.get(threadNumber).getCapacity();

            //System.out.println("Operation list size: " + operationsList.size());
            ArrayList<String> task = operationsList;
            if(2*serverCapacity <= operationsList.size())
            {
                task = new ArrayList<String>(operationsList.subList(0, 2 * serverCapacity));
                //System.out.println("Created subtask of size: " + task.size());
            }

            final ArrayList<String> threadTask = new ArrayList<>(task);
            //System.out.println("Thread task size: " + threadTask.size());

            // Remove operations from 0 to task size
            operationsList.subList(0, task.size()).clear();
            threads[threadNumber] = new Thread(() ->
            {
                try
                {
                    int result = operationServerStubs.get(threadNumber).calculateResult(threadTask);
                    //System.out.println("Task size: " + threadTask.size());
                    //System.out.println("The result is : " + result + ".");
                    totalResult.getAndAdd(result);
                }
                catch (RemoteException | TaskRejectedException e)
                {
                    operationsList.addAll(threadTask);
                    //System.err.println("Error: " + e.getMessage());
                }
            });
            threads[threadNumber].start();
        }

        // Wait for threads to terminate
        for (Thread thread : threads)
        {
            try
            {
                thread.join();
            }
            catch (InterruptedException e)
            {
                System.err.println("Error: " + e.getMessage());
            }
        }

        long end = System.nanoTime();
        System.out.println("Temps pour " + nbAvailableServers + " serveurs : " + (end - start) / 1E9 + " s");
        System.out.println("Final result : " + totalResult.get() + ".");
    }

    private boolean checkIfThreadAlive(Thread[] threads)
    {
        boolean aThreadIsAlive = false;
        for (Thread thread : threads)
        {
            if (thread != null && thread.isAlive())
            {
                aThreadIsAlive = true;
                break;
            }
        }
        return aThreadIsAlive;
    }

    private Integer getServerWithBiggestCapacity(int[] serverCapacities)
    {
        Integer biggestCapacity = 0;
        boolean found = false;
        for(int i = 0; i < serverCapacities.length; i++)
        {
            if(serverCapacities[i] > biggestCapacity)
            {
                biggestCapacity = i;
                found = true;
            }
        }
        if (found)
        {
            return biggestCapacity;
        }
        else
        {
            return null;
        }
    }

    private ArrayList<String> readOperationsFile(String operationsFilename)
    {
        ArrayList<String> allOperations = new ArrayList<>();
        File operationsFile = new File(OPERATIONS_DIRECTORY + "/" + operationsFilename);

        try
        {
            BufferedReader in = new BufferedReader(new FileReader(operationsFile));
            String readedOperation = "";
            while ((readedOperation = in.readLine()) != null)
            {
                allOperations.add(readedOperation);
            }
        }
        catch (IOException e)
        {
            System.err.println("Error: " + e.getMessage());
        }

        return allOperations;
    }

}