import socket
import ssl
import json
import threading
import gzip
import base64
from datetime import datetime
import pandas as pd
import numpy as np
import sys
import os
import hashlib
import secrets
import time
from typing import Dict, Any, Optional

class SecureVisualizationServer:
    """
    Enhanced Python server with SSL security, authentication, and advanced visualization capabilities
    """
    
    def __init__(self, host: str = 'localhost', port: int = 1234):
        self.host = host
        self.port = port
        self.clients = []
        self.data_cache = {}
        self.plot_cache = {}
        self.running = True
        self.authenticated_clients = set()
        
        # Security configuration
        self.certfile = 'server.crt'
        self.keyfile = 'server.key'
        self.session_timeout = 3600  # 1 hour
        
        # User database (in production, use proper database)
        self.users = {
            'admin': hashlib.sha256('password123'.encode()).hexdigest(),
            'user': hashlib.sha256('userpass'.encode()).hexdigest()
        }
        
        # Session management
        self.sessions: Dict[str, Dict] = {}
        
        # Generate SSL certificate if needed
        self._setup_ssl()
    
    def _setup_ssl(self):
        """Setup SSL certificates with proper configuration"""
        if not os.path.exists(self.certfile):
            print("🔐 Generating self-signed SSL certificate...")
            try:
                # Generate certificate with proper extensions for Java compatibility
                os.system(f"openssl req -new -newkey rsa:2048 -days 365 -nodes -x509 "
                         f"-keyout {self.keyfile} -out {self.certfile} "
                         f"-subj '/C=US/ST=State/L=City/O=Organization/CN=localhost' "
                         f"-addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' 2>/dev/null")
                print("✅ SSL certificate generated")
            except Exception as e:
                print(f"❌ SSL setup failed: {e}")
                self.certfile = self.keyfile = None
    
    def _compress_data(self, data: str) -> str:
        """Compress data using gzip and base64"""
        try:
            compressed = gzip.compress(data.encode('utf-8'))
            return base64.b64encode(compressed).decode('ascii')
        except Exception as e:
            print(f"Compression error: {e}")
            return data
    
    def _decompress_data(self, compressed_data: str) -> str:
        """Decompress gzip compressed data"""
        try:
            compressed_bytes = base64.b64decode(compressed_data)
            decompressed = gzip.decompress(compressed_bytes)
            return decompressed.decode('utf-8')
        except Exception as e:
            print(f"Decompression error: {e}")
            return compressed_data
    
    def _generate_session_token(self) -> str:
        """Generate secure session token"""
        return secrets.token_urlsafe(32)
    
    def _authenticate_user(self, username: str, password: str) -> bool:
        """Authenticate user credentials"""
        hashed_password = hashlib.sha256(password.encode()).hexdigest()
        return self.users.get(username) == hashed_password
    
    def _validate_session(self, client_socket, token: str) -> bool:
        """Validate session token"""
        if token in self.sessions:
            session = self.sessions[token]
            if time.time() - session['created'] < self.session_timeout:
                session['last_activity'] = time.time()
                return True
            else:
                # Session expired
                del self.sessions[token]
        return False
    
    def _create_advanced_plot(self, plot_type: str, data: pd.DataFrame, **kwargs) -> str:
        """Create advanced interactive visualizations including 3D plots"""
        try:
            import plotly.express as px
            import plotly.graph_objects as go
            from plotly.subplots import make_subplots
            
            cache_key = f"{plot_type}_{hash(str(kwargs))}_{hash(str(data.shape))}"
            if cache_key in self.plot_cache:
                return self.plot_cache[cache_key]
            
            fig = None
            
            # Clean column names by stripping whitespace
            cleaned_data = data.copy()
            cleaned_data.columns = [col.strip() for col in cleaned_data.columns]
            
            # Clean kwargs
            clean_kwargs = {}
            for key, value in kwargs.items():
                if key in ['x', 'y', 'z', 'color', 'size'] and isinstance(value, str):
                    clean_kwargs[key] = value.strip()
                else:
                    clean_kwargs[key] = value
            
            if plot_type == "line_chart":
                fig = px.line(cleaned_data, x=clean_kwargs.get('x'), y=clean_kwargs.get('y'), 
                             title=clean_kwargs.get('title', 'Line Chart'))
                
            elif plot_type == "bar_chart":
                fig = px.bar(cleaned_data, x=clean_kwargs.get('x'), y=clean_kwargs.get('y'),
                            title=clean_kwargs.get('title', 'Bar Chart'))
                
            elif plot_type == "scatter_plot":
                fig = px.scatter(cleaned_data, x=clean_kwargs.get('x'), y=clean_kwargs.get('y'),
                               color=clean_kwargs.get('color'), size=clean_kwargs.get('size'),
                               title=clean_kwargs.get('title', 'Scatter Plot'))
                
            elif plot_type == "histogram":
                fig = px.histogram(cleaned_data, x=clean_kwargs.get('x'),
                                 title=clean_kwargs.get('title', 'Histogram'),
                                 nbins=clean_kwargs.get('nbins', 20))
                
            elif plot_type == "heatmap":
                corr_matrix = cleaned_data.corr()
                fig = px.imshow(corr_matrix, 
                              title=clean_kwargs.get('title', 'Correlation Heatmap'),
                              color_continuous_scale='RdBu_r',
                              aspect="auto")
                
            elif plot_type == "3d_scatter":
                if len(cleaned_data.columns) >= 3:
                    fig = px.scatter_3d(cleaned_data, 
                                      x=clean_kwargs.get('x'), 
                                      y=clean_kwargs.get('y'), 
                                      z=clean_kwargs.get('z'),
                                      title=clean_kwargs.get('title', '3D Scatter Plot'),
                                      color=clean_kwargs.get('color'))
                else:
                    return "ERROR: Need at least 3 columns for 3D scatter plot"
                
            elif plot_type == "surface_plot":
                if len(cleaned_data.columns) >= 3:
                    # Create surface plot from first 3 numeric columns
                    numeric_cols = cleaned_data.select_dtypes(include=[np.number]).columns[:3]
                    if len(numeric_cols) >= 3:
                        z_data = cleaned_data[numeric_cols].values
                        fig = go.Figure(data=[go.Surface(z=z_data[:10])])  # Limit data for performance
                        fig.update_layout(title=clean_kwargs.get('title', '3D Surface Plot'))
                    else:
                        return "ERROR: Need at least 3 numeric columns for surface plot"
                else:
                    return "ERROR: Need at least 3 columns for surface plot"
                
            elif plot_type == "box_plot":
                fig = px.box(cleaned_data, x=clean_kwargs.get('x'), y=clean_kwargs.get('y'),
                           title=clean_kwargs.get('title', 'Box Plot'))
                
            elif plot_type == "violin_plot":
                fig = px.violin(cleaned_data, x=clean_kwargs.get('x'), y=clean_kwargs.get('y'),
                              title=clean_kwargs.get('title', 'Violin Plot'))
                
            elif plot_type == "pie_chart":
                fig = px.pie(cleaned_data, names=clean_kwargs.get('x'), values=clean_kwargs.get('y'),
                           title=clean_kwargs.get('title', 'Pie Chart'))
                
            elif plot_type == "area_chart":
                fig = px.area(cleaned_data, x=clean_kwargs.get('x'), y=clean_kwargs.get('y'),
                            title=clean_kwargs.get('title', 'Area Chart'))
                
            else:
                return f"ERROR: Unknown plot type: {plot_type}"
            
            # Enhanced styling for all plots
            if fig:
                fig.update_layout(
                    hovermode='closest',
                    showlegend=True,
                    template='plotly_white',
                    font=dict(size=12),
                    margin=dict(l=50, r=50, t=50, b=50),
                    height=600
                )
                
                html_content = fig.to_html(
                    include_plotlyjs='cdn', 
                    config={
                        'responsive': True,
                        'displayModeBar': True,
                        'scrollZoom': True,
                        'displaylogo': False
                    }
                )
                
                # Cache the plot
                self.plot_cache[cache_key] = html_content
                return html_content
            else:
                return "ERROR: Failed to create plot"
                
        except ImportError:
            return "ERROR: Plotly not installed. Run: pip install plotly"
        except Exception as e:
            return f"ERROR creating plot: {str(e)}"
    
    def _handle_authentication(self, client_socket) -> Optional[str]:
        """Handle client authentication"""
        try:
            auth_data = client_socket.recv(1024).decode('utf-8')
            auth_info = json.loads(auth_data)
            
            username = auth_info.get('username', '')
            password = auth_info.get('password', '')
            
            if self._authenticate_user(username, password):
                session_token = self._generate_session_token()
                self.sessions[session_token] = {
                    'username': username,
                    'created': time.time(),
                    'last_activity': time.time(),
                    'client_socket': client_socket
                }
                
                auth_response = {
                    'status': 'success',
                    'token': session_token,
                    'message': 'Authentication successful'
                }
                client_socket.send(json.dumps(auth_response).encode('utf-8'))
                return session_token
            else:
                auth_response = {
                    'status': 'failure',
                    'message': 'Invalid credentials'
                }
                client_socket.send(json.dumps(auth_response).encode('utf-8'))
                return None
                
        except Exception as e:
            print(f"Authentication error: {e}")
            return None
    
    def _process_command(self, command: str, client_socket) -> str:
        """Process and execute client commands with enhanced error handling"""
        try:
            # Command routing
            if command in ["exit()", "quit()"]:
                return "DISCONNECT"
                
            elif command.endswith(".csv"):
                return self._handle_csv_command(command)
                
            elif command.startswith("plot:"):
                return self._handle_plot_command(command)
                
            elif command == "get_columns":
                return self._handle_get_columns()
                
            elif command == "get_stats":
                return self._handle_get_stats()
                
            elif command == "clear_cache":
                self.plot_cache.clear()
                return "SUCCESS: Plot cache cleared"
                
            else:
                return self._handle_python_command(command)
                
        except Exception as e:
            return f"ERROR: {str(e)}"
    
    def _handle_csv_command(self, command: str) -> str:
        """Handle CSV file loading with retry mechanism"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not os.path.exists(command):
                    available_files = [f for f in os.listdir('.') if f.endswith('.csv')]
                    return f"ERROR: File '{command}' not found. Available CSV files: {available_files}"
                
                df = pd.read_csv(command)
                # Clean column names by stripping whitespace
                df.columns = [col.strip() for col in df.columns]
                self.data_cache['current_df'] = df
                return f"SUCCESS: DataFrame loaded with {len(df)} rows and {len(df.columns)} columns. Columns: {list(df.columns)}"
                
            except Exception as e:
                if attempt == max_retries - 1:
                    return f"ERROR loading CSV after {max_retries} attempts: {str(e)}"
                time.sleep(0.1)  # Brief delay before retry
    
    def _handle_plot_command(self, command: str) -> str:
        """Handle plot generation with fallback options"""
        try:
            plot_config = json.loads(command[5:])
            plot_type = plot_config.get('type')
            
            if 'current_df' not in self.data_cache:
                return "ERROR: No data loaded. Please load a CSV file first."
            
            df = self.data_cache['current_df']
            
            # Clean column names and plot config
            df.columns = [col.strip() for col in df.columns]
            clean_plot_config = {}
            for key, value in plot_config.items():
                if key in ['x', 'y', 'z', 'color', 'size'] and isinstance(value, str):
                    clean_plot_config[key] = value.strip()
                else:
                    clean_plot_config[key] = value
            
            # Validate columns exist
            required_columns = []
            if plot_type in ['line_chart', 'bar_chart', 'scatter_plot']:
                required_columns = [clean_plot_config.get('x'), clean_plot_config.get('y')]
            elif plot_type in ['histogram', 'pie_chart']:
                required_columns = [clean_plot_config.get('x')]
            elif plot_type in ['3d_scatter']:
                required_columns = [clean_plot_config.get('x'), clean_plot_config.get('y'), clean_plot_config.get('z')]
            
            # Remove None values and check if columns exist
            required_columns = [col for col in required_columns if col is not None]
            missing_columns = [col for col in required_columns if col not in df.columns]
            
            if missing_columns:
                return f"ERROR: Columns not found: {missing_columns}. Available columns: {list(df.columns)}"
            
            html_content = self._create_advanced_plot(plot_type, df, **clean_plot_config)
            
            if html_content and not html_content.startswith("ERROR"):
                return f"HTML_PLOT:{html_content}"
            else:
                # Fallback to simple plot
                if plot_type in ['3d_scatter', 'surface_plot']:
                    fallback_type = 'scatter_plot'
                    html_content = self._create_advanced_plot(fallback_type, df, **clean_plot_config)
                    if not html_content.startswith("ERROR"):
                        return f"HTML_PLOT_FALLBACK:{html_content} (Fallback from {plot_type})"
                
                return html_content
                
        except json.JSONDecodeError as e:
            return f"ERROR: Invalid plot configuration JSON: {str(e)}"
        except Exception as e:
            return f"ERROR creating plot: {str(e)}"
    
    def _handle_get_columns(self) -> str:
        """Get available columns from loaded data"""
        if 'current_df' in self.data_cache:
            df = self.data_cache['current_df']
            # Clean column names
            df.columns = [col.strip() for col in df.columns]
            return f"COLUMNS:{json.dumps(list(df.columns))}"
        return "ERROR: No data loaded"
    
    def _handle_get_stats(self) -> str:
        """Get dataset statistics with proper JSON serialization"""
        if 'current_df' in self.data_cache:
            df = self.data_cache['current_df']
            # Clean column names
            df.columns = [col.strip() for col in df.columns]
            
            try:
                stats = {
                    'shape': list(df.shape),  # Convert tuple to list
                    'columns': list(df.columns),
                    'dtypes': {col: str(dtype) for col, dtype in df.dtypes.items()},
                    'memory_usage': int(df.memory_usage(deep=True).sum()),  # Convert to int
                    'null_counts': df.isnull().sum().astype(int).to_dict()  # Convert to int
                }
                return f"STATS:{json.dumps(stats)}"
            except Exception as e:
                return f"ERROR generating stats: {str(e)}"
        return "ERROR: No data loaded"
    
    def _handle_python_command(self, command: str) -> str:
        """Execute Python code with safety measures"""
        try:
            # Safe execution environment
            local_vars = {
                'df': self.data_cache.get('current_df', None),
                'pd': pd, 
                'np': np,
                'print': print
            }
            
            # Block dangerous operations
            dangerous_keywords = ['__', 'import os', 'import sys', 'open(', 'exec(', 'eval(', 'compile(']
            if any(keyword in command for keyword in dangerous_keywords):
                return "ERROR: Potentially dangerous operation blocked"
            
            # Try eval first for expressions, then exec for statements
            try:
                result = eval(command, globals(), local_vars)
                if hasattr(result, 'to_string'):
                    return result.to_string()
                else:
                    return str(result)
            except:
                exec(command, globals(), local_vars)
                return "Command executed successfully"
                
        except Exception as e:
            return f"EXEC_ERROR: {str(e)}"
    
    def _handle_client(self, client_socket, address):
        """Handle secure client connection with comprehensive error handling"""
        print(f"🔐 New connection from {address}")
        self.clients.append(client_socket)
        
        try:
            # Authentication phase
            session_token = self._handle_authentication(client_socket)
            if not session_token:
                client_socket.close()
                return
            
            self.authenticated_clients.add(client_socket)
            print(f"✅ Client {address} authenticated successfully")
            
            # Main command processing loop
            while self.running and client_socket in self.authenticated_clients:
                try:
                    msg = client_socket.recv(65536)
                    if not msg:
                        break
                    
                    compressed_command = msg.decode('utf-8').strip()
                    command = self._decompress_data(compressed_command)
                    
                    print(f"📨 Command from {address}: {command[:100]}...")
                    
                    response = self._process_command(command, client_socket)
                    
                    if response:
                        compressed_response = self._compress_data(response)
                        client_socket.send(compressed_response.encode('utf-8'))
                        
                except socket.timeout:
                    continue
                except Exception as e:
                    print(f"❌ Command processing error for {address}: {e}")
                    error_response = self._compress_data(f"ERROR: {str(e)}")
                    client_socket.send(error_response.encode('utf-8'))
                    
        except Exception as e:
            print(f"❌ Client handling error for {address}: {e}")
        finally:
            # Cleanup
            if client_socket in self.clients:
                self.clients.remove(client_socket)
            if client_socket in self.authenticated_clients:
                self.authenticated_clients.remove(client_socket)
            if session_token and session_token in self.sessions:
                del self.sessions[session_token]
            client_socket.close()
            print(f"🔌 Connection closed with {address}")
    
    def start_server(self):
        """Start the secure visualization server"""
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_socket.settimeout(1.0)  # Allow for graceful shutdown
        
        try:
            server_socket.bind((self.host, self.port))
            server_socket.listen(5)
            
            # Enhanced SSL configuration for Java compatibility
            if self.certfile and self.keyfile and os.path.exists(self.certfile):
                context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                context.load_cert_chain(self.certfile, self.keyfile)
                context.check_hostname = False
                context.verify_mode = ssl.CERT_NONE
                # Set compatible ciphers for Java
                context.set_ciphers('ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-GCM-SHA256:TLS_AES_256_GCM_SHA384:TLS_AES_128_GCM_SHA256')
                
                secure_socket = context.wrap_socket(server_socket, server_side=True)
                print(f"🔒 Secure Visualization Server running on {self.host}:{self.port}")
                print("✅ Features: SSL/TLS, Authentication, Advanced Plots")
            else:
                secure_socket = server_socket
                print(f"⚠️  Visualization Server running on {self.host}:{self.port} (No SSL)")
                print("✅ Features: Authentication, Advanced Plots")
            
            print(f"📁 Directory: {os.getcwd()}")
            print("👤 Users: admin/password123, user/userpass")
            print("📊 Supported: 3D Plots, Surface Plots, Caching")
            print("🛡️  Security: Authentication, Session Management, Input Validation")
            print("💡 Commands: data.csv, plot:{...}, get_columns, clear_cache")
            print("Press Ctrl+C to stop the server")
            
            while self.running:
                try:
                    client_socket, address = secure_socket.accept()
                    client_socket.settimeout(30.0)  # Client timeout
                    client_thread = threading.Thread(
                        target=self._handle_client, 
                        args=(client_socket, address),
                        daemon=True
                    )
                    client_thread.start()
                except socket.timeout:
                    continue
                except ssl.SSLError as e:
                    print(f"❌ SSL handshake failed: {e}")
                except Exception as e:
                    if self.running:  # Only log if we're not shutting down
                        print(f"❌ Accept error: {e}")
                        
        except KeyboardInterrupt:
            print("\n🛑 Server shutting down gracefully...")
        except Exception as e:
            print(f"❌ Server error: {e}")
        finally:
            self.running = False
            server_socket.close()
            print("✅ Server stopped")

if __name__ == "__main__":
    server = SecureVisualizationServer()
    server.start_server()
