import socket, math, datetime, random, sys, os
import numpy as np
import pandas as pd
from scipy import constants
import matplotlib.pyplot as plt
import seaborn as sns

def is_float(a_string):
    try:
        float(a_string)
        return True
    except ValueError:
        return False

try:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(("localhost", 1234)) 
    print(f"Server running on {socket.gethostname()}:1234")
    s.listen(5)
    
    while True:
        clt, adr = s.accept()
        print(f"Connection from {adr}")
        msg = clt.recv(24576)
        xcode = msg.decode("utf-8")
        print(f"Received command: {xcode}")
        
        if xcode == "exit()" or xcode == "quit()":
            print("output-0: ", xcode)
            clt.send("Server stopped".encode())
            clt.close()
            break
            
        elif xcode.endswith(".csv"):
            try:
                df = pd.read_csv(xcode)
                print("output-00: ", xcode, " loaded")
                clt.send("pandas data frame object with 'df' loaded.".encode())
            except Exception as e:
                clt.send(f"Error loading CSV: {str(e)}".encode())
                
        elif xcode == "chart":
            try:
                if os.path.exists("plot.jpg"):
                    os.remove("plot.jpg")
                # Save current plot
                plt.savefig("plot.jpg")
                plt.close()
                
                # Send file in chunks
                with open('plot.jpg', 'rb') as file:
                    while True:
                        chunk = file.read(4096)
                        if not chunk:
                            break
                        clt.sendall(chunk)
                print("Chart sent to client")
            except Exception as e:
                clt.send(f"Error generating chart: {str(e)}".encode())
                
        else:
            try:
                # Execute Python code - this includes plotting commands
                locals_dict = {}
                globals_dict = {'plt': plt, 'sns': sns, 'pd': pd, 'np': np}
                
                # Add df to globals if it exists
                if 'df' in globals():
                    globals_dict['df'] = df
                
                # Execute the command
                exec(xcode, globals_dict, locals_dict)
                
                # Send output if exists
                if 'out' in locals_dict:
                    output = str(locals_dict['out'])
                    clt.send(output.encode())
                    print("Output: ", output)
                else:
                    # For plotting commands or commands without output
                    clt.send("Command executed successfully".encode())
                    print("Command executed: ", xcode)
                    
            except Exception as e:
                error_msg = f"Runtime Error: {str(e)}"
                clt.send(error_msg.encode())
                print(f"Error: {e}")
                
        clt.close()
        
except Exception as err:
    print(f"Unexpected error: {err}")
    raise
finally:
    s.close()

