import serial
import time
import random
import argparse
import sys
import threading
import tkinter as tk
from tkinter import ttk, messagebox

# Constantes del protocolo
STX = b'\x02'
ETX = b'\x03'

class ScaleSimulatorGUI:
    def __init__(self, root, port, baudrate):
        self.root = root
        self.root.title("Smart Economato - Scale Simulator")
        self.root.geometry("400x500")
        self.root.configure(bg="#1e1e1e")
        
        self.port = port
        self.baudrate = baudrate
        self.current_weight = 0.0
        self.target_weight = 0.0
        self.running = True
        self.noise_level = 0.05
        self.convergence_factor = 0.15
        self.ser = None
        
        self.setup_ui()
        self.connect_serial()
        
        # Iniciar hilo de simulación
        self.sim_thread = threading.Thread(target=self.simulate_loop, daemon=True)
        self.sim_thread.start()
        
        # Iniciar actualización de UI
        self.update_ui_loop()

    def setup_ui(self):
        style = ttk.Style()
        style.theme_use('clam')
        
        # Estilos oscuros
        style.configure("TLabel", foreground="#ffffff", background="#1e1e1e", font=("Segoe UI", 10))
        style.configure("Header.TLabel", font=("Segoe UI", 16, "bold"))
        style.configure("Weight.TLabel", font=("Consolas", 48, "bold"), foreground="#00ff00")
        style.configure("TButton", font=("Segoe UI", 10))
        
        # Header
        header = ttk.Label(self.root, text="SIMULADOR DE BÁSCULA", style="Header.TLabel")
        header.pack(pady=20)
        
        # Port info
        port_info = ttk.Label(self.root, text=f"Puerto: {self.port} | {self.baudrate} bps")
        port_info.pack()
        
        # Peso Actual Display
        self.weight_label = ttk.Label(self.root, text="0.00", style="Weight.TLabel")
        self.weight_label.pack(pady=10)
        
        unit_label = ttk.Label(self.root, text="KILOGRAMOS (kg)")
        unit_label.pack()
        
        # Separador
        ttk.Separator(self.root, orient='horizontal').pack(fill='x', padx=20, pady=20)
        
        # Control de Peso Objetivo
        ttk.Label(self.root, text="Peso Objetivo:").pack()
        
        self.target_var = tk.DoubleVar(value=0.0)
        self.slider = ttk.Scale(self.root, from_=0.0, to=500.0, orient='horizontal', 
                               variable=self.target_var, command=self.on_slider_change)
        self.slider.pack(fill='x', padx=40, pady=10)
        
        self.target_entry_var = tk.StringVar(value="0.0")
        entry_frame = ttk.Frame(self.root, style="TFrame")
        entry_frame.pack(pady=10)
        
        self.target_entry = ttk.Entry(entry_frame, textvariable=self.target_entry_var, width=10, font=("Segoe UI", 12))
        self.target_entry.pack(side='left', padx=5)
        
        set_btn = ttk.Button(entry_frame, text="Fijar Peso", command=self.on_set_button)
        set_btn.pack(side='left', padx=5)
        
        # Status Bar
        self.status_var = tk.StringVar(value="Esperando...")
        status_bar = tk.Label(self.root, textvariable=self.status_var, bd=1, relief=tk.SUNKEN, anchor=tk.W,
                             bg="#333333", fg="#aaaaaa")
        status_bar.pack(side=tk.BOTTOM, fill=tk.X)

    def connect_serial(self):
        try:
            self.ser = serial.Serial(self.port, self.baudrate, timeout=1)
            self.status_var.set(f"Conectado a {self.port}")
        except Exception as e:
            messagebox.showerror("Error de Conexión", f"No se pudo abrir el puerto {self.port}:\n{e}")
            self.root.destroy()
            sys.exit(1)

    def on_slider_change(self, val):
        self.target_weight = float(val)
        self.target_entry_var.set(f"{self.target_weight:.2f}")

    def on_set_button(self):
        try:
            val = float(self.target_entry_var.get())
            self.target_weight = val
            self.target_var.set(val)
        except ValueError:
            messagebox.showwarning("Valor inválido", "Por favor, introduce un número válido.")

    def simulate_loop(self):
        while self.running:
            # Lógica de física (tambaleo)
            diff = self.target_weight - self.current_weight
            
            if abs(diff) < 0.01:
                self.current_weight = self.target_weight
                noise = random.uniform(-0.002, 0.002)
            else:
                self.current_weight += diff * self.convergence_factor
                noise = random.uniform(-self.noise_level, self.noise_level)
            
            display_weight = max(0, self.current_weight + noise)
            
            # Enviar por serial
            payload = f"{display_weight:.2f}"
            frame = STX + payload.encode('ascii') + ETX
            
            try:
                if self.ser:
                    self.ser.write(frame)
                    self.ser.flush()
            except:
                self.status_var.set("Error de escritura serial")
                break
                
            time.sleep(0.1)

    def update_ui_loop(self):
        if not self.running: return
        
        # Actualizar el label del peso con el valor actual (incluyendo ruido visual)
        noise = random.uniform(-0.005, 0.005)
        display_val = max(0, self.current_weight + noise)
        self.weight_label.config(text=f"{display_val:.2f}")
        
        # Cambiar color si está estable
        if abs(self.current_weight - self.target_weight) < 0.01:
            self.weight_label.config(foreground="#00ff00") # Verde estable
        else:
            self.weight_label.config(foreground="#ffaa00") # Naranja moviéndose
            
        self.root.after(100, self.update_ui_loop)

    def on_close(self):
        self.running = False
        if self.ser:
            self.ser.close()
        self.root.destroy()

def main():
    parser = argparse.ArgumentParser(description="Simulador de Báscula con GUI")
    parser.add_argument("--port", default="COM1", help="Puerto COM (ej: COM1)")
    parser.add_argument("--baud", type=int, default=9600, help="Baudrate")
    args = parser.parse_args()

    root = tk.Tk()
    app = ScaleSimulatorGUI(root, args.port, args.baud)
    root.protocol("WM_DELETE_WINDOW", app.on_close)
    root.mainloop()

if __name__ == "__main__":
    main()
