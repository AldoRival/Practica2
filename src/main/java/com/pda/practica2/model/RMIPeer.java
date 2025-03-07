package com.pda.practica2.model;

import com.pda.practica2.RMIApp;
import com.pda.practica2.interfaces.PeerInterface;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.io.*;
import java.net.*;

public class RMIPeer extends UnicastRemoteObject implements PeerInterface {
    private String name;
    private int id;
    private List<String> catalog;
    private String coordinator;
    private boolean electionInProgress;
    private boolean foundGreater;
    private List<String> peers;
    private RMIApp app;

    // Constructor público para permitir la creación de instancias desde otras clases
    public RMIPeer(String name, int id, RMIApp app) throws RemoteException {
        super();
        this.name = name;
        this.id = id;
        this.catalog = new ArrayList<>();
        this.coordinator = name; // Inicialmente, cada peer es su propio coordinador
        this.electionInProgress = false;
        this.foundGreater = false;
        this.peers = new ArrayList<>();
        this.app = app;

        // Asegurar que la carpeta 'storage' exista
        ensureStorageDirectoryExists();
    }

    private void ensureStorageDirectoryExists() {
        File storageDir = new File("storage");
        if (!storageDir.exists()) {
            storageDir.mkdir();
            System.out.println("Carpeta 'storage' creada en: " + storageDir.getAbsolutePath());
        } else {
            System.out.println("Carpeta 'storage' ya existe en: " + storageDir.getAbsolutePath());
        }
    }

    @Override
    public void message(String nodeID, String message) throws RemoteException {
        app.getjTextAreaMessages().append(nodeID + ": " + message + "\n");
    }

    @Override
    public void updatePeerList(String[] peers) throws RemoteException {
        this.peers.clear();
        this.peers.addAll(Arrays.asList(peers));
        System.out.println("Lista de peers actualizada: " + this.peers);
    }

    @Override
    public String[] searchFiles(String query) throws RemoteException {
        List<String> results = new ArrayList<>();
        for (String file : catalog) {
            if (file.contains(query)) {
                results.add(file);
            }
        }
        return results.toArray(new String[0]);
    }

@Override
public void transferFile(String fileName, String nodeID) throws RemoteException {
    System.out.println("Transferencia de archivo '" + fileName + "' a " + nodeID);

    try {
        // Obtener la dirección IP del peer destino
        String host = getPeerAddress(nodeID);
        if (host == null) {
            throw new RemoteException("No se pudo encontrar la dirección del peer: " + nodeID);
        }

        // Establecer conexión con el peer destino
        Socket socket = new Socket(host, 12345); // Suponiendo que el puerto 12345 está abierto para transferencias
        OutputStream output = socket.getOutputStream();

        // Asegurar que la carpeta 'storage' exista
        File storageDir = new File("storage");
        if (!storageDir.exists()) {
            storageDir.mkdir();
        }

        // Leer el archivo desde la carpeta 'storage'
        File fileToSend = new File(storageDir, fileName);
        if (!fileToSend.exists()) {
            throw new RemoteException("El archivo '" + fileName + "' no existe en la carpeta 'storage'.");
        }

        FileInputStream fileInputStream = new FileInputStream(fileToSend);

        // Leer y enviar el archivo
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }

        System.out.println("Transferencia completada.");
        fileInputStream.close();
        output.close();
        socket.close();

    } catch (IOException e) {
        throw new RemoteException("Error durante la transferencia de archivo: " + e.getMessage());
    }
}


    private String getPeerAddress(String nodeID) {
        // Buscar en la lista de peers conocidos para encontrar la dirección IP correspondiente
        for (String peer : peers) {
            String[] parts = peer.split(":");
            if (parts.length >= 2 && parts[0].equals(nodeID)) {
                return parts[1]; // Retorna la dirección IP asociada con el nodeID
            }
        }

        // Si no se encuentra el peer en la lista, intentar obtenerlo del registro RMI
        try {
            Registry registry = app.getRegistry();
            String[] registeredPeers = registry.list();

            for (String registeredPeer : registeredPeers) {
                if (registeredPeer.startsWith(nodeID + "_")) {
                    // Obtener la referencia remota para extraer información de conexión
                    PeerInterface peer = (PeerInterface) registry.lookup(registeredPeer);

                    // En un entorno real, se podría implementar un método en la interfaz
                    // para obtener la dirección IP directamente, pero como alternativa
                    // usamos la información de la conexión remota
                    String remoteAddress = peer.toString();
                    if (remoteAddress.contains("endpoint:[")) {
                        int startIdx = remoteAddress.indexOf("endpoint:[") + 10;
                        int endIdx = remoteAddress.indexOf("]", startIdx);
                        if (endIdx > startIdx) {
                            String endpoint = remoteAddress.substring(startIdx, endIdx);
                            if (endpoint.contains(":")) {
                                return endpoint.substring(0, endpoint.indexOf(":"));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error al buscar dirección del peer " + nodeID + ": " + e.getMessage());
            e.printStackTrace();
        }

        // Si no se encuentra, devolver la dirección local como fallback
        System.out.println("No se encontró dirección para " + nodeID + ", usando localhost como fallback");
        return "127.0.0.1";
    }

    @Override
    public void registerCatalog(String catalogItem) throws RemoteException {
        catalog.add(catalogItem);
        System.out.println("Archivo registrado: " + catalogItem);
    }

    @Override
    public String[] getCatalogs(String type) throws RemoteException {
        List<String> filteredCatalog = new ArrayList<>();
        for (String file : catalog) {
            if (file.endsWith("." + type)) {
                filteredCatalog.add(file);
            }
        }
        return filteredCatalog.toArray(new String[0]);
    }

    @Override
    public void startElection(String nameNode, int nodeId) throws RemoteException {
        electionInProgress = true;
        foundGreater = false;

        if (nameNode.equals(name)) {
            System.out.println("Comenzaste la elección...");

            Registry registry = app.getRegistry(); // Obtener el Registry desde RMIApp
            for (String nodeName : registry.list()) {
                String[] nameAndId = nodeName.split("_");

                if (!nodeName.equals(name) && Integer.parseInt(nameAndId[1]) > id) {
                    try {
                        PeerInterface stub = (PeerInterface) registry.lookup(nodeName);
                        System.out.println("Enviando mensaje de elección a: " + nodeName);
                        stub.startElection(nameNode, nodeId);
                        foundGreater = true;
                    } catch (NotBoundException e) {
                        System.err.println("Peer no encontrado: " + nodeName);
                        e.printStackTrace();
                    }
                }
            }
            if (!foundGreater) {
                iWon(name);
            }
        } else {
            System.out.println("Petición recibida de: " + nameNode);
            sendOk(name, nameNode);
        }
    }

    @Override
    public void sendOk(String where, String to) throws RemoteException {
        if (!name.equals(to)) {
            try {
                PeerInterface stub = (PeerInterface) app.getRegistry().lookup(to);
                System.out.println("Enviando OK a " + to);
                stub.sendOk(where, to);
                startElection(name, id);
            } catch (NotBoundException e) {
                System.err.println("Peer no encontrado: " + to);
                e.printStackTrace();
            } catch (RemoteException e) {
                System.err.println("Error de comunicación remota con el peer: " + to);
                e.printStackTrace();
                throw e; // Re-lanzar la excepción para que sea manejada por el llamador si es necesario
            }
        } else {
            System.out.println(where + " contestó con Ok..");
        }
    }

    @Override
    public void iWon(String node) throws RemoteException {
        coordinator = node;
        app.updateCoordinator(node);
        electionInProgress = false;
        if (node.equals(name)) {
            System.out.println("Has ganado la elección.");
            System.out.println("Notificando a los otros nodos.....");
            Registry registry = app.getRegistry(); // Obtener el Registry desde RMIApp
            for (String nodeName : registry.list()) {
                if (!nodeName.equals(name)) {
                    try {
                        PeerInterface stub = (PeerInterface) registry.lookup(nodeName);
                        stub.iWon(node);
                    } catch (NotBoundException e) {
                        System.err.println("Peer no encontrado: " + nodeName);
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("Nodo " + node + " es el nuevo coordinador\n");
        } else {
            System.out.println("Nodo " + node + " ganó la elección.");
            System.out.println("Nodo " + node + " es el nuevo coordinador\n");
        }
    }

    @Override
    public void updateCoor(String coordinator) throws RemoteException {
        this.coordinator = coordinator;
        System.out.println("Coordinador actualizado a: " + coordinator);
        app.getjLabelCoor().setText("Coordinador: " + coordinator);
    }

    @Override
    public boolean isalive() throws RemoteException {
        System.out.println("Peer " + name + " está activo.");
        return true;
    }

    @Override
    public void updatePeers(String peers) throws RemoteException {
        this.peers = Arrays.asList(peers.split(","));
        System.out.println("Lista de peers actualizada: " + this.peers);
        app.getjTextAreaPeers().setText(String.join("\n", this.peers));
    }

    @Override
    public String getName() throws RemoteException {
        return name;
    }

    @Override
    public String getCoordinator() throws RemoteException {
        return coordinator;
    }

    @Override
    public boolean getelectionInProgress() throws RemoteException {
        return electionInProgress;
    }

    @Override
    public int getId() throws RemoteException {
        return id;
    }
}
