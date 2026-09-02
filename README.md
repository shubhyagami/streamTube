# streamTube ⚡

> **CYBERPUNK 2077 // NIGHT CITY NEURAL VIDEO STREAM PROXY**  
> A self-hosted YouTube streaming web application built with **Java Spring Boot 3** and **Thymeleaf**, designed to bypass network restrictions using server-side **HTTP 206 Byte-Range chunked proxying**.

---

## ⚡ Features

- **Cyberpunk 2077 Night City UI**:
  - High-tech HUD layout with scanning grids, holographic scanlines, and tech corner brackets.
  - Electric Kiroshi Yellow (`#fcee0a`), Night City Neon Cyan (`#00f0ff`), and Arasaka Crimson accents.
  - Orbitron, Rajdhani, and Share Tech Mono typography with glitch styling.
- **HTTP 206 Byte-Range Chunked Streaming**:
  - Streams video in small continuous slices (`Range: bytes=start-end`).
  - Clients never communicate directly with YouTube or Google CDN domains — ideal for restricted school, college, or corporate networks.
  - Instant playback buffering and scrubbing support.
- **🎛️ Braindance Audio Studio (8-Band Equalizer)**:
  - Web Audio API parametric equalizer (60Hz, 150Hz, 400Hz, 1kHz, 2.4kHz, 6kHz, 12kHz, 16kHz).
  - +/- 12 dB precision gain sliders and master preamp boost.
  - Cyberpunk sound profiles: *Pacifica Heavy Bass*, *Johnny Silverhand Rock*, *Afterlife Lofi & Chill*, *Arasaka Dialogue*, *Netrunner Synthwave*.
- **📊 Real-Time Audio Visualizer**:
  - 48-band frequency spectrum with dynamic peak-cap gravity drops.
  - Oscilloscope waveform mode & Radial pulse visualizer.
  - Night City Neon, Kiroshi Amber, and Netrunner Emerald color palettes.
- **🐧 Linux & Android Termux Ready**:
  - Targets Java 17+ bytecode for universal compatibility.
  - Auto-detects `yt-dlp` and `ffmpeg` from system `$PATH`.

---

## 🚀 Quick Start

### 🐧 On Linux (Ubuntu / Debian / Raspberry Pi / VPS)

```bash
# 1. Install Java 17+ and utilities
sudo apt update
sudo apt install -y openjdk-17-jre yt-dlp ffmpeg

# 2. Run the JAR
java -jar yt-stream.jar

# 3. Open browser
# http://localhost:8080 or http://<server-ip>:8080
```

### 📱 On Android (Termux)

```bash
# 1. Update Termux & install packages
pkg update && pkg upgrade -y
pkg install -y openjdk-17 python ffmpeg

# 2. Install yt-dlp
pip install yt-dlp

# 3. Run the JAR
java -jar yt-stream.jar

# 4. Open in mobile browser
# http://localhost:8080
```

### 🪟 On Windows

```powershell
# Run using the bundled local binaries in bin/
java -jar yt-stream.jar
```

---

## 🛠️ Building From Source

Prerequisites: **Java 17+** and **Apache Maven 3.8+**

```bash
# Clone the repository
git clone https://github.com/shubhyagami/streamTube.git
cd streamTube

# Build the executable JAR
mvn clean package -DskipTests

# Run the newly built JAR
java -jar target/yt-stream-1.0.0.jar
```

---

## ⚙️ Configuration

Custom port and settings can be overridden on launch:

```bash
# Start on port 3000
java -jar yt-stream.jar --server.port=3000

# Or via environment variable
SERVER_PORT=3000 java -jar yt-stream.jar
```

---

## 📜 License

MIT License. Developed for education and personal media streaming on restricted networks.
