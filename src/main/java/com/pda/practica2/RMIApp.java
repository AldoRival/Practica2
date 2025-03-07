package com.pda.practica2;

import com.pda.practica2.interfaces.PeerInterface;
import com.pda.practica2.model.RMIPeer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;

public class RMIApp extends JFrame {
    private RMIPeer peer;
    private PeerInterface stub;
    private Registry registry;
    private JTextArea textAreaSearchResults;
    private JTextArea jTextAreaMessages; // Componente para mostrar mensajes
    private JTextField searchField;
    private JButton searchButton;
    private JButton uploadButton;
    private JTextArea catalogMP3;
    private JTextArea catalogMP4;
    private JPanel filePreviewPanel;
    private JLabel jLabelCoor; // Componente para mostrar el coordinador

    public RMIApp(String nodeID) throws RemoteException {
        super("Intercambio de Archivos P2P");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Campo de búsqueda y botón de buscar
        searchField = new JTextField(20);
        searchButton = new JButton("Buscar");
        searchButton.addActionListener(e -> searchFiles(searchField.getText()));

        uploadButton = new JButton("Subir Archivo");
        uploadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uploadFile();
            }
        });

        JPanel searchPanel = new JPanel();
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(uploadButton);
        add(searchPanel, BorderLayout.NORTH);

        // Área para mostrar resultados de búsqueda
        textAreaSearchResults = new JTextArea();
        JScrollPane scrollPaneSearchResults = new JScrollPane(textAreaSearchResults);
        add(scrollPaneSearchResults, BorderLayout.CENTER);

        // Área para mostrar mensajes
        jTextAreaMessages = new JTextArea();
        JScrollPane scrollPaneMessages = new JScrollPane(jTextAreaMessages);
        add(scrollPaneMessages, BorderLayout.SOUTH);

        // Áreas para visualizar catálogos
        catalogMP3 = new JTextArea();
        JScrollPane scrollPaneCatalogMP3 = new JScrollPane(catalogMP3);
        catalogMP4 = new JTextArea();
        JScrollPane scrollPaneCatalogMP4 = new JScrollPane(catalogMP4);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Catálogo MP3", scrollPaneCatalogMP3);
        tabbedPane.addTab("Catálogo MP4", scrollPaneCatalogMP4);
        add(tabbedPane, BorderLayout.SOUTH);

        // Área para visualizar/reproducir archivos
        filePreviewPanel = new JPanel();
        add(filePreviewPanel, BorderLayout.EAST);

        // Label para mostrar el coordinador
        jLabelCoor = new JLabel("Coordinador: ");
        add(jLabelCoor, BorderLayout.WEST);

        try {
            registry = LocateRegistry.createRegistry(1099);
            peer = new RMIPeer(nodeID, 1, this); // Asumiendo un ID de 1 para este ejemplo
            stub = (PeerInterface) UnicastRemoteObject.exportObject(peer, 0);
            registry.rebind(nodeID, stub);
            updatePeersList();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    public Registry getRegistry() {
        return registry;
    }

    public JTextArea getjTextAreaMessages() {
        return jTextAreaMessages;
    }

    public JLabel getjLabelCoor() {
        return jLabelCoor;
    }

    public JTextArea getjTextAreaPeers() {
        return textAreaSearchResults; // Asumiendo que los peers se muestran aquí
    }

private void uploadFile() {
    JFileChooser fileChooser = new JFileChooser();
    int selection = fileChooser.showOpenDialog(this);
    if (selection == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();
        try {
            // Registrar en el catálogo
            peer.registerCatalog(file.getName());
            
            // Copiar el archivo a la carpeta storage
            File storageDir = new File("storage");
            if (!storageDir.exists()) {
                storageDir.mkdir();
            }
            
            File destFile = new File(storageDir, file.getName());
            Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            textAreaSearchResults.append("Archivo subido: " + file.getName() + "\n");
            updateCatalogs();
        } catch (Exception ex) {
            ex.printStackTrace();
            textAreaSearchResults.append("Error al subir el archivo: " + ex.getMessage() + "\n");
        }
    }
}

    private void updateCatalogs() {
        try {
            String[] mp3Files = peer.getCatalogs("mp3");
            String[] mp4Files = peer.getCatalogs("mp4");
            catalogMP3.setText(String.join("\n", mp3Files));
            catalogMP4.setText(String.join("\n", mp4Files));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void updatePeersList() {
        try {
            StringBuilder sb = new StringBuilder();
            String[] listPeers = registry.list();
            for (String peerName : listPeers) {
                sb.append(peerName).append(" - Online\n");
            }
            textAreaSearchResults.setText(sb.toString());
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    public void searchFiles(String query) {
        try {
            String[] results = peer.searchFiles(query);
            textAreaSearchResults.setText(String.join("\n", results));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void updateCoordinator(String coordinator) {
        jLabelCoor.setText("Coordinador: " + coordinator);
    }

    public static void main(String[] args) {
        try {
            String name = JOptionPane.showInputDialog("Ingresa tu identificador");
            RMIApp app = new RMIApp(name);
            app.setVisible(true);
            app.setTitle("Peer '" + name + "'");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
