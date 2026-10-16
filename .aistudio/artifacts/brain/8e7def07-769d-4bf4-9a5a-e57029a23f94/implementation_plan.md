# AirShare: Offline File Transfer

AirShare is a high-speed, zero-data offline file transfer application engineered for environments with slow, expensive, or nonexistent internet. It leverages local Wi-Fi networks and device hotspots with QR code instant pairing and a built-in cross-platform Web Share portal to transfer files of any size at maximum local hardware speeds.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were aligned during the initial consultation:

- **Connection & Discovery**: Local Wi-Fi hotspot and QR code pairing. Devices connect directly via local network sockets without routing through any external cloud server or requiring cellular data.
- **Primary Layout & Workflow**: Dual Send and Receive hub with real-time transfer telemetry (MB/s throughput, progress percentages, estimated time remaining, and batch management).
- **Cross-Platform Access**: Built-in offline Web Share portal allowing any phone, laptop, or tablet on the same local network to download and upload files via a standard web browser without needing the app installed.

---

### 1. Overview & Core Concept

- **What It Does**: Enables lightning-fast peer-to-peer file transfers between Android devices (app-to-app) and cross-platform clients (app-to-browser) entirely offline. Users can send multiple photos, videos, documents, audio clips, and large archives in parallel or sequential batches.
- **Target Audience / Persona**: Travelers, students, field technicians, commuters, and residents in low-connectivity areas who need to share media, backups, or documents without wasting mobile data or waiting for congested cellular networks.
- **Key Value**: 100% offline, privacy-first (data never leaves the local subnet), zero mobile data consumption, and 20–50+ MB/s local Wi-Fi transfer speeds.

---

### 2. User Experience & Visual Design

#### Key User Flows

1. **Send Flow**:
   - Tap **Send Files** from the main hub.
   - Pick files via modern Android Photo Picker or System Document Picker with preview thumbnails, total batch size, and item counts.
   - Choose pairing method: Scan Receiver's QR code or select detected local peer.
   - Live transfer screen animates with real-time transfer ring, current throughput (e.g. `24.5 MB/s`), elapsed/remaining time, and per-file progress.
   - Success state displays celebration badge and options to send more or view history.

2. **Receive Flow**:
   - Tap **Receive Files** from the main hub.
   - App automatically spins up the secure local HTTP/TCP receiver server, checks local IP/hotspot state, and renders an animated QR code containing the connection payload (`http://<ip>:<port>/token`).
   - Receiver sees incoming connection prompts ("Incoming 3 files (142 MB) from Pixel 8") with Accept / Decline controls.
   - Real-time download progress bar with background notification support. Files save directly to device Downloads / App Storage with one-tap Open and Share actions.

3. **Web Share Flow**:
   - Toggle **Web Share Mode** on any device.
   - App presents a QR code and clean local URL (e.g. `http://192.168.43.1:8080`).
   - Any guest device (iPhone, Mac, Windows, Linux) connected to the phone's Wi-Fi or hotspot scans the QR code or opens the URL in Safari/Chrome.
   - Guest sees a responsive, lightweight web dashboard with one-tap file downloads and an upload dropzone to beam files back to the Android phone.

4. **Transfer History & Storage Manager**:
   - Filter past transfers by Sent, Received, and File Type (Images, Videos, Documents, Archives).
   - View speed stats, timestamps, file sizes, and quick actions to open, share, or clear logs.

#### Visual Identity & Theme

- **Aesthetic Direction**: High-tech Utilitarian & Modern Aeronautical. Clean, crisp cyber-minimalism inspired by aviation telemetry and radio communication instruments.
- **Color Palette**:
  - Dominant Background: Deep Midnight Obsidian (`#0F141C`) / Light Air White (`#F8FAFC`).
  - Primary Accent: Aero Cyan / Electric Teal (`#00E5FF` / `#00838F`), symbolizing high-speed signal flow.
  - Secondary Accent: Pulse Emerald (`#00E676`) for completed transfers and active hotspot links.
  - Surface Elevation: Frosted slate glass cards (`#1B2230`) with crisp subtle borders (`#2D3748`).
- **Typography & Hierarchy**:
  - Display & Speed Telemetry: Bold monospace-infused digital typography for transfer speeds (`MB/s`), byte counters, and IP addresses.
  - Body & Labels: Inter / Roboto Medium for scannable UI controls and file metadata.
- **Interactive Feedback & Motion**:
  - Pulsing radar wave animation on the Receive radar screen.
  - Smooth spring transitions between Send/Receive modes.
  - Haptic tick upon file selection and transfer completion.

---

### 3. Key Product Decisions & Trade-Offs

- **Pure Kotlin Embedded HTTP/TCP Server**:
  - *Chosen Approach*: Use a lightweight, robust pure-Kotlin non-blocking `ServerSocket` HTTP engine supporting chunked streaming and multipart uploads.
  - *Why*: Eliminates brittle external C/JNI libraries, guarantees compatibility across all Android versions (Android 7.0+ through Android 16), works identically on local Wi-Fi or portable Hotspots, and seamlessly powers both App-to-App and Browser Web Share.
  - *Alternatives Considered*: Wi-Fi Direct (P2P Service Discovery) was considered, but requires OEM-specific proprietary handshakes, frequent disconnections, and prevents non-Android devices from participating. Local Hotspot + HTTP pairing is universal, reliable, and 100% platform-agnostic.
- **QR Code Vector Matrix Generator**:
  - *Chosen Approach*: Lightweight in-app QR matrix encoder rendered via Jetpack Compose `Canvas`.
  - *Why*: Instant rendering with zero external dependency bloat, customizable accent colors, and offline reliability.
- **Room Database for Transfer History**:
  - *Chosen Approach*: Room with SQLite for persisting transfer logs, file paths, transfer speeds, and timestamps.
  - *Why*: Provides reactive `Flow` updates, fast search/filtering, and zero cloud reliance.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                       Jetpack Compose UI                        │
│   ┌───────────────────┬───────────────────┬─────────────────┐   │
│   │     Send Hub      │    Receive Hub    │   Web Share     │   │
│   │ (Picker & Queue)  │ (Radar & QR Code) │ (Browser Portal)│   │
│   └─────────┬─────────┴─────────┬─────────┴────────┬────────┘   │
└─────────────┼───────────────────┼──────────────────┼────────────┘
              ▼                   ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│               TransferViewModel & UI State Machine              │
│       - ConnectionState (Scanning, Connected, Hosting, Idle)    │
│       - ActiveTransferState (BytesTransferred, Speed, ETA)       │
│       - SelectedFilesBatch & Queue Manager                      │
└─────────────┬──────────────────────────────────────┬────────────┘
              ▼                                      ▼
┌───────────────────────────────┐  ┌──────────────────────────────┐
│     Network Transfer Engine   │  │    Local Room Persistence    │
│ - LocalHttpServer             │  │ - TransferRecordEntity       │
│   • /download?id=... (stream) │  │ - TransferDao                │
│   • /upload (multipart sink)  │  │ - TransferRepository         │
│   • /web (embedded web UI)    │  │   • Live Flow updates        │
│ - ChunkedSocketClient         │  │   • Query by type / status   │
│ - NetworkStateResolver (IP/AP)│  └──────────────────────────────┘
└───────────────────────────────┘
```

#### Core Data Entities

- **`TransferRecord`**:
  - `id: Long` (Auto-generated primary key)
  - `fileName: String`
  - `fileUri: String`
  - `fileSize: Long` (bytes)
  - `mimeType: String`
  - `direction: TransferDirection` (`SENT` / `RECEIVED`)
  - `transferSpeedBps: Long`
  - `timestamp: Long`
  - `peerIp: String`
  - `status: TransferStatus` (`COMPLETED`, `FAILED`, `CANCELLED`)

#### State & Event Mapping

- `TransferUiState`:
  - `activeTab`: `SEND`, `RECEIVE`, `HISTORY`, `WEB_SHARE`
  - `networkStatus`: `LocalNetworkInfo` (isHotspotActive, isWifiConnected, localIpAddress, port)
  - `stagedFiles`: `List<StagedFile>` (uri, name, size, mimeType)
  - `currentTransfer`: `TransferProgress?` (fileName, bytesSent, totalBytes, speedMbPerSec, etaSeconds)
  - `historyList`: `List<TransferRecord>`
