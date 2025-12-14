import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Modality;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SecureJavaFXClient extends Application {
    
    // UI Components
    private TextArea commandInput;
    private TextArea outputArea;
    private WebView plotWebView;
    private Button sendButton;
    private Button disconnectButton;
    private Button loginButton;
    private VBox mainLayout;
    private Label statusValue;
    private ProgressIndicator connectionProgress;
    
    // Connection components
    private SSLSocket clientSocket;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private volatile boolean connected = false;
    private volatile boolean authenticating = false;
    
    // Security components
    private String sessionToken;
    private String username = "admin";
    private String password = "password123";
    
    // Performance optimization
    private Map<String, String> plotCache = new HashMap<>();
    private ScheduledExecutorService scheduler;
    private int retryCount = 0;
    private final int MAX_RETRIES = 3;
    
    // UI State
    private ComboBox<String> plotTypeCombo;
    private ComboBox<String> xAxisCombo;
    private ComboBox<String> yAxisCombo;
    private ComboBox<String> zAxisCombo;
    private TextField plotTitleField;
    
    // Full screen stages
    private Stage fullScreenPlotStage;
    private Stage fullScreenOutputStage;
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("🔒 Secure Python-Java Visualization Dashboard");
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        
        initializeScheduler();
        showLoginDialog(primaryStage);
    }
    
    private void initializeScheduler() {
        scheduler = Executors.newScheduledThreadPool(2);
    }
    
    private void showLoginDialog(Stage primaryStage) {
        Dialog<Map<String, String>> loginDialog = new Dialog<>();
        loginDialog.setTitle("🔐 Secure Authentication");
        loginDialog.setHeaderText("Connect to Secure Visualization Server");
        
        ButtonType loginButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        loginDialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20, 25, 10, 25));
        
        // Server info
        Label serverLabel = new Label("Server: localhost:1234 (SSL)");
        serverLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        TextField usernameField = new TextField("admin");
        usernameField.setPromptText("Username");
        usernameField.setPrefWidth(200);
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setText("password123");
        
        CheckBox rememberCheckbox = new CheckBox("Remember credentials");
        
        grid.add(serverLabel, 0, 0, 2, 1);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(usernameField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(rememberCheckbox, 1, 3);
        
        loginDialog.getDialogPane().setContent(grid);
        Platform.runLater(usernameField::requestFocus);
        
        loginDialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                Map<String, String> result = new HashMap<>();
                result.put("username", usernameField.getText());
                result.put("password", passwordField.getText());
                return result;
            }
            return null;
        });
        
        loginDialog.showAndWait().ifPresent(credentials -> {
            this.username = credentials.get("username");
            this.password = credentials.get("password");
            initializeMainApplication(primaryStage);
        });
    }
    
    private void initializeMainApplication(Stage primaryStage) {
        setupSecurityWarning();
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setupAdvancedControls();
        
        Scene scene = new Scene(mainLayout, 1400, 900);
        primaryStage.setScene(scene);
        
        primaryStage.setOnShown(e -> connectToServerWithRetry());
        primaryStage.setOnCloseRequest(e -> shutdownApplication());
        
        primaryStage.show();
    }
    
    private void setupSecurityWarning() {
        System.out.println("🔒 SSL Security: Trusting self-signed certificate for development");
        System.out.println("⚠️  For production, use properly signed certificates");
    }
    
    // Security and Compression Methods
    private String compressData(String data) throws IOException {
        long startTime = System.currentTimeMillis();
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
            gzipStream.write(data.getBytes("UTF-8"));
        }
        String result = Base64.getEncoder().encodeToString(byteStream.toByteArray());
        long duration = System.currentTimeMillis() - startTime;
        if (duration > 100) {
            System.out.println("Compression took: " + duration + "ms");
        }
        return result;
    }
    
    private String decompressData(String compressedData) throws IOException {
        try {
            byte[] compressedBytes = Base64.getDecoder().decode(compressedData);
            ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedBytes);
            ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
            
            try (GZIPInputStream gzipStream = new GZIPInputStream(byteStream)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = gzipStream.read(buffer)) != -1) {
                    resultStream.write(buffer, 0, length);
                }
            }
            return resultStream.toString("UTF-8");
        } catch (Exception e) {
            appendOutput("❌ Decompression error: " + e.getMessage() + "\n");
            return compressedData;
        }
    }
    
    // SSL Trust Manager that accepts all certificates (Development Only)
    private static class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Trust all client certificates
        }
        
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Trust all server certificates - DEVELOPMENT ONLY!
        }
        
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
    
    // Create SSL context that trusts all certificates
    private SSLContext createTrustAllSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
        return sslContext;
    }
    
    // Connection Management with Retry Logic
    private void connectToServerWithRetry() {
        if (authenticating) {
            appendOutput("⚠️ Authentication in progress...\n");
            return;
        }
        
        authenticating = true;
        retryCount = 0;
        scheduler.execute(this::attemptConnectionWithRetry);
    }
    
    private void attemptConnectionWithRetry() {
        while (retryCount < MAX_RETRIES && !connected) {
            try {
                Platform.runLater(() -> {
                    updateConnectionStatus(false);
                    appendOutput("🔒 Attempting secure connection (" + (retryCount + 1) + "/" + MAX_RETRIES + ")...\n");
                });
                
                attemptSecureConnection();
                
                if (connected) {
                    Platform.runLater(() -> {
                        appendOutput("✅ Secure connection established\n");
                        loadInitialData();
                    });
                    break;
                }
                
            } catch (Exception e) {
                retryCount++;
                Platform.runLater(() -> 
                    appendOutput("❌ Connection attempt " + retryCount + " failed: " + e.getMessage() + "\n"));
                
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000 * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        authenticating = false;
        
        if (!connected && retryCount >= MAX_RETRIES) {
            Platform.runLater(() -> {
                appendOutput("💥 Failed to connect after " + MAX_RETRIES + " attempts\n");
                appendOutput("💡 Check if server is running and certificates are trusted\n");
            });
        }
    }
    
    private void attemptSecureConnection() {
        try {
            // Create custom SSL context that trusts all certificates
            SSLContext sslContext = createTrustAllSSLContext();
            SSLSocketFactory factory = sslContext.getSocketFactory();
            
            clientSocket = (SSLSocket) factory.createSocket("localhost", 1234);
            clientSocket.setSoTimeout(30000);
            
            // Enable modern TLS protocols
            clientSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            clientSocket.setEnabledCipherSuites(new String[]{
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_AES_256_GCM_SHA384",
                "TLS_AES_128_GCM_SHA256"
            });
            
            dataIn = new DataInputStream(clientSocket.getInputStream());
            dataOut = new DataOutputStream(clientSocket.getOutputStream());
            
            // Send authentication
            String authData = String.format(
                "{\"username\": \"%s\", \"password\": \"%s\"}",
                username, password
            );
            dataOut.write(authData.getBytes("UTF-8"));
            dataOut.flush();
            
            // Read authentication response with timeout
            byte[] authResponse = new byte[1024];
            int bytesRead = dataIn.read(authResponse);
            if (bytesRead == -1) {
                throw new IOException("Server closed connection during authentication");
            }
            
            String response = new String(authResponse, 0, bytesRead, "UTF-8").trim();
            System.out.println("🔐 Authentication response: " + response);
            
            // FIXED: Improved authentication response parsing
            if (response.contains("\"status\": \"success\"") || 
                response.contains("\"status\":\"success\"") ||
                (response.contains("success") && response.contains("token"))) {
                connected = true;
                Platform.runLater(() -> {
                    updateConnectionStatus(true);
                    appendOutput("✅ Authentication successful\n");
                });
                startServerListener();
            } else {
                throw new IOException("Authentication failed: " + response);
            }
            
        } catch (SocketTimeoutException e) {
            throw new RuntimeException("Connection timeout - server not responding");
        } catch (IOException e) {
            closeConnection();
            throw new RuntimeException("Connection failed: " + e.getMessage());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            closeConnection();
            throw new RuntimeException("SSL configuration failed: " + e.getMessage());
        } catch (Exception e) {
            closeConnection();
            throw new RuntimeException("Unexpected error: " + e.getMessage());
        }
    }
    
    private void loadInitialData() {
        sendCommand("data.csv");
        sendCommand("get_columns");
        
        scheduler.scheduleAtFixedRate(this::healthCheck, 30, 30, TimeUnit.SECONDS);
    }
    
    private void healthCheck() {
        if (connected) {
            try {
                sendCommand("'health_check'");
            } catch (Exception e) {
                Platform.runLater(() -> {
                    appendOutput("⚠️ Connection health check failed\n");
                    updateConnectionStatus(false);
                });
            }
        }
    }
    
    private void startServerListener() {
        Thread listenerThread = new Thread(() -> {
            byte[] buffer = new byte[65536];
            
            while (connected && clientSocket != null && !clientSocket.isClosed()) {
                try {
                    int bytesRead = dataIn.read(buffer);
                    if (bytesRead == -1) {
                        Platform.runLater(() -> {
                            appendOutput("🔌 Server closed connection\n");
                            updateConnectionStatus(false);
                        });
                        break;
                    }
                    
                    String compressedResponse = new String(buffer, 0, bytesRead, "UTF-8");
                    String response = decompressData(compressedResponse);
                    processServerResponse(response);
                    
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (connected) {
                        Platform.runLater(() -> {
                            appendOutput("❌ Connection error: " + e.getMessage() + "\n");
                            updateConnectionStatus(false);
                        });
                    }
                    break;
                } catch (Exception e) {
                    Platform.runLater(() -> 
                        appendOutput("❌ Error processing response: " + e.getMessage() + "\n"));
                }
            }
        });
        
        listenerThread.setDaemon(true);
        listenerThread.setName("Server-Listener");
        listenerThread.start();
    }
    
    private void processServerResponse(String response) {
        if (response.startsWith("HTML_PLOT:") || response.startsWith("HTML_PLOT_FALLBACK:")) {
            String prefix = response.startsWith("HTML_PLOT:") ? "HTML_PLOT:" : "HTML_PLOT_FALLBACK:";
            String htmlContent = response.substring(prefix.length());
            
            String cacheKey = Integer.toHexString(htmlContent.hashCode());
            plotCache.put(cacheKey, htmlContent);
            
            Platform.runLater(() -> {
                displayInteractivePlot(htmlContent);
                String message = response.startsWith("HTML_PLOT_FALLBACK:") ? 
                    "📊 Plot loaded (fallback mode)" : "📊 Interactive plot loaded";
                appendOutput(message + " [Cache: " + cacheKey + "]\n");
            });
            
        } else if (response.startsWith("COLUMNS:")) {
            String columnsJson = response.substring(8);
            Platform.runLater(() -> updateColumnSelectors(columnsJson));
            
        } else if (response.startsWith("STATS:")) {
            String statsJson = response.substring(6);
            Platform.runLater(() -> displayStatistics(statsJson));
            
        } else if (response.equals("DISCONNECT")) {
            Platform.runLater(() -> {
                appendOutput("🔌 Server requested disconnect\n");
                disconnectFromServer();
            });
            
        } else {
            Platform.runLater(() -> appendOutput("📡 " + response + "\n"));
        }
    }
    
    private void sendCommand(String command) {
        if (!connected || clientSocket == null || clientSocket.isClosed()) {
            Platform.runLater(() -> appendOutput("⚠️ Not connected to server\n"));
            return;
        }
        
        scheduler.execute(() -> {
            try {
                String compressedCommand = compressData(command);
                dataOut.write(compressedCommand.getBytes("UTF-8"));
                dataOut.flush();
            } catch (IOException e) {
                Platform.runLater(() -> {
                    appendOutput("❌ Error sending command: " + e.getMessage() + "\n");
                    updateConnectionStatus(false);
                });
            }
        });
    }
    
    private void disconnectFromServer() {
        closeConnection();
        Platform.runLater(() -> {
            updateConnectionStatus(false);
            appendOutput("🔌 Disconnected from secure server\n");
        });
    }
    
    private void closeConnection() {
        connected = false;
        authenticating = false;
        
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
    
    private void shutdownApplication() {
        // Close full screen windows
        if (fullScreenPlotStage != null) {
            fullScreenPlotStage.close();
        }
        if (fullScreenOutputStage != null) {
            fullScreenOutputStage.close();
        }
        
        disconnectFromServer();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        Platform.exit();
    }
    
    // Full Screen Methods
    private void showFullScreenPlot() {
        if (fullScreenPlotStage != null && fullScreenPlotStage.isShowing()) {
            fullScreenPlotStage.toFront();
            return;
        }
        
        fullScreenPlotStage = new Stage();
        fullScreenPlotStage.setTitle("📊 Full Screen Visualization");
        fullScreenPlotStage.initModality(Modality.NONE);
        
        WebView fullScreenWebView = new WebView();
        fullScreenWebView.getEngine().loadContent(plotWebView.getEngine().getLocation());
        
        // Create control panel
        HBox controlPanel = new HBox(10);
        controlPanel.setPadding(new Insets(10));
        controlPanel.setStyle("-fx-background-color: #2c3e50;");
        controlPanel.setAlignment(Pos.CENTER_RIGHT);
        
        Button exitFullScreenBtn = new Button("Exit Full Screen");
        exitFullScreenBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        exitFullScreenBtn.setOnAction(e -> fullScreenPlotStage.close());
        
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        refreshBtn.setOnAction(e -> fullScreenWebView.getEngine().reload());
        
        controlPanel.getChildren().addAll(refreshBtn, exitFullScreenBtn);
        
        BorderPane fullScreenLayout = new BorderPane();
        fullScreenLayout.setCenter(fullScreenWebView);
        fullScreenLayout.setTop(controlPanel);
        
        Scene fullScreenScene = new Scene(fullScreenLayout, 1200, 800);
        fullScreenPlotStage.setScene(fullScreenScene);
        fullScreenPlotStage.setMaximized(true);
        
        // Update content when main plot changes
        plotWebView.getEngine().locationProperty().addListener((obs, oldVal, newVal) -> {
            if (fullScreenPlotStage != null && fullScreenPlotStage.isShowing()) {
                fullScreenWebView.getEngine().loadContent(plotWebView.getEngine().getLocation());
            }
        });
        
        fullScreenPlotStage.setOnCloseRequest(e -> fullScreenPlotStage = null);
        fullScreenPlotStage.show();
    }
    
    private void showFullScreenOutput() {
        if (fullScreenOutputStage != null && fullScreenOutputStage.isShowing()) {
            fullScreenOutputStage.toFront();
            return;
        }
        
        fullScreenOutputStage = new Stage();
        fullScreenOutputStage.setTitle("📜 Full Screen Output Console");
        fullScreenOutputStage.initModality(Modality.NONE);
        
        TextArea fullScreenOutputArea = new TextArea();
        fullScreenOutputArea.setEditable(false);
        fullScreenOutputArea.setWrapText(true);
        fullScreenOutputArea.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 14px;");
        fullScreenOutputArea.setText(outputArea.getText());
        
        // Create control panel
        HBox controlPanel = new HBox(10);
        controlPanel.setPadding(new Insets(10));
        controlPanel.setStyle("-fx-background-color: #2c3e50;");
        controlPanel.setAlignment(Pos.CENTER_RIGHT);
        
        Button exitFullScreenBtn = new Button("Exit Full Screen");
        exitFullScreenBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        exitFullScreenBtn.setOnAction(e -> fullScreenOutputStage.close());
        
        Button clearBtn = new Button("Clear");
        clearBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold;");
        clearBtn.setOnAction(e -> {
            fullScreenOutputArea.clear();
            outputArea.clear();
        });
        
        Button copyBtn = new Button("Copy All");
        copyBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        copyBtn.setOnAction(e -> {
            fullScreenOutputArea.selectAll();
            fullScreenOutputArea.copy();
        });
        
        controlPanel.getChildren().addAll(copyBtn, clearBtn, exitFullScreenBtn);
        
        BorderPane fullScreenLayout = new BorderPane();
        fullScreenLayout.setCenter(fullScreenOutputArea);
        fullScreenLayout.setTop(controlPanel);
        
        Scene fullScreenScene = new Scene(fullScreenLayout, 1000, 700);
        fullScreenOutputStage.setScene(fullScreenScene);
        fullScreenOutputStage.setMaximized(true);
        
        // Sync output between main and full screen
        outputArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (fullScreenOutputStage != null && fullScreenOutputStage.isShowing()) {
                fullScreenOutputArea.setText(newVal);
                fullScreenOutputArea.setScrollTop(Double.MAX_VALUE);
            }
        });
        
        fullScreenOutputStage.setOnCloseRequest(e -> fullScreenOutputStage = null);
        fullScreenOutputStage.show();
    }
    
    // UI Components and Layout
    private void initializeComponents() {
        commandInput = new TextArea();
        commandInput.setPromptText("Enter commands or use visual controls below...");
        commandInput.setPrefRowCount(4);
        commandInput.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 12px;");
        
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 11px;");
        
        plotWebView = new WebView();
        plotWebView.setPrefHeight(500);
        plotWebView.getEngine().setUserStyleSheetLocation("data:,body { margin: 10px; }");
        
        sendButton = new Button("🚀 Execute");
        disconnectButton = new Button("🔌 Disconnect");
        loginButton = new Button("🔐 Reconnect");
        
        connectionProgress = new ProgressIndicator();
        connectionProgress.setVisible(false);
        connectionProgress.setPrefSize(20, 20);
        
        plotTypeCombo = new ComboBox<>();
        xAxisCombo = new ComboBox<>();
        yAxisCombo = new ComboBox<>();
        zAxisCombo = new ComboBox<>();
        plotTitleField = new TextField();
        
        setupComponentStyles();
    }
    
    private void setupComponentStyles() {
        String primaryStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;";
        String successStyle = "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;";
        String dangerStyle = "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;";
        
        sendButton.setStyle(primaryStyle);
        disconnectButton.setStyle(dangerStyle);
        loginButton.setStyle(successStyle);
        
        sendButton.setDisable(true);
        disconnectButton.setDisable(true);
    }
    
    private void setupLayout() {
        mainLayout = new VBox(15);
        mainLayout.setPadding(new Insets(20));
        mainLayout.setStyle("-fx-background-color: #f8f9fa;");
        
        // Header
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("🔒 Secure Data Visualization Dashboard");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        Label statusLabel = new Label("Status:");
        statusLabel.setStyle("-fx-font-weight: bold;");
        statusValue = new Label("Disconnected");
        statusValue.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        
        statusBox.getChildren().addAll(statusLabel, statusValue, connectionProgress);
        HBox.setHgrow(statusBox, Priority.ALWAYS);
        
        headerBox.getChildren().addAll(titleLabel, statusBox);
        
        // Connection Panel
        HBox connectionPanel = new HBox(10);
        connectionPanel.setAlignment(Pos.CENTER_LEFT);
        connectionPanel.setStyle("-fx-background-color: #ecf0f1; -fx-padding: 10; -fx-border-radius: 5;");
        connectionPanel.getChildren().addAll(loginButton, disconnectButton);
        
        // Command Section
        VBox commandBox = new VBox(8);
        commandBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-color: #bdc3c7; -fx-border-radius: 8;");
        
        Label commandLabel = new Label("💬 Command Console");
        commandLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        HBox commandButtons = new HBox(10);
        commandButtons.setAlignment(Pos.CENTER_LEFT);
        commandButtons.getChildren().addAll(sendButton, new Label("Quick:"), createQuickCommandButtons());
        
        commandBox.getChildren().addAll(commandLabel, commandInput, commandButtons);
        
        // Output Section with Full Screen Button
        VBox outputBox = new VBox(8);
        outputBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-color: #bdc3c7; -fx-border-radius: 8;");
        
        HBox outputHeader = new HBox(10);
        outputHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label outputLabel = new Label("📡 Server Communication");
        outputLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        HBox outputButtons = new HBox(5);
        outputButtons.setAlignment(Pos.CENTER_RIGHT);
        
        Button fullScreenOutputBtn = new Button("⛶ Full Screen");
        fullScreenOutputBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold;");
        fullScreenOutputBtn.setOnAction(e -> showFullScreenOutput());
        
        Button clearOutputBtn = new Button("🗑️ Clear");
        clearOutputBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
        clearOutputBtn.setOnAction(e -> outputArea.clear());
        
        outputButtons.getChildren().addAll(fullScreenOutputBtn, clearOutputBtn);
        
        HBox.setHgrow(outputLabel, Priority.ALWAYS);
        outputHeader.getChildren().addAll(outputLabel, outputButtons);
        
        outputBox.getChildren().addAll(outputHeader, outputArea);
        VBox.setVgrow(outputArea, Priority.ALWAYS);
        outputBox.setPrefHeight(200);
        
        // Visualization Section with Full Screen Button
        VBox visualizationBox = new VBox(8);
        visualizationBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-color: #bdc3c7; -fx-border-radius: 8;");
        
        HBox visualizationHeader = new HBox(10);
        visualizationHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label visualizationLabel = new Label("📊 Interactive Visualization");
        visualizationLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        Button fullScreenPlotBtn = new Button("⛶ Full Screen");
        fullScreenPlotBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold;");
        fullScreenPlotBtn.setOnAction(e -> showFullScreenPlot());
        
        HBox.setHgrow(visualizationLabel, Priority.ALWAYS);
        visualizationHeader.getChildren().addAll(visualizationLabel, fullScreenPlotBtn);
        
        visualizationBox.getChildren().addAll(visualizationHeader, plotWebView);
        VBox.setVgrow(plotWebView, Priority.ALWAYS);
        
        mainLayout.getChildren().addAll(
            headerBox,
            connectionPanel,
            commandBox,
            outputBox,
            visualizationBox
        );
        
        VBox.setVgrow(outputBox, Priority.SOMETIMES);
        VBox.setVgrow(visualizationBox, Priority.ALWAYS);
    }
    
    private HBox createQuickCommandButtons() {
        HBox quickButtons = new HBox(5);
        
        String[] commands = {"data.csv", "df.head()", "get_columns", "get_stats", "clear_cache"};
        String[] labels = {"📁 Data", "👀 Preview", "📋 Columns", "📊 Stats", "🗑️ Cache"};
        
        for (int i = 0; i < commands.length; i++) {
            Button btn = new Button(labels[i]);
            btn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-size: 11px;");
            String command = commands[i];
            btn.setOnAction(e -> {
                commandInput.setText(command);
                appendOutput("💡 Quick command set: " + command + "\n");
            });
            quickButtons.getChildren().add(btn);
        }
        
        return quickButtons;
    }
    
    private void setupAdvancedControls() {
        plotTypeCombo.getItems().addAll(
            "Line Chart", "Bar Chart", "Scatter Plot", "Histogram", 
            "Heatmap", "3D Scatter", "Surface Plot", "Box Plot", 
            "Violin Plot", "Pie Chart", "Area Chart"
        );
        plotTypeCombo.setValue("Line Chart");
        
        plotTitleField.setPromptText("Plot Title");
        plotTitleField.setText("Data Visualization");
        
        VBox advancedPanel = new VBox(10);
        advancedPanel.setPadding(new Insets(15));
        advancedPanel.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white;");
        
        Label advancedLabel = new Label("🎨 Advanced Visualization Controls");
        advancedLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        
        GridPane controlGrid = new GridPane();
        controlGrid.setHgap(10);
        controlGrid.setVgap(10);
        controlGrid.setAlignment(Pos.CENTER_LEFT);
        
        controlGrid.add(new Label("Type:"), 0, 0);
        controlGrid.add(plotTypeCombo, 1, 0);
        controlGrid.add(new Label("Title:"), 2, 0);
        controlGrid.add(plotTitleField, 3, 0);
        
        controlGrid.add(new Label("X-Axis:"), 0, 1);
        controlGrid.add(xAxisCombo, 1, 1);
        controlGrid.add(new Label("Y-Axis:"), 2, 1);
        controlGrid.add(yAxisCombo, 2, 1);
        controlGrid.add(new Label("Z-Axis:"), 3, 1);
        controlGrid.add(zAxisCombo, 3, 1);
        
        Button generatePlotBtn = new Button("🎯 Generate Plot");
        generatePlotBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        generatePlotBtn.setOnAction(e -> generatePlotFromControls());
        
        HBox buttonBox = new HBox(10, generatePlotBtn);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        advancedPanel.getChildren().addAll(advancedLabel, controlGrid, buttonBox);
        
        mainLayout.getChildren().add(3, advancedPanel);
    }
    
    private void setupEventHandlers() {
        sendButton.setOnAction(e -> executeCurrentCommand());
        
        disconnectButton.setOnAction(e -> disconnectFromServer());
        
        loginButton.setOnAction(e -> connectToServerWithRetry());
        
        commandInput.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER:
                    if (event.isControlDown()) {
                        executeCurrentCommand();
                        event.consume();
                    }
                    break;
                case F5:
                    connectToServerWithRetry();
                    event.consume();
                    break;
                case F11:
                    showFullScreenPlot();
                    event.consume();
                    break;
                case F12:
                    showFullScreenOutput();
                    event.consume();
                    break;
            }
        });
    }
    
    private void executeCurrentCommand() {
        String command = commandInput.getText().trim();
        if (!command.isEmpty()) {
            appendOutput(">>> " + command + "\n");
            sendCommand(command);
            commandInput.clear();
        }
    }
    
    // UI Update Methods
    private void updateConnectionStatus(boolean isConnected) {
        connected = isConnected;
        
        Platform.runLater(() -> {
            sendButton.setDisable(!isConnected);
            disconnectButton.setDisable(!isConnected);
            connectionProgress.setVisible(false);
            
            if (isConnected) {
                statusValue.setText("🔒 Connected (SSL)");
                statusValue.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            } else {
                statusValue.setText("🔓 Disconnected");
                statusValue.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }
        });
    }
    
    private void appendOutput(String text) {
        Platform.runLater(() -> {
            outputArea.appendText(text);
            outputArea.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    private void displayInteractivePlot(String htmlContent) {
        Platform.runLater(() -> {
            plotWebView.getEngine().loadContent(htmlContent);
        });
    }
    
    private void updateColumnSelectors(String columnsJson) {
        try {
            String cleanJson = columnsJson.replaceAll("[\\[\\]\"]", "");
            String[] columns = cleanJson.split(",");
            
            Platform.runLater(() -> {
                xAxisCombo.getItems().setAll(columns);
                yAxisCombo.getItems().setAll(columns);
                zAxisCombo.getItems().setAll(columns);
                
                if (columns.length > 0) {
                    xAxisCombo.setValue(columns[0]);
                    if (columns.length > 1) yAxisCombo.setValue(columns[1]);
                    if (columns.length > 2) zAxisCombo.setValue(columns[2]);
                }
                
                appendOutput("📋 Columns loaded: " + columns.length + " available\n");
            });
        } catch (Exception e) {
            appendOutput("❌ Error parsing columns: " + e.getMessage() + "\n");
        }
    }
    
    private void displayStatistics(String statsJson) {
        appendOutput("📊 Dataset statistics received\n");
    }
    
    private void generatePlotFromControls() {
        String plotType = plotTypeCombo.getValue().toLowerCase().replace(" ", "_");
        String xCol = xAxisCombo.getValue();
        String yCol = yAxisCombo.getValue();
        String zCol = zAxisCombo.getValue();
        String title = plotTitleField.getText().trim();
        
        if (xCol == null || xCol.isEmpty()) {
            appendOutput("❌ Please select X axis\n");
            return;
        }
        
        if ((plotType.equals("line_chart") || plotType.equals("bar_chart") || 
             plotType.equals("scatter_plot") || plotType.equals("area_chart")) && 
            (yCol == null || yCol.isEmpty())) {
            appendOutput("❌ Please select Y axis\n");
            return;
        }
        
        StringBuilder plotConfig = new StringBuilder();
        plotConfig.append("{\"type\": \"").append(plotType).append("\"");
        plotConfig.append(", \"x\": \"").append(xCol).append("\"");
        
        if (yCol != null && !yCol.isEmpty()) {
            plotConfig.append(", \"y\": \"").append(yCol).append("\"");
        }
        
        if (zCol != null && !zCol.isEmpty() && plotType.contains("3d")) {
            plotConfig.append(", \"z\": \"").append(zCol).append("\"");
        }
        
        if (!title.isEmpty()) {
            plotConfig.append(", \"title\": \"").append(title).append("\"");
        }
        
        plotConfig.append("}");
        
        String plotCommand = "plot:" + plotConfig.toString();
        appendOutput("🎨 Generating " + plotType + " plot...\n");
        sendCommand(plotCommand);
    }
    
    public static void main(String[] args) {
        // Reduced SSL debugging for cleaner output
        System.setProperty("javax.net.debug", "");
        
        // Launch the application
        launch(args);
    }
}
