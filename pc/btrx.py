# Audio PC -> movil (puerto 5000) y microfono movil -> PC (puerto 5001), por USB (adb) o WiFi.
# Uso: btrx.exe            (USB: usa adb.exe si esta junto al exe o en el PATH)
#      btrx.exe IP_MOVIL   (WiFi)
import os, shutil, socket, struct, subprocess, sys, threading, time
import pyaudiowpatch as pa

HOST = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"

def setup_adb():
    if len(sys.argv) > 1:
        return
    base = os.path.dirname(sys.executable if getattr(sys, "frozen", False) else os.path.abspath(__file__))
    exe = shutil.which("adb") or next((x for x in (os.path.join(base, "adb.exe"),
           os.path.join(base, "platform-tools", "adb.exe")) if os.path.exists(x)), None)
    if not exe:
        print("AVISO: no encuentro adb.exe (ponlo junto a btrx.exe o usa WiFi con la IP del movil)")
        return
    for port in (5000, 5001):
        subprocess.run([exe, "forward", f"tcp:{port}", f"tcp:{port}"], capture_output=True)

def connect(port):
    while True:
        try:
            s = socket.create_connection((HOST, port), timeout=3)
            s.settimeout(None)
            s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            return s
        except OSError:
            time.sleep(1)

def speaker_loop():
    p = pa.PyAudio()
    api = p.get_host_api_info_by_type(pa.paWASAPI)
    while True:
        try:
            spk = p.get_device_info_by_index(api["defaultOutputDevice"])
            if not spk.get("isLoopbackDevice"):
                for lb in p.get_loopback_device_info_generator():
                    if spk["name"] in lb["name"]:
                        spk = lb
                        break
            rate, ch = int(spk["defaultSampleRate"]), min(2, spk["maxInputChannels"])
            s = connect(5000)
            s.sendall(struct.pack(">II", rate, ch))
            dead = threading.Event()
            def cb(data, frames, t, status):
                try:
                    s.sendall(data)
                except OSError:
                    dead.set()
                return (None, pa.paContinue)
            st = p.open(format=pa.paInt16, channels=ch, rate=rate, input=True,
                        input_device_index=spk["index"], frames_per_buffer=rate // 200,
                        stream_callback=cb)
            st.start_stream()
            print(f"[audio] enviando {rate} Hz {ch} ch")
            dead.wait()
            st.stop_stream(); st.close(); s.close()
            print("[audio] desconectado, reintentando...")
        except Exception as e:
            print("[audio] error:", e)
            time.sleep(1)

def recvn(s, n):
    b = bytearray()
    while len(b) < n:
        c = s.recv(n - len(b))
        if not c:
            raise ConnectionError
        b += c
    return bytes(b)

def mic_loop():
    p = pa.PyAudio()
    dev = next((i for i in range(p.get_device_count())
                if "CABLE Input" in p.get_device_info_by_index(i)["name"]
                and p.get_device_info_by_index(i)["maxOutputChannels"] > 0), None)
    if dev is None:
        print("[mic] VB-Cable no instalado: microfono desactivado")
        return
    while True:
        try:
            s = connect(5001)
            rate, ch = struct.unpack(">II", recvn(s, 8))
            st = p.open(format=pa.paInt16, channels=ch, rate=rate, output=True,
                        output_device_index=dev, frames_per_buffer=rate // 200)
            print(f"[mic] recibiendo {rate} Hz")
            chunk = rate // 200 * ch * 2
            while True:
                st.write(recvn(s, chunk))
        except ConnectionError:
            try: st.close()
            except Exception: pass
            print("[mic] desconectado, reintentando...")
            time.sleep(1)
        except Exception as e:
            print("[mic] error:", e)
            time.sleep(1)

setup_adb()
threading.Thread(target=speaker_loop, daemon=True).start()
threading.Thread(target=mic_loop, daemon=True).start()
print("Funcionando. Ctrl+C para salir.")
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    pass
