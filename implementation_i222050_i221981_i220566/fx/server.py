import socket
import matplotlib.pyplot as plt
import pandas as pd
import numpy as np

# Server setup
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", 1234))
s.listen(1)
print("SERVER IS ALIVE ON 127.0.0.1:1234 ")

# Namespace for evaluating commands
ns = {'np': np, 'pd': pd, 'plt': plt, 'print': print}

while True:
    try:
        c, a = s.accept()
        cmd = c.recv(4096).decode("utf-8").strip()
        print("→", cmd)

        # Exit command
        if cmd in ("exit()", "quit()"):
            c.send(b"Bye")
            c.close()
            continue

        # Chart command
        if cmd == "chart":
            plt.savefig("plot.jpg", dpi=200, bbox_inches='tight')
            plt.close('all')
            with open("plot.jpg", "rb") as f:
                c.sendall(f.read())
            c.close()
            continue

        # Load CSV
        if cmd.endswith(".csv"):
            try:
                df = pd.read_csv(cmd)
                ns['df'] = df
                c.send(f"df loaded: {len(df)} rows".encode())
            except Exception as e:
                c.send(str(e).encode())
            c.close()
            continue

        # Evaluate command
        try:
            r = eval(cmd, {"__builtins__": {}}, ns)
            c.send(str(r).encode())
        except:
            try:
                exec(cmd, {"__builtins__": {}}, ns)
                c.send(b"OK")
            except Exception as e:
                c.send(f"ERROR: {e}".encode())
            c.close()
    except Exception as e:
        print("Server error:", e)

