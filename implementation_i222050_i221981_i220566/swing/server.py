import socket, sys, io, struct
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

# Start server
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.bind(("127.0.0.1", 1234))
s.listen(5)
print("Server running on 127.0.0.1:1234")

while True:
    clt, adr = s.accept()
    print("Client connected:", adr)
    try:
        msg = clt.recv(24576)
        xcode = msg.decode("utf-8").strip()
        print("Received:", xcode)

        if xcode in ["exit()", "quit()"]:
            clt.send(b"Server stopping...\nEND_OF_TEXT\n")
            clt.close()
            sys.exit("Server stopped by command")

        # Python execution environment
        local_env = {"plt": plt, "np": np, "pd": pd}

        try:
            out = eval(xcode, {}, local_env)
        except:
            exec(xcode, {}, local_env)
            out = None

        # Check if a figure exists (any type of plot)
        fig = plt.gcf()
        has_plot = any(len(ax.get_children()) > 2 for ax in fig.axes)

        if has_plot:
            buf = io.BytesIO()
            fig.savefig(buf, format='png')
            buf.seek(0)
            data = buf.read()
            clt.send(struct.pack('>I', len(data)))  # send length
            clt.sendall(data)                        # send image bytes
            plt.close(fig)
        else:
            if out is None:
                clt.send(b"Command executed, no output\nEND_OF_TEXT\n")
            else:
                clt.send(str(out).encode() + b"\nEND_OF_TEXT\n")

    except Exception as e:
        clt.send(f"Runtime Error: {e}\nEND_OF_TEXT\n".encode())
        clt.close()

