# Health Blockchain System 🏥🔒

A secure, blockchain-inspired healthcare data management system. This project provides a robust, encrypted environment for storing and sharing patient medical records, complete with Role-Based Access Control (RBAC) for Doctors and Patients, and emergency override protocols.

## ✨ Features

- **Role-Based Dashboards:** Separate secure portals for Patients and Doctors.
- **Secure Authentication:** Passwords are encrypted using BCrypt.
- **End-to-End Encryption:** All medical files are encrypted using AES/CBC before storage.
- **Access Control Protocol:** Doctors must request access to patient records, which patients can approve or deny.
- **Emergency Access:** Special emergency override mechanism for critical situations, protected by audit logging.
- **Audit Trails:** Comprehensive logging of all file access, requests, and downloads.

## 🛠️ Technology Stack

- **Backend:** Java 25 & Spring Boot 4.1.0
- **Frontend:** HTML5, CSS3, Vanilla JavaScript
- **Security:** `javax.crypto` (AES-256), Spring Security (BCrypt)
- **Data Layer:** Local JSON File DB & Encrypted File Storage

## 🚀 Getting Started

### Prerequisites
- [Java Development Kit (JDK) 25](https://jdk.java.net/25/)
- Maven (included via wrapper)

### Running Locally (Windows/Mac/Linux)

1. **Clone the repository**
   ```bash
   git clone https://github.com/chitrasankara4321-gif/Health-Blockchain.git
   cd Health-Blockchain/backend-java
   ```

2. **Start the Spring Boot Server**
   ```bash
   # On Windows
   .\mvnw.cmd clean spring-boot:run
   
   # On Mac/Linux/Codespaces
   ./mvnw clean spring-boot:run
   ```

3. **Access the Application**
   Open your browser and navigate to: [http://localhost:3000](http://localhost:3000)

### Running in GitHub Codespaces

1. Click **Code** -> **Codespaces** -> **Create Codespace on main**
2. Open the terminal and install Java 25:
   ```bash
   sdk install java 25-open
   sdk default java 25-open
   ```
3. Run the application:
   ```bash
   cd backend-java
   mvn clean spring-boot:run
   ```
4. Click "Open in Browser" when prompted.

## 👥 Default Test Accounts

When you start the server for the very first time, the database will auto-populate with these test accounts:

| Role | Username | Password |
|------|----------|----------|
| Doctor | `dr_wilson` | `doc123456` |
| Doctor | `dr_sankar` | `admin` |
| Patient | `alice_patient` | `pat123456` |
| Patient | `bob_patient` | `pat123456` |
| Patient | `charlie_patient` | `pat123456` |

## 📁 Project Structure

- `/backend-java`: The main Spring Boot backend source code.
- `/frontend`: The HTML/JS templates for the UI.
- `/blockchain`: Smart contract files and deployment scripts.
- `/users`: JSON-based user and access control databases (Ignored by Git).
- `/storage`: Encrypted medical files (Ignored by Git).
- `/audit_logs`: Detailed access logs (Ignored by Git).

## 🔒 Security Notes

The `users.json`, `storage/`, and `audit_logs/` directories are intentionally excluded from version control to prevent exposing sensitive user data or encrypted records.
