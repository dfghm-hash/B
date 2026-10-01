# HyperCipher - High-Performance Parallel Large-File Encryption Engine

HyperCipher is a production-grade, high-performance file encryption and decryption application built for Android. It is engineered to process very large files (5 GB, 10 GB, or larger) efficiently without exhausting RAM, leveraging modern cryptographic primitives and multi-core parallelism.

---

## 1. Architectural Highlights

### Authenticated Encryption & Key Derivation
- **Algorithm:** AES-256-GCM (`AES/GCM/NoPadding`) with native Conscrypt / ARMv8 / AES-NI hardware acceleration.
- **Key Derivation:** PBKDF2-HMAC-SHA256 with 100,000 iterations and a cryptographically secure 32-byte salt (`SecureRandom`).
- **Nonce Strategy:** Unique 12-byte cryptographically secure random IV generated per chunk.
- **AAD Integrity Binding:** Each chunk binds its 64-bit chunk index as Additional Authenticated Data (`cipher.updateAAD()`), mathematically preventing chunk reordering, substitution, or permutation attacks.
- **End-to-End Verification:** A container footer contains an authenticating SHA-256 digest of the entire original plaintext stream, verified on decryption alongside per-chunk GCM auth tags.

### Streaming Chunk-Based Parallel Pipeline
```
Input File / Stream (Buffered I/O)
       ↓
Bounded Chunk Channel (Backpressure)
       ↓
Worker Pool (Auto / Balanced / Max / Custom Cores)
 ┌─────────┬─────────┬─────────┬─────────┐
 │Worker 1 │Worker 2 │Worker 3 │Worker N │
 └─────────┴─────────┴─────────┴─────────┘
       ↓
Out-of-Order Channel
       ↓
Ordered Chunk Writer (Sequential Reassembly)
       ↓
Encrypted / Decrypted Output File
```

### Memory Efficiency & Bounded Work Queue
- **Zero Full-File Loading:** The file is never loaded into RAM. Processing is strictly streaming.
- **Reusable `BufferPool`:** Pre-allocated byte array pool for chunk buffers minimizes garbage collector allocations and prevents memory thrashing.
- **Backpressure Mechanism:** Kotlin Coroutine Channels enforce a strict capacity limit (`capacity = workerCount * 2`). The reader pauses automatically if the disk writer or worker pool is congested.

---

## 2. Container Format Specification

### Header Format
| Field | Type / Length | Description |
|---|---|---|
| Magic | 4 bytes | `HYPC` (0x48, 0x59, 0x50, 0x43) |
| Version | 2 bytes (Int16) | Format Version (`1`) |
| Algorithm ID | 1 byte | `0x01` (AES-256-GCM) |
| KDF ID | 1 byte | `0x01` (PBKDF2-HMAC-SHA-256) |
| KDF Iterations | 4 bytes (Int32) | Iterations count (`100,000`) |
| Salt Length | 1 byte | `32` |
| Salt | 32 bytes | CSPRNG Random Salt |
| Chunk Size | 4 bytes (Int32) | Configured chunk size (2MB – 32MB) |
| File Size | 8 bytes (Int64) | Original size in bytes (-1 if unknown stream) |
| Total Chunks | 8 bytes (Int64) | Expected chunks count |
| Checksum | 32 bytes | SHA-256 digest of the preceding header bytes |

### Chunk Format
| Field | Length | Description |
|---|---|---|
| Chunk Index | 4 bytes (Int32) | Zero-indexed chunk sequence number |
| Ciphertext Length | 4 bytes (Int32) | Chunk length including 16-byte GCM tag |
| Chunk IV | 12 bytes | 96-bit unique random nonce |
| Ciphertext + Tag | Variable | Encrypted chunk data + 16-byte GCM AEAD tag |

### Footer Format
| Field | Length | Description |
|---|---|---|
| Footer Magic | 4 bytes | `ENDF` (0x45, 0x4E, 0x44, 0x46) |
| Total Bytes Processed | 8 bytes (Int64) | Total verified plaintext bytes |
| SHA-256 Checksum | 32 bytes | End-to-end original plaintext hash |

---

## 3. Features & User Interface

1. **Crypt Screen:**
   - One-tap switch between **Encrypt** and **Decrypt** modes.
   - Android SAF Storage File Picker + Quick Synthetic Test File Generator (25MB, 50MB, 100MB, 500MB).
   - Password strength & entropy gauge.
   - Multi-core concurrency selector (Auto, Balanced, Max Performance, Custom 1–16 workers).
   - Configurable stream chunk size (2MB, 4MB, 8MB, 16MB, 32MB).
   - Live dashboard: Real-time speed gauge (MB/s), average throughput, ETA countdown, active worker grid, and chunk matrix visualization.
   - Pause, resume, and cancellation controls.
2. **Benchmark & Calibration Screen:**
   - Detects hardware profile (CPU cores, Max heap RAM, Conscrypt / ARMv8 hardware acceleration status).
   - Runs synthetic payload benchmarks (32MB, 64MB, 128MB, 256MB).
   - Multi-core scaling chart comparing 1, 2, 4, 8, and max worker throughput.
   - Bottleneck detector (identifies whether the system is CPU-bound, crypto-bound, or storage I/O-bound).
3. **Automated Test Suite Screen:**
   - One-tap verification suite running 10 exhaustive tests:
     1. 1 KB small file roundtrip
     2. 1 MB medium file roundtrip
     3. 25 MB large stream roundtrip
     4. Wrong password rejection
     5. Corrupted chunk tamper detection (1 bit flip)
     6. Truncated file detection
     7. Tampered header detection
     8. Multi-core scaling fidelity (1-worker vs 4-worker comparison)
     9. Variable chunk size compatibility (2MB vs 8MB)
     10. Memory pressure invariance (flat heap verification)
4. **Job Audit History:**
   - Persistent Room database tracking all jobs, throughput (MB/s), duration, and cryptographic checksums.

---

## 4. Build and Run Instructions

### Prerequisites
- JDK 17 or higher
- Android SDK 36 (minSdk 24)

### Compile App
```bash
gradle :app:assembleDebug
```

### Run Automated JVM & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
